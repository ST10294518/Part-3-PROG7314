# Winx Part 3 release notes

## New since the prototype

- **Favourites** lets users save journal entries and filter My Entries to their saved items. Winx.API uses Azure Table Storage for favourites and verifies entry ownership before a change. The app can import eligible device-local favourites after signing in and connecting.
- **Surprise me** opens an eligible journal memory at random and avoids immediately repeating it when another entry is available.
- **Recently Deleted** lets users restore entries with their original IDs and attachments, permanently delete one entry, or empty the list. Permanent removal queues cloud attachment cleanup.
- **Offline Journal** caches cloud entries and lets users save text entries without connectivity. WorkManager uploads pending items after reconnection. The cache and queue are scoped to the Firebase account.
- **Biometric lock** can protect an existing signed-in session with Android biometrics. Language settings include English, isiZulu, and Afrikaans on supported screens.
- **Notification inbox** records accepted Firebase Cloud Messaging events per account. Users can mute phone alerts and manage local notification history.

## Storage and delivery

- Winx.API runs over HTTPS on Azure App Service. Firebase ID tokens identify the account for journal, media, and favourites requests.
- Journal records and attachment metadata use SQLite. Private Azure Blob Storage holds uploaded photos and videos. Azure Table Storage holds favourites.
- Media uploads support a stable operation ID for retries. The app keeps the ID while an attachment is pending and the API can return the attachment from a completed attempt.
- GitHub Actions builds Android and runs Android unit tests and API integration tests.

## Current scope

The Offline Journal queues new text entries. Media uploads and changes to existing entries require a connection. The signed dc35542 release passed physical-phone checks for foreground and background notification delivery and notification isolation across accounts.
