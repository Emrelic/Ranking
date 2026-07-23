package com.example.ranking.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ranking_results",
    indices = [Index("listId")]
)
data class RankingResult(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val songId: Long,
    val listId: Long,
    val rankingMethod: String, // "DIRECT_SCORING", "LEAGUE", "ELIMINATION", "SWISS", "EMRE_CORRECT"
    val score: Double,
    val position: Int,
    val createdAt: Long = System.currentTimeMillis()
)