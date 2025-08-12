package com.example.ranking.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

/**
 * Stores the current state of a Swiss match being played
 * Allows resuming from any point during match selection/scoring
 */
@Entity(
    tableName = "swiss_match_states",
    foreignKeys = [
        ForeignKey(
            entity = Match::class,
            parentColumns = ["id"],
            childColumns = ["matchId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VotingSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SwissMatchState(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val matchId: Long,
    val currentRound: Int,
    val song1Id: Long,
    val song2Id: Long,
    val song1Name: String,
    val song2Name: String,
    val isMatchInProgress: Boolean = true, // true = currently being played
    val preliminaryWinnerId: Long? = null, // temporary selection before confirm
    val preliminaryScore1: Int? = null,
    val preliminaryScore2: Int? = null,
    val matchStartTime: Long = System.currentTimeMillis(),
    val lastUpdateTime: Long = System.currentTimeMillis()
)

/**
 * Stores complete fixture information for Swiss tournament
 * Preserves all match pairings and results across sessions
 */
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
    val fixtureData: String, // JSON: complete fixture with all rounds
    val currentStandings: String, // JSON: live standings
    val nextMatchIndex: Int = 0, // which match to play next
    val isRoundComplete: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)