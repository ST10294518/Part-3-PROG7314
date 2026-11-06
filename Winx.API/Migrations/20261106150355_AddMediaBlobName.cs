using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Winx.API.Migrations
{
    /// <inheritdoc />
    public partial class AddMediaBlobName : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "BlobName",
                table: "MediaItem",
                type: "TEXT",
                nullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "BlobName",
                table: "MediaItem");
        }
    }
}
