package com.owlsoda.pageportal.reader.epub

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

data class SmilData(
    val id: String, // Manifest ID of the SMIL file
    val parList: List<SmilPar>
)

data class SmilPar(
    val textSrc: String, // "chapter1.html#para1"
    val audioSrc: String, // "audio/chapter1.mp3"
    val clipBegin: Float, // seconds
    val clipEnd: Float    // seconds
)

object SmilParser {

    fun parse(inputStream: InputStream, smilId: String, baseDir: String): SmilData {
        val parser = Xml.newPullParser().apply {
            setInput(inputStream, null)
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        }

        val parList = mutableListOf<SmilPar>()
        var currentTextSrc: String? = null
        var currentAudioSrc: String? = null
        var currentClipBegin: Float = 0f
        var currentClipEnd: Float = 0f

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "par" -> {
                        currentTextSrc = null
                        currentAudioSrc = null
                        currentClipBegin = 0f
                        currentClipEnd = 0f
                    }
                    "text" -> {
                        val src = parser.getAttributeValue(null, "src")
                        currentTextSrc = resolvePath(baseDir, src)
                    }
                    "audio" -> {
                        val src = parser.getAttributeValue(null, "src")
                        currentAudioSrc = resolvePath(baseDir, src)
                        
                        val beginStr = parser.getAttributeValue(null, "clipBegin")
                        val endStr = parser.getAttributeValue(null, "clipEnd")
                        
                        currentClipBegin = parseSmilTime(beginStr)
                        currentClipEnd = parseSmilTime(endStr)
                    }
                }
            } else if (parser.eventType == XmlPullParser.END_TAG) {
                if (parser.name == "par") {
                    if (currentTextSrc != null && currentAudioSrc != null) {
                        parList.add(
                            SmilPar(
                                textSrc = currentTextSrc!!,
                                audioSrc = currentAudioSrc!!,
                                clipBegin = currentClipBegin,
                                clipEnd = currentClipEnd
                            )
                        )
                    }
                }
            }
        }

        return SmilData(smilId, parList)
    }
    
    private fun resolvePath(baseDir: String, src: String?): String? {
        if (src == null) return null
        if (src.startsWith("/") || src.contains(":")) return src // Absolute or protocol
        
        val separator = if (baseDir.isNotEmpty() && !baseDir.endsWith("/")) "/" else ""
        return baseDir + separator + src
    }

    private fun parseSmilTime(timeStr: String?): Float {
        if (timeStr == null) return 0f
        // Formats: "123.45s", "00:05:00", "00:05:00.500"
        
        try {
            val clean = timeStr.trim()
            if (clean.endsWith("s") || clean.endsWith("ms")) {
                // simple seconds/ms format
                val value = clean.replace(Regex("[a-zA-Z]"), "").toFloatOrNull() ?: 0f
                return if (clean.endsWith("ms")) value / 1000f else value
            } else if (clean.contains(":")) {
                // HH:MM:SS or MM:SS
                val parts = clean.split(":").map { it.toFloatOrNull() ?: 0f }
                if (parts.size == 3) {
                    return parts[0] * 3600 + parts[1] * 60 + parts[2]
                } else if (parts.size == 2) {
                    return parts[0] * 60 + parts[1]
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0f
    }
}
