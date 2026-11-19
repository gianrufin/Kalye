# Kalye — Offline-First Overland & Ride Tracker

**Kalye** is an Android ride tracker built for overlanders, long-drive enthusiasts, and off-road adventurers to map routes, log fuel & ferry expenses, profile elevation, and export tracks without relying on cell reception.

---

## Download APK

> **Latest Release:** [**Download Kalye APK from GitHub Releases**](https://github.com/gianrufin/Kalye/releases/latest)
>
> All versioned `.apk` builds and release notes are published on the [**Releases Page**](https://github.com/gianrufin/Kalye/releases).

### Installing on Android
1. Open the [**Latest Release**](https://github.com/gianrufin/Kalye/releases/latest) above on your Android device.
2. Download the `.apk` asset attached under **Assets**.
3. Open the downloaded file and allow **Install from this source** if prompted.

---

## Features

- **High-Accuracy GPS & Foreground Tracking** — Records route geometry, elevation, and speed via `FusedLocationProviderClient` inside a location foreground service for uninterrupted backcountry recording.
- **Offline Wilderness Map Caching** — Download OpenTopoMap & OpenStreetMap sectors (`Z10–Z15`) locally for zero-signal navigation, with an OLED-friendly dark map filter.
- **Interactive Altitude Profiling** — Touch-scrubbing elevation curve showing climb, descent, peak altitude, and instantaneous grade `%`.
- **Fuel, Ferry & Stop-Over Cost Ledger** — Track fuel volume, price per unit, fuel efficiency (`km/L`), ferry shipping fees, car camps, tolls, and total journey spend in your preferred currency.
- **One-Handed Driving Cockpit** — Oversized thumb-zone controls and 1-tap quick waypoint logging (`Fuel`, `Ferry`, `Camp`, `Stop`).
- **GPX 1.1 & CSV Spreadsheet Exports** — Share standard `.gpx` tracks (waypoints + trackpoints) and organized `.csv` expense spreadsheets.
- **Strava-Style PNG Share Card Studio** — Generate customizable `1080×1350` PNG summary cards (including transparent overlays) with `km`/`mi` unit switching.
- **Offline-First + Cloud Sync** — Local Room & DataStore persistence backed by Google Sign-In (`CredentialManager`) and Cloud Firestore sync.
- **Home-Screen Dashboard Widget** — Jetpack Glance widget summarizing daily distance, elevation gain, ride count, and spend.

---

## Publishing APK Releases via GitHub Actions

This repository includes an automated release workflow at `.github/workflows/release-apk.yml`.

### Triggering a New Version Release
1. Either push a semantic version tag (`v1.0.1`, `v1.0.2`, ...):
   ```bash
   git tag v1.0.2
   git push origin v1.0.2
   ```
   Or trigger **Actions → Release Android APK → Run workflow** with `tag_name = auto` to automatically bump the patch version from the latest `v*` tag.
2. GitHub Actions automatically injects the release `versionName` and monotonic `versionCode` into Gradle, runs `:app:testDebugUnitTest`, builds the APK, and publishes a GitHub Release at the [**Download link**](https://github.com/gianrufin/Kalye/releases/latest) above.
3. Users can tap **Settings → Check for Updates** inside the app to scan the repository for newer versions and open the release APK download.

### Optional: Firebase & Consistent Signing Secrets
- **Firebase & Google Sign-In** (if `app/google-services.json` was excluded when pushing to GitHub):
  - `GOOGLE_SERVICES_JSON`: Paste the full contents of `app/google-services.json` (from AI Studio or Firebase Console) into **GitHub → Settings → Secrets and variables → Actions**.
- **Signing Keystore** (optional — if omitted, CI generates a temporary debug keystore automatically):
  - `RELEASE_KEYSTORE_BASE64`: Base64-encoded `.jks` upload keystore (`keyAlias`: `upload`)
  - `STORE_PASSWORD`: Keystore password
  - `KEY_PASSWORD`: `upload` key password

---

## Tech Stack
- **UI**: Kotlin, Jetpack Compose, Material Design 3, Navigation Compose (`@Serializable` routes)
- **Persistence & Sync**: Room (SQLite), DataStore Preferences, Firebase Auth (Credential Manager Google Sign-In), Cloud Firestore
- **Location & Maps**: Play Services Location (`FusedLocationProviderClient`), OkHttp Disk Tile Cache, Compose Canvas Map
- **Widget**: Jetpack Glance AppWidget (`KalyeDailyWidget`)
