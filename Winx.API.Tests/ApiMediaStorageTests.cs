using System.Collections.Concurrent;
using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using Azure;
using Azure.Storage.Blobs;
using Azure.Storage.Blobs.Models;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Winx.API.Data;
using Winx.API.Models;
using Xunit;

namespace Winx.API.Tests;

public partial class ApiAuthenticationTests
{
    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task BlobMedia_EnforcesOwnershipAndQueuesDeletion(bool deleteParentEntry)
    {
        var connectionString =
            $"Data Source=winx-media-{Guid.NewGuid():N};Mode=Memory;Cache=Shared;Pooling=False";
        await using var connection = new SqliteConnection(connectionString);
        await connection.OpenAsync();

        using var app = new WinxApiFactory(connectionString, new InMemoryBlobContainer());
        using var owner = app.CreateClient();
        using var other = app.CreateClient();

        owner.DefaultRequestHeaders.Authorization =
            new AuthenticationHeaderValue("Bearer", app.CreateToken(expired: false));

        other.DefaultRequestHeaders.Authorization =
            new AuthenticationHeaderValue(
                "Bearer",
                app.CreateToken(expired: false, uid: "test-account-b"));

        await using var scope = app.Services.CreateAsyncScope();
        var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        await db.Database.EnsureCreatedAsync();

        using var created = await owner.PostAsJsonAsync("/api/Entries", new
        {
            Title = "Media storage test",
            Location = "Durban",
            Country = "South Africa",
            Date = DateTime.UtcNow,
            Rating = 4,
            Notes = ""
        });

        Assert.Equal(HttpStatusCode.Created, created.StatusCode);

        var entry = await created.Content.ReadFromJsonAsync<TravelEntry>();
        Assert.NotNull(entry);

        var bytes = Convert.FromBase64String(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a1xkAAAAASUVORK5CYII=");

        var uploadUrl = $"/api/Media/entry/{entry.Id}/upload";

        using var deniedForm = CreateUploadForm(bytes);
        using var deniedUpload = await other.PostAsync(uploadUrl, deniedForm);
        Assert.Equal(HttpStatusCode.NotFound, deniedUpload.StatusCode);

        using var form = CreateUploadForm(bytes);
        using var uploaded = await owner.PostAsync(uploadUrl, form);
        Assert.Equal(HttpStatusCode.Created, uploaded.StatusCode);

        var media = await uploaded.Content.ReadFromJsonAsync<MediaItem>();
        Assert.NotNull(media);

        var contentUrl = $"/api/Media/{media.Id}/content";

        using var deniedDownload = await other.GetAsync(contentUrl);
        Assert.Equal(HttpStatusCode.NotFound, deniedDownload.StatusCode);

        using var downloaded = await owner.GetAsync(contentUrl);
        Assert.Equal(HttpStatusCode.OK, downloaded.StatusCode);
        Assert.Equal("image/png", downloaded.Content.Headers.ContentType?.MediaType);
        Assert.Equal(bytes, await downloaded.Content.ReadAsByteArrayAsync());

        var stored = await db.MediaItems.AsNoTracking().SingleAsync();
        Assert.False(string.IsNullOrWhiteSpace(stored.BlobName));

        if (deleteParentEntry)
        {
            // Soft deletion must preserve media for restoration.
            using var softDeleted =
                await owner.DeleteAsync($"/api/Entries/{entry.Id}");

            Assert.Equal(HttpStatusCode.NoContent, softDeleted.StatusCode);

            using var hiddenContent = await owner.GetAsync(contentUrl);
            Assert.Equal(HttpStatusCode.NotFound, hiddenContent.StatusCode);

            var retainedEntry = await db.TravelEntries
                .AsNoTracking()
                .SingleAsync(item => item.Id == entry.Id);

            Assert.True(retainedEntry.IsDeleted);

            Assert.True(await db.MediaItems
                .AsNoTracking()
                .AnyAsync(item => item.Id == media.Id));

            Assert.False(await db.PendingBlobDeletions.AnyAsync());

            // Other accounts cannot restore or permanently remove the entry.
            using var deniedRestore = await other.PostAsync(
                $"/api/Entries/{entry.Id}/restore",
                null);

            Assert.Equal(HttpStatusCode.NotFound, deniedRestore.StatusCode);

            using var deniedPermanentDelete = await other.DeleteAsync(
                $"/api/Entries/{entry.Id}/permanent");

            Assert.Equal(
                HttpStatusCode.NotFound,
                deniedPermanentDelete.StatusCode);

            // Restoration must make the original media available again.
            using var restored = await owner.PostAsync(
                $"/api/Entries/{entry.Id}/restore",
                null);

            Assert.Equal(HttpStatusCode.NoContent, restored.StatusCode);

            using var restoredContent = await owner.GetAsync(contentUrl);
            Assert.Equal(HttpStatusCode.OK, restoredContent.StatusCode);
            Assert.Equal(
                bytes,
                await restoredContent.Content.ReadAsByteArrayAsync());

            var restoredEntry = await db.TravelEntries
                .AsNoTracking()
                .SingleAsync(item => item.Id == entry.Id);

            Assert.False(restoredEntry.IsDeleted);
            Assert.False(await db.PendingBlobDeletions.AnyAsync());

            // Only permanent deletion should queue the blob for cleanup.
            using var deletedAgain =
                await owner.DeleteAsync($"/api/Entries/{entry.Id}");

            Assert.Equal(HttpStatusCode.NoContent, deletedAgain.StatusCode);

            using var permanentlyDeleted = await owner.DeleteAsync(
                $"/api/Entries/{entry.Id}/permanent");

            Assert.Equal(
                HttpStatusCode.NoContent,
                permanentlyDeleted.StatusCode);

            Assert.False(await db.TravelEntries
                .AsNoTracking()
                .AnyAsync(item => item.Id == entry.Id));
        }
        else
        {
            // Direct media deletion still queues cleanup immediately.
            using var deleted =
                await owner.DeleteAsync($"/api/Media/{media.Id}");

            Assert.Equal(HttpStatusCode.NoContent, deleted.StatusCode);
        }

        using var afterDelete = await owner.GetAsync(contentUrl);
        Assert.Equal(HttpStatusCode.NotFound, afterDelete.StatusCode);

        Assert.False(await db.MediaItems
            .AsNoTracking()
            .AnyAsync(item => item.Id == media.Id));

        var pending = await db.PendingBlobDeletions
            .AsNoTracking()
            .SingleAsync();

        Assert.Equal(stored.BlobName, pending.BlobName);
    }

    private static MultipartFormDataContent CreateUploadForm(byte[] bytes)
    {
        var form = new MultipartFormDataContent();
        form.Add(new ByteArrayContent(bytes), "file", "test.png");
        return form;
    }

    private sealed class InMemoryBlobContainer : BlobContainerClient
    {
        private readonly ConcurrentDictionary<string, (byte[] Bytes, string ContentType)> _blobs = new();

        public override BlobClient GetBlobClient(string blobName) =>
            new InMemoryBlobClient(_blobs, blobName);
    }

    private sealed class InMemoryBlobClient(
        ConcurrentDictionary<string, (byte[] Bytes, string ContentType)> blobs,
        string blobName) : BlobClient
    {
        public override async Task<Response<BlobContentInfo>> UploadAsync(
            Stream content, BlobUploadOptions options, CancellationToken cancellationToken = default)
        {
            using var buffer = new MemoryStream();
            await content.CopyToAsync(buffer, cancellationToken);
            blobs[blobName] = (buffer.ToArray(), options.HttpHeaders.ContentType);
            return new TestResponse<BlobContentInfo>(BlobsModelFactory.BlobContentInfo());
        }

        public override Task<Response<BlobProperties>> GetPropertiesAsync(
            BlobRequestConditions? conditions = null, CancellationToken cancellationToken = default)
        {
            var properties = BlobsModelFactory.BlobProperties(contentType: blobs[blobName].ContentType);
            return Task.FromResult<Response<BlobProperties>>(new TestResponse<BlobProperties>(properties));
        }

        public override Task<Stream> OpenReadAsync(
            BlobOpenReadOptions options, CancellationToken cancellationToken = default) =>
            Task.FromResult<Stream>(new MemoryStream(blobs[blobName].Bytes, writable: false));
    }

    private sealed class TestResponse<T>(T value) : Response<T>
    {
        public override T Value => value;
        public override Response GetRawResponse() => throw new NotSupportedException();
    }
}