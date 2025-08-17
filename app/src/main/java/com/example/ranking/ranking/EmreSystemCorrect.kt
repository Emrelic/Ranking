package com.example.ranking.ranking

import com.example.ranking.data.Song
import com.example.ranking.data.Match
<<<<<<< HEAD

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
=======
import com.example.ranking.data.RankingResult

/**
 * GERÇEK Emre Usulü Sıralama Sistemi
 * 
 * Algoritma Kuralları:
 * 1. Başlangıç: Takımlara ID (sabit) ve sıra numarası (1-79) atanır
 * 2. İlk tur: 1-2, 3-4, 5-6... eşleştirme, tek sayıda ise son takım bye geçer
 * 3. Puanlama: Galibiyet=1, beraberlik=0.5, mağlubiyet=0, bye=1 puan
 * 4. Yeniden sıralama: Puana göre → tiebreaker → yeni sıra numaraları (1-79)
 * 5. Sonraki turlar: Sıralı eşleştirme ama daha önce oynamışlar eşleşmez
 * 6. Kontrol: Aynı puanlı eşleşme var mı? Varsa devam, yoksa bitir
 * 7. Her takım diğer takımla EN FAZLA 1 KEZ oynar
 */
object EmreSystemCorrect {
    
    data class EmreTeam(
        val song: Song,
        var points: Double = 0.0,
        var currentPosition: Int = 0,    // Mevcut sıra numarası (değişken)
        val teamId: Long,                // Sabit ID numarası
        var byePassed: Boolean = false
    ) {
        val id: Long get() = song.id
    }
    
    data class EmreState(
        val teams: List<EmreTeam>,
        val matchHistory: Set<Pair<Long, Long>> = emptySet(), // Oynanan eşleşmeler
        val currentRound: Int = 1,
        val isComplete: Boolean = false
    )
    
    data class EmrePairingResult(
        val matches: List<Match>,
        val byeTeam: EmreTeam? = null,
        val hasSamePointMatch: Boolean = false, // Aynı puanlı eşleşme var mı?
>>>>>>> bf1b6b7411b9d4284631176ddb268f736eb5d438
        val canContinue: Boolean = true
    )
    
    /**
<<<<<<< HEAD
     * Emre usulü turnuva başlatma
=======
     * Emre turnuvası başlat
>>>>>>> bf1b6b7411b9d4284631176ddb268f736eb5d438
     */
    fun initializeEmreTournament(songs: List<Song>): EmreState {
        val teams = songs.mapIndexed { index, song ->
            EmreTeam(
<<<<<<< HEAD
                song = song,
                totalPoints = 0.0,
                currentPosition = index + 1
=======
                song = song, 
                points = 0.0, 
                currentPosition = index + 1,  // Başlangıç sıra numarası
                teamId = song.id,             // Sabit ID
                byePassed = false
>>>>>>> bf1b6b7411b9d4284631176ddb268f736eb5d438
            )
        }
        
        return EmreState(
            teams = teams,
<<<<<<< HEAD
            currentRound = 1,
            matchHistory = emptySet(),
=======
            matchHistory = emptySet(),
            currentRound = 1,
>>>>>>> bf1b6b7411b9d4284631176ddb268f736eb5d438
            isComplete = false
        )
    }
    
    /**
<<<<<<< HEAD
     * Sonraki tur eşleştirmelerini oluştur
=======
     * Sonraki tur için eşleştirmeler oluştur
>>>>>>> bf1b6b7411b9d4284631176ddb268f736eb5d438
     */
    fun createNextRound(state: EmreState): EmrePairingResult {
        if (state.isComplete) {
            return EmrePairingResult(emptyList(), null, false, false)
        }
        
<<<<<<< HEAD
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
        
=======
        // Takımları sıra numaralarına göre sırala
        val sortedTeams = state.teams.sortedBy { it.currentPosition }
        
        // Bye kontrolü (tek sayıda takım varsa)
        val (teamsToMatch, byeTeam) = handleByeTeam(sortedTeams)
        
        // Eşleştirmeleri oluştur
        val (matches, hasSamePointMatch) = createPairings(teamsToMatch, state.matchHistory, state.currentRound)
        
        // Turnuva devam edebilir mi kontrol et
>>>>>>> bf1b6b7411b9d4284631176ddb268f736eb5d438
        val canContinue = hasSamePointMatch
        
        return EmrePairingResult(matches, byeTeam, hasSamePointMatch, canContinue)
    }
    
    /**
<<<<<<< HEAD
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
=======
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
     * Emre Usulü Eşleştirme: 1-2, 3-4, 5-6... ama daha önce oynamışlar eşleşmez
     */
    private fun createPairings(
        teams: List<EmreTeam>, 
        matchHistory: Set<Pair<Long, Long>>,
        currentRound: Int
    ): Pair<List<Match>, Boolean> {
        val matches = mutableListOf<Match>()
        var hasSamePointMatch = false
        val usedTeams = mutableSetOf<Long>()
        
        for (i in teams.indices) {
            val team1 = teams[i]
            
            // Bu takım zaten eşleşmişse atla
            if (team1.id in usedTeams) continue
            
            // team1 için partner ara (yukarıdan aşağı)
            var foundPartner = false
            for (j in i + 1 until teams.size) {
                val team2 = teams[j]
                
                // Bu takım zaten eşleşmişse atla
                if (team2.id in usedTeams) continue
                
                // Daha önce oynamış mı kontrol et
                val pair1 = Pair(team1.id, team2.id)
                val pair2 = Pair(team2.id, team1.id)
                
                if (pair1 !in matchHistory && pair2 !in matchHistory) {
                    // Eşleştirme oluştur
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
                    
                    // Aynı puanda mı kontrol et
                    if (team1.points == team2.points) {
                        hasSamePointMatch = true
                    }
                    
                    // Bu takımları işaretle
                    usedTeams.add(team1.id)
                    usedTeams.add(team2.id)
                    
                    foundPartner = true
                    break
                }
            }
            
            // Partner bulunamadı, bu takım bye geçer (normalde en alttaki bye geçer ama güvenlik için)
            if (!foundPartner) {
                usedTeams.add(team1.id)
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
                        // Takım 1 kazandı (+1 puan)
                        val winnerIndex = updatedTeams.indexOfFirst { it.id == match.songId1 }
                        if (winnerIndex >= 0) {
                            updatedTeams[winnerIndex] = updatedTeams[winnerIndex].copy(
                                points = updatedTeams[winnerIndex].points + 1.0
                            )
                        }
                        // Takım 2 kaybetti (+0 puan, değişiklik yok)
                    }
                    match.songId2 -> {
                        // Takım 2 kazandı (+1 puan)
                        val winnerIndex = updatedTeams.indexOfFirst { it.id == match.songId2 }
                        if (winnerIndex >= 0) {
                            updatedTeams[winnerIndex] = updatedTeams[winnerIndex].copy(
                                points = updatedTeams[winnerIndex].points + 1.0
                            )
                        }
                        // Takım 1 kaybetti (+0 puan, değişiklik yok)
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
        
        // Emre usulü sıralamayı yenile
        val reorderedTeams = reorderTeamsEmreStyle(updatedTeams, newMatchHistory)
        
        return EmreState(
            teams = reorderedTeams,
            matchHistory = newMatchHistory,
            currentRound = state.currentRound + 1,
>>>>>>> bf1b6b7411b9d4284631176ddb268f736eb5d438
            isComplete = false
        )
    }
    
    /**
<<<<<<< HEAD
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
=======
     * Takımları Emre usulü kurallarına göre yeniden sırala
     * 
     * Sıralama Kuralları:
     * 1. Puana göre yüksekten alçağa
     * 2. Eşit puanlı takımlar için tiebreaker:
     *    - Aralarında maç varsa, kazanan üstte
     *    - Maç yoksa, önceki sıralamada yukarıdaki üstte
     * 3. Yeni sıra numaraları atanır (1-79)
     */
    private fun reorderTeamsEmreStyle(teams: List<EmreTeam>, matchHistory: Set<Pair<Long, Long>>): List<EmreTeam> {
        val sortedTeams = teams.sortedWith(compareBy<EmreTeam> { -it.points } // Yüksek puan önce
            .thenBy { it.currentPosition } // Eşit puanlılar için önceki sıralama
        )
        
        // Yeni sıra numaralarını ata
        return sortedTeams.mapIndexed { index, team ->
            team.copy(currentPosition = index + 1)
        }
    }
    
    /**
     * Final sonuçlarını hesapla
     */
    fun calculateFinalResults(state: EmreState): List<RankingResult> {
        val sortedTeams = state.teams.sortedBy { it.currentPosition }
        
        return sortedTeams.mapIndexed { index, team ->
            RankingResult(
                songId = team.id,
                listId = team.song.listId,
                rankingMethod = "EMRE_CORRECT",
                score = team.points,
>>>>>>> bf1b6b7411b9d4284631176ddb268f736eb5d438
                position = index + 1
            )
        }
    }
    
    /**
<<<<<<< HEAD
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
=======
     * Turnuva durumunu kontrol et
     */
    fun checkTournamentStatus(state: EmreState): TournamentStatus {
        return when {
            state.isComplete -> TournamentStatus.COMPLETED
            state.currentRound == 1 -> TournamentStatus.NOT_STARTED
>>>>>>> bf1b6b7411b9d4284631176ddb268f736eb5d438
            else -> TournamentStatus.IN_PROGRESS
        }
    }
    
    enum class TournamentStatus {
        NOT_STARTED,
<<<<<<< HEAD
        IN_PROGRESS, 
=======
        IN_PROGRESS,
>>>>>>> bf1b6b7411b9d4284631176ddb268f736eb5d438
        COMPLETED
    }
}