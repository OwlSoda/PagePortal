package com.owlsoda.pageportal.services.storyteller

import android.util.Log
import com.owlsoda.pageportal.services.*

fun BookResponse.toServiceBookSafe(baseUrl: String?, authToken: String?): ServiceBook? {
    return try {
        ServiceBook(
            serviceType = ServiceType.STORYTELLER,
            serviceId = uuid,
            title = title,
            authors = authors?.map { it.name } ?: emptyList(),
            narrators = narrators?.map { it.name } ?: emptyList(),
            series = series?.firstOrNull()?.name,
            seriesIndex = series?.firstOrNull()?.seriesIndex?.toFloatOrNull(),
            coverUrl = if (baseUrl != null) "$baseUrl/api/v2/books/$uuid/cover" else "",
            audiobookCoverUrl = if (baseUrl != null && audiobook != null) "$baseUrl/api/v2/books/$uuid/cover?format=square" else null,
            hasEbook = ebook != null,
            hasAudiobook = audiobook != null,
            hasReadAloud = readaloud != null && (readaloud.status == "completed" || readaloud.status == "ready" || !readaloud.filepath.isNullOrBlank()),
            description = description,
            publishedYear = publicationDate?.take(4)?.toIntOrNull(),
            tags = tags?.map { it.name } ?: emptyList(),
            collections = collections?.map { CollectionRef(it.uuid, it.name) } ?: emptyList()
        )
    } catch (e: Exception) {
        Log.e("StorytellerMapping", "Failed to map book: $title ($uuid)", e)
        null
    }
}
