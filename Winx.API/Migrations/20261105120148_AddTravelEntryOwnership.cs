using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Winx.API.Migrations
{
    /// <inheritdoc />
    public partial class AddTravelEntryOwnership : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "OwnerUid",
                table: "TravelEntries",
                type: "TEXT",
                nullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "OwnerUid",
                table: "TravelEntries");
        }
    }
}
