package com.example.ranking.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "youtube_tracks",
    indices = [
        Index("videoId"),
        Index("artist"),
        Index(value = ["viewCount"], orders = [Index.Order.DESC])
    ]
)
data class YouTubeTrack(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val videoId: String,
    val title: String,
    val artist: String,
    val viewCount: Long,
    val durationSeconds: Int? = null,
    val thumbnailUrl: String? = null,
    val publishedAt: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)