using System.Text.Json.Serialization;

namespace Winx.API.Models
{
    public class TravelEntry
    {
        public int Id { get; set; }

        // Stable identifier supplied only by the dedicated offline-create endpoint.
        public string? ClientOperationId { get; set; }

        // Soft deletion preserves the original ID and associated media for restoration.
        public bool IsDeleted { get; set; }

        // Null for legacy entries that predate Firebase ownership.
        [JsonIgnore]
        public string? OwnerUid { get; set; }

        public string Title { get; set; } = string.Empty;

        public string Location { get; set; } = string.Empty;

        public string Country { get; set; } = string.Empty;

        public DateTime Date { get; set; }

        public int Rating { get; set; }

        public string Notes { get; set; } = string.Empty;

        public ICollection<MediaItem> MediaItems { get; set; }
            = new List<MediaItem>();
    }
}
