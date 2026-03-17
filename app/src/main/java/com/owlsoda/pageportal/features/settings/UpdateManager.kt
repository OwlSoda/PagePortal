package com.owlsoda.pageportal.features.settings

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.owlsoda.pageportal.BuildConfig
import com.owlsoda.pageportal.network.GitHubUpdateService
import com.owlsoda.pageportal.network.GitHubRelease
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpdateAvailable(val release: GitHubRelease) : UpdateState()
    object NoUpdate : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gitHubUpdateService: GitHubUpdateService
) {
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState = _updateState.asStateFlow()

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var downloadId: Long = -1

    suspend fun checkForUpdates() {
        _updateState.value = UpdateState.Checking
        try {
            val latestRelease = gitHubUpdateService.getLatestRelease()
            val currentVersion = BuildConfig.VERSION_NAME
            
            if (isNewerVersion(latestRelease.tagName, currentVersion)) {
                _updateState.value = UpdateState.UpdateAvailable(latestRelease)
            } else {
                _updateState.value = UpdateState.NoUpdate
            }
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error(e.message ?: "Failed to check for updates")
        }
    }

    fun downloadAndInstall(release: GitHubRelease) {
        val asset = release.assets.find { it.name.endsWith(".apk") }
            ?: return _updateState.update { UpdateState.Error("No APK found in release") }

        val request = DownloadManager.Request(Uri.parse(asset.downloadUrl))
            .setTitle("PagePortal Update")
            .setDescription("Downloading version ${release.tagName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "PagePortal-${release.tagName}.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        downloadId = downloadManager.enqueue(request)
        _updateState.value = UpdateState.Downloading(0f)

        // Register receiver for completion
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    installApk(release.tagName)
                    context.unregisterReceiver(this)
                }
            }
        }
        
        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
    }

    private fun installApk(tagName: String) {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PagePortal-$tagName.apk")
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestClean = latest.removePrefix("v").split(".")
        val currentClean = current.removePrefix("v").split(".")
        
        for (i in 0 until minOf(latestClean.size, currentClean.size)) {
            val l = latestClean[i].toIntOrNull() ?: 0
            val c = currentClean[i].toIntOrNull() ?: 0
            if (l > c) return true
            if (l < c) return false
        }
        return latestClean.size > currentClean.size
    }

    // Extension helper for state update
    private inline fun <T> MutableStateFlow<T>.update(function: (T) -> T) {
        val prevValue = value
        value = function(prevValue)
    }
}
