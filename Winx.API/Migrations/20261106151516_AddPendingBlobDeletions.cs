using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Winx.API.Migrations
{
    /// <inheritdoc />
    public partial class AddPendingBlobDeletions : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "PendingBlobDeletions",
                columns: table => new
                {
                    BlobName = table.Column<string>(type: "TEXT", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_PendingBlobDeletions", x => x.BlobName);
                });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "PendingBlobDeletions");
        }
    }
}
