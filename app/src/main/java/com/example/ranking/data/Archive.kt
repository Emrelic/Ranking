package com.example.ranking.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "archives")
data class Archive(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String, // User-provided name for this archive
    val listId: Long, // Reference to the original song list
    val listName: String, // Copy of list name at time of archiving
    val method: String, // Ranking method used
    val totalSongs: Int, // Number of participants
    val totalMatches: Int, // Total matches played
    val completedMatches: Int, // Matches completed
    val finalResults: String, // JSON string of final rankings
    val leagueTable: String?, // JSON string of league table (if applicable)
    val matchResults: String, // JSON string of all match results
    val leagueSettings: String?, // JSON string of league settings
    val archivedAt: Long = System.currentTimeMillis(),
    val isCompleted: Boolean // Whether all matches were finished when archived
)