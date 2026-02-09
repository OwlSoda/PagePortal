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
    val isVerticalScroll: Boolean = true,
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
    val debugLog: String = ""
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
    private val serverDao: ServerDao,
    private val highlightDao: HighlightDao,
    private val bookmarkDao: com.owlsoda.pageportal.core.database.dao.BookmarkDao,
    private val preferencesRepository: PreferencesRepository,
    private val libraryRepository: com.owlsoda.pageportal.data.repository.LibraryRepository
) : ViewModel() {

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
    
    // Audio Equalizer
    private var equalizerManager: AudioEqualizerManager? = null
    
    // Cache for extracted audio files
    private var audioCacheDir: File? = null
    
    fun loadBook(bookId: String, context: android.content.Context, preferReadAloud: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            val id = bookId.toLongOrNull()
            if (id == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Invalid book ID")
                return@launch
            }
            currentBookId = id
            loadHighlights(id)
            
            val bookEntity = bookDao.getBookById(id)
            if (bookEntity == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Book not found")
                return@launch
            }
            
            // 1. Check if we have a direct local path (e.g. from Import)
            if (!bookEntity.localFilePath.isNullOrBlank()) {
                val file = File(bookEntity.localFilePath!!)
                if (file.exists()) {
                    parseAndLoad(file, id)
                    
                    // Initialize audio context
                    appContext = context.applicationContext
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
                     // Load saved progress
                     val progress = progressDao.getProgressByBookId(id)
                     if (progress != null) {
                         _uiState.update { it.copy(currentChapterIndex = progress.currentChapter) }
                     }
                    return@launch
                }
            }
            
            if (file == null || !file.exists()) {
                 // Try legacy/fallback location if any
                 val server = serverDao.getServerById(bookEntity.serverId.toLong())
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
                 _uiState.value = _uiState.value.copy(isLoading = false, error = "Book file not found. Please download, import, or fix the file path.")
                 return@launch
            }
            
            val validFile = file!!
            parseAndLoad(validFile, id)
            
            // Initialize audio context
            appContext = context.applicationContext
            
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
                     isReadAloudAvailable = hasAudio
                 )
                 
                 // Load saved progress
                 val progress = progressDao.getProgressByBookId(bookId)
                 if (progress != null) {
                     val safeIndex = progress.currentChapter.coerceAtLeast(0)
                     _uiState.update { it.copy(currentChapterIndex = safeIndex) }
                 }
                 
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
        // We reuse the cache structure from ReadAloudPlayerViewModel
        // This ensures shared cache if user plays via different screens
        val cacheDir = File(appContext?.cacheDir, "readaloud/$bookId")
        audioCacheDir = cacheDir
        
        if (!cacheDir.exists() || cacheDir.listFiles()?.isEmpty() == true) {
             withContext(Dispatchers.IO) {
                 try {
                     com.owlsoda.pageportal.util.DownloadUtils.unzipFile(epubFile, cacheDir)
                 } catch (e: Exception) {
                     e.printStackTrace()
                 }
             }
        }
        
        // Initialize player if not already
        if (exoPlayer == null && appContext != null) {
            withContext(Dispatchers.Main) {
                initializePlayer(appContext!!)
            }
        }
        
        // Load initial track
        loadAudioForChapter(_uiState.value.currentChapterIndex)
    }

    private fun initializePlayer(context: android.content.Context) {
        android.util.Log.d("ReaderViewModel", "initializePlayer: Creating ExoPlayer...")
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            android.util.Log.d("ReaderViewModel", "ExoPlayer created, audio session ID: $audioSessionId")
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.update { it.copy(isPlayingAudio = isPlaying) }
                    if (isPlaying) startSyncTicker() else stopSyncTicker()
                }
                
                override fun onPlaybackStateChanged(playbackState: Int) {
                     if (playbackState == Player.STATE_ENDED) {
                         // Auto-advance chapter?
                         if (_uiState.value.isPlayingAudio) {
                             nextChapter()
                         }
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
        val book = _uiState.value.book ?: return
        val chapter = book.chapters.getOrNull(chapterIndex) ?: return
        val smilData = book.smilData[chapter.id] ?: return
        
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
                // Handle chapter boundary crossing (future enhancement)
                appendLog("Chapter change needed: $newChapterHref#$fragmentId")
            }
        )
        
        // We assume the SMIL has ONE audio file reference for simplicity, 
        // or we take the first one. 
        // In complex SMIL, multiple audio files map to one text.
        // But usually it's 1-to-1 per chapter.
        
        val firstPar = smilData.parList.firstOrNull() ?: return
        val audioPath = firstPar.audioSrc // Relative path in zip, e.g. "audio/01.mp3"
        
        // Resolve absolute path in cache
        val audioFile = File(audioCacheDir, audioPath)
        val canonicalFile = audioFile.canonicalFile
        
        appendLog("Audio: Resolving $audioPath -> ${canonicalFile.path}. Exists: ${canonicalFile.exists()}")
        
        if (canonicalFile.exists()) {
             val mediaItem = MediaItem.fromUri(canonicalFile.path)
             exoPlayer?.let { player ->
                 player.setMediaItem(mediaItem)
                 player.prepare()
                 if (autoPlay) player.play()
             }
        } else {
             appendLog("Audio file missing!")
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
                         val currentPosSeconds = player.currentPosition / 1000.0
                         updateHighlight(currentPosSeconds)
                     }
                 }
                 delay(100) // 10Hz update
             }
        }
    }
    
    private fun stopSyncTicker() {
        syncTickerJob?.cancel()
    }
    
    private fun updateHighlight(time: Double) {
        // Use SmilSynchronizer for efficient position tracking
        smilSynchronizer?.updatePlaybackPosition((time * 1000).toLong())
        // Note: The highlight update is handled by the synchronizer's callback
        // which updates _uiState.activeSmilHighlightId
    }
    
    fun toggleAudioPlay() {
        android.util.Log.d("ReaderViewModel", "toggleAudioPlay called")
        
        // Lazy initialization if player is missing
        if (exoPlayer == null) {
             android.util.Log.d("ReaderViewModel", "exoPlayer is null, attempting lazy initialization...")
             if (appContext != null) {
                 initializePlayer(appContext!!)
                 // Try to load audio for current chapter
                 loadAudioForChapter(_uiState.value.currentChapterIndex, autoPlay = true)
             } else {
                 android.util.Log.e("ReaderViewModel", "Cannot initialize player: context is null")
             }
             return
        }

        exoPlayer?.let { player ->
            android.util.Log.d("ReaderViewModel", "ExoPlayer exists, isPlaying = ${player.isPlaying}")
            if (player.isPlaying) {
                // Pause and remember position
                player.pause()
                lastPausePosition = player.currentPosition
                android.util.Log.d("ReaderViewModel", "Paused at position $lastPausePosition")
            } else {
                // Check if we have media
                if (player.duration == androidx.media3.common.C.TIME_UNSET || player.duration <= 0) {
                     android.util.Log.d("ReaderViewModel", "Player has no media, loading info for chapter ${_uiState.value.currentChapterIndex}")
                     loadAudioForChapter(_uiState.value.currentChapterIndex, autoPlay = true)
                     return@let
                }

                // Auto-rewind 5 seconds on resume
                if (lastPausePosition != null) {
                    val rewindPosition = (lastPausePosition!! - 5000).coerceAtLeast(0)
                    player.seekTo(rewindPosition)
                    lastPausePosition = null
                    android.util.Log.d("ReaderViewModel", "Rewound to position $rewindPosition")
                }
                android.util.Log.d("ReaderViewModel", "Calling player.play()")
                player.play()
            }
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
                    preferencesRepository.readerBrightness
                ) { vertical, align, spacing, bright ->
                    ReaderPreferencesPart2(vertical, align, spacing, bright)
                }.collect { p2 ->
                    _uiState.update { it.copy(
                        isVerticalScroll = p2.isVerticalScroll,
                        textAlignment = p2.textAlignment,
                        paragraphSpacing = p2.paragraphSpacing,
                        brightness = p2.brightness
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
            // Part 4: Gestures
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
        val brightness: Float
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
        val book = _uiState.value.book ?: return null
        val chapter = book.chapters.getOrNull(chapterIndex) ?: return null
        
        return try {
            val stream = parser.getInputStream(chapter.href)
            stream?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }
    
    fun nextChapter() {
        val currentState = _uiState.value
        val book = currentState.book ?: return
        if (currentState.currentChapterIndex < book.chapters.size - 1) {
            val nextIndex = currentState.currentChapterIndex + 1
            _uiState.value = currentState.copy(currentChapterIndex = nextIndex)
            
            if (currentState.isReadAloudAvailable) {
                loadAudioForChapter(nextIndex, autoPlay = currentState.isPlayingAudio)
            }
        }
    }
    
    fun previousChapter() {
        if (_uiState.value.currentChapterIndex > 0) {
            val prevIndex = _uiState.value.currentChapterIndex - 1
            _uiState.value = _uiState.value.copy(currentChapterIndex = prevIndex)
            
            if (_uiState.value.isReadAloudAvailable) {
                loadAudioForChapter(prevIndex, autoPlay = _uiState.value.isPlayingAudio)
            }
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

    private var syncJob: kotlinx.coroutines.Job? = null

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
    
    fun getBookmarks(bookId: Long) = bookmarkDao.getBookmarksForBook(bookId)
    
    fun deleteBookmark(bookmark: com.owlsoda.pageportal.core.database.entity.BookmarkEntity) {
        viewModelScope.launch {
            bookmarkDao.deleteBookmark(bookmark)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Try to sync one last time if there's a pending job
        syncJob?.let {
            if (it.isActive) {
                currentBookId?.let { id ->
                    // We can't use viewModelScope as it's being cancelled, 
                    // but we can fire a sync if needed. 
                    // Actually repository should probably handle this or use workmanager.
                }
            }
        }
        parser.close()
        exoPlayer?.release()
        exoPlayer = null
    }

}
