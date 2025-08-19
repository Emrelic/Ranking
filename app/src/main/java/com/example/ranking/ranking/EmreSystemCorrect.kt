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
        val canContinue: Boolean = true,
        val candidateMatches: List<CandidateMatch> = emptyList() // Aday eşleşmeler
    )
    
    // Aday eşleşme sistemi için yeni data class
    data class CandidateMatch(
        val team1: EmreTeam,
        val team2: EmreTeam,
        val isAsymmetricPoints: Boolean // Farklı puanlı mı?
    )
    
    // İki kademeli kontrol durumları
    enum class PairingPhase {
        CANDIDATE_CREATION,    // Aday eşleştirme oluşturma
        CONFIRMATION_PENDING,  // Onay bekliyor
        CONFIRMED,            // Onaylandı
        TOURNAMENT_FINISHED   // Turnuva bitti (asimetrik puan yok)
    }
    
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
     * İKİ KADEMELİ KONTROLLU SONRAKI TUR EŞLEŞTİRMELERİ
     * 
     * KULLANICININ TARİF ETTİĞİ SİSTEM:
     * 1. Onay sistemi: "X. Tur eşleştirmeleri yapılacaktır" → kullanıcı onayı
     * 2. Aday eşleştirmeler oluştur → asimetrik puan kontrolü
     * 3. Eğer asimetrik puan yoksa → turnuva biter
     * 4. Eğer asimetrik puan varsa → onay sonrası kesin eşleştirmeler
     */
    fun createNextRoundWithConfirmation(state: EmreState): EmrePairingResult {
        if (state.isComplete) {
            return EmrePairingResult(
                matches = emptyList(), 
                byeTeam = null, 
                hasSamePointMatch = false, 
                canContinue = false,
                candidateMatches = emptyList()
            )
        }
        
        // Takımları sıra numaralarına göre sırala (en yüksek puan 1. sırada)
        val sortedTeams = state.teams.sortedBy { it.currentPosition }
        
        // Bye kontrolü (tek sayıda takım varsa)
        val (teamsToMatch, byeTeam) = handleByeTeam(sortedTeams)
        
        // YENİ İKİ KADEMELİ ALGORİTMA İLE EŞLEŞTİRME
        val result = createAdvancedSwissPairings(teamsToMatch, state.matchHistory, state.currentRound)
        
        // Bye ekibini result'a ekle
        return result.copy(byeTeam = byeTeam)
    }
    
    /**
     * Geriye uyumluluk için eski fonksiyon - deprecated
     */
    @Deprecated("Use createNextRoundWithConfirmation instead")
    fun createNextRound(state: EmreState): EmrePairingResult {
        return createNextRoundWithConfirmation(state)
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
     * KULLANICININ TARİF ETTİĞİ DOĞRU ALGORİTMA - TEK TEK TAKIM BAZINDA
     * 
     * 1. En üst takım → eşleştirme arayan statüsü
     * 2. Kendinden sonraki ilk uygun takımla → aday listeye ekle  
     * 3. Henüz aday listede olmayan en üst takım → yeni arama döngüsü
     * 4. Eğer sonraki hiçbiriyle eşleşemiyorsa → geriye dön (94,93,92...)
     * 5. İlk uygun bulunca → önceki eşleşmesini boz
     * 6. Bozulan takım yeniden arama döngüsüne gir
     * 7. Tüm aday eşleşmeler hazır → aynı puanlı kontrol
     * 8. En az bir aynı puanlı varsa → tur onaylanır
     * 9. Hiçbir aynı puanlı yoksa → tur iptal, şampiyona biter
     */
    private fun createAdvancedSwissPairings(
        teams: List<EmreTeam>, 
        matchHistory: Set<Pair<Long, Long>>,
        currentRound: Int
    ): EmrePairingResult {
        
        val candidateMatches = mutableListOf<CandidateMatch>()
        val usedTeams = mutableSetOf<Long>()
        var byeTeam: EmreTeam? = null
        
        // SIRA SIRA EŞLEŞTİRME ALGORİTMASI
        var searchIndex = 0
        while (searchIndex < teams.size) {
            // Henüz aday listede olmayan en üst takımı bul
            val searchingTeam = teams.find { it.currentPosition == searchIndex + 1 && it.id !in usedTeams }
            if (searchingTeam == null) {
                searchIndex++
                continue
            }
            
            // Bu takım için eşleştirme ara
            val partnerResult = findPartnerSequentially(
                searchingTeam = searchingTeam,
                teams = teams,
                usedTeams = usedTeams,
                matchHistory = matchHistory,
                candidateMatches = candidateMatches
            )
            
            when (partnerResult) {
                is SequentialPartnerResult.Found -> {
                    // Partner bulundu → aday listesine ekle
                    candidateMatches.add(
                        CandidateMatch(
                            team1 = searchingTeam,
                            team2 = partnerResult.partner,
                            isAsymmetricPoints = searchingTeam.points != partnerResult.partner.points
                        )
                    )
                    usedTeams.add(searchingTeam.id)
                    usedTeams.add(partnerResult.partner.id)
                }
                
                is SequentialPartnerResult.NeedsBacktrack -> {
                    // Geri dönüş gerekiyor → eşleşme boz ve yeniden başla
                    breakExistingMatch(
                        targetTeam = partnerResult.targetTeam,
                        searchingTeam = searchingTeam,
                        candidateMatches = candidateMatches,
                        usedTeams = usedTeams
                    )
                    // Bozulan takım yeniden arama döngüsüne girecek
                    searchIndex = 0 // Baştan başla
                    continue
                }
                
                is SequentialPartnerResult.Bye -> {
                    // Bye geçer (tek sayıda liste durumunda)
                    byeTeam = searchingTeam
                    usedTeams.add(searchingTeam.id)
                }
            }
            
            searchIndex++
        }
        
        // AYNI PUANLI KONTROL VE TUR ONAY SİSTEMİ
        return checkAndApproveRound(candidateMatches, byeTeam, currentRound)
    }
    
    /**
     * SIRA SIRA EŞLEŞTİRME - Tek takım için partner bul
     * 
     * ⚠️ KRİTİK: ADAY LİSTEDE DUPLICATE KONTROL EKLENDI
     */
    private fun findPartnerSequentially(
        searchingTeam: EmreTeam,
        teams: List<EmreTeam>, 
        usedTeams: Set<Long>,
        matchHistory: Set<Pair<Long, Long>>,
        candidateMatches: List<CandidateMatch>
    ): SequentialPartnerResult {
        
        // ÖNCE SONRAKI EKİPLERE BAK (kendisinden sonraki sıradakiler)
        for (i in searchingTeam.currentPosition until teams.size) {
            val potentialPartner = teams.find { it.currentPosition == i + 1 }
            if (potentialPartner == null || potentialPartner.id in usedTeams) continue
            
            // ⚠️ KRİTİK KONTROLLER:
            // 1. Daha önce oynamışlar mı kontrol et (match history)
            if (hasTeamsPlayedBefore(searchingTeam.id, potentialPartner.id, matchHistory)) {
                continue // Bu takımla daha önce oynamış, sonrakini dene
            }
            
            // 2. Aday listede zaten bu ikili var mı kontrol et
            if (candidateMatches.any { 
                (it.team1.id == searchingTeam.id && it.team2.id == potentialPartner.id) ||
                (it.team1.id == potentialPartner.id && it.team2.id == searchingTeam.id)
            }) {
                continue // Aday listede zaten var, sonrakini dene
            }
            
            // Her iki kontrol de geçti → partner bulundu
            return SequentialPartnerResult.Found(potentialPartner)
        }
        
        // SONRAKI EKİPLERDE BULUNAMADI → GERİYE DÖN (94,93,92,91...)
        for (i in searchingTeam.currentPosition - 2 downTo 0) {
            val potentialPartner = teams.find { it.currentPosition == i + 1 }
            if (potentialPartner == null || potentialPartner.id in usedTeams) continue
            
            // ⚠️ KRİTİK KONTROLLER:
            // 1. Daha önce oynamışlar mı kontrol et
            if (hasTeamsPlayedBefore(searchingTeam.id, potentialPartner.id, matchHistory)) {
                continue // Bu takımla daha önce oynamış, sonrakini dene
            }
            
            // 2. Aday listede zaten bu ikili var mı kontrol et  
            if (candidateMatches.any { 
                (it.team1.id == searchingTeam.id && it.team2.id == potentialPartner.id) ||
                (it.team1.id == potentialPartner.id && it.team2.id == searchingTeam.id)
            }) {
                continue // Aday listede zaten var, sonrakini dene
            }
            
            // Bu takım önceki bir eşleşmede kullanılıyor → backtrack gerekir
            return SequentialPartnerResult.NeedsBacktrack(potentialPartner)
        }
        
        // HIÇBIR YERDE PARTNER BULUNAMADI → BYE GEÇ
        return SequentialPartnerResult.Bye
    }
    
    /**
     * Sıralı partner arama sonucu
     */
    sealed class SequentialPartnerResult {
        data class Found(val partner: EmreTeam) : SequentialPartnerResult()
        data class NeedsBacktrack(val targetTeam: EmreTeam) : SequentialPartnerResult()
        object Bye : SequentialPartnerResult()
    }
    
    /**
     * Mevcut eşleşmeyi boz ve yeniden eşleştir
     */
    private fun breakExistingMatch(
        targetTeam: EmreTeam,
        searchingTeam: EmreTeam,
        candidateMatches: MutableList<CandidateMatch>,
        usedTeams: MutableSet<Long>
    ) {
        // Target team'in mevcut eşleşmesini bul ve kaldır
        val existingMatch = candidateMatches.find { 
            it.team1.id == targetTeam.id || it.team2.id == targetTeam.id 
        }
        
        existingMatch?.let { match ->
            // Eski eşleşmeyi kaldır
            candidateMatches.remove(match)
            usedTeams.remove(match.team1.id)
            usedTeams.remove(match.team2.id)
            
            // Yeni eşleşmeyi ekle
            candidateMatches.add(
                CandidateMatch(
                    team1 = searchingTeam,
                    team2 = targetTeam,
                    isAsymmetricPoints = searchingTeam.points != targetTeam.points
                )
            )
            usedTeams.add(searchingTeam.id)
            usedTeams.add(targetTeam.id)
        }
    }
    
    /**
     * AYNI PUANLI KONTROL VE TUR ONAY - Kullanıcının tarif ettiği sistem
     */
    private fun checkAndApproveRound(
        candidateMatches: List<CandidateMatch>,
        byeTeam: EmreTeam?,
        currentRound: Int
    ): EmrePairingResult {
        
        if (candidateMatches.isEmpty()) {
            return EmrePairingResult(
                matches = emptyList(),
                byeTeam = byeTeam,
                hasSamePointMatch = false,
                canContinue = false,
                candidateMatches = emptyList()
            )
        }
        
        // AYNI PUANLI EŞLEŞİM VAR MI KONTROL ET
        val hasSamePointMatch = candidateMatches.any { !it.isAsymmetricPoints }
        
        if (hasSamePointMatch) {
            // EN AZ BİR AYNI PUANLI EŞLEŞİM VAR → TUR ONAYLANIR
            val matches = candidateMatches.map { candidate ->
                Match(
                    listId = candidate.team1.song.listId,
                    rankingMethod = "EMRE",
                    songId1 = candidate.team1.id,
                    songId2 = candidate.team2.id,
                    winnerId = null,
                    round = currentRound
                )
            }
            
            return EmrePairingResult(
                matches = matches,
                byeTeam = byeTeam,
                hasSamePointMatch = true,
                canContinue = true,
                candidateMatches = candidateMatches
            )
        } else {
            // HİÇBİR EŞLEŞİM AYNI PUANDA DEĞİL → TUR İPTAL, ŞAMPIYONA BITER
            return EmrePairingResult(
                matches = emptyList(),
                byeTeam = byeTeam,
                hasSamePointMatch = false,
                canContinue = false,
                candidateMatches = candidateMatches
            )
        }
    }
    
    /**
     * Eşleşme arayan ekip için partner bul
     */
    private fun findPartnerForTeam(
        searchingTeam: EmreTeam,
        teams: List<EmreTeam>,
        usedTeams: Set<Long>,
        matchHistory: Set<Pair<Long, Long>>,
        startIndex: Int
    ): PartnerSearchResult {
        
        // İleri doğru ara (kendisinden sonraki ekipler)
        for (i in startIndex + 1 until teams.size) {
            val potentialPartner = teams[i]
            
            if (potentialPartner.id in usedTeams) continue
            
            if (!hasTeamsPlayedBefore(searchingTeam.id, potentialPartner.id, matchHistory)) {
                return PartnerSearchResult.Found(potentialPartner)
            }
        }
        
        // İleri doğru partner bulunamadı → geriye doğru ara
        for (i in startIndex - 1 downTo 0) {
            val potentialPartner = teams[i]
            
            if (potentialPartner.id in usedTeams) continue
            
            if (!hasTeamsPlayedBefore(searchingTeam.id, potentialPartner.id, matchHistory)) {
                // Bu takım önceki bir eşleşmede kullanılıyorsa backtrack gerekir
                return PartnerSearchResult.RequiresBacktrack(potentialPartner)
            }
        }
        
        // Hiçbir yerde partner bulunamadı
        return PartnerSearchResult.NotFound
    }
    
    /**
     * Partner arama sonucu
     */
    sealed class PartnerSearchResult {
        data class Found(val partner: EmreTeam) : PartnerSearchResult()
        object NotFound : PartnerSearchResult()
        data class RequiresBacktrack(val conflictTeam: EmreTeam) : PartnerSearchResult()
    }
    
    /**
     * Geri dönüş senaryosunu handle et
     */
    private fun handleBacktrackScenario(
        candidateMatches: MutableList<CandidateMatch>,
        usedTeams: MutableSet<Long>,
        conflictTeam: EmreTeam,
        searchingTeam: EmreTeam
    ) {
        // Conflict team'in önceki eşleşmesini bul ve kaldır
        val conflictMatch = candidateMatches.find { 
            it.team1.id == conflictTeam.id || it.team2.id == conflictTeam.id 
        }
        
        conflictMatch?.let { match ->
            candidateMatches.remove(match)
            usedTeams.remove(match.team1.id)
            usedTeams.remove(match.team2.id)
            
            // Yeni eşleşmeyi ekle
            candidateMatches.add(
                CandidateMatch(
                    team1 = searchingTeam,
                    team2 = conflictTeam,
                    isAsymmetricPoints = searchingTeam.points != conflictTeam.points
                )
            )
            usedTeams.add(searchingTeam.id)
            usedTeams.add(conflictTeam.id)
        }
    }
    
    /**
     * İki takımın daha önce oynayıp oynamadığını kontrol et
     */
    private fun hasTeamsPlayedBefore(team1Id: Long, team2Id: Long, matchHistory: Set<Pair<Long, Long>>): Boolean {
        val pair1 = Pair(team1Id, team2Id)
        val pair2 = Pair(team2Id, team1Id)
        val hasPlayed = (pair1 in matchHistory) || (pair2 in matchHistory)
        
        // 🔍 DEBUG LOG - Duplicate kontrolü
        if (hasPlayed) {
            android.util.Log.w("EmreSystemCorrect", "🚫 DUPLICATE DETECTED: Team $team1Id and $team2Id have played before!")
            android.util.Log.w("EmreSystemCorrect", "Match History size: ${matchHistory.size}")
            android.util.Log.w("EmreSystemCorrect", "Looking for: $pair1 or $pair2")
        }
        
        return hasPlayed
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