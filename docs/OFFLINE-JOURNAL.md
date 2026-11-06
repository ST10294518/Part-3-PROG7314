# Offline journal: installation, scope and evidence

This patch is based on the supplied Developer ZIP after the media/Recently Deleted merge. It adds SQLite persistence and WorkManager automatic sync without removing the existing online entry/media form, favourites, random memories, biometrics, notifications or soft deletion.

## Installation

Extract the package. From the existing project root run the included INSTALL-OFFLINE.ps1 with -ProjectPath pointing at the project root. It copies the complete supplied files to their correct paths and backs up overwritten sources under TEMP. It does not change RetrofitClient.kt, any local database, Firebase settings, or any earlier migration. No manual class creation is needed.

Build on the PC:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:testDebugUnitTest --tests "com.winx.app.offline.OfflineRulesTest"
$env:Firebase__ProjectId = "winx-prog7314"
dotnet test Winx.API.Tests/Winx.API.Tests.csproj
dotnet ef migrations has-pending-model-changes --project Winx.API --startup-project Winx.API
```

Five new API tests include replay after a lost response, account scoping, permanent deletion tombstones, missing operation ID rejection and a real SQLite upgrade from the existing soft-delete migration. Five Android unit tests cover retry/account policy. Four Android instrumentation tests exercise actual SQLite persistence, account separation, retries and transactional acknowledgement.

With a connected phone, run the database tests:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.winx.app.offline.OfflineStoreTest
```

The assistant could not run Android/.NET builds in its environment: Gradle download network access was unavailable and the .NET SDK was absent. Source checks and a SQLite schema smoke check were performed. The above tests must pass on the user's PC before claiming implementation complete.

## API deployment: Person 1

This package includes the complete AddOfflineOperationId migration, designer and combined snapshot. DO NOT generate another migration for the same fields. Back up the actual hosted database and deploy the new API plus apply all pending migrations using the existing deployment workflow. Database__MigrateOnStartup can apply migrations at startup when enabled; disable it after successful verification. Preserve Firebase__ProjectId and the existing database/media configuration. Do not upload local winx.db.

For local testing against the same database configuration used by the API:

```powershell
$env:Firebase__ProjectId = "winx-prog7314"
dotnet ef database update --project Winx.API --startup-project Winx.API
```

The new POST /api/Entries/offline requires a valid UUID clientOperationId. A normal existing API deployment returns 404 for this route: the client marks the item FAILED and retains it. After deployment press Refresh / retry sync. No queued item is silently posted through the old non-idempotent route.

## How to use

My Entries > Offline Journal. Open My Entries online or use Refresh first to cache cloud entries. The offline journal displays complete text fields for cached entries and pending entries. Cached entries can also be returned by the main repository when cloud refresh fails. Last refresh time identifies stale cached data.

Create a text entry in Offline Journal and press Save on this phone. A SQLite commit occurs before showing success. The screen displays PENDING, FAILED, waiting-for-sign-in, or SYNCED/CACHED states. It observes a local flow so background sync changes appear without closing the screen. A successful upload atomically inserts the cloud cache record and removes the pending row.

Network-constrained WorkManager work persists independently of the activity and is scheduled on queue insertion, app startup, account sign-in and manual retry. A 15-minute periodic safety sweep covers process death between database insertion and scheduling. Transient failures use exponential backoff. Android may defer execution; this is not a guarantee of instant delivery. Swiping from recents is different from Settings > Force stop: after a force stop, reopen the app to resume work. User/device power restrictions can delay background work.

## Ownership and conflict policy

Every cache, queue, state and metadata query includes Firebase UID. A worker captures the queue UID, checks it before requesting a Firebase token, before each upload and after the HTTP response. FirebaseRequestSession captures the matching Firebase user; an account change cancels that request session. If an A request was already sent when the account changes, its token still identifies A, never B. A's pending item remains in A's queue until an authenticated response can be acknowledged. Sign in again as A to resume.

The API derives ownership from the verified Firebase token, not request JSON. A database unique index plus a durable per-account operation receipt prevents duplicate creates on retries, including after a lost response. Receipts survive permanent entry deletion, so replay returns 410 instead of resurrecting the entry. A repeated operation returns the current server entry without overwriting later cloud edits. There are no queued offline edits/deletes: existing cloud entries are read-only in the offline screen, and the normal edit/delete actions still require successful online responses. Pending text entries are immutable after local save. This avoids competing offline update conflicts.

Only text creation is supported offline. Offline attachments are deliberately not accepted by the offline form. Existing online media upload/manual retry remains available in the original form, with its account-scoped navigation state; media binaries are not downloaded or queued by this module. The offline feature is exposed through its own screen rather than changing the behaviour of the online media form. New offline-screen labels currently use English; existing translated screens are preserved.

## Physical phone acceptance checklist

- [ ] Build, API tests and model check pass.
- [ ] API and migration deployed (or local test server updated and app pointed at it).
- [ ] Sign in as A and refresh online. Cached entries appear.
- [ ] Turn off BOTH Wi-Fi and mobile data. Read cached title/location/country/date/rating/notes.
- [ ] Save a new text memory offline. It appears PENDING immediately.
- [ ] Close/swipe away the app, reopen while still offline. The pending entry remains.
- [ ] Reconnect. Without pressing Retry, the entry moves to SYNCED/CACHED when WorkManager runs.
- [ ] Refresh My Entries: exactly one cloud record exists. Reopen/retry multiple times: no duplicate.
- [ ] Queue a second memory as A offline. Sign out; sign in as B while online.
- [ ] B sees neither A's cache nor A's queue, and A's pending memory is not uploaded as B.
- [ ] Sign back in as A: its pending memory automatically syncs once.
- [ ] Failed requests remain visible and retry retains the original operation ID.
- [ ] Existing online media entry creation, favourites, random memory and Recently Deleted still work.

Capture screenshots of pending after restart and synced after reconnect, plus the API test summary. Record the offline/reconnect/account-switch demonstration for the POE. Do not mark phone checks complete just because compilation passes.
