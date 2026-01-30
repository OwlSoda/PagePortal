package com.owlsoda.pageportal.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "collections",
    foreignKeys = [
        ForeignKey(
            entity = ServerEntity::class,
            parentColumns = ["id"],
            childColumns = ["serverId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("serverId"),
        Index(value = ["serverId", "serviceId"], unique = true)
    ]
)
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val serverId: Long,
    val serviceId: String, // ID from the remote service
    val name: String,
    val description: String? = null,
    val bookIds: String, // JSON array of serviceBookIds (or unified IDs? Service IDs are safer for initial sync)
    val lastSync: Long = System.currentTimeMillis()
)
