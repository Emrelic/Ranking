package com.example.ranking.ranking

import com.example.ranking.data.Song
import com.example.ranking.data.Match
import com.example.ranking.data.RankingResult
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.pow

object RankingEngine {
    
    fun createDirectScoringResults(songs: List<Song>, scores: Map<Long, Double>): List<RankingResult> {
        return songs.mapIndexed { index, song ->
            val score = scores[song.id] ?: 0.0
            RankingResult(
                songId = song.id,
                listId = song.listId,
                rankingMethod = "DIRECT_SCORING",
                score = score,
                position = index + 1
            )
        }.sortedByDescending { it.score }
            .mapIndexed { index, result ->
                result.copy(position = index + 1)
            }
    }
    
    fun createLeagueMatches(songs: List<Song>): List<Match> {
        val matches = mutableListOf<Match>()
        
        for (i in songs.indices) {
            for (j in i + 1 until songs.size) {
                matches.add(
                    Match(
                        listId = songs[i].listId,
                        rankingMethod = "LEAGUE",
                        songId1 = songs[i].id,
                        songId2 = songs[j].id,
                        winnerId = null,
                        round = 1
                    )
                )
            }
        }
        
        return matches
    }
    
    fun calculateLeagueResults(songs: List<Song>, matches: List<Match>): List<RankingResult> {
        val points = mutableMapOf<Long, Double>()
        
        songs.forEach { song ->
            points[song.id] = 0.0
        }
        
        matches.filter { it.isCompleted }.forEach { match ->
            when (match.winnerId) {
                match.songId1 -> points[match.songId1] = points[match.songId1]!! + 2.0
                match.songId2 -> points[match.songId2] = points[match.songId2]!! + 2.0
                null -> { // Draw
                    points[match.songId1] = points[match.songId1]!! + 1.0
                    points[match.songId2] = points[match.songId2]!! + 1.0
                }
            }
        }
        
        return songs.map { song ->
            RankingResult(
                songId = song.id,
                listId = song.listId,
                rankingMethod = "LEAGUE",
                score = points[song.id] ?: 0.0,
                position = 1
            )
        }.sortedByDescending { it.score }
            .mapIndexed { index, result ->
                result.copy(position = index + 1)
            }
    }
    
    fun createEliminationMatches(songs: List<Song>): List<Match> {
        val matches = mutableListOf<Match>()
        val songCount = songs.size
        
        // Find next power of 2
        val targetSize = 2.0.pow(ceil(log2(songCount.toDouble()))).toInt()
        
        if (songCount < targetSize) {
            // Create groups for preliminary round
            val groupSize = 5
            val groupCount = ceil(songCount.toDouble() / groupSize).toInt()
            @Suppress("UNUSED_VARIABLE")
            val songsToEliminate = songCount - (targetSize / 2) * 2
            
            // Create group stage matches
            val shuffledSongs = songs.shuffled()
            for (groupId in 0 until groupCount) {
                val groupStart = groupId * groupSize
                val groupEnd = minOf(groupStart + groupSize, songCount)
                val groupSongs = shuffledSongs.subList(groupStart, groupEnd)
                
                // Create all vs all matches in group
                for (i in groupSongs.indices) {
                    for (j in i + 1 until groupSongs.size) {
                        matches.add(
                            Match(
                                listId = songs[0].listId,
                                rankingMethod = "ELIMINATION",
                                songId1 = groupSongs[i].id,
                                songId2 = groupSongs[j].id,
                                winnerId = null,
                                round = 0, // Group stage
                                groupId = groupId
                            )
                        )
                    }
                }
            }
        }
        
        return matches
    }
    
    fun createSwissMatches(songs: List<Song>, roundNumber: Int, completedMatches: List<Match>): List<Match> {
        if (roundNumber == 1) {
            // First round: pair by initial seeding
            val matches = mutableListOf<Match>()
            val shuffledSongs = songs.shuffled()
            val half = shuffledSongs.size / 2
            
            for (i in 0 until half) {
                if (i + half < shuffledSongs.size) {
                    matches.add(
                        Match(
                            listId = songs[0].listId,
                            rankingMethod = "SWISS",
                            songId1 = shuffledSongs[i].id,
                            songId2 = shuffledSongs[i + half].id,
                            winnerId = null,
                            round = roundNumber
                        )
                    )
                }
            }
            return matches
        }
        
        // Calculate current points for each song
        val points = calculateSwissPoints(songs, completedMatches)
        
        // Group songs by points and pair within groups
        val songsByPoints = songs.groupBy { points[it.id] ?: 0.0 }
            .toSortedMap(compareByDescending { it })
        
        val matches = mutableListOf<Match>()
        val pairedSongs = mutableSetOf<Long>()
        
        songsByPoints.values.forEach { songsWithSamePoints ->
            val availableSongs = songsWithSamePoints.filter { it.id !in pairedSongs }.toMutableList()
            
            while (availableSongs.size >= 2) {
                val song1 = availableSongs.removeAt(0)
                val song2 = availableSongs.removeAt(0)
                
                matches.add(
                    Match(
                        listId = songs[0].listId,
                        rankingMethod = "SWISS",
                        songId1 = song1.id,
                        songId2 = song2.id,
                        winnerId = null,
                        round = roundNumber
                    )
                )
                
                pairedSongs.add(song1.id)
                pairedSongs.add(song2.id)
            }
        }
        
        return matches
    }
    
    fun calculateSwissResults(songs: List<Song>, matches: List<Match>): List<RankingResult> {
        val points = calculateSwissPoints(songs, matches)
        
        return songs.map { song ->
            RankingResult(
                songId = song.id,
                listId = song.listId,
                rankingMethod = "SWISS",
                score = points[song.id] ?: 0.0,
                position = 1
            )
        }.sortedByDescending { it.score }
            .mapIndexed { index, result ->
                result.copy(position = index + 1)
            }
    }
    
    private fun calculateSwissPoints(songs: List<Song>, matches: List<Match>): Map<Long, Double> {
        val points = mutableMapOf<Long, Double>()
        
        songs.forEach { song ->
            points[song.id] = 0.0
        }
        
        matches.filter { it.isCompleted }.forEach { match ->
            when (match.winnerId) {
                match.songId1 -> points[match.songId1] = points[match.songId1]!! + 1.0
                match.songId2 -> points[match.songId2] = points[match.songId2]!! + 1.0
                null -> { // Draw
                    points[match.songId1] = points[match.songId1]!! + 0.5
                    points[match.songId2] = points[match.songId2]!! + 0.5
                }
            }
        }
        
        return points
    }
    
    fun createEmreMatches(songs: List<Song>, roundNumber: Int): List<Match> {
        val matches = mutableListOf<Match>()
        
        // Pair consecutive songs: 1-2, 3-4, 5-6, etc.
        for (i in 0 until songs.size - 1 step 2) {
            matches.add(
                Match(
                    listId = songs[0].listId,
                    rankingMethod = "EMRE",
                    songId1 = songs[i].id,
                    songId2 = songs[i + 1].id,
                    winnerId = null,
                    round = roundNumber
                )
            )
        }
        
        return matches
    }
    
    fun calculateEmreResults(songs: List<Song>, allMatches: List<Match>): List<RankingResult> {
        var currentSongs = songs.toMutableList()
        val roundCount = ceil(log2(songs.size.toDouble())).toInt()
        
        for (round in 1..roundCount) {
            val roundMatches = allMatches.filter { it.round == round && it.isCompleted }
            val winners = mutableListOf<Song>()
            val losers = mutableListOf<Song>()
            
            roundMatches.forEach { match ->
                val song1 = currentSongs.find { it.id == match.songId1 }
                val song2 = currentSongs.find { it.id == match.songId2 }
                
                when (match.winnerId) {
                    match.songId1 -> {
                        song1?.let { winners.add(it) }
                        song2?.let { losers.add(it) }
                    }
                    match.songId2 -> {
                        song2?.let { winners.add(it) }
                        song1?.let { losers.add(it) }
                    }
                }
            }
            
            // Reorganize: winners first, then losers
            currentSongs = (winners + losers).toMutableList()
        }
        
        return currentSongs.mapIndexed { index, song ->
            RankingResult(
                songId = song.id,
                listId = song.listId,
                rankingMethod = "EMRE",
                score = (songs.size - index).toDouble(),
                position = index + 1
            )
        }
    }
    
    fun getSwissRoundCount(songCount: Int): Int {
        return when {
            songCount <= 8 -> 3
            songCount <= 16 -> 4
            songCount <= 32 -> 5
            songCount <= 64 -> 6
            songCount <= 128 -> 7
            else -> 8
        }
    }
    
    fun getEmreRoundCount(songCount: Int): Int {
        return ceil(log2(songCount.toDouble())).toInt()
    }
}