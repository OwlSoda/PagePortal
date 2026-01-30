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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.owlsoda.pageportal.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * Media3 playback service for audiobook playback.
 * Handles background playback, media notifications, and audio focus.
 */
@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    
    @Inject lateinit var okHttpClient: OkHttpClient
    
    private var mediaSession: MediaSession? = null
    private var bitmapLoaderExecutor: java.util.concurrent.ExecutorService? = null
    
    // No longer needed as AuthInterceptor handles it globally
    // private var authToken: String? = null
    
    fun setAuthToken(token: String?) {
        // Keeping for compatibility but delegating to interceptor logic
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Configure renderers with extension support
        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            setEnableDecoderFallback(true)
        }
        
        val trackSelector = DefaultTrackSelector(this)
        
        // Buffer configuration for audiobooks
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(20000, 60000, 2000, 3000)
            .build()
        
        // Data source with global auth interceptor support
        val dataSourceFactory = DataSource.Factory {
            val okHttpFactory = OkHttpDataSource.Factory(okHttpClient)
            val defaultFactory = DefaultDataSource.Factory(this, okHttpFactory)
            defaultFactory.createDataSource()
        }
        
        // Extractors with constant bitrate seeking for audiobooks
        val extractorsFactory = DefaultExtractorsFactory()
            .setAdtsExtractorFlags(AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
        
        // Build the ExoPlayer
        val player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this, extractorsFactory)
                    .setDataSourceFactory(dataSourceFactory)
            )
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // Handle audio focus
            )
            .setHandleAudioBecomingNoisy(true) // Pause on headphone disconnect
            .build()
        
        // Wrap player to provide lean metadata for notifications
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
        
        // Add logging listener for debugging
        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                android.util.Log.d("PlaybackService", "Tracks changed: ${tracks.groups.size} groups")
            }
            
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("PlaybackService", "Playback error (code ${error.errorCode}): ${error.message}")
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_READY -> "READY"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_ENDED -> "ENDED"
                    Player.STATE_IDLE -> "IDLE"
                    else -> "UNKNOWN"
                }
                android.util.Log.d("PlaybackService", "Playback state: $stateName")
            }
        })
        
        // Set up media notification
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build()
        )
        
        // Build media session
        mediaSession = MediaSession.Builder(this, leanPlayer)
            .setSessionActivity(createSessionActivity())
            .build()
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
        mediaSession?.player?.let { player ->
            player.pause()
            player.stop()
        }
        stopSelf()
    }
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }
    
    override fun onDestroy() {
        bitmapLoaderExecutor?.shutdown()
        mediaSession?.run {
            player.release()
            release()
        }
        super.onDestroy()
    }
    
    companion object {
        /**
         * Build a MediaController future for connecting to the PlaybackService.
         */
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
