package com.owlsoda.pageportal.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unified_books")
data class UnifiedBookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val authors: String, // JSON array
    val series: String? = null,
    val seriesIndex: String? = null,
    val coverUrl: String? = null,
    val audiobookCoverUrl: String? = null,
    val description: String? = null,
    val tags: String? = null, // JSON array
    val lastUpdated: Long = System.currentTimeMillis()
)
