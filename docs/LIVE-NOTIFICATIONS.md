# Winx live Firebase notifications

This implementation adds real backend-triggered Firebase Cloud Messaging (FCM), not just a Firebase Console test. Install the Android and API changes together, run the checks below and configure Firebase server credentials before claiming the feature is complete.

## What generates a message

| Successful action | Notification |
| --- | --- |
| Create an online memory | Memory saved |
| Upload a queued offline memory | Offline memory synced |
| Edit a memory with changed values | Memory updated |
| Move a memory to Recently Deleted | Memory moved to Recently Deleted |
| Restore a memory | Memory restored |
| Permanently remove a memory | Memory permanently deleted |
| Empty Recently Deleted | Recently Deleted cleared |
| Add a photo/video, including the legacy media endpoint | Media added |
| Remove a media item | Media removed |

Favourites, Surprise Me, typing, opening screens and unsuccessful requests do not generate backend notifications. Offline creation first shows Pending using the existing offline feature; its cloud-sync notification is generated after the server commits the upload. Replaying the same offline operation ID does not create another notification.

## How it works

1. The API writes a notification event in the same database transaction as the successful change.
2. A background dispatcher checks the outbox every three seconds while the API is running. It sends data-only, high-priority FCM messages to enabled installations belonging to that event's account.
3. The Android service checks the current Firebase UID, saves the event and posts a phone notification if permission and alert settings allow it.
4. Stable event IDs deduplicate retries and prevent a second alert when an event has already been fetched into the inbox.
5. The phone automatically registers the latest token. A random installation secret stored outside Android backups protects account rebinding and allows revocation after logout. The API stores a hash of that secret.
6. The authenticated server inbox recovers missed events. Recovery runs on opening the app, while foregrounded every 30 seconds, and through network-constrained WorkManager recovery. Background periodic work is scheduled by Android, not at an exact interval. Recovery does not manufacture a push alert.
7. Notification taps open the inbox through the normal signed-in and biometric-protected navigation. Tapping a card marks that message as read. Read and clear operations are queued per UID and sent when connectivity permits. Clearing history applies to that account across devices after refresh.

Firebase sending uses the official Firebase Admin .NET SDK. Server credentials are never embedded in the APK. Message bodies are generic confirmations rather than private entry titles or notes. This keeps push content minimal.

## Install the source changes

Run the supplied INSTALL-PUSH.ps1 from the extracted package, supplying your project root. It backs up replaced files in your Windows temporary directory. It checks the existing contents against the uploaded Developer ZIP and refuses to overwrite newer source changes. If it reports a mismatch, stop and merge that file rather than forcing an overwrite.

The installer preserves your endpoint, Google configuration, database and existing migration files. It adds the migration 20261106210000_AddPushNotifications and updates the model snapshot. Do not run `migrations add` for the same change.

## Build and test on the PC

Run from the project root in PowerShell. Keep push sending disabled during local automated tests.

```powershell
$env:Firebase__ProjectId = 'winx-prog7314'
$env:Push__Enabled = 'false'
dotnet test Winx.API.Tests/Winx.API.Tests.csproj
# Continue only if tests pass.
dotnet ef migrations has-pending-model-changes --project Winx.API --startup-project Winx.API
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:testDebugUnitTest --tests 'com.winx.app.notifications.NotificationRulesTest'
```

A phone or emulator is needed for the five Android inbox persistence/integration tests:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.winx.app.notifications.NotificationInboxTest'
```

The six new API tests use a fake push sender, not live Firebase credentials. They cover event generation and ownership, offline retry deduplication, rejected actions, token rotation/account switching/revocation, delivery retries and invalid tokens. The existing migration test now verifies the new tables. Unit tests do not prove live FCM delivery.

In the preparation environment, SQLite DDL and transaction/isolation smoke checks passed and unrelated screens/original migrations were compared byte for byte. Android compilation and the .NET tests could not be run there because Gradle downloads were blocked and .NET was unavailable. Treat PC and phone checks as outstanding until you run them.

## Configure Firebase sending

1. Open Firebase Console and select **Winx-PROG7314**. Confirm its project ID is **winx-prog7314**, matching Android's google-services.json.
2. Open **Project settings > Cloud Messaging**. Confirm the Firebase Cloud Messaging API (V1) is enabled. If needed, follow its Manage API link and enable the API for the same project in Google Cloud.
3. Open **Project settings > Service accounts > Firebase Admin SDK** and generate a private key for the server. An existing authorised server service account may also be used. It needs permission to send FCM messages in this project.
4. Store the downloaded JSON outside the project, for example in a private folder under your Windows user profile. Do not commit it, add it to Android assets, include it in the video or send it in chat. google-services.json is Android client configuration and is NOT the server private key.
5. Keep the key available only to the server deployment account. The code accepts Firebase__ServiceAccountJson as a server secret or Application Default Credentials when securely configured. In Azure, use an App Service secret setting or a Key Vault reference. No key is included in this package.

## Deploy to the existing Azure API

Back up the hosted database first using your existing database backup procedure. Do not replace the hosted database with the PC's winx.db. Keep existing connection-string, media-storage, managed-identity and other deployment settings.

The required Azure environment variables are:

| Setting | Value |
| --- | --- |
| Firebase__ProjectId | winx-prog7314 |
| Firebase__ServiceAccountJson | Complete private service account JSON, stored as a server secret |
| Push__Enabled | true |
| Database__MigrateOnStartup | true for the deployment migration, then false after verification |

The API remains a .NET 10 app. This package adds FirebaseAdmin 3.4.0. Keep the Android BASE_URL on the existing Azure HTTPS hostname.

You can deploy using the established deployment method. Alternatively, with Azure CLI installed and signed in, use the included script. Supply the **actual resource group and app resource name** from Azure Overview; the resource name need not equal the full hostname prefix. The script publishes into TEMP, sets the four values above without printing them, and deploys a ZIP. It leaves all other app settings alone.

```powershell
az login
$resourceGroup = Read-Host 'Azure resource group'
$appName = Read-Host 'Azure App Service resource name'
$keyFile = Read-Host 'Full path to the server service account JSON outside the project'
.\scripts\Deploy-PushApi.ps1 -ResourceGroup $resourceGroup -AppName $appName -ServiceAccountFile $keyFile
```

If script execution is blocked, invoke it with `powershell -NoProfile -ExecutionPolicy Bypass -File ...` and the same parameters. The script does not install Azure CLI or create a new Azure service.

In Azure **Log stream**, confirm the migration `20261106210000_AddPushNotifications` applied and the application started. Then set `Database__MigrateOnStartup=false` in Environment variables and apply the change. Do not set `Push__Enabled=false` in Azure; that disables the sender. If the App Service is stopped or suspended, its dispatcher cannot run.

For a purely local API test, set the Firebase project and server credentials in that PowerShell session, apply `dotnet ef database update --project Winx.API --startup-project Winx.API`, then run the API. Do not change Android to a local address for the final cloud demonstration.

## Register the phone

1. Build and install the updated Android app through Android Studio. Keep the app installed; avoid clearing data during the test.
2. Sign in with the test account. Allow Android notifications and turn **Stay Updated** on.
3. Keep the phone online. In Logcat filter `tag:WinxFCM` or use the command below.
4. Wait for **Device registration and inbox recovery completed.** This proves registration/inbox HTTP calls succeeded, not FCM delivery.
5. Create an entry in the app. Wait for **Memory saved**. Open Notifications and find the matching event. Tap it to mark it as read.

```powershell
$adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adbPath logcat -s 'WinxFCM:D' '*:S'
```

Azure logs should show **FCM accepted notification event ...**. This means Firebase accepted the send request. Verify the actual phone alert as separate evidence.

## Prove a real background push with one phone

A normal create action is done while the app is foregrounded. To test a backend event while the phone is in the background, first register the phone, press Home, and run this authenticated API helper from the project root:

```powershell
.\scripts\Test-LivePush.ps1 -Action Create
```

Use the email/password test account that is signed in on the phone. The helper asks for its email and password securely, creates an actual journal entry through your Azure API, and prints its ID. It never asks for a service account key or a copied FCM token. A Google-only account's Google password cannot be used with this helper. If Firebase API-key restrictions reject password REST sign-in, use a prepared authorised API client or a second signed-in phone instead; do not weaken restrictions to make the demonstration work.

With the phone still on its Home screen, watch the push arrive. Tap it: Winx should require biometric unlock when enabled, open Notifications and mark the event as read. This route demonstrates backend-triggered FCM rather than a Firebase Console test.

Use the printed entry ID for further tests:

```powershell
.\scripts\Test-LivePush.ps1 -Action Update -EntryId 123
.\scripts\Test-LivePush.ps1 -Action Delete -EntryId 123
.\scripts\Test-LivePush.ps1 -Action Restore -EntryId 123
```

Replace 123 with the actual ID. Each helper call asks for credentials again and performs the named real operation. Use disposable demonstration entries.

## Physical phone acceptance checklist

- [ ] Create a memory: one saved event, one phone alert and one inbox row.
- [ ] Change its notes: one updated event. Saving unchanged values does not generate another update event.
- [ ] Delete and restore it: appropriate events, with original restoration/media features preserved.
- [ ] Permanently delete a disposable entry: event appears; queued blob cleanup remains separate.
- [ ] Add and remove media: appropriate events, with media still functioning.
- [ ] Create while offline, restart and reconnect: existing journal sync succeeds and one sync event appears. Repeating the same operation must not add another event.
- [ ] Background the phone and use the API helper: actual FCM alert arrives. An inbox row alone is insufficient proof of push.
- [ ] Mute Stay Updated, perform an action and reopen Notifications: history is recovered, without a phone alert. Re-enable before the next alert test.
- [ ] Mark a message as read, reopen the app and confirm it remains read.
- [ ] Clear history, reconnect and refresh: cleared records stay hidden; new events still appear.
- [ ] Sign out of A, sign into B and trigger an event for A from the API helper: B must show no A alert or A history. Return to A and recover A's history.
- [ ] Deny Android notification permission: no phone alerts, but history remains recoverable.
- [ ] Tap an alert while biometric protection is enabled: the inbox cannot bypass the lock.

## Troubleshooting and limits

| Symptom | Check |
| --- | --- |
| 404 on notification endpoints | The latest API was not deployed, or the app points to an older endpoint. |
| Tables missing / HTTP 500 | Apply the supplied migration to the actual database. |
| Inbox appears but no push | Check Push__Enabled, credentials, FCM API permission, device registration, Android permission/channel, local mute and Azure FCM acceptance logs. Recovery polling alone can show inbox rows without push working. |
| Registration conflict | Check that no older/different installation is claiming the same token. Do not bypass the installation-secret check. |
| Azure sender warning | Check the server key, matching Firebase project, FCM API and sender permissions. No credentials are logged. |
| Token changed | The registration flow handles it automatically. Do not hardcode a permanent token. |
| Console background test is missing from inbox | Console notification payloads can be auto-displayed outside the service. Use backend data-only events for the integrated feature. |
| Old alerts vanish on account switch | Intentional privacy behaviour. They are not cleared simply by opening the activity anymore. |

Delivery depends on network connectivity, Android scheduling, channel settings, notification permission, Do Not Disturb, phone battery restrictions and the API being running. Android force-stop can prevent push until the app is opened again. Data-only FCM delivery is not an unconditional instant-delivery guarantee. Missed events can still be recovered from the authenticated inbox, which shows the latest 100 events.

The current implementation uses the existing legacy onMessageReceived/token APIs for compatibility with the supplied Android SDK; Firebase documents newer registration/callback APIs too. Do not change only the server message format without updating its Android callbacks as well. This package intentionally keeps the server payload and client callback compatible.

## POE demonstration wording

After you actually see the result, say: “Saving this memory created an event in our hosted API. The server sent the update through Firebase Cloud Messaging. The phone received the alert, and the same event appears in the account's notification history.”

Then background the phone and trigger the helper action, showing the alert arriving on the physical device. Show the matching API/database event and the Azure FCM acceptance log without exposing token columns, installation secrets or service account JSON. Do not count source code or fake-sender tests as proof of live delivery.

## References

- Firebase Admin server setup: https://firebase.google.com/docs/admin/setup
- Send messages using the Admin SDK: https://firebase.google.com/docs/cloud-messaging/send/admin-sdk
- Android message routing: https://firebase.google.com/docs/cloud-messaging/android/receive-messages
- Token management: https://firebase.google.com/docs/cloud-messaging/manage-tokens
- Android notification channels: https://developer.android.com/develop/ui/compose/notifications/channels
- Azure ZIP deployment CLI: https://learn.microsoft.com/en-us/cli/azure/webapp#az-webapp-deploy
