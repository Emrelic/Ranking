package com.example.ranking.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val artist: String = "",
    val album: String = "",
    val trackNumber: Int = 0,
    val listId: Long,
    val csvData: String? = null, // JSON formatted tabular data from CSV: {"column1": "value1", "column2": "value2"}
    val youtubeVideoId: String? = null, // YouTube video ID for integration
    val viewCount: Long = 0 // YouTube view count
)