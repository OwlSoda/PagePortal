package com.owlsoda.pageportal.features.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.dao.HighlightDao
import com.owlsoda.pageportal.core.database.dao.ProgressDao
import com.owlsoda.pageportal.core.database.dao.ServerDao
import com.owlsoda.pageportal.core.database.entity.HighlightEntity
import com.owlsoda.pageportal.data.preferences.PreferencesRepository
import com.owlsoda.pageportal.data.repository.DownloadRepository
import com.owlsoda.pageportal.data.repository.SyncRepository
import com.owlsoda.pageportal.reader.epub.EpubBook
import com.owlsoda.pageportal.reader.epub.EpubParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.LruCache
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.owlsoda.pageportal.reader.epub.SmilData
import com.owlsoda.pageportal.reader.epub.SmilPar
import com.owlsoda.pageportal.reader.epub.SmilSynchronizer
import com.owlsoda.pageportal.reader.audio.AudioEqualizerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import java.net.URLDecoder
import com.owlsoda.pageportal.data.repository.LibraryRepository
import com.owlsoda.pageportal.core.database.entity.ProgressEntity

data class SearchResult(
    val chapterIndex: Int,
    val locator: String,
    val previewText: String
)



data class ReaderUiState(
    val book: EpubBook? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentChapterIndex: Int = 0,
    val fontSize: Int = 100, // Percent
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val fontFamily: String = "Serif",
    val lineHeight: Float = 1.5f,
    val margin: Int = 1,
    val highlights: List<HighlightEntity> = emptyList(),
    val searchResults: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val isVerticalScroll: Boolean = false,
    val textAlignment: String = "LEFT",
    val paragraphSpacing: Float = 1.0f,
    val brightness: Float = -1.0f,
    
    val gestureTapLeft: String = "PREV",
    val gestureTapCenter: String = "MENU",
    val gestureTapRight: String = "NEXT",

    val pdfFile: File? = null,
    // ReadAloud State
    val isReadAloudAvailable: Boolean = false,
    val isPlayingAudio: Boolean = false,
    val activeSmilHighlightId: String? = null, // The text ID to highlight
    val playbackSpeed: Float = 1.0f,
    val sleepTimerMinutes: Int = 0,
    val sleepTimerRemaining: Long = 0, // Seconds remaining
    val skipSilenceEnabled: Boolean = false,
    val currentEqualizerPreset: String = "Spoken Word",  // Active EQ preset
    val debugLog: String = "",
    val rewindSeconds: Int = 10,
    val forwardSeconds: Int = 30,
    val smilHighlightColor: String = "#FFF176",
    val smilUnderlineColor: String = "#FF6D00",
    val isLiveSyncEnabled: Boolean = false,
    val isZeroSyncActive: Boolean = false
)

enum class ReaderTheme(val backgroundColor: String, val textColor: String) {
    LIGHT("#FFFFFF", "#000000"),
    DARK("#121212", "#E0E0E0"),
    AMOLED("#000000", "#FFFFFF"),
    SEPIA("#F4ECD8", "#5B4636");
    
    companion object {
        fun fromString(value: String): ReaderTheme {
            return try {
                valueOf(value.uppercase())
            } catch (e: Exception) {
                 LIGHT
            }
        }
    }
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val progressDao: ProgressDao,
    private val highlightDao: HighlightDao,
    private val bookmarkDao: com.owlsoda.pageportal.core.database.dao.BookmarkDao,
    private val serverDao: ServerDao,
    private val preferencesRepository: PreferencesRepository,
    private val libraryRepository: LibraryRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    val isSyncing = syncRepository.isSyncing
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    
    init {
        observePreferences()
    }
    
    // Parser instance to serve resources
    private val parser = EpubParser()
    private var currentBookId: Long? = null
    
    // Audio Player
    private var exoPlayer: ExoPlayer? = null
    private var syncTickerJob: Job? = null
    private var appContext: android.content.Context? = null
    
    // SMIL Synchronization
    private var smilSynchronizer: SmilSynchronizer? = null
    private var currentChapterId: String? = null
    
    // Auto-rewind
    private var lastPausePosition: Long? = null
    
    // On-Device ZeroSync Aligner
    private var zeroSyncAligner: ZeroSyncAligner? = null
    private var isLiveSyncPreferenceEnabled: Boolean = false
    
    // Audio Equalizer
    private var equalizerManager: AudioEqualizerManager? = null
    
    // Cache for extracted audio files
    private var audioCacheDir: File? = null
    
    // Chapter content for Zero-Sync
    private val chapterTextElements = mutableListOf<Pair<String, String>>()
    
    // Chapter HTML cache — avoids re-parsing EPUB entries on swipe
    private val chapterHtmlCache = LruCache<Int, String>(5)
    
    fun loadBook(bookId: String, context: android.content.Context, preferReadAloud: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            // Set app context early - needed by prepareReadAloudFiles inside parseAndLoad
            appContext = context.applicationContext
            
            val id = bookId.toLongOrNull()
            if (id == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Invalid book ID")
                return@launch
            }
            currentBookId = id
            
            // Sync progress from server before loading local
            try {
                syncRepository.syncProgress(id)
            } catch (e: Exception) {
                android.util.Log.e("ReaderViewModel", "Sync failed: ${e.message}")
            }
            
            loadHighlights(id)
            
            val bookEntity = bookDao.getBookById(id)
            if (bookEntity == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Book not found")
                return@launch
            }
            
            // 1. Check if we have a direct local path (e.g. from Import)
            bookEntity.localFilePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    parseAndLoad(file, id)
                    return@launch
                }
            }
            
            // 2. Fallback to standard download location
            val baseDir = context.filesDir
            var file: File? = null
            
            // If preferReadAloud, try that first
            if (preferReadAloud) {
                val raFile = com.owlsoda.pageportal.util.DownloadUtils.getFilePath(
                    baseDir, bookEntity, com.owlsoda.pageportal.util.DownloadUtils.DownloadFormat.READALOUD
                )
                if (raFile.exists()) file = raFile
            }
            
            // Then EBOOK
            if (file == null) {
                 val ebookFile = com.owlsoda.pageportal.util.DownloadUtils.getFilePath(
                    baseDir, bookEntity, com.owlsoda.pageportal.util.DownloadUtils.DownloadFormat.EBOOK
                )
                if (ebookFile.exists()) file = ebookFile
            }
            
            // Then try ReadAloud if we didn't check it yet
            if (file == null && !preferReadAloud) {
                 val raFile = com.owlsoda.pageportal.util.DownloadUtils.getFilePath(
                    baseDir, bookEntity, com.owlsoda.pageportal.util.DownloadUtils.DownloadFormat.READALOUD
                )
                if (raFile.exists()) file = raFile
            }
            
            // Fallback: PDF
            if (file == null) {
                 val pdfFile = com.owlsoda.pageportal.util.DownloadUtils.getFilePath(
                    baseDir, 
                    bookEntity, 
                    com.owlsoda.pageportal.util.DownloadUtils.DownloadFormat.PDF
                )
                if (pdfFile.exists()) {
                    _uiState.value = _uiState.value.copy(
                         pdfFile = pdfFile,
                         isLoading = false
                    )
                     val book = bookDao.getBookById(id) ?: return@launch
                
                // Bidirectional sync
                _uiState.update { it.copy(isLoading = true) }
                syncRepository.syncProgress(id)
                
                val progress = progressDao.getProgressByBookId(id)
                     if (progress != null) {
                         _uiState.update { it.copy(currentChapterIndex = progress.currentChapter) }
                     }
                    return@launch
                }
            }
            
            if (file == null || !file.exists()) {
                 // Try legacy/fallback location if any
                 val server = bookEntity.serverId?.let { serverDao.getServerById(it) }
                 if (server != null) {
                     val serviceTypeName = server.serviceType.lowercase()
                     val fileName = "${bookEntity.title}.bin"
                     val legacyFile = File(baseDir, "downloads/$serviceTypeName/$fileName")
                     if (legacyFile.exists()) {
                         file = legacyFile
                     }
                 }
            }
            
            if (file == null || !file.exists()) {
                 _uiState.update { it.copy(
                     isLoading = false, 
                     error = "Book file not found. Please download the book first."
                 ) }
                 return@launch
            }
            
            parseAndLoad(file, id)
            
            // Auto-play if requested and available
            if (preferReadAloud) {
                if (_uiState.value.isReadAloudAvailable) {
                     delay(500) // Small buffer
                     toggleAudioPlay()
                }
            }
        }
    }

    // ... (rest of loadBook)

    private suspend fun parseAndLoad(file: File, bookId: Long) {
        val result = parser.parse(file)
        
        result.fold(
            onSuccess = { epubBook ->
                 val hasAudio = epubBook.hasMediaOverlays
                 _uiState.value = _uiState.value.copy(
                     book = epubBook,
                     isLoading = false,
                     isReadAloudAvailable = hasAudio,
                     // Force vertical scroll for ReadAloud so text flows like synced lyrics
                     isVerticalScroll = if (hasAudio) true else _uiState.value.isVerticalScroll
                 )
                 
                 // Load saved progress
                 val progress = progressDao.getProgressByBookId(bookId)
                 if (progress != null) {
                     val safeIndex = progress.currentChapter.coerceAtLeast(0)
                     _uiState.update { it.copy(currentChapterIndex = safeIndex) }
                 } else if (hasAudio) {
                     // No saved progress + ReadAloud: auto-skip to first chapter with SMIL data
                     val firstSmilChapter = epubBook.chapters.indexOfFirst { ch ->
                         epubBook.smilData[ch.id] != null
                     }
                     if (firstSmilChapter > 0) {
                         android.util.Log.d("ReaderViewModel", "Auto-skipping to first SMIL chapter: $firstSmilChapter")
                         _uiState.update { it.copy(currentChapterIndex = firstSmilChapter) }
                     }
                 }
                 
                 // Preload adjacent chapters for instant swipe
                 preloadAdjacentChapters(_uiState.value.currentChapterIndex)
                 
                 if (hasAudio) {
                     prepareReadAloudFiles(file, bookId)
                 }
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to parse EPUB: ${error.message}"
                )
            }
        )
    }

    private suspend fun prepareReadAloudFiles(epubFile: File, bookId: Long) {
        // BUG-06 FIX: Null-safe context access
        val ctx = appContext
        if (ctx == null) {
            android.util.Log.e("ReaderViewModel", "prepareReadAloudFiles: context is null!")
            _uiState.update { it.copy(error = "App context not available") }
            return
        }
        
        // We reuse the cache structure from ReadAloudPlayerViewModel
        // This ensures shared cache if user plays via different screens
        val cacheDir = File(ctx.cacheDir, "readaloud/$bookId")
        audioCacheDir = cacheDir
        
        if (!cacheDir.exists() || cacheDir.listFiles()?.isEmpty() == true) {
             withContext(Dispatchers.IO) {
                 try {
                     android.util.Log.d("ReaderViewModel", "Extracting EPUB to $cacheDir")
                     com.owlsoda.pageportal.util.DownloadUtils.unzipFile(epubFile, cacheDir)
                     android.util.Log.d("ReaderViewModel", "Extraction complete. Files: ${cacheDir.listFiles()?.size ?: 0}")
                 } catch (e: Exception) {
                     android.util.Log.e("ReaderViewModel", "Unzip failed: ${e.message}")
                     e.printStackTrace()
                 }
             }
        }
        
        // BUG-01 FIX: Initialize player if not already, with proper sequencing
        if (exoPlayer == null) {
            withContext(Dispatchers.Main) {
                initializePlayer(ctx)
            }
            // Give ExoPlayer a moment to fully initialize
            delay(100)
        }
        
        // Find and load the first chapter that has SMIL audio data
        val book = _uiState.value.book
        if (book != null) {
            // Lazy init ZeroSyncAligner
            if (zeroSyncAligner == null) {
                appContext?.let { ctx ->
                    zeroSyncAligner = ZeroSyncAligner(ctx, viewModelScope)
                }
            }
            
            val hasSmilData = book.smilData.isNotEmpty()
            val firstAudioChapterIndex = book.chapters.indexOfFirst { chapter ->
                val smil = book.smilData[chapter.id]
                smil != null && smil.parList.isNotEmpty()
            }
            
            if (firstAudioChapterIndex >= 0) {
                appendLog("First audio chapter at index $firstAudioChapterIndex (${book.chapters[firstAudioChapterIndex].id})")
                loadAudioForChapter(firstAudioChapterIndex)
            } else {
                appendLog("No chapters with SMIL data found. Live Sync availability: $isLiveSyncPreferenceEnabled")
                _uiState.update { it.copy(isReadAloudAvailable = isLiveSyncPreferenceEnabled) }
            }
        }
    }

    private fun initializePlayer(context: android.content.Context) {
        android.util.Log.d("ReaderViewModel", "initializePlayer: Creating ExoPlayer...")
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            android.util.Log.d("ReaderViewModel", "ExoPlayer created, audio session ID: $audioSessionId")
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.update { it.copy(isPlayingAudio = isPlaying) }
                    if (isPlaying) startSyncTicker() else stopSyncTicker()
                    updateZeroSyncState()
                }
                
                override fun onPlaybackStateChanged(playbackState: Int) {
                     if (playbackState == Player.STATE_ENDED) {
                         android.util.Log.d("ReaderViewModel", "Audio playback ended, auto-advancing")
                         // Auto-advance to next chapter with audio
                         val currentState = _uiState.value
                         val book = currentState.book ?: return
                         val nextIndex = currentState.currentChapterIndex + 1
                         if (nextIndex < book.chapters.size) {
                             _uiState.update { it.copy(currentChapterIndex = nextIndex) }
                             loadAudioForChapter(nextIndex, autoPlay = true)
                         } else {
                             _uiState.update { it.copy(isPlayingAudio = false) }
                             stopSyncTicker()
                         }
                     }
                     if (playbackState == Player.STATE_READY) {
                         _uiState.update { it.copy(isPlayingAudio = exoPlayer?.isPlaying == true) }
                     }
                }
            })
        }
        
        // Initialize equalizer
        exoPlayer?.let { player ->
            equalizerManager = AudioEqualizerManager(player.audioSessionId).apply {
                initialize()
                applyPreset("Spoken Word")
            }
        }
    }
    
    private fun loadAudioForChapter(chapterIndex: Int, autoPlay: Boolean = false) {
        val book = _uiState.value.book ?: run {
            appendLog("ERROR: No book loaded")
            _uiState.update { it.copy(error = "No book loaded") }
            return
        }
        val chapter = book.chapters.getOrNull(chapterIndex) ?: run {
            appendLog("ERROR: Invalid chapter index $chapterIndex")
            _uiState.update { it.copy(error = "Invalid chapter") }
            return
        }
        
        appendLog("Loading audio for chapter $chapterIndex (id=${chapter.id}, href=${chapter.href})")
        appendLog("Available SMIL keys: ${book.smilData.keys.joinToString()}")
        
        // BUG-02 FIX: Try multiple lookup strategies
        var smilData = book.smilData[chapter.id]
        
        if (smilData == null) {
            appendLog("SMIL lookup by chapter.id failed, trying href basename...")
            // Fallback: try matching by href basename (without path)
            val hrefBasename = chapter.href.substringAfterLast("/").substringBefore(".")
            smilData = book.smilData.values.firstOrNull { data ->
                data.parList.any { par ->
                    par.textSrc.contains(hrefBasename, ignoreCase = true)
                }
            }
            if (smilData != null) {
                appendLog("Found SMIL via href matching: $hrefBasename")
            }
        }
        
        if (smilData == null) {
            // Try direct key lookup with common variations
            val altKeys = listOf(
                chapter.id,
                chapter.href,
                chapter.href.substringAfterLast("/"),
                "chapter${chapterIndex + 1}",
                "ch${chapterIndex + 1}"
            )
            for (key in altKeys) {
                smilData = book.smilData[key]
                if (smilData != null) {
                    appendLog("Found SMIL via alternate key: $key")
                    break
                }
            }
        }
        
        if (smilData == null) {
            // Not an error - many chapters (cover, TOC, etc.) legitimately have no audio
            appendLog("No SMIL for chapter '${chapter.id}' - this chapter has no audio overlay (normal for front/back matter)")
            currentChapterId = chapter.id
            updateZeroSyncState()
            return
        }
        
        appendLog("Found SMIL with ${smilData.parList.size} entries")
        
        // Initialize SmilSynchronizer for this chapter
        currentChapterId = chapter.id
        smilSynchronizer = SmilSynchronizer(
            smilData = smilData,
            onHighlight = { fragmentId, chapterHref ->
                // Update UI state with highlighted fragment
                if (fragmentId != _uiState.value.activeSmilHighlightId) {
                    _uiState.update { it.copy(activeSmilHighlightId = fragmentId) }
                    appendLog("Highlight: $fragmentId in $chapterHref")
                }
            },
            onChapterChange = { newChapterHref, fragmentId ->
                // Navigate to the chapter matching this href
                appendLog("Chapter change needed: $newChapterHref#$fragmentId")
                val targetIndex = book.chapters.indexOfFirst { ch ->
                    ch.href.endsWith(newChapterHref, ignoreCase = true) ||
                    newChapterHref.endsWith(ch.href, ignoreCase = true) ||
                    ch.href.substringAfterLast("/") == newChapterHref.substringAfterLast("/")
                }
                if (targetIndex >= 0 && targetIndex != chapterIndex) {
                    appendLog("Navigating to chapter $targetIndex for $newChapterHref")
                    viewModelScope.launch {
                        _uiState.update { it.copy(currentChapterIndex = targetIndex) }
                        loadAudioForChapter(targetIndex, autoPlay = true)
                    }
                }
            }
        )
        
        updateZeroSyncState()
        
        val firstPar = smilData.parList.firstOrNull() ?: run {
            // Not an error - some chapters have SMIL entries but no audio pars
            appendLog("SMIL for '${chapter.id}' has no par entries (normal for some front matter)")
            return
        }
        
        // BUG-03 FIX: audioPath from SmilParser is already resolved relative to SMIL dir
        // It should be a path like "OEBPS/audio/ch01.mp3" - use it directly against cache root
        val audioPath = firstPar.audioSrc
        appendLog("Audio path from SMIL: $audioPath")
        
        // Try multiple resolutions
        val candidatePaths = listOf(
            File(audioCacheDir, audioPath),
            File(audioCacheDir, audioPath.substringAfterLast("/")),  // Just filename
            // If path has OEBPS prefix but cache doesn't, try without
            File(audioCacheDir, audioPath.removePrefix("OEBPS/").removePrefix("oebps/")),
            // Search recursively as last resort
        )
        
        var audioFile: File? = null
        for (candidate in candidatePaths) {
            val canonicalFile = try { candidate.canonicalFile } catch (e: Exception) { candidate }
            appendLog("Trying: ${canonicalFile.path} - Exists: ${canonicalFile.exists()}")
            if (canonicalFile.exists()) {
                audioFile = canonicalFile
                break
            }
        }
        
        // Fallback: recursive search for audio file
        if (audioFile == null) {
            audioCacheDir?.let { dir ->
                appendLog("Falling back to recursive audio search in ${dir.path}...")
                val audioFilename = audioPath.substringAfterLast("/")
                audioFile = dir.walkTopDown()
                    .filter { it.isFile && it.name.equals(audioFilename, ignoreCase = true) }
                    .firstOrNull()
                if (audioFile != null) {
                    appendLog("Found via recursive search: ${audioFile?.path}")
                }
            }
        }
        
        val finalAudioFile = audioFile
        if (finalAudioFile != null && finalAudioFile.exists()) {
            appendLog("SUCCESS: Loading audio from ${finalAudioFile.path}")
            val mediaItem = MediaItem.fromUri(finalAudioFile.path)
            exoPlayer?.let { player ->
                player.setMediaItem(mediaItem)
                player.prepare()
                if (autoPlay) player.play()
            } ?: run {
                appendLog("ERROR: ExoPlayer is null!")
                _uiState.update { it.copy(error = "Audio player not initialized") }
            }
        } else {
            val errorMsg = "Audio file not found: $audioPath"
            appendLog("ERROR: $errorMsg")
            appendLog("Cache dir contents: ${audioCacheDir?.listFiles()?.joinToString { it.name } ?: "N/A"}")
            _uiState.update { it.copy(error = errorMsg) }
        }
    }
    
    private fun appendLog(msg: String) {
        _uiState.update { it.copy(debugLog = it.debugLog + "\n" + msg) }
    }
    
    private fun startSyncTicker() {
        syncTickerJob?.cancel()
        syncTickerJob = viewModelScope.launch {
             while (isActive) {
                 exoPlayer?.let { player ->
                     if (player.isPlaying) {
                         // Pass ms directly - SmilSynchronizer converts to seconds internally
                         smilSynchronizer?.updatePlaybackPosition(player.currentPosition)
                     }
                 }
                 delay(100) // 10Hz update
             }
        }
    }
    
    private fun stopSyncTicker() {
        syncTickerJob?.cancel()
    }
    
    fun toggleAudioPlay() {
        android.util.Log.d("ReaderViewModel", "toggleAudioPlay called")
        
        // Lazy initialization if player is missing
        if (exoPlayer == null) {
             android.util.Log.d("ReaderViewModel", "exoPlayer is null, attempting lazy initialization...")
             appContext?.let { ctx ->
                 initializePlayer(ctx)
                 // Try to load audio for current chapter
                 loadAudioForChapter(_uiState.value.currentChapterIndex, autoPlay = true)
             } ?: run {
                 android.util.Log.e("ReaderViewModel", "Cannot initialize player: context is null")
             }
             return
        }

        exoPlayer?.let { player ->
            android.util.Log.d("ReaderViewModel", "ExoPlayer exists, isPlaying = ${player.isPlaying}")
            if (player.isPlaying) {
                // Pause and remember position
                player.pause()
                stopSyncTicker()
                lastPausePosition = player.currentPosition
                android.util.Log.d("ReaderViewModel", "Paused at position $lastPausePosition")
                
                // Push progress on pause
                currentBookId?.let { id ->
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                           // Update local DB first (handled by syncTicker mostly, but ensure latest)
                           progressDao.updatePosition(
                               bookId = id,
                               position = player.currentPosition,
                               chapter = _uiState.value.currentChapterIndex,
                               percent = calculatePercent(), // Helper needed or use existing logic
                               timestamp = System.currentTimeMillis()
                           )
                           syncRepository.pushProgress(id)
                        } catch(e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } else {
                // Check if we have media
                if (player.duration == androidx.media3.common.C.TIME_UNSET || player.duration <= 0) {
                     android.util.Log.d("ReaderViewModel", "Player has no media, loading info for chapter ${_uiState.value.currentChapterIndex}")
                     loadAudioForChapter(_uiState.value.currentChapterIndex, autoPlay = true)
                     return@let
                }

                // Auto-rewind 5 seconds on resume
                lastPausePosition?.let { pos ->
                    val rewindPosition = (pos - 5000).coerceAtLeast(0)
                    player.seekTo(rewindPosition)
                    lastPausePosition = null
                    android.util.Log.d("ReaderViewModel", "Rewound to position $rewindPosition")
                }
                android.util.Log.d("ReaderViewModel", "Calling player.play()")
                player.play()
                startSyncTicker()
            }
            _uiState.update { it.copy(isPlayingAudio = player.isPlaying) }
            updateZeroSyncState()
        }
    }
    
    fun rewindAudio(seconds: Int = 10) {
        exoPlayer?.let {
            val newPosition = (it.currentPosition - seconds * 1000).coerceAtLeast(0)
            it.seekTo(newPosition)
        }
    }
    
    fun forwardAudio(seconds: Int = 30) {
        exoPlayer?.let {
            val newPosition = (it.currentPosition + seconds * 1000).coerceAtMost(it.duration)
            it.seekTo(newPosition)
        }
    }
    
    fun toggleSkipSilence() {
        exoPlayer?.let {
            it.skipSilenceEnabled = !it.skipSilenceEnabled
            _uiState.update { state -> state.copy(skipSilenceEnabled = it.skipSilenceEnabled) }
        }
    }

    private var sleepTimerJob: Job? = null

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesRepository.readerFontFamily.collect { family ->
                _uiState.update { it.copy(fontFamily = family) }
            }
        }
        
        viewModelScope.launch {
            preferencesRepository.readerLiveSyncEnabled.collect { enabled ->
                isLiveSyncPreferenceEnabled = enabled
                _uiState.update { it.copy(isLiveSyncEnabled = enabled) }
                updateZeroSyncState()
            }
        }

        viewModelScope.launch {
            // Part 1: Visual Settings
            launch {
                kotlinx.coroutines.flow.combine(
                    preferencesRepository.readerFontSize,
                    preferencesRepository.readerTheme,
                    preferencesRepository.readerFontFamily,
                    preferencesRepository.readerLineHeight,
                    preferencesRepository.readerMargin
                ) { size, theme, family, lineHeight, margin ->
                    ReaderPreferencesPart1(size, theme, family, lineHeight, margin)
                }.collect { p1 ->
                    _uiState.update { it.copy(
                        fontSize = (p1.fontSize * 100).toInt(),
                        theme = ReaderTheme.fromString(p1.theme),
                        fontFamily = p1.fontFamily,
                        lineHeight = p1.lineHeight,
                        margin = p1.margin
                    ) }
                }
            }
            
            // Part 2: Layout & Brightness
            launch {
                kotlinx.coroutines.flow.combine(
                    preferencesRepository.readerVerticalScroll,
                    preferencesRepository.readerTextAlignment,
                    preferencesRepository.readerParagraphSpacing,
                    preferencesRepository.readerBrightness,
                    preferencesRepository.readerSmilHighlightColor,
                    preferencesRepository.readerSmilUnderlineColor
                ) { values: Array<*> ->
                    ReaderPreferencesPart2(
                        isVerticalScroll = values[0] as Boolean,
                        textAlignment = values[1] as String,
                        paragraphSpacing = values[2] as Float,
                        brightness = values[3] as Float,
                        smilHighlightColor = values[4] as String,
                        smilUnderlineColor = values[5] as String
                    )
                }.collect { p2 ->
                    _uiState.update { it.copy(
                        isVerticalScroll = p2.isVerticalScroll,
                        textAlignment = p2.textAlignment,
                        paragraphSpacing = p2.paragraphSpacing,
                        brightness = p2.brightness,
                        smilHighlightColor = p2.smilHighlightColor,
                        smilUnderlineColor = p2.smilUnderlineColor
                    ) }
                }
            }
            
            // Part 3: Audio Settings (Speed & Sleep Timer)
            launch {
                kotlinx.coroutines.flow.combine(
                     preferencesRepository.playbackSpeed,
                     preferencesRepository.sleepTimerMinutes
                ) { speed, minutes -> 
                     Pair(speed, minutes)
                }.collect { (speed, minutes) ->
                     _uiState.update { it.copy(playbackSpeed = speed) }
                     
                     // Apply speed to player
                     exoPlayer?.setPlaybackSpeed(speed)
                     
                     // Handle Sleep Timer
                     handleSleepTimer(minutes)
                }
            }
            
            // Part 4: Audio Skip Settings
            launch {
                kotlinx.coroutines.flow.combine(
                    preferencesRepository.rewindSeconds,
                    preferencesRepository.forwardSeconds
                ) { rewind, forward -> 
                     Pair(rewind, forward)
                }.collect { (rewind, forward) ->
                     _uiState.update { it.copy(
                         rewindSeconds = rewind, 
                         forwardSeconds = forward 
                     ) }
                }
            }

            // Part 5: Gestures
            launch {
                kotlinx.coroutines.flow.combine(
                    preferencesRepository.gestureTapLeft,
                    preferencesRepository.gestureTapCenter,
                    preferencesRepository.gestureTapRight
                ) { left, center, right ->
                    Triple(left, center, right)
                }.collect { (left, center, right) ->
                    _uiState.update { it.copy(
                        gestureTapLeft = left,
                        gestureTapCenter = center,
                        gestureTapRight = right
                    ) }
                }
            }
        }
    }
    
    private fun handleSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        
        if (minutes > 0) {
            sleepTimerJob = viewModelScope.launch {
                 val durationMillis = minutes * 60 * 1000L
                 delay(durationMillis)
                 
                 // Timer finished
                 if (_uiState.value.isPlayingAudio) {
                     toggleAudioPlay() // Pause
                 }
                 
                 // Reset timer in prefs
                 preferencesRepository.setSleepTimerMinutes(0)
            }
        }
    }
    
    data class ReaderPreferencesPart1(
        val fontSize: Float,
        val theme: String,
        val fontFamily: String,
        val lineHeight: Float,
        val margin: Int
    )
    
    data class ReaderPreferencesPart2(
        val isVerticalScroll: Boolean,
        val textAlignment: String,
        val paragraphSpacing: Float,
        val brightness: Float,
        val smilHighlightColor: String,
        val smilUnderlineColor: String
    )

    private fun loadHighlights(bookId: Long) {
        viewModelScope.launch {
            highlightDao.getHighlightsForBook(bookId).collect { highlights ->
                _uiState.value = _uiState.value.copy(highlights = highlights)
            }
        }
    }

    fun addHighlight(chapterIndex: Int, cfi: String, text: String, color: String = "#FFFF00") {
        val bookId = currentBookId ?: return
        viewModelScope.launch {
            val highlight = HighlightEntity(
                bookId = bookId,
                chapterIndex = chapterIndex,
                cfi = cfi,
                selectedText = text,
                color = color
            )
            highlightDao.insertHighlight(highlight)
        }
    }
    
    fun deleteHighlight(highlight: HighlightEntity) {
        viewModelScope.launch {
            highlightDao.deleteHighlight(highlight)
        }
    }

    fun searchBook(query: String) {
        if (query.length < 3) return
        
        _uiState.value = _uiState.value.copy(isSearching = true)
        
        viewModelScope.launch(Dispatchers.IO) {
            val book = _uiState.value.book
            if (book == null) {
                _uiState.value = _uiState.value.copy(isSearching = false)
                return@launch
            }

            val results = mutableListOf<SearchResult>()
            
            book.chapters.forEachIndexed { index, chapter ->
                // Basic text search implementation
                // In production, this should be optimized and run on chunks

                val inputStream = parser.getInputStream(book.basePath + chapter.href) ?: return@forEachIndexed
                try {
                    val content = inputStream.bufferedReader().use { it.readText() }
                    // Strip HTML tags for searching (naive regex)
                    val textContent = content.replace(Regex("<[^>]*>"), " ")
                    
                    var startIndex = 0
                    while (true) {
                        val foundIndex = textContent.indexOf(query, startIndex, ignoreCase = true)
                        if (foundIndex == -1) break
                        
                        val startPreview = (foundIndex - 20).coerceAtLeast(0)
                        val endPreview = (foundIndex + query.length + 20).coerceAtMost(textContent.length)
                        val preview = "..." + textContent.substring(startPreview, endPreview).replace("\n", " ") + "..."
                        
                        // We need a way to link back to this position. 
                        // Without mapped CFI, we can only link to the chapter.
                        results.add(SearchResult(index, "chapter_$index", preview))
                        
                        startIndex = foundIndex + query.length
                        if (results.size > 50) break // Limit results
                    }
                } catch (e: Exception) {
                    // Skip chapter on error
                }
            }
            
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isSearching = false, searchResults = results)
            }
        }
    }
    
    fun clearSearch() {
        _uiState.value = _uiState.value.copy(isSearching = false, searchResults = emptyList())
    }

    /**
     * Called by WebViewClient to intercept requests.
     * Path should be relative to OPF base path or root?
     * Our parser stores manifest hrefs relative to OPF.
     * But WebView might request absolute paths.
     * Strategy: We serve everything under http://localhost/
     */
    fun getResource(url: String): InputStream? {
        val book = _uiState.value.book ?: return null
        
        // Remove localhost base
        var path = url.removePrefix("http://localhost/")
        try {
            path = URLDecoder.decode(path, "UTF-8")
        } catch (e: Exception) {
            // ignore
        }
        
        // If path matches a chapter href, we get that.
        // But internal resources (css, images) in HTML might be relative.
        // e.g. ../Styles/style.css
        // We need to resolve this against the current chapter's path if possible?
        // Actually, if we use loadDataWithBaseUrl("http://localhost/OEBPS/"), 
        // then "Styles/style.css" becomes "http://localhost/OEBPS/Styles/style.css".
        // The parser has raw zip paths.
        
        // Simple approach: Try to find the exact path in the zip.
        return parser.getInputStream(path) ?: parser.getInputStream(book.basePath + path)
    }
    
    fun getChapterHtml(chapterIndex: Int): String? {
        // Check cache first
        chapterHtmlCache.get(chapterIndex)?.let { return it }
        
        val book = _uiState.value.book ?: return null
        val chapter = book.chapters.getOrNull(chapterIndex) ?: return null
        
        return try {
            val stream = parser.getInputStream(chapter.href)
            val html = stream?.bufferedReader()?.use { it.readText() }
            // Cache the result
            if (html != null) {
                chapterHtmlCache.put(chapterIndex, html)
            }
            html
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Preloads HTML for chapters adjacent to [currentIndex] in the background.
     * This eliminates the parse delay when swiping to the next/previous chapter.
     */
    private fun preloadAdjacentChapters(currentIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val totalChapters = _uiState.value.book?.chapters?.size ?: return@launch
            listOf(currentIndex - 1, currentIndex + 1)
                .filter { it in 0 until totalChapters }
                .forEach { idx ->
                    if (chapterHtmlCache.get(idx) == null) {
                        try {
                            val chapter = _uiState.value.book?.chapters?.getOrNull(idx)
                            if (chapter != null) {
                                val stream = parser.getInputStream(chapter.href)
                                val html = stream?.bufferedReader()?.use { it.readText() }
                                if (html != null) {
                                    chapterHtmlCache.put(idx, html)
                                    android.util.Log.d("ReaderViewModel", "Preloaded chapter $idx (${html.length} chars)")
                                }
                            }
                        } catch (e: Exception) {
                            // Non-critical — the chapter will be loaded on demand
                        }
                    }
                }
        }
    }
    
    fun nextChapter() {
        val currentState = _uiState.value
        val book = currentState.book ?: return
        if (currentState.currentChapterIndex < book.chapters.size - 1) {
            val nextIndex = currentState.currentChapterIndex + 1
            _uiState.value = currentState.copy(currentChapterIndex = nextIndex)
            preloadAdjacentChapters(nextIndex)
        }
    }
    
    fun previousChapter() {
        val currentState = _uiState.value
        val book = currentState.book ?: return
        if (currentState.currentChapterIndex > 0) {
            val prevIndex = currentState.currentChapterIndex - 1
            _uiState.value = currentState.copy(currentChapterIndex = prevIndex)
            preloadAdjacentChapters(prevIndex)
        }
    }
    
    fun setFontSize(percent: Int) {
        viewModelScope.launch {
            preferencesRepository.setReaderFontSize(percent / 100f)
        }
    }
    
    fun setTheme(theme: ReaderTheme) {
        viewModelScope.launch {
            preferencesRepository.setReaderTheme(theme.name)
        }
    }
    
    fun setFontFamily(family: String) {
        viewModelScope.launch {
            preferencesRepository.setReaderFontFamily(family)
        }
    }
    
    fun setLineHeight(height: Float) {
        viewModelScope.launch {
            preferencesRepository.setReaderLineHeight(height)
        }
    }
    
    fun setMargin(margin: Int) {
        viewModelScope.launch {
            preferencesRepository.setReaderMargin(margin)
        }
    }

    fun setScrollMode(isVertical: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setReaderVerticalScroll(isVertical)
        }
    }
    
    fun setTextAlignment(alignment: String) {
        viewModelScope.launch {
            preferencesRepository.setReaderTextAlignment(alignment)
        }
    }
    
    fun setParagraphSpacing(spacing: Float) {
        viewModelScope.launch {
            preferencesRepository.setReaderParagraphSpacing(spacing)
        }
    }
    
    fun setBrightness(brightness: Float) {
        viewModelScope.launch {
            preferencesRepository.setReaderBrightness(brightness)
        }
    }

    fun setSmilHighlightColor(color: String) {
        viewModelScope.launch {
            preferencesRepository.setReaderSmilHighlightColor(color)
        }
    }

    fun setSmilUnderlineColor(color: String) {
        viewModelScope.launch {
            preferencesRepository.setReaderSmilUnderlineColor(color)
        }
    }
    
    fun setLiveSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setReaderLiveSyncEnabled(enabled)
        }
    }

    private fun updateZeroSyncState() {
        val uiState = _uiState.value
        val hasManualSmil = uiState.book?.smilData?.get(currentChapterId)?.parList?.isNotEmpty() ?: false
        val shouldBeActive = isLiveSyncPreferenceEnabled && uiState.isPlayingAudio && !hasManualSmil
        
        android.util.Log.d("ReaderViewModel", "updateZeroSyncState: enabled=$isLiveSyncPreferenceEnabled, playing=${uiState.isPlayingAudio}, hasSmil=$hasManualSmil -> active=$shouldBeActive")
        
        if (shouldBeActive) {
            zeroSyncAligner?.start { elementId ->
                _uiState.update { it.copy(activeSmilHighlightId = elementId) }
            }
            _uiState.update { it.copy(isZeroSyncActive = true) }
        } else {
            zeroSyncAligner?.stop()
            _uiState.update { it.copy(isZeroSyncActive = false) }
        }
    }

    private var syncJob: kotlinx.coroutines.Job? = null
    private var progressUpdateJob: Job? = null

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            preferencesRepository.setPlaybackSpeed(speed)
        }
    }
    
    fun setSleepTimer(minutes: Int) {
        viewModelScope.launch {
            preferencesRepository.setSleepTimerMinutes(minutes)
        }
    }

    fun setRewindSeconds(seconds: Int) {
        viewModelScope.launch {
            preferencesRepository.setRewindSeconds(seconds)
        }
    }

    fun setForwardSeconds(seconds: Int) {
        viewModelScope.launch {
            preferencesRepository.setForwardSeconds(seconds)
        }
    }
    
    fun onProgressChanged(chapterIndex: Int, progressInChapter: Float) {
        val bookId = currentBookId ?: return
        val book = _uiState.value.book ?: return
        
        viewModelScope.launch {
            // Calculate total percentage
            val totalChapters = book.chapters.size
            if (totalChapters == 0) return@launch
            
            val clippedProgress = progressInChapter.coerceIn(0f, 1f)
            val totalProgress = ((chapterIndex + clippedProgress) / totalChapters) * 100f
            
            progressDao.updatePosition(
                bookId = bookId,
                position = 0, 
                chapter = chapterIndex,
                percent = totalProgress
            )
            
            // Debounce network sync
            syncJob?.cancel()
            syncJob = viewModelScope.launch {
                kotlinx.coroutines.delay(5000) // 5 seconds debounce
                libraryRepository.syncProgress(bookId)
            }
        }
    }

    
    fun setEqualizerPreset(presetName: String) {
        equalizerManager?.applyPreset(presetName)
        _uiState.update { it.copy(currentEqualizerPreset = presetName) }
    }
    
    // Bookmarks
    fun addBookmark(note: String? = null) {
        viewModelScope.launch {
            val bookId = currentBookId ?: return@launch
            val book = _uiState.value.book ?: return@launch
            val chapterIndex = _uiState.value.currentChapterIndex
            val chapter = book.chapters.getOrNull(chapterIndex) ?: return@launch
            val audioPosition = exoPlayer?.currentPosition ?: 0L
            val fragmentId = _uiState.value.activeSmilHighlightId ?: ""
            
            val bookmark = com.owlsoda.pageportal.core.database.entity.BookmarkEntity(
                bookId = bookId,
                chapterId = chapter.id,
                fragmentId = fragmentId,
                audioPosition = audioPosition,
                note = note
            )
            
            bookmarkDao.insertBookmark(bookmark)
        }
    }
    
    fun jumpToBookmark(bookmark: com.owlsoda.pageportal.core.database.entity.BookmarkEntity) {
        viewModelScope.launch {
            val book = _uiState.value.book ?: return@launch
            
            // Find chapter index by ID
            val chapterIndex = book.chapters.indexOfFirst { it.id == bookmark.chapterId }
            if (chapterIndex == -1) return@launch
            
            // Navigate to chapter if different
            if (chapterIndex != _uiState.value.currentChapterIndex) {
                _uiState.update { it.copy(currentChapterIndex = chapterIndex) }
                loadAudioForChapter(chapterIndex)
            }
            
            // Seek to audio position
            exoPlayer?.seekTo(bookmark.audioPosition)
            
            // Auto-play
            exoPlayer?.play()
        }
    }

    fun jumpToChapter(index: Int) {
        viewModelScope.launch {
            val book = _uiState.value.book ?: return@launch
            if (index < 0 || index >= book.chapters.size) return@launch
            if (index == _uiState.value.currentChapterIndex) return@launch
            
            _uiState.update { it.copy(currentChapterIndex = index) }
            
            // Only reload audio if currently playing
            if (_uiState.value.isPlayingAudio) {
                loadAudioForChapter(index, autoPlay = true)
            } else {
                loadAudioForChapter(index, autoPlay = false)
            }
        }
    }
    
    fun getBookmarks(bookId: Long) = bookmarkDao.getBookmarksForBook(bookId)
    
    fun deleteBookmark(bookmark: com.owlsoda.pageportal.core.database.entity.BookmarkEntity) {
        viewModelScope.launch {
            bookmarkDao.deleteBookmark(bookmark)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Guaranteed final progress sync — runs outside viewModelScope which is being cancelled.
        // Uses NonCancellable to ensure the coroutine completes even during teardown.
        syncJob?.cancel()
        currentBookId?.let { id ->
            @Suppress("GlobalCoroutineUsage")
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                try {
                    val progress = progressDao.getProgressByBookId(id)
                    if (progress != null && (progress.syncedAt == null || progress.lastUpdated > progress.syncedAt!!)) {
                        android.util.Log.d("ReaderViewModel", "onCleared: Pushing final progress for book $id")
                        syncRepository.pushProgress(id)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ReaderViewModel", "onCleared: Final sync failed for book $id: ${e.message}")
                    // Progress is already saved in Room — SyncWorker will pick it up on next run
                }
            }
        }
        parser.close()
        exoPlayer?.release()
        exoPlayer = null
    }

    private fun calculatePercent(): Float {
        // Simple percent calculation based on chapter index
        // Ideally this would be more granular (e.g., words read / total words)
        val book = _uiState.value.book ?: return 0f
        val totalChapters = book.chapters.size
        if (totalChapters == 0) return 0f
        
        val currentChapter = _uiState.value.currentChapterIndex
        val percent = (currentChapter.toFloat() / totalChapters) * 100f
        
        // If playing audio, refinement:
        val player = exoPlayer
        if (_uiState.value.isPlayingAudio && player != null) {
            val duration = player.duration
            val position = player.currentPosition
            if (duration > 0) {
                 val chapterProgress = position.toFloat() / duration
                 // Adjust percent: currentChapter + chapterProgress
                 return ((currentChapter + chapterProgress) / totalChapters) * 100f
            }
        }
        
        return percent.coerceIn(0f, 100f)
    }

}
