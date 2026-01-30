package com.owlsoda.pageportal.features.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.dao.ProgressDao
import com.owlsoda.pageportal.core.database.dao.ServerDao
import com.owlsoda.pageportal.core.database.entity.ProgressEntity
import com.owlsoda.pageportal.player.PlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject

data class ReadAloudPlayerState(
    val bookId: String = "",
    val title: String = "",
    val author: String = "",
    val coverUrl: String = "",
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val isLoading: Boolean = true,
    val error: String? = null,
    val chapters: List<Chapter> = emptyList(),
    val currentChapterIndex: Int = -1,
    val sleepTimerRemaining: Long = 0L
)

@HiltViewModel
class ReadAloudPlayerViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val progressDao: ProgressDao,
    private val serverDao: ServerDao
) : ViewModel() {
    
    private val _state = MutableStateFlow(ReadAloudPlayerState())
    val state: StateFlow<ReadAloudPlayerState> = _state.asStateFlow()
    
    private var player: Player? = null
    private var progressUpdateJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var lastSaveTime = 0L
    
    private var appContext: android.content.Context? = null
    
    fun initializePlayer(context: android.content.Context) {
        appContext = context.applicationContext
        
        if (player == null) {
            PlaybackService.buildController(context) { controller ->
                player = controller
                setupPlayerListener(controller)
                startProgressUpdates()
            }
        }
    }
    
    private fun setupPlayerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            duration = controller.duration
                        )
                    }
                    Player.STATE_ENDED -> {
                        saveProgress()
                    }
                    else -> {}
                }
            }
            
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _state.value = _state.value.copy(
                    error = error.message ?: "Playback error"
                )
            }
        })
    }
    
    fun loadBook(bookId: String, autoPlay: Boolean = true) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            try {
                val id = bookId.toLongOrNull() ?: return@launch
                val book = bookDao.getBookById(id)
                if (book == null) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Book not found"
                    )
                    return@launch
                }
                
                // Check for ReadAloud download
                if (!book.isReadAloudDownloaded) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "ReadAloud version not downloaded. Please download it first."
                    )
                    return@launch
                }
                
                val progress = progressDao.getProgressByBookId(book.id)
                val resumePosition = progress?.currentPosition ?: 0L
                
                _state.value = _state.value.copy(
                    bookId = bookId,
                    title = book.title,
                    author = book.authors,
                    coverUrl = book.coverUrl ?: ""
                )
                
                // Build path to ReadAloud file: downloads/storyteller/readaloud/{title}.mp3
                val baseDir = appContext?.getExternalFilesDir(null) ?: appContext?.filesDir
                val readAloudFile = File(baseDir, "downloads/storyteller/readaloud/${book.title}.mp3")
                
                if (!readAloudFile.exists()) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "ReadAloud file not found at expected path."
                    )
                    return@launch
                }
                
                val mediaMetadata = MediaMetadata.Builder()
                    .setTitle(book.title)
                    .setArtist(book.authors)
                    .setArtworkUri(book.coverUrl?.let { android.net.Uri.parse(it) })
                    .build()
                
                val mediaItem = MediaItem.Builder()
                    .setUri(android.net.Uri.fromFile(readAloudFile))
                    .setMediaMetadata(mediaMetadata)
                    .build()
                
                val playerReady = withTimeoutOrNull(5000) {
                    while (player == null) { delay(100) }
                    true
                } ?: false
                
                if (!playerReady || player == null) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Player initialization timeout"
                    )
                    return@launch
                }
                
                withContext(Dispatchers.Main) {
                    player?.apply {
                        setMediaItem(mediaItem)
                        prepare()
                        if (resumePosition > 0) {
                            seekTo(resumePosition)
                        }
                        if (autoPlay) {
                            play()
                        }
                    }
                }
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load book"
                )
            }
        }
    }
    
    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = viewModelScope.launch {
            while (isActive) {
                player?.let { p ->
                    val currentState = _state.value
                    _state.value = currentState.copy(
                        currentPosition = p.currentPosition,
                        duration = if (p.duration > 0) p.duration else currentState.duration
                    )
                    
                    if (currentState.isPlaying) {
                        val now = System.currentTimeMillis()
                        if (now - lastSaveTime > 5000) {
                            saveProgress()
                            lastSaveTime = now
                        }
                    }
                }
                delay(1000)
            }
        }
    }
    
    fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
                saveProgress()
            } else {
                it.play()
            }
        }
    }
    
    fun play() = player?.play()
    fun pause() = player?.pause()
    
    fun seekTo(position: Long) {
        player?.seekTo(position)
        _state.value = _state.value.copy(currentPosition = position)
    }
    
    fun rewind(seconds: Int = 10) {
        val newPos = (_state.value.currentPosition - seconds * 1000).coerceAtLeast(0)
        seekTo(newPos)
    }
    
    fun forward(seconds: Int = 30) {
        val newPos = (_state.value.currentPosition + seconds * 1000)
            .coerceAtMost(_state.value.duration)
        seekTo(newPos)
    }
    
    fun setPlaybackSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
        _state.value = _state.value.copy(playbackSpeed = speed)
    }
    
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        
        if (minutes <= 0) {
            _state.value = _state.value.copy(sleepTimerRemaining = 0)
            return
        }
        
        var remaining = minutes * 60 * 1000L
        _state.value = _state.value.copy(sleepTimerRemaining = remaining)
        
        sleepTimerJob = viewModelScope.launch {
            while (remaining > 0) {
                delay(1000)
                if (_state.value.isPlaying) {
                    remaining -= 1000
                    _state.value = _state.value.copy(sleepTimerRemaining = remaining)
                    
                    if (remaining <= 0) {
                        player?.pause()
                        _state.value = _state.value.copy(sleepTimerRemaining = 0)
                    }
                }
            }
        }
    }
    
    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _state.value = _state.value.copy(sleepTimerRemaining = 0)
    }
    
    private fun saveProgress() {
        val state = _state.value
        if (state.bookId.isEmpty()) return
        
        viewModelScope.launch {
            val progress = ProgressEntity(
                bookId = state.bookId.toLongOrNull() ?: 0L,
                currentPosition = state.currentPosition,
                currentChapter = state.currentChapterIndex,
                percentComplete = if (state.duration > 0) {
                    (state.currentPosition.toFloat() / state.duration * 100).coerceIn(0f, 100f)
                } else 0f,
                lastUpdated = System.currentTimeMillis()
            )
            progressDao.insertProgress(progress)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        progressUpdateJob?.cancel()
        sleepTimerJob?.cancel()
        saveProgress()
    }
}
