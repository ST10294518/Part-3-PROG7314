# Winx

<img src="app/src/main/ic_launcher-playstore.png" alt="Winx app icon" width="96">

Winx is a native Android travel and food journal for the PROG7314 / OPSC7312 Portfolio of Evidence. A Kotlin and Jetpack Compose app connects to the custom ASP.NET Core Winx.API hosted on Azure App Service.

[Prototype demonstration](https://youtu.be/eLnRsHEydFQ) · [Part 3 release notes](docs/RELEASE-NOTES.md) · [AI usage](docs/AI-USAGE.md)

[Promotional screenshots and demo evidence](evidence/promotional-screenshots)

[Final Part 3 demo](https://youtu.be/DWNfOwCqL1k)

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [1. Run the Winx.API Backend](#1-run-the-winxapi-backend)
  - [2. Run the Android App](#2-run-the-android-app)
- [Azure deployment](#azure-deployment)
- [REST API Reference](#rest-api-reference)
- [Authentication](#authentication)
- [Localisation](#localisation)
- [Continuous Integration](#continuous-integration)
- [Known Limitations](#known-limitations)

---

## Overview

Winx lets users log the places they've visited (and the meals they've had along the way), attach photos and videos, rate the experience, and browse everything back through a dashboard, calendar, and library view. Winx.API handles cloud journal operations. The Android Offline Journal keeps its cache and pending text entries in local SQLite until it can synchronise.

## Features

-  **Authentication** — Email/password login & registration plus Google Sign-In (via Firebase Auth and Credential Manager)
-  **Dashboard** — At-a-glance view of recent entries with quick actions
-  **Entry management** — Create, view, edit, and delete travel entries (title, location, country, date, rating, notes)
-  **Media attachments** — Attach and preview photos/videos per entry (Coil for images, Media3 ExoPlayer for video)
-  **Calendar view** — Browse entries by date
-  **Library** — Central hub linking to entries, calendar, and settings
-  **Country picker** — Attach a country to each entry
-  **Settings suite** — Profile, Language, Notifications, Security, Recently Deleted, About Us
-  **Multi-language support** — English, isiZulu, and Afrikaans on supported screens.
-  **Offline Journal** — Read cached entries and queue new text entries for synchronisation when connectivity returns. See the [offline journal guide](docs/OFFLINE-JOURNAL.md).
-  **Favourites and Surprise me** — Save entries across devices or rediscover a random memory.
-  **Biometric lock** — Protect a signed-in account with the phone's enrolled biometrics.
-  **REST-backed persistence** — Winx.API stores entries and attachment metadata in SQLite and uploaded files in private Azure Blob Storage.

## Tech Stack

**Android app** (`app/`)
- Kotlin, Jetpack Compose (Material 3), Navigation Compose
- Retrofit 2 + Gson for REST networking
- Coil for image loading, Media3 ExoPlayer for video playback
- Firebase Auth + Credential Manager / Google Identity for Google Sign-In
- Kotlin Coroutines
- Min SDK 24, Target/Compile SDK 36/37

**Backend API** (`Winx.API/`)
- ASP.NET Core Web API (.NET 10)
- Entity Framework Core with SQLite
- Azure Blob Storage for uploaded photos and videos
- Azure Table Storage for account-scoped favourites
- Swagger / OpenAPI (Swashbuckle) for API documentation

## Project Structure

```
Part-3-PROG7314/
├── app/                                # Android application module
│   └── src/main/java/com/winx/app/
│       ├── auth/                       # GoogleAuthManager
│       ├── data/remote/                # Retrofit services, DTOs, repositories
│       ├── navigation/                 # AppNavigation (single-activity NavHost)
│       ├── screens/                    # All Compose screens
│       └── ui/theme/                   # Colors, typography, theme
├── Winx.API/                           # ASP.NET Core backend
│   ├── Controllers/                    # EntriesController, MediaController
│   ├── Models/                         # TravelEntry, MediaItem
│   ├── Data/                           # AppDbContext
│   └── Migrations/                     # EF Core migrations
├── gradle/                             # Gradle wrapper & version catalog
├── build.gradle.kts
└── settings.gradle.kts
```

## Getting Started

### Prerequisites

- Android Studio (latest stable) with an emulator or physical device
- JDK 17
- .NET 10 SDK
- A Firebase project configured for this app (a `google-services.json` is already included in `app/`)

### 1. Run the Winx.API Backend

The Android app uses the hosted API by default. To run the backend locally for development, use the .NET 10 SDK and run these commands from the repository root in PowerShell.

```powershell
$firebaseProjectId = (Get-Content .\app\google-services.json -Raw | ConvertFrom-Json).project_info.project_id
$localDb = Join-Path $env:TEMP "winx-local.db"

dotnet run --project .\Winx.API\Winx.API.csproj --launch-profile http -- `
    "--Firebase:ProjectId=$firebaseProjectId" `
    "--ConnectionStrings:DefaultConnection=Data Source=$localDb" `
    "--Database:MigrateOnStartup=true"
```

Local Swagger is available at http://localhost:5001/swagger. If you need to test the local backend from an Android emulator, set `BASE_URL` in `RetrofitClient.kt` to `http://10.0.2.2:5001/` for that local test.

### 2. Run the Android App

1. Open the project root in Android Studio and let Gradle sync.
2. Run the `app` configuration on an emulator or connected Android device.
3. Sign in with a Winx account. The default build uses the hosted HTTPS API.

## Azure deployment

Winx.API runs on Azure App Service in South Africa North using .NET 10 on Linux. App Service requires HTTPS, and protected routes verify Firebase ID tokens before enforcing entry and media ownership.

| App Service setting | Purpose |
|---|---|
| `Firebase__ProjectId` | Matches the Firebase project configured in `app/google-services.json`. |
| `ConnectionStrings__DefaultConnection` | Points SQLite to `Data Source=/home/winx.db`. |
| `WEBSITES_ENABLE_APP_SERVICE_STORAGE` | Enables persistent App Service storage. Set to `true`. |
| `Database__MigrateOnStartup` | Applies EF Core migrations when enabled. It was set to `true` for initial deployment and set back to `false` after verification. |
| `FavouritesStorage__ServiceUri` | Azure Table service URL, such as `https://<storage-account>.table.core.windows.net/`. |
| `FavouritesStorage__TableName` | Favourites table name. Defaults to `WinxJournalFavourites`. |
| `MediaStorage__ContainerUri` | HTTPS URL of the private media container, such as `https://<storage-account>.blob.core.windows.net/media`. |

Enable the App Service system-assigned managed identity and grant it **Storage Blob Data Contributor** scoped to the media container. The deployed storage account disables anonymous blob access and shared-key authentication. Grant the same managed identity **Storage Table Data Contributor** scoped to the favourites table.

To deploy, publish `Winx.API.csproj` in `Release` for `linux-x64` as a framework-dependent build. ZIP the contents of the publish folder and upload the ZIP with `az webapp deploy --type zip`. Azure initialises the SQLite database separately from the deployment package.

### Deployment verification

- An unauthenticated HTTPS request to `/api/Entries` returned `401`.
- Account A created the test entry **Azure restart test** (`201`) and read it back (`200`).
- After restarting App Service, the same entry was still available as entry `1` (`200`).
- The updated debug APK loaded the Azure test entry on a physical Android phone.
- An authenticated PNG upload returned `201` and the download returned `200` with identical bytes. Deleting the media returned `204`, the content route then returned `404`, and the cleanup worker removed the blob.

[Hosted Winx.API](https://winx-api-part3-d9b31478.azurewebsites.net/)

## REST API Reference

**Base route:** `api/`

| Method | Endpoint                    | Description                          |
|--------|------------------------------|---------------------------------------|
| GET    | `/api/Entries`               | Get all travel entries               |
| GET    | `/api/Entries/{id}`          | Get a single entry by ID             |
| POST   | `/api/Entries`                | Create a new entry                   |
| PUT    | `/api/Entries/{id}`          | Update an existing entry             |
| DELETE | `/api/Entries/{id}`          | Move an entry to Recently Deleted. |
| GET    | `/api/Entries/deleted`       | List the signed-in user's deleted entries. |
| POST   | `/api/Entries/{id}/restore` | Restore an entry with its original ID and attachments. |
| DELETE | `/api/Entries/{id}/permanent` | Permanently delete one entry from Recently Deleted. |
| DELETE | `/api/Entries/deleted`       | Empty Recently Deleted for the signed-in user. |
| GET    | `/api/Favourites`            | List the signed-in user's active favourite entry IDs. |
| PUT    | `/api/Favourites/{id}`       | Save an owned active entry as a favourite. |
| DELETE | `/api/Favourites/{id}`       | Remove an owned active entry from favourites. |
| GET    | `/api/Media/entry/{travelEntryId}` | Get all media for a given entry |
| GET    | `/api/Media/{id}`            | Get a single media item by ID        |
| POST   | `/api/Media`                  | Create a legacy media metadata record |
| POST   | `/api/Media/entry/{travelEntryId}/upload` | Upload a file using multipart form field `file`. Returns `201` for a new attachment or `200` for a completed retry. |
| GET    | `/api/Media/{id}/content`    | Stream the uploaded file. Supports HTTP range requests. |
| DELETE | `/api/Media/{id}`            | Delete the media record and queue blob cleanup. |

**Validation notes:**
- `Rating` must be between 1 and 5 (enforced on create and update).
- `MediaType` must be either `"Photo"` or `"Video"`.
- Media items require a valid, existing `TravelEntryId`.
- Uploads require a non-empty JPEG, PNG, WebP, or MP4 file no larger than 25 MiB. The upload and content routes require a Firebase bearer token and return `404` when the entry or media belongs to another account.
- Send a stable UUID in the optional `Idempotency-Key` header when uploading. Retry the same attachment with the same key. The Android app keeps the key with the pending upload.

### Recently Deleted and blob cleanup

Open **Settings → Recently Deleted** to restore an entry, permanently delete it, or empty the list. Soft deletion hides the entry and preserves its attachments. Restoration keeps the original entry ID and makes its attachments available again.

Permanent entry deletion and emptying Recently Deleted queue the associated blobs for removal. Deleting an individual attachment also queues its blob. The cleanup worker retries failed deletions.

## Favourites and storage

Winx.API stores favourite entry IDs in Azure Table Storage and checks entry ownership in SQLite. Each favourite uses a partition derived from the signed-in Firebase UID and the entry ID as its row key. Repeating a save targets the same row. Soft-deleted entries are hidden from favourites until restored. Permanently deleted entries are excluded from the list even if a favourite row remains.

Journal entries, ownership and media metadata stay in SQLite at `/home/winx.db` in Azure. This change adds no SQLite migration and preserves the existing database and blob attachments. The Android app imports eligible device-local favourites on the first successful cloud refresh, then caches cloud favourites per account for viewing when disconnected. Changing favourites requires a connection.

## Authentication

Winx supports two sign-in paths:
- **Email/password** via the in-app Login/Register screens
- **Google Sign-In** via `GoogleAuthManager`, using Android Credential Manager + Google Identity Services backed by Firebase Auth

## Localisation

The app includes a lightweight, in-app translation layer (`WinxLanguage.kt`) that swaps UI strings between **English**, **isiZulu**, and **Afrikaans** on supported screens, toggled from the Settings → Language screen without requiring an app restart.

## Continuous Integration

The [Android CI workflow](.github/workflows/android-ci.yml) runs on pushes and pull requests. It runs Android unit tests, builds the debug APK, and uploads the Android test report. A separate .NET 10 job runs the Winx.API integration tests for authentication and ownership.

From the repository root in Windows PowerShell, run the local checks with:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
dotnet test .\Winx.API.Tests\Winx.API.Tests.csproj --configuration Release
```

[Passing GitHub Actions run for commit `4b48b26` on `Feature/Member-1`](https://github.com/ST10294518/Part-3-PROG7314/actions/runs/42045974200)

## Known Limitations

- Legacy media records created from local file paths may only open on the device that selected the file.
- Upload clients that omit the optional `Idempotency-Key` can create duplicates if they retry after losing the original response.


Group project for **PROG7314 / OPSC7312** at The Independent Institute of Education (IIE).
