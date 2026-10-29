package com.zhuxue.zhuxuebao.data

import com.zhuxue.zhuxuebao.data.local.PracticeResultDao
import com.zhuxue.zhuxuebao.data.local.PracticeResultEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PracticeRepository(private val dao: PracticeResultDao) {

    fun observeAttempts(): Flow<List<PracticeAttempt>> =
        dao.observeAll().map { list ->
            list.map {
                PracticeAttempt(
                    subject = it.subject,
                    knowledgePoint = it.knowledgePoint,
                    correct = it.correct,
                    at = it.createdAt
                )
            }
        }

    suspend fun addAttempt(subject: String, knowledgePoint: String, correct: Boolean) {
        val name = knowledgePoint.trim()
        if (name.isEmpty()) return
        dao.insert(
            PracticeResultEntity(
                subject = subject,
                knowledgePoint = name,
                correct = correct,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteForPoints(subject: String, names: List<String>) {
        val trimmed = names.map { it.trim() }.filter { it.isNotEmpty() }
        if (trimmed.isEmpty()) return
        dao.deleteByPoints(subject, trimmed)
    }
}
