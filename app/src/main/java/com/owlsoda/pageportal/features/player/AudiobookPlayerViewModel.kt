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
import com.owlsoda.pageportal.services.ServiceManager
import com.owlsoda.pageportal.services.ServiceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class Chapter(
    val title: String,
    val startOffset: Long,
    val duration: Long
)

data class AudiobookPlayerState(
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
class AudiobookPlayerViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val progressDao: ProgressDao,
    private val serverDao: ServerDao,
    private val serviceManager: ServiceManager,
    private val libraryRepository: com.owlsoda.pageportal.data.repository.LibraryRepository,
    private val preferencesRepository: com.owlsoda.pageportal.data.preferences.PreferencesRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow(AudiobookPlayerState())
    val state: StateFlow<AudiobookPlayerState> = _state.asStateFlow()
    
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
        
        // Observe persistent playback speed
        viewModelScope.launch {
            preferencesRepository.playbackSpeed.collect { speed ->
                _state.value = _state.value.copy(playbackSpeed = speed)
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
                        extractChapters()
                    }
                    Player.STATE_BUFFERING -> {
                        // Still loading
                    }
                    Player.STATE_ENDED -> {
                        saveProgress()
                    }
                    Player.STATE_IDLE -> {
                        // Idle
                    }
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
        // Optimization: prevent redundant reloading
        if (_state.value.bookId == bookId && !_state.value.isLoading && _state.value.error == null) {
             if (autoPlay) play()
             return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            try {
                // Get book from local cache
                val id = bookId.toLongOrNull() ?: return@launch
                val book = bookDao.getBookById(id)
                if (book == null) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Book not found"
                    )
                    return@launch
                }
                
                // Get saved progress
                val progress = progressDao.getProgressByBookId(book.id)
                val resumePosition = progress?.currentPosition ?: 0L
                
                _state.value = _state.value.copy(
                    bookId = bookId,
                    title = book.title,
                    author = book.authors,
                    coverUrl = book.coverUrl ?: ""
                )
                
                // Build media item
                val mediaMetadata = MediaMetadata.Builder()
                    .setTitle(book.title)
                    .setArtist(book.authors)
                    .setArtworkUri(book.coverUrl?.let { android.net.Uri.parse(it) })
                    .build()
                
                // Determine Media URI (Local vs Remote)
                val mediaUri = if (book.isAudiobookDownloaded && !book.localFilePath.isNullOrBlank()) {
                    android.net.Uri.fromFile(java.io.File(book.localFilePath))
                } else {
                    val service = serviceManager.getService(book.serverId)
                    val streamUrl = if (service != null) {
                        try {
                            // Try specialized stream URL first (for ABS/REST services)
                            if (service is com.owlsoda.pageportal.services.audiobookshelf.AudiobookshelfService) {
                                service.getStreamUrl(book.serviceBookId)
                            } else {
                                // Fallback to file download URL
                                val details = service.getBookDetails(book.serviceBookId)
                                details.files.firstOrNull { it.mimeType.startsWith("audio") }?.downloadUrl
                            }
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                    
                    if (streamUrl.isNullOrBlank()) {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error = "No audio available for streaming or offline"
                        )
                        return@launch
                    }
                    android.net.Uri.parse(streamUrl)
                }
                
                val mediaItem = MediaItem.Builder()
                    .setUri(mediaUri)
                    .setMediaMetadata(mediaMetadata)
                    .build()
                
                // Wait for player to be ready with timeout
                val playerReady = withTimeoutOrNull(5000) {
                    while (player == null) {
                        delay(100)
                    }
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
                    
                    updateCurrentChapterIndex()
                    
                    // Auto-save every 5 seconds while playing
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
    
    private fun extractChapters() {
        val p = player ?: return
        val chapters = mutableListOf<Chapter>()
        
        val timeline = p.currentTimeline
        if (!timeline.isEmpty && timeline.windowCount > 1) {
            var accumulated = 0L
            val window = androidx.media3.common.Timeline.Window()
            
            for (i in 0 until timeline.windowCount) {
                timeline.getWindow(i, window)
                val title = window.mediaItem.mediaMetadata.title?.toString() 
                    ?: "Chapter ${i + 1}"
                chapters.add(Chapter(title, accumulated, window.durationMs))
                accumulated += window.durationMs
            }
        }
        
        _state.value = _state.value.copy(chapters = chapters)
    }
    
    private fun updateCurrentChapterIndex() {
        val chapters = _state.value.chapters
        if (chapters.isEmpty()) return
        
        val pos = _state.value.currentPosition
        val index = chapters.indexOfLast { pos >= it.startOffset }
        
        if (index != _state.value.currentChapterIndex) {
            _state.value = _state.value.copy(currentChapterIndex = index)
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
        viewModelScope.launch {
            preferencesRepository.setPlaybackSpeed(speed)
            // _state update will happen via subscription or service feedback
            _state.value = _state.value.copy(playbackSpeed = speed)
        }
    }
    
    fun cyclePlaybackSpeed() {
        val newSpeed = when (_state.value.playbackSpeed) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2.0f
            2.0f -> 0.75f
            else -> 1.0f
        }
        setPlaybackSpeed(newSpeed)
    }
    
    fun skipToChapter(index: Int) {
        val chapters = _state.value.chapters
        if (index in chapters.indices) {
            seekTo(chapters[index].startOffset)
        }
    }
    
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        
        // Send command to Service
        val args = android.os.Bundle().apply {
            putInt(PlaybackService.ARG_MINUTES, minutes)
        }
        val command = androidx.media3.session.SessionCommand(PlaybackService.COMMAND_SLEEP_TIMER, android.os.Bundle.EMPTY)
        (player as? MediaController)?.sendCustomCommand(command, args)
        
        if (minutes <= 0) {
            _state.value = _state.value.copy(sleepTimerRemaining = 0)
            return
        }
        
        var remaining = minutes * 60 * 1000L
        _state.value = _state.value.copy(sleepTimerRemaining = remaining)
        
        // UI-only countdown
        sleepTimerJob = viewModelScope.launch {
            while (remaining > 0) {
                delay(1000)
                if (_state.value.isPlaying) {
                    remaining -= 1000
                    _state.value = _state.value.copy(sleepTimerRemaining = remaining)
                } else {
                    // If paused (by service or user), stop countdown
                    break
                }
            }
            if (!isActive) return@launch
            // We don't pause here; the Service does it. 
            // When Service pauses, isPlaying -> false, loop breaks.
        }
    }
    
    fun cancelSleepTimer() {
        setSleepTimer(0)
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
            
            // Sync to server via library repository
            try {
                libraryRepository.syncProgress(state.bookId.toLongOrNull() ?: 0L)
            } catch (e: Exception) {
                // Ignore sync failures for now, they'll be retried next time
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        progressUpdateJob?.cancel()
        sleepTimerJob?.cancel()
        saveProgress()
    }
}
