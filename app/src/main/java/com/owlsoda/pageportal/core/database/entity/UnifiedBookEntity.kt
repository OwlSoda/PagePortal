package com.owlsoda.pageportal.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unified_books")
data class UnifiedBookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String,
    val coverUrl: String? = null,
    val audiobookCoverUrl: String? = null,
    val description: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)
