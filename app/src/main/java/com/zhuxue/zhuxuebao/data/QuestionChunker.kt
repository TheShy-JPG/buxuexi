package com.zhuxue.zhuxuebao.data

object QuestionChunker {

    const val CHUNK_SIZE = 900
    private const val MIN_FLUSH_SIZE = 400

    fun split(text: String): List<String> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isBlank()) return emptyList()

        val chunks = mutableListOf<String>()
        val builder = StringBuilder()

        fun flush() {
            if (builder.isNotBlank()) {
                chunks += builder.toString().trim()
                builder.clear()
            }
        }

        for (line in normalized.split('\n')) {
            if (line.isBlank()) {
                if (builder.length >= MIN_FLUSH_SIZE) flush()
                continue
            }
            if (line.length > CHUNK_SIZE) {
                flush()
                chunks += line.chunked(CHUNK_SIZE)
                continue
            }
            if (builder.length + line.length + 1 > CHUNK_SIZE) flush()
            if (builder.isNotEmpty()) builder.append('\n')
            builder.append(line)
        }
        flush()
        return chunks
    }
}
