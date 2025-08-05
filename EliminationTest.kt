package com.example.ranking.test

import com.example.ranking.data.Song
import com.example.ranking.ranking.RankingEngine

fun main() {
    // Test scenarios
    testEliminationSystem(8)   // Perfect power of 2
    testEliminationSystem(16)  // Perfect power of 2 
    testEliminationSystem(25)  // Needs groups: 25->16, eliminate 9
    testEliminationSystem(13)  // Needs groups: 13->8, eliminate 5
    testEliminationSystem(7)   // Needs groups: 7->4, eliminate 3
}

fun testEliminationSystem(participantCount: Int) {
    println("\n=== Test with $participantCount participants ===")
    
    // Create test songs
    val songs = (1..participantCount).map { 
        Song(
            id = it.toLong(),
            listId = 1L,
            name = "Song $it",
            artist = "Artist $it"
        ) 
    }
    
    // Create elimination matches
    val matches = RankingEngine.createEliminationMatches(songs)
    
    println("Created ${matches.size} matches")
    
    // Check group distribution
    val groupMatches = matches.filter { it.round == 0 }
    if (groupMatches.isNotEmpty()) {
        val groups = groupMatches.groupBy { it.groupId }
        println("Group stage: ${groups.size} groups")
        
        groups.forEach { (groupId, groupMatches) ->
            val songsInGroup = groupMatches.flatMap { listOf(it.songId1, it.songId2) }.distinct()
            println("  Group $groupId: ${songsInGroup.size} teams, ${groupMatches.size} matches")
        }
        
        // Calculate what would happen after groups
        val targetSize = kotlin.math.pow(2.0, kotlin.math.floor(kotlin.math.log2(participantCount.toDouble()))).toInt()
        val teamsToEliminate = participantCount - targetSize
        val groupConfig = RankingEngine.calculateOptimalGroupConfig(participantCount, teamsToEliminate)
        
        println("Config: ${groupConfig.groupCount} groups, eliminate ${groupConfig.eliminationsPerGroup} per group")
        println("Expected qualifiers: ${participantCount - teamsToEliminate} teams")
        println("Target bracket size: $targetSize")
    } else {
        println("Direct elimination (already power of 2)")
        val knockoutMatches = matches.filter { it.round > 0 }
        println("Knockout rounds: ${knockoutMatches.groupBy { it.round }.size}")
    }
}