using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Winx.API.Data;
using Winx.API.Models;

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
            return _context.TravelEntries.Where(entry => entry.OwnerUid == uid);
        }
    }

    private IQueryable<TravelEntry> ActiveEntries => OwnedEntries.Where(e => !e.IsDeleted);

    public EntriesController(AppDbContext context)
    {
        _context = context;
    }

    // GET: api/Entries
    [HttpGet]
    public async Task<ActionResult<IEnumerable<TravelEntry>>> GetEntries()
    {
        var entries = await ActiveEntries
            .AsNoTracking()
            .ToListAsync();

        return Ok(entries);
    }

    // GET: api/Entries/5
    [HttpGet("{id:int}")]
    public async Task<ActionResult<TravelEntry>> GetEntry(int id)
    {
        var entry = await ActiveEntries
            .AsNoTracking()
            .FirstOrDefaultAsync(e => e.Id == id);

        if (entry == null)
        {
            return NotFound(new
            {
                message = $"Entry with ID {id} was not found."
            });
        }

        return Ok(entry);
    }

    // POST: api/Entries
    [HttpPost]
    public async Task<ActionResult<TravelEntry>> CreateEntry(TravelEntry entry)
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

        // Media must be created through the owner-checked media endpoint.
        entry.MediaItems = new List<MediaItem>();

        _context.TravelEntries.Add(entry);
        await _context.SaveChangesAsync();

        return CreatedAtAction(
            nameof(GetEntry),
            new { id = entry.Id },
            entry
        );
    }

    // PUT: api/Entries/5
    [HttpPut("{id:int}")]
    public async Task<IActionResult> UpdateEntry(
        int id,
        TravelEntry updatedEntry)
    {
        if (id != updatedEntry.Id && updatedEntry.Id != 0)
        {
            return BadRequest(new
            {
                message = "The entry ID in the URL does not match the entry ID in the request."
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
            .FirstOrDefaultAsync(e => e.Id == id);

        if (existingEntry == null)
        {
            return NotFound(new
            {
                message = $"Entry with ID {id} was not found."
            });
        }

        existingEntry.Title = updatedEntry.Title;
        existingEntry.Location = updatedEntry.Location;
        existingEntry.Country = updatedEntry.Country;
        existingEntry.Date = updatedEntry.Date;
        existingEntry.Rating = updatedEntry.Rating;
        existingEntry.Notes = updatedEntry.Notes;

        await _context.SaveChangesAsync();

        return NoContent();
    }

    // DELETE: api/Entries/5
    [HttpDelete("{id:int}")]
    public async Task<IActionResult> DeleteEntry(int id)
    {
        var entry = await ActiveEntries
            .FirstOrDefaultAsync(e => e.Id == id);

        if (entry == null)
        {
            return NotFound(new
            {
                message = $"Entry with ID {id} was not found."
            });
        }

        entry.IsDeleted = true;
        await _context.SaveChangesAsync();

        return NoContent();
    }
    // Deleted entries remain account-owned and are never returned in the active list.
    [HttpGet("deleted")]
    public async Task<ActionResult<IEnumerable<TravelEntry>>> GetDeletedEntries()
    {
        return Ok(await OwnedEntries.Where(e => e.IsDeleted)
            .AsNoTracking().OrderByDescending(e => e.Id).ToListAsync());
    }

    [HttpPost("{id:int}/restore")]
    public async Task<IActionResult> RestoreEntry(int id)
    {
        var entry = await OwnedEntries.FirstOrDefaultAsync(e => e.Id == id && e.IsDeleted);
        if (entry == null) return NotFound();
        entry.IsDeleted = false;
        await _context.SaveChangesAsync();
        return NoContent();
    }

    [HttpDelete("{id:int}/permanent")]
    public async Task<IActionResult> PermanentlyDeleteEntry(int id)
    {
        var entry = await OwnedEntries.FirstOrDefaultAsync(e => e.Id == id && e.IsDeleted);
        if (entry == null) return NotFound();
        _context.TravelEntries.Remove(entry);
        await _context.SaveChangesAsync();
        return NoContent();
    }

    [HttpDelete("deleted")]
    public async Task<IActionResult> EmptyDeletedEntries()
    {
        var entries = await OwnedEntries.Where(e => e.IsDeleted).ToListAsync();
        _context.TravelEntries.RemoveRange(entries);
        await _context.SaveChangesAsync();
        return NoContent();
    }
}