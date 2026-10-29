package com.zhuxue.zhuxuebao.data

import com.zhuxue.zhuxuebao.data.local.StudyRecordDao
import com.zhuxue.zhuxuebao.data.local.StudyRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

class RecordRepository(private val dao: StudyRecordDao) {

    fun observeRecords(): Flow<List<StudyRecord>> =
        dao.observeAll().map { list -> list.mapNotNull { it.toDomain() } }

    suspend fun getRecord(id: Long): StudyRecord? = dao.getById(id)?.toDomain()

    suspend fun save(
        subject: String,
        title: String,
        imagePaths: List<String>,
        analysis: AnalysisResult
    ): Long = dao.insert(
        StudyRecordEntity(
            subject = subject.ifBlank { "其他" },
            title = title.ifBlank { "错题分析" },
            createdAt = System.currentTimeMillis(),
            imagePathsJson = AppJson.encodeToString(imagePaths),
            analysisJson = AppJson.encodeToString(analysis)
        )
    )

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun updateQuestions(recordId: Long, analysis: AnalysisResult) {
        dao.updateAnalysis(recordId, AppJson.encodeToString(analysis))
    }

    private fun StudyRecordEntity.toDomain(): StudyRecord? {
        val analysis = runCatching {
            AppJson.decodeFromString<AnalysisResult>(analysisJson)
        }.getOrElse {
            AnalysisResult(title = title, subject = subject, summary = "这条记录的数据格式已损坏。")
        }
        val images = runCatching {
            AppJson.decodeFromString<List<String>>(imagePathsJson)
        }.getOrDefault(emptyList())
        return StudyRecord(
            id = id,
            subject = subject,
            title = title,
            createdAt = createdAt,
            imagePaths = images,
            analysis = analysis
        )
    }
}
