package com.example.ranking.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "youtube_playlists",
    indices = [Index("artist")]
)
data class YouTubePlaylist(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val name: String,
    val artist: String,
    val trackCount: Int = 0,
    val totalViews: Long = 0,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)