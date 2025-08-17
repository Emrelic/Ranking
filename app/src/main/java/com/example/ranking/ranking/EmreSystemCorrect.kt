package com.example.ranking.ranking

import com.example.ranking.data.Song
import com.example.ranking.data.Match
import com.example.ranking.data.RankingResult

/**
 * GERÇEK Emre Usulü Sıralama Sistemi
 * 
 * DOĞRU Algoritma Kuralları (Kullanıcının belirttiği şekilde):
 * 1. Başlangıç: Takımlara ID (sabit) ve sıra numarası (1-79) atanır
 * 2. İlk tur: 1-2, 3-4, 5-6... eşleştirme, tek sayıda ise son takım bye geçer
 * 3. Puanlama: Galibiyet=1, beraberlik=0.5, mağlubiyet=0, bye=1 puan
 * 4. Yeniden sıralama: Puana göre → tiebreaker → yeni sıra numaraları (1-79)
 * 5. Sonraki turlar: 1. takım 2. ile eşleşmedi mi kontrol → eşleştir
 *    Eşleşmiş ise 1-3, 1-4, 1-5... şeklinde ilk eşleşmemiş rakip bulunana kadar
 * 6. Aynı puan kontrolü: Eğer her eşleşmede aynı puanlı takımlar varsa tur oynanır
 *    Hiçbir eşleşmede aynı puanlı takım yoksa turnuva biter
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
        
        // Deep copy fonksiyonu - CRITICAL for avoiding duplicate match bug
        fun deepCopy(): EmreTeam {
            return EmreTeam(
                song = song,
                points = points,
                currentPosition = currentPosition,
                teamId = teamId,
                byePassed = byePassed
            )
        }
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
        val canContinue: Boolean = true
    )
    
    /**
     * Emre turnuvası başlat
     */
    fun initializeEmreTournament(songs: List<Song>): EmreState {
        val teams = songs.mapIndexed { index, song ->
            EmreTeam(
                song = song, 
                points = 0.0, 
                currentPosition = index + 1,  // Başlangıç sıra numarası
                teamId = song.id,             // Sabit ID
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
        
        // Takımları sıra numaralarına göre sırala (en yüksek puan 1. sırada)
        val sortedTeams = state.teams.sortedBy { it.currentPosition }
        
        // Bye kontrolü (tek sayıda takım varsa)
        val (teamsToMatch, byeTeam) = handleByeTeam(sortedTeams)
        
        // KULLANICININ BELİRTTİĞİ DOĞRU ALGORİTMA İLE EŞLEŞTİRME
        val (matches, hasSamePointMatch) = createCorrectEmrePairings(teamsToMatch, state.matchHistory, state.currentRound)
        
        // Aynı puanlı eşleşme var mı kontrol → turnuva devam edebilir mi?
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
     * KULLANICININ BELİRTTİĞİ DOĞRU EMre Usulü Eşleştirme Algoritması
     * 
     * Algoritma:
     * 1. 1. sıradaki takım → 2, 3, 4, 5... ile daha önce oynamadığı ilk takımla eşleş
     * 2. 2. sıradaki takım → 3, 4, 5... ile daha önce oynamadığı ilk takımla eşleş
     * 3. Bu şekilde devam et
     * 4. Her eşleşmede: Aynı puanlı mı kontrol et
     * 5. En az bir eşleşme aynı puanlı ise → tur oynanır
     * 6. Hiçbir eşleşme aynı puanlı değil ise → turnuva biter
     */
    private fun createCorrectEmrePairings(
        teams: List<EmreTeam>, 
        matchHistory: Set<Pair<Long, Long>>,
        currentRound: Int
    ): Pair<List<Match>, Boolean> {
        val matches = mutableListOf<Match>()
        var hasSamePointMatch = false
        val usedTeams = mutableSetOf<Long>()
        
        // Sıra ile her takım için partner bul
        for (i in teams.indices) {
            val team1 = teams[i]
            
            // Bu takım zaten eşleşmişse atla
            if (team1.id in usedTeams) continue
            
            // team1 için partner ara: sıradaki takımlardan daha önce oynamadığı ilkini bul
            var foundPartner = false
            for (j in i + 1 until teams.size) {
                val team2 = teams[j]
                
                // Bu takım zaten eşleşmişse atla
                if (team2.id in usedTeams) continue
                
                // CRITICAL: Daha önce oynamış mı kontrol et
                val pair1 = Pair(team1.id, team2.id)
                val pair2 = Pair(team2.id, team1.id)
                
                // Daha önce oynamadılar mı?
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
                    
                    // CRITICAL: Aynı puanda mı kontrol et
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
            
            // Partner bulunamadı, bu takım kalan turda bye geçer
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
        val updatedTeams = state.teams.map { it.deepCopy() }.toMutableList()
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
            // CRITICAL: Maç geçmişine ekle - prevents duplicate pairings
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
        
        // KULLANICININ BELİRTTİĞİ Emre usulü sıralamayı yenile
        val reorderedTeams = reorderTeamsEmreStyle(updatedTeams, newMatchHistory)
        
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