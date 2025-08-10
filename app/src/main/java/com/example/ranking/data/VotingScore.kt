package com.example.ranking.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "voting_scores",
    foreignKeys = [
        ForeignKey(
            entity = VotingSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["songId"]),
        Index(value = ["sessionId", "songId"], unique = true)
    ]
)
data class VotingScore(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val songId: Long,
    val score: Double,
    val timestamp: Long = System.currentTimeMillis()
)