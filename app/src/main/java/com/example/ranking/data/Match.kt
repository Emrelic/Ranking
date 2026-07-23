package com.example.ranking.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "matches",
    indices = [Index("listId"), Index("tournamentId")]
)
data class Match(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val listId: Long,
    val rankingMethod: String,
    val songId1: Long,
    val songId2: Long,
    val winnerId: Long?, // null for draw, songId for winner
    val score1: Int? = null, // Score for player/team 1
    val score2: Int? = null, // Score for player/team 2
    val round: Int = 1,
    val groupId: Int? = null, // for group stage matches
    val matchNumber: Int = 0, // 🆕 Alternating match numbering (1, 18, 2, 17...)
    val tournamentId: Long? = null, // 🆕 Tournament relationship for cleaner data structure
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)