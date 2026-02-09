package com.owlsoda.pageportal.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("bookId")
    ]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val chapterId: String,        // Chapter identifier from EPUB
    val fragmentId: String,        // SMIL fragment ID for precise location
    val audioPosition: Long,       // Audio position in milliseconds
    val note: String? = null,      // Optional user note
    val createdAt: Long = System.currentTimeMillis()
)
