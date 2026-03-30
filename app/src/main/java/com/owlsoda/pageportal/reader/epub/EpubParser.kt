package com.owlsoda.pageportal.reader.epub

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class EpubBook(
    val title: String,
    val author: String,
    val coverImage: String?, // Relative path to cover
    val chapters: List<EpubChapter>,
    val cssFiles: List<String>,
    val basePath: String, // Path inside zip where OPF is located
    val hasMediaOverlays: Boolean = false,
    val smilData: Map<String, SmilData> = emptyMap(), // ID -> SmilData
    val description: String? = null,
    val series: String? = null,
    val seriesIndex: String? = null,
    val tags: List<String> = emptyList()
)

data class EpubChapter(
    val id: String,
    val href: String,
    val title: String?
)

class EpubParser {

    private var zipFile: ZipFile? = null
    private val resources = mutableMapOf<String, String>() // ID -> Href

    suspend fun parse(file: File): Result<EpubBook> = withContext(Dispatchers.IO) {
        try {
            val zip = ZipFile(file)
            zipFile = zip
            
            // 1. Find OPF path from META-INF/container.xml
            val opfPath = getOpfPath(zip) ?: return@withContext Result.failure(Exception("Invalid EPUB: No OPF found"))
            val basePath = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""
            
            // 2. Parse OPF
            val opfEntry = zip.getEntry(opfPath) ?: return@withContext Result.failure(Exception("OPF file missing"))
            val opfResult = parseOpf(zip.getInputStream(opfEntry), basePath, zip)
            
            Result.success(opfResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getInputStream(path: String): InputStream? {
        return zipFile?.getEntry(path)?.let { zipFile?.getInputStream(it) }
    }
    
    fun close() {
        zipFile?.close()
    }

    private fun getOpfPath(zip: ZipFile): String? {
        val containerEntry = zip.getEntry("META-INF/container.xml") ?: return null
        val parser = Xml.newPullParser().apply {
            setInput(containerEntry.let { zip.getInputStream(it) }, null)
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        }

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                return parser.getAttributeValue(null, "full-path")
            }
        }
        return null
    }

    private fun parseOpf(inputStream: InputStream, basePath: String, zip: ZipFile): EpubBook {
        val parser = Xml.newPullParser().apply {
            setInput(inputStream, null)
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        }

        var title = "Unknown Title"
        var author = "Unknown Author"
        val manifest = mutableMapOf<String, String>() // ID -> Href
        val spine = mutableListOf<String>() // ID references
        val cssFiles = mutableListOf<String>()
        val smilFiles = mutableMapOf<String, String>() // ID -> Href
        val chapterSmilMap = mutableMapOf<String, String>() // Chapter ID -> SMIL ID
        var coverId: String? = null
        var hasMediaOverlays = false
        var ncxHref: String? = null // NCX file for chapter titles
        var description: String? = null
        var series: String? = null
        var seriesIndex: String? = null
        val tags = mutableListOf<String>()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "dc:title", "title" -> {
                        if (parser.next() == XmlPullParser.TEXT) title = parser.text
                    }
                    "dc:creator", "creator" -> {
                        if (parser.next() == XmlPullParser.TEXT) author = parser.text
                    }
                    "dc:description", "description" -> {
                        if (parser.next() == XmlPullParser.TEXT) description = parser.text
                    }
                    "dc:subject", "subject" -> {
                        if (parser.next() == XmlPullParser.TEXT) tags.add(parser.text)
                    }
                    "item" -> {
                        val id = parser.getAttributeValue(null, "id")
                        val href = parser.getAttributeValue(null, "href")
                        val mediaType = parser.getAttributeValue(null, "media-type")
                        val mediaOverlay = parser.getAttributeValue(null, "media-overlay")
                        
                        if (id != null && href != null) {
                            val fullPath = basePath + href
                            manifest[id] = fullPath
                            if (mediaType == "text/css") {
                                cssFiles.add(fullPath)
                            }
                            if (mediaType == "application/smil+xml") {
                                hasMediaOverlays = true
                                smilFiles[id] = fullPath
                            }
                            if (mediaType == "application/x-dtbncx+xml") {
                                ncxHref = fullPath
                            }
                            
                            if (mediaOverlay != null) {
                                chapterSmilMap[id] = mediaOverlay
                            }
                            
                            // Detect cover
                            if (id.contains("cover") || href.contains("cover")) {
                                if (mediaType?.startsWith("image/") == true) {
                                    coverId = id // Rough heuristic
                                }
                            }
                        }
                    }
                    "itemref" -> {
                        val idref = parser.getAttributeValue(null, "idref")
                        if (idref != null) spine.add(idref)
                    }
                    "meta" -> {
                        val property = parser.getAttributeValue(null, "property")
                        val name = parser.getAttributeValue(null, "name")
                        val content = parser.getAttributeValue(null, "content")
                        
                        if (name == "cover") {
                            coverId = content
                        }
                        
                        // Series parsing (standard EPUB 3/Calibre meta)
                        when (property ?: name) {
                            "belongs-to-collection", "calibre:series" -> {
                                series = content ?: if (parser.next() == XmlPullParser.TEXT) parser.text else null
                            }
                            "group-position", "calibre:series_index" -> {
                                seriesIndex = content ?: if (parser.next() == XmlPullParser.TEXT) parser.text else null
                            }
                        }
                    }
                }
            }
        }
        
        // Parse NCX for chapter titles
        val chapterTitles = mutableMapOf<String, String>() // href -> title
        if (ncxHref != null) {
            try {
                val ncxEntry = zip.getEntry(ncxHref)
                if (ncxEntry != null) {
                    val ncxParser = Xml.newPullParser().apply {
                        setInput(zip.getInputStream(ncxEntry), null)
                        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    }
                    var currentTitle: String? = null
                    var inNavLabel = false
                    while (ncxParser.next() != XmlPullParser.END_DOCUMENT) {
                        when (ncxParser.eventType) {
                            XmlPullParser.START_TAG -> {
                                when (ncxParser.name) {
                                    "navLabel" -> inNavLabel = true
                                    "text" -> {
                                        if (inNavLabel && ncxParser.next() == XmlPullParser.TEXT) {
                                            currentTitle = ncxParser.text
                                        }
                                    }
                                    "content" -> {
                                        val src = ncxParser.getAttributeValue(null, "src")
                                        if (src != null && currentTitle != null) {
                                            // Normalize: remove fragment, resolve path
                                            val hrefNoFragment = src.substringBefore("#")
                                            val fullHref = basePath + hrefNoFragment
                                            chapterTitles[fullHref] = currentTitle!!
                                            android.util.Log.d("EpubParser", "NCX title: '$currentTitle' -> '$fullHref'")
                                        }
                                    }
                                }
                            }
                            XmlPullParser.END_TAG -> {
                                when (ncxParser.name) {
                                    "navLabel" -> inNavLabel = false
                                    "navPoint" -> currentTitle = null
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("EpubParser", "Error parsing NCX: ${e.message}")
            }
        }
        
        // Build chapters from spine
        val chapters = spine.mapNotNull { idref ->
            manifest[idref]?.let { href ->
                EpubChapter(
                    id = idref,
                    href = href,
                    title = chapterTitles[href] // Look up title from NCX
                )
            }
        }
        
        // Parse SMIL files and map to Chapter ID
        // The smilData definition in EpubBook is: val smilData: Map<String, SmilData>
        // We want the key to be the Chapter ID (HTML ID) so ViewModel can look it up easily.
        
        val smilDataMap = mutableMapOf<String, SmilData>()
        
        if (hasMediaOverlays) {
            android.util.Log.d("EpubParser", "=== SMIL Mapping Debug ===")
            android.util.Log.d("EpubParser", "smilFiles (${smilFiles.size}): ${smilFiles.entries.joinToString { "${it.key} -> ${it.value}" }}")
            android.util.Log.d("EpubParser", "chapterSmilMap (${chapterSmilMap.size}): ${chapterSmilMap.entries.joinToString { "${it.key} -> ${it.value}" }}")
            android.util.Log.d("EpubParser", "spine (${spine.size}): ${spine.joinToString()}")
            
            // first parse all SMIL files by their own ID
            val rawSmilData = mutableMapOf<String, SmilData>()
            
            for ((smilId, href) in smilFiles) {
                try {
                    val entry = zip.getEntry(href)
                    if (entry != null) {
                        // Calculate base directory for this SMIL file
                        val smilDir = if (href.contains("/")) href.substringBeforeLast("/") else ""
                        
                        // We use the SMIL ID for internal storage
                        val data = SmilParser.parse(zip.getInputStream(entry), smilId, smilDir)
                        rawSmilData[smilId] = data
                        android.util.Log.d("EpubParser", "Parsed SMIL '$smilId' from '$href': ${data.parList.size} pars")
                    } else {
                        android.util.Log.e("EpubParser", "SMIL zip entry NOT FOUND: '$href'")
                        // Try without basePath prefix in case it's already included
                        val altHref = href.removePrefix(basePath)
                        val altEntry = zip.getEntry(altHref)
                        if (altEntry != null) {
                            val smilDir = if (altHref.contains("/")) altHref.substringBeforeLast("/") else ""
                            val data = SmilParser.parse(zip.getInputStream(altEntry), smilId, smilDir)
                            rawSmilData[smilId] = data
                            android.util.Log.d("EpubParser", "Found SMIL via alt path '$altHref': ${data.parList.size} pars")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("EpubParser", "Error parsing SMIL '$smilId': ${e.message}")
                    e.printStackTrace()
                }
            }
            
            // Now map to Chapter IDs
            for ((chapterId, smilId) in chapterSmilMap) {
                val data = rawSmilData[smilId]
                if (data != null) {
                    smilDataMap[chapterId] = data
                    android.util.Log.d("EpubParser", "Mapped chapter '$chapterId' -> SMIL '$smilId' (${data.parList.size} pars)")
                } else {
                    android.util.Log.e("EpubParser", "No raw SMIL data for smilId='$smilId' (chapter='$chapterId')")
                }
            }
            
            android.util.Log.d("EpubParser", "Final smilDataMap keys (${smilDataMap.size}): ${smilDataMap.keys.joinToString()}")
        }
        
        val coverImage = coverId?.let { manifest[it] }

        return EpubBook(
            title = title,
            author = author,
            coverImage = coverImage,
            chapters = chapters,
            cssFiles = cssFiles,
            basePath = basePath,
            hasMediaOverlays = hasMediaOverlays,
            smilData = smilDataMap,
            description = description,
            series = series,
            seriesIndex = seriesIndex,
            tags = tags
        )
    }
}
