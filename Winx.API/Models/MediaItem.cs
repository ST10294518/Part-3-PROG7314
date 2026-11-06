using System.Text.Json.Serialization;

namespace Winx.API.Models
{
    public class MediaItem
    {
        public int Id { get; set; }

        public int TravelEntryId { get; set; }

        public string MediaType { get; set; } = string.Empty;

        public string FileName { get; set; } = string.Empty;

        public string FilePath { get; set; } = string.Empty;

        [JsonIgnore]
        public string? BlobName { get; set; }

        public TravelEntry? TravelEntry { get; set; }
    }
}
