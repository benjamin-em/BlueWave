package com.ounben.amaradio.database.user

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stationUuid: String,
    val stationName: String,
    val fileUri: String,
    val timestamp: Long,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val format: String = "mp3"
)
