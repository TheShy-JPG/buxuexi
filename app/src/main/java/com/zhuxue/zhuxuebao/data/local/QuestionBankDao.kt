package com.zhuxue.zhuxuebao.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionBankDao {
    @Query("SELECT * FROM question_bank ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<QuestionBankEntity>>

    @Query("SELECT * FROM question_bank WHERE id = :id")
    suspend fun getById(id: Long): QuestionBankEntity?

    @Query("SELECT COUNT(*) FROM question_bank")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(charCount), 0) FROM question_bank")
    suspend fun totalChars(): Int

    @Insert
    suspend fun insert(entity: QuestionBankEntity): Long

    @Insert
    suspend fun insertChunks(chunks: List<QuestionChunkEntity>)

    @Query("DELETE FROM question_chunks WHERE fileId = :fileId")
    suspend fun deleteChunks(fileId: Long)

    @Query("DELETE FROM question_bank WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE question_bank SET path = '' WHERE path != ''")
    suspend fun clearPaths()

    @Query(
        "SELECT * FROM question_chunks WHERE text LIKE '%' || :term || '%' " +
            "ORDER BY fileId, position LIMIT :limit"
    )
    suspend fun search(term: String, limit: Int): List<QuestionChunkEntity>

    @Query(
        "SELECT c.* FROM question_chunks c " +
            "INNER JOIN question_bank b ON b.id = c.fileId " +
            "ORDER BY b.addedAt DESC, c.position ASC LIMIT :limit"
    )
    suspend fun recent(limit: Int): List<QuestionChunkEntity>

    @Transaction
    suspend fun insertWithChunks(entity: QuestionBankEntity, chunks: List<String>): Long {
        val id = insert(entity)
        if (chunks.isNotEmpty()) {
            insertChunks(
                chunks.mapIndexed { index, text ->
                    QuestionChunkEntity(
                        fileId = id,
                        fileName = entity.name,
                        position = index,
                        text = text
                    )
                }
            )
        }
        return id
    }

    @Transaction
    suspend fun deleteWithChunks(fileId: Long) {
        deleteChunks(fileId)
        deleteById(fileId)
    }
}
