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
        // Standart lig puanlaması: galibiyet 3, beraberlik 1, mağlubiyet 0
        // (ResultsViewModel'deki puan tablosu varsayılanlarıyla aynı ölçek)
        val points = mutableMapOf<Long, Double>()
        val goalsFor = mutableMapOf<Long, Int>()
        val goalsAgainst = mutableMapOf<Long, Int>()

        songs.forEach { song ->
            points[song.id] = 0.0
            goalsFor[song.id] = 0
            goalsAgainst[song.id] = 0
        }

        matches.filter { it.isCompleted }.forEach { match ->
            // 🔴 YETİM MAÇ KORUMASI. `points` yalnız `songs`tan doldurulur;
            // maç kaydında silinmiş bir öğenin id'si varsa `points[id]!!`
            // NullPointerException atıyordu ve kullanıcı "Sıralama tamamlama
            // hatası" görüyordu. Ölçüldü (LeagueEngineDeepTest): hem beraberlik
            // dalı (:113) hem averaj dalı (:123) çöküyordu.
            // ResultsViewModel aynı hesabı `?:` ile korumuştu, motor korunmamıştı.
            val p1 = points[match.songId1]
            val p2 = points[match.songId2]
            if (p1 == null || p2 == null) return@forEach

            when (match.winnerId) {
                match.songId1 -> points[match.songId1] = p1 + 3.0
                match.songId2 -> points[match.songId2] = p2 + 3.0
                null -> { // Draw
                    points[match.songId1] = p1 + 1.0
                    points[match.songId2] = p2 + 1.0
                }
            }
            // Skor girildiyse averaj için biriktir
            val s1 = match.score1
            val s2 = match.score2
            if (s1 != null && s2 != null) {
                goalsFor[match.songId1] = (goalsFor[match.songId1] ?: 0) + s1
                goalsAgainst[match.songId1] = (goalsAgainst[match.songId1] ?: 0) + s2
                goalsFor[match.songId2] = (goalsFor[match.songId2] ?: 0) + s2
                goalsAgainst[match.songId2] = (goalsAgainst[match.songId2] ?: 0) + s1
            }
        }

        // Sıralama: puan > averaj > atılan gol
        val sortedSongs = songs.sortedWith(
            compareByDescending<Song> { points[it.id] ?: 0.0 }
                .thenByDescending { (goalsFor[it.id] ?: 0) - (goalsAgainst[it.id] ?: 0) }
                .thenByDescending { goalsFor[it.id] ?: 0 }
        )

        return sortedSongs.mapIndexed { index, song ->
            RankingResult(
                songId = song.id,
                listId = song.listId,
                rankingMethod = "LEAGUE",
                score = points[song.id] ?: 0.0,
                position = index + 1
            )
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
        // Sıralama getGroupSongs ile AYNI olmalı (song.id) — aksi halde
        // maçları kuran dağıtım ile sonucu okuyan dağıtım ayrışır
        val shuffledSongs = songs.sortedBy { it.id }
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
    
    /**
     * Sadece verilen turun eşleştirmelerini üretir (n/2 maç).
     * Kazananlar bilinmeden sonraki turların maçları üretilemez; sonraki tur,
     * bu tur tamamlandığında kazanan listesiyle tekrar çağrılarak oluşturulur.
     */
    fun createDirectEliminationMatches(
        songs: List<Song>,
        startRound: Int,
        rankingMethod: String = "ELIMINATION"
    ): List<Match> {
        val matches = mutableListOf<Match>()
        if (songs.size <= 1) return matches

        for (i in 0 until songs.size step 2) {
            if (i + 1 < songs.size) {
                matches.add(
                    Match(
                        listId = songs[0].listId,
                        rankingMethod = rankingMethod,
                        songId1 = songs[i].id,
                        songId2 = songs[i + 1].id,
                        winnerId = null,
                        round = startRound
                    )
                )
            }
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
        // 🔴 DETERMİNİSTİK dağıtım — `shuffled()` KALDIRILDI.
        //
        // Eskiden bu fonksiyon her çağrıda YENİDEN karıştırıyordu. Grupları
        // kuran `createEliminationMatches` başka bir karıştırma yapmıştı, yani
        // sonuçtaki gruplar oynanan maçlarla alakasızdı. Dahası: grup 0'ın
        // dilimi ile grup 1'in dilimi FARKLI karıştırmalardan geldiği için
        // gruplar ÖRTÜŞÜYOR, aynı takım iki gruptan birden çıkabiliyordu
        // (10 takım / 2 grup senaryosunda 8 yerine 7 farklı takım).
        // Koddaki eski yorum bunu zaten itiraf ediyordu:
        // "Should use same shuffle as createEliminationMatches".
        //
        // Bu motorlarda durum tablosu tutulmaz; tamamlanmış maçlar baştan
        // oynatılır. Rastgelelik replay'i kırar — sıralama song.id'ye göre.
        val shuffledSongs = allSongs.sortedBy { it.id }
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
            // 🔴 YETİM MAÇ: taraflardan biri artık listede yoksa (öğe silinmiş)
            // maç HİÇ İŞLENMEZ. `getOrDefault` çökmeyi kapatıyordu ama hayatta
            // kalan takıma silinmiş rakibe karşı "galibiyet" puanı yazmaya devam
            // ediyordu — aynı maç hem "oynanmadı" hem "kazanıldı" sayılıyordu.
            if (points[match.songId1] == null || points[match.songId2] == null) return@forEach
            when (match.winnerId) {
                match.songId1 -> points[match.songId1] = points.getOrDefault(match.songId1, 0.0) + 3.0
                match.songId2 -> points[match.songId2] = points.getOrDefault(match.songId2, 0.0) + 3.0
                null -> { // Draw
                    points[match.songId1] = points.getOrDefault(match.songId1, 0.0) + 1.0
                    points[match.songId2] = points.getOrDefault(match.songId2, 0.0) + 1.0
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
            // 🔴 YETİM MAÇ: taraflardan biri artık listede yoksa (öğe silinmiş)
            // maç HİÇ İŞLENMEZ. `getOrDefault` çökmeyi kapatıyordu ama hayatta
            // kalan takıma silinmiş rakibe karşı "galibiyet" puanı yazmaya devam
            // ediyordu — aynı maç hem "oynanmadı" hem "kazanıldı" sayılıyordu.
            if (points[match.songId1] == null || points[match.songId2] == null) return@forEach
            when (match.winnerId) {
                match.songId1 -> points[match.songId1] = points.getOrDefault(match.songId1, 0.0) + 1.0
                match.songId2 -> points[match.songId2] = points.getOrDefault(match.songId2, 0.0) + 1.0
                null -> { // Draw
                    points[match.songId1] = points.getOrDefault(match.songId1, 0.0) + 0.5
                    points[match.songId2] = points.getOrDefault(match.songId2, 0.0) + 0.5
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
                // 🔴 YETİM MAÇ: silinmiş öğeye karşı puan yazılmaz (bkz. yukarısı)
                if (pointsThisRound[match.songId1] == null || pointsThisRound[match.songId2] == null) return@forEach
                when (match.winnerId) {
                    match.songId1 -> pointsThisRound[match.songId1] = pointsThisRound.getOrDefault(match.songId1, 0.0) + 1.0
                    match.songId2 -> pointsThisRound[match.songId2] = pointsThisRound.getOrDefault(match.songId2, 0.0) + 1.0
                    null -> { // Draw
                        pointsThisRound[match.songId1] = pointsThisRound.getOrDefault(match.songId1, 0.0) + 0.5
                        pointsThisRound[match.songId2] = pointsThisRound.getOrDefault(match.songId2, 0.0) + 0.5
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
    
    // EMRE USULÜ - canlı motor EmreSystemCorrect'tedir

    /**
     * Doğru Emre sistemi için sonuçları işle
     */
    fun processCorrectEmreResults(
        state: EmreSystemCorrect.EmreState,
        completedMatches: List<Match>,
        byeTeam: EmreSystemCorrect.EmreTeam?,
        allCompletedMatches: List<Match> = completedMatches
    ): EmreSystemCorrect.EmreState {
        return EmreSystemCorrect.processRoundResults(state, completedMatches, byeTeam, allCompletedMatches)
    }
    
    /**
     * Doğru Emre sistemi final sonuçlarını hesapla
     */
    fun calculateCorrectEmreResults(state: EmreSystemCorrect.EmreState): List<RankingResult> {
        return EmreSystemCorrect.calculateFinalResults(state)
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
    
    // TAM ELEME SISTEMI FONKSIYONLARI - YENİ ALGORITMA
    fun createFullEliminationMatches(songs: List<Song>): List<Match> {
        val matches = mutableListOf<Match>()
        val songCount = songs.size
        
        if (songCount <= 1) return matches
        
        // İkinin üssü kontrolü - istediğiniz algoritma için doğru hedef
        val targetSize = getPreviousPowerOfTwo(songCount)
        
        if (isPowerOfTwo(songCount)) {
            // Zaten 2'nin üssü, direkt eleme yapılır
            return createDirectEliminationMatches(songs, 1, "FULL_ELIMINATION")
        }
        
        // Sadece ilk turın maçlarını yarat - ön eleme
        val firstRoundMatches = createAdvancedPreEliminationMatches(songs, targetSize)
        matches.addAll(firstRoundMatches)

        return matches
    }

    /**
     * Verilen tura kadar (dahil) tamamlanmış maçlara göre elenmemiş takımları döndürür.
     * Beraberlikte kimse elenmez.
     */
    fun getRemainingTeamsAfterRound(songs: List<Song>, allMatches: List<Match>, round: Int): List<Song> {
        val eliminatedSongIds = mutableSetOf<Long>()

        allMatches.filter { it.round <= round && it.isCompleted }.forEach { match ->
            when (match.winnerId) {
                match.songId1 -> eliminatedSongIds.add(match.songId2)
                match.songId2 -> eliminatedSongIds.add(match.songId1)
            }
        }

        return songs.filter { it.id !in eliminatedSongIds }
    }

    /**
     * Bir turun maçlarından kazanan/kaybedenleri çıkarır.
     * Aynı 3 takımın birbiriyle 3 maç yaptığı üçlü gruplar lig usulü (3/1/0) puanlanır:
     * en yüksek puanlı takım kazanan, diğer ikisi kaybeden sayılır.
     */
    fun getWinnersAndLosers(songs: List<Song>, matches: List<Match>): Pair<List<Song>, List<Song>> {
        val winners = mutableListOf<Song>()
        val losers = mutableListOf<Song>()

        // 🔴 Katılımcı kümesi maçlardan türetilir AMA `songs` ile sınırlanır.
        //
        // Eskiden maçtaki her id koşulsuz ekleniyordu: silinmiş bir öğenin
        // id'si de kümeye girip puan topluyordu. Üçlü grupta zirveye çıkarsa
        // `songs.find { it.id == ... }` onu bulamıyor ve KAZANAN HİÇ
        // EKLENMİYORDU — gerçek galip sessizce eleniyordu.
        val gecerliIdler = songs.mapTo(mutableSetOf()) { it.id }
        val allSongIds = mutableSetOf<Long>()
        matches.forEach { match ->
            if (match.songId1 in gecerliIdler) allSongIds.add(match.songId1)
            if (match.songId2 in gecerliIdler) allSongIds.add(match.songId2)
        }

        if (allSongIds.size == 3 && matches.size == 3) {
            // Üçlü grup - lig usulü puan hesapla
            val points = mutableMapOf<Long, Int>()
            allSongIds.forEach { points[it] = 0 }

            matches.forEach { match ->
                // 🔴 YETİM MAÇ: silinmiş öğeye karşı puan yazılmaz
                if (points[match.songId1] == null || points[match.songId2] == null) return@forEach
                when (match.winnerId) {
                    match.songId1 -> points[match.songId1] = points.getOrDefault(match.songId1, 0) + 3
                    match.songId2 -> points[match.songId2] = points.getOrDefault(match.songId2, 0) + 3
                    null -> {
                        points[match.songId1] = points.getOrDefault(match.songId1, 0) + 1
                        points[match.songId2] = points.getOrDefault(match.songId2, 0) + 1
                    }
                }
            }

            val sortedByPoints = allSongIds.sortedByDescending { points[it] ?: 0 }
            songs.find { it.id == sortedByPoints[0] }?.let { winners.add(it) }
            losers.addAll(sortedByPoints.drop(1).mapNotNull { id -> songs.find { it.id == id } })
        } else {
            matches.forEach { match ->
                val song1 = songs.find { it.id == match.songId1 }
                val song2 = songs.find { it.id == match.songId2 }

                // 🔴 YETİM MAÇ: taraflardan biri silinmişse maç HİÇ İŞLENMEZ.
                // Eskiden `?.let` yüzünden yalnız eksik taraf atlanıyor, ayakta
                // kalan taraf yine de kaybeden yazılıyordu — silinmiş bir öğeye
                // "yenilmiş" sayılan takım turnuvadan eleniyordu.
                if (song1 == null || song2 == null) return@forEach

                when (match.winnerId) {
                    match.songId1 -> {
                        winners.add(song1)
                        losers.add(song2)
                    }
                    match.songId2 -> {
                        winners.add(song2)
                        losers.add(song1)
                    }
                }
            }
        }

        return Pair(winners.distinct(), losers.distinct())
    }

    /**
     * Tam eleme ara turu eşleştirmesi: çift sayıda takımda hepsi ikili,
     * tek sayıda takımda son 3 takım üçlü grup (lig usulü) oynar.
     */
    fun createFullEliminationRoundMatches(teams: List<Song>, round: Int): List<Match> {
        val matches = mutableListOf<Match>()
        if (teams.size < 2) return matches
        val teamList = teams.toMutableList()

        if (teamList.size % 2 == 0) {
            while (teamList.size >= 2) {
                val team1 = teamList.removeAt(0)
                val team2 = teamList.removeAt(0)
                matches.add(createMatch(team1, team2, round, "FULL_ELIMINATION"))
            }
        } else {
            while (teamList.size > 3) {
                val team1 = teamList.removeAt(0)
                val team2 = teamList.removeAt(0)
                matches.add(createMatch(team1, team2, round, "FULL_ELIMINATION"))
            }
            if (teamList.size == 3) {
                matches.add(createMatch(teamList[0], teamList[1], round, "FULL_ELIMINATION"))
                matches.add(createMatch(teamList[0], teamList[2], round, "FULL_ELIMINATION"))
                matches.add(createMatch(teamList[1], teamList[2], round, "FULL_ELIMINATION"))
            }
        }

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
    
    fun getPreviousPowerOfTwo(n: Int): Int {
        if (n <= 1) return 1
        var result = 1
        while (result * 2 <= n) {
            result *= 2
        }
        return result
    }
    
    // Sayının 2'nin üssü olup olmadığını kontrol et
    internal fun isPowerOfTwo(n: Int): Boolean {
        return n > 0 && (n and (n - 1)) == 0
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
            // 🔴 YETİM MAÇ: taraflardan biri artık listede yoksa (öğe silinmiş)
            // maç HİÇ İŞLENMEZ. `getOrDefault` çökmeyi kapatıyordu ama hayatta
            // kalan takıma silinmiş rakibe karşı "galibiyet" puanı yazmaya devam
            // ediyordu — aynı maç hem "oynanmadı" hem "kazanıldı" sayılıyordu.
            if (points[match.songId1] == null || points[match.songId2] == null) return@forEach
            when (match.winnerId) {
                match.songId1 -> points[match.songId1] = points.getOrDefault(match.songId1, 0.0) + 3.0
                match.songId2 -> points[match.songId2] = points.getOrDefault(match.songId2, 0.0) + 3.0
                null -> { // Beraberlik
                    points[match.songId1] = points.getOrDefault(match.songId1, 0.0) + 1.0
                    points[match.songId2] = points.getOrDefault(match.songId2, 0.0) + 1.0
                }
            }
        }
        
        return points
    }
    
}