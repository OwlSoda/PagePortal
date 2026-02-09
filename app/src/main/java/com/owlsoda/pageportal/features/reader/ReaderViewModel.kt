package com.owlsoda.pageportal.features.reader

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.dao.ProgressDao
import com.owlsoda.pageportal.reader.epub.EpubBook
import com.owlsoda.pageportal.reader.epub.EpubParser
import com.owlsoda.pageportal.reader.epub.SmilData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject

data class ReaderUiState(
    val book: EpubBook? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentChapterIndex: Int = 0,
    val fontSize: Int = 100, // Percent
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val isPlaying: Boolean = false,
    val hasAudio: Boolean = false,
    val highlightedElementId: String? = null
)

enum class ReaderTheme(val backgroundColor: String, val textColor: String) {
    LIGHT("#FFFFFF", "#000000"),
    DARK("#121212", "#E0E0E0"),
    SEPIA("#F4ECD8", "#5B4636")
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val progressDao: ProgressDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    
    // Parser instance to serve resources
    private val parser = EpubParser()
    
    // Audio Player
    private var exoPlayer: ExoPlayer? = null
    private var currentSmilData: SmilData? = null
    private var audioSrcs: List<String> = emptyList()
    private var syncJob: Job? = null

    fun loadBook(bookId: String, context: Context) { // Keep context param for backward compat if needed, but we use injected context
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            val id = bookId.toLongOrNull()
            if (id == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Invalid book ID")
                return@launch
            }
            val bookEntity = bookDao.getBookById(id)
            if (bookEntity == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Book not found")
                return@launch
            }
            
            val fileName = "${bookEntity.title.replace(Regex("[^a-zA-Z0-9]"), "_")}.epub" 
            val file = File(context.filesDir, "downloads/$fileName")
            
            if (!file.exists()) {
                 _uiState.value = _uiState.value.copy(isLoading = false, error = "File not found: ${file.absolutePath}")
                 return@launch
            }
            
            val result = parser.parse(file)
            
            result.fold(
                onSuccess = { epubBook ->
                     _uiState.value = _uiState.value.copy(
                         book = epubBook,
                         isLoading = false
                     )
                     prepareAudioForChapter(epubBook, 0)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to parse EPUB: ${error.message}"
                    )
                }
            )
        }
    }
    
    private suspend fun prepareAudioForChapter(book: EpubBook, chapterIndex: Int) {
        val chapter = book.chapters.getOrNull(chapterIndex) ?: return
        val mediaOverlayId = chapter.mediaOverlayId
        
        if (mediaOverlayId != null) {
            val smilData = book.smilData[mediaOverlayId]
            if (smilData != null) {
                currentSmilData = smilData
                _uiState.value = _uiState.value.copy(hasAudio = true)

                // Extract audio files and prepare player
                preparePlayer(smilData)
            } else {
                _uiState.value = _uiState.value.copy(hasAudio = false)
                currentSmilData = null
            }
        } else {
            _uiState.value = _uiState.value.copy(hasAudio = false)
            currentSmilData = null
        }
    }

    private suspend fun preparePlayer(smilData: SmilData) = withContext(Dispatchers.IO) {
        // Identify unique audio sources in order
        // A smarter way is to group by par order, but typically it's sequential.
        // We'll trust the order in pars.
        val distinctAudioSrcs = smilData.pars.map { it.audioSrc }.distinct()
        audioSrcs = distinctAudioSrcs
        
        val mediaItems = distinctAudioSrcs.mapNotNull { src ->
            // Extract file to cache
            val inputStream = parser.getInputStream(src) ?: return@mapNotNull null
            val cacheFile = File(context.cacheDir, "audio_cache_${src.hashCode()}.mp3")
            if (!cacheFile.exists()) {
                try {
                    FileOutputStream(cacheFile).use { output ->
                        inputStream.copyTo(output)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    return@mapNotNull null
                }
            }
            MediaItem.fromUri(Uri.fromFile(cacheFile))
        }
        
        withContext(Dispatchers.Main) {
            initPlayer()
            exoPlayer?.setMediaItems(mediaItems)
            exoPlayer?.prepare()
        }
    }

    private fun initPlayer() {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                        if (isPlaying) {
                            startSyncLoop()
                        } else {
                            stopSyncLoop()
                        }
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            _uiState.value = _uiState.value.copy(isPlaying = false, highlightedElementId = null)
                            stopSyncLoop()
                        }
                    }

                    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                         // Update highlight immediately on seek
                         updateHighlight()
                    }
                })
            }
        }
    }

    private fun startSyncLoop() {
        stopSyncLoop()
        syncJob = viewModelScope.launch {
            while (true) {
                updateHighlight()
                delay(100)
            }
        }
    }

    private fun stopSyncLoop() {
        syncJob?.cancel()
        syncJob = null
    }

    private fun updateHighlight() {
        val player = exoPlayer ?: return
        if (!player.isPlaying && player.playbackState != Player.STATE_READY) return // Don't update if not playing/ready? Actually we want to update on pause too if user seeks.

        val position = player.currentPosition / 1000.0 // seconds
        val currentMediaItemIndex = player.currentMediaItemIndex

        if (currentMediaItemIndex < 0 || currentMediaItemIndex >= audioSrcs.size) return
        val currentAudioSrc = audioSrcs[currentMediaItemIndex]

        val smilData = currentSmilData ?: return
        val matchingPar = smilData.pars.find { par ->
            par.audioSrc == currentAudioSrc && position >= par.clipBegin && position < par.clipEnd
        }

        val newHighlightId = matchingPar?.textSrc?.substringAfter("#", "")?.takeIf { it.isNotEmpty() }

        if (_uiState.value.highlightedElementId != newHighlightId) {
             _uiState.value = _uiState.value.copy(highlightedElementId = newHighlightId)
        }
    }

    fun toggleAudio() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun getResource(url: String): InputStream? {
        val book = _uiState.value.book ?: return null
        val path = url.removePrefix("http://localhost/")
        return parser.getInputStream(path) ?: parser.getInputStream(book.basePath + path)
    }
    
    fun nextChapter() {
        val currentState = _uiState.value
        val book = currentState.book ?: return
        if (currentState.currentChapterIndex < book.chapters.size - 1) {
            val newIndex = currentState.currentChapterIndex + 1
            _uiState.value = currentState.copy(currentChapterIndex = newIndex, isPlaying = false, highlightedElementId = null)
            exoPlayer?.stop()
            viewModelScope.launch {
                prepareAudioForChapter(book, newIndex)
            }
        }
    }
    
    fun previousChapter() {
        val currentState = _uiState.value
        val book = currentState.book ?: return
        if (currentState.currentChapterIndex > 0) {
            val newIndex = currentState.currentChapterIndex - 1
            _uiState.value = currentState.copy(currentChapterIndex = newIndex, isPlaying = false, highlightedElementId = null)
            exoPlayer?.stop()
             viewModelScope.launch {
                prepareAudioForChapter(book, newIndex)
            }
        }
    }
    
    fun setFontSize(percent: Int) {
        _uiState.value = _uiState.value.copy(fontSize = percent.coerceIn(50, 200))
    }
    
    fun setTheme(theme: ReaderTheme) {
        _uiState.value = _uiState.value.copy(theme = theme)
    }

    override fun onCleared() {
        super.onCleared()
        exoPlayer?.release()
        exoPlayer = null
        parser.close()
    }
}
