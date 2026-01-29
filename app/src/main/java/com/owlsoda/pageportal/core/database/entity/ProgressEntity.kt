package com.owlsoda.pageportal.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores reading/listening progress for books.
 * Synced with remote services and used for offline access.
 */
@Entity(
    tableName = "progress",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId", unique = true)]
)
data class ProgressEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val currentPosition: Long = 0,  // Milliseconds for audio, position for ebook
    val currentChapter: Int = 0,
    val percentComplete: Float = 0f,
    val isFinished: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null,  // When last synced to remote
    val locatorJson: String? = null  // Full locator for precise position
)
