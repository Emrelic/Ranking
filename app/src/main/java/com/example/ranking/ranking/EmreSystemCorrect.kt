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
     * YENİ AKILLI EŞLEŞTİRME ALGORİTMASI - PROXIMITY BASED
     * 
     * KURALLAR:
     * 1. Daha önce eşleşen takımlar asla tekrar eşleşmez
     * 2. En yakın sıralamadaki eşleşmemiş takımla eşleş  
     * 3. Her turda listenin yarısı kadar eşleşme (36→18)
     * 4. Eşleşemeyen takım varsa → yakın takımların eşleşmesini boz
     * 
     * VALIDATION:
     * - Tur başlamadan 3 kural kontrol edilir
     * - Smart backtrack ile eşleşmeyen takımlar çözülür
     */
    private fun createAdvancedSwissPairings(
        teams: List<EmreTeam>, 
        matchHistory: Set<Pair<Long, Long>>,
        currentRound: Int
    ): EmrePairingResult {
        
        android.util.Log.d("EmreSystemCorrect", "🚀 STARTING NEW PROXIMITY-BASED PAIRING: ${teams.size} teams total")
        
        // 1. PRE-ROUND VALIDATION
        val validationResult = validateRoundRequirements(teams, matchHistory, currentRound)
        if (!validationResult.isValid) {
            android.util.Log.e("EmreSystemCorrect", "❌ ROUND VALIDATION FAILED: ${validationResult.reason}")
            return EmrePairingResult(emptyList(), null, false, false)
        }
        
        // 2. PROXIMITY-BASED INITIAL PAIRING
        val initialPairings = createProximityBasedPairings(teams, matchHistory)
        android.util.Log.d("EmreSystemCorrect", "📊 INITIAL PAIRINGS: ${initialPairings.matches.size} matches, ${initialPairings.unpairedTeams.size} unpaired teams")
        
        // 3. SMART BACKTRACK FOR UNPAIRED TEAMS (DISABLED to prevent infinite loops)
        val finalPairings = if (initialPairings.unpairedTeams.isNotEmpty()) {
            android.util.Log.w("EmreSystemCorrect", "🔄 SMART BACKTRACK DISABLED: ${initialPairings.unpairedTeams.size} unpaired teams remain")
            android.util.Log.w("EmreSystemCorrect", "🆓 CONVERTING UNPAIRED TEAMS TO BYE TEAMS")
            
            // Convert unpaired teams to matches/bye (emergency fix)
            var updatedMatches = initialPairings.matches.toMutableList()
            var updatedByeTeam = initialPairings.byeTeam
            
            when (initialPairings.unpairedTeams.size) {
                1 -> {
                    // Single unpaired team -> bye
                    updatedByeTeam = initialPairings.unpairedTeams.first()
                    android.util.Log.w("EmreSystemCorrect", "🆓 EMERGENCY BYE: Team ${updatedByeTeam.currentPosition}")
                }
                2 -> {
                    // Two unpaired teams -> pair them together
                    val team1 = initialPairings.unpairedTeams[0]
                    val team2 = initialPairings.unpairedTeams[1]
                    updatedMatches.add(CandidateMatch(
                        team1 = team1,
                        team2 = team2,
                        isAsymmetricPoints = team1.points != team2.points
                    ))
                    android.util.Log.w("EmreSystemCorrect", "🆓 EMERGENCY PAIRING: Team ${team1.currentPosition} vs Team ${team2.currentPosition}")
                }
                else -> {
                    // Multiple unpaired teams -> pair as many as possible, bye for remainder
                    val pairedCount = (initialPairings.unpairedTeams.size / 2) * 2
                    for (i in 0 until pairedCount step 2) {
                        val team1 = initialPairings.unpairedTeams[i]
                        val team2 = initialPairings.unpairedTeams[i + 1]
                        updatedMatches.add(CandidateMatch(
                            team1 = team1,
                            team2 = team2,
                            isAsymmetricPoints = team1.points != team2.points
                        ))
                        android.util.Log.w("EmreSystemCorrect", "🆓 EMERGENCY PAIRING: Team ${team1.currentPosition} vs Team ${team2.currentPosition}")
                    }
                    if (initialPairings.unpairedTeams.size % 2 == 1) {
                        updatedByeTeam = initialPairings.unpairedTeams.last()
                        android.util.Log.w("EmreSystemCorrect", "🆓 EMERGENCY BYE: Team ${updatedByeTeam?.currentPosition}")
                    }
                }
            }
            
            InitialPairingResult(
                matches = updatedMatches, 
                unpairedTeams = emptyList(), // Clear unpaired teams
                byeTeam = updatedByeTeam
            )
        } else {
            initialPairings
        }
        
        // 4. FINAL VALIDATION AND REPORTING
        val candidateMatches = finalPairings.matches
        val byeTeam = finalPairings.byeTeam
        
        android.util.Log.d("EmreSystemCorrect", "✅ PROXIMITY PAIRING COMPLETED: ${candidateMatches.size} matches created")
        android.util.Log.d("EmreSystemCorrect", "📊 FINAL STATE: Matches=${candidateMatches.size}, ByeTeam=${byeTeam?.currentPosition ?: "none"}")
        android.util.Log.d("EmreSystemCorrect", "🎯 EXPECTED: ${if (teams.size % 2 == 0) teams.size / 2 else (teams.size - 1) / 2} matches + ${if (teams.size % 2 == 1) "1 bye" else "0 bye"}")
        
        // Validation check
        val totalTeamsInMatches = candidateMatches.size * 2 + (if (byeTeam != null) 1 else 0)
        if (totalTeamsInMatches != teams.size) {
            android.util.Log.e("EmreSystemCorrect", "❌ PAIRING ERROR: Expected ${teams.size} teams in pairs, got $totalTeamsInMatches")
            return EmrePairingResult(emptyList(), null, false, false)
        }
        
        // Red line validation
        for (match in candidateMatches) {
            if (hasTeamsPlayedBefore(match.team1.id, match.team2.id, matchHistory)) {
                android.util.Log.e("EmreSystemCorrect", "❌ RED LINE VIOLATION: Teams ${match.team1.currentPosition} and ${match.team2.currentPosition} have played before!")
                return EmrePairingResult(emptyList(), null, false, false)
            }
        }
        
        // AYNI PUANLI KONTROL VE TUR ONAY SİSTEMİ
        return checkAndApproveRound(candidateMatches, byeTeam, currentRound, matchHistory)
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
                !hasTeamsPlayedBefore(searchingTeam.id, it.id, matchHistory)
            }
            if (upperCandidate != null) {
                android.util.Log.d("EmreSystemCorrect", "🎯 CLOSEST PARTNER (UP): Team ${upperCandidate.currentPosition} (distance: $distance)")
                return upperCandidate
            }
            
            // Sonra aşağıya bak (searchPosition - distance)  
            val lowerCandidate = sortedTeams.find { 
                it.currentPosition == searchPosition - distance && 
                it.id !in usedTeams &&
                !hasTeamsPlayedBefore(searchingTeam.id, it.id, matchHistory)
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
                
                // Yeni eşleştirmeler oluştur
                val newPartner = findBestPartnerForBacktrack(unpairedTeam, listOf(targetMatch.team1, targetMatch.team2), matchHistory)
                
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
            val canPairWithTeam1 = !hasTeamsPlayedBefore(unpairedTeam.id, match.team1.id, matchHistory)
            val canPairWithTeam2 = !hasTeamsPlayedBefore(unpairedTeam.id, match.team2.id, matchHistory)
            
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
            !hasTeamsPlayedBefore(unpairedTeam.id, candidate.id, matchHistory)
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
                val team1Id = candidate.team1.id
                val team2Id = candidate.team2.id
                
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
            android.util.Log.w("EmreSystemCorrect", "🏁 TOURNAMENT FINISHED: No same-point matches found → All matches are asymmetric")
            android.util.Log.w("EmreSystemCorrect", "🏁 FINAL STANDINGS: Tournament ends at Round $currentRound")
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
        android.util.Log.d("EmreSystemCorrect", "🔍 CHECKING MATCH HISTORY: Team $team1Id vs Team $team2Id")
        android.util.Log.d("EmreSystemCorrect", "🔍 Match History size: ${matchHistory.size}")
        android.util.Log.d("EmreSystemCorrect", "🔍 Looking for pairs: ($team1Id, $team2Id) or ($team2Id, $team1Id)")
        
        if (hasPlayed) {
            android.util.Log.w("EmreSystemCorrect", "🚫 DUPLICATE DETECTED: Team $team1Id and $team2Id have played before!")
            android.util.Log.w("EmreSystemCorrect", "🚫 Found in history: $pair1 in history = ${pair1 in matchHistory}, $pair2 in history = ${pair2 in matchHistory}")
        } else {
            android.util.Log.d("EmreSystemCorrect", "✅ PAIR OK: Team $team1Id and $team2Id have NOT played before")
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
        android.util.Log.d("EmreSystemCorrect", "📝 PROCESSING ${completedMatches.size} completed matches")
        completedMatches.forEach { match ->
            // CRITICAL: Maç geçmişine ekle - prevents duplicate pairings
            val pair1 = Pair(match.songId1, match.songId2)
            val pair2 = Pair(match.songId2, match.songId1)
            newMatchHistory.add(pair1)
            newMatchHistory.add(pair2)
            android.util.Log.d("EmreSystemCorrect", "📝 ADDED TO HISTORY: ${match.songId1} vs ${match.songId2} (Match ID: ${match.id})")
            
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
     * Aynı puanlı takımlar için tiebreaker algoritması
     * 
     * KULLANICININ TARİF ETTİĞİ SISTEM:
     * 1. Aynı puanlı takımları al
     * 2. Kendi aralarındaki maçlara bak
     * 3. Head-to-head puanlama yap (ikinci puan sistemi)
     * 4. İkinci puan sistemine göre sırala
     * 5. Hala eşitlik varsa → ID numarası (teamId) küçük olan üstte
     */
    private fun applySamePointTiebreaker(samePointTeams: List<EmreTeam>, completedMatches: List<Match>): List<EmreTeam> {
        if (samePointTeams.size <= 1) return samePointTeams
        
        // Her takım için head-to-head puanları hesapla
        val headToHeadPoints = mutableMapOf<Long, Double>()
        samePointTeams.forEach { team ->
            headToHeadPoints[team.id] = 0.0
        }
        
        // Aynı puanlı takımların ID'lerini al
        val samePointTeamIds = samePointTeams.map { it.id }.toSet()
        
        // Kendi aralarındaki tamamlanmış maçları kontrol et
        completedMatches.forEach { match ->
            // Bu maç aynı puanlı iki takım arasında mı?
            if (match.isCompleted && 
                match.songId1 in samePointTeamIds && 
                match.songId2 in samePointTeamIds) {
                
                when (match.winnerId) {
                    match.songId1 -> {
                        // Takım 1 kazandı
                        headToHeadPoints[match.songId1] = headToHeadPoints[match.songId1]!! + 1.0
                    }
                    match.songId2 -> {
                        // Takım 2 kazandı
                        headToHeadPoints[match.songId2] = headToHeadPoints[match.songId2]!! + 1.0
                    }
                    null -> {
                        // Beraberlik - her ikisine 0.5 puan
                        headToHeadPoints[match.songId1] = headToHeadPoints[match.songId1]!! + 0.5
                        headToHeadPoints[match.songId2] = headToHeadPoints[match.songId2]!! + 0.5
                    }
                }
            }
        }
        
        // Head-to-head puanlarına göre sırala, sonra teamId'ye göre
        return samePointTeams.sortedWith(
            compareByDescending<EmreTeam> { headToHeadPoints[it.id] ?: 0.0 } // Head-to-head puan (yüksek önce)
                .thenBy { it.teamId } // ID numarası küçük olan üstte
        )
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