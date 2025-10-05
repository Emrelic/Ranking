package com.example.ranking.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "swiss_fixtures",
    foreignKeys = [
        ForeignKey(
            entity = VotingSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SwissFixture(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val currentRound: Int,
    val totalRounds: Int,
    val fixtureData: String, // JSON serialized fixture data
    val currentStandings: String, // JSON serialized current standings
    val nextMatchIndex: Int = 0,
    val isRoundComplete: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)