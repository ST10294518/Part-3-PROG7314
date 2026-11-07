using Azure.Data.Tables;
using Azure.Identity;
using Azure.Storage.Blobs;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Authorization;
using Microsoft.IdentityModel.Tokens;
using Microsoft.EntityFrameworkCore;
using Microsoft.OpenApi;
using Winx.API.Data;

var builder = WebApplication.CreateBuilder(args);

var firebaseProjectId = builder.Configuration["Firebase:ProjectId"];
if (string.IsNullOrWhiteSpace(firebaseProjectId))
{
    throw new InvalidOperationException("Set Firebase__ProjectId before starting Winx.API.");
}

var firebaseIssuer = $"https://securetoken.google.com/{firebaseProjectId}";

builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.Authority = firebaseIssuer;
        options.Audience = firebaseProjectId;
        options.MapInboundClaims = false;
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidIssuer = firebaseIssuer,
            ValidAlgorithms = new[] { SecurityAlgorithms.RsaSha256 },
            ClockSkew = TimeSpan.Zero
        };
        options.Events = new JwtBearerEvents
        {
            OnTokenValidated = context =>
            {
                var uid = context.Principal?.FindFirst("sub")?.Value;
                if (string.IsNullOrWhiteSpace(uid) || uid.Length > 128)
                {
                    context.Fail("The Firebase token has no valid user ID.");
                }

                return Task.CompletedTask;
            }
        };
    });

builder.Services.AddAuthorization(options =>
{
    options.FallbackPolicy = new AuthorizationPolicyBuilder()
        .RequireAuthenticatedUser()
        .Build();
});

builder.Services.AddSingleton<Winx.API.Notifications.IPushSender, Winx.API.Notifications.FirebasePushSender>();
builder.Services.AddScoped<Winx.API.Notifications.PushDispatcher>();
if (builder.Configuration.GetValue<bool>("Push:Enabled"))
    builder.Services.AddHostedService<Winx.API.Notifications.PushDeliveryWorker>();

// Register API controllers
builder.Services.AddControllers();

var mediaContainerUri = builder.Configuration["MediaStorage:ContainerUri"];
if (!string.IsNullOrWhiteSpace(mediaContainerUri))
{
    builder.Services.AddSingleton(_ => new BlobContainerClient(
        new Uri(mediaContainerUri, UriKind.Absolute),
        new ManagedIdentityCredential(ManagedIdentityId.SystemAssigned)));
    builder.Services.AddHostedService<BlobDeletionWorker>();
}

var favouritesEndpoint = builder.Configuration["FavouritesStorage:ServiceUri"];
if (!string.IsNullOrWhiteSpace(favouritesEndpoint))
{
    builder.Services.AddSingleton(_ => new TableClient(
        new Uri(favouritesEndpoint, UriKind.Absolute),
        builder.Configuration["FavouritesStorage:TableName"] ?? "WinxJournalFavourites",
        new ManagedIdentityCredential(ManagedIdentityId.SystemAssigned)));
}

// Register SQLite database
builder.Services.AddDbContext<AppDbContext>(options =>
    options.UseSqlite(
        builder.Configuration.GetConnectionString("DefaultConnection")
        ?? "Data Source=winx.db"
    ));

// Register Swagger/OpenAPI
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(options =>
{
    options.SwaggerDoc("v1", new OpenApiInfo
    {
        Title = "Winx REST API",
        Version = "v1",
        Description = "REST API for the Winx travel and restaurant journal."
    });
});

var app = builder.Build();

// Initialise the SQLite schema when explicitly enabled for deployment.
if (app.Configuration.GetValue<bool>("Database:MigrateOnStartup"))
{
    await using var scope = app.Services.CreateAsyncScope();
    var database = scope.ServiceProvider.GetRequiredService<AppDbContext>().Database;

    // Use a rollback journal for the database file on App Service's shared storage.
    await database.OpenConnectionAsync();
    await using var command = database.GetDbConnection().CreateCommand();
    command.CommandText = "PRAGMA journal_mode = DELETE;";
    var journalMode = await command.ExecuteScalarAsync();

    if (!string.Equals(journalMode as string, "delete", StringComparison.OrdinalIgnoreCase))
    {
        throw new InvalidOperationException("Could not enable SQLite rollback journaling.");
    }

    await database.MigrateAsync();
}

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.Run();