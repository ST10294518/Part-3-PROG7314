using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable
namespace Winx.API.Migrations
{
    public partial class AddOfflineOperationId : Migration
    {
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "OfflineCreateReceipts",
                columns: table => new
                {
                    OwnerUid = table.Column<string>(type: "TEXT", nullable: false),
                    OperationId = table.Column<string>(type: "TEXT", maxLength: 36, nullable: false),
                    EntryId = table.Column<int>(type: "INTEGER", nullable: false)
                },
                constraints: table => table.PrimaryKey("PK_OfflineCreateReceipts", x => new { x.OwnerUid, x.OperationId }));
            migrationBuilder.AddColumn<string>(name: "ClientOperationId", table: "TravelEntries",
                type: "TEXT", maxLength: 36, nullable: true);
            migrationBuilder.CreateIndex(name: "IX_TravelEntries_OwnerUid_ClientOperationId",
                table: "TravelEntries", columns: new[] { "OwnerUid", "ClientOperationId" },
                unique: true, filter: "\"ClientOperationId\" IS NOT NULL");
        }
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(name: "OfflineCreateReceipts");
            migrationBuilder.DropIndex(name: "IX_TravelEntries_OwnerUid_ClientOperationId", table: "TravelEntries");
            migrationBuilder.DropColumn(name: "ClientOperationId", table: "TravelEntries");
        }
    }
}
