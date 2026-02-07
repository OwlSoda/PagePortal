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

    val pdfFile: File? = null,
    // ReadAloud State
    val isReadAloudAvailable: Boolean = false,
    val isPlayingAudio: Boolean = false,
    val activeSmilHighlightId: String? = null, // The text ID to highlight
    val playbackSpeed: Float = 1.0f,
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
        exoPlayer = ExoPlayer.Builder(context).build().apply {
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
    }
    
    private fun loadAudioForChapter(chapterIndex: Int, autoPlay: Boolean = false) {
        val book = _uiState.value.book ?: return
        val chapter = book.chapters.getOrNull(chapterIndex) ?: return
        val smilData = book.smilData[chapter.id] ?: return
        
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
        val book = _uiState.value.book ?: return
        val chapter = book.chapters.getOrNull(_uiState.value.currentChapterIndex) ?: return
        val smilData = book.smilData[chapter.id] ?: return
        
        // Find matching par
        // Optimization: Could cache last index and search forward
        val match = smilData.parList.find { par ->
             time >= par.clipBegin && time < par.clipEnd
        }
        
        if (match != null) {
            // Extract ID from textSrc ("file.xhtml#para1" -> "para1")
            val id = match.textSrc.substringAfter("#", "")
            if (id.isNotEmpty() && id != _uiState.value.activeSmilHighlightId) {
                _uiState.update { it.copy(activeSmilHighlightId = id) }
            }
        }
    }
    
    fun toggleAudioPlay() {
        exoPlayer?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                kotlinx.coroutines.flow.combine(
                    preferencesRepository.readerFontSize,
                    preferencesRepository.readerTheme,
                    preferencesRepository.readerFontFamily
                ) { size, theme, family ->
                    Triple(size, theme, family)
                },
                kotlinx.coroutines.flow.combine(
                    preferencesRepository.readerLineHeight,
                    preferencesRepository.readerMargin,
                    preferencesRepository.readerVerticalScroll
                ) { height, margin, vertical ->
                     Triple(height, margin, vertical)
                }
            ) { (size, theme, family), (height, margin, vertical) ->
                ReaderPreferences(size, theme, family, height, margin, vertical)
            }.collect { prefs ->
                _uiState.update { 
                    it.copy(
                        fontSize = (prefs.fontSize * 100).toInt(),
                        theme = ReaderTheme.fromString(prefs.theme),
                        fontFamily = prefs.fontFamily,
                        lineHeight = prefs.lineHeight,
                        margin = prefs.margin,
                        isVerticalScroll = prefs.isVerticalScroll
                    ) 
                }
            }
        }
    }
    
    data class ReaderPreferences(
        val fontSize: Float,
        val theme: String,
        val fontFamily: String,
        val lineHeight: Float,
        val margin: Int,
        val isVerticalScroll: Boolean
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

    private var syncJob: kotlinx.coroutines.Job? = null

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
