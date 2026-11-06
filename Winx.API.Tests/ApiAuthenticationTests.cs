using System.IdentityModel.Tokens.Jwt;
using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Security.Claims;
using System.Security.Cryptography;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.IdentityModel.Protocols.OpenIdConnect;
using Microsoft.IdentityModel.Tokens;
using Winx.API.Data;
using Winx.API.Models;
using Xunit;

namespace Winx.API.Tests;

public class ApiAuthenticationTests
{
    [Theory]
    [InlineData("/api/Entries")]
    [InlineData("/api/Media/entry/1")]
    public async Task GetWithoutToken_ReturnsUnauthorized(string route)
    {
        using var app = new WinxApiFactory();
        using var client = app.CreateClient();
        using var response = await client.GetAsync(route);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Theory]
    [InlineData(true, HttpStatusCode.Unauthorized)]
    [InlineData(false, HttpStatusCode.BadRequest)]
    public async Task CreateEntry_EnforcesTokenExpiry(
        bool expired,
        HttpStatusCode expectedStatus)
    {
        using var app = new WinxApiFactory();
        using var client = app.CreateClient();
        client.DefaultRequestHeaders.Authorization =
            new AuthenticationHeaderValue("Bearer", app.CreateToken(expired));

        // A zero rating is rejected before the endpoint writes to the database.
        using var response = await client.PostAsJsonAsync("/api/Entries", new
        {
            Title = "Authentication test",
            Location = "Durban",
            Country = "South Africa",
            Date = DateTime.UtcNow,
            Rating = 0,
            Notes = ""
        });

        Assert.Equal(expectedStatus, response.StatusCode);
    }

    [Fact]
    public async Task EntryAndMedia_CannotBeAccessedByAnotherAccount()
    {
        var databaseConnectionString =
            $"Data Source=winx-{Guid.NewGuid():N};Mode=Memory;Cache=Shared;Pooling=False";
        await using var connection = new SqliteConnection(databaseConnectionString);
        await connection.OpenAsync();

        using var app = new WinxApiFactory(databaseConnectionString);
        using var owner = app.CreateClient();
        using var otherAccount = app.CreateClient();

        owner.DefaultRequestHeaders.Authorization =
            new AuthenticationHeaderValue("Bearer", app.CreateToken(expired: false));
        otherAccount.DefaultRequestHeaders.Authorization =
            new AuthenticationHeaderValue("Bearer", app.CreateToken(expired: false, uid: "test-account-b"));

        await using (var scope = app.Services.CreateAsyncScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
            await db.Database.EnsureCreatedAsync();
        }

        using var createEntry = await owner.PostAsJsonAsync("/api/Entries", new
        {
            Title = "Ownership test",
            Location = "Durban",
            Country = "South Africa",
            Date = DateTime.UtcNow,
            Rating = 4,
            Notes = "Integration test"
        });
        Assert.Equal(HttpStatusCode.Created, createEntry.StatusCode);
        var entry = await createEntry.Content.ReadFromJsonAsync<TravelEntry>();
        Assert.NotNull(entry);

        using var createMedia = await owner.PostAsJsonAsync("/api/Media", new
        {
            TravelEntryId = entry.Id,
            MediaType = "Photo",
            FileName = "ownership-test.jpg",
            FilePath = "test-placeholder"
        });
        Assert.Equal(HttpStatusCode.Created, createMedia.StatusCode);
        var media = await createMedia.Content.ReadFromJsonAsync<MediaItem>();
        Assert.NotNull(media);

        using var ownerEntry = await owner.GetAsync($"/api/Entries/{entry.Id}");
        using var ownerMedia = await owner.GetAsync($"/api/Media/{media.Id}");
        Assert.Equal(HttpStatusCode.OK, ownerEntry.StatusCode);
        Assert.Equal(HttpStatusCode.OK, ownerMedia.StatusCode);

        var otherEntries = await otherAccount.GetFromJsonAsync<TravelEntry[]>("/api/Entries");
        Assert.NotNull(otherEntries);
        Assert.Empty(otherEntries);

        using var otherEntry = await otherAccount.GetAsync($"/api/Entries/{entry.Id}");
        using var otherMedia = await otherAccount.GetAsync($"/api/Media/{media.Id}");
        Assert.Equal(HttpStatusCode.NotFound, otherEntry.StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, otherMedia.StatusCode);

        using var otherEditEntry = await otherAccount.PutAsJsonAsync(
            $"/api/Entries/{entry.Id}", new
            {
                Title = "Cross-account edit",
                Location = "Durban",
                Country = "South Africa",
                Date = DateTime.UtcNow,
                Rating = 4,
                Notes = "This update must be rejected"
            });
        using var otherAddMedia = await otherAccount.PostAsJsonAsync("/api/Media", new
        {
            TravelEntryId = entry.Id,
            MediaType = "Photo",
            FileName = "cross-account.jpg",
            FilePath = "test-placeholder"
        });
        using var otherDeleteMedia = await otherAccount.DeleteAsync($"/api/Media/{media.Id}");
        using var otherDeleteEntry = await otherAccount.DeleteAsync($"/api/Entries/{entry.Id}");

        Assert.Equal(HttpStatusCode.NotFound, otherEditEntry.StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, otherAddMedia.StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, otherDeleteMedia.StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, otherDeleteEntry.StatusCode);

        var savedEntry = await owner.GetFromJsonAsync<TravelEntry>($"/api/Entries/{entry.Id}");
        var savedMedia = await owner.GetFromJsonAsync<MediaItem[]>($"/api/Media/entry/{entry.Id}");

        Assert.NotNull(savedEntry);
        Assert.Equal(entry.Title, savedEntry.Title);
        Assert.NotNull(savedMedia);
        Assert.Equal(media.Id, Assert.Single(savedMedia).Id);
    }

    // TravelEntry identifies the assembly containing the Winx.API entry point.
    private sealed class WinxApiFactory(string databaseConnectionString = "Data Source=:memory:") : WebApplicationFactory<TravelEntry>
    {
        private const string ProjectId = "winx-integration-tests";
        private const string Issuer = "https://securetoken.google.com/" + ProjectId;
        private readonly RsaSecurityKey _signingKey = CreateSigningKey();

        protected override IHost CreateHost(IHostBuilder builder)
        {
            builder.ConfigureHostConfiguration(configuration =>
            {
                configuration.AddInMemoryCollection(new Dictionary<string, string?>
                {
                    ["Firebase:ProjectId"] = ProjectId,
                    ["ConnectionStrings:DefaultConnection"] = databaseConnectionString
                });
            });

            return base.CreateHost(builder);
        }

        protected override void ConfigureWebHost(IWebHostBuilder builder)
        {
            builder.ConfigureTestServices(services =>
            {
                services.Configure<JwtBearerOptions>(
                    JwtBearerDefaults.AuthenticationScheme,
                    options =>
                    {
                        var configuration = new OpenIdConnectConfiguration
                        {
                            Issuer = Issuer
                        };
                        configuration.SigningKeys.Add(_signingKey);
                        options.Configuration = configuration;
                    });
            });
        }

        public string CreateToken(bool expired, string uid = "test-account-a")
        {
            var now = DateTime.UtcNow;
            var token = new JwtSecurityToken(
                issuer: Issuer,
                audience: ProjectId,
                claims: new[] { new Claim(JwtRegisteredClaimNames.Sub, uid) },
                notBefore: now.AddMinutes(-10),
                expires: expired ? now.AddMinutes(-5) : now.AddMinutes(5),
                signingCredentials: new SigningCredentials(
                    _signingKey, SecurityAlgorithms.RsaSha256));

            return new JwtSecurityTokenHandler().WriteToken(token);
        }

        private static RsaSecurityKey CreateSigningKey()
        {
            using var rsa = RSA.Create(2048);
            return new RsaSecurityKey(rsa.ExportParameters(includePrivateParameters: true))
            {
                KeyId = "winx-test-key"
            };
        }
    }
}
