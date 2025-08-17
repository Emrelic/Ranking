package com.example.ranking.ranking

import com.example.ranking.data.Song
import com.example.ranking.data.Match
import com.example.ranking.data.RankingResult

/**
 * Emre Usulü Sıralama Sistemi - Gelişmiş Swiss Style Turnuva
 * 
 * Bu sistem aşağıdaki kurallara göre çalışır:
 * 1. Her tur ekipler puan sıralamasına göre eşleşir (1-2, 3-4, 5-6...)
 * 2. Aynı puanlı ekipler önce birbiriyle eşleşir
 * 3. Daha önce eşleşmiş ekipler tekrar eşleşmez
 * 4. Tek sayıda ekip varsa, en az puanlı bye geçer (+1 puan)
 * 5. Aynı puanlı tüm ekipler birbiriyle oynadığında turnuva biter
 */
object EmreSystem {
    
    data class EmreTeam(
        val song: Song,
        var points: Double = 0.0,
        var originalOrder: Int,
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
    
    data class PairingResult(
        val matches: List<Match>,
        val byeTeam: EmreTeam? = null,
        val canContinue: Boolean = true
    )
    
    /**
     * Yeni Emre turnuvası başlat
     */
    fun initializeEmreTournament(songs: List<Song>): EmreState {
        val teams = songs.mapIndexed { index, song ->
            EmreTeam(song, 0.0, index, false)
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
    fun createNextRound(state: EmreState): PairingResult {
        if (state.isComplete) {
            return PairingResult(emptyList(), null, false)
        }
        
        // Takımları puana göre sırala (puan aynıysa orijinal sıraya göre)
        val sortedTeams = state.teams.sortedWith(compareByDescending<EmreTeam> { it.points }
            .thenBy { it.originalOrder })
        
        // Bye kontrolü
        val (teamsToMatch, byeTeam) = handleByeTeam(sortedTeams)
        
        // Eşleştirmeleri oluştur
        val matches = when (state.currentRound) {
            1 -> {
                // İlk tur: Sıralı eşleştirme (1-2, 3-4, 5-6...)
                createFirstRoundPairings(teamsToMatch, state.currentRound)
            }
            2 -> {
                // İkinci tur: Özel eşleştirme (kazananlar arası, kaybedenler arası)
                createSecondRoundPairings(teamsToMatch, state.matchHistory, state.currentRound)
            }
            else -> {
                // Sonraki turlar: Normal puan bazlı eşleştirme
                createPairings(teamsToMatch, state.matchHistory, state.currentRound)
            }
        }
        
        // Turnuva bitip bitmediğini kontrol et
        val canContinue = checkIfCanContinue(teamsToMatch, state.matchHistory)
        
        return PairingResult(matches, byeTeam, canContinue)
    }
    
    /**
     * İlk tur eşleştirmeleri (sıralı: 1-2, 3-4, 5-6...)
     */
    private fun createFirstRoundPairings(teams: List<EmreTeam>, currentRound: Int): List<Match> {
        val matches = mutableListOf<Match>()
        
        // Sıralı eşleştirme: 1-2, 3-4, 5-6...
        for (i in 0 until teams.size - 1 step 2) {
            matches.add(
                Match(
                    listId = teams[i].song.listId,
                    rankingMethod = "EMRE_NEW",
                    songId1 = teams[i].id,
                    songId2 = teams[i + 1].id,
                    winnerId = null,
                    round = currentRound
                )
            )
        }
        
        return matches
    }
    
    /**
     * İkinci tur özel eşleştirmeleri (çapraz eşleştirme)
     * Emre usulünde ikinci turda farklı eşleştirme yapılmalı
     */
    private fun createSecondRoundPairings(teams: List<EmreTeam>, matchHistory: Set<Pair<Long, Long>>, currentRound: Int): List<Match> {
        val matches = mutableListOf<Match>()
        
        // Puana göre grupla
        val teamsByPoints = teams.groupBy { it.points }.toSortedMap(compareByDescending { it })
        
        // Eğer iki farklı puan grubu varsa (kazananlar vs kaybedenler)
        if (teamsByPoints.size == 2) {
            val winners = teamsByPoints.values.first() // Yüksek puanlılar
            val losers = teamsByPoints.values.last()   // Düşük puanlılar
            
            // Çapraz eşleştirme yap: 1. kazanan vs 2. kazanan, 1. kaybeden vs 2. kaybeden
            // Ama daha önce eşleşmemiş olanları seç
            
            if (winners.size >= 2) {
                // Kazananlar arası eşleştirme - farklı kombinasyon kullan
                val winnerPairs = createAlternatePairing(winners, matchHistory, currentRound)
                matches.addAll(winnerPairs)
            }
            
            if (losers.size >= 2) {
                // Kaybedenler arası eşleştirme - farklı kombinasyon kullan
                val loserPairs = createAlternatePairing(losers, matchHistory, currentRound)
                matches.addAll(loserPairs)
            }
            
            // Eğer tek takım kaldıysa cross-group eşleştirme yap
            val remainingWinners = winners.size % 2
            val remainingLosers = losers.size % 2
            
            if (remainingWinners > 0 && remainingLosers > 0) {
                val lastWinner = winners.last()
                val lastLoser = losers.last()
                
                // Bu ikisi daha önce eşleşmemiş mi kontrol et
                val pair1 = Pair(lastWinner.id, lastLoser.id)
                val pair2 = Pair(lastLoser.id, lastWinner.id)
                
                if (pair1 !in matchHistory && pair2 !in matchHistory) {
                    matches.add(
                        Match(
                            listId = lastWinner.song.listId,
                            rankingMethod = "EMRE_NEW",
                            songId1 = lastWinner.id,
                            songId2 = lastLoser.id,
                            winnerId = null,
                            round = currentRound
                        )
                    )
                }
            }
        } else {
            // Hepsi aynı puanda ise normal algoritma kullan
            return createPairings(teams, matchHistory, currentRound)
        }
        
        return matches
    }
    
    /**
     * Alternatif eşleştirme oluştur (çapraz kombinasyon)
     */
    private fun createAlternatePairing(teams: List<EmreTeam>, matchHistory: Set<Pair<Long, Long>>, currentRound: Int): List<Match> {
        val matches = mutableListOf<Match>()
        val availableTeams = teams.toMutableList()
        
        // Çapraz eşleştirme: 1-3, 2-4 yerine 1-2, 3-4 yapmıştık, şimdi farklı yap
        while (availableTeams.size >= 2) {
            val team1 = availableTeams[0]
            var team2: EmreTeam? = null
            
            // En uygun rakibi bul (daha önce eşleşmemiş)
            for (i in 1 until availableTeams.size) {
                val candidate = availableTeams[i]
                val pair1 = Pair(team1.id, candidate.id)
                val pair2 = Pair(candidate.id, team1.id)
                
                if (pair1 !in matchHistory && pair2 !in matchHistory) {
                    team2 = candidate
                    break
                }
            }
            
            if (team2 != null) {
                matches.add(
                    Match(
                        listId = team1.song.listId,
                        rankingMethod = "EMRE_NEW",
                        songId1 = team1.id,
                        songId2 = team2.id,
                        winnerId = null,
                        round = currentRound
                    )
                )
                
                availableTeams.remove(team1)
                availableTeams.remove(team2)
            } else {
                // Eşleşecek kimse yok
                break
            }
        }
        
        return matches
    }
    
    /**
     * Bye geçecek takımı belirle (tek sayıda takım varsa)
     */
    private fun handleByeTeam(sortedTeams: List<EmreTeam>): Pair<List<EmreTeam>, EmreTeam?> {
        if (sortedTeams.size % 2 == 0) {
            return Pair(sortedTeams, null)
        }
        
        // En az puanlı takım bye geçer
        val byeTeam = sortedTeams.last()
        val remainingTeams = sortedTeams.dropLast(1)
        
        return Pair(remainingTeams, byeTeam)
    }
    
    /**
     * Eşleştirmeleri oluştur
     */
    private fun createPairings(
        teams: List<EmreTeam>, 
        matchHistory: Set<Pair<Long, Long>>,
        currentRound: Int
    ): List<Match> {
        val matches = mutableListOf<Match>()
        val availableTeams = teams.toMutableList()
        
        // Puana göre grupla
        val teamsByPoints = teams.groupBy { it.points }.toSortedMap(compareByDescending { it })
        
        // Her puan grubu için eşleştirme yap
        for ((points, pointGroup) in teamsByPoints) {
            val groupTeams = pointGroup.filter { it in availableTeams }.toMutableList()
            
            // Bu gruptaki takımları eşleştir
            while (groupTeams.size >= 2) {
                val team1 = groupTeams[0]
                var team2: EmreTeam? = null
                
                // team1 için uygun rakip bul (daha önce eşleşmemiş)
                for (i in 1 until groupTeams.size) {
                    val candidate = groupTeams[i]
                    val pair1 = Pair(team1.id, candidate.id)
                    val pair2 = Pair(candidate.id, team1.id)
                    
                    if (pair1 !in matchHistory && pair2 !in matchHistory) {
                        team2 = candidate
                        break
                    }
                }
                
                if (team2 != null) {
                    // Eşleştirmeyi oluştur
                    matches.add(
                        Match(
                            listId = team1.song.listId,
                            rankingMethod = "EMRE_NEW",
                            songId1 = team1.id,
                            songId2 = team2.id,
                            winnerId = null,
                            round = currentRound
                        )
                    )
                    
                    groupTeams.remove(team1)
                    groupTeams.remove(team2)
                    availableTeams.remove(team1)
                    availableTeams.remove(team2)
                } else {
                    // Bu grupta eşleşecek kimse yok, sonraki puan grubuna aktar
                    break
                }
            }
        }
        
        // Kalan takımları farklı puan gruplarıyla eşleştir
        while (availableTeams.size >= 2) {
            val team1 = availableTeams[0]
            var team2: EmreTeam? = null
            
            // team1 için uygun rakip bul
            for (i in 1 until availableTeams.size) {
                val candidate = availableTeams[i]
                val pair1 = Pair(team1.id, candidate.id)
                val pair2 = Pair(candidate.id, team1.id)
                
                if (pair1 !in matchHistory && pair2 !in matchHistory) {
                    team2 = candidate
                    break
                }
            }
            
            if (team2 != null) {
                matches.add(
                    Match(
                        listId = team1.song.listId,
                        rankingMethod = "EMRE_NEW",
                        songId1 = team1.id,
                        songId2 = team2.id,
                        winnerId = null,
                        round = currentRound
                    )
                )
                
                availableTeams.remove(team1)
                availableTeams.remove(team2)
            } else {
                // Hiç eşleşecek takım kalmadı
                break
            }
        }
        return matches
    }
    
    /**
     * Turnuvanın devam edip edemeyeceğini kontrol et
     */
    private fun checkIfCanContinue(teams: List<EmreTeam>, matchHistory: Set<Pair<Long, Long>>): Boolean {
        // Aynı puanlı grupları kontrol et
        val teamsByPoints = teams.groupBy { it.points }
        
        for ((points, pointGroup) in teamsByPoints) {
            if (pointGroup.size < 2) continue
            
            // Bu puan grubundaki takımlar arasında eşleşilmemiş çift var mı?
            for (i in pointGroup.indices) {
                for (j in i + 1 until pointGroup.size) {
                    val team1 = pointGroup[i]
                    val team2 = pointGroup[j]
                    val pair1 = Pair(team1.id, team2.id)
                    val pair2 = Pair(team2.id, team1.id)
                    
                    if (pair1 !in matchHistory && pair2 !in matchHistory) {
                        return true // En az bir eşleşme daha var
                    }
                }
            }
        }
        
        return false // Hiç eşleşecek takım kalmadı
    }
    
    /**
     * Tur sonuçlarını işle ve durumu güncelle
     */
    fun processRoundResults(state: EmreState, completedMatches: List<Match>, byeTeam: EmreTeam? = null): EmreState {
        val updatedTeams = state.teams.map { team ->
            team.copy()
        }.toMutableList()
        
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
        
        // Maç sonuçlarını işle - Tamamlanmış ve tamamlanmamış maçların hepsini geçmişe ekle
        completedMatches.forEach { match ->
            // Maç geçmişine ekle (tamamlanıp tamamlanmadığına bakma)
            newMatchHistory.add(Pair(match.songId1, match.songId2))
            newMatchHistory.add(Pair(match.songId2, match.songId1))
            
            // Sadece tamamlanmış maçları puanla
            if (match.isCompleted) {
                // Puanları güncelle
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
        
        // Turnuva bitip bitmediğini kontrol et
        val isComplete = !checkIfCanContinue(updatedTeams, newMatchHistory)
        
        return EmreState(
            teams = updatedTeams,
            matchHistory = newMatchHistory,
            currentRound = state.currentRound + 1,
            isComplete = isComplete
        )
    }
    
    /**
     * Final sonuçlarını hesapla
     */
    fun calculateFinalResults(state: EmreState): List<RankingResult> {
        // Takımları puana göre sırala (puan aynıysa orijinal sıraya göre)
        val sortedTeams = state.teams.sortedWith(
            compareByDescending<EmreTeam> { it.points }
                .thenBy { it.originalOrder }
        )
        
        return sortedTeams.mapIndexed { index, team ->
            RankingResult(
                songId = team.id,
                listId = team.song.listId,
                rankingMethod = "EMRE_NEW",
                score = team.points,
                position = index + 1
            )
        }
    }
    
    /**
     * Mevcut durumu kontrol et
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