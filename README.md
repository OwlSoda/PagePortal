# PagePortal

A multi-service ebook and audiobook reader for Android that aggregates content from multiple self-hosted servers.

## Features

- **Multi-Service Support**: Connect to Storyteller, Audiobookshelf, and Booklore servers
- **Unified Library**: View all your books from all services in one place
- **Multiple Formats**: 
  - Audiobooks (streaming and offline)
  - EPUBs (with read-aloud support)
  - Comics (CBZ/CBR)
- **Reading Progress Sync**: Keep your place synchronized across devices
- **Offline Support**: Download books for offline reading/listening

## Supported Services

- **Storyteller**: Self-hosted audiobook server
- **Audiobookshelf**: Open-source audiobook and podcast server
- **Booklore**: Self-hosted ebook library management

## Building

```bash
./gradlew assembleDebug
```

APK will be in `app/build/outputs/apk/debug/`

## Setup

1. Install the APK on your Android device
2. Launch the app
3. Enter your server URL, username, and password
4. Select the service type
5. Click login to connect

## Requirements

- Android 8.0 (API 26) or higher
- Kotlin 1.9+
- Gradle 8.0+

## Architecture

Built with Android Jetpack:
- **Compose** for UI
- **Hilt** for dependency injection
- **Room** for local database
- **Retrofit** for networking
- **Coroutines** for async operations

## License

[Your License Here]
