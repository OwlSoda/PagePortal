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
    val lastSyncAt: Long = System.currentTimeMillis()
)
