using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Infrastructure;
using Microsoft.EntityFrameworkCore.Migrations;
using Microsoft.Extensions.DependencyInjection;
using Winx.API.Data;
using Winx.API.Models;
using Xunit;

namespace Winx.API.Tests;

public partial class ApiAuthenticationTests
{
    private static TravelEntry OfflineTestEntry(string operationId) => new()
    {
        Title = "Offline memory", Country = "South Africa", Location = "Durban",
        Date = new DateTime(2026, 11, 6), Rating = 4, Notes = "Saved without a connection",
        ClientOperationId = operationId
    };

    [Fact]
    public async Task OfflineRetryCreatesOneRecordAndDoesNotOverwriteLaterEdit()
    {
        var cs = $"Data Source=offline-{Guid.NewGuid():N};Mode=Memory;Cache=Shared;Pooling=False";
        await using var connection = new SqliteConnection(cs);
        await connection.OpenAsync();
        using var app = new WinxApiFactory(cs);
        using var client = app.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", app.CreateToken(false));
        await using var scope = app.Services.CreateAsyncScope();
        var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        await db.Database.EnsureCreatedAsync();
        var draft = OfflineTestEntry(Guid.NewGuid().ToString());
        using var first = await client.PostAsJsonAsync("/api/Entries/offline", draft);
        Assert.Equal(HttpStatusCode.Created, first.StatusCode);
        var saved = await first.Content.ReadFromJsonAsync<TravelEntry>();
        Assert.NotNull(saved);
        // Repeating after a lost response must return the same server ID.
        using var repeated = await client.PostAsJsonAsync("/api/Entries/offline", draft);
        Assert.Equal(HttpStatusCode.OK, repeated.StatusCode);
        var replay = await repeated.Content.ReadFromJsonAsync<TravelEntry>();
        Assert.NotNull(replay);
        Assert.Equal(saved.Id, replay.Id);
        Assert.Equal(1, await db.TravelEntries.CountAsync());
        Assert.Equal(1, await db.OfflineCreateReceipts.CountAsync());
        saved.Title = "Later cloud edit";
        using var edited = await client.PutAsJsonAsync($"/api/Entries/{saved.Id}", saved);
        Assert.Equal(HttpStatusCode.NoContent, edited.StatusCode);
        using var oldRetry = await client.PostAsJsonAsync("/api/Entries/offline", draft);
        var current = await oldRetry.Content.ReadFromJsonAsync<TravelEntry>();
        Assert.NotNull(current);
        Assert.Equal("Later cloud edit", current.Title);
    }

    [Fact]
    public async Task OfflineOperationKeysAreScopedByAuthenticatedAccount()
    {
        var cs = $"Data Source=offline-{Guid.NewGuid():N};Mode=Memory;Cache=Shared;Pooling=False";
        await using var connection = new SqliteConnection(cs);
        await connection.OpenAsync();
        using var app = new WinxApiFactory(cs);
        using var accountA = app.CreateClient();
        using var accountB = app.CreateClient();
        accountA.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", app.CreateToken(false));
        accountB.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", app.CreateToken(false, "test-account-b"));
        await using var scope = app.Services.CreateAsyncScope();
        var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        await db.Database.EnsureCreatedAsync();
        var draft = OfflineTestEntry(Guid.NewGuid().ToString());
        using var first = await accountA.PostAsJsonAsync("/api/Entries/offline", draft);
        using var second = await accountB.PostAsJsonAsync("/api/Entries/offline", draft);
        Assert.Equal(HttpStatusCode.Created, first.StatusCode);
        Assert.Equal(HttpStatusCode.Created, second.StatusCode);
        var a = await first.Content.ReadFromJsonAsync<TravelEntry>();
        var b = await second.Content.ReadFromJsonAsync<TravelEntry>();
        Assert.NotNull(a); Assert.NotNull(b);
        Assert.NotEqual(a.Id, b.Id);
        using var denied = await accountB.GetAsync($"/api/Entries/{a.Id}");
        Assert.Equal(HttpStatusCode.NotFound, denied.StatusCode);
        Assert.Equal(2, await db.OfflineCreateReceipts.CountAsync());
    }

    [Fact]
    public async Task OfflineRetryCannotResurrectPermanentlyDeletedEntry()
    {
        var cs = $"Data Source=offline-{Guid.NewGuid():N};Mode=Memory;Cache=Shared;Pooling=False";
        await using var connection = new SqliteConnection(cs);
        await connection.OpenAsync();
        using var app = new WinxApiFactory(cs);
        using var client = app.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", app.CreateToken(false));
        await using var scope = app.Services.CreateAsyncScope();
        var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        await db.Database.EnsureCreatedAsync();
        var draft = OfflineTestEntry(Guid.NewGuid().ToString());
        using var first = await client.PostAsJsonAsync("/api/Entries/offline", draft);
        var saved = await first.Content.ReadFromJsonAsync<TravelEntry>();
        Assert.NotNull(saved);
        using var deleted = await client.DeleteAsync($"/api/Entries/{saved.Id}");
        Assert.Equal(HttpStatusCode.NoContent, deleted.StatusCode);
        using var permanent = await client.DeleteAsync($"/api/Entries/{saved.Id}/permanent");
        Assert.Equal(HttpStatusCode.NoContent, permanent.StatusCode);
        using var retry = await client.PostAsJsonAsync("/api/Entries/offline", draft);
        Assert.Equal(HttpStatusCode.Gone, retry.StatusCode);
        Assert.Equal(0, await db.TravelEntries.CountAsync());
        Assert.Equal(1, await db.OfflineCreateReceipts.CountAsync());
    }

    [Fact]
    public async Task OfflineCreationRejectsMissingOperationId()
    {
        using var app = new WinxApiFactory();
        using var client = app.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", app.CreateToken(false));
        using var response = await client.PostAsJsonAsync("/api/Entries/offline", OfflineTestEntry(""));
        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }
}

public class OfflineMigrationTests
{
    [Fact]
    public async Task UpgradeFromSoftDeletePreservesEntryAndAddsOfflineSchema()
    {
        await using var connection = new SqliteConnection("Data Source=:memory:");
        await connection.OpenAsync();
        await using var db = new AppDbContext(new DbContextOptionsBuilder<AppDbContext>().UseSqlite(connection).Options);
        await db.GetService<IMigrator>().MigrateAsync("20261106174936_AddEntrySoftDelete");
        await db.Database.ExecuteSqlRawAsync("INSERT INTO TravelEntries (OwnerUid,Title,Location,Country,Date,Rating,Notes,IsDeleted) VALUES ('a','Keep me','','','2026-11-06',4,'',0)");
        await db.Database.MigrateAsync();
        Assert.Equal("Keep me", (await db.TravelEntries.AsNoTracking().SingleAsync()).Title);
        Assert.Equal(0, await db.OfflineCreateReceipts.CountAsync());
        Assert.False(db.Database.HasPendingModelChanges());
    }
}
