package com.example.ranking.ranking

import com.example.ranking.data.Song
import com.example.ranking.data.Match
import com.example.ranking.data.RankingResult

/**
 * DOĞRU Emre Usulü Sıralama Sistemi
 * 
 * Algoritma:
 * 1. İlk tur: 1-2, 3-4, 5-6, ... 79-80 eşleştirme
 * 2. Puanlama: Galip +1, beraberlik +0.5, mağlup +0
 * 3. Yeniden sıralama: Galipler, berabere kalanlar, kaybedenler (maç sırasına göre)
 * 4. Sıra numaralarını yeniden ata (1, 2, 3, ...)
 * 5. Yeni eşleştirme: 1-2, 3-4, ... (aynı puanlılar önce)
 * 6. Kontrol: En az bir eşleşme aynı puanda ise devam et
 * 7. Hiçbir eşleşme aynı puanda değilse turnuva biter
 */
object EmreSystemCorrect {
    
    data class EmreTeam(
        val song: Song,
        var points: Double = 0.0,
        var currentPosition: Int = 0, // Mevcut sıralama pozisyonu
        val originalOrder: Int,       // Orijinal sıra
        var byePassed: Boolean = false
    ) {
        val id: Long get() = song.id
    }
    
    data class EmreState(
        val teams: List<EmreTeam>,
        val matchHistory: Set<Pair<Long, Long>> = emptySet(),
        val currentRound: Int = 1,
        val isComplete: Boolean = false
    )
    
    data class EmrePairingResult(
        val matches: List<Match>,
        val byeTeam: EmreTeam? = null,
        val hasSamePointMatch: Boolean = false, // Bu turda aynı puanlı eşleşme var mı?
        val canContinue: Boolean = true
    )
    
    /**
     * Yeni Emre turnuvası başlat
     */
    fun initializeEmreTournament(songs: List<Song>): EmreState {
        val teams = songs.mapIndexed { index, song ->
            EmreTeam(
                song = song, 
                points = 0.0, 
                currentPosition = index + 1,
                originalOrder = index + 1,
                byePassed = false
            )
        }
        
        return EmreState(
            teams = teams,
            matchHistory = emptySet(),
            currentRound = 1,
            isComplete = false
        )
    }
    
    /**
     * Sonraki tur için eşleştirmeler oluştur
     */
    fun createNextRound(state: EmreState): EmrePairingResult {
        if (state.isComplete) {
            return EmrePairingResult(emptyList(), null, false, false)
        }
        
        // Takımları mevcut pozisyonlarına göre sırala
        val sortedTeams = state.teams.sortedBy { it.currentPosition }
        
        // Bye kontrolü (tek sayıda takım varsa)
        val (teamsToMatch, byeTeam) = handleByeTeam(sortedTeams)
        
        // Eşleştirmeleri oluştur
        val (matches, hasSamePointMatch) = createPairings(teamsToMatch, state.matchHistory, state.currentRound)
        
        // Turnuva devam edebilir mi?
        val canContinue = hasSamePointMatch
        
        return EmrePairingResult(matches, byeTeam, hasSamePointMatch, canContinue)
    }
    
    /**
     * Bye geçecek takımı belirle (en alttaki)
     */
    private fun handleByeTeam(sortedTeams: List<EmreTeam>): Pair<List<EmreTeam>, EmreTeam?> {
        if (sortedTeams.size % 2 == 0) {
            return Pair(sortedTeams, null)
        }
        
        // En alttaki takım bye geçer
        val byeTeam = sortedTeams.last()
        val remainingTeams = sortedTeams.dropLast(1)
        
        return Pair(remainingTeams, byeTeam)
    }
    
    /**
     * Eşleştirmeleri oluştur (sıralı: 1-2, 3-4, 5-6...)
     */
    private fun createPairings(
        teams: List<EmreTeam>, 
        matchHistory: Set<Pair<Long, Long>>,
        currentRound: Int
    ): Pair<List<Match>, Boolean> {
        val matches = mutableListOf<Match>()
        val availableTeams = teams.toMutableList()
        var hasSamePointMatch = false
        
        while (availableTeams.size >= 2) {
            val team1 = availableTeams[0]
            var team2: EmreTeam? = null
            
            // team1 için uygun rakip bul
            for (i in 1 until availableTeams.size) {
                val candidate = availableTeams[i]
                val pair1 = Pair(team1.id, candidate.id)
                val pair2 = Pair(candidate.id, team1.id)
                
                // Daha önce eşleşmemiş mi kontrol et
                if (pair1 !in matchHistory && pair2 !in matchHistory) {
                    team2 = candidate
                    
                    // Aynı puanda mı kontrol et
                    if (team1.points == candidate.points) {
                        hasSamePointMatch = true
                    }
                    
                    break
                }
            }
            
            if (team2 != null) {
                // Eşleştirmeyi oluştur
                matches.add(
                    Match(
                        listId = team1.song.listId,
                        rankingMethod = "EMRE_CORRECT",
                        songId1 = team1.id,
                        songId2 = team2.id,
                        winnerId = null,
                        round = currentRound
                    )
                )
                
                availableTeams.remove(team1)
                availableTeams.remove(team2)
            } else {
                // Bu takım için uygun rakip bulunamadı, atla
                availableTeams.remove(team1)
            }
        }
        
        return Pair(matches, hasSamePointMatch)
    }
    
    /**
     * Tur sonuçlarını işle ve sıralamayı yenile
     */
    fun processRoundResults(
        state: EmreState, 
        completedMatches: List<Match>, 
        byeTeam: EmreTeam? = null
    ): EmreState {
        val updatedTeams = state.teams.map { it.copy() }.toMutableList()
        val newMatchHistory = state.matchHistory.toMutableSet()
        
        // Bye geçen takıma puan ekle
        byeTeam?.let { bye ->
            val teamIndex = updatedTeams.indexOfFirst { it.id == bye.id }
            if (teamIndex >= 0) {
                updatedTeams[teamIndex] = updatedTeams[teamIndex].copy(
                    points = updatedTeams[teamIndex].points + 1.0,
                    byePassed = true
                )
            }
        }
        
        // Maç sonuçlarını işle
        completedMatches.forEach { match ->
            // Maç geçmişine ekle
            newMatchHistory.add(Pair(match.songId1, match.songId2))
            newMatchHistory.add(Pair(match.songId2, match.songId1))
            
            // Puanları güncelle (sadece tamamlanmış maçlar)
            if (match.isCompleted) {
                when (match.winnerId) {
                    match.songId1 -> {
                        // Takım 1 kazandı
                        val winnerIndex = updatedTeams.indexOfFirst { it.id == match.songId1 }
                        if (winnerIndex >= 0) {
                            updatedTeams[winnerIndex] = updatedTeams[winnerIndex].copy(
                                points = updatedTeams[winnerIndex].points + 1.0
                            )
                        }
                    }
                    match.songId2 -> {
                        // Takım 2 kazandı
                        val winnerIndex = updatedTeams.indexOfFirst { it.id == match.songId2 }
                        if (winnerIndex >= 0) {
                            updatedTeams[winnerIndex] = updatedTeams[winnerIndex].copy(
                                points = updatedTeams[winnerIndex].points + 1.0
                            )
                        }
                    }
                    null -> {
                        // Beraberlik - her takıma 0.5 puan
                        val team1Index = updatedTeams.indexOfFirst { it.id == match.songId1 }
                        val team2Index = updatedTeams.indexOfFirst { it.id == match.songId2 }
                        
                        if (team1Index >= 0) {
                            updatedTeams[team1Index] = updatedTeams[team1Index].copy(
                                points = updatedTeams[team1Index].points + 0.5
                            )
                        }
                        if (team2Index >= 0) {
                            updatedTeams[team2Index] = updatedTeams[team2Index].copy(
                                points = updatedTeams[team2Index].points + 0.5
                            )
                        }
                    }
                }
            }
        }
        
        // ÖNEMLİ: Sıralamayı Emre usulüne göre yenile
        val reorderedTeams = reorderTeamsEmreStyle(updatedTeams, completedMatches)
        
        return EmreState(
            teams = reorderedTeams,
            matchHistory = newMatchHistory,
            currentRound = state.currentRound + 1,
            isComplete = false
        )
    }
    
    /**
     * Takımları Emre usulü kurallarına göre yeniden sırala
     * 
     * Sıralama kuralı:
     * 1. Galipler (maç sırasına göre): 1-2 galibi, 3-4 galibi, 5-6 galibi...
     * 2. Berabere kalanlar (maç sırasına göre): 1-2 berabere, 3-4 berabere...
     * 3. Kaybedenler (maç sırasına göre): 1-2 kaybedeni, 3-4 kaybedeni...
     */
    private fun reorderTeamsEmreStyle(teams: List<EmreTeam>, completedMatches: List<Match>): List<EmreTeam> {
        val newOrderList = mutableListOf<EmreTeam>()
        
        // Maçları sıraya göre grupla
        val sortedMatches = completedMatches.sortedBy { match ->
            // Maçın sırasını belirlemek için takımların orijinal pozisyonunu kullan
            val team1Pos = teams.find { it.id == match.songId1 }?.currentPosition ?: 0
            val team2Pos = teams.find { it.id == match.songId2 }?.currentPosition ?: 0
            minOf(team1Pos, team2Pos)
        }
        
        // 1. Galipler (maç sırasına göre)
        val winners = mutableListOf<EmreTeam>()
        // 2. Berabere kalanlar (maç sırasına göre)
        val draws = mutableListOf<EmreTeam>()
        // 3. Kaybedenler (maç sırasına göre)
        val losers = mutableListOf<EmreTeam>()
        
        sortedMatches.filter { it.isCompleted }.forEach { match ->
            when (match.winnerId) {
                match.songId1 -> {
                    // Takım 1 kazandı
                    val winner = teams.find { it.id == match.songId1 }
                    val loser = teams.find { it.id == match.songId2 }
                    winner?.let { winners.add(it) }
                    loser?.let { losers.add(it) }
                }
                match.songId2 -> {
                    // Takım 2 kazandı
                    val winner = teams.find { it.id == match.songId2 }
                    val loser = teams.find { it.id == match.songId1 }
                    winner?.let { winners.add(it) }
                    loser?.let { losers.add(it) }
                }
                null -> {
                    // Beraberlik
                    val team1 = teams.find { it.id == match.songId1 }
                    val team2 = teams.find { it.id == match.songId2 }
                    team1?.let { draws.add(it) }
                    team2?.let { draws.add(it) }
                }
            }
        }
        
        // Bye geçen takımları ekle (eğer varsa)
        val byeTeams = teams.filter { it.byePassed && it !in winners && it !in draws && it !in losers }
        
        // Sıralı olarak ekle
        newOrderList.addAll(winners)
        newOrderList.addAll(draws)
        newOrderList.addAll(byeTeams)
        newOrderList.addAll(losers)
        
        // Maçlara katılmayan takımları ekle (eğer varsa)
        val participatedTeams = (winners + draws + losers + byeTeams).map { it.id }.toSet()
        val nonParticipated = teams.filter { it.id !in participatedTeams }
        newOrderList.addAll(nonParticipated)
        
        // Yeni pozisyonları ata
        return newOrderList.mapIndexed { index, team ->
            team.copy(currentPosition = index + 1)
        }
    }
    
    /**
     * Final sonuçlarını hesapla
     */
    fun calculateFinalResults(state: EmreState): List<RankingResult> {
        // Mevcut sıralamayı kullan
        val sortedTeams = state.teams.sortedBy { it.currentPosition }
        
        return sortedTeams.mapIndexed { index, team ->
            RankingResult(
                songId = team.id,
                listId = team.song.listId,
                rankingMethod = "EMRE_CORRECT",
                score = team.points,
                position = index + 1
            )
        }
    }
    
    /**
     * Turnuva durumunu kontrol et
     */
    fun checkTournamentStatus(state: EmreState): TournamentStatus {
        return when {
            state.isComplete -> TournamentStatus.COMPLETED
            state.currentRound == 1 -> TournamentStatus.NOT_STARTED
            else -> TournamentStatus.IN_PROGRESS
        }
    }
    
    enum class TournamentStatus {
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETED
    }
}