package com.example.ranking.ranking

import com.example.ranking.data.Song
import com.example.ranking.data.Match

object EmreSystemCorrect {
    
    // Emre takım bilgileri
    data class EmreTeam(
        val song: Song,
        var totalPoints: Double = 0.0,
        var currentPosition: Int = 0,
        val matchHistory: MutableSet<Long> = mutableSetOf()
    ) {
        // Deep copy fonksiyonu
        fun deepCopy(): EmreTeam {
            return EmreTeam(
                song = song,
                totalPoints = totalPoints,
                currentPosition = currentPosition,
                matchHistory = matchHistory.toMutableSet() // Yeni Set oluştur
            )
        }
    }
    
    // Emre turnuva durumu
    data class EmreState(
        val teams: List<EmreTeam>,
        val currentRound: Int = 1,
        val matchHistory: Set<Pair<Long, Long>> = emptySet(),
        val isComplete: Boolean = false
    )
    
    // Emre eşleştirme sonucu
    data class EmrePairingResult(
        val matches: List<Match>,
        val byeTeam: EmreTeam? = null,
        val hasSamePointMatch: Boolean = false,
        val canContinue: Boolean = true
    )
    
    /**
     * Emre usulü turnuva başlatma
     */
    fun initializeEmreTournament(songs: List<Song>): EmreState {
        val teams = songs.mapIndexed { index, song ->
            EmreTeam(
                song = song,
                totalPoints = 0.0,
                currentPosition = index + 1
            )
        }
        
        return EmreState(
            teams = teams,
            currentRound = 1,
            matchHistory = emptySet(),
            isComplete = false
        )
    }
    
    /**
     * Sonraki tur eşleştirmelerini oluştur
     */
    fun createNextRound(state: EmreState): EmrePairingResult {
        if (state.isComplete) {
            return EmrePairingResult(emptyList(), null, false, false)
        }
        
        // Takımları puana göre sırala (eşit puanlılar için tiebreaker)
        val sortedTeams = sortTeamsByPoints(state.teams, state.matchHistory)
        
        // Eşleştirme yap
        val matches = mutableListOf<Match>()
        val pairedTeams = mutableSetOf<Long>()
        var byeTeam: EmreTeam? = null
        var hasSamePointMatch = false
        
        // Tek sayıda takım varsa, en alttaki bye geçer
        if (sortedTeams.size % 2 == 1) {
            byeTeam = sortedTeams.last()
            pairedTeams.add(byeTeam.song.id)
        }
        
        // Eşleştirme: 1-2, 3-4, 5-6...
        var i = 0
        while (i < sortedTeams.size - 1) {
            if (sortedTeams[i].song.id in pairedTeams || sortedTeams[i + 1].song.id in pairedTeams) {
                i++
                continue
            }
            
            val team1 = sortedTeams[i]
            val team2 = sortedTeams[i + 1]
            
            // Aynı puanlı mı kontrol et
            if (team1.totalPoints == team2.totalPoints) {
                hasSamePointMatch = true
            }
            
            // Daha önce eşleşmiş mi kontrol et
            val pair1 = Pair(team1.song.id, team2.song.id)
            val pair2 = Pair(team2.song.id, team1.song.id)
            
            if (pair1 in state.matchHistory || pair2 in state.matchHistory) {
                // Daha önce eşleşmişler, alternatif bul
                val alternative = findAlternativeOpponent(team1, sortedTeams, pairedTeams, state.matchHistory, i)
                if (alternative != null) {
                    // Alternatif rakip bulundu
                    matches.add(createMatch(team1, alternative, state.currentRound))
                    pairedTeams.add(team1.song.id)
                    pairedTeams.add(alternative.song.id)
                    
                    if (team1.totalPoints == alternative.totalPoints) {
                        hasSamePointMatch = true
                    }
                } else {
                    // Alternatif bulunamadı, mecburen tekrar eşleştir
                    matches.add(createMatch(team1, team2, state.currentRound))
                    pairedTeams.add(team1.song.id)
                    pairedTeams.add(team2.song.id)
                }
            } else {
                // İlk kez eşleşiyorlar
                matches.add(createMatch(team1, team2, state.currentRound))
                pairedTeams.add(team1.song.id)
                pairedTeams.add(team2.song.id)
            }
            
            i += 2
        }
        
        val canContinue = hasSamePointMatch
        
        return EmrePairingResult(matches, byeTeam, hasSamePointMatch, canContinue)
    }
    
    /**
     * Tur sonuçlarını işle ve yeni state oluştur
     */
    fun processRoundResults(state: EmreState, matches: List<Match>, byeTeam: EmreTeam?): EmreState {
        val updatedTeams = state.teams.map { it.deepCopy() }.toMutableList()
        val newMatchHistory = state.matchHistory.toMutableSet()
        
        // Maç sonuçlarını işle
        matches.filter { it.isCompleted }.forEach { match ->
            val team1 = updatedTeams.find { it.song.id == match.songId1 }
            val team2 = updatedTeams.find { it.song.id == match.songId2 }
            
            if (team1 != null && team2 != null) {
                // Puanları güncelle
                when (match.winnerId) {
                    match.songId1 -> {
                        team1.totalPoints += 1.0
                        team2.totalPoints += 0.0
                    }
                    match.songId2 -> {
                        team1.totalPoints += 0.0
                        team2.totalPoints += 1.0
                    }
                    null -> {
                        // Beraberlik
                        team1.totalPoints += 0.5
                        team2.totalPoints += 0.5
                    }
                }
                
                // Maç geçmişini güncelle
                newMatchHistory.add(Pair(match.songId1, match.songId2))
                newMatchHistory.add(Pair(match.songId2, match.songId1))
                team1.matchHistory.add(match.songId2)
                team2.matchHistory.add(match.songId1)
            }
        }
        
        // Bye geçen takıma puan ver
        byeTeam?.let { bye ->
            val byeTeamInList = updatedTeams.find { it.song.id == bye.song.id }
            byeTeamInList?.totalPoints = byeTeamInList?.totalPoints?.plus(1.0) ?: 1.0
        }
        
        // Yeni sıralamayı hesapla
        val newSortedTeams = sortTeamsByPoints(updatedTeams, newMatchHistory)
        newSortedTeams.forEachIndexed { index, team ->
            team.currentPosition = index + 1
        }
        
        return EmreState(
            teams = newSortedTeams,
            currentRound = state.currentRound + 1,
            matchHistory = newMatchHistory,
            isComplete = false
        )
    }
    
    /**
     * Final sonuçları hesapla
     */
    fun calculateFinalResults(state: EmreState): List<com.example.ranking.data.RankingResult> {
        val sortedTeams = sortTeamsByPoints(state.teams, state.matchHistory)
        
        return sortedTeams.mapIndexed { index, team ->
            com.example.ranking.data.RankingResult(
                songId = team.song.id,
                listId = team.song.listId,
                rankingMethod = "EMRE",
                score = team.totalPoints,
                position = index + 1
            )
        }
    }
    
    /**
     * Takımları puana göre sırala (tiebreaker ile)
     */
     private fun sortTeamsByPoints(teams: List<EmreTeam>, matchHistory: Set<Pair<Long, Long>>): List<EmreTeam> {
        return teams.sortedWith(compareByDescending<EmreTeam> { it.totalPoints }
            .thenBy { it.currentPosition }) // Basit tiebreaker: önceki pozisyon
    }
    
    /**
     * Alternatif rakip bul
     */
    private fun findAlternativeOpponent(
        team: EmreTeam,
        sortedTeams: List<EmreTeam>,
        pairedTeams: Set<Long>,
        matchHistory: Set<Pair<Long, Long>>,
        currentIndex: Int
    ): EmreTeam? {
        // Aynı puan grubunda alternatif ara
        for (i in currentIndex + 2 until sortedTeams.size) {
            val candidate = sortedTeams[i]
            if (candidate.song.id !in pairedTeams && 
                candidate.totalPoints == team.totalPoints &&
                Pair(team.song.id, candidate.song.id) !in matchHistory &&
                Pair(candidate.song.id, team.song.id) !in matchHistory) {
                return candidate
            }
        }
        
        // Bulunamadıysa null döner
        return null
    }
    
    /**
     * Maç oluştur
     */
    private fun createMatch(team1: EmreTeam, team2: EmreTeam, round: Int): Match {
        return Match(
            listId = team1.song.listId,
            rankingMethod = "EMRE",
            songId1 = team1.song.id,
            songId2 = team2.song.id,
            winnerId = null,
            round = round
        )
    }
    
    /**
     * Turnuva durumunu kontrol et
     */
    fun getTournamentStatus(state: EmreState): TournamentStatus {
        return when {
            state.isComplete -> TournamentStatus.COMPLETED
            state.teams.isEmpty() -> TournamentStatus.NOT_STARTED
            state.currentRound == 1 -> TournamentStatus.IN_PROGRESS
            else -> TournamentStatus.IN_PROGRESS
        }
    }
    
    enum class TournamentStatus {
        NOT_STARTED,
        IN_PROGRESS, 
        COMPLETED
    }
}