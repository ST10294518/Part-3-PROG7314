namespace Winx.API.Models;

// Kept independently of entries so delete/restore history remains readable.
public class PushNotification
{
    public string Id { get; set; } = Guid.NewGuid().ToString("N");
    public string OwnerUid { get; set; } = "";
    public string Title { get; set; } = "";
    public string Body { get; set; } = "";
    public string Kind { get; set; } = "";
    public int? EntryId { get; set; }
    public long ReceivedAt { get; set; } = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
    public bool Read { get; set; }
    public bool Hidden { get; set; }
    public bool Dispatched { get; set; }
    public int Attempts { get; set; }
    public long NextAttemptAt { get; set; }
}

public class PushDevice
{
    public string InstallationId { get; set; } = "";
    public string BindingHash { get; set; } = "";
    public string? OwnerUid { get; set; }
    public string Token { get; set; } = "";
    public bool Enabled { get; set; } = true;
    public long UpdatedAt { get; set; }
}
