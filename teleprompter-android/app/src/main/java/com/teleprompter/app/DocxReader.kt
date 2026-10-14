package com.teleprompter.app

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Native, lightweight DOCX document reader.
 * Parses Microsoft Word (.docx) files by extracting and streaming 'word/document.xml'
 * without requiring heavy external dependencies such as Apache POI.
 */
object DocxReader {

    /**
     * Reads a DOCX file from an Android content Uri and returns a list of text paragraphs.
     */
    fun readDocxFromUri(context: Context, uri: Uri): Result<List<String>> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(IllegalArgumentException("Не удалось открыть файл по URI: $uri"))
            val paragraphs = readDocx(inputStream)
            Result.success(paragraphs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reads a DOCX file from an InputStream.
     */
    fun readDocx(inputStream: InputStream): List<String> {
        var documentXmlContent: String? = null

        ZipInputStream(inputStream).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val normalizedName = entry.name.replace('\\', '/')
                if (normalizedName == "word/document.xml") {
                    documentXmlContent = zip.bufferedReader(Charsets.UTF_8).readText()
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        if (documentXmlContent.isNullOrBlank()) {
            throw IllegalArgumentException("В архиве DOCX не найден файл word/document.xml")
        }

        return parseDocumentXml(documentXmlContent!!)
    }

    /**
     * Parses the XML of word/document.xml and extracts all text within <w:p> and <w:t> tags.
     */
    private fun parseDocumentXml(xml: String): List<String> {
        val paragraphs = mutableListOf<String>()
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        var currentParagraph = StringBuilder()
        var insideParagraph = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name.equals("p", ignoreCase = true)) {
                        insideParagraph = true
                        currentParagraph.clear()
                    } else if (name.equals("t", ignoreCase = true) && insideParagraph) {
                        // Read text inside <w:t>
                        val text = parser.nextText()
                        currentParagraph.append(text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name.equals("p", ignoreCase = true)) {
                        val trimmed = currentParagraph.toString().trim()
                        if (trimmed.isNotEmpty()) {
                            paragraphs.add(trimmed)
                        }
                        insideParagraph = false
                        currentParagraph.clear()
                    }
                }
            }

            eventType = parser.next()
        }

        return if (paragraphs.isNotEmpty()) {
            paragraphs
        } else {
            listOf("Документ не содержит текстовых параграфов.")
        }
    }
}
