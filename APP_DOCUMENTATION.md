# PagePortal Application Documentation

**Last Updated:** 2026-04-08
**Version:** 0.1.84

## 1. Overview
PagePortal is a unified e-reading and audiobook listening application designed to aggregate books from multiple sources (Storyteller, Audiobookshelf, Booklore) and local files into a single, cohesive library. It supports seamless switching between reading and listening, with offline download capabilities.

## 2. Technical Architecture

### Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material3)
- **Architecture Pattern**: MVVM (Model-View-ViewModel)
- **Dependency Injection**: Hilt
- **Database**: Room (SQLite)
- **Networking**: Retrofit / OkHttp
- **Async**: Coroutines / Flow
- **Image Loading**: Coil
- **Build System**: Gradle

### Project Structure (Key Packages)
- `core/database`: Room entities, DAOs, and TypeConverters.
- `data/repository`: Data logic implementation (Library, Download, active repositories).
- `services/`: Interface adapters for external providers (Storyteller, etc.).
- `features/reader/`: Native reader logic and the new `WebReaderScreen` for embedded streaming.
- `core/database`: Room entities, DAOs, and TypeConverters.
- `workers/`: Background tasks (WorkManager) for downloads and progress syncing.

## 3. Core Features

### 3.1 Unified Library
The library aggregates books from all connected services.
- **UnifiedBook**: When the same book exists on multiple servers (e.g., Audiobookshelf and Storyteller), they can be linked into a single `UnifiedBookEntity`.
- **Sync**: `LibraryRepository.syncLibrary()` fetches metadata.
- **Progress Sync**: `SyncRepository` implements a "Furthest Progress Wins" tie-breaker to handle multi-device sync and clock drift within a 5-minute confidence window. Includes conflict logging in `sync_conflicts.log`.

### 3.2 Services & Providers
The `ServiceManager` handles authentication and API calls to providers.
- **Storyteller**: Supports Audiobooks, Ebooks, and ReadAloud (Sync). Verified Support.
- **Audiobookshelf**: (Planned/Partial)
- **Booklore**: (Planned/Partial)
- **Local**: Supports direct import of `.epub`, `.mp3`, `.m4b`. Files are copied to internal storage.

### 3.3 The Reader (Ebooks)
- **Engine**: Custom `EpubParser` interacting with a `WebView`.
- **Hybrid Web Reader**: An embedded `WebReaderScreen` (WebView-based) that connects to Storyteller for native-feeling streaming of ReadAloud content and synced audio without leaving the app. Includes auto-auth cookie injection.
- **Formatting**: Supports user-defined font size, font family, line height, and margins.
- **Improved Layout**: Robust CSS column handling via `#page-container` wrapping and precise image scaling to prevent paged-view breakage.
- **Themes**: Light, Dark, Sepia.

### 3.4 The Player (Audiobooks)
- *(Documentation Pending deep dive into `player/` package)* - Generally handles media playback services.

### 3.5 Downloads
Reference: `DownloadRepository` & `DownloadUtils`
- **Mechanism**: `WorkManager` (DownloadWorker).
- **Paths**: `context.filesDir` / [Author] / [Series] / [Title].[ext]
- **Formats**:
    - **Audio**: `.m4b` / `.mp3`
    - **Ebook**: `.epub`
    - **ReadAloud**: `.zip` or `.epub` (Sync files)
- **Resumable**: Uses HTTP `Range` headers.

## 4. Workflows

### 4.1 Local Book Import
1.  User clicks `+` in Library.
2.  `LocalBookImporter` copies file to app storage.
3.  Metadata extracted via `EpubParser` (Ebook) or `MediaMetadataRetriever` (Audio).
4.  Entry added to `BookEntity` with `ServiceType.LOCAL`.

### 4.2 Sync & Match
1.  Downloads metadata from all API services.
2.  `MatchingEngine` attempts to group books by Title/Author/ISBN.
3.  Creates/Updates `UnifiedBookEntity`.

## 5. Development

### Build
Run standard Android build:
```bash
./gradlew assembleDebug
```

### Key Files
- `AppDatabase.kt`: The Room database definition.
- `ServiceManager.kt`: The central hub for external APIs.
- `ReaderViewModel.kt`: The logic behind the e-reader view.

---
*This document is maintained by the AI Assistant. Run the `update_docs` workflow to refresh content.*
