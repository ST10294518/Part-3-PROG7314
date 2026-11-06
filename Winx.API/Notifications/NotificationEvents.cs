using Winx.API.Data;
using Winx.API.Models;
namespace Winx.API.Notifications;

public static class NotificationEvents
{
    // Add before the action's SaveChanges: event and mutation commit together.
    public static void Add(AppDbContext db, string uid, string kind, int? entryId = null)
    {
        var (title, body) = kind switch
        {
            "entry.created" => ("Memory saved", "Your new travel memory was saved successfully."),
            "entry.synced" => ("Offline memory synced", "Your offline travel memory is now saved in the cloud."),
            "entry.updated" => ("Memory updated", "Your travel memory changes were saved."),
            "entry.deleted" => ("Memory moved to Recently Deleted", "You can restore this memory from Recently Deleted."),
            "entry.restored" => ("Memory restored", "Your travel memory is available again."),
            "entry.permanent" => ("Memory permanently deleted", "The memory was permanently removed. Media cleanup is queued."),
            "entries.emptied" => ("Recently Deleted cleared", "Your deleted memories were permanently removed."),
            "media.added" => ("Media added", "Your photo or video was added to your travel memory."),
            "media.deleted" => ("Media removed", "The media item was removed from your travel memory."),
            _ => throw new ArgumentException("Unknown notification event.", nameof(kind))
        };
        db.PushNotifications.Add(new PushNotification { OwnerUid = uid, Kind = kind,
            EntryId = entryId, Title = title, Body = body });
    }
}
