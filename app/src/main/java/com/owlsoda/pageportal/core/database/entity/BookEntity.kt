package com.owlsoda.pageportal.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local cache of books from all connected services.
 * Each book is associated with a specific server.
 */
@Entity(
    tableName = "books",
    foreignKeys = [
        ForeignKey(
            entity = ServerEntity::class,
            parentColumns = ["id"],
            childColumns = ["serverId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UnifiedBookEntity::class,
            parentColumns = ["id"],
            childColumns = ["unifiedBookId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("serverId"),
        Index("serviceBookId"),
        Index("unifiedBookId"),
        Index(value = ["serverId", "serviceBookId"], unique = true)
    ]
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val serverId: Long,
    val unifiedBookId: Long? = null,
    val isManuallyLinked: Boolean = false,
    val serviceBookId: String,  // The ID used by the remote service
    val title: String,
    val authors: String,  // JSON array of author names
    val narrators: String = "[]",  // JSON array of narrator names
    val description: String? = null,
    val coverUrl: String? = null,
    val series: String? = null,
    val seriesIndex: String? = null,
    val hasEbook: Boolean = false,
    val hasAudiobook: Boolean = false,
    val hasReadAloud: Boolean = false,
    val duration: Long? = null,  // Audiobook duration in seconds
    val publishedYear: Int? = null,
    val isbn: String? = null,
    val asin: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastSyncAt: Long = System.currentTimeMillis(),
    
    // Download Tracking (per format)
    val isAudiobookDownloaded: Boolean = false,
    val isEbookDownloaded: Boolean = false,
    val isReadAloudDownloaded: Boolean = false,
    
    // Download URLs (from server response)
    val audiobookUrl: String? = null,
    val ebookUrl: String? = null,
    val syncedUrl: String? = null,  // ReadAloud/sync URL
    
    // Processing status (for ReadAloud generation)
    val processingStatus: String? = null,  // queued, processing, completed, failed
    val processingStage: String? = null,
    val processingProgress: Float? = null,
    
    // Legacy fields (kept for migration compatibility)
    val downloadStatus: String = "NONE", // Enum name: NONE, QUEUED, DOWNLOADING, COMPLETED, FAILED
    val localFilePath: String? = null,
    val downloadProgress: Float = 0f  // 0.0 to 1.0
)
