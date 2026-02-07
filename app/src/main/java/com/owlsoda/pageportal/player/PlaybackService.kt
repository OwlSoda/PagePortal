package com.owlsoda.pageportal.player

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.AdtsExtractor
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService

import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.owlsoda.pageportal.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlinx.coroutines.launch
import com.owlsoda.pageportal.data.preferences.PreferencesRepository

/**
 * Media3 playback service for audiobook playback.
 * Handles background playback, media notifications, and audio focus.
 */
@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {
    
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var preferencesRepository: PreferencesRepository
    
    private var bitmapLoaderExecutor: java.util.concurrent.ExecutorService? = null
    
    // No longer needed as AuthInterceptor handles it globally
    // private var authToken: String? = null
    

    
    private var mediaLibrarySession: MediaLibrarySession? = null
    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        
        // ... (Renderers, TrackSelector, LoadControl, DataSource setup same as before) 
        
        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            setEnableDecoderFallback(true)
        }
        val trackSelector = DefaultTrackSelector(this)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(20000, 60000, 2000, 3000)
            .build()
            
        val dataSourceFactory = DataSource.Factory {
            val okHttpFactory = OkHttpDataSource.Factory(okHttpClient)
            DefaultDataSource.Factory(this, okHttpFactory).createDataSource()
        }
        
        val extractorsFactory = DefaultExtractorsFactory()
            .setAdtsExtractorFlags(AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
        
        val player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this, extractorsFactory).setDataSourceFactory(dataSourceFactory))
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            
        // Lean player wrapper remains... (omitted for brevity in replacement if possible, but assuming partial replace)
        // RE-INSERTING LEAN PLAYER LOGIC FOR SAFETY
         val leanPlayer = object : ForwardingPlayer(player) {
            override fun getMediaMetadata(): MediaMetadata {
                val original = super.getMediaMetadata()
                return MediaMetadata.Builder()
                    .setTitle(original.title)
                    .setArtist(original.artist)
                    .setSubtitle(original.subtitle)
                    .setArtworkUri(original.artworkUri)
                    .setExtras(original.extras)
                    .build()
            }
        }
        
        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                 android.util.Log.d("PlaybackService", "Tracks changed")
            }
             override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("PlaybackService", "Playback error: ${error.message}")
            }
        })
        
        // Observe playback speed
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            preferencesRepository.playbackSpeed.collect { speed ->
                player.setPlaybackSpeed(speed)
            }
        }

        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build())

        // Build Library Session
        mediaLibrarySession = MediaLibrarySession.Builder(this, leanPlayer, CustomLibrarySessionCallback())
            .setSessionActivity(createSessionActivity())
            .build()
    }

    private inner class CustomLibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val validCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(COMMAND_SLEEP_TIMER, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(validCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == COMMAND_SLEEP_TIMER) {
                val minutes = args.getInt(ARG_MINUTES, 0)
                handleSleepTimer(minutes)
                return com.google.common.util.concurrent.Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_SUCCESS)
                )
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // Root for Android Auto
            val rootItem = MediaItem.Builder()
                .setMediaId("root")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setTitle("Root")
                        .build()
                )
                .build()
            return com.google.common.util.concurrent.Futures.immediateFuture(
                LibraryResult.ofItem(rootItem, params)
            )
        }
        
        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            // Return empty list for now - Auto browsing will be empty but service will connect
             return com.google.common.util.concurrent.Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.of(), params)
            )
        }
    }
    
    private fun handleSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) return
        
        sleepTimerJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val duration = minutes * 60 * 1000L
            kotlinx.coroutines.delay(duration)
            
            // Fade out
            val player = mediaLibrarySession?.player ?: return@launch
            val startVolume = player.volume
            val steps = 20
            for (i in 1..steps) {
                player.volume = startVolume * (1f - (i.toFloat() / steps))
                kotlinx.coroutines.delay(100)
            }
            player.pause()
            player.volume = startVolume // Reset
            sleepTimerJob = null
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }
    
    override fun onDestroy() {
        sleepTimerJob?.cancel()
        mediaLibrarySession?.run {
            player.release()
            release()
        }
        super.onDestroy()
    }
    
    private fun createSessionActivity(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_click", true)
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        mediaLibrarySession?.player?.let { player ->
            player.pause()
            player.stop()
        }
        stopSelf()
    }
    
    companion object {
        const val COMMAND_SLEEP_TIMER = "COMMAND_SLEEP_TIMER"
        const val ARG_MINUTES = "ARG_MINUTES"
        
        fun buildController(
            context: android.content.Context,
            onConnected: (MediaController) -> Unit
        ): ListenableFuture<MediaController> {
            val sessionToken = SessionToken(
                context,
                ComponentName(context, PlaybackService::class.java)
            )
            val future = MediaController.Builder(context, sessionToken).buildAsync()
            future.addListener({
                try {
                    val controller = future.get()
                    onConnected(controller)
                } catch (e: Exception) {
                    android.util.Log.e("PlaybackService", "Failed to connect controller", e)
                }
            }, MoreExecutors.directExecutor())
            return future
        }
    }
}
