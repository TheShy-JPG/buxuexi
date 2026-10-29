package com.zhuxue.zhuxuebao.data

import com.zhuxue.zhuxuebao.data.local.DiscussedPointDao
import com.zhuxue.zhuxuebao.data.local.DiscussedPointEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DiscussionRepository(private val dao: DiscussedPointDao) {

    fun observePoints(): Flow<List<DiscussedPoint>> =
        dao.observeAll().map { list ->
            list.map {
                DiscussedPoint(
                    id = it.id,
                    subject = it.subject,
                    name = it.name,
                    note = it.note,
                    at = it.createdAt,
                    learningDirection = it.learningDirection,
                    searchQuery = it.searchQuery
                )
            }
        }

    suspend fun add(subject: String, name: String, note: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        dao.insert(
            DiscussedPointEntity(
                subject = subject,
                name = trimmed,
                note = note.trim(),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteForNames(subject: String, names: List<String>) {
        val trimmed = names.map { it.trim() }.filter { it.isNotEmpty() }
        if (trimmed.isEmpty()) return
        dao.deleteByNames(subject, trimmed)
    }

    suspend fun updateGuide(
        subject: String,
        name: String,
        learningDirection: String,
        searchQuery: String
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || learningDirection.isBlank()) return
        dao.updateGuide(subject, trimmed, learningDirection.trim(), searchQuery.trim())
    }
}
