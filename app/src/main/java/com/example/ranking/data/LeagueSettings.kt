package com.example.ranking.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "league_settings")
data class LeagueSettings(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val listId: Long,
    val rankingMethod: String,
    val useScores: Boolean = false, // Whether to use actual scores
    val winPoints: Int = 3, // Points for a win
    val drawPoints: Int = 1, // Points for a draw
    val losePoints: Int = 0, // Points for a loss
    val allowDraws: Boolean = true, // Whether draws are allowed
    val createdAt: Long = System.currentTimeMillis()
)