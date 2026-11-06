using Microsoft.EntityFrameworkCore.Migrations;
#nullable disable
namespace Winx.API.Migrations;
public partial class AddPushNotifications : Migration
{
    protected override void Up(MigrationBuilder migrationBuilder)
    {
            migrationBuilder.CreateTable(name: "PushDevices", columns: table => new
                {
                    InstallationId = table.Column<string>(type: "TEXT", nullable: false),
                    BindingHash = table.Column<string>(type: "TEXT", nullable: false),
                    OwnerUid = table.Column<string>(type: "TEXT", nullable: true),
                    Token = table.Column<string>(type: "TEXT", nullable: false),
                    Enabled = table.Column<bool>(type: "INTEGER", nullable: false),
                    UpdatedAt = table.Column<long>(type: "INTEGER", nullable: false)
                }, constraints: table => table.PrimaryKey("PK_PushDevices", x => x.InstallationId));
            migrationBuilder.CreateTable(name: "PushNotifications", columns: table => new
                {
                    Id = table.Column<string>(type: "TEXT", nullable: false),
                    OwnerUid = table.Column<string>(type: "TEXT", nullable: false),
                    Title = table.Column<string>(type: "TEXT", nullable: false),
                    Body = table.Column<string>(type: "TEXT", nullable: false),
                    Kind = table.Column<string>(type: "TEXT", nullable: false),
                    EntryId = table.Column<int>(type: "INTEGER", nullable: true),
                    ReceivedAt = table.Column<long>(type: "INTEGER", nullable: false),
                    Read = table.Column<bool>(type: "INTEGER", nullable: false),
                    Hidden = table.Column<bool>(type: "INTEGER", nullable: false),
                    Dispatched = table.Column<bool>(type: "INTEGER", nullable: false),
                    Attempts = table.Column<int>(type: "INTEGER", nullable: false),
                    NextAttemptAt = table.Column<long>(type: "INTEGER", nullable: false)
                }, constraints: table => table.PrimaryKey("PK_PushNotifications", x => x.Id));
            migrationBuilder.CreateIndex("IX_PushDevices_Token", "PushDevices", "Token", unique: true);
            migrationBuilder.CreateIndex("IX_PushDevices_OwnerUid", "PushDevices", "OwnerUid");
            migrationBuilder.CreateIndex("IX_PushNotifications_Dispatched_NextAttemptAt", "PushNotifications", new[] { "Dispatched", "NextAttemptAt" });
            migrationBuilder.CreateIndex("IX_PushNotifications_OwnerUid_ReceivedAt", "PushNotifications", new[] { "OwnerUid", "ReceivedAt" });
    }
    protected override void Down(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.DropTable("PushDevices");
        migrationBuilder.DropTable("PushNotifications");
    }
}
