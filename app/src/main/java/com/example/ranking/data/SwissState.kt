package com.example.ranking.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "swiss_states",
    foreignKeys = [
        ForeignKey(
            entity = VotingSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SwissState(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val currentRound: Int = 1,
    val maxRounds: Int,
    val standings: String, // JSON string of current standings/points
    val pairingHistory: String, // JSON string of who has played whom
    val roundHistory: String, // JSON string of completed round results
    val lastUpdated: Long = System.currentTimeMillis()
)