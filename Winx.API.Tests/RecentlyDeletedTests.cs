using System.Security.Claims;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Winx.API.Controllers;
using Winx.API.Data;
using Winx.API.Models;
using Xunit;

namespace Winx.API.Tests;

public class RecentlyDeletedTests
{
    private static EntriesController Controller(AppDbContext db, string uid) => new(db)
    {
        ControllerContext = new ControllerContext
        {
            HttpContext = new DefaultHttpContext
            {
                User = new ClaimsPrincipal(new ClaimsIdentity(
                    new[] { new Claim("sub", uid) }, "Test"))
            }
        }
    };

    [Fact]
    public async Task DeleteAndRestorePreserveIdAndMedia()
    {
        using var connection = new SqliteConnection("Data Source=:memory:");
        await connection.OpenAsync();
        await using var db = new AppDbContext(new DbContextOptionsBuilder<AppDbContext>()
            .UseSqlite(connection).Options);
        await db.Database.EnsureCreatedAsync();
        var entry = new TravelEntry { OwnerUid = "a", Title = "Memory", Rating = 4 };
        db.TravelEntries.Add(entry);
        await db.SaveChangesAsync();
        db.MediaItems.Add(new MediaItem {
            TravelEntryId = entry.Id, MediaType = "Photo", FileName = "photo.jpg", FilePath = "test"
        });
        await db.SaveChangesAsync();
        var controller = Controller(db, "a");
        var originalId = entry.Id;

        Assert.IsType<NoContentResult>(await controller.DeleteEntry(entry.Id));
        Assert.True(entry.IsDeleted);
        var active = Assert.IsType<OkObjectResult>((await controller.GetEntries()).Result);
        Assert.Empty(Assert.IsAssignableFrom<IEnumerable<TravelEntry>>(active.Value));
        var deleted = Assert.IsType<OkObjectResult>((await controller.GetDeletedEntries()).Result);
        Assert.Equal(originalId, Assert.Single(
            Assert.IsAssignableFrom<IEnumerable<TravelEntry>>(deleted.Value)).Id);
        Assert.Single(await db.MediaItems.ToListAsync());

        Assert.IsType<NoContentResult>(await controller.RestoreEntry(entry.Id));
        Assert.False(entry.IsDeleted);
        Assert.Equal(originalId, entry.Id);
        Assert.Single(await db.MediaItems.ToListAsync());
    }

    [Fact]
    public async Task OtherAccountCannotListRestoreOrPermanentlyDelete()
    {
        using var connection = new SqliteConnection("Data Source=:memory:");
        await connection.OpenAsync();
        await using var db = new AppDbContext(new DbContextOptionsBuilder<AppDbContext>()
            .UseSqlite(connection).Options);
        await db.Database.EnsureCreatedAsync();
        var entry = new TravelEntry { OwnerUid = "a", Title = "Private", IsDeleted = true };
        db.TravelEntries.Add(entry);
        await db.SaveChangesAsync();
        var other = Controller(db, "b");
        var result = Assert.IsType<OkObjectResult>((await other.GetDeletedEntries()).Result);
        Assert.Empty(Assert.IsAssignableFrom<IEnumerable<TravelEntry>>(result.Value));
        Assert.IsType<NotFoundResult>(await other.RestoreEntry(entry.Id));
        Assert.IsType<NotFoundResult>(await other.PermanentlyDeleteEntry(entry.Id));
        await other.EmptyDeletedEntries();
        Assert.True(await db.TravelEntries.AnyAsync(e => e.Id == entry.Id));
    }

    [Fact]
    public async Task PermanentRemovalRequiresDeletedStateAndEmptyKeepsActiveEntries()
    {
        using var connection = new SqliteConnection("Data Source=:memory:");
        await connection.OpenAsync();
        await using var db = new AppDbContext(new DbContextOptionsBuilder<AppDbContext>()
            .UseSqlite(connection).Options);
        await db.Database.EnsureCreatedAsync();
        var active = new TravelEntry { OwnerUid = "a", Title = "Active" };
        var deleted = new TravelEntry { OwnerUid = "a", Title = "Deleted", IsDeleted = true };
        db.TravelEntries.AddRange(active, deleted);
        await db.SaveChangesAsync();
        var owner = Controller(db, "a");
        Assert.IsType<NotFoundResult>(await owner.PermanentlyDeleteEntry(active.Id));
        Assert.IsType<NoContentResult>(await owner.EmptyDeletedEntries());
        Assert.Equal(active.Id, Assert.Single(await db.TravelEntries.ToListAsync()).Id);
    }
}
