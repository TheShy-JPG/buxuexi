package com.zhuxue.zhuxuebao.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "question_bank")
data class QuestionBankEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val path: String,
    val text: String,
    val addedAt: Long,
    val charCount: Int = 0
)
