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
        var currentPosition: Int = 0,           // Anlık sıra numarası (değişken)
        val teamId: Long,                       // Sabit ID numarası  
        var preRoundPosition: Int = 0,          // 🆕 Tur öncesi anlık sıralama (tiebreaker için)
        var byePassed: Boolean = false,         // Bye geçti mi?
        var byeCount: Int = 0                   // 🆕 Kaç kere bye geçti (maksimum 1)
    ) {
        val id: Long get() = song.id
        
        // Deep copy fonksiyonu - CRITICAL for avoiding duplicate match bug
        fun deepCopy(): EmreTeam {
            return EmreTeam(
                song = song,
                points = points,
                currentPosition = currentPosition,
                teamId = teamId,
                preRoundPosition = preRoundPosition,
                byePassed = byePassed,
                byeCount = byeCount
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
    
    // 🆕 YENİ KAVRAMLAR İÇİN DATA CLASS'LAR
    
    /**
     * Eşleşme motoru durumları
     */
    data class PairingEngineState(
        val candidateMatches: MutableList<CandidateMatch> = mutableListOf(),
        val usedTeams: MutableSet<Long> = mutableSetOf(),
        val unpairedTeams: MutableList<EmreTeam> = mutableListOf(), // 🆕 Eşleşilemeden kalanlar grubu
        var byeTeam: EmreTeam? = null,
        var currentSearchingTeam: EmreTeam? = null // 🆕 Eşleştirme arayan takım
    )
    
    /**
     * Backtrack operasyonu sonucu
     */
    data class BacktrackResult(
        val success: Boolean,
        val newMatch: CandidateMatch? = null,
        val stolenPartnerTeam: EmreTeam? = null, // 🆕 Partneri çalınan takım
        val reason: String = ""
    )
    
    /**
     * 🆕 Eşleştirme arama sonuçları
     */
    sealed class PairingSearchResult {
        data class Success(val partner: EmreTeam) : PairingSearchResult()
        data class RequiresBacktrack(val targetTeam: EmreTeam) : PairingSearchResult()
        object TournamentFinished : PairingSearchResult()
    }
    
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
                currentPosition = index + 1,      // Anlık sıra numarası
                teamId = song.id,                 // Sabit ID numarası
                preRoundPosition = index + 1,     // 🆕 İlk turda tur öncesi = başlangıç sırası
                byePassed = false,
                byeCount = 0
            )
        }
        
        android.util.Log.d("EmreSystemCorrect", "🏁 TOURNAMENT INITIALIZED: ${teams.size} teams with ID and position numbers")
        teams.forEach { team ->
            android.util.Log.d("EmreSystemCorrect", "📋 TEAM: ID=${team.teamId}, Position=${team.currentPosition}, PreRound=${team.preRoundPosition}")
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
        
        // 🆕 TUR ÖNCESİ ANLIK SIRALAMA HAFIZAYA ALINIR
        val teamsWithPreRoundPosition = state.teams.map { team ->
            team.deepCopy().apply {
                preRoundPosition = currentPosition // Tur öncesi sıralama hafızaya al
            }
        }
        
        // Takımları anlık sıra numaralarına göre sırala 
        val sortedTeams = teamsWithPreRoundPosition.sortedBy { it.currentPosition }
        
        android.util.Log.d("EmreSystemCorrect", "📊 PRE-ROUND POSITIONS STORED: Round ${state.currentRound}")
        sortedTeams.forEach { team ->
            android.util.Log.d("EmreSystemCorrect", "📍 TEAM ${team.currentPosition}: PreRound=${team.preRoundPosition}, Points=${team.points}")
        }
        
        // 🆕 YENİ BYE KONTROL SİSTEMİ
        val (teamsToMatch, byeTeam) = handleByeTeamAdvanced(sortedTeams)
        
        // 🆕 YENİ EŞLEŞTIRME MOTORU
        val result = createNewPairingEngine(teamsToMatch, state.matchHistory, state.currentRound)
        
        // Bye ekibini result'a ekle
        return result.copy(byeTeam = byeTeam)
    }
    
    
    /**
     * 🆕 YENİ BYE KONTROL SİSTEMİ - Geliştirilmiş İsviçre Usulü Kuralları
     * 
     * KURALLAR:
     * - Çift sayıda takım: Hiçbir takım bye geçemez
     * - Tek sayıda takım: En alttaki (en düşük anlık sıralı) takım bye geçer
     * - Bir takım turnuvada en fazla 1 kere bye geçebilir
     */
    private fun handleByeTeamAdvanced(sortedTeams: List<EmreTeam>): Pair<List<EmreTeam>, EmreTeam?> {
        android.util.Log.d("EmreSystemCorrect", "🔍 BYE CONTROL: ${sortedTeams.size} teams total")
        
        if (sortedTeams.size % 2 == 0) {
            // Çift sayıda takım - hiçbir takım bye geçemez
            android.util.Log.d("EmreSystemCorrect", "✅ EVEN TEAMS: No bye needed (${sortedTeams.size} teams)")
            return Pair(sortedTeams, null)
        }
        
        // Tek sayıda takım - bye geçecek takımı bul
        android.util.Log.d("EmreSystemCorrect", "⚠️ ODD TEAMS: Looking for bye candidate (${sortedTeams.size} teams)")
        
        // En alttaki takımdan başlayarak bye geçmemiş takımı bul
        var byeTeam: EmreTeam? = null
        val remainingTeams = mutableListOf<EmreTeam>()
        
        // Tersten tarayarak en alttaki bye geçmemiş takımı bul
        for (i in sortedTeams.indices.reversed()) {
            val team = sortedTeams[i]
            if (byeTeam == null && team.byeCount == 0) {
                byeTeam = team
                android.util.Log.d("EmreSystemCorrect", "🆓 BYE ASSIGNED: Team ${team.currentPosition} (ID: ${team.teamId}) - first time bye")
            } else {
                remainingTeams.add(0, team) // Başa ekle (sıralama korunur)
            }
        }
        
        // Eğer tüm takımlar bye geçmişse, en alttakini tekrar bye yap (olağanüstü durum)
        if (byeTeam == null) {
            byeTeam = sortedTeams.last()
            remainingTeams.addAll(sortedTeams.dropLast(1))
            android.util.Log.w("EmreSystemCorrect", "⚠️ EMERGENCY BYE: Team ${byeTeam.currentPosition} (all teams have had bye)")
        }
        
        return Pair(remainingTeams, byeTeam)
    }
    
    /**
     * 🆕 YENİ EŞLEŞTIRME MOTORU - Geliştirilmiş İsviçre Usulü
     * 
     * ALGORİTMA AKIŞI:
     * 1. En üst anlık sıralı takım → "eşleştirme arayan takım" statüsü
     * 2. Kendinden sonraki ilk eşleşmemiş takımla eşleştir
     * 3. Eğer bulamazsa geriye dön ve mevcut eşleşmeyi boz
     * 4. "Partneri çalınan takım" ile "eşleşilemeden kalanlar" kontrolü
     * 5. Tüm takımlar eşleşince "eş puanlı eşleşme" kontrolü
     * 6. En az 1 eş puanlı varsa tur oyna, yoksa turnuva bitir
     */
    private fun createNewPairingEngine(
        teams: List<EmreTeam>, 
        matchHistory: Set<Pair<Long, Long>>,
        currentRound: Int
    ): EmrePairingResult {
        
        android.util.Log.d("EmreSystemCorrect", "🚀 STARTING NEW PAIRING ENGINE: ${teams.size} teams total, Round $currentRound")
        
        // 1. EŞLEŞTIRME MOTORU DURUMU BAŞLAT
        val engineState = PairingEngineState()
        val availableTeams = teams.sortedBy { it.currentPosition }.toMutableList()
        
        android.util.Log.d("EmreSystemCorrect", "📋 AVAILABLE TEAMS: ${availableTeams.size} teams ready for pairing")
        availableTeams.forEach { team ->
            android.util.Log.d("EmreSystemCorrect", "👥 TEAM ${team.currentPosition}: ID=${team.teamId}, Points=${team.points}")
        }
        
        // 2. ANA EŞLEŞTIRME DÖNGÜSÜ
        var safetyCounter = 0
        val maxIterations = teams.size * 3 // Güvenlik limiti
        
        while (availableTeams.isNotEmpty() && safetyCounter < maxIterations) {
            safetyCounter++
            
            // En üst anlık sıralı takımı "eşleştirme arayan takım" yap
            val searchingTeam = availableTeams.removeFirstOrNull()
            if (searchingTeam == null) break
            
            engineState.currentSearchingTeam = searchingTeam
            android.util.Log.d("EmreSystemCorrect", "🔍 SEARCHING TEAM: ${searchingTeam.currentPosition} looking for partner")
            
            // Bu takım için eşleştirme arayan statüsü
            val pairingResult = findPartnerForSearchingTeam(
                searchingTeam, 
                availableTeams, 
                engineState, 
                matchHistory
            )
            
            when (pairingResult) {
                is PairingSearchResult.Success -> {
                    // Başarılı eşleştirme - her iki takımı da available listeden çıkar
                    availableTeams.remove(pairingResult.partner)
                    engineState.usedTeams.add(searchingTeam.id)
                    engineState.usedTeams.add(pairingResult.partner.id)
                    
                    android.util.Log.d("EmreSystemCorrect", "✅ MATCH CREATED: ${searchingTeam.currentPosition} vs ${pairingResult.partner.currentPosition}")
                }
                is PairingSearchResult.RequiresBacktrack -> {
                    // Backtrack gerekiyor - önceki eşleştirmeyi boz
                    val backtrackResult = performAdvancedBacktrack(
                        searchingTeam, 
                        pairingResult.targetTeam,
                        engineState, 
                        availableTeams, 
                        matchHistory
                    )
                    
                    if (!backtrackResult.success) {
                        // Backtrack başarısız - eşleşilemeden kalanlar grubuna ekle
                        engineState.unpairedTeams.add(searchingTeam)
                        android.util.Log.w("EmreSystemCorrect", "⚠️ UNPAIRED: Team ${searchingTeam.currentPosition} cannot find partner")
                    }
                }
                is PairingSearchResult.TournamentFinished -> {
                    // Turnuva bitti - kimse ile eşleşemiyor
                    android.util.Log.e("EmreSystemCorrect", "🏁 TOURNAMENT END: Team ${searchingTeam.currentPosition} cannot pair with anyone")
                    return EmrePairingResult(emptyList(), null, false, false, emptyList())
                }
            }
        }
        
        // 4. EŞLEŞİLEMEDEN KALANLAR GRUBU İŞLEME
        if (engineState.unpairedTeams.isNotEmpty()) {
            android.util.Log.w("EmreSystemCorrect", "⚠️ PROCESSING UNPAIRED TEAMS: ${engineState.unpairedTeams.size} teams need resolution")
            // Eşleşilemeden kalanlar için emergency pairing logic burada olacak
            // Şimdilik basit eşleştirme yapalım
            while (engineState.unpairedTeams.size >= 2) {
                val team1 = engineState.unpairedTeams.removeAt(0)
                val team2 = engineState.unpairedTeams.removeAt(0)
                
                engineState.candidateMatches.add(CandidateMatch(
                    team1 = team1,
                    team2 = team2, 
                    isAsymmetricPoints = team1.points != team2.points
                ))
                android.util.Log.w("EmreSystemCorrect", "🆓 EMERGENCY PAIR: ${team1.currentPosition} vs ${team2.currentPosition}")
            }
            
            // Tek kalan takım varsa bye yap
            if (engineState.unpairedTeams.isNotEmpty()) {
                engineState.byeTeam = engineState.unpairedTeams.first()
                android.util.Log.w("EmreSystemCorrect", "🆓 EMERGENCY BYE: ${engineState.byeTeam?.currentPosition}")
            }
        }
        
        // 5. FINAL VALIDATION
        val totalTeamsInPairs = engineState.candidateMatches.size * 2 + (if (engineState.byeTeam != null) 1 else 0)
        if (totalTeamsInPairs != teams.size) {
            android.util.Log.e("EmreSystemCorrect", "❌ TEAM COUNT MISMATCH: Expected ${teams.size}, got $totalTeamsInPairs")
            return EmrePairingResult(emptyList(), null, false, false, emptyList())
        }
        
        android.util.Log.d("EmreSystemCorrect", "✅ PAIRING ENGINE COMPLETED: ${engineState.candidateMatches.size} matches, ${if (engineState.byeTeam != null) "1 bye" else "no bye"}")
        
        // 6. EŞ PUANLI EŞLEŞME KONTROLÜ VE TUR ONAY
        return performAsymmetricPointCheck(engineState.candidateMatches, engineState.byeTeam, currentRound)
    }
    
    /**
     * 🆕 EŞLEŞTİRME ARAYAN TAKIM İÇİN PARTNER BULMA
     * 
     * ALGORİTMA:
     * 1. Kendinden sonraki takımları kontrol et
     * 2. Eşleşmemiş ve daha önce oynamamış ilk takımla eşleş
     * 3. Bulamazsa kendinden önceki takımları kontrol et
     * 4. Önceki takım kullanılmışsa backtrack gerekir
     */
    private fun findPartnerForSearchingTeam(
        searchingTeam: EmreTeam,
        availableTeams: List<EmreTeam>,
        engineState: PairingEngineState,
        matchHistory: Set<Pair<Long, Long>>
    ): PairingSearchResult {
        
        android.util.Log.d("EmreSystemCorrect", "🔍 PARTNER SEARCH: Team ${searchingTeam.currentPosition} searching...")
        
        // 1. ÖNCE SONRAKI TAKIMLARI KONTROL ET
        for (candidate in availableTeams) {
            if (candidate.currentPosition <= searchingTeam.currentPosition) continue // Sadece sonrakiler
            if (candidate.id in engineState.usedTeams) continue // Zaten kullanılmış
            
            // Daha önce oynamış mı kontrol et
            if (hasTeamsPlayedBefore(searchingTeam.teamId, candidate.teamId, matchHistory)) {
                android.util.Log.d("EmreSystemCorrect", "⏭️ SKIP: ${candidate.currentPosition} (played before)")
                continue
            }
            
            // UYGUN PARTNER BULUNDU
            android.util.Log.d("EmreSystemCorrect", "✅ FORWARD PARTNER: ${searchingTeam.currentPosition} → ${candidate.currentPosition}")
            
            // Eşleştirmeyi candidate matches'a ekle
            engineState.candidateMatches.add(CandidateMatch(
                team1 = searchingTeam,
                team2 = candidate,
                isAsymmetricPoints = searchingTeam.points != candidate.points
            ))
            
            return PairingSearchResult.Success(candidate)
        }
        
        android.util.Log.d("EmreSystemCorrect", "⬆️ NO FORWARD PARTNER: Checking backwards...")
        
        // 2. GERİYE DÖN - ÖNCEKİ TAKIMLARI KONTROL ET
        for (candidate in availableTeams.reversed()) {
            if (candidate.currentPosition >= searchingTeam.currentPosition) continue // Sadece öncekiler
            
            // Daha önce oynamış mı kontrol et
            if (hasTeamsPlayedBefore(searchingTeam.teamId, candidate.teamId, matchHistory)) {
                android.util.Log.d("EmreSystemCorrect", "⏭️ SKIP: ${candidate.currentPosition} (played before)")
                continue
            }
            
            // Bu takım zaten kullanılmış mı?
            if (candidate.id in engineState.usedTeams) {
                // BACKTRACK GEREKLİ - bu takımın eşleşmesini boz
                android.util.Log.w("EmreSystemCorrect", "🔄 BACKTRACK NEEDED: ${searchingTeam.currentPosition} wants ${candidate.currentPosition}")
                return PairingSearchResult.RequiresBacktrack(candidate)
            }
            
            // UYGUN PARTNER BULUNDU (GERİDEN)
            android.util.Log.d("EmreSystemCorrect", "✅ BACKWARD PARTNER: ${searchingTeam.currentPosition} → ${candidate.currentPosition}")
            
            // Eşleştirmeyi candidate matches'a ekle
            engineState.candidateMatches.add(CandidateMatch(
                team1 = searchingTeam,
                team2 = candidate,
                isAsymmetricPoints = searchingTeam.points != candidate.points
            ))
            
            return PairingSearchResult.Success(candidate)
        }
        
        // 3. HİÇBİR YERİDE PARTNER BULUNAMADI
        android.util.Log.e("EmreSystemCorrect", "🏁 NO PARTNER FOUND: Team ${searchingTeam.currentPosition} exhausted all options")
        return PairingSearchResult.TournamentFinished
    }
    
    /**
     * 🆕 GELİŞMİŞ BACKTRACK İŞLEMİ
     * 
     * KULLANICININ ALGORİTMASI:
     * 1. Hedef takımın mevcut eşleşmesini boz
     * 2. "Partneri çalınan takım" ile "eşleşilemeden kalanlar" kontrolü
     * 3. En uygun çözümü uygula
     */
    private fun performAdvancedBacktrack(
        searchingTeam: EmreTeam,
        targetTeam: EmreTeam,
        engineState: PairingEngineState,
        availableTeams: MutableList<EmreTeam>,
        matchHistory: Set<Pair<Long, Long>>
    ): BacktrackResult {
        
        android.util.Log.w("EmreSystemCorrect", "🔄 ADVANCED BACKTRACK: ${searchingTeam.currentPosition} breaking ${targetTeam.currentPosition}'s match")
        
        // 1. HEDEF TAKIMIN MEVCUT EŞLEŞMESİNİ BUL VE BOZ
        val existingMatch = engineState.candidateMatches.find { 
            it.team1.id == targetTeam.id || it.team2.id == targetTeam.id 
        }
        
        if (existingMatch == null) {
            android.util.Log.e("EmreSystemCorrect", "❌ BACKTRACK FAILED: ${targetTeam.currentPosition} has no existing match")
            return BacktrackResult(false, reason = "Target team has no existing match")
        }
        
        // 2. EŞLEŞMEYİ BOZ VE PARTNERİ ÇALINAN TAKIMI BELİRLE
        engineState.candidateMatches.remove(existingMatch)
        val stolenPartnerTeam = if (existingMatch.team1.id == targetTeam.id) existingMatch.team2 else existingMatch.team1
        
        // Used teams'den çıkar
        engineState.usedTeams.remove(existingMatch.team1.id)
        engineState.usedTeams.remove(existingMatch.team2.id)
        
        // Available teams'e geri ekle
        availableTeams.add(stolenPartnerTeam)
        availableTeams.sortBy { it.currentPosition }
        
        android.util.Log.w("EmreSystemCorrect", "💥 MATCH BROKEN: ${existingMatch.team1.currentPosition} vs ${existingMatch.team2.currentPosition}")
        android.util.Log.w("EmreSystemCorrect", "👤 STOLEN PARTNER: Team ${stolenPartnerTeam.currentPosition}")
        
        // 3. YENİ EŞLEŞMEYİ OLUŞTUR (searchingTeam + targetTeam)
        val newMatch = CandidateMatch(
            team1 = searchingTeam,
            team2 = targetTeam,
            isAsymmetricPoints = searchingTeam.points != targetTeam.points
        )
        engineState.candidateMatches.add(newMatch)
        engineState.usedTeams.add(searchingTeam.id)
        engineState.usedTeams.add(targetTeam.id)
        
        // Target team'i available'dan çıkar (zaten available değildi ama güvenlik için)
        availableTeams.remove(targetTeam)
        
        android.util.Log.w("EmreSystemCorrect", "✅ NEW MATCH CREATED: ${searchingTeam.currentPosition} vs ${targetTeam.currentPosition}")
        
        return BacktrackResult(
            success = true,
            newMatch = newMatch,
            stolenPartnerTeam = stolenPartnerTeam,
            reason = "Backtrack successful"
        )
    }
    
    /**
     * 🆕 ESİMETRİK PUAN KONTROLÜ VE TUR ONAY
     * 
     * KULLANICININ KURALI:
     * - En az 1 aynı puanlı eşleşme varsa → tur oyna
     * - Hiçbir aynı puanlı eşleşme yoksa → turnuva bitir
     */
    private fun performAsymmetricPointCheck(
        candidateMatches: List<CandidateMatch>,
        byeTeam: EmreTeam?,
        currentRound: Int
    ): EmrePairingResult {
        
        android.util.Log.d("EmreSystemCorrect", "🎯 ASYMMETRIC POINT CHECK: Round $currentRound, ${candidateMatches.size} matches")
        
        // Her eşleştirmeyi kontrol et
        candidateMatches.forEachIndexed { index, match ->
            android.util.Log.d("EmreSystemCorrect", "🔍 MATCH $index: ${match.team1.currentPosition}(${match.team1.points}p) vs ${match.team2.currentPosition}(${match.team2.points}p) → Asymmetric=${match.isAsymmetricPoints}")
        }
        
        // Aynı puanlı eşleşme var mı kontrol et
        val hasSamePointMatch = if (currentRound == 1) {
            android.util.Log.d("EmreSystemCorrect", "✅ FIRST ROUND: Always continue")
            true // İlk tur her zaman oynanır
        } else {
            val samePointMatches = candidateMatches.filter { !it.isAsymmetricPoints }
            android.util.Log.d("EmreSystemCorrect", "⚖️ SAME POINT MATCHES: ${samePointMatches.size} out of ${candidateMatches.size}")
            samePointMatches.forEach { match ->
                android.util.Log.d("EmreSystemCorrect", "⚖️ EQUAL POINTS: ${match.team1.currentPosition}(${match.team1.points}p) vs ${match.team2.currentPosition}(${match.team2.points}p)")
            }
            candidateMatches.any { !it.isAsymmetricPoints }
        }
        
        if (hasSamePointMatch) {
            // TUR OYNA
            android.util.Log.d("EmreSystemCorrect", "✅ TOURNAMENT CONTINUES: Round $currentRound approved")
            
            val matches = candidateMatches.map { candidate ->
                Match(
                    listId = candidate.team1.song.listId,
                    rankingMethod = "EMRE_CORRECT",
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
            // TURNUVA BİTİR
            android.util.Log.e("EmreSystemCorrect", "🏁 TOURNAMENT FINISHED: All matches are asymmetric (Round $currentRound)")
            android.util.Log.e("EmreSystemCorrect", "🏁 FINAL ANALYSIS: ${candidateMatches.size} total matches, 0 same-point matches")
            
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
     * SIRA SIRA EŞLEŞTİRME - Kullanıcının tarif ettiği DOĞRU algoritma
     * ✅ DÜZELTME: Displaced team tracking eklendi
     * 
     * ⚠️ KRİTİK FARK: Geriye dönükten uygun takım bulduğunda eşleştirmeyi KONTROL ET
     * ✅ YENİ: Backtrack sırasında displaced team'i otomatik track eder
     */
    private fun findPartnerSequentiallyWithDisplacement(
        searchingTeam: EmreTeam,
        teams: List<EmreTeam>, 
        usedTeams: MutableSet<Long>,
        matchHistory: Set<Pair<Long, Long>>,
        candidateMatches: MutableList<CandidateMatch>,
        displacedTeams: MutableSet<Long> // ✅ YENİ PARAMETRE
    ): SequentialPartnerResult {
        
        android.util.Log.d("EmreSystemCorrect", "🔍 PARTNER SEARCH: Team ${searchingTeam.currentPosition} (ID: ${searchingTeam.id}) searching for partner")
        
        // ÖNCE SONRAKI EKİPLERE BAK (kendisinden sonraki sıradakiler)
        for (i in searchingTeam.currentPosition until teams.size) {
            val potentialPartner = teams.find { it.currentPosition == i + 1 }
            if (potentialPartner == null || potentialPartner.id in usedTeams) continue
            
            // ⚠️ KRİTİK KONTROLLER:
            // 1. Daha önce oynamışlar mı kontrol et (match history)
            if (hasTeamsPlayedBefore(searchingTeam.teamId, potentialPartner.teamId, matchHistory)) {
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
            android.util.Log.d("EmreSystemCorrect", "✅ FORWARD PARTNER FOUND: Team ${searchingTeam.currentPosition} will pair with Team ${potentialPartner.currentPosition}")
            return SequentialPartnerResult.Found(potentialPartner)
        }
        
        android.util.Log.d("EmreSystemCorrect", "⬆️ NO FORWARD PARTNER: Team ${searchingTeam.currentPosition} checking backwards")
        
        // SONRAKI EKİPLERDE BULUNAMADI → GERİYE DÖN (kendinden öncekiler)
        for (i in searchingTeam.currentPosition - 2 downTo 0) {
            val potentialPartner = teams.find { it.currentPosition == i + 1 }
            if (potentialPartner == null) continue
            
            // ⚠️ KRİTİK KONTROLLER:
            // 1. Daha önce oynamışlar mı kontrol et
            if (hasTeamsPlayedBefore(searchingTeam.teamId, potentialPartner.teamId, matchHistory)) {
                continue // Bu takımla daha önce oynamış, sonrakini dene
            }
            
            // 2. Aday listede zaten bu ikili var mı kontrol et  
            if (candidateMatches.any { 
                (it.team1.id == searchingTeam.id && it.team2.id == potentialPartner.id) ||
                (it.team1.id == potentialPartner.id && it.team2.id == searchingTeam.id)
            }) {
                continue // Aday listede zaten var, sonrakini dene
            }
            
            // 🎯 KRİTİK NOKTA: Bu takım zaten kullanılmış mı kontrol et
            if (potentialPartner.id in usedTeams) {
                // EVET KULLANILMIŞ → EŞLEŞTIRMEYI BOZ VE YENİSİNİ YAP
                android.util.Log.w("EmreSystemCorrect", "🔄 BACKTRACK EXECUTING: Team ${searchingTeam.currentPosition} wants Team ${potentialPartner.currentPosition} (breaking existing match)")
                
                // MEVCUT EŞLEŞMEYİ BOZ
                val existingMatch = candidateMatches.find { 
                    it.team1.id == potentialPartner.id || it.team2.id == potentialPartner.id 
                }
                
                existingMatch?.let { match ->
                    android.util.Log.w("EmreSystemCorrect", "💥 REMOVING MATCH: Team ${match.team1.currentPosition} vs Team ${match.team2.currentPosition}")
                    candidateMatches.remove(match)
                    usedTeams.remove(match.team1.id)
                    usedTeams.remove(match.team2.id)
                    
                    // ✅ KRİTİK DÜZELTME: BOZULAN TAKIMI DISPLACED QUEUE'YA EKLE
                    val displacedTeam = if (match.team1.id == potentialPartner.id) match.team2 else match.team1
                    displacedTeams.add(displacedTeam.id)
                    android.util.Log.d("EmreSystemCorrect", "🔄 DISPLACED TEAM ADDED: Team ${displacedTeam.currentPosition} added to displaced queue")
                }
                
                // YENİ EŞLEŞMEYİ OLUŞTUR
                android.util.Log.d("EmreSystemCorrect", "✅ NEW MATCH CREATED: Team ${searchingTeam.currentPosition} vs Team ${potentialPartner.currentPosition}")
                return SequentialPartnerResult.Found(potentialPartner)
            } else {
                // HAYIR KULLANILMAMIŞA → direkt eşleştir
                android.util.Log.d("EmreSystemCorrect", "✅ BACKWARD PARTNER FOUND: Team ${searchingTeam.currentPosition} will pair with Team ${potentialPartner.currentPosition}")
                return SequentialPartnerResult.Found(potentialPartner)
            }
        }
        
        // HIÇBIR YERDE PARTNER BULUNAMADI → kontrol et
        android.util.Log.w("EmreSystemCorrect", "🏁 NO PARTNER FOUND: Team ${searchingTeam.currentPosition} cannot find any partner")
        android.util.Log.w("EmreSystemCorrect", "🏁 REASON: This team has played against all other available teams or all are used")
        
        // KRİTİK FIX REMOVED: FORCE PAIRING VİOLATES RED LINE RULE #1
        // KIRMIZI ÇİZGİ İHLALİ: Aynı takımlar tekrar eşleşemez - bu fix kaldırıldı
        
        // Eğer bu takım displaced ise ve partner bulamıyorsa bye yap
        if (searchingTeam.id in displacedTeams) {
            android.util.Log.w("EmreSystemCorrect", "🆓 DISPLACED TEAM TO BYE: Team ${searchingTeam.currentPosition} will get bye")
            return SequentialPartnerResult.Bye
        }
        
        return SequentialPartnerResult.TournamentFinished
    }
    
    /**
     * 1. PRE-ROUND VALIDATION SYSTEM
     * Tur başlamadan önce 3 temel kuralı kontrol eder
     */
    private fun validateRoundRequirements(
        teams: List<EmreTeam>,
        matchHistory: Set<Pair<Long, Long>>,
        currentRound: Int
    ): ValidationResult {
        
        android.util.Log.d("EmreSystemCorrect", "🔍 VALIDATING ROUND REQUIREMENTS...")
        
        // KURAL 1: Takım sayısı kontrolü
        if (teams.isEmpty()) {
            return ValidationResult(false, "No teams available for pairing")
        }
        
        // KURAL 2: Her turda tam yarı eşleştirme olmalı (36→18, 37→18+1bye)
        val expectedMatches = if (teams.size % 2 == 0) teams.size / 2 else (teams.size - 1) / 2
        val expectedBye = teams.size % 2
        
        android.util.Log.d("EmreSystemCorrect", "📊 EXPECTED: $expectedMatches matches + $expectedBye bye")
        
        // KURAL 3: Match history integrity check
        val totalPossiblePairs = teams.size * (teams.size - 1) / 2
        val playedPairs = matchHistory.size
        val remainingPairs = totalPossiblePairs - playedPairs
        
        android.util.Log.d("EmreSystemCorrect", "📈 MATCH HISTORY: $playedPairs played, $remainingPairs remaining out of $totalPossiblePairs total")
        
        if (remainingPairs < expectedMatches) {
            return ValidationResult(false, "Not enough unique pairs remaining: need $expectedMatches, have $remainingPairs")
        }
        
        return ValidationResult(true, "All requirements validated")
    }
    
    /**
     * 2. PROXIMITY-BASED INITIAL PAIRING
     * En yakın sıralamadaki takımlarla eşleştirme yapar
     */
    private fun createProximityBasedPairings(
        teams: List<EmreTeam>,
        matchHistory: Set<Pair<Long, Long>>
    ): InitialPairingResult {
        
        android.util.Log.d("EmreSystemCorrect", "🎯 CREATING PROXIMITY-BASED PAIRINGS...")
        
        val matches = mutableListOf<CandidateMatch>()
        val usedTeams = mutableSetOf<Long>()
        val unpairedTeams = mutableListOf<EmreTeam>()
        var byeTeam: EmreTeam? = null
        
        // Takımları sıraya göre sırala
        val sortedTeams = teams.sortedBy { it.currentPosition }
        
        for (searchingTeam in sortedTeams) {
            if (searchingTeam.id in usedTeams) continue
            
            android.util.Log.d("EmreSystemCorrect", "🔍 FINDING PARTNER FOR: Team ${searchingTeam.currentPosition}")
            
            // En yakın uygun partner'ı bul
            val partner = findClosestAvailablePartner(searchingTeam, sortedTeams, usedTeams, matchHistory)
            
            if (partner != null) {
                // Eşleştirme oluştur
                matches.add(CandidateMatch(
                    team1 = searchingTeam,
                    team2 = partner,
                    isAsymmetricPoints = searchingTeam.points != partner.points
                ))
                usedTeams.add(searchingTeam.id)
                usedTeams.add(partner.id)
                android.util.Log.d("EmreSystemCorrect", "✅ PAIRED: Team ${searchingTeam.currentPosition} vs Team ${partner.currentPosition}")
            } else {
                // Partner bulunamadı
                unpairedTeams.add(searchingTeam)
                android.util.Log.w("EmreSystemCorrect", "⚠️ UNPAIRED: Team ${searchingTeam.currentPosition}")
            }
        }
        
        // ENHANCED BYE TEAM LOGIC: Handle odd number of teams properly
        if (teams.size % 2 == 1) {
            if (unpairedTeams.size == 1) {
                byeTeam = unpairedTeams.first()
                unpairedTeams.clear()
                android.util.Log.d("EmreSystemCorrect", "🆓 BYE ASSIGNED: Team ${byeTeam.currentPosition}")
            } else if (unpairedTeams.size > 1) {
                // Multiple unpaired teams, assign lowest position as bye
                val lowestPositionTeam = unpairedTeams.maxByOrNull { it.currentPosition }
                if (lowestPositionTeam != null) {
                    byeTeam = lowestPositionTeam
                    unpairedTeams.remove(lowestPositionTeam)
                    android.util.Log.d("EmreSystemCorrect", "🆓 BYE ASSIGNED (from multiple): Team ${byeTeam.currentPosition}")
                }
            }
        } else if (unpairedTeams.size > 0) {
            android.util.Log.w("EmreSystemCorrect", "⚠️ UNEXPECTED: Even number of teams (${teams.size}) but ${unpairedTeams.size} unpaired teams remain")
        }
        
        return InitialPairingResult(matches, unpairedTeams, byeTeam)
    }
    
    /**
     * 3. EN YAKIN PARTNER BULMA ALGORİTMASI
     * Sıraya göre en yakın eşleşmemiş takımı bulur
     */
    private fun findClosestAvailablePartner(
        searchingTeam: EmreTeam,
        sortedTeams: List<EmreTeam>,
        usedTeams: Set<Long>,
        matchHistory: Set<Pair<Long, Long>>
    ): EmreTeam? {
        
        val searchPosition = searchingTeam.currentPosition
        
        // İki yönde de ara: önce yakın olanlar, sonra uzak olanlar
        var distance = 1
        val maxDistance = sortedTeams.size
        
        while (distance < maxDistance) {
            // Önce yukarıya bak (searchPosition + distance)
            val upperCandidate = sortedTeams.find { 
                it.currentPosition == searchPosition + distance && 
                it.id !in usedTeams &&
                !hasTeamsPlayedBefore(searchingTeam.teamId, it.teamId, matchHistory)
            }
            if (upperCandidate != null) {
                android.util.Log.d("EmreSystemCorrect", "🎯 CLOSEST PARTNER (UP): Team ${upperCandidate.currentPosition} (distance: $distance)")
                return upperCandidate
            }
            
            // Sonra aşağıya bak (searchPosition - distance)  
            val lowerCandidate = sortedTeams.find { 
                it.currentPosition == searchPosition - distance && 
                it.id !in usedTeams &&
                !hasTeamsPlayedBefore(searchingTeam.teamId, it.teamId, matchHistory)
            }
            if (lowerCandidate != null) {
                android.util.Log.d("EmreSystemCorrect", "🎯 CLOSEST PARTNER (DOWN): Team ${lowerCandidate.currentPosition} (distance: $distance)")
                return lowerCandidate
            }
            
            distance++
        }
        
        return null // Partner bulunamadı
    }
    
    /**
     * 4. SMART BACKTRACK FOR UNPAIRED TEAMS
     * Eşleşemeyen takımlar için yakın eşleştirmeleri bozar
     */
    private fun resolveUnpairedTeamsWithSmartBacktrack(
        initialResult: InitialPairingResult,
        teams: List<EmreTeam>,
        matchHistory: Set<Pair<Long, Long>>
    ): InitialPairingResult {
        
        android.util.Log.d("EmreSystemCorrect", "🔄 RESOLVING UNPAIRED TEAMS WITH SMART BACKTRACK...")
        
        val matches = initialResult.matches.toMutableList()
        val unpairedTeams = initialResult.unpairedTeams.toMutableList()
        var byeTeam = initialResult.byeTeam
        
        // CRITICAL FIX: Use while loop with dynamic list to handle newly added unpaired teams
        var index = 0
        var iterationCount = 0
        val maxIterations = teams.size * 5 // Safety limit to prevent infinite loops
        
        while (index < unpairedTeams.size && iterationCount < maxIterations) {
            iterationCount++
            val unpairedTeam = unpairedTeams[index]
            android.util.Log.w("EmreSystemCorrect", "🔄 RESOLVING UNPAIRED ($index/${unpairedTeams.size}): Team ${unpairedTeam.currentPosition}")
            
            // En yakın eşleştirmeyi bul ve boz
            val targetMatch = findClosestMatchToBreak(unpairedTeam, matches, teams, matchHistory)
            
            if (targetMatch != null) {
                android.util.Log.w("EmreSystemCorrect", "💥 BREAKING MATCH: Team ${targetMatch.team1.currentPosition} vs Team ${targetMatch.team2.currentPosition}")
                
                // Eşleştirmeyi boz
                matches.remove(targetMatch)
                
                // Yeni eşleştirmeler oluştur - TÜM TAKIMLARDAN daha önce oynamadığı birini bul
                val allAvailableTeams = listOf(targetMatch.team1, targetMatch.team2) + teams.filter { team ->
                    team.id != unpairedTeam.id && 
                    team.id != targetMatch.team1.id && 
                    team.id != targetMatch.team2.id &&
                    !hasTeamsPlayedBefore(unpairedTeam.id, team.id, matchHistory)
                }
                val newPartner = findBestPartnerForBacktrack(unpairedTeam, allAvailableTeams, matchHistory)
                
                if (newPartner != null) {
                    val remainingTeam = if (newPartner == targetMatch.team1) targetMatch.team2 else targetMatch.team1
                    
                    // Yeni eşleştirme ekle
                    matches.add(CandidateMatch(
                        team1 = unpairedTeam,
                        team2 = newPartner,
                        isAsymmetricPoints = unpairedTeam.points != newPartner.points
                    ))
                    
                    // CRITICAL FIX: Remove current unpaired team and add remaining team
                    unpairedTeams.removeAt(index)  // Remove current team at index
                    unpairedTeams.add(remainingTeam)  // Add remaining team to end
                    
                    android.util.Log.d("EmreSystemCorrect", "✅ NEW PAIRING: Team ${unpairedTeam.currentPosition} vs Team ${newPartner.currentPosition}")
                    android.util.Log.w("EmreSystemCorrect", "⚠️ NEW UNPAIRED: Team ${remainingTeam.currentPosition} (added to queue)")
                    
                    // Don't increment index since we removed current item
                    continue
                }
            }
            
            // Move to next unpaired team
            index++
        }
        
        // Check for infinite loop protection
        if (iterationCount >= maxIterations) {
            android.util.Log.e("EmreSystemCorrect", "💀 INFINITE LOOP DETECTED: Breaking after $iterationCount iterations")
            android.util.Log.e("EmreSystemCorrect", "💀 REMAINING UNPAIRED: ${unpairedTeams.size} teams")
            unpairedTeams.forEachIndexed { i, team ->
                android.util.Log.e("EmreSystemCorrect", "💀 STUCK TEAM[$i]: Team ${team.currentPosition} (ID: ${team.id})")
            }
        }
        
        // CRITICAL VALIDATION: Ensure no teams are lost during backtrack
        val totalProcessedTeams = matches.size * 2 + unpairedTeams.size + (if (byeTeam != null) 1 else 0)
        if (totalProcessedTeams != teams.size) {
            android.util.Log.e("EmreSystemCorrect", "❌ BACKTRACK VALIDATION FAILED: Expected ${teams.size} teams, got $totalProcessedTeams")
            android.util.Log.e("EmreSystemCorrect", "📊 BREAKDOWN: ${matches.size} matches (${matches.size * 2} teams) + ${unpairedTeams.size} unpaired + ${if (byeTeam != null) 1 else 0} bye")
            
            // Log unpaired teams for debugging
            unpairedTeams.forEachIndexed { i, team ->
                android.util.Log.e("EmreSystemCorrect", "🔍 UNPAIRED[$i]: Team ${team.currentPosition} (ID: ${team.id})")
            }
        } else {
            android.util.Log.d("EmreSystemCorrect", "✅ BACKTRACK VALIDATION PASSED: All ${teams.size} teams accounted for")
        }
        
        return InitialPairingResult(matches, unpairedTeams, byeTeam)
    }
    
    /**
     * 5. EN YAKIN EŞLEŞTİRMEYİ BULMA (BOZMAK İÇİN)
     */
    private fun findClosestMatchToBreak(
        unpairedTeam: EmreTeam,
        matches: List<CandidateMatch>,
        teams: List<EmreTeam>,
        matchHistory: Set<Pair<Long, Long>>
    ): CandidateMatch? {
        
        var closestMatch: CandidateMatch? = null
        var minDistance = Int.MAX_VALUE
        
        for (match in matches) {
            // Bu maçtaki takımlardan biriyle eşleşebilir mi?
            val canPairWithTeam1 = !hasTeamsPlayedBefore(unpairedTeam.teamId, match.team1.teamId, matchHistory)
            val canPairWithTeam2 = !hasTeamsPlayedBefore(unpairedTeam.teamId, match.team2.teamId, matchHistory)
            
            if (canPairWithTeam1 || canPairWithTeam2) {
                val distance1 = kotlin.math.abs(unpairedTeam.currentPosition - match.team1.currentPosition)
                val distance2 = kotlin.math.abs(unpairedTeam.currentPosition - match.team2.currentPosition)
                val minMatchDistance = kotlin.math.min(distance1, distance2)
                
                if (minMatchDistance < minDistance) {
                    minDistance = minMatchDistance
                    closestMatch = match
                }
            }
        }
        
        return closestMatch
    }
    
    /**
     * 6. BACKTRACK İÇİN EN İYİ PARTNER SEÇİMİ
     */
    private fun findBestPartnerForBacktrack(
        unpairedTeam: EmreTeam,
        candidates: List<EmreTeam>,
        matchHistory: Set<Pair<Long, Long>>
    ): EmreTeam? {
        
        return candidates.find { candidate ->
            !hasTeamsPlayedBefore(unpairedTeam.teamId, candidate.teamId, matchHistory)
        }
    }
    
    // Yardımcı data class'lar
    data class ValidationResult(val isValid: Boolean, val reason: String)
    data class InitialPairingResult(val matches: List<CandidateMatch>, val unpairedTeams: List<EmreTeam>, val byeTeam: EmreTeam?)
    
    /**
     * Sıralı partner arama sonucu
     */
    sealed class SequentialPartnerResult {
        data class Found(val partner: EmreTeam) : SequentialPartnerResult()
        object Bye : SequentialPartnerResult()
        object TournamentFinished : SequentialPartnerResult()
    }
    
    // breakExistingMatch fonksiyonu kaldırıldı - backtrack işlemi findPartnerSequentially içinde yapılıyor
    
    /**
     * AYNI PUANLI KONTROL VE TUR ONAY - Kullanıcının tarif ettiği sistem
     */
    private fun checkAndApproveRound(
        candidateMatches: List<CandidateMatch>,
        byeTeam: EmreTeam?,
        currentRound: Int,
        matchHistory: Set<Pair<Long, Long>>
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
        // ⚠️ ÖNEMLİ: İlk turda (currentRound == 1) herkes 0 puanda - özel durum
        
        android.util.Log.d("EmreSystemCorrect", "🎯 TOURNAMENT FINISH CHECK: Round $currentRound, ${candidateMatches.size} candidate matches")
        candidateMatches.forEachIndexed { index, match ->
            android.util.Log.d("EmreSystemCorrect", "🔍 MATCH $index: Team ${match.team1.currentPosition} (${match.team1.points}p) vs Team ${match.team2.currentPosition} (${match.team2.points}p) → isAsymmetric=${match.isAsymmetricPoints}")
        }
        
        val hasSamePointMatch = if (currentRound == 1) {
            android.util.Log.d("EmreSystemCorrect", "✅ FIRST ROUND: Always continue")
            true // İlk tur her zaman oynanır
        } else {
            val samePointMatches = candidateMatches.filter { !it.isAsymmetricPoints }
            android.util.Log.d("EmreSystemCorrect", "🔍 SAME POINT MATCHES: ${samePointMatches.size} out of ${candidateMatches.size}")
            samePointMatches.forEach { match ->
                android.util.Log.d("EmreSystemCorrect", "⚖️ SAME POINTS: Team ${match.team1.currentPosition} (${match.team1.points}p) vs Team ${match.team2.currentPosition} (${match.team2.points}p)")
            }
            candidateMatches.any { !it.isAsymmetricPoints }  // isAsymmetricPoints=false → aynı puanlı
        }
        
        if (hasSamePointMatch) {
            // EN AZ BİR AYNI PUANLI EŞLEŞİM VAR → TUR ONAYLANIR
            android.util.Log.d("EmreSystemCorrect", "✅ TOURNAMENT CONTINUES: At least one same-point match found → Round $currentRound will be played")
            
            // ⚠️ CRITICAL FIX: Final duplicate check before creating matches
            val validMatches = candidateMatches.filter { candidate ->
                val team1Id = candidate.team1.teamId
                val team2Id = candidate.team2.teamId
                
                // Final duplicate kontrolü - bu çift daha önce eşleşmiş mi?
                val isDuplicate = hasTeamsPlayedBefore(team1Id, team2Id, matchHistory)
                
                if (isDuplicate) {
                    android.util.Log.e("EmreSystemCorrect", "🚨 CRITICAL: Duplicate pair in final matches: $team1Id vs $team2Id - FILTERING OUT!")
                }
                
                !isDuplicate
            }
            
            val matches = validMatches.map { candidate ->
                Match(
                    listId = candidate.team1.song.listId,
                    rankingMethod = "EMRE_CORRECT",
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
            android.util.Log.e("EmreSystemCorrect", "🏁 TOURNAMENT FINISHED: No same-point matches found → All matches are asymmetric")
            android.util.Log.e("EmreSystemCorrect", "🏁 DETAILED ANALYSIS: Why tournament ended at Round $currentRound")
            android.util.Log.e("EmreSystemCorrect", "🏁 TOTAL MATCHES: ${candidateMatches.size} matches analyzed")
            
            candidateMatches.forEachIndexed { index, match ->
                val team1Points = match.team1.points
                val team2Points = match.team2.points
                val pointDiff = kotlin.math.abs(team1Points - team2Points)
                android.util.Log.e("EmreSystemCorrect", "🏁 MATCH $index: Team ${match.team1.currentPosition}(${team1Points}p) vs Team ${match.team2.currentPosition}(${team2Points}p) → Diff: ${pointDiff}p, Asymmetric: ${match.isAsymmetricPoints}")
            }
            
            android.util.Log.e("EmreSystemCorrect", "🏁 SAME POINT COUNT: ${candidateMatches.count { !it.isAsymmetricPoints }} matches have same points")
            android.util.Log.e("EmreSystemCorrect", "🏁 ASYMMETRIC COUNT: ${candidateMatches.count { it.isAsymmetricPoints }} matches have different points")
            android.util.Log.e("EmreSystemCorrect", "🏁 RULE: Tournament continues ONLY if at least 1 same-point match exists")
            android.util.Log.e("EmreSystemCorrect", "🏁 RESULT: TOURNAMENT ENDS → No same-point matches found")
            
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
     * DEPRECATED - Eski partner arama fonksiyonu
     * ✅ YENİ: findPartnerSequentiallyWithDisplacement kullanılıyor
     */
    @Deprecated("Use findPartnerSequentiallyWithDisplacement instead")
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
            
            if (!hasTeamsPlayedBefore(searchingTeam.teamId, potentialPartner.teamId, matchHistory)) {
                return PartnerSearchResult.Found(potentialPartner)
            }
        }
        
        // İleri doğru partner bulunamadı → geriye doğru ara
        for (i in startIndex - 1 downTo 0) {
            val potentialPartner = teams[i]
            
            if (potentialPartner.id in usedTeams) continue
            
            if (!hasTeamsPlayedBefore(searchingTeam.teamId, potentialPartner.teamId, matchHistory)) {
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
     * DEPRECATED - Geri dönüş senaryosunu handle et
     * ✅ YENİ: Backtrack findPartnerSequentiallyWithDisplacement içinde yapılıyor
     */
    @Deprecated("Backtrack is now handled inside findPartnerSequentiallyWithDisplacement")
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
        
        // 🔍 DETAILED DEBUG LOG - Match history kontrolü
        android.util.Log.d("EmreSystemCorrect", "🔍 CHECKING MATCH HISTORY: TeamID $team1Id vs TeamID $team2Id")
        android.util.Log.d("EmreSystemCorrect", "🔍 Match History size: ${matchHistory.size}")
        android.util.Log.d("EmreSystemCorrect", "🔍 Looking for pairs: ($team1Id, $team2Id) or ($team2Id, $team1Id)")
        
        if (hasPlayed) {
            android.util.Log.w("EmreSystemCorrect", "🚫 DUPLICATE DETECTED: TeamID $team1Id and $team2Id have played before!")
            android.util.Log.w("EmreSystemCorrect", "🚫 Found in history: $pair1 in history = ${pair1 in matchHistory}, $pair2 in history = ${pair2 in matchHistory}")
        } else {
            android.util.Log.d("EmreSystemCorrect", "✅ PAIR OK: TeamID $team1Id and $team2Id have NOT played before")
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
        
        // 🆕 Create song-to-team mapping from current teams
        val songToTeamMap = state.teams.associate { team -> team.song.id to team.teamId }
        android.util.Log.d("EmreSystemCorrect", "🗺️ SONG-TO-TEAM MAPPING CREATED: ${songToTeamMap.size} teams mapped")
        
        // 🆕 BYE GEÇEN TAKIMA PUAN EKLE VE BYE COUNT GÜNCELLE
        byeTeam?.let { bye ->
            val teamIndex = updatedTeams.indexOfFirst { it.id == bye.id }
            if (teamIndex >= 0) {
                val currentTeam = updatedTeams[teamIndex]
                updatedTeams[teamIndex] = currentTeam.copy(
                    points = currentTeam.points + 1.0,
                    byePassed = true,
                    byeCount = currentTeam.byeCount + 1 // 🆕 Bye count artır
                )
                android.util.Log.d("EmreSystemCorrect", "🆓 BYE UPDATE: Team ${currentTeam.currentPosition} → Points: ${currentTeam.points + 1.0}, ByeCount: ${currentTeam.byeCount + 1}")
            }
        }
        
        // Maç sonuçlarını işle
        android.util.Log.d("EmreSystemCorrect", "📝 PROCESSING ${completedMatches.size} completed matches")
        completedMatches.forEach { match ->
            // 🆕 CRITICAL FIX: Convert song IDs to stable team IDs for match history
            val teamId1 = songToTeamMap[match.songId1]
            val teamId2 = songToTeamMap[match.songId2]
            
            if (teamId1 != null && teamId2 != null) {
                val pair1 = Pair(teamId1, teamId2)
                val pair2 = Pair(teamId2, teamId1)
                newMatchHistory.add(pair1)
                newMatchHistory.add(pair2)
                android.util.Log.d("EmreSystemCorrect", "📝 ADDED TO HISTORY: TeamID $teamId1 vs TeamID $teamId2 (Match ID: ${match.id}, SongIDs: ${match.songId1} vs ${match.songId2})")
            } else {
                android.util.Log.e("EmreSystemCorrect", "❌ MAPPING ERROR: Cannot find team IDs for songs ${match.songId1} vs ${match.songId2}")
            }
            
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
     * KULLANICININ TARİF ETTİĞİ TİEBREAKER KURALLARI:
     * 1. Puana göre yüksekten alçağa
     * 2. Eşit puanlı takımlar için:
     *    a) Aynı puanlı ekipler ayrı grup alınır
     *    b) Kendi aralarındaki maçlara göre ikinci puan listesi yapılır
     *    c) İkinci puan listesine göre sıralama
     *    d) İkinci puanda da eşitlik → ID numarası (teamId) küçük olan üstte
     * 3. Yeni sıra numaraları atanır (1-79)
     */
    private fun reorderTeamsEmreStyle(teams: List<EmreTeam>, completedMatches: List<Match>): List<EmreTeam> {
        // Önce puana göre grupla
        val pointGroups = teams.groupBy { it.points }
        val sortedResults = mutableListOf<EmreTeam>()
        
        // Her puan grubunu en yüksekten en alçağa işle
        pointGroups.keys.sortedDescending().forEach { points ->
            val samePointTeams = pointGroups[points]!!
            
            if (samePointTeams.size == 1) {
                // Tek takım varsa direkt ekle
                sortedResults.addAll(samePointTeams)
            } else {
                // Aynı puanlı takımlar için karmaşık tiebreaker
                val tiebreakerSorted = applySamePointTiebreaker(samePointTeams, completedMatches)
                sortedResults.addAll(tiebreakerSorted)
            }
        }
        
        // Yeni sıra numaralarını ata
        return sortedResults.mapIndexed { index, team ->
            team.copy(currentPosition = index + 1)
        }
    }
    
    /**
     * 🆕 YENİ TIEBREAKER ALGORİTMASI - Geliştirilmiş İsviçre Usulü
     * 
     * KULLANICININ YENİ SİSTEMİ:
     * 1. Aynı puanlı takımları al
     * 2. Kendi aralarındaki maçlara bak (bye puanları hariç)
     * 3. Head-to-head puanlama yap (ikinci puan sistemi)
     * 4. İkinci puan sistemine göre sırala
     * 5. Hala eşitlik varsa → Tur öncesi anlık sıralama (düşük önce)
     */
    private fun applySamePointTiebreaker(samePointTeams: List<EmreTeam>, completedMatches: List<Match>): List<EmreTeam> {
        if (samePointTeams.size <= 1) return samePointTeams
        
        android.util.Log.d("EmreSystemCorrect", "🔄 TIEBREAKER: Processing ${samePointTeams.size} same-point teams")
        samePointTeams.forEach { team ->
            android.util.Log.d("EmreSystemCorrect", "📊 TEAM ${team.currentPosition}: Points=${team.points}, PreRound=${team.preRoundPosition}")
        }
        
        // Her takım için head-to-head puanları hesapla
        val headToHeadPoints = mutableMapOf<Long, Double>()
        samePointTeams.forEach { team ->
            headToHeadPoints[team.id] = 0.0
        }
        
        // Aynı puanlı takımların ID'lerini al
        val samePointTeamIds = samePointTeams.map { it.id }.toSet()
        
        // 🆕 Kendi aralarındaki tamamlanmış maçları kontrol et (BYE PUANLARI HARİÇ)
        completedMatches.forEach { match ->
            // Bu maç aynı puanlı iki takım arasında mı ve bye maçı değil mi?
            if (match.isCompleted && 
                match.songId1 in samePointTeamIds && 
                match.songId2 in samePointTeamIds &&
                match.songId1 != match.songId2) { // Bye maçları değil
                
                when (match.winnerId) {
                    match.songId1 -> {
                        // Takım 1 kazandı
                        headToHeadPoints[match.songId1] = headToHeadPoints[match.songId1]!! + 1.0
                        android.util.Log.d("EmreSystemCorrect", "⚔️ HEAD-TO-HEAD: ${match.songId1} beats ${match.songId2} → +1.0")
                    }
                    match.songId2 -> {
                        // Takım 2 kazandı
                        headToHeadPoints[match.songId2] = headToHeadPoints[match.songId2]!! + 1.0
                        android.util.Log.d("EmreSystemCorrect", "⚔️ HEAD-TO-HEAD: ${match.songId2} beats ${match.songId1} → +1.0")
                    }
                    null -> {
                        // Beraberlik - her ikisine 0.5 puan
                        headToHeadPoints[match.songId1] = headToHeadPoints[match.songId1]!! + 0.5
                        headToHeadPoints[match.songId2] = headToHeadPoints[match.songId2]!! + 0.5
                        android.util.Log.d("EmreSystemCorrect", "⚔️ HEAD-TO-HEAD: ${match.songId1} draws ${match.songId2} → +0.5 each")
                    }
                }
            }
        }
        
        // Head-to-head puanlarını logla
        headToHeadPoints.forEach { (teamId, points) ->
            val team = samePointTeams.find { it.id == teamId }
            android.util.Log.d("EmreSystemCorrect", "📈 H2H POINTS: Team ${team?.currentPosition} → ${points}")
        }
        
        // 🆕 YENİ SIRALAMA: Head-to-head, sonra tur öncesi sıralama
        val sortedTeams = samePointTeams.sortedWith(
            compareByDescending<EmreTeam> { headToHeadPoints[it.id] ?: 0.0 } // Head-to-head puan (yüksek önce)
                .thenBy { it.preRoundPosition } // 🆕 Tur öncesi sıralama (düşük önce = üstte olan)
        )
        
        android.util.Log.d("EmreSystemCorrect", "🏆 TIEBREAKER RESULT:")
        sortedTeams.forEachIndexed { index, team ->
            android.util.Log.d("EmreSystemCorrect", "🥇 RANK ${index + 1}: Team ${team.currentPosition} (H2H: ${headToHeadPoints[team.id]}, PreRound: ${team.preRoundPosition})")
        }
        
        return sortedTeams
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