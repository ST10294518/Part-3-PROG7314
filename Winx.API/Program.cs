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

// Register API controllers
builder.Services.AddControllers();

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

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.Run();