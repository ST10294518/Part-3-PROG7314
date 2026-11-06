using System.ComponentModel.DataAnnotations;

namespace Winx.API.Models;

public class PendingBlobDeletion
{
    [Key]
    public required string BlobName { get; init; }
}
