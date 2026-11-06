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
            [FromServices] ILogger<MediaController> logger,
            CancellationToken cancellationToken)
        {
            var uid = OwnerUid;
            var entryExists = await _context.TravelEntries.AnyAsync(
                entry => entry.Id == travelEntryId && entry.OwnerUid == uid,
                cancellationToken);

            if (!entryExists)
            {
                return NotFound(new { message = $"Travel entry with ID {travelEntryId} was not found." });
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

            var container = HttpContext.RequestServices.GetService<BlobContainerClient>();
            if (container is null)
            {
                return StatusCode(StatusCodes.Status503ServiceUnavailable,
                    new { message = "Media storage is unavailable." });
            }

            var blobName = Guid.NewGuid().ToString("N");
            var blob = container.GetBlobClient(blobName);
            var media = new MediaItem
            {
                TravelEntryId = travelEntryId,
                MediaType = mediaType,
                FileName = fileName,
                BlobName = blobName
            };

            await using var stream = file.OpenReadStream();
            try
            {
                await blob.UploadAsync(stream, new BlobUploadOptions
                {
                    HttpHeaders = new BlobHttpHeaders { ContentType = contentType }
                }, cancellationToken);

                _context.MediaItems.Add(media);
                // Complete the database write even if the caller disconnects after upload.
                await _context.SaveChangesAsync();
            }
            catch
            {
                try
                {
                    using var cleanupTimeout = new CancellationTokenSource(TimeSpan.FromSeconds(10));
                    await blob.DeleteIfExistsAsync(cancellationToken: cleanupTimeout.Token);
                }
                catch (Exception cleanupError)
                {
                    logger.LogWarning(cleanupError, "Could not clean up media blob {BlobName}.", blobName);
                }

                throw;
            }

            return CreatedAtAction(nameof(GetMedia), new { id = media.Id }, media);
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
