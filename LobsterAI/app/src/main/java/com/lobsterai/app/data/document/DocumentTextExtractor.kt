package com.lobsterai.app.data.document

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ExtractedDocument(
    val name: String,
    val mimeType: String,
    val text: String
)

@Singleton
class DocumentTextExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun extract(uri: Uri): ExtractedDocument = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val name = resolveName(uri)
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            readLimited(input, MAX_FILE_BYTES)
        } ?: error("无法读取文件")

        val lowerName = name.lowercase(Locale.ROOT)
        val text = when {
            mime == "application/pdf" || lowerName.endsWith(".pdf") -> extractPdf(bytes)
            mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                lowerName.endsWith(".docx") -> extractDocx(bytes)
            mime == "text/html" || lowerName.endsWith(".html") || lowerName.endsWith(".htm") ->
                Jsoup.parse(bytes.toString(Charsets.UTF_8)).text()
            mime.contains("json") ||
                mime.startsWith("text/") ||
                lowerName.endsWith(".txt") ||
                lowerName.endsWith(".md") ||
                lowerName.endsWith(".markdown") ||
                lowerName.endsWith(".json") ||
                lowerName.endsWith(".xml") ||
                lowerName.endsWith(".csv") ->
                bytes.toString(Charsets.UTF_8)
            else -> error("暂不支持此文件类型：${mime.ifBlank { lowerName.substringAfterLast('.', "未知") }}")
        }

        val clean = text
            .replace("\u0000", "")
            .replace(Regex("[ \\t]+\\n"), "\n")
            .replace(Regex("\\n{4,}"), "\n\n\n")
            .trim()
            .take(MAX_TEXT_CHARS)

        require(clean.isNotBlank()) { "文件没有提取到可读文本" }

        ExtractedDocument(
            name = name,
            mimeType = mime.ifBlank { "application/octet-stream" },
            text = clean
        )
    }

    private fun extractPdf(bytes: ByteArray): String {
        PDFBoxResourceLoader.init(context)
        PDDocument.load(ByteArrayInputStream(bytes)).use { document ->
            return PDFTextStripper().getText(document)
        }
    }

    private fun extractDocx(bytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    return Jsoup.parse(zip.readBytes().toString(Charsets.UTF_8)).text()
                }
            }
        }
        error("DOCX 中未找到正文")
    }

    private fun resolveName(uri: Uri): String {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index).orEmpty().ifBlank { "导入文件" }
            }
        }

        return uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.ifBlank { "导入文件" }
            ?: "导入文件"
    }

    private fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "文件过大，请选择 25MB 以内文件" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private companion object {
        const val MAX_FILE_BYTES = 25 * 1024 * 1024
        const val MAX_TEXT_CHARS = 500_000
    }
}
