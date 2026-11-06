# Winx 

Github Link: https://github.com/EMKNDW/prog7314-g1-2026-prog7314-2026-poe-st10294518.git 

Video Link: https://youtu.be/eLnRsHEydFQ 

**Capture places. Cherish moments. Remember every journey.**

Winx is a native Android travel and food journal app built for the **PROG7314 / OPSC7312 (Programming 3D & Open Source Coding)** Portfolio of Evidence — Part 2: App Prototype Development. It pairs a Kotlin + Jetpack Compose Android client with a self-hosted **Winx.API** REST backend (ASP.NET Core) for storing and syncing travel entries and their media.

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
- [Team](#team)

---

## Overview

Winx lets users log the places they've visited (and the meals they've had along the way), attach photos and videos, rate the experience, and browse everything back through a dashboard, calendar, and library view. All data is persisted through a custom REST API rather than a third-party BaaS, satisfying the POE requirement for a self-hosted backend.

## Features

-  **Authentication** — Email/password login & registration plus Google Sign-In (via Firebase Auth and Credential Manager)
-  **Dashboard** — At-a-glance view of recent entries with quick actions
-  **Entry management** — Create, view, edit, and delete travel entries (title, location, country, date, rating, notes)
-  **Media attachments** — Attach and preview photos/videos per entry (Coil for images, Media3 ExoPlayer for video)
-  **Calendar view** — Browse entries by date
-  **Library** — Central hub linking to entries, calendar, and settings
-  **Country picker** — Attach a country to each entry
-  **Settings suite** — Profile, Language, Notifications, Security, Recently Deleted, About Us
-  **Multi-language support** — In-app English / isiZulu translation toggle
-  **REST-backed persistence** — All entries and media are stored via the Winx.API backend (SQLite + Entity Framework Core)

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
- Swagger / OpenAPI (Swashbuckle) for API documentation

## Project Structure

```
PROG7314-Part-2-Repo/
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

To deploy, publish `Winx.API.csproj` in `Release` for `linux-x64` as a framework-dependent build. ZIP the contents of the publish folder and upload the ZIP with `az webapp deploy --type zip`. Azure initialises the SQLite database separately from the deployment package.

### Deployment verification

- An unauthenticated HTTPS request to `/api/Entries` returned `401`.
- Account A created the test entry **Azure restart test** (`201`) and read it back (`200`).
- After restarting App Service, the same entry was still available as entry `1` (`200`).
- The updated debug APK loaded the Azure test entry on a physical Android phone.

[Hosted Winx.API](https://winx-api-part3-d9b31478.azurewebsites.net/)

## REST API Reference

**Base route:** `api/`

| Method | Endpoint                    | Description                          |
|--------|------------------------------|---------------------------------------|
| GET    | `/api/Entries`               | Get all travel entries               |
| GET    | `/api/Entries/{id}`          | Get a single entry by ID             |
| POST   | `/api/Entries`                | Create a new entry                   |
| PUT    | `/api/Entries/{id}`          | Update an existing entry             |
| DELETE | `/api/Entries/{id}`          | Delete an entry                      |
| GET    | `/api/Media/entry/{travelEntryId}` | Get all media for a given entry |
| GET    | `/api/Media/{id}`            | Get a single media item by ID        |
| POST   | `/api/Media`                  | Add a media item (photo/video) to an entry |
| DELETE | `/api/Media/{id}`            | Delete a media item                  |

**Validation notes:**
- `Rating` must be between 1 and 5 (enforced on create and update).
- `MediaType` must be either `"Photo"` or `"Video"`.
- Media items require a valid, existing `TravelEntryId`.

## Authentication

Winx supports two sign-in paths:
- **Email/password** via the in-app Login/Register screens
- **Google Sign-In** via `GoogleAuthManager`, using Android Credential Manager + Google Identity Services backed by Firebase Auth

## Localisation

The app includes a lightweight, in-app translation layer (`WinxLanguage.kt`) that swaps UI strings between **English** and **isiZulu**, toggled from the Settings → Language screen without requiring an app restart.

## Continuous Integration

The [Android CI workflow](.github/workflows/android-ci.yml) runs on pushes and pull requests. It runs Android unit tests, builds the debug APK, and uploads the Android test report. A separate .NET 10 job runs the Winx.API integration tests for authentication and ownership.

From the repository root in Windows PowerShell, run the local checks with:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
dotnet test .\Winx.API.Tests\Winx.API.Tests.csproj --configuration Release
```

[Passing GitHub Actions run for commit `4b48b26` on `Feature/Member-1`](https://github.com/ST10294518/Part-3-PROG7314/actions/runs/42045974200)

## Known Limitations

- Media files are referenced by file name/path rather than uploaded as binary content to the API.


Group project for **PROG7314 / OPSC7312** at The Independent Institute of Education (IIE).
