# PagePortal 📚

**Your Unified Gateway to Digital Reading & Listening**

PagePortal is a modern, material-design Android application that aggregates your digital library from multiple self-hosted services. specificially designed for power users who manage their own content, it provides a seamless, unified interface for ebooks, audiobooks, and comics.

---

## ✨ Key Features

### 🔗 Multi-Service Aggregation
Connect to multiple backend services simultaneously:
- **Storyteller**: Best-in-class audiobook streaming.
- **Audiobookshelf**: Comprehensive audiobook and podcast management.
- **Booklore**: Lightweight ebook library management.

### 🎧 Advanced Audio Player
- **ReadAloud Support**:  Synchronized text-to-speech with real-time text highlighting.
- **Smart Resume**: Auto-rewind on resume to regain context.
- **Sleep Timer**:  Drift off without losing your place.
- **Equalizer**:  Fine-tune audio for specific narrators.
- **Playback Speed**:  Adjust speed without pitch distortion (0.5x - 3.0x).

### 📖 Premium Reader Experience
- **Customizable**:  Adjust fonts, margins, line height, and themes (Light, Dark, AMOLED, Sepia).
- **Format Support**:  EPUB, PDF, CBZ/CBR (Comics).
- **Immersive Mode**:  Focus entirely on the text with distraction-free reading.

### 🔄 Seamless Sync
- **Progress Tracking**:  Keep your reading/listening position synchronized across devices.
- **Offline Mode**:  Download content usage while disconnected; syncs back when online.

---

## 🛠️ Technology Stack

PagePortal is built with modern Android development standards:

- **Kotlin**: 100% Kotlin codebase.
- **Jetpack Compose**:  Modern, declarative UI toolkit.
- **Coroutines & Flow**:  Asynchronous programming and reactive data streams.
- **Hilt**:  Dependency injection.
- **Room**:  Local database persistence.
- **Retrofit/OkHttp**:  Robust networking.
- **ExoPlayer (Media3)**:  Industry-standard media playback engine.
- **Coil**:  Image loading and caching.

---

## 🚀 Getting Started

### Prerequisites
- Android 8.0 (Oreo) or higher.
- A running instance of Storyteller, Audiobookshelf, or Booklore.

### Installation
1.  Download the latest APK from the [Releases](tags) page.
2.  Install on your Android device.
3.  Launch PagePortal.
4.  Navigate to **Settings > Services**.
5.  Add your server URL and credentials.

### Building from Source

```bash
# Clone the repository
git clone https://github.com/OwlSoda/PagePortal.git

# Open in Android Studio or build via command line
./gradlew assembleDebug
```

The APK will be located in `app/build/outputs/apk/debug/`.

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

[License Information]
