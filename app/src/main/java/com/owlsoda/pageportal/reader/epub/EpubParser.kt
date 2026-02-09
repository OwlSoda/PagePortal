package com.owlsoda.pageportal.reader.epub

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

data class EpubBook(
    val title: String,
    val author: String,
    val coverImage: String?, // Relative path to cover
    val chapters: List<EpubChapter>,
    val cssFiles: List<String>,
    val basePath: String, // Path inside zip where OPF is located
    val smilData: Map<String, SmilData> = emptyMap() // Map of manifest ID to SmilData
)

data class EpubChapter(
    val id: String,
    val href: String,
    val title: String?,
    val mediaOverlayId: String? = null
)

data class SmilData(
    val pars: List<SmilPar>
)

data class SmilPar(
    val id: String?,
    val textSrc: String, // Resolved path inside ZIP (e.g., OEBPS/chapter1.xhtml#p1)
    val audioSrc: String, // Resolved path inside ZIP (e.g., OEBPS/audio/chapter1.mp3)
    val clipBegin: Double, // seconds
    val clipEnd: Double // seconds
)

class EpubParser(
    private val xmlParserProvider: XmlParserProvider = AndroidXmlParserProvider()
) {

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

            val resourceLoader: (String) -> InputStream? = { path ->
                 zip.getEntry(path)?.let { zip.getInputStream(it) }
            }

            val opfResult = parseOpf(zip.getInputStream(opfEntry), basePath, resourceLoader)
            
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
        val parser = xmlParserProvider.getParser().apply {
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

    private fun parseOpf(
        inputStream: InputStream,
        basePath: String,
        resourceLoader: (String) -> InputStream?
    ): EpubBook {
        val parser = xmlParserProvider.getParser().apply {
            setInput(inputStream, null)
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        }

        var title = "Unknown Title"
        var author = "Unknown Author"
        val manifest = mutableMapOf<String, String>() // ID -> Href
        val manifestMediaOverlays = mutableMapOf<String, String>() // ID -> MediaOverlay ID
        val spine = mutableListOf<String>() // ID references
        val cssFiles = mutableListOf<String>()
        var coverId: String? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "dc:title", "title" -> {
                        if (parser.next() == XmlPullParser.TEXT) title = parser.text
                    }
                    "dc:creator", "creator" -> {
                        if (parser.next() == XmlPullParser.TEXT) author = parser.text
                    }
                    "item" -> {
                        val id = parser.getAttributeValue(null, "id")
                        val href = parser.getAttributeValue(null, "href")
                        val mediaType = parser.getAttributeValue(null, "media-type")
                        val mediaOverlay = parser.getAttributeValue(null, "media-overlay")
                        
                        if (id != null && href != null) {
                            manifest[id] = basePath + href
                            if (mediaType == "text/css") {
                                cssFiles.add(basePath + href)
                            }
                            if (mediaOverlay != null) {
                                manifestMediaOverlays[id] = mediaOverlay
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
                        if (parser.getAttributeValue(null, "name") == "cover") {
                            coverId = parser.getAttributeValue(null, "content")
                        }
                    }
                }
            }
        }
        
        // Build chapters from spine
        val chapters = spine.mapNotNull { idref ->
            manifest[idref]?.let { href ->
                EpubChapter(
                    id = idref,
                    href = href,
                    title = null,
                    mediaOverlayId = manifestMediaOverlays[idref]
                )
            }
        }
        
        val coverImage = coverId?.let { manifest[it] }

        // Parse SMILs
        val smilDataMap = mutableMapOf<String, SmilData>()
        val usedMediaOverlays = chapters.mapNotNull { it.mediaOverlayId }.distinct()

        for (smilId in usedMediaOverlays) {
            val smilHref = manifest[smilId]
            if (smilHref != null) {
                resourceLoader(smilHref)?.use { stream ->
                     val smilDir = if (smilHref.contains("/")) smilHref.substringBeforeLast("/") + "/" else ""
                     val smilData = parseSmil(stream, smilDir)
                     smilDataMap[smilId] = smilData
                }
            }
        }

        return EpubBook(
            title = title,
            author = author,
            coverImage = coverImage,
            chapters = chapters,
            cssFiles = cssFiles,
            basePath = basePath,
            smilData = smilDataMap
        )
    }

    private fun parseSmil(inputStream: InputStream, smilDir: String): SmilData {
        val parser = xmlParserProvider.getParser().apply {
             setInput(inputStream, null)
             setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        }

        val pars = mutableListOf<SmilPar>()

        var currentParId: String? = null
        var textSrc: String? = null
        var audioSrc: String? = null
        var clipBegin: Double = 0.0
        var clipEnd: Double = 0.0

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "par" -> {
                        currentParId = parser.getAttributeValue(null, "id")
                        textSrc = null
                        audioSrc = null
                        clipBegin = 0.0
                        clipEnd = 0.0
                    }
                    "text" -> {
                        val src = parser.getAttributeValue(null, "src")
                        if (src != null) {
                            textSrc = resolvePath(smilDir, src)
                        }
                    }
                    "audio" -> {
                        val src = parser.getAttributeValue(null, "src")
                        if (src != null) {
                            audioSrc = resolvePath(smilDir, src)
                        }
                        clipBegin = parseClockValue(parser.getAttributeValue(null, "clipBegin"))
                        clipEnd = parseClockValue(parser.getAttributeValue(null, "clipEnd"))
                    }
                }
            } else if (parser.eventType == XmlPullParser.END_TAG) {
                 if (parser.name == "par") {
                     if (textSrc != null && audioSrc != null) {
                     pars.add(SmilPar(currentParId, textSrc, audioSrc, clipBegin, clipEnd))
                     }
                 }
            }
        }
        return SmilData(pars)
    }

    private fun resolvePath(baseDir: String, relativePath: String): String {
        if (relativePath.startsWith("/")) return relativePath.substring(1)

        // Remove fragment from relative path for resolution, then append it back?
        // Wait, textSrc has fragment. audioSrc doesn't.
        val fragmentIndex = relativePath.indexOf('#')
        val pathPart = if (fragmentIndex >= 0) relativePath.substring(0, fragmentIndex) else relativePath
        val fragmentPart = if (fragmentIndex >= 0) relativePath.substring(fragmentIndex) else ""

        // Resolve pathPart against baseDir
        // baseDir ends with "/"
        val parts = (baseDir + pathPart).split("/")
        val result = mutableListOf<String>()
        for (part in parts) {
            if (part == "..") {
                if (result.isNotEmpty()) result.removeAt(result.lastIndex)
            } else if (part != "." && part.isNotEmpty()) {
                result.add(part)
            }
        }
        return result.joinToString("/") + fragmentPart
    }

    private fun parseClockValue(value: String?): Double {
        if (value == null) return 0.0
        return try {
            if (value.endsWith("s")) {
                value.dropLast(1).toDouble()
            } else if (value.contains(":")) {
                val parts = value.split(":")
                if (parts.size == 3) {
                    parts[0].toDouble() * 3600 + parts[1].toDouble() * 60 + parts[2].toDouble()
                } else if (parts.size == 2) {
                     parts[0].toDouble() * 60 + parts[1].toDouble()
                } else {
                    0.0
                }
            } else {
                value.toDouble()
            }
        } catch (e: Exception) {
            0.0
        }
    }
}
