package com.zhuxue.zhuxuebao.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiscussedPointDao {
    @Query("SELECT * FROM discussed_points ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DiscussedPointEntity>>

    @Insert
    suspend fun insert(entity: DiscussedPointEntity): Long

    @Query("DELETE FROM discussed_points WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM discussed_points WHERE subject = :subject AND name IN (:names)")
    suspend fun deleteByNames(subject: String, names: List<String>)

    @Query(
        "UPDATE discussed_points SET learningDirection = :direction, searchQuery = :query " +
            "WHERE subject = :subject AND name = :name"
    )
    suspend fun updateGuide(
        subject: String,
        name: String,
        direction: String,
        query: String
    ): Int
}
