package com.owlsoda.pageportal.services.audiobookshelf

import com.google.gson.annotations.SerializedName
import retrofit2.http.*

/**
 * Retrofit interface for the Audiobookshelf API.
 * https://api.audiobookshelf.org/
 */
interface AudiobookshelfApi {
    
    // ===== Authentication =====
    
    @POST("login")
    suspend fun login(
        @Body request: AbsLoginRequest
    ): AbsLoginResponse

    @GET("status")
    suspend fun getStatus(): AbsStatusResponse

    @GET("auth/openid/callback")
    suspend fun oauthCallback(
        @Query("state") state: String,
        @Query("code") code: String,
        @Query("code_verifier") verifier: String
    ): AbsLoginResponse
    
    // ===== Libraries =====
    
    @GET("api/libraries")
    suspend fun getLibraries(
        @Header("Authorization") token: String
    ): AbsLibrariesResponse
    
    @GET("api/libraries/{libraryId}/items")
    suspend fun getLibraryItems(
        @Header("Authorization") token: String,
        @Path("libraryId") libraryId: String,
        @Query("limit") limit: Int = 100,
        @Query("page") page: Int = 0,
        @Query("minified") minified: Int = 0,
        @Query("include") include: String = "progress",
        @Query("filter") filter: String? = null,
        @Query("sort") sort: String = "media.metadata.title",
        @Query("desc") desc: Int = 0
    ): AbsLibraryItemsResponse
    
    // ===== Library Items =====
    
    @GET("api/items/{itemId}")
    suspend fun getItem(
        @Header("Authorization") token: String,
        @Path("itemId") itemId: String,
        @Query("expanded") expanded: Int = 1,
        @Query("include") include: String = "progress"
    ): AbsLibraryItem
    
    @GET("api/items/{itemId}/cover")
    fun getCoverUrl(
        @Path("itemId") itemId: String
    ): String
    
    // ===== Playback =====
    
    @POST("api/items/{itemId}/play")
    suspend fun startPlayback(
        @Header("Authorization") token: String,
        @Path("itemId") itemId: String,
        @Body request: AbsPlayRequest
    ): AbsPlaybackSession
    
    @POST("api/items/{itemId}/play/{episodeId}")
    suspend fun startPodcastPlayback(
        @Header("Authorization") token: String,
        @Path("itemId") itemId: String,
        @Path("episodeId") episodeId: String,
        @Body request: AbsPlayRequest
    ): AbsPlaybackSession
    
    // ===== Progress =====
    
    @GET("api/me/progress/{itemId}")
    suspend fun getProgress(
        @Header("Authorization") token: String,
        @Path("itemId") itemId: String
    ): AbsMediaProgress
    
    @PATCH("api/me/progress/{itemId}")
    suspend fun updateProgress(
        @Header("Authorization") token: String,
        @Path("itemId") itemId: String,
        @Body progress: AbsProgressUpdate
    )
    
    @POST("api/session/{sessionId}/sync")
    suspend fun syncSession(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String,
        @Body sync: AbsSessionSync
    )
    
    @POST("api/session/{sessionId}/close")
    suspend fun closeSession(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String
    )
    
    // ===== User =====
    
    @GET("api/me")
    suspend fun getMe(
        @Header("Authorization") token: String
    ): AbsUser
    
    @GET("api/me/items-in-progress")
    suspend fun getItemsInProgress(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 25
    ): AbsItemsInProgressResponse
    
    // ===== Search =====
    
    @GET("api/libraries/{libraryId}/search")
    suspend fun search(
        @Header("Authorization") token: String,
        @Path("libraryId") libraryId: String,
        @Query("q") query: String,
        @Query("limit") limit: Int = 25
    ): AbsSearchResponse
}

// ===== Request/Response Models =====

data class AbsLoginRequest(
    val username: String,
    val password: String
)

data class AbsLoginResponse(
    val user: AbsUser,
    val userDefaultLibraryId: String?,
    val serverSettings: AbsServerSettings?
)

data class AbsUser(
    val id: String,
    val username: String,
    val type: String,
    val token: String,
    val mediaProgress: List<AbsMediaProgress>? = null,
    val isActive: Boolean = true
)

data class AbsServerSettings(
    val id: String?,
    val serverName: String? = null,
    val authOpenIDAutoLaunch: Boolean = false,
    val authOpenIDEnabled: Boolean = false
)

data class AbsStatusResponse(
    val serverSettings: AbsServerSettings
)

data class AbsLibrariesResponse(
    val libraries: List<AbsLibrary>
)

data class AbsLibrary(
    val id: String,
    val name: String,
    val folders: List<AbsFolder>,
    val displayOrder: Int,
    val icon: String?,
    val mediaType: String, // "book" or "podcast"
    val provider: String?,
    val settings: AbsLibrarySettings?,
    val createdAt: Long,
    val lastUpdate: Long
)

data class AbsFolder(
    val id: String,
    val fullPath: String,
    val libraryId: String
)

data class AbsLibrarySettings(
    val coverAspectRatio: Int = 1,
    val disableWatcher: Boolean = false
)

data class AbsLibraryItemsResponse(
    val results: List<AbsLibraryItem>,
    val total: Int,
    val limit: Int,
    val page: Int,
    val sortBy: String?,
    val sortDesc: Boolean?,
    val filterBy: String?,
    val mediaType: String
)

data class AbsLibraryItem(
    val id: String,
    @SerializedName("ino") val inode: String?,
    val libraryId: String,
    val folderId: String?,
    val path: String?,
    val relPath: String?,
    val isFile: Boolean = false,
    val mtimeMs: Long?,
    val ctimeMs: Long?,
    val addedAt: Long?,
    val updatedAt: Long?,
    val isMissing: Boolean = false,
    val isInvalid: Boolean = false,
    val mediaType: String, // "book" or "podcast"
    val media: AbsMedia,
    val numFiles: Int? = null,
    val size: Long? = null,
    val userMediaProgress: AbsMediaProgress? = null
)

data class AbsMedia(
    val metadata: AbsBookMetadata?,
    val coverPath: String?,
    val tags: List<String>? = null,
    val numTracks: Int? = null,
    val numAudioFiles: Int? = null,
    val numChapters: Int? = null,
    val duration: Double? = null,
    val size: Long? = null,
    val ebookFile: AbsEbookFile? = null,
    val chapters: List<AbsChapter>? = null,
    val audioFiles: List<AbsAudioFile>? = null
)

data class AbsBookMetadata(
    val title: String,
    val titleIgnorePrefix: String?,
    val subtitle: String?,
    @SerializedName("authorName") val authorName: String?,
    @SerializedName("authorNameLF") val authorNameLF: String?,
    @SerializedName("narratorName") val narratorName: String?,
    @SerializedName("seriesName") val seriesName: String?,
    val genres: List<String>? = null,
    val publishedYear: String?,
    val publishedDate: String?,
    val publisher: String?,
    val description: String?,
    val isbn: String?,
    val asin: String?,
    val language: String?,
    val explicit: Boolean = false,
    val authors: List<AbsAuthor>? = null,
    val narrators: List<String>? = null,
    val series: List<AbsSeriesSequence>? = null
)

data class AbsAuthor(
    val id: String,
    val name: String
)

data class AbsSeriesSequence(
    val id: String,
    val name: String,
    val sequence: String?
)

data class AbsChapter(
    val id: Int,
    val start: Double,
    val end: Double,
    val title: String
)

data class AbsAudioFile(
    val index: Int,
    val ino: String?,
    val metadata: AbsFileMetadata?,
    val addedAt: Long?,
    val updatedAt: Long?,
    val format: String?,
    val duration: Double?,
    val bitRate: Int?,
    val language: String?,
    val codec: String?,
    val timeBase: String?,
    val channels: Int?,
    val channelLayout: String?,
    val mimeType: String?
)

data class AbsFileMetadata(
    val filename: String?,
    val ext: String?,
    val path: String?,
    val relPath: String?,
    val size: Long?
)

data class AbsEbookFile(
    val ino: String?,
    val metadata: AbsFileMetadata?,
    val ebookFormat: String?,
    val addedAt: Long?,
    val updatedAt: Long?
)

data class AbsMediaProgress(
    val id: String?,
    val libraryItemId: String,
    val episodeId: String? = null,
    val duration: Double,
    val progress: Double, // 0.0 to 1.0
    val currentTime: Double,
    val isFinished: Boolean,
    val hideFromContinueListening: Boolean = false,
    val lastUpdate: Long,
    val startedAt: Long,
    val finishedAt: Long? = null
)

data class AbsProgressUpdate(
    val currentTime: Double? = null,
    val progress: Double? = null,
    val duration: Double? = null,
    val isFinished: Boolean? = null
)

data class AbsPlayRequest(
    val deviceInfo: AbsDeviceInfo,
    val forceDirectPlay: Boolean = true,
    val forceTranscode: Boolean = false,
    val supportedMimeTypes: List<String> = listOf(
        "audio/mpeg",
        "audio/mp4",
        "audio/aac",
        "audio/x-m4a",
        "audio/flac",
        "audio/ogg"
    ),
    val mediaPlayer: String = "PagePortal"
)

data class AbsDeviceInfo(
    val deviceId: String,
    val clientName: String = "PagePortal",
    val clientVersion: String = "1.0.0",
    val manufacturer: String? = null,
    val model: String? = null,
    val sdkVersion: Int? = null
)

data class AbsPlaybackSession(
    val id: String,
    val userId: String,
    val libraryId: String,
    val libraryItemId: String,
    val episodeId: String? = null,
    val mediaType: String,
    val displayTitle: String,
    val displayAuthor: String?,
    val coverPath: String?,
    val duration: Double,
    val playMethod: Int, // 0 = direct play, 1 = direct stream, 2 = transcode
    val mediaPlayer: String?,
    val deviceInfo: AbsDeviceInfo?,
    val currentTime: Double,
    val startTime: Double,
    val audioTracks: List<AbsAudioTrack>? = null
)

data class AbsAudioTrack(
    val index: Int,
    val startOffset: Double,
    val duration: Double,
    val title: String?,
    val contentUrl: String,
    val mimeType: String,
    val metadata: AbsAudioTrackMetadata?
)

data class AbsAudioTrackMetadata(
    val filename: String?,
    val ext: String?,
    val path: String?,
    val relPath: String?
)

data class AbsSessionSync(
    val currentTime: Double,
    val timeListened: Double,
    val duration: Double
)

data class AbsItemsInProgressResponse(
    val libraryItems: List<AbsLibraryItem>
)

data class AbsSearchResponse(
    val book: List<AbsSearchResult>? = null,
    val podcast: List<AbsSearchResult>? = null,
    val authors: List<AbsAuthor>? = null,
    val series: List<AbsSeriesSequence>? = null,
    val tags: List<String>? = null
)

data class AbsSearchResult(
    val libraryItem: AbsLibraryItem,
    val matchKey: String?,
    val matchText: String?
)
