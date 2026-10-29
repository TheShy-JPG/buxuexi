package com.zhuxue.zhuxuebao.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zhuxue.zhuxuebao.data.QuestionChunker

@Database(
    entities = [
        StudyRecordEntity::class,
        PracticeResultEntity::class,
        DiscussedPointEntity::class,
        QuestionBankEntity::class,
        QuestionChunkEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studyRecordDao(): StudyRecordDao
    abstract fun practiceResultDao(): PracticeResultDao
    abstract fun discussedPointDao(): DiscussedPointDao
    abstract fun questionBankDao(): QuestionBankDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `practice_results` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`subject` TEXT NOT NULL, " +
                        "`knowledgePoint` TEXT NOT NULL, " +
                        "`correct` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `discussed_points` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`subject` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `question_bank` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`path` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `question_bank` ADD COLUMN `charCount` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `question_chunks` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`fileId` INTEGER NOT NULL, " +
                        "`fileName` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "`text` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_question_chunks_fileId` " +
                        "ON `question_chunks` (`fileId`)"
                )

                val cursor = db.query("SELECT id, name, text FROM question_bank")
                cursor.use { rows ->
                    while (rows.moveToNext()) {
                        val id = rows.getLong(0)
                        val name = rows.getString(1).orEmpty()
                        val text = rows.getString(2).orEmpty()
                        QuestionChunker.split(text).forEachIndexed { index, chunk ->
                            db.execSQL(
                                "INSERT INTO question_chunks (fileId, fileName, position, text) " +
                                    "VALUES (?, ?, ?, ?)",
                                arrayOf(id, name, index, chunk)
                            )
                        }
                        db.execSQL(
                            "UPDATE question_bank SET charCount = ?, text = '' WHERE id = ?",
                            arrayOf(text.length, id)
                        )
                    }
                }
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `discussed_points` ADD COLUMN " +
                        "`learningDirection` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE `discussed_points` ADD COLUMN " +
                        "`searchQuery` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "zhuxuebao.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
    }
}
