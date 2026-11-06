using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Winx.API.Controllers;
using Winx.API.Data;
using Winx.API.Models;
using Winx.API.Notifications;
using Xunit;
namespace Winx.API.Tests;

public partial class ApiAuthenticationTests
{
    private static string NotificationDatabase() => $"Data Source=push-{Guid.NewGuid():N};Mode=Memory;Cache=Shared;Pooling=False";
    private static HttpClient NotificationClient(WinxApiFactory app, string uid = "test-account-a") {
        var client = app.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", app.CreateToken(false, uid));
        return client;
    }

    [Fact]
    public async Task EntryActionsCreatePrivateInboxEventsAndReadClearAreOwnerChecked()
    {
        var cs = NotificationDatabase(); await using var connection = new SqliteConnection(cs); await connection.OpenAsync();
        using var app = new WinxApiFactory(cs); using var a = NotificationClient(app); using var b = NotificationClient(app, "test-account-b");
        await using var scope = app.Services.CreateAsyncScope(); var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        await db.Database.EnsureCreatedAsync();
        using var created = await a.PostAsJsonAsync("/api/Entries", OfflineTestEntry(Guid.NewGuid().ToString()));
        Assert.Equal(HttpStatusCode.Created, created.StatusCode);
        var entry = (await created.Content.ReadFromJsonAsync<TravelEntry>())!;
        entry.Notes = "Changed";
        using var edited = await a.PutAsJsonAsync($"/api/Entries/{entry.Id}", entry);
        Assert.Equal(HttpStatusCode.NoContent, edited.StatusCode);
        using var deleted = await a.DeleteAsync($"/api/Entries/{entry.Id}");
        Assert.Equal(HttpStatusCode.NoContent, deleted.StatusCode);
        using var restored = await a.PostAsync($"/api/Entries/{entry.Id}/restore", null);
        Assert.Equal(HttpStatusCode.NoContent, restored.StatusCode);
        var rows = (await a.GetFromJsonAsync<List<NotificationsController.InboxItem>>("/api/Notifications"))!;
        Assert.Equal(4, rows.Count);
        Assert.Contains(rows, n => n.Kind == "entry.created"); Assert.Contains(rows, n => n.Kind == "entry.updated");
        Assert.Contains(rows, n => n.Kind == "entry.deleted"); Assert.Contains(rows, n => n.Kind == "entry.restored");
        Assert.Empty((await b.GetFromJsonAsync<List<NotificationsController.InboxItem>>("/api/Notifications"))!);
        var ids = new NotificationsController.EventIds(rows.Select(n => n.Id).ToArray());
        using var wrongRead = await b.PostAsJsonAsync("/api/Notifications/read", ids);
        using var wrongClear = await b.PostAsJsonAsync("/api/Notifications/clear", ids);
        Assert.All((await a.GetFromJsonAsync<List<NotificationsController.InboxItem>>("/api/Notifications"))!, n => Assert.False(n.Read));
        using var read = await a.PostAsJsonAsync("/api/Notifications/read", ids);
        Assert.All((await a.GetFromJsonAsync<List<NotificationsController.InboxItem>>("/api/Notifications"))!, n => Assert.True(n.Read));
        using var clear = await a.PostAsJsonAsync("/api/Notifications/clear", ids);
        Assert.Empty((await a.GetFromJsonAsync<List<NotificationsController.InboxItem>>("/api/Notifications"))!);
        using var anonymous = app.CreateClient(); using var denied = await anonymous.GetAsync("/api/Notifications");
        Assert.Equal(HttpStatusCode.Unauthorized, denied.StatusCode);
    }

    [Fact]
    public async Task OfflineLostResponseReplayDoesNotCreateAnotherNotification()
    {
        var cs = NotificationDatabase(); await using var connection = new SqliteConnection(cs); await connection.OpenAsync();
        using var app = new WinxApiFactory(cs); using var a = NotificationClient(app);
        await using var scope = app.Services.CreateAsyncScope(); var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        await db.Database.EnsureCreatedAsync();
        var draft = OfflineTestEntry(Guid.NewGuid().ToString());
        using var first = await a.PostAsJsonAsync("/api/Entries/offline", draft);
        using var retry = await a.PostAsJsonAsync("/api/Entries/offline", draft);
        Assert.Equal(HttpStatusCode.Created, first.StatusCode); Assert.Equal(HttpStatusCode.OK, retry.StatusCode);
        Assert.Equal(1, await db.PushNotifications.CountAsync(n => n.Kind == "entry.synced"));
        Assert.Equal(1, await db.TravelEntries.CountAsync());
    }

    [Fact]
    public async Task RejectedMutationDoesNotCreateNotification()
    {
        var cs = NotificationDatabase(); await using var connection = new SqliteConnection(cs); await connection.OpenAsync();
        using var app = new WinxApiFactory(cs); using var a = NotificationClient(app);
        await using var scope = app.Services.CreateAsyncScope(); var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        await db.Database.EnsureCreatedAsync();
        var entry = OfflineTestEntry(Guid.NewGuid().ToString()); entry.Rating = 0;
        using var rejected = await a.PostAsJsonAsync("/api/Entries", entry);
        Assert.Equal(HttpStatusCode.BadRequest, rejected.StatusCode);
        using var missing = await a.DeleteAsync("/api/Entries/99999");
        Assert.Equal(HttpStatusCode.NotFound, missing.StatusCode);
        Assert.Equal(0, await db.PushNotifications.CountAsync());
    }

    [Fact]
    public async Task DeviceRotationAccountSwitchAndRevocationRequireInstallationSecret()
    {
        var cs = NotificationDatabase(); await using var connection = new SqliteConnection(cs); await connection.OpenAsync();
        using var app = new WinxApiFactory(cs); using var a = NotificationClient(app); using var b = NotificationClient(app, "test-account-b");
        await using var scope = app.Services.CreateAsyncScope(); var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        await db.Database.EnsureCreatedAsync();
        var id = Guid.NewGuid().ToString(); var secret = new string('x', 64);
        var body = new NotificationsController.RegisterDevice(id, secret, "token-one", true);
        using var registered = await a.PutAsJsonAsync("/api/Notifications/device", body);
        Assert.Equal(HttpStatusCode.NoContent, registered.StatusCode);
        using var stolen = await b.PutAsJsonAsync("/api/Notifications/device", body with { Secret = new string('y', 64) });
        Assert.Equal(HttpStatusCode.Conflict, stolen.StatusCode);
        using var rotated = await a.PutAsJsonAsync("/api/Notifications/device", body with { Token = "token-two" });
        Assert.Equal(HttpStatusCode.NoContent, rotated.StatusCode);
        using var switched = await b.PutAsJsonAsync("/api/Notifications/device", body with { Token = "token-two" });
        Assert.Equal(HttpStatusCode.NoContent, switched.StatusCode);
        Assert.Equal("test-account-b", (await db.PushDevices.AsNoTracking().SingleAsync()).OwnerUid);
        using var signedOut = app.CreateClient();
        using var wrongSecret = await signedOut.PostAsJsonAsync("/api/Notifications/device/revoke", new NotificationsController.RevokeDevice(id, new string('z',64)));
        Assert.NotNull((await db.PushDevices.AsNoTracking().SingleAsync()).OwnerUid);
        using var revoked = await signedOut.PostAsJsonAsync("/api/Notifications/device/revoke", new NotificationsController.RevokeDevice(id, secret));
        Assert.Equal(HttpStatusCode.NoContent, revoked.StatusCode);
        var device = await db.PushDevices.AsNoTracking().SingleAsync(); Assert.Null(device.OwnerUid); Assert.False(device.Enabled);
        Assert.Equal("token-two", device.Token); Assert.NotEqual(secret, device.BindingHash);
    }

    [Fact]
    public async Task DispatcherRetriesStableEventAndIgnoresOtherOrMutedDevices()
    {
        var cs = NotificationDatabase(); await using var connection = new SqliteConnection(cs); await connection.OpenAsync();
        var sender = new FakeNotificationSender { FailOnce = true };
        using var app = new WinxApiFactory(cs, pushSender: sender); using var a = NotificationClient(app);
        await using var scope = app.Services.CreateAsyncScope(); var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        await db.Database.EnsureCreatedAsync();
        db.PushDevices.AddRange(
            new PushDevice { InstallationId="one", BindingHash="x", OwnerUid="test-account-a", Token="owner-token", Enabled=true },
            new PushDevice { InstallationId="two", BindingHash="y", OwnerUid="test-account-b", Token="other-token", Enabled=true },
            new PushDevice { InstallationId="three", BindingHash="z", OwnerUid="test-account-a", Token="muted-token", Enabled=false });
        await db.SaveChangesAsync();
        using var created = await a.PostAsJsonAsync("/api/Entries", OfflineTestEntry(Guid.NewGuid().ToString()));
        var dispatcher = scope.ServiceProvider.GetRequiredService<PushDispatcher>();
        await dispatcher.ProcessBatchAsync(CancellationToken.None);
        db.ChangeTracker.Clear(); var n = await db.PushNotifications.SingleAsync(); Assert.False(n.Dispatched);
        n.NextAttemptAt = 0; await db.SaveChangesAsync();
        await dispatcher.ProcessBatchAsync(CancellationToken.None);
        Assert.True((await db.PushNotifications.AsNoTracking().SingleAsync()).Dispatched);
        Assert.Equal(2, sender.Calls.Count); Assert.All(sender.Calls, c => Assert.Equal("owner-token", c.Token));
        Assert.Single(sender.Calls.Select(c => c.EventId).Distinct());
    }

    [Fact]
    public async Task InvalidTokenIsDisabledWithoutDroppingInboxHistory()
    {
        var cs = NotificationDatabase(); await using var connection = new SqliteConnection(cs); await connection.OpenAsync();
        var sender = new FakeNotificationSender { Invalid = true };
        using var app = new WinxApiFactory(cs, pushSender: sender); using var a = NotificationClient(app);
        await using var scope = app.Services.CreateAsyncScope(); var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        await db.Database.EnsureCreatedAsync();
        db.PushDevices.Add(new PushDevice { InstallationId="one", BindingHash="x", OwnerUid="test-account-a", Token="expired" });
        await db.SaveChangesAsync();
        using var created = await a.PostAsJsonAsync("/api/Entries", OfflineTestEntry(Guid.NewGuid().ToString()));
        await scope.ServiceProvider.GetRequiredService<PushDispatcher>().ProcessBatchAsync(CancellationToken.None);
        Assert.False((await db.PushDevices.AsNoTracking().SingleAsync()).Enabled);
        Assert.Single((await a.GetFromJsonAsync<List<NotificationsController.InboxItem>>("/api/Notifications"))!);
    }

    private sealed class FakeNotificationSender : IPushSender
    {
        public bool FailOnce { get; set; } public bool Invalid { get; set; }
        public List<(string Token, string EventId)> Calls { get; } = new();
        public Task<PushSendResult> SendAsync(string token, PushNotification n, CancellationToken ct) {
            Calls.Add((token, n.Id));
            if (FailOnce) { FailOnce=false; throw new IOException("Simulated temporary outage"); }
            return Task.FromResult(Invalid ? PushSendResult.InvalidToken : PushSendResult.Sent);
        }
    }
}
