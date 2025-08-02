package com.example.ranking.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "matches")
data class Match(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val listId: Long,
    val rankingMethod: String,
    val songId1: Long,
    val songId2: Long,
    val winnerId: Long?, // null for draw, songId for winner
    val round: Int = 1,
    val groupId: Int? = null, // for group stage matches
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)