using Microsoft.EntityFrameworkCore;
using Winx.API.Data;
namespace Winx.API.Notifications;

public sealed class PushDispatcher(AppDbContext db, IPushSender sender, ILogger<PushDispatcher> logger)
{
    public async Task ProcessBatchAsync(CancellationToken ct)
    {
        var now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        var events = await db.PushNotifications.Where(n => !n.Dispatched && !n.Hidden && !n.Read && n.NextAttemptAt <= now)
            .OrderBy(n => n.ReceivedAt).Take(20).ToListAsync(ct);
        foreach (var n in events)
        {
            var retry = false;
            var devices = await db.PushDevices.AsNoTracking().Where(d => d.OwnerUid == n.OwnerUid && d.Enabled).ToListAsync(ct);
            foreach (var device in devices)
            {
                // Recheck immediately before send; delayed packets are also UID checked on the phone.
                if (!await db.PushDevices.AnyAsync(d => d.InstallationId == device.InstallationId &&
                    d.OwnerUid == n.OwnerUid && d.Enabled && d.Token == device.Token, ct)) continue;
                try
                {
                    var result = await sender.SendAsync(device.Token, n, ct);
                    if (result == PushSendResult.Sent)
                        logger.LogInformation("FCM accepted notification event {EventId}.", n.Id);
                    if (result == PushSendResult.InvalidToken)
                        await db.PushDevices.Where(d => d.InstallationId == device.InstallationId && d.Token == device.Token)
                            .ExecuteUpdateAsync(s => s.SetProperty(d => d.Enabled, false), ct);
                }
                catch (OperationCanceledException) when (ct.IsCancellationRequested) { throw; }
                catch (Exception)
                {
                    retry = true;
                    // Never log credentials, tokens, user text or SDK request bodies.
                    logger.LogWarning("FCM send failed for event {EventId}; retry scheduled. Check server credentials and FCM API access.", n.Id);
                }
            }
            n.Attempts++;
            n.Dispatched = !retry;
            n.NextAttemptAt = now + Math.Min(900, 5 * (1L << Math.Min(n.Attempts, 7))) * 1000;
            await db.SaveChangesAsync(ct);
        }
    }
}

public sealed class PushDeliveryWorker(IServiceScopeFactory scopes, ILogger<PushDeliveryWorker> logger) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        using var timer = new PeriodicTimer(TimeSpan.FromSeconds(3));
        do
        {
            try {
                await using var scope = scopes.CreateAsyncScope();
                await scope.ServiceProvider.GetRequiredService<PushDispatcher>().ProcessBatchAsync(stoppingToken);
            }
            catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested) { break; }
            catch (Exception) { logger.LogWarning("Notification dispatcher unavailable. Check database migrations and server configuration."); }
        } while (await timer.WaitForNextTickAsync(stoppingToken));
    }
}
