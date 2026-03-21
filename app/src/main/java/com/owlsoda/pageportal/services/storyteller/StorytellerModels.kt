package com.owlsoda.pageportal.services.storyteller

import com.google.gson.annotations.SerializedName

/**
 * API response models for Storyteller service.
 * Maps JSON responses to Kotlin data classes.
 */

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String
)

data class BookResponse(
    val uuid: String? = null,
    val id: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val description: String? = null,
    val language: String? = null,
    val rating: Float? = null,
    val suffix: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val publicationDate: String? = null,
    val duration: Float? = null,
    @SerializedName("total_duration") val totalDuration: Float? = null,
    @SerializedName("totalDurationMs") val totalDurationMs: Long? = null,
    val authors: List<AuthorResponse>? = emptyList(),
    val narrators: List<NarratorResponse>? = emptyList(),
    val series: List<SeriesResponse>? = emptyList(),
    val collections: List<CollectionResponse>? = emptyList(),
    val tags: List<TagResponse>? = emptyList(),
    val status: Any? = null,
    val position: Any? = null,
    val audiobook: AudiobookResponse? = null,
    @SerializedName("audioBook") val audioBookField: AudiobookResponse? = null,
    val ebook: EbookResponse? = null,
    @SerializedName("eBook") val eBookField: EbookResponse? = null,
    @SerializedName("readaloud") val readaloud: ReadAloudResponse? = null,
    @SerializedName("readAloud") val readAloudField: ReadAloudResponse? = null
)

data class CollectionResponse(
    val uuid: String? = null,
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class SeriesResponse(
    val uuid: String? = null,
    val id: String? = null,
    val name: String? = null,
    val featured: Any? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    @SerializedName("index") val index: Any? = null,
    @SerializedName("sequence") val sequence: Any? = null,
    @SerializedName("position") val position: Any? = null,
    @SerializedName("series_index") val series_index: Any? = null,
    @SerializedName("seriesIndex") val seriesIndexField: Any? = null
) {
    val seriesIndex: String?
        get() {
            val raw = index ?: sequence ?: position ?: series_index ?: seriesIndexField
            if (raw == null) return null
            
            val s = raw.toString()
            val cleanS = if (s.endsWith(".0")) s.substringBefore(".0") else s
            
            return if (cleanS.isBlank() || cleanS.equals("null", ignoreCase = true)) null else cleanS
        }
}

data class AuthorResponse(
    val uuid: String? = null,
    val id: String? = null,
    val name: String? = null,
    val fileAs: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class NarratorResponse(
    val uuid: String? = null,
    val id: String? = null,
    val name: String? = null,
    val fileAs: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class TagResponse(
    val uuid: String? = null,
    val id: String? = null,
    val name: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class AudiobookResponse(
    val uuid: String? = null,
    val id: String? = null,
    val filepath: String? = null,
    val duration: Float? = null,
    @SerializedName("total_duration") val totalDuration: Float? = null,
    @SerializedName("totalDurationMs") val totalDurationMs: Long? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class EbookResponse(
    val uuid: String? = null,
    val id: String? = null,
    val filepath: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class ReadAloudResponse(
    val uuid: String? = null,
    val id: String? = null,
    val status: String? = null,
    val filepath: String? = null,
    val queuePosition: Int? = null,
    val currentStage: String? = null,
    val stageProgress: Double? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class Position(
    val locator: Locator,
    val timestamp: Long = System.currentTimeMillis()
)

data class Locator(
    val href: String,
    @SerializedName("type") val mediaType: String,
    val title: String? = null,
    val locations: Locations
)

data class Locations(
    val progression: Double? = 0.0,
    val position: Int? = null,
    @SerializedName("totalProgression") val totalProgression: Double? = null,
    val audioTimestampMs: Long? = null,
    val chapterIndex: Int? = null,
    val elementId: String? = null,
    val totalChapters: Int? = null,
    val totalDurationMs: Long? = null
)

data class UserResponse(
    val id: String,
    val name: String,
    val email: String? = null
)

data class MetadataRequest(
    val title: String? = null,
    val description: String? = null,
    val series: String? = null,
    val seriesIndex: Float? = null,
    val authors: List<String>? = null,
    val narrators: List<String>? = null,
    val tags: List<String>? = null
)

