package com.zhuxue.zhuxuebao.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyRecordDao {
    @Query("SELECT * FROM study_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<StudyRecordEntity>>

    @Query("SELECT * FROM study_records WHERE id = :id")
    suspend fun getById(id: Long): StudyRecordEntity?

    @Insert
    suspend fun insert(entity: StudyRecordEntity): Long

    @Query("DELETE FROM study_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE study_records SET analysisJson = :analysisJson WHERE id = :id")
    suspend fun updateAnalysis(id: Long, analysisJson: String)
}
