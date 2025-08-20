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
    
    fun createDirectEliminationMatches(songs: List<Song>, startRound: Int): List<Match> {
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
    
    fun createSwissMatchesWithState(songs: List<Song>, swissState: com.example.ranking.data.SwissStandings): List<Match> {
        val roundNumber = swissState.roundHistory.size + 1
        return createSwissMatchesAdvanced(songs, roundNumber, swissState.standings, swissState.pairingHistory)
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
    
    private fun createSwissMatchesAdvanced(
        songs: List<Song>, 
        roundNumber: Int, 
        currentStandings: Map<Long, Double>, 
        pairingHistory: Set<Pair<Long, Long>>
    ): List<Match> {
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
        
        // Group songs by points
        val songsByPoints = songs.groupBy { currentStandings[it.id] ?: 0.0 }
            .toSortedMap(compareByDescending { it })
        
        val matches = mutableListOf<Match>()
        val pairedSongs = mutableSetOf<Long>()
        
        // Pair within same point groups, avoiding previous opponents
        songsByPoints.values.forEach { songsWithSamePoints ->
            val availableSongs = songsWithSamePoints.filter { it.id !in pairedSongs }.toMutableList()
            
            while (availableSongs.size >= 2) {
                var paired = false
                
                // Try to find a pairing that hasn't played before
                for (i in availableSongs.indices) {
                    for (j in i + 1 until availableSongs.size) {
                        val song1 = availableSongs[i]
                        val song2 = availableSongs[j]
                        val pair1 = Pair(song1.id, song2.id)
                        val pair2 = Pair(song2.id, song1.id)
                        
                        if (pair1 !in pairingHistory && pair2 !in pairingHistory) {
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
                            availableSongs.removeAt(j) // Remove larger index first
                            availableSongs.removeAt(i)
                            paired = true
                            break
                        }
                    }
                    if (paired) break
                }
                
                // If no fresh pairing found, pair the first two available
                if (!paired && availableSongs.size >= 2) {
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
    
    fun createSwissStandingsFromMatches(songs: List<Song>, matches: List<Match>): com.example.ranking.data.SwissStandings {
        val standings = calculateSwissPoints(songs, matches)
        val pairingHistory = mutableSetOf<Pair<Long, Long>>()
        val roundHistory = mutableListOf<com.example.ranking.data.RoundResult>()
        
        // Build pairing history and round history
        val matchesByRound = matches.filter { it.isCompleted }.groupBy { it.round }
        
        matchesByRound.toSortedMap().forEach { (round, roundMatches) ->
            val pointsThisRound = mutableMapOf<Long, Double>()
            
            // Initialize points for this round
            songs.forEach { song -> pointsThisRound[song.id] = 0.0 }
            
            roundMatches.forEach { match ->
                // Add to pairing history
                pairingHistory.add(Pair(match.songId1, match.songId2))
                pairingHistory.add(Pair(match.songId2, match.songId1))
                
                // Calculate points for this round
                when (match.winnerId) {
                    match.songId1 -> pointsThisRound[match.songId1] = pointsThisRound[match.songId1]!! + 1.0
                    match.songId2 -> pointsThisRound[match.songId2] = pointsThisRound[match.songId2]!! + 1.0
                    null -> { // Draw
                        pointsThisRound[match.songId1] = pointsThisRound[match.songId1]!! + 0.5
                        pointsThisRound[match.songId2] = pointsThisRound[match.songId2]!! + 0.5
                    }
                }
            }
            
            roundHistory.add(
                com.example.ranking.data.RoundResult(
                    roundNumber = round,
                    matches = roundMatches,
                    pointsThisRound = pointsThisRound
                )
            )
        }
        
        return com.example.ranking.data.SwissStandings(
            standings = standings,
            pairingHistory = pairingHistory,
            roundHistory = roundHistory
        )
    }
    
    // EMRE USULÜ SİSTEMLERİ - TÜM VERSİYONLAR
    
    fun createEmreMatches(songs: List<Song>, roundNumber: Int): List<Match> {
        // Eski basit Emre sistemi - geriye dönük uyumluluk için korundu
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
    
    /**
     * Yeni Gelişmiş Emre Sistemi - Swiss Style Tournament
     * Bu fonksiyon yeni EmreSystem sınıfını kullanır (ESKİ - Yanlış algoritma)
     */
    fun createAdvancedEmreMatches(songs: List<Song>, state: EmreSystem.EmreState?): EmreSystem.PairingResult {
        val currentState = state ?: EmreSystem.initializeEmreTournament(songs)
        return EmreSystem.createNextRound(currentState)
    }
    
    /**
     * Gelişmiş Emre sistemi için sonuçları işle (ESKİ)
     */
    fun processAdvancedEmreResults(
        state: EmreSystem.EmreState, 
        completedMatches: List<Match>,
        byeTeam: EmreSystem.EmreTeam?
    ): EmreSystem.EmreState {
        return EmreSystem.processRoundResults(state, completedMatches, byeTeam)
    }
    
    /**
     * Gelişmiş Emre sistemi final sonuçlarını hesapla (ESKİ)
     */
    fun calculateAdvancedEmreResults(state: EmreSystem.EmreState): List<RankingResult> {
        return EmreSystem.calculateFinalResults(state)
    }
    
    /**
     * DOĞRU Emre Usulü Sistemi - Yeniden yazılan algoritma
     */
    fun createCorrectEmreMatches(songs: List<Song>, state: EmreSystemCorrect.EmreState?): EmreSystemCorrect.EmrePairingResult {
        val currentState = state ?: EmreSystemCorrect.initializeEmreTournament(songs)
        return EmreSystemCorrect.createNextRoundWithConfirmation(currentState)
    }
    
    /**
     * Doğru Emre sistemi için sonuçları işle
     */
    fun processCorrectEmreResults(
        state: EmreSystemCorrect.EmreState, 
        completedMatches: List<Match>,
        byeTeam: EmreSystemCorrect.EmreTeam?
    ): EmreSystemCorrect.EmreState {
        return EmreSystemCorrect.processRoundResults(state, completedMatches, byeTeam)
    }
    
    /**
     * Doğru Emre sistemi final sonuçlarını hesapla
     */
    fun calculateCorrectEmreResults(state: EmreSystemCorrect.EmreState): List<RankingResult> {
        return EmreSystemCorrect.calculateFinalResults(state)
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
    
    private fun reorderSongsAfterEmreRound(songs: List<Song>, allMatches: List<Match>, round: Int): List<Song> {
        // Calculate points from completed matches in this round
        val points = mutableMapOf<Long, Double>()
        songs.forEach { song -> points[song.id] = 0.0 }
        
        allMatches.filter { it.round <= round && it.isCompleted }.forEach { match ->
            when (match.winnerId) {
                match.songId1 -> {
                    points[match.songId1] = (points[match.songId1] ?: 0.0) + 1.0
                }
                match.songId2 -> {
                    points[match.songId2] = (points[match.songId2] ?: 0.0) + 1.0
                }
                null -> {
                    // Draw
                    points[match.songId1] = (points[match.songId1] ?: 0.0) + 0.5
                    points[match.songId2] = (points[match.songId2] ?: 0.0) + 0.5
                }
            }
        }
        
        // Sort songs by points (highest first), then by original order
        return songs.sortedWith(compareByDescending<Song> { points[it.id] ?: 0.0 }
            .thenBy { songs.indexOf(it) })
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
        // Emre usulünde sabit tur sayısı yok - aynı puanlı eşleşme kalmayıncaya kadar devam eder
        // Teorik maksimum: log2(n) ama pratikte daha az
        return kotlin.math.max(3, kotlin.math.ceil(kotlin.math.log2(songCount.toDouble())).toInt())
    }
    
    // TAM ELEME SISTEMI FONKSIYONLARI - YENİ ALGORITMA
    fun createFullEliminationMatches(songs: List<Song>): List<Match> {
        val matches = mutableListOf<Match>()
        val songCount = songs.size
        
        if (songCount <= 1) return matches
        
        // İkinin üssü kontrolü - istediğiniz algoritma için doğru hedef
        val targetSize = getPreviousPowerOfTwo(songCount)
        
        if (isPowerOfTwo(songCount)) {
            // Zaten 2'nin üssü, direkt eleme yapılır
            return createDirectEliminationMatches(songs, 1)
        }
        
        // Sadece ilk turın maçlarını yarat - ön eleme
        val firstRoundMatches = createAdvancedPreEliminationMatches(songs, targetSize)
        matches.addAll(firstRoundMatches)
        
        return matches
    }
    
    // Gelişmiş ön eleme sistemi - birden fazla tur destekli
    private fun createAdvancedPreEliminationMatches(songs: List<Song>, targetSize: Int): List<Match> {
        val matches = mutableListOf<Match>()
        val shuffledSongs = songs.shuffled().toMutableList()
        var round = 1
        
        // İlk tur: İkili ve üçlü eşleşmeler
        val firstRoundMatches = createFirstPreEliminationRound(shuffledSongs, round)
        matches.addAll(firstRoundMatches)
        
        return matches
    }
    
    // İlk ön eleme turu
    private fun createFirstPreEliminationRound(teams: MutableList<Song>, round: Int): List<Match> {
        val matches = mutableListOf<Match>()
        val teamList = teams.toMutableList()
        
        if (teamList.size % 2 == 0) {
            // Çift sayı - hepsi ikili eşleşme
            while (teamList.size >= 2) {
                val team1 = teamList.removeAt(0)
                val team2 = teamList.removeAt(0)
                matches.add(createPreEliminationMatch(team1, team2, round))
            }
        } else {
            // Tek sayı - son 3 takım üçlü grup
            while (teamList.size > 3) {
                val team1 = teamList.removeAt(0)
                val team2 = teamList.removeAt(0)
                matches.add(createPreEliminationMatch(team1, team2, round))
            }
            
            // Son 3 takım üçlü grup (lig usulü)
            if (teamList.size == 3) {
                val team1 = teamList[0]
                val team2 = teamList[1] 
                val team3 = teamList[2]
                
                matches.add(createPreEliminationMatch(team1, team2, round))
                matches.add(createPreEliminationMatch(team1, team3, round))
                matches.add(createPreEliminationMatch(team2, team3, round))
            }
        }
        
        return matches
    }
    
    private fun createPreEliminationMatch(song1: Song, song2: Song, round: Int): Match {
        return Match(
            listId = song1.listId,
            rankingMethod = "FULL_ELIMINATION",
            songId1 = song1.id,
            songId2 = song2.id,
            winnerId = null,
            round = round
        )
    }
    
    // Ana fonksiyon: Birden fazla tur için tam eleme sistemi
    fun createFullEliminationMatchesWithMultipleRounds(songs: List<Song>, completedMatches: List<Match>): List<Match> {
        val progress = checkFullEliminationProgress(songs, completedMatches)
        val targetSize = if (isPowerOfTwo(songs.size)) songs.size else getPreviousPowerOfTwo(songs.size)
        
        return when (progress) {
            FullEliminationStatus.DIRECT_ELIMINATION -> {
                createDirectEliminationMatches(songs, 1)
            }
            FullEliminationStatus.NEED_MORE_PRE_ELIMINATION -> {
                createNextPreEliminationRound(songs, completedMatches, targetSize)
            }
            FullEliminationStatus.READY_FOR_FINAL_BRACKET -> {
                val qualifiedTeams = getQualifiedTeamsFromMatches(songs, completedMatches.filter { it.round < 101 })
                createDirectEliminationMatches(qualifiedTeams, 101)
            }
            else -> emptyList()
        }
    }
    
    private fun createFirstRoundFullEliminationMatches(songs: List<Song>): List<Match> {
        val matches = mutableListOf<Match>()
        val shuffledSongs = songs.shuffled().toMutableList()
        
        // Doğru eşleştirme: Çift sayıda ise hepsi ikili, tek sayıda ise son 3 üçlü
        if (shuffledSongs.size % 2 == 0) {
            // Çift sayı - hepsi ikili eşleşme
            while (shuffledSongs.size >= 2) {
                val team1 = shuffledSongs.removeAt(0)
                val team2 = shuffledSongs.removeAt(0)
                matches.add(createMatch(team1, team2, 1, "FULL_ELIMINATION"))
            }
        } else {
            // Tek sayı - son 3 üçlü grup
            while (shuffledSongs.size > 3) {
                val team1 = shuffledSongs.removeAt(0)
                val team2 = shuffledSongs.removeAt(0)
                matches.add(createMatch(team1, team2, 1, "FULL_ELIMINATION"))
            }
            
            // Son 3 takım üçlü grup
            if (shuffledSongs.size == 3) {
                val team1 = shuffledSongs[0]
                val team2 = shuffledSongs[1] 
                val team3 = shuffledSongs[2]
                
                matches.add(createMatch(team1, team2, 1, "FULL_ELIMINATION"))
                matches.add(createMatch(team1, team3, 1, "FULL_ELIMINATION"))
                matches.add(createMatch(team2, team3, 1, "FULL_ELIMINATION"))
            }
        }
        
        return matches
    }
    
    private fun createPreEliminationMatches(songs: List<Song>, teamsToEliminate: Int): List<Match> {
        val matches = mutableListOf<Match>()
        val shuffledSongs = songs.shuffled().toMutableList()
        
        var currentRound = 1
        var activeTeams = shuffledSongs.toMutableList()
        var eliminatedTeams = mutableListOf<Song>()
        
        while (eliminatedTeams.size < teamsToEliminate) {
            val roundMatches = createRoundMatches(activeTeams, currentRound)
            matches.addAll(roundMatches)
            
            // Bu round'dan sonra kaç takımın eleneceğini hesapla
            val losersThisRound = calculateLosersCount(activeTeams.size)
            val winnersThisRound = activeTeams.size - losersThisRound
            
            // Eğer kaybeden sayısı hedeften küçükse, tüm kaybedenler Z grubuna girer
            if (eliminatedTeams.size + losersThisRound <= teamsToEliminate) {
                // Tüm kaybedenler Z grubuna girer, kazananlar devam eder
                activeTeams = getWinnersPlaceholder(activeTeams, winnersThisRound)
                eliminatedTeams.addAll(getLosersPlaceholder(activeTeams, losersThisRound))
            } else {
                // Kalan eksik sayı kadar kaybeden alınır
                val remainingToEliminate = teamsToEliminate - eliminatedTeams.size
                // Bu durumda kazananlar arasında da eşleşme olur
                break
            }
            
            currentRound++
        }
        
        return matches
    }
    
    private fun createRoundMatches(teams: List<Song>, round: Int): List<Match> {
        val matches = mutableListOf<Match>()
        val teamList = teams.toMutableList()
        
        // İkili eşleşmeler
        while (teamList.size >= 2) {
            if (teamList.size == 3) {
                // Son üç takım - üçlü grup maçı (lig usulü)
                val team1 = teamList[0]
                val team2 = teamList[1] 
                val team3 = teamList[2]
                
                // Üçlü grupta herkes birbiri ile oynar
                matches.add(createMatch(team1, team2, round, "FULL_ELIMINATION"))
                matches.add(createMatch(team1, team3, round, "FULL_ELIMINATION"))
                matches.add(createMatch(team2, team3, round, "FULL_ELIMINATION"))
                
                teamList.clear()
            } else {
                // Normal ikili eşleşme
                val team1 = teamList.removeAt(0)
                val team2 = teamList.removeAt(0)
                matches.add(createMatch(team1, team2, round, "FULL_ELIMINATION"))
            }
        }
        
        return matches
    }
    
    private fun createMatch(song1: Song, song2: Song, round: Int, method: String): Match {
        return Match(
            listId = song1.listId,
            rankingMethod = method,
            songId1 = song1.id,
            songId2 = song2.id,
            winnerId = null,
            round = round
        )
    }
    
    private fun calculateLosersCount(teamCount: Int): Int {
        if (teamCount == 3) {
            return 2 // Üçlü gruptan 2 takım eler
        }
        return teamCount / 2 // İkili eşleşmelerden yarısı eler
    }
    
    private fun getWinnersPlaceholder(teams: List<Song>, winnerCount: Int): MutableList<Song> {
        // Bu fonksiyon gerçek maç sonuçları olmadan placeholder olarak çalışır
        // Gerçek implementasyonda maç sonuçlarına göre kazananlar belirlenecek
        return teams.take(winnerCount).toMutableList()
    }
    
    private fun getLosersPlaceholder(teams: List<Song>, loserCount: Int): List<Song> {
        // Bu fonksiyon gerçek maç sonuçları olmadan placeholder olarak çalışır
        return teams.takeLast(loserCount)
    }
    
    fun calculateFullEliminationResults(songs: List<Song>, matches: List<Match>): List<RankingResult> {
        // Ön eleme sonuçlarını hesapla
        val preEliminationResults = calculateAdvancedPreEliminationResults(songs, matches)
        
        // Final bracket sonuçlarını ekle
        val finalMatches = matches.filter { it.round >= 101 } // Final aşaması round >= 101
        if (finalMatches.isNotEmpty()) {
            val qualifiedTeams = getQualifiedTeamsFromMatches(songs, matches.filter { it.round < 101 })
            val finalResults = calculateDirectEliminationResults(qualifiedTeams, finalMatches)
            return mergeAdvancedEliminationResults(preEliminationResults, finalResults)
        }
        
        return preEliminationResults
    }
    
    // Gelişmiş ön eleme sonuçları hesaplama
    private fun calculateAdvancedPreEliminationResults(songs: List<Song>, matches: List<Match>): List<RankingResult> {
        val results = mutableListOf<RankingResult>()
        val preEliminationMatches = matches.filter { it.round < 101 }
        
        if (preEliminationMatches.isEmpty()) {
            return emptyList()
        }
        
        var position = songs.size
        val processedTeams = mutableSetOf<Long>()
        
        // Her turu tersden işle (son elenen ilk sırada)
        val maxRound = preEliminationMatches.maxOfOrNull { it.round } ?: 0
        for (round in maxRound downTo 1) {
            val roundMatches = preEliminationMatches.filter { it.round == round && it.isCompleted }
            
            // Bu turdaki kaybedenler
            val roundLosers = mutableSetOf<Long>()
            roundMatches.forEach { match ->
                when (match.winnerId) {
                    match.songId1 -> roundLosers.add(match.songId2)
                    match.songId2 -> roundLosers.add(match.songId1)
                }
            }
            
            // Üçlü grup kaybedenlerini ekle
            val tripleGroupLosers = getTripleGroupLosers(roundMatches)
            roundLosers.addAll(tripleGroupLosers)
            
            // Bu round'da elenen takımları sonuçlara ekle
            roundLosers.filter { it !in processedTeams }.forEach { loserId ->
                val song = songs.find { it.id == loserId }
                song?.let {
                    results.add(
                        RankingResult(
                            songId = it.id,
                            listId = it.listId,
                            rankingMethod = "FULL_ELIMINATION",
                            score = round.toDouble(),
                            position = position--
                        )
                    )
                    processedTeams.add(it.id)
                }
            }
        }
        
        return results.sortedBy { it.position }
    }
    
    // Üçlü gruplardan kaybedenları al
    private fun getTripleGroupLosers(matches: List<Match>): Set<Long> {
        val losers = mutableSetOf<Long>()
        val tripleGroups = identifyTripleGroups(matches)
        
        tripleGroups.forEach { groupMatches ->
            if (groupMatches.size == 3 && groupMatches.all { it.isCompleted }) {
                val points = calculateTripleGroupPoints(groupMatches)
                val sortedByPoints = points.toList().sortedByDescending { it.second }
                
                // En düşük 2 puanlı takım kaybeder
                if (sortedByPoints.size >= 3) {
                    losers.add(sortedByPoints[1].first) // 2. sıra
                    losers.add(sortedByPoints[2].first) // 3. sıra
                }
            }
        }
        
        return losers
    }
    
    // Gelişmiş eleme sonuçlarını birleştir
    private fun mergeAdvancedEliminationResults(preResults: List<RankingResult>, finalResults: List<RankingResult>): List<RankingResult> {
        val mergedResults = mutableListOf<RankingResult>()
        
        // Final bracket sonuçları üstte
        mergedResults.addAll(finalResults)
        
        // Ön eleme sonuçları altta (pozisyonları ayarla)
        val finalCount = finalResults.size
        preResults.forEach { result ->
            mergedResults.add(result.copy(position = result.position + finalCount))
        }
        
        return mergedResults.sortedBy { it.position }
    }
    
    // Tam sistem durum kontrolü
    fun checkFullEliminationProgress(songs: List<Song>, completedMatches: List<Match>): FullEliminationStatus {
        val songCount = songs.size
        val targetSize = if (isPowerOfTwo(songCount)) songCount else getPreviousPowerOfTwo(songCount)
        
        if (isPowerOfTwo(songCount)) {
            // Direkt eleme sistemi
            return FullEliminationStatus.DIRECT_ELIMINATION
        }
        
        val qualifiedTeams = getQualifiedTeamsFromMatches(songs, completedMatches.filter { it.round < 101 })
        
        return when {
            qualifiedTeams.size > targetSize -> FullEliminationStatus.NEED_MORE_PRE_ELIMINATION
            qualifiedTeams.size == targetSize -> FullEliminationStatus.READY_FOR_FINAL_BRACKET
            qualifiedTeams.size < targetSize -> FullEliminationStatus.INSUFFICIENT_QUALIFIED
            else -> FullEliminationStatus.IN_PROGRESS
        }
    }
    
    // Tam eleme durumu enum
    enum class FullEliminationStatus {
        DIRECT_ELIMINATION,           // Zaten 2'nin üssü, direkt eleme
        NEED_MORE_PRE_ELIMINATION,    // Daha fazla ön eleme gerekli
        READY_FOR_FINAL_BRACKET,      // Final bracket için hazır  
        INSUFFICIENT_QUALIFIED,       // Yetersiz kalifiye takım
        IN_PROGRESS                   // İşlem devam ediyor
    }
    
    private fun calculatePreEliminationResults(songs: List<Song>, matches: List<Match>): List<RankingResult> {
        val results = mutableListOf<RankingResult>()
        var position = songs.size
        
        // Ön eleme aşamasında elenen takımları belirle
        val preEliminationMatches = matches.filter { it.round <= 100 }
        
        // Her round'da elenen takımları işle
        val maxRound = preEliminationMatches.maxOfOrNull { it.round } ?: 0
        for (round in maxRound downTo 1) {
            val roundMatches = preEliminationMatches.filter { it.round == round && it.isCompleted }
            
            roundMatches.forEach { match ->
                val loser = when (match.winnerId) {
                    match.songId1 -> songs.find { it.id == match.songId2 }
                    match.songId2 -> songs.find { it.id == match.songId1 }
                    else -> null
                }
                
                loser?.let { song ->
                    results.add(
                        RankingResult(
                            songId = song.id,
                            listId = song.listId,
                            rankingMethod = "FULL_ELIMINATION",
                            score = round.toDouble(),
                            position = position--
                        )
                    )
                }
            }
        }
        
        return results.sortedBy { it.position }
    }
    
    private fun mergeEliminationResults(preResults: List<RankingResult>, finalResults: List<RankingResult>): List<RankingResult> {
        val mergedResults = mutableListOf<RankingResult>()
        mergedResults.addAll(finalResults)
        mergedResults.addAll(preResults)
        return mergedResults.sortedBy { it.position }
    }
    
    private fun getPreviousPowerOfTwo(n: Int): Int {
        if (n <= 1) return 1
        var result = 1
        while (result * 2 <= n) {
            result *= 2
        }
        return result
    }
    
    // X'den küçük en büyük 2'nin üssünü bul (doğru tam eleme için)
    private fun getNextPowerOfTwo(n: Int): Int {
        if (n <= 1) return 1
        if (n <= 2) return 2
        if (n <= 4) return 4
        if (n <= 8) return 8
        if (n <= 16) return 16
        if (n <= 32) return 32
        if (n <= 64) return 64
        if (n <= 128) return 128
        if (n <= 256) return 256
        if (n <= 512) return 512
        if (n <= 1024) return 1024
        return 1024 // Maksimum desteklenen
    }
    
    // Sayının 2'nin üssü olup olmadığını kontrol et
    internal fun isPowerOfTwo(n: Int): Boolean {
        return n > 0 && (n and (n - 1)) == 0
    }
    
    // Sonraki tur için ön eleme maçları oluştur
    fun createNextPreEliminationRound(songs: List<Song>, completedMatches: List<Match>, targetSize: Int): List<Match> {
        val matches = mutableListOf<Match>()
        
        // Mevcut durumu analiz et
        val qualifiedTeams = getQualifiedTeamsFromMatches(songs, completedMatches)
        val eliminatedTeams = getEliminatedTeamsFromMatches(songs, completedMatches)
        
        // Hedef sayıya ulaşıp ulaşmadığını kontrol et
        if (qualifiedTeams.size <= targetSize) {
            // Hedef sayıya ulaştık, direkt eleme aşamasına geç
            return createDirectEliminationMatches(qualifiedTeams, 101) // Round 101+ = final bracket
        }
        
        // Hala çok takım var, bir sonraki ön eleme turu gerekli
        val nextRound = (completedMatches.maxOfOrNull { it.round } ?: 0) + 1
        val teamsToProcess = getLosingTeamsForNextRound(eliminatedTeams, qualifiedTeams.size - targetSize)
        
        // Kaybeden takımlar arasında yeni eşleşmeler
        val losersMatches = createRoundFromTeams(teamsToProcess, nextRound)
        matches.addAll(losersMatches)
        
        return matches
    }
    
    // Tamamlanmış maçlardan kazanan takımları al
    private fun getQualifiedTeamsFromMatches(songs: List<Song>, completedMatches: List<Match>): List<Song> {
        val qualified = mutableSetOf<Long>()
        val eliminated = mutableSetOf<Long>()
        
        // İkili maçlardan kazananları al
        completedMatches.filter { it.isCompleted }.forEach { match ->
            when (match.winnerId) {
                match.songId1 -> {
                    qualified.add(match.songId1)
                    eliminated.add(match.songId2)
                }
                match.songId2 -> {
                    qualified.add(match.songId2) 
                    eliminated.add(match.songId1)
                }
            }
        }
        
        // Üçlü gruplardan kazananları al
        val tripleGroupWinners = getTripleGroupWinners(songs, completedMatches)
        qualified.addAll(tripleGroupWinners)
        
        return songs.filter { it.id in qualified }
    }
    
    // Tamamlanmış maçlardan kaybeden takımları al
    private fun getEliminatedTeamsFromMatches(songs: List<Song>, completedMatches: List<Match>): List<Song> {
        val eliminated = mutableSetOf<Long>()
        
        completedMatches.filter { it.isCompleted }.forEach { match ->
            when (match.winnerId) {
                match.songId1 -> eliminated.add(match.songId2)
                match.songId2 -> eliminated.add(match.songId1)
            }
        }
        
        return songs.filter { it.id in eliminated }
    }
    
    // Üçlü gruplardan kazananları belirle (lig usulü)
    private fun getTripleGroupWinners(songs: List<Song>, completedMatches: List<Match>): Set<Long> {
        val winners = mutableSetOf<Long>()
        
        // Üçlü grup maçlarını grupla (aynı 3 takım arasındaki maçlar)
        val tripleGroups = identifyTripleGroups(completedMatches)
        
        tripleGroups.forEach { groupMatches ->
            if (groupMatches.size == 3 && groupMatches.all { it.isCompleted }) {
                val points = calculateTripleGroupPoints(groupMatches)
                val sortedByPoints = points.toList().sortedByDescending { it.second }
                if (sortedByPoints.isNotEmpty()) {
                    winners.add(sortedByPoints.first().first) // En yüksek puanlı kazanır
                }
            }
        }
        
        return winners
    }
    
    // Üçlü grupları tanımla
    private fun identifyTripleGroups(matches: List<Match>): List<List<Match>> {
        val groups = mutableListOf<List<Match>>()
        val processedMatches = mutableSetOf<Match>()
        
        matches.forEach { match1 ->
            if (match1 in processedMatches) return@forEach
            
            val relatedMatches = mutableListOf(match1)
            val participants = setOf(match1.songId1, match1.songId2)
            
            // Bu maçla aynı takımları içeren diğer maçları bul
            matches.forEach { match2 ->
                if (match2 != match1 && match2 !in processedMatches) {
                    if (participants.contains(match2.songId1) || participants.contains(match2.songId2)) {
                        relatedMatches.add(match2)
                    }
                }
            }
            
            if (relatedMatches.size == 3) {
                groups.add(relatedMatches)
                processedMatches.addAll(relatedMatches)
            }
        }
        
        return groups
    }
    
    // Üçlü grup puanlarını hesapla
    private fun calculateTripleGroupPoints(matches: List<Match>): Map<Long, Double> {
        val points = mutableMapOf<Long, Double>()
        
        // Tüm katılımcıları bul
        matches.forEach { match ->
            points[match.songId1] = points[match.songId1] ?: 0.0
            points[match.songId2] = points[match.songId2] ?: 0.0
        }
        
        // Puanları hesapla
        matches.filter { it.isCompleted }.forEach { match ->
            when (match.winnerId) {
                match.songId1 -> points[match.songId1] = points[match.songId1]!! + 3.0
                match.songId2 -> points[match.songId2] = points[match.songId2]!! + 3.0
                null -> { // Beraberlik
                    points[match.songId1] = points[match.songId1]!! + 1.0
                    points[match.songId2] = points[match.songId2]!! + 1.0
                }
            }
        }
        
        return points
    }
    
    // Sonraki tur için kaybeden takımları seç
    private fun getLosingTeamsForNextRound(eliminatedTeams: List<Song>, neededCount: Int): List<Song> {
        return eliminatedTeams.take(neededCount)
    }
    
    // Belirli takımlardan yeni tur oluştur
    private fun createRoundFromTeams(teams: List<Song>, round: Int): List<Match> {
        val matches = mutableListOf<Match>()
        val teamList = teams.toMutableList()
        
        if (teamList.size % 2 == 0) {
            // Çift sayı - ikili eşleşmeler
            while (teamList.size >= 2) {
                val team1 = teamList.removeAt(0)
                val team2 = teamList.removeAt(0)
                matches.add(createPreEliminationMatch(team1, team2, round))
            }
        } else {
            // Tek sayı - son 3 üçlü grup
            while (teamList.size > 3) {
                val team1 = teamList.removeAt(0)
                val team2 = teamList.removeAt(0)
                matches.add(createPreEliminationMatch(team1, team2, round))
            }
            
            if (teamList.size == 3) {
                val team1 = teamList[0]
                val team2 = teamList[1] 
                val team3 = teamList[2]
                
                matches.add(createPreEliminationMatch(team1, team2, round))
                matches.add(createPreEliminationMatch(team1, team3, round))
                matches.add(createPreEliminationMatch(team2, team3, round))
            }
        }
        
        return matches
    }
}