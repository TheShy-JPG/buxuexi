package com.zhuxue.zhuxuebao.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionChunkerTest {

    @Test
    fun `blank text produces no chunks`() {
        assertTrue(QuestionChunker.split("   \n\n ").isEmpty())
    }

    @Test
    fun `long single line is split by chunk size`() {
        val line = "题".repeat(QuestionChunker.CHUNK_SIZE * 2 + 10)
        val chunks = QuestionChunker.split(line)
        assertEquals(3, chunks.size)
        assertTrue(chunks.all { it.length <= QuestionChunker.CHUNK_SIZE })
    }

    @Test
    fun `short paragraphs merge until chunk limit`() {
        val text = (1..120).joinToString("\n\n") { "第${it}题的题干内容，用于测试分块逻辑。" }
        val chunks = QuestionChunker.split(text)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= QuestionChunker.CHUNK_SIZE })
        assertEquals(
            text.replace("\n\n", "\n").trim(),
            chunks.joinToString("\n").trim()
        )
    }
}
