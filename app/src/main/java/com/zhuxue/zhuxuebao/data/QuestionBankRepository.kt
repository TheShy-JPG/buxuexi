package com.zhuxue.zhuxuebao.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.zhuxue.zhuxuebao.data.local.QuestionBankDao
import com.zhuxue.zhuxuebao.data.local.QuestionBankEntity
import com.zhuxue.zhuxuebao.data.local.QuestionChunkEntity
import java.io.File
import java.io.IOException
import java.io.Reader
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class QuestionBankFile(
    val id: Long,
    val name: String,
    val charCount: Int,
    val addedAt: Long
)

const val QUESTION_BANK_MAX_FILES = 40
const val QUESTION_BANK_MAX_CHARS_PER_FILE = 2_000_000
const val QUESTION_BANK_MAX_TOTAL_CHARS = 12_000_000

class QuestionBankRepository(
    private val context: Context,
    private val dao: QuestionBankDao
) {

    fun observeFiles(): Flow<List<QuestionBankFile>> =
        dao.observeAll().map { list ->
            list.map {
                QuestionBankFile(
                    id = it.id,
                    name = it.name,
                    charCount = it.charCount,
                    addedAt = it.addedAt
                )
            }
        }

    suspend fun add(uri: Uri): QuestionBankFile = withContext(Dispatchers.IO) {
        val name = displayName(uri)
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension !in setOf("docx", "pdf")) {
            throw IllegalArgumentException("题库只支持 Word(.docx) 或 PDF，不能是照片")
        }
        if (dao.count() >= QUESTION_BANK_MAX_FILES) {
            throw IllegalArgumentException("最多保存 $QUESTION_BANK_MAX_FILES 个题库文件，请先在设置里删除旧题库")
        }
        if ((declaredSize(uri) ?: 0L) > MAX_IMPORT_BYTES) {
            throw IllegalArgumentException("文件超过 ${MAX_IMPORT_BYTES / (1024 * 1024)}MB，请拆分后再上传")
        }

        val tempDir = File(context.cacheDir, "bank_import").apply { mkdirs() }
        val target = File(tempDir, "${System.currentTimeMillis()}_${sanitize(name)}")
        try {
            try {
                copyToTemp(uri, target)
            } catch (error: IOException) {
                throw IllegalArgumentException("文件读取失败，请重新选择")
            }

            val text = try {
                if (extension == "docx") extractDocxText(target) else extractPdfText(target)
            } catch (error: IllegalArgumentException) {
                throw error
            } catch (error: Exception) {
                throw IllegalArgumentException("文件解析失败，请换一个可复制文字的 Word 或 PDF")
            }.trim()

            if (text.count { !it.isWhitespace() } < 40) {
                throw IllegalArgumentException("读不到文字，可能是扫描件或照片；请上传可复制文字的 Word 或 PDF")
            }
            if (text.length > QUESTION_BANK_MAX_CHARS_PER_FILE) {
                throw IllegalArgumentException(
                    "单个题库文件超过 ${QUESTION_BANK_MAX_CHARS_PER_FILE / 10_000} 万字，请拆分后再上传"
                )
            }
            if (dao.totalChars() + text.length > QUESTION_BANK_MAX_TOTAL_CHARS) {
                throw IllegalArgumentException("题库容量已满，请先在设置里删除不用的题库")
            }

            val chunks = QuestionChunker.split(text)
            if (chunks.isEmpty()) {
                throw IllegalArgumentException("读不到可用文字，请换一个文件")
            }
            val addedAt = System.currentTimeMillis()
            val id = dao.insertWithChunks(
                QuestionBankEntity(
                    name = name,
                    path = "",
                    text = "",
                    addedAt = addedAt,
                    charCount = text.length
                ),
                chunks
            )
            QuestionBankFile(id = id, name = name, charCount = text.length, addedAt = addedAt)
        } finally {
            target.delete()
        }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        dao.getById(id)?.path
            ?.takeIf { it.isNotBlank() }
            ?.let { path -> runCatching { File(path).delete() } }
        dao.deleteWithChunks(id)
    }

    suspend fun hasFiles(): Boolean = withContext(Dispatchers.IO) { dao.count() > 0 }

    /**
     * 只取出与关键词相关的题目块，避免把整个题库读进内存。
     * 没有任何命中时返回空串，让练习按来源优先级回退。
     */
    suspend fun combinedText(subject: String, keyword: String, budget: Int = 9000): String =
        withContext(Dispatchers.IO) {
            val terms = searchTerms(keyword, subject)
            if (terms.isEmpty()) return@withContext ""

            val matches = LinkedHashMap<Long, Pair<QuestionChunkEntity, Int>>()
            for ((index, term) in terms.withIndex()) {
                dao.search(term, SEARCH_PER_TERM).forEach { chunk ->
                    val previous = matches[chunk.id]
                    matches[chunk.id] = chunk to ((previous?.second ?: 0) + 1)
                }
                // 长词优先匹配，候选足够时不再为补充召回多扫整张表
                if (index >= 1 && matches.size >= SEARCH_ENOUGH) break
            }
            if (matches.isEmpty()) return@withContext ""

            val selected = matches.values.sortedWith(
                compareByDescending<Pair<QuestionChunkEntity, Int>> { it.second }
                    .thenBy { it.first.fileId }
                    .thenBy { it.first.position }
            )

            val builder = StringBuilder()
            val headedFiles = HashSet<Long>()
            for ((chunk, _) in selected) {
                val header = if (headedFiles.add(chunk.fileId)) "【${chunk.fileName}】\n" else ""
                val remaining = budget - builder.length
                if (remaining <= header.length + MIN_SNIPPET_CHARS) break
                builder.append(header)
                builder.append(chunk.text.take(remaining - header.length).trim())
                builder.append("\n\n")
                if (builder.length >= budget) break
            }
            builder.toString().trim()
        }

    suspend fun cleanupLegacyFiles() = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "question_bank")
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        runCatching { dir.delete() }
        dao.clearPaths()
    }

    private fun searchTerms(keyword: String, subject: String): List<String> {
        val cleaned = keyword
            .replace(Regex("[\\s，,。；;：:、（）()《》<>\"'“”‘’·\\-—_/%]+"), " ")
            .trim()
        if (cleaned.isBlank()) return emptyList()
        val terms = LinkedHashSet<String>()
        val parts = cleaned.split(' ').filter { it.isNotBlank() }
        parts.forEach { part ->
            if (part.length <= MAX_TERM_LENGTH) terms += part
        }
        val subjectTerm = subject.trim()
        if (subjectTerm.isNotBlank()) terms += subjectTerm
        parts.forEach { part ->
            if (part.length > 4) {
                for (index in 0..part.length - 2) {
                    terms += part.substring(index, index + 2)
                }
            }
        }
        return terms.take(MAX_SEARCH_TERMS)
    }

    private fun copyToTemp(uri: Uri, target: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("文件读取失败，请重新选择")
        input.use { source ->
            target.outputStream().use { output ->
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_IMPORT_BYTES) {
                        throw IllegalArgumentException(
                            "文件超过 ${MAX_IMPORT_BYTES / (1024 * 1024)}MB，请拆分后再上传"
                        )
                    }
                    output.write(buffer, 0, read)
                }
                if (total == 0L) throw IllegalArgumentException("文件读取失败，请重新选择")
            }
        }
    }

    private fun extractDocxText(file: File): String {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: return ""
            if (entry.size > MAX_DOCX_XML_BYTES) {
                throw IllegalArgumentException("Word 文档正文过大，请拆分后再上传")
            }
            val xml = zip.getInputStream(entry).bufferedReader().use { readTextLimited(it) }
            return xml
                .replace("</w:p>", "\n")
                .replace("<w:br/>", "\n")
                .replace("<w:tab/>", "\t")
                .replace(Regex("<[^>]+>"), "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace(Regex("\n{3,}"), "\n\n")
        }
    }

    private fun extractPdfText(file: File): String {
        PDFBoxResourceLoader.init(context)
        return PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { document ->
            PDFTextStripper().getText(document)
        }
    }

    private fun readTextLimited(reader: Reader): String {
        val builder = StringBuilder()
        val buffer = CharArray(16 * 1024)
        while (true) {
            val read = reader.read(buffer)
            if (read < 0) break
            builder.append(buffer, 0, read)
            if (builder.length > MAX_DOCX_XML_CHARS) {
                throw IllegalArgumentException("Word 文档正文过大，请拆分后再上传")
            }
        }
        return builder.toString()
    }

    private fun declaredSize(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                    cursor.getLong(index)
                } else {
                    null
                }
            }
    }.getOrNull()

    private fun displayName(uri: Uri): String {
        val fromProvider = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
        return fromProvider?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "题库文件"
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    private companion object {
        const val SEARCH_PER_TERM = 80
        const val SEARCH_ENOUGH = 160
        const val MAX_SEARCH_TERMS = 10
        const val MAX_TERM_LENGTH = 24
        const val MIN_SNIPPET_CHARS = 120
        const val MAX_IMPORT_BYTES = 30L * 1024 * 1024
        const val MAX_DOCX_XML_BYTES = 30L * 1024 * 1024
        const val MAX_DOCX_XML_CHARS = 16_000_000
    }
}
