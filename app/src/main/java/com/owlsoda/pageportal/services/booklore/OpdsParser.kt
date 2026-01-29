package com.owlsoda.pageportal.services.booklore

import android.util.Xml
import com.owlsoda.pageportal.services.MediaFormat
import com.owlsoda.pageportal.services.ServiceBook
import com.owlsoda.pageportal.services.ServiceType
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Parses OPDS 1.1/1.2 Atom feeds into ServiceBooks.
 */
class OpdsParser {

    data class OpdsEntry(
        val id: String,
        val title: String,
        val author: String,
        val summary: String?,
        val coverUrl: String?,
        val downloadUrl: String?,
        val format: MediaFormat
    )

    fun parse(inputStream: InputStream, serverUrl: String): List<OpdsEntry> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)
        parser.nextTag()
        return readFeed(parser, serverUrl)
    }

    private fun readFeed(parser: XmlPullParser, serverUrl: String): List<OpdsEntry> {
        val entries = mutableListOf<OpdsEntry>()

        parser.require(XmlPullParser.START_TAG, null, "feed")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            if (parser.name == "entry") {
                readEntry(parser, serverUrl)?.let { entries.add(it) }
            } else {
                skip(parser)
            }
        }
        return entries
    }

    private fun readEntry(parser: XmlPullParser, serverUrl: String): OpdsEntry? {
        parser.require(XmlPullParser.START_TAG, null, "entry")
        
        var id: String? = null
        var title: String? = null
        var author: String? = null
        var summary: String? = null
        var coverUrl: String? = null
        var downloadUrl: String? = null
        var format: MediaFormat = MediaFormat.EBOOK // Default

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            when (parser.name) {
                "id" -> id = readText(parser)
                "title" -> title = readText(parser)
                "author" -> author = readAuthor(parser)
                "summary", "content" -> summary = readText(parser)
                "link" -> {
                    val rel = parser.getAttributeValue(null, "rel")
                    val href = parser.getAttributeValue(null, "href")
                    val type = parser.getAttributeValue(null, "type")
                    
                    if (href != null) {
                        val absoluteHref = resolveUrl(serverUrl, href)
                        
                        when {
                            // Cover image
                            rel?.contains("image") == true || rel?.contains("thumbnail") == true -> {
                                coverUrl = absoluteHref
                            }
                            // Acquisition link
                            rel?.contains("acquisition") == true -> {
                                downloadUrl = absoluteHref
                                format = determineFormat(type)
                            }
                        }
                    }
                    parser.nextTag()
                }
                else -> skip(parser)
            }
        }
        
        return if (id != null && title != null && downloadUrl != null) {
            OpdsEntry(
                id = id,
                title = title,
                author = author ?: "Unknown",
                summary = summary,
                coverUrl = coverUrl,
                downloadUrl = downloadUrl,
                format = format
            )
        } else {
            null
        }
    }

    private fun readAuthor(parser: XmlPullParser): String {
        parser.require(XmlPullParser.START_TAG, null, "author")
        var name = ""
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "name") {
                name = readText(parser)
            } else {
                skip(parser)
            }
        }
        return name
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }
    
    private fun resolveUrl(baseUrl: String, relativePath: String): String {
        if (relativePath.startsWith("http")) return relativePath
        
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val relative = if (relativePath.startsWith("/")) relativePath.substring(1) else relativePath
        return base + relative
    }
    
    private fun determineFormat(mimeType: String?): MediaFormat {
        return when {
            mimeType?.contains("audio") == true -> MediaFormat.AUDIOBOOK
            mimeType?.contains("pdf") == true -> MediaFormat.PDF
            mimeType?.contains("zip") == true || mimeType?.contains("cbz") == true -> MediaFormat.COMIC
            else -> MediaFormat.EBOOK // epub, etc.
        }
    }
}
