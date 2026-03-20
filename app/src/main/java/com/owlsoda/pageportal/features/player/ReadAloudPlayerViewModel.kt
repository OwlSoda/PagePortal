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
import com.owlsoda.pageportal.data.repository.LibraryRepository
import com.owlsoda.pageportal.data.repository.SyncRepository
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
    private val serverDao: ServerDao,
    private val syncRepository: SyncRepository,
    private val libraryRepository: LibraryRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow(ReadAloudPlayerState())
    val state: StateFlow<ReadAloudPlayerState> = _state.asStateFlow()
    
    val isSyncing = syncRepository.isSyncing
    
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

    private fun logToFile(message: String) {
        try {
            val debugFile = java.io.File(appContext?.filesDir, "readaloud_debug.log")
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            debugFile.appendText("[$timestamp] $message\n")
        } catch (e: Exception) { }
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
        // Optimization: prevent redundant reloading
        if (_state.value.bookId == bookId && !_state.value.isLoading && _state.value.error == null) {
             if (autoPlay) play()
             return
        }

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
                
                
                // 1. Check for local progress
                val localProgress = progressDao.getProgressByBookId(book.id)
                var currentPos = localProgress?.currentPosition ?: 0L
                
                // 2. Perform bidirectional sync
                _state.value = _state.value.copy(isLoading = true)
                val syncResult = syncRepository.syncProgress(id)
                if (syncResult.isSuccess) {
                    // Update current pos if sync changed it
                    val updatedProgress = progressDao.getProgressByBookId(id)
                    currentPos = updatedProgress?.currentPosition ?: 0L
                }

                _state.value = _state.value.copy(
                    bookId = bookId,
                    title = book.title,
                    author = book.authors,
                    coverUrl = book.coverUrl ?: "",
                    currentPosition = currentPos
                )
                
                // Build path to ReadAloud file using consistent utility
                val baseDir = appContext?.filesDir ?: return@launch
                val readAloudFile = com.owlsoda.pageportal.util.DownloadUtils.getFilePath(
                    baseDir, 
                    book, 
                    com.owlsoda.pageportal.util.DownloadUtils.DownloadFormat.READALOUD
                )
                
                if (!readAloudFile.exists()) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "ReadAloud file not found: ${readAloudFile.name}"
                    )
                    return@launch
                }
                
                // Check for cached unzipped content
                val cacheDir = File(appContext?.cacheDir, "readaloud/${book.id}")
                var attemptedUnzip = false
                
                if (!cacheDir.exists() || cacheDir.listFiles()?.isEmpty() == true) {
                    _state.value = _state.value.copy(isLoading = true) 
                    withContext(Dispatchers.IO) {
                        try {
                             com.owlsoda.pageportal.util.DownloadUtils.unzipFile(readAloudFile, cacheDir)
                             attemptedUnzip = true
                        } catch (e: Exception) {
                            logToFile("Unzip failed: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                }

                // Helper to find audio
                fun findAudioInCache(): File? {
                    return cacheDir.walkTopDown()
                        .filter { file -> 
                            file.isFile && 
                            (file.extension.equals("mp3", ignoreCase = true) || 
                             file.extension.equals("m4b", ignoreCase = true) || 
                             file.extension.equals("ogg", ignoreCase = true) || 
                             file.extension.equals("wav", ignoreCase = true) ||
                             file.extension.equals("m4a", ignoreCase = true) ||
                             file.extension.equals("mp4", ignoreCase = true) ||
                             file.extension.equals("aac", ignoreCase = true))
                        }
                        .firstOrNull()
                }

                var audioFile = findAudioInCache()

                // Retry logic: If not found and we didn't just unzip, delete and try again
                if (audioFile == null && !attemptedUnzip) {
                    logToFile("Audio not found in existing cache. Deleting and retrying unzip...")
                    withContext(Dispatchers.IO) {
                        try {
                            cacheDir.deleteRecursively()
                            com.owlsoda.pageportal.util.DownloadUtils.unzipFile(readAloudFile, cacheDir)
                        } catch (e: Exception) {
                            logToFile("Retry zip failed: ${e.message}")
                        }
                    }
                    audioFile = findAudioInCache()
                }

                if (audioFile == null) {
                    // thorough debug of source file
                    val sb = StringBuilder()
                    sb.append("Error: No audio found.\n")
                    sb.append("Source File: ${readAloudFile.name}\n")
                    sb.append("Source Size: ${readAloudFile.length()} bytes\n")
                    sb.append("Source Exists: ${readAloudFile.exists()}\n")
                    
                    try {
                        sb.append("Zip Contents:\n")
                        java.util.zip.ZipFile(readAloudFile).use { zip ->
                            val entries = zip.entries()
                            var count = 0
                            while (entries.hasMoreElements() && count < 10) {
                                val entry = entries.nextElement()
                                sb.append("- ${entry.name} (${entry.size} bytes)\n")
                                count++
                            }
                            if (entries.hasMoreElements()) sb.append("...and more\n")
                        }
                    } catch (e: Exception) {
                        sb.append("Failed to read Zip: ${e.message}\n")
                    }
                    
                    sb.append("\nCache Dir (${cacheDir.isDirectory}):\n")
                    // Increase limit and show all extensions to debug
                    val fileList = cacheDir.walkTopDown().take(20).joinToString("\n") { "${it.name} (${it.length()} bytes)" }
                    sb.append(fileList)

                    val msg = sb.toString()
                    logToFile(msg)
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = msg
                    )
                    return@launch
                }
                
                logToFile("Found audio file: ${audioFile.absolutePath}")
                
                val mediaMetadata = MediaMetadata.Builder()
                    .setTitle(book.title)
                    .setArtist(book.authors)
                    .setArtworkUri(book.coverUrl?.let { android.net.Uri.parse(it) })
                    .build()
                
                val mediaItem = MediaItem.Builder()
                    .setUri(android.net.Uri.fromFile(audioFile))
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
                        if (currentPos > 0) {
                            seekTo(currentPos)
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
    
    fun deleteReadAloud() {
        viewModelScope.launch {
            val bookIdPath = _state.value.bookId
            if (bookIdPath.isEmpty()) return@launch
            
             try {
                // 1. Delete Unzipped Cache
                val cacheDir = File(appContext?.cacheDir, "readaloud/$bookIdPath")
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                }

                // 2. Delete Original Downloaded File
                val id = bookIdPath.toLongOrNull() ?: return@launch
                val book = bookDao.getBookById(id) ?: return@launch
                
                // Update DB to reflect not downloaded
                 bookDao.insertBook(book.copy(isReadAloudDownloaded = false))

                val baseDir = appContext?.filesDir ?: return@launch
                val readAloudFile = com.owlsoda.pageportal.util.DownloadUtils.getFilePath(
                    baseDir, 
                    book, 
                    com.owlsoda.pageportal.util.DownloadUtils.DownloadFormat.READALOUD
                )
                
                if (readAloudFile.exists()) {
                    readAloudFile.delete()
                }
                
                // 3. Update State
                _state.value = _state.value.copy(
                    error = "File deleted. Please re-download from the library.",
                    isLoading = false,
                    isPlaying = false
                )
                
                player?.stop()
                player?.clearMediaItems()
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Delete failed: ${e.message}")
            }
        }
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
            
            // Push to server
            val id = state.bookId.toLongOrNull() ?: return@launch
            syncRepository.pushProgress(id)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        progressUpdateJob?.cancel()
        sleepTimerJob?.cancel()
        saveProgress()
    }
}
