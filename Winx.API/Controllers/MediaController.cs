using Azure;
using Azure.Storage.Blobs;
using Azure.Storage.Blobs.Models;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Winx.API.Data;
using Winx.API.Models;
using Winx.API.Notifications;

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