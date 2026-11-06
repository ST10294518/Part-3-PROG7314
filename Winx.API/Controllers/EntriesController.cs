using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Winx.API.Data;
using Winx.API.Models;
using Winx.API.Notifications;

namespace Winx.API.Controllers;

[ApiController]
[Route("api/[controller]")]
public class EntriesController : ControllerBase
{
    private readonly AppDbContext _context;

    private string OwnerUid => User.FindFirst("sub")!.Value;

    private IQueryable<TravelEntry> OwnedEntries
    {
        get
        {
            var uid = OwnerUid;

            return _context.TravelEntries
                .Where(entry => entry.OwnerUid == uid);
        }
    }

    private IQueryable<TravelEntry> ActiveEntries =>
        OwnedEntries.Where(entry => !entry.IsDeleted);

    public EntriesController(AppDbContext context)
    {
        _context = context;
    }

    [HttpGet]
    public async Task<ActionResult<IEnumerable<TravelEntry>>> GetEntries()
    {
        var entries = await ActiveEntries
            .AsNoTracking()
            .ToListAsync();

        return Ok(entries);
    }

    [HttpGet("{id:int}")]
    public async Task<ActionResult<TravelEntry>> GetEntry(int id)
    {
        var entry = await ActiveEntries
            .AsNoTracking()
            .FirstOrDefaultAsync(entry => entry.Id == id);

        if (entry == null)
        {
            return NotFound(new
            {
                message = $"Entry with ID {id} was not found."
            });
        }

        return Ok(entry);
    }

    [HttpPost]
    public async Task<ActionResult<TravelEntry>> CreateEntry(
        TravelEntry entry)
    {
        if (entry.Rating < 1 || entry.Rating > 5)
        {
            return BadRequest(new
            {
                message = "Rating must be between 1 and 5."
            });
        }

        entry.Id = 0;
        entry.OwnerUid = OwnerUid;
        entry.IsDeleted = false;
        entry.ClientOperationId = null;

        // Media is created through the owner-checked media endpoint.
        entry.MediaItems = new List<MediaItem>();

        _context.TravelEntries.Add(entry);
        NotificationEvents.Add(_context, OwnerUid, "entry.created");
        await _context.SaveChangesAsync();

        return CreatedAtAction(
            nameof(GetEntry),
            new { id = entry.Id },
            entry
        );
    }

    // Idempotent creation: the receipt and entry are committed together.
    [HttpPost("offline")]
    public async Task<ActionResult<TravelEntry>> CreateOfflineEntry(TravelEntry entry)
    {
        if (!Guid.TryParse(entry.ClientOperationId, out var operationId) ||
            entry.Rating < 1 || entry.Rating > 5 || string.IsNullOrWhiteSpace(entry.Title))
        {
            return BadRequest(new { message = "A valid operation ID, title and rating are required." });
        }
        var uid = OwnerUid;
        var key = operationId.ToString("D");
        var receipt = await _context.OfflineCreateReceipts.AsNoTracking()
            .FirstOrDefaultAsync(r => r.OwnerUid == uid && r.OperationId == key);
        if (receipt != null)
        {
            var previous = await OwnedEntries.AsNoTracking().FirstOrDefaultAsync(e => e.Id == receipt.EntryId);
            if (previous == null) return StatusCode(410, new { message = "The original entry was permanently deleted." });
            return Ok(previous); // Never overwrite a later cloud edit on retry.
        }

        await using var transaction = await _context.Database.BeginTransactionAsync();
        entry.Id = 0;
        entry.OwnerUid = uid;
        entry.ClientOperationId = key;
        entry.IsDeleted = false;
        entry.MediaItems = new List<MediaItem>();
        _context.TravelEntries.Add(entry);
        try
        {
            await _context.SaveChangesAsync();
            _context.OfflineCreateReceipts.Add(new OfflineCreateReceipt
            {
                OwnerUid = uid, OperationId = key, EntryId = entry.Id
            });
            await _context.SaveChangesAsync();
            NotificationEvents.Add(_context, uid, "entry.synced", entry.Id);
            await _context.SaveChangesAsync();
            await transaction.CommitAsync();
        }
        catch (DbUpdateException)
        {
            await transaction.RollbackAsync();
            _context.ChangeTracker.Clear();
            // A concurrent retry can win the database unique-index race.
            receipt = await _context.OfflineCreateReceipts.AsNoTracking()
                .FirstOrDefaultAsync(r => r.OwnerUid == uid && r.OperationId == key);
            if (receipt == null) throw;
            var previous = await OwnedEntries.AsNoTracking().FirstOrDefaultAsync(e => e.Id == receipt.EntryId);
            if (previous == null) return StatusCode(410, new { message = "The original entry was permanently deleted." });
            return Ok(previous);
        }
        return CreatedAtAction(nameof(GetEntry), new { id = entry.Id }, entry);
    }

    [HttpPut("{id:int}")]
    public async Task<IActionResult> UpdateEntry(
        int id,
        TravelEntry updatedEntry)
    {
        if (id != updatedEntry.Id && updatedEntry.Id != 0)
        {
            return BadRequest(new
            {
                message =
                    "The entry ID in the URL does not match the entry ID in the request."
            });
        }

        if (updatedEntry.Rating < 1 || updatedEntry.Rating > 5)
        {
            return BadRequest(new
            {
                message = "Rating must be between 1 and 5."
            });
        }

        var existingEntry = await ActiveEntries
            .FirstOrDefaultAsync(entry => entry.Id == id);

        if (existingEntry == null)
        {
            return NotFound(new
            {
                message = $"Entry with ID {id} was not found."
            });
        }

        var changed = existingEntry.Title != updatedEntry.Title || existingEntry.Location != updatedEntry.Location ||
            existingEntry.Country != updatedEntry.Country || existingEntry.Date != updatedEntry.Date ||
            existingEntry.Rating != updatedEntry.Rating || existingEntry.Notes != updatedEntry.Notes;
        existingEntry.Title = updatedEntry.Title;
        existingEntry.Location = updatedEntry.Location;
        existingEntry.Country = updatedEntry.Country;
        existingEntry.Date = updatedEntry.Date;
        existingEntry.Rating = updatedEntry.Rating;
        existingEntry.Notes = updatedEntry.Notes;
        if (changed) NotificationEvents.Add(_context, OwnerUid, "entry.updated", id);

        await _context.SaveChangesAsync();

        return NoContent();
    }

    // Move to Recently Deleted. Preserve media for restoration.
    [HttpDelete("{id:int}")]
    public async Task<IActionResult> DeleteEntry(int id)
    {
        await using var transaction =
            await _context.Database.BeginTransactionAsync();

        var entry = await ActiveEntries
            .FirstOrDefaultAsync(entry => entry.Id == id);

        if (entry == null)
        {
            return NotFound(new
            {
                message = $"Entry with ID {id} was not found."
            });
        }

        entry.IsDeleted = true;
        NotificationEvents.Add(_context, OwnerUid, "entry.deleted", id);

        await _context.SaveChangesAsync();
        await transaction.CommitAsync();

        return NoContent();
    }

    [HttpGet("deleted")]
    public async Task<ActionResult<IEnumerable<TravelEntry>>>
        GetDeletedEntries()
    {
        var entries = await OwnedEntries
            .Where(entry => entry.IsDeleted)
            .AsNoTracking()
            .OrderByDescending(entry => entry.Id)
            .ToListAsync();

        return Ok(entries);
    }

    [HttpPost("{id:int}/restore")]
    public async Task<IActionResult> RestoreEntry(int id)
    {
        var entry = await OwnedEntries
            .FirstOrDefaultAsync(entry =>
                entry.Id == id && entry.IsDeleted);

        if (entry == null)
        {
            return NotFound();
        }

        entry.IsDeleted = false;
        NotificationEvents.Add(_context, OwnerUid, "entry.restored", id);

        await _context.SaveChangesAsync();

        return NoContent();
    }

    // Queue blob cleanup only when deletion becomes permanent.
    [HttpDelete("{id:int}/permanent")]
    public async Task<IActionResult> PermanentlyDeleteEntry(int id)
    {
        await using var transaction =
            await _context.Database.BeginTransactionAsync();

        var entry = await OwnedEntries
            .FirstOrDefaultAsync(entry =>
                entry.Id == id && entry.IsDeleted);

        if (entry == null)
        {
            return NotFound();
        }

        await QueueBlobDeletions(new[] { entry.Id });

        _context.TravelEntries.Remove(entry);
        NotificationEvents.Add(_context, OwnerUid, "entry.permanent", id);

        await _context.SaveChangesAsync();
        await transaction.CommitAsync();

        return NoContent();
    }

    [HttpDelete("deleted")]
    public async Task<IActionResult> EmptyDeletedEntries()
    {
        await using var transaction =
            await _context.Database.BeginTransactionAsync();

        var entries = await OwnedEntries
            .Where(entry => entry.IsDeleted)
            .ToListAsync();

        if (entries.Count > 0)
        {
            var entryIds = entries
                .Select(entry => entry.Id)
                .ToArray();

            await QueueBlobDeletions(entryIds);

            _context.TravelEntries.RemoveRange(entries);
            NotificationEvents.Add(_context, OwnerUid, "entries.emptied");

            await _context.SaveChangesAsync();
        }

        await transaction.CommitAsync();

        return NoContent();
    }

    // Called within the same transaction as permanent database removal.
    private async Task QueueBlobDeletions(int[] entryIds)
    {
        var blobNames = await _context.MediaItems
            .Where(media =>
                entryIds.Contains(media.TravelEntryId) &&
                media.BlobName != null &&
                media.BlobName != "")
            .Select(media => media.BlobName!)
            .Distinct()
            .ToListAsync();

        _context.PendingBlobDeletions.AddRange(
            blobNames.Select(blobName =>
                new PendingBlobDeletion
                {
                    BlobName = blobName
                }));
    }
}