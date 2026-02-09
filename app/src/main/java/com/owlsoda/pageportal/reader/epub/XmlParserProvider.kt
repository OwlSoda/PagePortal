package com.owlsoda.pageportal.reader.epub

import android.util.Xml
import org.xmlpull.v1.XmlPullParser

interface XmlParserProvider {
    fun getParser(): XmlPullParser
}

class AndroidXmlParserProvider : XmlParserProvider {
    override fun getParser(): XmlPullParser {
        return Xml.newPullParser()
    }
}
