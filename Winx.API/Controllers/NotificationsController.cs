using System.Security.Cryptography;
using System.Text;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Winx.API.Data;
using Winx.API.Models;
namespace Winx.API.Controllers;

[ApiController]
[Route("api/Notifications")]
public class NotificationsController(AppDbContext db) : ControllerBase
{
    private string Uid => User.FindFirst("sub")!.Value;
    public record RegisterDevice(string InstallationId, string Secret, string Token, bool Enabled);
    public record RevokeDevice(string InstallationId, string Secret);
    public record EventIds(string[] Ids, long? BeforeReceivedAt = null);
    public record InboxItem(string Id, string Title, string Body, long ReceivedAt, bool Read, string Kind, int? EntryId);
    private static string Hash(string secret) => Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(secret)));
    private static bool Valid(string id, string secret) => Guid.TryParse(id, out _) &&
        !string.IsNullOrWhiteSpace(secret) && secret.Length >= 32 && secret.Length <= 256;

    [HttpPut("device")]
    public async Task<IActionResult> Register(RegisterDevice request)
    {
        if (!Valid(request.InstallationId, request.Secret) || string.IsNullOrWhiteSpace(request.Token) || request.Token.Length > 4096)
            return BadRequest();
        var id = Guid.Parse(request.InstallationId).ToString("N");
        var device = await db.PushDevices.FindAsync(id);
        var hash = Hash(request.Secret);
        if (device != null && device.BindingHash != hash) return Conflict();
        // A token cannot be silently stolen by presenting another installation ID.
        if (await db.PushDevices.AnyAsync(d => d.Token == request.Token && d.InstallationId != id)) return Conflict();
        if (device == null) { device = new PushDevice { InstallationId = id, BindingHash = hash }; db.PushDevices.Add(device); }
        device.OwnerUid = Uid; device.Token = request.Token; device.Enabled = request.Enabled;
        device.UpdatedAt = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        try { await db.SaveChangesAsync(); } catch (DbUpdateException) { return Conflict(); }
        return NoContent();
    }

    // Revocation still works after Firebase sign-out. A random installation secret
    // authorises only disabling this device; it never grants account or inbox access.
    [AllowAnonymous]
    [HttpPost("device/revoke")]
    public async Task<IActionResult> Revoke(RevokeDevice request)
    {
        if (!Valid(request.InstallationId, request.Secret)) return BadRequest();
        var id = Guid.Parse(request.InstallationId).ToString("N");
        var hash = Hash(request.Secret);
        await db.PushDevices.Where(d => d.InstallationId == id && d.BindingHash == hash)
            .ExecuteUpdateAsync(s => s.SetProperty(d => d.OwnerUid, (string?)null).SetProperty(d => d.Enabled, false));
        return NoContent();
    }

    [HttpGet]
    public async Task<ActionResult<List<InboxItem>>> Get()
    {
        var rows = await db.PushNotifications.AsNoTracking().Where(n => n.OwnerUid == Uid && !n.Hidden)
            .OrderByDescending(n => n.ReceivedAt).ThenByDescending(n => n.Id).Take(100)
            .Select(n => new InboxItem(n.Id, n.Title, n.Body, n.ReceivedAt, n.Read, n.Kind, n.EntryId)).ToListAsync();
        return Ok(rows);
    }

    [HttpPost("read")]
    public async Task<IActionResult> MarkRead(EventIds request)
    {
        if (request.Ids == null || request.Ids.Length > 1000) return BadRequest();
        await db.PushNotifications.Where(n => n.OwnerUid == Uid && request.Ids.Contains(n.Id))
            .ExecuteUpdateAsync(s => s.SetProperty(n => n.Read, true));
        return NoContent();
    }

    [HttpPost("clear")]
    public async Task<IActionResult> Clear(EventIds request)
    {
        if (request.Ids == null || request.Ids.Length > 1000) return BadRequest();
        // Cutoff also clears older history outside the latest 100 visible items.
        // Events created after the user's clear action are preserved.
        var before = request.BeforeReceivedAt ?? 0;
        await db.PushNotifications.Where(n => n.OwnerUid == Uid &&
            (request.Ids.Contains(n.Id) || (before > 0 && n.ReceivedAt <= before)))
            .ExecuteUpdateAsync(s => s.SetProperty(n => n.Hidden, true));
        return NoContent();
    }
}
