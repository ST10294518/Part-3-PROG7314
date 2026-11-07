namespace Winx.API.Models;

// No foreign key: the receipt survives permanent entry deletion and blocks resurrection on retry.
public class OfflineCreateReceipt
{
    public string OwnerUid { get; set; } = string.Empty;
    public string OperationId { get; set; } = string.Empty;
    public int EntryId { get; set; }
}
