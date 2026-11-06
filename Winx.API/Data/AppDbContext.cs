using Microsoft.EntityFrameworkCore;
using Winx.API.Models;

namespace Winx.API.Data
{
    public class AppDbContext : DbContext
    {
        public AppDbContext(DbContextOptions<AppDbContext> options)
            : base(options)
        {
        }

        public DbSet<OfflineCreateReceipt> OfflineCreateReceipts { get; set; }

        public DbSet<TravelEntry> TravelEntries { get; set; }

        public DbSet<MediaItem> MediaItems { get; set; }

        public DbSet<PendingBlobDeletion> PendingBlobDeletions { get; set; }

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            base.OnModelCreating(modelBuilder);

            modelBuilder.Entity<OfflineCreateReceipt>()
                .HasKey(e => new { e.OwnerUid, e.OperationId });
            modelBuilder.Entity<OfflineCreateReceipt>()
                .Property(e => e.OperationId).HasMaxLength(36);

            modelBuilder.Entity<TravelEntry>()
                .Property(e => e.ClientOperationId).HasMaxLength(36);
            modelBuilder.Entity<TravelEntry>()
                .HasIndex(e => new { e.OwnerUid, e.ClientOperationId })
                .IsUnique()
                .HasFilter("\"ClientOperationId\" IS NOT NULL");

            modelBuilder.Entity<MediaItem>()
                .ToTable("MediaItem");

            modelBuilder.Entity<MediaItem>()
                .HasOne(m => m.TravelEntry)
                .WithMany(t => t.MediaItems)
                .HasForeignKey(m => m.TravelEntryId)
                .OnDelete(DeleteBehavior.Cascade);
        }
    }
}
