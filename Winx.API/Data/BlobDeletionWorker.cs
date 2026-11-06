using Azure.Storage.Blobs;
using Azure.Storage.Blobs.Models;
using Microsoft.EntityFrameworkCore;

namespace Winx.API.Data;

public sealed class BlobDeletionWorker(
    IServiceScopeFactory scopeFactory,
    BlobContainerClient container,
    ILogger<BlobDeletionWorker> logger) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        using var timer = new PeriodicTimer(TimeSpan.FromSeconds(30));

        do
        {
            try
            {
                await ProcessPendingDeletionsAsync(stoppingToken);
            }
            catch (Exception error) when (!stoppingToken.IsCancellationRequested)
            {
                logger.LogWarning(error, "Could not process the media cleanup queue.");
            }
        }
        while (await timer.WaitForNextTickAsync(stoppingToken));
    }

    internal async Task ProcessPendingDeletionsAsync(CancellationToken cancellationToken)
    {
        await using var scope = scopeFactory.CreateAsyncScope();
        var context = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        var blobNames = await context.PendingBlobDeletions
            .Select(item => item.BlobName)
            .ToListAsync(cancellationToken);

        foreach (var blobName in blobNames)
        {
            try
            {
                await container.GetBlobClient(blobName).DeleteIfExistsAsync(
                    DeleteSnapshotsOption.IncludeSnapshots,
                    cancellationToken: cancellationToken);

                await context.PendingBlobDeletions
                    .Where(item => item.BlobName == blobName)
                    .ExecuteDeleteAsync(cancellationToken);
            }
            catch (Exception error) when (!cancellationToken.IsCancellationRequested)
            {
                logger.LogWarning(error, "Media blob {BlobName} cleanup will be retried.", blobName);
            }
        }
    }
}
