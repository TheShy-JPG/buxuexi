package com.zhuxue.zhuxuebao.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PracticeResultDao {
    @Query("SELECT * FROM practice_results ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PracticeResultEntity>>

    @Insert
    suspend fun insert(entity: PracticeResultEntity): Long

    @Query("DELETE FROM practice_results WHERE subject = :subject AND knowledgePoint IN (:names)")
    suspend fun deleteByPoints(subject: String, names: List<String>)
}
