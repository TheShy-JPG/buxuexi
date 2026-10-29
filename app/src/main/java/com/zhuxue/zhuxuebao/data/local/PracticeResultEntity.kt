package com.zhuxue.zhuxuebao.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "practice_results")
data class PracticeResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val knowledgePoint: String,
    val correct: Boolean,
    val createdAt: Long
)
