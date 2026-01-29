package com.owlsoda.pageportal.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.owlsoda.pageportal.services.ServiceType

/**
 * Represents a connected server in the local database.
 * Stores authentication tokens and connection details for each service.
 */
@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val serviceType: String,  // ServiceType name
    val serverUrl: String,
    val username: String,
    val authToken: String?,
    val userId: String?,
    val displayName: String,  // User-provided name or auto-generated
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSyncAt: Long? = null
) {
    fun toServiceType(): ServiceType = ServiceType.valueOf(serviceType)
}
