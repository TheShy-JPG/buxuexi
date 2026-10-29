package com.zhuxue.zhuxuebao.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "question_chunks", indices = [Index("fileId")])
data class QuestionChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileId: Long,
    val fileName: String,
    val position: Int,
    val text: String
)
