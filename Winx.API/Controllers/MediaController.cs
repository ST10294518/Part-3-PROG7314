using Azure;
using Azure.Storage.Blobs;
using Azure.Storage.Blobs.Models;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Winx.API.Data;
using Winx.API.Models;

namespace Winx.API.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class MediaController : ControllerBase
    {
        private readonly AppDbContext _context;

        private string OwnerUid => User.FindFirst("sub")!.Value;

        private IQueryable<MediaItem> OwnedMedia
        {
            get
            {
                var uid = OwnerUid;
                return _context.MediaItems.Where(media =>
                    media.TravelEntry != null && media.TravelEntry.OwnerUid == uid && !media.TravelEntry.IsDeleted);
            }
        }

        public MediaController(AppDbContext context)
        {
            _context = context;
        }

        // GET: api/Media/entry/1
        [HttpGet("entry/{travelEntryId}")]
        public async Task<ActionResult<IEnumerable<MediaItem>>> GetMediaForEntry(int travelEntryId)
        {
            var uid = OwnerUid;
            var entryExists = await _context.TravelEntries
                .AnyAsync(e => e.Id == travelEntryId && e.OwnerUid == uid && !e.IsDeleted);

            if (!entryExists)
            {
                return NotFound(new
                {
                    message = $"Travel entry with ID {travelEntryId} was not found."
                });
            }

            var media = await OwnedMedia
                .Where(m => m.TravelEntryId == travelEntryId)
                .AsNoTracking()
                .ToListAsync();

            return Ok(media);
        }

        // GET: api/Media/1
        [HttpGet("{id}")]
        public async Task<ActionResult<MediaItem>> GetMedia(int id)
        {
            var media = await OwnedMedia
                .AsNoTracking()
                .FirstOrDefaultAsync(m => m.Id == id);

            if (media == null)
            {
                return NotFound(new
                {
                    message = $"Media item with ID {id} was not found."
                });
            }

            return Ok(media);
        }

        // POST: api/Media
        [HttpPost]
        public async Task<ActionResult<MediaItem>> CreateMedia(MediaItem media)
        {
            if (media.TravelEntryId <= 0)
            {
                return BadRequest(new
                {
                    message = "A valid travel entry ID is required."
                });
            }

            if (string.IsNullOrWhiteSpace(media.MediaType))
            {
                return BadRequest(new
                {
                    message = "Media type is required."
                });
            }

            if (media.MediaType != "Photo" && media.MediaType != "Video")
            {
                return BadRequest(new
                {
                    message = "Media type must be either Photo or Video."
                });
            }

            if (string.IsNullOrWhiteSpace(media.FileName))
            {
                return BadRequest(new
                {
                    message = "File name is required."
                });
            }

            if (string.IsNullOrWhiteSpace(media.FilePath))
            {
                return BadRequest(new
                {
                    message = "File path is required."
                });
            }

            var uid = OwnerUid;
            var entryExists = await _context.TravelEntries
                .AnyAsync(e => e.Id == media.TravelEntryId && e.OwnerUid == uid && !e.IsDeleted);

            if (!entryExists)
            {
                return NotFound(new
                {
                    message = $"Travel entry with ID {media.TravelEntryId} was not found."
                });
            }

            media.Id = 0;
            media.TravelEntry = null;
            media.MediaType = media.MediaType.Trim();
            media.FileName = media.FileName.Trim();
            media.FilePath = media.FilePath.Trim();

            _context.MediaItems.Add(media);
            await _context.SaveChangesAsync();

            return CreatedAtAction(
                nameof(GetMedia),
                new { id = media.Id },
                media);
        }

        [HttpPost("entry/{travelEntryId:int}/upload")]
        [RequestSizeLimit(26 * 1024 * 1024)]
        public async Task<ActionResult<MediaItem>> UploadMedia(
            int travelEntryId,
            [FromForm] IFormFile file,
            [FromHeader(Name = "Idempotency-Key")] Guid? operationId,
            [FromServices] ILogger<MediaController> logger,
            CancellationToken cancellationToken)
        {
            var uid = OwnerUid;
            if (!await OwnsActiveEntryAsync(travelEntryId, uid, cancellationToken))
            {
                return NotFound(new { message = $"Travel entry with ID {travelEntryId} was not found." });
            }

            if (operationId == Guid.Empty)
            {
                return BadRequest(new { message = "The upload operation ID must be a non-empty UUID." });
            }

            if (file.Length is <= 0 or > 25 * 1024 * 1024)
            {
                return BadRequest(new { message = "Select a non-empty file no larger than 25 MiB." });
            }

            var fileName = Path.GetFileName(file.FileName.Replace('\\', '/'));
            if (fileName.Length > 255)
            {
                return BadRequest(new { message = "The file name is too long." });
            }

            var (mediaType, contentType) = Path.GetExtension(fileName).ToLowerInvariant() switch
            {
                ".jpg" or ".jpeg" => ("Photo", "image/jpeg"),
                ".png" => ("Photo", "image/png"),
                ".webp" => ("Photo", "image/webp"),
                ".mp4" => ("Video", "video/mp4"),
                _ => ("", "")
            };

            if (mediaType.Length == 0)
            {
                return BadRequest(new { message = "Use a JPEG, PNG, WebP, or MP4 file." });
            }

            // Each attempt owns a separate blob. A losing concurrent retry can delete
            // its own blob without touching the blob already referenced by SQLite.
            var prefix = operationId is { } operation ? $"upload-{operation:N}-" : null;
            var completed = await FindCompletedUploadAsync(travelEntryId, prefix, cancellationToken);
            if (completed is not null)
            {
                return Ok(completed);
            }

            var container = HttpContext.RequestServices.GetService<BlobContainerClient>();
            if (container is null)
            {
                return StatusCode(StatusCodes.Status503ServiceUnavailable,
                    new { message = "Media storage is unavailable." });
            }

            var blobName = prefix + Guid.NewGuid().ToString("N");
            var blob = container.GetBlobClient(blobName);
            var media = new MediaItem
            {
                TravelEntryId = travelEntryId,
                MediaType = mediaType,
                FileName = fileName,
                BlobName = blobName
            };

            try
            {
                await using (var stream = file.OpenReadStream())
                {
                    await blob.UploadAsync(stream, new BlobUploadOptions
                    {
                        HttpHeaders = new BlobHttpHeaders { ContentType = contentType }
                    }, cancellationToken);
                }

                // SQLite serializes writers. Recheck after upload inside the transaction
                // so overlapping retries cannot insert two media rows for one operation.
                // Complete the database write even if the caller disconnects after upload.
                await using var transaction = await _context.Database.BeginTransactionAsync();
                if (await OwnsActiveEntryAsync(travelEntryId, uid, CancellationToken.None))
                {
                    completed = await FindCompletedUploadAsync(travelEntryId, prefix, CancellationToken.None);
                    if (completed is null)
                    {
                        _context.MediaItems.Add(media);
                        await _context.SaveChangesAsync();
                        await transaction.CommitAsync();
                        return CreatedAtAction(nameof(GetMedia), new { id = media.Id }, media);
                    }
                }
            }
            catch (RequestFailedException error)
            {
                await TryDeleteUploadAsync(blob, blobName, logger);
                logger.LogWarning(error, "Media upload failed for entry {EntryId}.", travelEntryId);
                return StatusCode(StatusCodes.Status503ServiceUnavailable,
                    new { message = "Media upload failed. Please try again." });
            }
            catch
            {
                await TryDeleteUploadAsync(blob, blobName, logger);
                throw;
            }

            await TryDeleteUploadAsync(blob, blobName, logger);
            if (completed is not null)
            {
                return Ok(completed);
            }

            return NotFound(new { message = $"Travel entry with ID {travelEntryId} was not found." });
        }

        private Task<bool> OwnsActiveEntryAsync(int id, string uid, CancellationToken cancellationToken) =>
            _context.TravelEntries.AnyAsync(entry => entry.Id == id && entry.OwnerUid == uid &&
                !entry.IsDeleted, cancellationToken);

        private Task<MediaItem?> FindCompletedUploadAsync(
            int entryId, string? prefix, CancellationToken cancellationToken) =>
            prefix is null
                ? Task.FromResult<MediaItem?>(null)
                : OwnedMedia.AsNoTracking().FirstOrDefaultAsync(
                    item => item.TravelEntryId == entryId && item.BlobName != null &&
                        item.BlobName.StartsWith(prefix), cancellationToken);

        private static async Task TryDeleteUploadAsync(
            BlobClient blob, string blobName, ILogger<MediaController> logger)
        {
            try
            {
                using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(10));
                await blob.DeleteIfExistsAsync(cancellationToken: timeout.Token);
            }
            catch (Exception error)
            {
                logger.LogWarning(error, "Could not clean up media blob {BlobName}.", blobName);
            }
        }

        [HttpGet("{id:int}/content")]
        public async Task<IActionResult> DownloadMedia(int id, CancellationToken cancellationToken)
        {
            var media = await OwnedMedia
                .AsNoTracking()
                .FirstOrDefaultAsync(item => item.Id == id, cancellationToken);

            if (media is null || string.IsNullOrEmpty(media.BlobName))
            {
                return NotFound(new { message = $"Media content with ID {id} was not found." });
            }

            var container = HttpContext.RequestServices.GetService<BlobContainerClient>();
            if (container is null)
            {
                return StatusCode(StatusCodes.Status503ServiceUnavailable,
                    new { message = "Media storage is unavailable." });
            }

            var blob = container.GetBlobClient(media.BlobName);
            try
            {
                var properties = await blob.GetPropertiesAsync(cancellationToken: cancellationToken);
                var stream = await blob.OpenReadAsync(
                    new BlobOpenReadOptions(allowModifications: false),
                    cancellationToken);

                Response.Headers["Cache-Control"] = "private, no-store";
                Response.Headers["X-Content-Type-Options"] = "nosniff";

                return File(
                    stream,
                    properties.Value.ContentType ?? "application/octet-stream",
                    media.FileName,
                    enableRangeProcessing: true);
            }
            catch (Azure.RequestFailedException ex) when (ex.Status == 404)
            {
                return NotFound(new { message = $"Media content with ID {id} was not found." });
            }
        }

        // DELETE: api/Media/1
        [HttpDelete("{id}")]
        public async Task<IActionResult> DeleteMedia(int id)
        {
            await using var transaction = await _context.Database.BeginTransactionAsync();

            var media = await OwnedMedia
                .FirstOrDefaultAsync(m => m.Id == id);

            if (media == null)
            {
                return NotFound(new
                {
                    message = $"Media item with ID {id} was not found."
                });
            }

            if (!string.IsNullOrEmpty(media.BlobName))
            {
                _context.PendingBlobDeletions.Add(new PendingBlobDeletion { BlobName = media.BlobName });
            }

            _context.MediaItems.Remove(media);
            await _context.SaveChangesAsync();
            await transaction.CommitAsync();

            return NoContent();
        }
    }
}
