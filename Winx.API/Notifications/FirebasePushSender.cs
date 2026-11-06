using FirebaseAdmin;
using FirebaseAdmin.Messaging;
using Google.Apis.Auth.OAuth2;
using System.Text.Json;
using Winx.API.Models;
namespace Winx.API.Notifications;

public enum PushSendResult { Sent, InvalidToken }
public interface IPushSender
{
    Task<PushSendResult> SendAsync(string token, PushNotification notification, CancellationToken cancellationToken);
}

public sealed class FirebasePushSender(IConfiguration configuration) : IPushSender, IDisposable
{
    private FirebaseApp? app;
    private FirebaseMessaging? messaging;
    private FirebaseMessaging Messaging
    {
        get
        {
            if (messaging != null) return messaging;
            var project = configuration["Firebase:ProjectId"]!;
            var json = configuration["Firebase:ServiceAccountJson"];
            GoogleCredential credential;
            if (!string.IsNullOrWhiteSpace(json))
            {
                // This is a server secret, never an Android config or user-uploaded credential.
                using var doc = JsonDocument.Parse(json);
                if (doc.RootElement.GetProperty("type").GetString() != "service_account" ||
                    doc.RootElement.GetProperty("project_id").GetString() != project)
                    throw new InvalidOperationException("FCM service account must belong to Firebase:ProjectId.");
                credential = GoogleCredential.FromJson(json);
            }
            else credential = GoogleCredential.GetApplicationDefault();
            app = FirebaseApp.Create(new AppOptions { ProjectId = project, Credential = credential }, "winx-" + Guid.NewGuid().ToString("N"));
            return messaging = FirebaseMessaging.GetMessaging(app);
        }
    }

    public async Task<PushSendResult> SendAsync(string token, PushNotification n, CancellationToken cancellationToken)
    {
        try
        {
            // Data-only messages run our recipient/mute/history checks in foreground
            // and background with the current Android SDK. Do not add Notification here.
            await Messaging.SendAsync(new Message {
                Token = token,
                Data = new Dictionary<string, string> {
                    ["recipient_uid"] = n.OwnerUid, ["event_id"] = n.Id,
                    ["winx_title"] = n.Title, ["winx_body"] = n.Body,
                    ["received_at"] = n.ReceivedAt.ToString(System.Globalization.CultureInfo.InvariantCulture),
                    ["kind"] = n.Kind, ["read"] = n.Read ? "true" : "false"
                },
                Android = new AndroidConfig { Priority = Priority.High, TimeToLive = TimeSpan.FromHours(24) }
            }, cancellationToken);
            return PushSendResult.Sent;
        }
        catch (FirebaseMessagingException e) when (e.MessagingErrorCode == MessagingErrorCode.Unregistered)
        {
            return PushSendResult.InvalidToken;
        }
    }
    public void Dispose() => app?.Delete();
}
