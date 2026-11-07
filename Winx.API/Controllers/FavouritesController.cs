using System.Globalization;
using System.Security.Cryptography;
using System.Text;
using Azure.Data.Tables;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Winx.API.Data;

namespace Winx.API.Controllers;

[ApiController]
[Route("api/[controller]")]
public class FavouritesController(AppDbContext context, TableClient? table = null) : ControllerBase
{
    private string OwnerUid => User.FindFirst("sub")!.Value;

    private string PartitionKey =>
        Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(OwnerUid)));

    [HttpGet]
    public async Task<ActionResult<int[]>> GetFavourites(CancellationToken cancellationToken)
    {
        if (table is null)
        {
            return StatusCode(StatusCodes.Status503ServiceUnavailable);
        }

        var partition = PartitionKey;
        var filter = TableClient.CreateQueryFilter($"PartitionKey eq {partition}");
        var favouriteIds = new List<int>();
        await foreach (var entity in table.QueryAsync<TableEntity>(
            filter, select: new[] { "RowKey" }, cancellationToken: cancellationToken))
        {
            if (int.TryParse(entity.RowKey, NumberStyles.None, CultureInfo.InvariantCulture, out var id))
            {
                favouriteIds.Add(id);
            }
        }

        if (favouriteIds.Count == 0)
        {
            return Ok(Array.Empty<int>());
        }

        var ownerUid = OwnerUid;
        var activeIds = await context.TravelEntries
            .Where(entry => entry.OwnerUid == ownerUid && !entry.IsDeleted && favouriteIds.Contains(entry.Id))
            .OrderBy(entry => entry.Id)
            .Select(entry => entry.Id)
            .ToArrayAsync(cancellationToken);

        return Ok(activeIds);
    }

    [HttpPut("{id:int}")]
    public Task<IActionResult> SaveFavourite(int id, CancellationToken cancellationToken) =>
        ChangeFavourite(id, save: true, cancellationToken);

    [HttpDelete("{id:int}")]
    public Task<IActionResult> RemoveFavourite(int id, CancellationToken cancellationToken) =>
        ChangeFavourite(id, save: false, cancellationToken);

    private async Task<IActionResult> ChangeFavourite(int id, bool save, CancellationToken cancellationToken)
    {
        if (table is null)
        {
            return StatusCode(StatusCodes.Status503ServiceUnavailable);
        }

        var ownerUid = OwnerUid;
        var ownsEntry = await context.TravelEntries.AnyAsync(
            entry => entry.Id == id && entry.OwnerUid == ownerUid && !entry.IsDeleted, cancellationToken);
        if (!ownsEntry)
        {
            return NotFound();
        }

        var partition = PartitionKey;
        var row = id.ToString(CultureInfo.InvariantCulture);
        if (save)
        {
            await table.UpsertEntityAsync(new TableEntity(partition, row),
                cancellationToken: cancellationToken);
        }
        else
        {
            await table.DeleteEntityAsync(partition, row, cancellationToken: cancellationToken);
        }

        return NoContent();
    }
}
