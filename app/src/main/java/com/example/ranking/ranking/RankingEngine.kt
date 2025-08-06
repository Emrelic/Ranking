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
    
    fun createLeagueMatches(songs: List<Song>, doubleRoundRobin: Boolean = false): List<Match> {
        if (songs.size < 2) return emptyList()
        
        val matches = mutableListOf<Match>()
        val teams = songs.toMutableList()
        
        // Eğer takım sayısı tek sayıysa, "BYE" takımı ekle (geçer)
        val hasOddTeams = teams.size % 2 != 0
        if (hasOddTeams) {
            teams.add(Song(id = -1, name = "BYE", artist = "", album = "", trackNumber = 0, listId = teams[0].listId))
        }
        
        val numTeams = teams.size
        val numRounds = numTeams - 1
        val matchesPerRound = numTeams / 2
        
        // Round-robin algoritması (Circle method)
        for (round in 1..numRounds) {
            val roundMatches = mutableListOf<Match>()
            
            for (match in 0 until matchesPerRound) {
                val home = (round - 1 + match) % (numTeams - 1)
                val away = (numTeams - 1 - match + round - 1) % (numTeams - 1)
                
                val homeTeam = if (match == 0) teams.last() else teams[home]
                val awayTeam = teams[away]
                
                // BYE takımıyla olan maçları atla
                if (homeTeam.id != -1L && awayTeam.id != -1L) {
                    roundMatches.add(
                        Match(
                            listId = songs[0].listId,
                            rankingMethod = "LEAGUE",
                            songId1 = homeTeam.id,
                            songId2 = awayTeam.id,
                            winnerId = null,
                            round = round
                        )
                    )
                }
            }
            
            matches.addAll(roundMatches)
        }
        
        // Rövanşlı lig (İkinci devre)
        if (doubleRoundRobin) {
            val firstLegMatches = matches.toList() // Kopyala
            
            for (originalMatch in firstLegMatches) {
                // Ev sahibi ve misafir takımları yer değiştir
                matches.add(
                    Match(
                        listId = originalMatch.listId,
                        rankingMethod = originalMatch.rankingMethod,
                        songId1 = originalMatch.songId2, // Yer değişimi
                        songId2 = originalMatch.songId1, // Yer değişimi
                        winnerId = null,
                        round = originalMatch.round + numRounds // İkinci devreye round ekle
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
        
        if (songCount <= 1) return matches
        
        // Find the largest power of 2 that is less than or equal to songCount
        val targetSize = 2.0.pow(kotlin.math.floor(log2(songCount.toDouble()))).toInt()
        
        // If already a power of 2, start direct elimination
        if (songCount == targetSize) {
            return createDirectEliminationMatches(songs, 1)
        }
        
        // Calculate teams to eliminate and optimal group configuration
        val teamsToEliminate = songCount - targetSize
        val groupConfig = calculateOptimalGroupConfig(songCount, teamsToEliminate)
        
        // Create group stage matches
        val shuffledSongs = songs.shuffled()
        var songIndex = 0
        
        for (groupId in 0 until groupConfig.groupCount) {
            val groupSize = if (groupId < groupConfig.remainderGroups) {
                groupConfig.baseGroupSize + 1
            } else {
                groupConfig.baseGroupSize
            }
            
            val groupSongs = shuffledSongs.subList(songIndex, songIndex + groupSize)
            songIndex += groupSize
            
            // Create round-robin matches within group
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
        
        return matches
    }
    
    data class GroupConfig(
        val groupCount: Int,
        val baseGroupSize: Int,
        val remainderGroups: Int,
        val eliminationsPerGroup: Int
    )
    
    fun calculateOptimalGroupConfig(totalTeams: Int, teamsToEliminate: Int): GroupConfig {
        // Try 1 elimination per group first
        var groupCount = teamsToEliminate
        var baseGroupSize = totalTeams / groupCount
        var remainder = totalTeams % groupCount
        
        // Check if group sizes are within acceptable range (3-6)
        val minGroupSize = if (remainder > 0) baseGroupSize else baseGroupSize
        val maxGroupSize = if (remainder > 0) baseGroupSize + 1 else baseGroupSize
        
        if (minGroupSize >= 3 && maxGroupSize <= 6) {
            return GroupConfig(groupCount, baseGroupSize, remainder, 1)
        }
        
        // If 1 elimination per group doesn't work, try 2 eliminations per group
        groupCount = (teamsToEliminate + 1) / 2  // Round up division
        val actualEliminations = groupCount * 2
        
        // Adjust if we eliminate too many
        if (actualEliminations > teamsToEliminate) {
            // Some groups eliminate 1, others eliminate 2
            val groupsEliminating2 = teamsToEliminate - (groupCount * 2 - groupCount)
            val groupsEliminating1 = groupCount - groupsEliminating2
            
            baseGroupSize = totalTeams / groupCount
            remainder = totalTeams % groupCount
            
            return GroupConfig(groupCount, baseGroupSize, remainder, 2)
        }
        
        baseGroupSize = totalTeams / groupCount
        remainder = totalTeams % groupCount
        
        return GroupConfig(groupCount, baseGroupSize, remainder, 2)
    }
    
    private fun createDirectEliminationMatches(songs: List<Song>, startRound: Int): List<Match> {
        val matches = mutableListOf<Match>()
        val currentRoundSongs = songs.toMutableList()
        
        for (round in startRound until startRound + log2(songs.size.toDouble()).toInt()) {
            for (i in 0 until currentRoundSongs.size step 2) {
                if (i + 1 < currentRoundSongs.size) {
                    matches.add(
                        Match(
                            listId = songs[0].listId,
                            rankingMethod = "ELIMINATION",
                            songId1 = currentRoundSongs[i].id,
                            songId2 = currentRoundSongs[i + 1].id,
                            winnerId = null,
                            round = round
                        )
                    )
                }
            }
            // For next iteration, we'd have half the songs (winners)
            if (currentRoundSongs.size <= 2) break
            // Note: We don't actually update currentRoundSongs here as we don't know winners yet
            // This is just for creating the bracket structure
        }
        
        return matches
    }
    
    fun getGroupQualifiers(songs: List<Song>, groupMatches: List<Match>, groupConfig: GroupConfig): List<Song> {
        val qualifiers = mutableListOf<Song>()
        
        for (groupId in 0 until groupConfig.groupCount) {
            val groupSongs = getGroupSongs(songs, groupId, groupConfig)
            val groupResults = calculateGroupStandings(groupSongs, groupMatches.filter { it.groupId == groupId })
            
            // Advance top teams (eliminate bottom teams based on eliminationsPerGroup)
            val teamsToAdvance = groupSongs.size - groupConfig.eliminationsPerGroup
            qualifiers.addAll(groupResults.take(teamsToAdvance).map { it.first })
        }
        
        return qualifiers
    }
    
    private fun getGroupSongs(allSongs: List<Song>, groupId: Int, groupConfig: GroupConfig): List<Song> {
        val shuffledSongs = allSongs.shuffled() // Should use same shuffle as createEliminationMatches
        var songIndex = 0
        
        for (currentGroupId in 0 until groupId) {
            val groupSize = if (currentGroupId < groupConfig.remainderGroups) {
                groupConfig.baseGroupSize + 1
            } else {
                groupConfig.baseGroupSize
            }
            songIndex += groupSize
        }
        
        val groupSize = if (groupId < groupConfig.remainderGroups) {
            groupConfig.baseGroupSize + 1
        } else {
            groupConfig.baseGroupSize
        }
        
        return shuffledSongs.subList(songIndex, songIndex + groupSize)
    }
    
    private fun calculateGroupStandings(groupSongs: List<Song>, groupMatches: List<Match>): List<Pair<Song, Double>> {
        val points = mutableMapOf<Long, Double>()
        
        groupSongs.forEach { song ->
            points[song.id] = 0.0
        }
        
        groupMatches.filter { it.isCompleted }.forEach { match ->
            when (match.winnerId) {
                match.songId1 -> points[match.songId1] = points[match.songId1]!! + 3.0
                match.songId2 -> points[match.songId2] = points[match.songId2]!! + 3.0
                null -> { // Draw
                    points[match.songId1] = points[match.songId1]!! + 1.0
                    points[match.songId2] = points[match.songId2]!! + 1.0
                }
            }
        }
        
        return groupSongs.map { song ->
            Pair(song, points[song.id] ?: 0.0)
        }.sortedByDescending { it.second }
    }
    
    fun createEliminationKnockoutMatches(qualifierSongs: List<Song>, startRound: Int): List<Match> {
        return createDirectEliminationMatches(qualifierSongs, startRound)
    }
    
    fun calculateEliminationResults(songs: List<Song>, allMatches: List<Match>): List<RankingResult> {
        val songCount = songs.size
        val targetSize = 2.0.pow(kotlin.math.floor(log2(songCount.toDouble()))).toInt()
        
        if (songCount == targetSize) {
            // Direct elimination - calculate based on elimination round
            return calculateDirectEliminationResults(songs, allMatches)
        }
        
        // Group stage + knockout
        val groupMatches = allMatches.filter { it.round == 0 }
        val knockoutMatches = allMatches.filter { it.round > 0 }
        
        val teamsToEliminate = songCount - targetSize
        val groupConfig = calculateOptimalGroupConfig(songCount, teamsToEliminate)
        
        // Get group standings for eliminated teams
        val results = mutableListOf<RankingResult>()
        var currentPosition = songCount
        
        // Process each group to rank eliminated teams
        for (groupId in 0 until groupConfig.groupCount) {
            val groupSongs = getGroupSongs(songs, groupId, groupConfig)
            val groupStandings = calculateGroupStandings(groupSongs, groupMatches.filter { it.groupId == groupId })
            
            // Add eliminated teams (bottom teams in group)
            val eliminatedTeams = groupStandings.takeLast(groupConfig.eliminationsPerGroup)
            eliminatedTeams.reversed().forEach { (song, score) ->
                results.add(
                    RankingResult(
                        songId = song.id,
                        listId = song.listId,
                        rankingMethod = "ELIMINATION",
                        score = score,
                        position = currentPosition--
                    )
                )
            }
        }
        
        // Get qualifiers and their knockout results
        val qualifiers = getGroupQualifiers(songs, groupMatches, groupConfig)
        val knockoutResults = calculateDirectEliminationResults(qualifiers, knockoutMatches)
        
        // Adjust positions for knockout results
        knockoutResults.forEach { result ->
            results.add(result.copy(position = result.position))
        }
        
        return results.sortedBy { it.position }
    }
    
    private fun calculateDirectEliminationResults(songs: List<Song>, matches: List<Match>): List<RankingResult> {
        val eliminated = mutableSetOf<Long>()
        val roundResults = mutableMapOf<Int, List<Long>>() // round -> eliminated song IDs
        
        // Process each round to find eliminated teams
        val maxRound = matches.maxOfOrNull { it.round } ?: 0
        for (round in 1..maxRound) {
            val roundMatches = matches.filter { it.round == round && it.isCompleted }
            val roundEliminated = mutableListOf<Long>()
            
            roundMatches.forEach { match ->
                val loser = when (match.winnerId) {
                    match.songId1 -> match.songId2
                    match.songId2 -> match.songId1
                    else -> null
                }
                loser?.let { 
                    roundEliminated.add(it)
                    eliminated.add(it)
                }
            }
            
            if (roundEliminated.isNotEmpty()) {
                roundResults[round] = roundEliminated
            }
        }
        
        // Create results based on elimination round (later elimination = higher rank) 
        val results = mutableListOf<RankingResult>()
        var currentPosition = songs.size
        
        // Add eliminated teams by round (reverse order - last eliminated get better positions)
        for (round in 1..maxRound) {
            roundResults[round]?.forEach { songId ->
                val song = songs.find { it.id == songId }
                if (song != null) {
                    results.add(
                        RankingResult(
                            songId = song.id,
                            listId = song.listId,
                            rankingMethod = "ELIMINATION",
                            score = (maxRound - round + 1).toDouble(),
                            position = currentPosition--
                        )
                    )
                }
            }
        }
        
        // Add winner (not eliminated)
        val winner = songs.find { it.id !in eliminated }
        winner?.let {
            results.add(
                RankingResult(
                    songId = it.id,
                    listId = it.listId,
                    rankingMethod = "ELIMINATION", 
                    score = (maxRound + 1).toDouble(),
                    position = 1
                )
            )
        }
        
        return results.sortedBy { it.position }
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
    
    fun createEmreMatchesWithOrdering(songs: List<Song>, roundNumber: Int, allMatches: List<Match>): List<Match> {
        // First round uses original order
        if (roundNumber == 1) {
            return createEmreMatches(songs, roundNumber)
        }
        
        // Get previous round results and reorder songs
        val reorderedSongs = reorderSongsAfterEmreRound(songs, allMatches, roundNumber - 1)
        return createEmreMatches(reorderedSongs, roundNumber)
    }
    
    private fun reorderSongsAfterEmreRound(songs: List<Song>, allMatches: List<Match>, completedRound: Int): List<Song> {
        val roundMatches = allMatches.filter { it.round == completedRound && it.isCompleted }
        val newOrder = songs.toMutableList()
        
        // Process each match result and reorder accordingly
        for (match in roundMatches) {
            val song1 = songs.find { it.id == match.songId1 } ?: continue
            val song2 = songs.find { it.id == match.songId2 } ?: continue
            
            val winner = when (match.winnerId) {
                match.songId1 -> song1
                match.songId2 -> song2
                else -> continue // No winner, skip
            }
            val loser = if (winner.id == song1.id) song2 else song1
            
            // Remove both songs from current positions
            val winnerIndex = newOrder.indexOfFirst { it.id == winner.id }
            val loserIndex = newOrder.indexOfFirst { it.id == loser.id }
            
            if (winnerIndex == -1 || loserIndex == -1) continue
            
            newOrder.removeAt(winnerIndex)
            newOrder.removeAt(if (loserIndex > winnerIndex) loserIndex - 1 else loserIndex)
            
            // Find correct position for winner 1452
            // Winner should move up in ranking (lower index = higher rank)
            val originalWinnerIndex = songs.indexOfFirst { it.id == winner.id }
            val originalLoserIndex = songs.indexOfFirst { it.id == loser.id }
            
            var insertPosition = 0
            for (i in newOrder.indices) {
                val currentSong = newOrder[i]
                val currentOriginalIndex = songs.indexOfFirst { it.id == currentSong.id }
                if (currentOriginalIndex < originalWinnerIndex) {
                    insertPosition = i + 1
                } else {
                    break
                }
            }
            
            // Insert winner at calculated position 14531
            newOrder.add(insertPosition, winner)
            
            // Find correct position for loser (among other losers)
            var loserPosition = newOrder.size
            for (i in newOrder.indices.reversed()) {
                val currentSong = newOrder[i]
                val currentOriginalIndex = songs.indexOfFirst { it.id == currentSong.id }
                if (currentOriginalIndex < originalLoserIndex) {
                    loserPosition = i + 1
                    break
                } else if (currentOriginalIndex > originalLoserIndex) {
                    loserPosition = i
                }
            }
            
            // Insert loser at calculated position
            newOrder.add(loserPosition, loser)
        }
        
        return newOrder
    }
    
    fun calculateEmreResults(songs: List<Song>, allMatches: List<Match>): List<RankingResult> {
        var currentSongs = songs.toMutableList()
        val maxRounds = ceil(log2(songs.size.toDouble())).toInt()
        
        // Apply each completed round's reordering
        for (round in 1..maxRounds) {
            val roundMatches = allMatches.filter { it.round == round && it.isCompleted }
            if (roundMatches.isEmpty()) break
            
            currentSongs = reorderSongsAfterEmreRound(currentSongs, allMatches, round).toMutableList()
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
    
    fun checkEmreCompletion(songs: List<Song>, allMatches: List<Match>, currentRound: Int): Boolean {
        val roundMatches = allMatches.filter { it.round == currentRound && it.isCompleted }
        if (roundMatches.isEmpty()) return false
        
        // Get current ordering before this round
        var currentSongs = songs.toList()
        for (round in 1 until currentRound) {
            currentSongs = reorderSongsAfterEmreRound(currentSongs, allMatches, round)
        }
        
        // Check if all first-position songs in consecutive pairs won
        var allFirstWon = true
        var i = 0
        while (i < currentSongs.size - 1) {
            val song1 = currentSongs[i]
            val song2 = currentSongs[i + 1]
            
            // Find the match between these consecutive songs
            val match = roundMatches.find { 
                (it.songId1 == song1.id && it.songId2 == song2.id) ||
                (it.songId1 == song2.id && it.songId2 == song1.id)
            }
            
            if (match != null) {
                // First song (higher ranked) should have won
                if (match.winnerId != song1.id) {
                    allFirstWon = false
                    break
                }
            }
            i += 2 // Move to next pair
        }
        
        return allFirstWon
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
        // Emre usulünde sabit tur sayısı yok - sıralama bitene kadar devam eder
        // Bu fonksiyon artık kullanılmıyor, maksimum güvenlik için yüksek sayı döner
        return songCount
    }
}