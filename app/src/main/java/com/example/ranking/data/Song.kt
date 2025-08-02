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
    val listId: Long
)