package com.zhuxue.zhuxuebao.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_records")
data class StudyRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val title: String,
    val createdAt: Long,
    val imagePathsJson: String,
    val analysisJson: String
)
