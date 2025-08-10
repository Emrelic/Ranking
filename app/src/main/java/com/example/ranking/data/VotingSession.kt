package com.example.ranking.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "voting_sessions",
    foreignKeys = [
        ForeignKey(
            entity = SongList::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class VotingSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val listId: Long,
    val rankingMethod: String,
    val sessionName: String = "",
    val isCompleted: Boolean = false,
    val isPaused: Boolean = false,
    val currentIndex: Int = 0,
    val totalItems: Int = 0,
    val progress: Float = 0f,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    // Direct scoring specific
    val currentSongId: Long? = null,
    // Match-based specific
    val currentMatchId: Long? = null,
    val currentRound: Int = 1,
    val completedMatches: Int = 0,
    val totalMatches: Int = 0
)