package com.owlsoda.pageportal.reader.epub

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class EpubParserTest {

    class TestXmlParserProvider : XmlParserProvider {
        override fun getParser(): XmlPullParser {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            return factory.newPullParser()
        }
    }

    @Test
    fun testParse() = runBlocking {
        // Try to instantiate XmlPullParserFactory. If it fails (e.g. not in classpath), we skip the test or fail.
        try {
             XmlPullParserFactory.newInstance()
        } catch (e: Exception) {
             println("Skipping test: XmlPullParserFactory not available: ${e.message}")
             return@runBlocking
        }

        val parser = EpubParser(TestXmlParserProvider())
        val file = File.createTempFile("test", ".epub")
        file.deleteOnExit()

        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write("""
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                    </rootfiles>
                </container>
            """.trimIndent().toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zos.write("""
                <?xml version="1.0" encoding="utf-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="bookid" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test Book</dc:title>
                        <dc:language>en</dc:language>
                    </metadata>
                    <manifest>
                        <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                        <item id="ch1" href="chapter1.xhtml" media-type="application/xhtml+xml" media-overlay="mo1"/>
                        <item id="mo1" href="chapter1.smil" media-type="application/smil+xml"/>
                    </manifest>
                    <spine toc="ncx">
                        <itemref idref="ch1"/>
                    </spine>
                </package>
            """.trimIndent().toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/chapter1.xhtml"))
            zos.write("<html><body><p id=\"p1\">Hello World</p></body></html>".toByteArray())
            zos.closeEntry()

             zos.putNextEntry(ZipEntry("OEBPS/chapter1.smil"))
            zos.write("""
                <smil xmlns="http://www.w3.org/ns/SMIL" version="3.0">
                    <body>
                        <par id="par1">
                            <text src="chapter1.xhtml#p1"/>
                            <audio src="audio/chapter1.mp3" clipBegin="0s" clipEnd="10s"/>
                        </par>
                    </body>
                </smil>
            """.trimIndent().toByteArray())
            zos.closeEntry()
        }

        val result = parser.parse(file)

        if (result.isFailure) {
            println("Parse failed: ${result.exceptionOrNull()}")
            result.exceptionOrNull()?.printStackTrace()
        }

        assertTrue("Parser failed: ${result.exceptionOrNull()}", result.isSuccess)

        val book = result.getOrNull()!!
        assertEquals("Test Book", book.title)
        assertEquals(1, book.chapters.size)

        val chapter = book.chapters[0]
        assertEquals("ch1", chapter.id)
        assertEquals("mo1", chapter.mediaOverlayId)

        assertTrue(book.smilData.containsKey("mo1"))
        val smilData = book.smilData["mo1"]!!
        assertEquals(1, smilData.pars.size)

        val par = smilData.pars[0]
        assertEquals("par1", par.id)
        // Check resolved paths
        assertEquals("OEBPS/chapter1.xhtml#p1", par.textSrc)
        assertEquals("OEBPS/audio/chapter1.mp3", par.audioSrc)
        assertEquals(0.0, par.clipBegin, 0.001)
        assertEquals(10.0, par.clipEnd, 0.001)
    }
}
