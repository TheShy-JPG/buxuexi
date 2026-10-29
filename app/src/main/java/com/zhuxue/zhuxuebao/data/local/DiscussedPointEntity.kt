package com.zhuxue.zhuxuebao.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "discussed_points")
data class DiscussedPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val name: String,
    val note: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "''") val learningDirection: String = "",
    @ColumnInfo(defaultValue = "''") val searchQuery: String = ""
)
