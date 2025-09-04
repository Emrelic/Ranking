package com.example.ranking.ranking

import com.example.ranking.data.Song
import com.example.ranking.data.Match
import com.example.ranking.data.RankingResult
import com.example.ranking.data.CriterionList
import com.example.ranking.data.CriterionScore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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
        var byeCount: Int = 0,                  // 🆕 Kaç kere bye geçti (maksimum 1)
        var secondaryPoints: Double = 0.0,      // 🆕 İkincil puan (H2H) - UI gösterimi için
        
        // 🆕 KRİTER SİSTEMİ YENİ ALANLAR
        var criteriaScores: MutableMap<String, Double> = mutableMapOf(),  // Her kriter için toplam puan
        var criteriaMatchCount: MutableMap<String, Int> = mutableMapOf(),  // Her kriter için kaç maçta puanlandı
        var compositeCriteriaScore: Double = 0.0,                         // Ağırlıklı kriter puanı
        var normalizedCriteriaScores: MutableMap<String, Double> = mutableMapOf()  // Normalize edilmiş kriter puanları
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
                byeCount = byeCount,
                secondaryPoints = secondaryPoints,
                criteriaScores = criteriaScores.toMutableMap(),
                criteriaMatchCount = criteriaMatchCount.toMutableMap(),
                compositeCriteriaScore = compositeCriteriaScore,
                normalizedCriteriaScores = normalizedCriteriaScores.toMutableMap()
            )
        }
        
        // 🆕 KRİTER PUAN HESAPLAMA FONKSİYONLARI
        fun addCriteriaScore(criterionName: String, score: Double) {
            criteriaScores[criterionName] = (criteriaScores[criterionName] ?: 0.0) + score
            criteriaMatchCount[criterionName] = (criteriaMatchCount[criterionName] ?: 0) + 1
        }
        
        fun getAverageCriteriaScore(criterionName: String): Double {
            val totalScore = criteriaScores[criterionName] ?: 0.0
            val matchCount = criteriaMatchCount[criterionName] ?: 0
            return if (matchCount > 0) totalScore / matchCount else 0.0
        }
        
        fun calculateCompositeCriteriaScore(criteriaWeights: Map<String, Double>): Double {
            var totalWeightedScore = 0.0
            var totalWeight = 0.0
            
            criteriaWeights.forEach { (criterionName, weight) ->
                val avgScore = getAverageCriteriaScore(criterionName)
                totalWeightedScore += avgScore * weight
                totalWeight += weight
            }
            
            compositeCriteriaScore = if (totalWeight > 0) totalWeightedScore / totalWeight else 0.0
            return compositeCriteriaScore
        }
    }
    
    data class EmreState(
        val teams: List<EmreTeam>,
        val matchHistory: Set<Pair<Long, Long>> = emptySet(), // Oynanan eşleşmeler
        val currentRound: Int = 1,
        val isComplete: Boolean = false,
        
        // 🆕 KRİTER SİSTEMİ ENTEGRASYONU
        val criterionList: CriterionList? = null,              // Kullanılan kriter listesi
        val criteriaNames: List<String> = emptyList(),         // Parse edilmiş kriter isimleri
        val criteriaWeights: Map<String, Double> = emptyMap(), // Her kriterin ağırlığı (varsayılan: eşit)
        val useCriteriaForTiebreaking: Boolean = false,        // Eşitlik durumunda kriter kullanılsın mı?
        val criteriaInfluenceFactor: Double = 0.1              // Kriter etkisinin ana puanlamadaki ağırlığı (0.0-1.0)
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
        val isAsymmetricPoints: Boolean, // Farklı puanlı mı?
        var matchNumber: Int = 0 // 🆕 Alternating match numbering için
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
        data class Success(val partner: EmreTeam, val displacedTeam: EmreTeam? = null) : PairingSearchResult()
        data class RequiresBacktrack(val targetTeam: EmreTeam) : PairingSearchResult()
        object TournamentFinished : PairingSearchResult()
        object Bye : PairingSearchResult()
    }
    
    // İki kademeli kontrol durumları
    enum class PairingPhase {
        CANDIDATE_CREATION,    // Aday eşleştirme oluşturma
        CONFIRMATION_PENDING,  // Onay bekliyor
        CONFIRMED,            // Onaylandı
        TOURNAMENT_FINISHED   // Turnuva bitti (asimetrik puan yok)
    }
    
    /**
     * Emre turnuvası başlat - Kriter sistemi ile
     */
    fun initializeEmreTournament(songs: List<Song>, criterionList: CriterionList? = null): EmreState {
        val teams = songs.mapIndexed { index, song ->
            EmreTeam(
                song = song, 
                points = 0.0, 
                currentPosition = index + 1,      // Anlık sıra numarası
                teamId = (1000 + index).toLong(), // 4 basamaklı TeamID: 1000, 1001, 1002...
                preRoundPosition = index + 1,     // 🆕 İlk turda tur öncesi = başlangıç sırası
                byePassed = false,
                byeCount = 0
            )
        }
        
        android.util.Log.d("EmreSystemCorrect", "🏁 TOURNAMENT INITIALIZED: ${teams.size} teams with ID and position numbers")
        teams.forEach { team ->
            android.util.Log.d("EmreSystemCorrect", "📋 TEAM: ID=${team.teamId}, Position=${team.currentPosition}, PreRound=${team.preRoundPosition}")
        }
        
        // 🆕 Kriter listesini parse et
        val criteriaNames = criterionList?.let { list ->
            try {
                val gson = Gson()
                val listType = object : TypeToken<List<String>>() {}.type
                gson.fromJson<List<String>>(list.criteria, listType)
            } catch (e: Exception) {
                android.util.Log.e("EmreSystemCorrect", "❌ CRITERIA PARSING ERROR: ${e.message}")
                emptyList()
            }
        } ?: emptyList()
        
        // 🆕 Eşit ağırlıklar oluştur (varsayılan)
        val criteriaWeights = if (criteriaNames.isNotEmpty()) {
            val equalWeight = 1.0 / criteriaNames.size
            criteriaNames.associateWith { equalWeight }
        } else {
            emptyMap()
        }
        
        android.util.Log.d("EmreSystemCorrect", "🏁 TOURNAMENT INITIALIZED: ${teams.size} teams with criteria support")
        android.util.Log.d("EmreSystemCorrect", "📊 CRITERIA: ${criteriaNames.joinToString(", ")}")
        android.util.Log.d("EmreSystemCorrect", "⚖️ WEIGHTS: $criteriaWeights")
        
        return EmreState(
            teams = teams,
            matchHistory = emptySet(),
            currentRound = 1,
            isComplete = false,
            criterionList = criterionList,
            criteriaNames = criteriaNames,
            criteriaWeights = criteriaWeights,
            useCriteriaForTiebreaking = criterionList != null,
            criteriaInfluenceFactor = if (criterionList != null) 0.1 else 0.0
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
        
        // 🆕 YENİ EŞLEŞTIRME MOTORU - Round numarası doğru kullanılacak
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
     * 🧪 TEST LOG 1: DUPLICATE PAIRING CHECK
     * Her eşleştirme öncesi duplicate kontrolünü detaylıca loglar
     */
    private fun logDuplicateTest(team1: EmreTeam, team2: EmreTeam, matchHistory: Set<Pair<Long, Long>>, testResult: Boolean, testName: String) {
        android.util.Log.i("TEST_DUPLICATE", "🧪 $testName: TeamID ${team1.teamId}(Pos:${team1.currentPosition}) vs TeamID ${team2.teamId}(Pos:${team2.currentPosition})")
        android.util.Log.i("TEST_DUPLICATE", "🧪 RESULT: ${if (testResult) "✅ DUPLICATE DETECTED - BLOCKED" else "✅ NEW PAIRING - ALLOWED"}")
        android.util.Log.i("TEST_DUPLICATE", "🧪 MATCH HISTORY SIZE: ${matchHistory.size} pairs played so far")
    }
    
    /**
     * 🧪 TEST LOG 2: EQUAL MATCH COUNT PER ROUND
     * Her turda eşit sayıda maç oynanıp oynanmadığını kontrol eder
     */
    private fun logMatchCountTest(teams: List<EmreTeam>, matches: List<CandidateMatch>, byeTeam: EmreTeam?, currentRound: Int) {
        val totalTeams = teams.size
        val expectedMatches = if (totalTeams % 2 == 0) totalTeams / 2 else (totalTeams - 1) / 2
        val expectedBye = totalTeams % 2
        val actualMatches = matches.size
        val actualBye = if (byeTeam != null) 1 else 0
        
        android.util.Log.i("TEST_MATCH_COUNT", "🧪 ROUND $currentRound MATCH COUNT TEST:")
        android.util.Log.i("TEST_MATCH_COUNT", "🧪 TOTAL TEAMS: $totalTeams")
        android.util.Log.i("TEST_MATCH_COUNT", "🧪 EXPECTED: $expectedMatches matches + $expectedBye bye")
        android.util.Log.i("TEST_MATCH_COUNT", "🧪 ACTUAL: $actualMatches matches + $actualBye bye")
        android.util.Log.i("TEST_MATCH_COUNT", "🧪 TEST RESULT: ${if (actualMatches == expectedMatches && actualBye == expectedBye) "✅ PASSED" else "❌ FAILED"}")
        
        if (actualMatches != expectedMatches || actualBye != expectedBye) {
            android.util.Log.e("TEST_MATCH_COUNT", "🧪 ERROR: Match count mismatch detected!")
        }
    }
    
    /**
     * 🧪 TEST LOG 3: TOURNAMENT END ALGORITHM 
     * Turnuva bitişinin algoritmasal doğruluğunu test eder
     */
    private fun logTournamentEndTest(candidateMatches: List<CandidateMatch>, canContinue: Boolean, currentRound: Int) {
        val samePointMatches = candidateMatches.filter { !it.isAsymmetricPoints }
        val asymmetricMatches = candidateMatches.filter { it.isAsymmetricPoints }
        
        android.util.Log.i("TEST_TOURNAMENT_END", "🧪 TOURNAMENT END ALGORITHM TEST - ROUND $currentRound:")
        android.util.Log.i("TEST_TOURNAMENT_END", "🧪 TOTAL CANDIDATE MATCHES: ${candidateMatches.size}")
        android.util.Log.i("TEST_TOURNAMENT_END", "🧪 SAME POINT MATCHES: ${samePointMatches.size}")
        android.util.Log.i("TEST_TOURNAMENT_END", "🧪 ASYMMETRIC MATCHES: ${asymmetricMatches.size}")
        
        samePointMatches.forEachIndexed { index, match ->
            android.util.Log.i("TEST_TOURNAMENT_END", "🧪 SAME POINT[$index]: Team ${match.team1.currentPosition}(${match.team1.points}p) vs Team ${match.team2.currentPosition}(${match.team2.points}p)")
        }
        
        asymmetricMatches.forEachIndexed { index, match ->
            android.util.Log.i("TEST_TOURNAMENT_END", "🧪 ASYMMETRIC[$index]: Team ${match.team1.currentPosition}(${match.team1.points}p) vs Team ${match.team2.currentPosition}(${match.team2.points}p)")
        }
        
        android.util.Log.i("TEST_TOURNAMENT_END", "🧪 ALGORITHM RULE: Tournament continues ONLY if samePointMatches > 0")
        android.util.Log.i("TEST_TOURNAMENT_END", "🧪 CAN CONTINUE: $canContinue")
        android.util.Log.i("TEST_TOURNAMENT_END", "🧪 TEST RESULT: ${if (canContinue == (samePointMatches.isNotEmpty())) "✅ ALGORITHM CORRECT" else "❌ ALGORITHM ERROR"}")
    }
    
    /**
     * 🔍 COMPREHENSIVE TOURNAMENT ANALYSIS
     * User requested detailed analysis of team pairings, duplicates, and round validation
     */
    fun generateComprehensiveTournamentAnalysis(state: EmreState, allMatches: List<Match>) {
        android.util.Log.e("TournamentAnalysis", "🔍 ===== COMPREHENSIVE GİS TOURNAMENT ANALYSIS =====")
        
        // 1. Tournament Overview
        android.util.Log.e("TournamentAnalysis", "📊 TOURNAMENT OVERVIEW:")
        android.util.Log.e("TournamentAnalysis", "📊 Total Teams: ${state.teams.size}")
        android.util.Log.e("TournamentAnalysis", "📊 Total Matches Played: ${allMatches.size}")
        android.util.Log.e("TournamentAnalysis", "📊 Match History Size: ${state.matchHistory.size}")
        
        // 2. Round-by-round analysis
        val roundMatches = allMatches.groupBy { it.round }
        android.util.Log.e("TournamentAnalysis", "")
        android.util.Log.e("TournamentAnalysis", "🔄 ROUND-BY-ROUND ANALYSIS:")
        roundMatches.keys.sorted().forEach { round ->
            val matches = roundMatches[round]!!
            android.util.Log.e("TournamentAnalysis", "🔄 Round $round: ${matches.size} matches (Expected: ${state.teams.size / 2})")
        }
        
        // 3. Individual team pairing history
        android.util.Log.e("TournamentAnalysis", "")
        android.util.Log.e("TournamentAnalysis", "👥 INDIVIDUAL TEAM PAIRING HISTORY:")
        
        val songToTeamMap = state.teams.associate { team -> team.song.id to team }
        
        state.teams.sortedBy { it.teamId }.forEach { team ->
            val opponentList = mutableListOf<String>()
            
            allMatches.forEach { match ->
                when {
                    match.songId1 == team.song.id -> {
                        val opponent = songToTeamMap[match.songId2]
                        opponent?.let { opponentList.add("TeamID ${it.teamId} (Round ${match.round})") }
                    }
                    match.songId2 == team.song.id -> {
                        val opponent = songToTeamMap[match.songId1]
                        opponent?.let { opponentList.add("TeamID ${it.teamId} (Round ${match.round})") }
                    }
                }
            }
            
            android.util.Log.e("TournamentAnalysis", "👥 TeamID ${team.teamId} played against: ${opponentList.joinToString(", ")}")
        }
        
        // 4. Duplicate pairing detection
        android.util.Log.e("TournamentAnalysis", "")
        android.util.Log.e("TournamentAnalysis", "🚫 DUPLICATE PAIRING ANALYSIS:")
        
        val pairingCounts = mutableMapOf<Pair<Long, Long>, Int>()
        allMatches.forEach { match ->
            val team1 = songToTeamMap[match.songId1]
            val team2 = songToTeamMap[match.songId2]
            
            if (team1 != null && team2 != null) {
                val normalizedPair = if (team1.teamId < team2.teamId) {
                    Pair(team1.teamId, team2.teamId)
                } else {
                    Pair(team2.teamId, team1.teamId)
                }
                pairingCounts[normalizedPair] = pairingCounts.getOrDefault(normalizedPair, 0) + 1
            }
        }
        
        val duplicates = pairingCounts.filter { it.value > 1 }
        if (duplicates.isEmpty()) {
            android.util.Log.e("TournamentAnalysis", "✅ NO DUPLICATE PAIRINGS FOUND - All teams played each opponent only once")
        } else {
            android.util.Log.e("TournamentAnalysis", "❌ DUPLICATE PAIRINGS DETECTED:")
            duplicates.forEach { (pair, count) ->
                android.util.Log.e("TournamentAnalysis", "❌ TeamID ${pair.first} vs TeamID ${pair.second}: $count times")
            }
        }
        
        // 5. Final standings
        android.util.Log.e("TournamentAnalysis", "")
        android.util.Log.e("TournamentAnalysis", "🏆 FINAL STANDINGS:")
        state.teams.sortedBy { it.currentPosition }.forEach { team ->
            android.util.Log.e("TournamentAnalysis", "🏆 Position ${team.currentPosition}: TeamID ${team.teamId} - ${team.points} points")
        }
        
        // 6. Secondary scoring system analysis
        android.util.Log.e("TournamentAnalysis", "")
        android.util.Log.e("TournamentAnalysis", "⚖️ SECONDARY SCORING SYSTEM ANALYSIS:")
        val samePointGroups = state.teams.groupBy { it.points }
        samePointGroups.forEach { (points, teams) ->
            if (teams.size > 1) {
                android.util.Log.e("TournamentAnalysis", "⚖️ Teams with $points points: ${teams.map { "TeamID ${it.teamId}" }.joinToString(", ")}")
            }
        }
        
        android.util.Log.e("TournamentAnalysis", "🔍 ===== ANALYSIS COMPLETE =====")
    }

    /**
     * 🧪 TEST LOG 4: HEAD-TO-HEAD TIEBREAKER TEST
     * İkincil puan durumu (head-to-head) hesaplamasını test eder
     */
    private fun logHeadToHeadTest(samePointTeams: List<EmreTeam>, headToHeadPoints: Map<Long, Double>, completedMatches: List<Match>) {
        android.util.Log.i("TEST_HEAD_TO_HEAD", "🧪 HEAD-TO-HEAD TIEBREAKER TEST:")
        android.util.Log.i("TEST_HEAD_TO_HEAD", "🧪 SAME POINT TEAMS COUNT: ${samePointTeams.size}")
        
        samePointTeams.forEach { team ->
            val h2hPoints = headToHeadPoints[team.id] ?: 0.0
            android.util.Log.i("TEST_HEAD_TO_HEAD", "🧪 TEAM ${team.currentPosition}: MainPoints=${team.points}, H2H_Points=${h2hPoints}, PreRoundPos=${team.preRoundPosition}")
        }
        
        // Head-to-head maçlarını say
        val samePointTeamIds = samePointTeams.map { it.id }.toSet()
        val h2hMatches = completedMatches.filter { match ->
            match.isCompleted &&
            match.songId1 in samePointTeamIds &&
            match.songId2 in samePointTeamIds &&
            match.songId1 != match.songId2
        }
        
        android.util.Log.i("TEST_HEAD_TO_HEAD", "🧪 HEAD-TO-HEAD MATCHES FOUND: ${h2hMatches.size}")
        h2hMatches.forEachIndexed { index, match ->
            val winner = when (match.winnerId) {
                match.songId1 -> "Team with Song ${match.songId1}"
                match.songId2 -> "Team with Song ${match.songId2}"
                null -> "DRAW"
                else -> "Unknown"
            }
            android.util.Log.i("TEST_HEAD_TO_HEAD", "🧪 H2H MATCH[$index]: Song ${match.songId1} vs Song ${match.songId2} → Winner: $winner")
        }
        
        // Test doğruluğu
        val hasValidH2H = samePointTeams.size > 1 && h2hMatches.isNotEmpty()
        android.util.Log.i("TEST_HEAD_TO_HEAD", "🧪 TEST RESULT: ${if (hasValidH2H) "✅ HEAD-TO-HEAD ACTIVE" else "⚠️ NO HEAD-TO-HEAD NEEDED"}")
        
        // Direct head-to-head testini ekle
        if (samePointTeams.size >= 2) {
            val directMatches = mutableListOf<String>()
            for (i in 0 until samePointTeams.size - 1) {
                for (j in i + 1 until samePointTeams.size) {
                    val team1 = samePointTeams[i]
                    val team2 = samePointTeams[j]
                    val directMatch = completedMatches.find { match ->
                        match.isCompleted &&
                        ((match.songId1 == team1.id && match.songId2 == team2.id) ||
                         (match.songId1 == team2.id && match.songId2 == team1.id))
                    }
                    
                    if (directMatch != null) {
                        val winner = when (directMatch.winnerId) {
                            team1.id -> "Team ${team1.currentPosition}"
                            team2.id -> "Team ${team2.currentPosition}" 
                            null -> "DRAW"
                            else -> "Unknown"
                        }
                        directMatches.add("Team ${team1.currentPosition} vs Team ${team2.currentPosition} → Winner: $winner")
                    }
                }
            }
            
            android.util.Log.i("TEST_HEAD_TO_HEAD", "🧪 DIRECT H2H MATCHES: ${directMatches.size}")
            directMatches.forEach { matchResult ->
                android.util.Log.i("TEST_HEAD_TO_HEAD", "🧪 DIRECT: $matchResult")
            }
        }
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
        val maxIterations = teams.size * 5 // Backtrack için daha yüksek limit
        
        while (availableTeams.isNotEmpty() && safetyCounter < maxIterations) {
            safetyCounter++
            
            if (safetyCounter % 50 == 0) {
                android.util.Log.w("EmreSystemCorrect", "⚠️ ITERATION WARNING: ${safetyCounter}/${maxIterations} iterations, ${availableTeams.size} teams remaining")
            }
            
            // En üst anlık sıralı takımı "eşleştirme arayan takım" yap
            val searchingTeam = availableTeams.removeFirstOrNull()
            if (searchingTeam == null) break
            
            engineState.currentSearchingTeam = searchingTeam
            android.util.Log.d("EmreSystemCorrect", "🔍 SEARCHING TEAM: ${searchingTeam.currentPosition} looking for partner (${availableTeams.size} teams remaining)")
            
            // Bu takım için eşleştirme arayan statüsü
            val pairingResult = findPartnerForSearchingTeam(
                searchingTeam, 
                availableTeams, 
                teams, // 🆕 All teams for backward search
                engineState, 
                matchHistory
            )
            
            when (pairingResult) {
                is PairingSearchResult.Success -> {
                    // Başarılı eşleştirme - her iki takımı da available listeden çıkar
                    availableTeams.remove(pairingResult.partner)
                    engineState.usedTeams.add(searchingTeam.teamId)
                    engineState.usedTeams.add(pairingResult.partner.teamId)
                    
                    android.util.Log.d("EmreSystemCorrect", "✅ MATCH CREATED: ${searchingTeam.currentPosition} vs ${pairingResult.partner.currentPosition}")
                    
                    // 🔴 CRITICAL FIX: DISPLACED TEAM'İ MAIN DÖNGÜYE GERİ AL
                    pairingResult.displacedTeam?.let { displacedTeam ->
                        if (!availableTeams.contains(displacedTeam)) {
                            availableTeams.add(0, displacedTeam) // Başa ekle (öncelikli)
                            android.util.Log.d("EmreSystemCorrect", "🔄 DISPLACED TEAM RESTORED: Team ${displacedTeam.currentPosition} added back to main loop (priority)")
                        } else {
                            android.util.Log.w("EmreSystemCorrect", "⚠️ DISPLACED TEAM ALREADY EXISTS: Team ${displacedTeam.currentPosition} already in available list")
                        }
                    }
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
                    } else {
                        // 🔴 CRITICAL FIX: STOLEN PARTNER'İ MAIN DÖNGÜYE GERİ AL
                        backtrackResult.stolenPartnerTeam?.let { stolenPartner ->
                            availableTeams.add(stolenPartner)
                            android.util.Log.d("EmreSystemCorrect", "🔄 STOLEN PARTNER RESTORED: Team ${stolenPartner.currentPosition} added back to main loop")
                        }
                    }
                }
                is PairingSearchResult.TournamentFinished -> {
                    // 🔥 FIX: Turnuva bitti - ama asymmetric check yap!
                    android.util.Log.e("EmreSystemCorrect", "🏁 EARLY TOURNAMENT END: Team ${searchingTeam.currentPosition} cannot pair with anyone")
                    android.util.Log.e("EmreSystemCorrect", "🔥 CHECKING EXISTING MATCHES: ${engineState.candidateMatches.size} matches before early exit")
                    
                    // Mevcut candidate matches ile asymmetric check yap
                    return performAsymmetricPointCheck(engineState.candidateMatches, engineState.byeTeam, currentRound, teams.size)
                }
                is PairingSearchResult.Bye -> {
                    // Takım bye geçer
                    android.util.Log.d("EmreSystemCorrect", "🆓 BYE TEAM: Team ${searchingTeam.currentPosition} will get bye")
                    engineState.byeTeam = searchingTeam
                    availableTeams.remove(searchingTeam)
                }
            }
        }
        
        // 3. INFINITE LOOP PROTECTION - KALAN TAKIMLAR
        if (availableTeams.isNotEmpty()) {
            android.util.Log.e("EmreSystemCorrect", "⚠️ INFINITE LOOP DETECTED: ${availableTeams.size} teams stuck after ${safetyCounter} iterations")
            // Kalan takımları unpaired teams'e ekle
            engineState.unpairedTeams.addAll(availableTeams)
            availableTeams.clear()
        }
        
        // 4. EŞLEŞİLEMEDEN KALANLAR GRUBU İŞLEME
        if (engineState.unpairedTeams.isNotEmpty()) {
            android.util.Log.w("EmreSystemCorrect", "⚠️ PROCESSING UNPAIRED TEAMS: ${engineState.unpairedTeams.size} teams need resolution")
            
            // 🔴 CRITICAL FIX: DUPLICATE PREVENTION IN EMERGENCY PAIRING
            val processedTeams = mutableSetOf<Long>()
            val emergencyPairs = mutableListOf<Pair<EmreTeam, EmreTeam>>()
            
            // İlk olarak tüm unpaired teams'i duplicate olmayan çiftler halinde eşleştir
            for (i in engineState.unpairedTeams.indices) {
                val team1 = engineState.unpairedTeams[i]
                if (team1.teamId in processedTeams) continue
                
                // Bu takım için duplicate olmayan partner ara
                var foundPartner: EmreTeam? = null
                for (j in i + 1 until engineState.unpairedTeams.size) {
                    val team2 = engineState.unpairedTeams[j]
                    if (team2.teamId in processedTeams) continue
                    
                    // DUPLICATE KONTROL
                    val duplicateCheck = hasTeamsPlayedBefore(team1.teamId, team2.teamId, matchHistory)
                    logDuplicateTest(team1, team2, matchHistory, duplicateCheck, "EMERGENCY_PAIRING")
                    
                    if (!duplicateCheck) {
                        foundPartner = team2
                        break
                    }
                }
                
                if (foundPartner != null) {
                    emergencyPairs.add(Pair(team1, foundPartner))
                    processedTeams.add(team1.teamId)
                    processedTeams.add(foundPartner.teamId)
                    android.util.Log.w("EmreSystemCorrect", "🆓 EMERGENCY PAIR (DUPLICATE-SAFE): ${team1.currentPosition} vs ${foundPartner.currentPosition}")
                } else {
                    android.util.Log.e("EmreSystemCorrect", "❌ EMERGENCY FAIL: Team ${team1.currentPosition} cannot pair with anyone (all duplicates)")
                }
            }
            
            // Duplicate-safe eşleştirmeleri candidateMatches'e ekle
            for (pair in emergencyPairs) {
                engineState.candidateMatches.add(CandidateMatch(
                    team1 = pair.first,
                    team2 = pair.second, 
                    isAsymmetricPoints = pair.first.points != pair.second.points
                ))
            }
            
            // İşlenmeyen takımları bye grubuna ekle
            engineState.unpairedTeams.clear()
            for (team in engineState.unpairedTeams.toList()) {
                if (team.teamId !in processedTeams) {
                    android.util.Log.w("EmreSystemCorrect", "🆓 EMERGENCY BYE: Team ${team.currentPosition} (no duplicate-safe partner found)")
                    engineState.byeTeam = team // Son çare olarak bye
                }
            }
        }
        
        // 5. FINAL VALIDATION
        val totalTeamsInPairs = engineState.candidateMatches.size * 2 + (if (engineState.byeTeam != null) 1 else 0)
        if (totalTeamsInPairs != teams.size) {
            android.util.Log.e("EmreSystemCorrect", "❌ TEAM COUNT MISMATCH: Expected ${teams.size}, got $totalTeamsInPairs")
            android.util.Log.e("EmreSystemCorrect", "🔥 MISMATCH DETAILS: ${engineState.candidateMatches.size} matches (${engineState.candidateMatches.size * 2} teams) + ${if (engineState.byeTeam != null) 1 else 0} bye team")
            
            // 🔥 FIX: Team count mismatch olsa bile asymmetric check yap!
            return performAsymmetricPointCheck(engineState.candidateMatches, engineState.byeTeam, currentRound, teams.size)
        }
        
        android.util.Log.d("EmreSystemCorrect", "✅ PAIRING ENGINE COMPLETED: ${engineState.candidateMatches.size} matches, ${if (engineState.byeTeam != null) "1 bye" else "no bye"}")
        
        // 🧪 TEST LOG 2: MATCH COUNT TEST
        logMatchCountTest(teams, engineState.candidateMatches, engineState.byeTeam, currentRound)
        
        // 6. EŞ PUANLI EŞLEŞME KONTROLÜ VE TUR ONAY
        return performAsymmetricPointCheck(engineState.candidateMatches, engineState.byeTeam, currentRound, teams.size)
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
        allTeams: List<EmreTeam>, // 🆕 For backward search
        engineState: PairingEngineState,
        matchHistory: Set<Pair<Long, Long>>
    ): PairingSearchResult {
        
        android.util.Log.d("EmreSystemCorrect", "🔍 PARTNER SEARCH: Team ${searchingTeam.currentPosition} searching...")
        
        // 1. ÖNCE SONRAKI TAKIMLARI KONTROL ET
        for (candidate in availableTeams) {
            if (candidate.currentPosition <= searchingTeam.currentPosition) continue // Sadece sonrakiler
            if (candidate.id in engineState.usedTeams) continue // Zaten kullanılmış
            
            // 🔴 KRİTİK: Daha önce oynamış mı kontrol et
            val duplicateCheck = hasTeamsPlayedBefore(searchingTeam.teamId, candidate.teamId, matchHistory)
            logDuplicateTest(searchingTeam, candidate, matchHistory, duplicateCheck, "FORWARD_SEARCH")
            
            if (duplicateCheck) {
                android.util.Log.d("EmreSystemCorrect", "⏭️ FORWARD SKIP: ${candidate.currentPosition} (played before with TeamID ${searchingTeam.teamId})")
                continue
            }
            
            // 🔴 KRİTİK: Candidate matches'te duplicate kontrolü
            val isDuplicateInCandidates = engineState.candidateMatches.any { existingMatch ->
                (existingMatch.team1.teamId == searchingTeam.teamId && existingMatch.team2.teamId == candidate.teamId) ||
                (existingMatch.team1.teamId == candidate.teamId && existingMatch.team2.teamId == searchingTeam.teamId)
            }
            if (isDuplicateInCandidates) {
                android.util.Log.w("EmreSystemCorrect", "🚫 FORWARD SKIP: ${candidate.currentPosition} (already in candidate matches)")
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
        
        // 2. GERİYE DÖN - ÖNCEKİ TAKIMLARI KONTROL ET (TÜM TAKIMLARDAN)
        android.util.Log.d("EmreSystemCorrect", "🔍 BACKWARD SEARCH: Team ${searchingTeam.currentPosition} checking ${allTeams.size} total teams backwards")
        for (candidate in allTeams.reversed()) {
            android.util.Log.d("EmreSystemCorrect", "🔍 BACKWARD CANDIDATE: Team ${candidate.currentPosition} (TeamID: ${candidate.teamId})")
            
            // Sadece kendinden önceki takımları kontrol et
            if (candidate.currentPosition >= searchingTeam.currentPosition) {
                android.util.Log.d("EmreSystemCorrect", "⏭️ SKIP: ${candidate.currentPosition} (not backwards: ${candidate.currentPosition} >= ${searchingTeam.currentPosition})")
                continue
            }
            
            // 🔴 KRİTİK: Daha önce oynamış mı kontrol et
            val duplicateCheck = hasTeamsPlayedBefore(searchingTeam.teamId, candidate.teamId, matchHistory)
            logDuplicateTest(searchingTeam, candidate, matchHistory, duplicateCheck, "BACKWARD_SEARCH")
            
            if (duplicateCheck) {
                android.util.Log.d("EmreSystemCorrect", "⏭️ BACKWARD SKIP: ${candidate.currentPosition} (played before with TeamID ${searchingTeam.teamId})")
                continue
            }
            
            // 🔴 KRİTİK: Candidate matches'te duplicate kontrolü
            val isDuplicateInCandidates = engineState.candidateMatches.any { existingMatch ->
                (existingMatch.team1.teamId == searchingTeam.teamId && existingMatch.team2.teamId == candidate.teamId) ||
                (existingMatch.team1.teamId == candidate.teamId && existingMatch.team2.teamId == searchingTeam.teamId)
            }
            if (isDuplicateInCandidates) {
                android.util.Log.w("EmreSystemCorrect", "🚫 BACKWARD SKIP: ${candidate.currentPosition} (already in candidate matches)")
                continue
            }
            
            // Bu takım zaten kullanılmış mı? (Eşleşmiş takımlar için ADVANCED BACKTRACK)
            if (candidate.teamId in engineState.usedTeams) {
                // 🔥 GELİŞTİRİLMİŞ BACKTRACK ALGORİTMASI (METİNDEKİ 7. MADDE)
                android.util.Log.w("EmreSystemCorrect", "🔄 ADVANCED BACKTRACK: ${searchingTeam.currentPosition} wants ${candidate.currentPosition}")
                val backtrackResult = performAdvancedBacktrackAlgorithm(
                    searchingTeam = searchingTeam,
                    targetTeam = candidate,
                    engineState = engineState,
                    matchHistory = matchHistory,
                    allTeams = allTeams
                )
                
                return when (backtrackResult.success) {
                    true -> PairingSearchResult.Success(candidate, backtrackResult.displacedTeam)
                    false -> PairingSearchResult.Bye // Eşleştirme başarısız, bye geçer
                }
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
        engineState.usedTeams.remove(existingMatch.team1.teamId)
        engineState.usedTeams.remove(existingMatch.team2.teamId)
        
        // Available teams'e geri ekle - ÖNCELİKLİ OLARAK BAŞA EKLE
        availableTeams.add(0, stolenPartnerTeam) // Displaced team önceliği
        android.util.Log.w("EmreSystemCorrect", "🔄 DISPLACED PRIORITY: Team ${stolenPartnerTeam.currentPosition} moved to front of queue")
        
        android.util.Log.w("EmreSystemCorrect", "💥 MATCH BROKEN: ${existingMatch.team1.currentPosition} vs ${existingMatch.team2.currentPosition}")
        android.util.Log.w("EmreSystemCorrect", "👤 STOLEN PARTNER: Team ${stolenPartnerTeam.currentPosition}")
        
        // 3. YENİ EŞLEŞMEYİ OLUŞTUR (searchingTeam + targetTeam) - DUPLICATE KONTROL İLE
        
        // 🔴 CRITICAL CHECK: DUPLICATE PREVENTION IN BACKTRACK
        val backtrackDuplicateCheck = hasTeamsPlayedBefore(searchingTeam.teamId, targetTeam.teamId, matchHistory)
        logDuplicateTest(searchingTeam, targetTeam, matchHistory, backtrackDuplicateCheck, "BACKTRACK_VALIDATION")
        
        if (backtrackDuplicateCheck) {
            android.util.Log.e("EmreSystemCorrect", "❌ BACKTRACK DUPLICATE: ${searchingTeam.currentPosition} and ${targetTeam.currentPosition} have played before!")
            android.util.Log.e("EmreSystemCorrect", "❌ CANCELLING BACKTRACK: Reverting broken match")
            
            // Broken match'i geri yükle
            engineState.candidateMatches.add(existingMatch)
            engineState.usedTeams.add(existingMatch.team1.teamId)
            engineState.usedTeams.add(existingMatch.team2.teamId)
            availableTeams.remove(stolenPartnerTeam) // Displaced team'i geri çıkar
            
            return BacktrackResult(false, reason = "Backtrack would create duplicate pairing")
        }
        
        val newMatch = CandidateMatch(
            team1 = searchingTeam,
            team2 = targetTeam,
            isAsymmetricPoints = searchingTeam.points != targetTeam.points
        )
        engineState.candidateMatches.add(newMatch)
        engineState.usedTeams.add(searchingTeam.teamId)
        engineState.usedTeams.add(targetTeam.teamId)
        
        // Target team'i available'dan çıkar (zaten available değildi ama güvenlik için)
        availableTeams.remove(targetTeam)
        
        android.util.Log.w("EmreSystemCorrect", "✅ NEW MATCH CREATED (DUPLICATE-SAFE): ${searchingTeam.currentPosition} vs ${targetTeam.currentPosition}")
        
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
        currentRound: Int,
        totalTeams: Int
    ): EmrePairingResult {
        
        android.util.Log.d("EmreSystemCorrect", "🎯 ASYMMETRIC POINT CHECK: Round $currentRound, ${candidateMatches.size} matches")
        
        // 🔴 KESIN ONAY KONTROLÜ - ADAY EŞLEŞTİRMELER BEKLENEN SAYIYA ULAŞMALI
        val expectedMatches = if (totalTeams % 2 == 0) {
            // Çift sayıda takım: Tüm takımlar eşleşir, bye yok
            totalTeams / 2
        } else {
            // Tek sayıda takım: 1 bye, geri kalan eşleşir  
            (totalTeams - 1) / 2
        }
        
        android.util.Log.e("EmreSystemCorrect", "🔍 KESIN ONAY KONTROLÜ:")
        android.util.Log.e("EmreSystemCorrect", "📊 Toplam Takım Sayısı: $totalTeams")
        android.util.Log.e("EmreSystemCorrect", "📊 Beklenen Eşleştirme: $expectedMatches")
        android.util.Log.e("EmreSystemCorrect", "📊 Aday Eşleştirme: ${candidateMatches.size}")
        android.util.Log.e("EmreSystemCorrect", "📊 Bye Takım: ${if (byeTeam != null) "1 takım (${byeTeam.currentPosition})" else "Yok"}")
        
        if (candidateMatches.size < expectedMatches) {
            android.util.Log.e("EmreSystemCorrect", "❌ KESIN ONAY REDDEDILDI: Eksik eşleştirme!")
            android.util.Log.e("EmreSystemCorrect", "❌ ${candidateMatches.size} eşleştirme var, $expectedMatches olmalı")
            android.util.Log.e("EmreSystemCorrect", "❌ TOURNAMENT BLOCKED: Insufficient pairings")
            
            // Eksik eşleştirmelerle turnuva devam etmesin
            return EmrePairingResult(
                matches = emptyList(),
                canContinue = false,
                byeTeam = byeTeam
            )
        } else {
            android.util.Log.e("EmreSystemCorrect", "✅ KESIN ONAY: ${candidateMatches.size} eşleştirme beklenen sayıda!")
        }
        
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
        
        // 🧪 TEST LOG 3: TOURNAMENT END ALGORITHM TEST
        logTournamentEndTest(candidateMatches, hasSamePointMatch, currentRound)
        
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
     * 🔥 GELİŞTİRİLMİŞ BACKTRACK ALGORİTMASI - METİNDEKİ 7. MADDE
     * 
     * KULLANICININ ALGORİTMASI (29-25-15 Örneği):
     * 1. 29 nolu takım 25 ile eşleşebilir → 25'in mevcut eşleşmesi BOZULUR (25-15 eşleştirmesi)
     * 2. 15 nolu takıma "partneri çalınan takım" denir
     * 3. KONTROLLAR:
     *    - 29 ile 15 eşleşebilir mi? 
     *    - 15 ile "eşleşilemeden kalanlar grubu" eşleşebilir mi?
     *    - 25 ile "eşleşilemeden kalanlar grubu" eşleşebilir mi?
     * 4. En uygun çözümü uygula
     */
    data class AdvancedBacktrackResult(
        val success: Boolean,
        val reason: String = "",
        val displacedTeam: EmreTeam? = null
    )
    
    private fun performAdvancedBacktrackAlgorithm(
        searchingTeam: EmreTeam,        // 29 nolu takım
        targetTeam: EmreTeam,           // 25 nolu takım (eşleşmesi bozulacak)
        engineState: PairingEngineState,
        matchHistory: Set<Pair<Long, Long>>,
        allTeams: List<EmreTeam>
    ): AdvancedBacktrackResult {
        
        android.util.Log.d("EmreSystemCorrect", "🔥 BAŞLIYOR: Advanced Backtrack - ${searchingTeam.currentPosition} wants ${targetTeam.currentPosition}")
        
        // 1. HEDEF TAKIMIN MEVCUT EŞLEŞMESİNİ BUL VE BOZ
        val existingMatch = engineState.candidateMatches.find { match ->
            match.team1.teamId == targetTeam.teamId || match.team2.teamId == targetTeam.teamId
        }
        
        if (existingMatch == null) {
            android.util.Log.e("EmreSystemCorrect", "❌ BACKTRACK FAIL: Target team ${targetTeam.currentPosition} has no existing match!")
            return AdvancedBacktrackResult(false, "Target team has no existing match")
        }
        
        // Partneri çalınan takımı tespit et (15 nolu takım)
        val displacedTeam = if (existingMatch.team1.teamId == targetTeam.teamId) {
            existingMatch.team2
        } else {
            existingMatch.team1
        }
        
        android.util.Log.d("EmreSystemCorrect", "💔 BREAKING MATCH: ${targetTeam.currentPosition} vs ${displacedTeam.currentPosition}")
        android.util.Log.d("EmreSystemCorrect", "😢 DISPLACED TEAM: ${displacedTeam.currentPosition} (partneri çalınan takım)")
        
        // 2. DUPLICATE KONTROLÜ - 29 ile 25 eşleşebilir mi?
        val canPairWith25 = !hasTeamsPlayedBefore(searchingTeam.teamId, targetTeam.teamId, matchHistory)
        if (!canPairWith25) {
            android.util.Log.e("EmreSystemCorrect", "🚫 BACKTRACK FAIL: ${searchingTeam.currentPosition} already played with ${targetTeam.currentPosition}")
            return AdvancedBacktrackResult(false, "Teams have already played together")
        }
        
        // 3. KARMAŞIK KONTROL SİSTEMİ (METİNDEKİ 7. MADDE DETAYLARI)
        
        // 3a. 29 ile 15 eşleşebilir mi kontrolü
        val canPairWith15 = !hasTeamsPlayedBefore(searchingTeam.teamId, displacedTeam.teamId, matchHistory)
        android.util.Log.d("EmreSystemCorrect", "🔍 29-15 CHECK: Can ${searchingTeam.currentPosition} pair with ${displacedTeam.currentPosition}? $canPairWith15")
        
        // 3b. "Eşleşilemeden kalanlar grubu"nu tespit et
        val unpairedTeams = findUnpairedTeamsGroup(allTeams, engineState)
        android.util.Log.d("EmreSystemCorrect", "👥 UNPAIRED GROUP: ${unpairedTeams.map { it.currentPosition }}")
        
        // 3c. 15 ile unpaired teams eşleşebilir mi?
        val displaced15CanPairWithUnpaired = unpairedTeams.any { unpairedTeam ->
            !hasTeamsPlayedBefore(displacedTeam.teamId, unpairedTeam.teamId, matchHistory)
        }
        
        // 3d. 25 ile unpaired teams eşleşebilir mi?
        val target25CanPairWithUnpaired = unpairedTeams.any { unpairedTeam ->
            !hasTeamsPlayedBefore(targetTeam.teamId, unpairedTeam.teamId, matchHistory)
        }
        
        android.util.Log.d("EmreSystemCorrect", "🔍 15-UNPAIRED CHECK: Can ${displacedTeam.currentPosition} pair with unpaired? $displaced15CanPairWithUnpaired")
        android.util.Log.d("EmreSystemCorrect", "🔍 25-UNPAIRED CHECK: Can ${targetTeam.currentPosition} pair with unpaired? $target25CanPairWithUnpaired")
        
        // 4. METİNDEKİ KOŞULLU KARAR AĞACI
        
        if (!canPairWith15) {
            // 29 ile 15 eşleşemez → 29-25 eşleştir, 15 ayrı çözüm bul
            android.util.Log.d("EmreSystemCorrect", "✅ SOLUTION A: 29-25 pair (15 can't pair with 29)")
            return executeBacktrackSolution(searchingTeam, targetTeam, displacedTeam, engineState, "29-25, 15 displaced")
        }
        
        if (canPairWith15 && displaced15CanPairWithUnpaired) {
            // 29 ile 15 eşleşebilir VE 15 unpaired ile eşleşebilir
            // → 29-25 eşleştir, 15'i unpaired ile eşleştir
            android.util.Log.d("EmreSystemCorrect", "✅ SOLUTION B: 29-25 pair, 15 pairs with unpaired")
            val unpairedPartner = unpairedTeams.find { !hasTeamsPlayedBefore(displacedTeam.teamId, it.teamId, matchHistory) }
            if (unpairedPartner != null) {
                return executeBacktrackSolutionWithUnpaired(searchingTeam, targetTeam, displacedTeam, unpairedPartner, engineState, "29-25, 15-unpaired")
            }
        }
        
        if (canPairWith15 && !displaced15CanPairWithUnpaired && target25CanPairWithUnpaired) {
            // 15 unpaired ile eşleşemez ama 25 eşleşebilir
            // → 25'i unpaired ile eşleştir, 29-15 eşleştir
            android.util.Log.d("EmreSystemCorrect", "✅ SOLUTION C: 25-unpaired pair, 29-15 pair")
            val unpairedPartner = unpairedTeams.find { !hasTeamsPlayedBefore(targetTeam.teamId, it.teamId, matchHistory) }
            if (unpairedPartner != null) {
                return executeBacktrackSolutionAlternative(searchingTeam, targetTeam, displacedTeam, unpairedPartner, engineState, "25-unpaired, 29-15")
            }
        }
        
        if (!displaced15CanPairWithUnpaired) {
            // 15 hiçbir unpaired ile eşleşemez → 29-25 eşleştir, 15 displaced
            android.util.Log.d("EmreSystemCorrect", "✅ SOLUTION D: 29-25 pair, 15 fully displaced")
            return executeBacktrackSolution(searchingTeam, targetTeam, displacedTeam, engineState, "29-25, 15 displaced")
        }
        
        // Fallback - basit çözüm
        android.util.Log.w("EmreSystemCorrect", "⚠️ FALLBACK SOLUTION: Simple 29-25 pairing")
        return executeBacktrackSolution(searchingTeam, targetTeam, displacedTeam, engineState, "fallback 29-25")
    }
    
    private fun findUnpairedTeamsGroup(allTeams: List<EmreTeam>, engineState: PairingEngineState): List<EmreTeam> {
        return allTeams.filter { team ->
            team.teamId !in engineState.usedTeams &&
            engineState.candidateMatches.none { match ->
                match.team1.teamId == team.teamId || match.team2.teamId == team.teamId
            }
        }
    }
    
    private fun executeBacktrackSolution(
        searchingTeam: EmreTeam,
        targetTeam: EmreTeam, 
        displacedTeam: EmreTeam,
        engineState: PairingEngineState,
        solution: String
    ): AdvancedBacktrackResult {
        
        android.util.Log.d("EmreSystemCorrect", "⚡ EXECUTING: $solution")
        
        // Eski eşleştirmeyi kaldır
        engineState.candidateMatches.removeAll { match ->
            match.team1.teamId == targetTeam.teamId || match.team2.teamId == targetTeam.teamId
        }
        
        // Used teams'den displaced team'i çıkar
        engineState.usedTeams.remove(displacedTeam.teamId)
        
        // Yeni eşleştirmeyi ekle (29-25)
        engineState.candidateMatches.add(CandidateMatch(
            team1 = searchingTeam,
            team2 = targetTeam,
            isAsymmetricPoints = searchingTeam.points != targetTeam.points
        ))
        
        // Used teams'e ekle
        engineState.usedTeams.add(searchingTeam.teamId)
        engineState.usedTeams.add(targetTeam.teamId)
        
        android.util.Log.d("EmreSystemCorrect", "✅ NEW MATCH CREATED: ${searchingTeam.currentPosition} vs ${targetTeam.currentPosition}")
        android.util.Log.d("EmreSystemCorrect", "😢 DISPLACED FOR LATER: ${displacedTeam.currentPosition}")
        
        return AdvancedBacktrackResult(true, solution, displacedTeam)
    }
    
    private fun executeBacktrackSolutionWithUnpaired(
        searchingTeam: EmreTeam,
        targetTeam: EmreTeam,
        displacedTeam: EmreTeam,
        unpairedPartner: EmreTeam,
        engineState: PairingEngineState,
        solution: String
    ): AdvancedBacktrackResult {
        
        android.util.Log.d("EmreSystemCorrect", "⚡ EXECUTING WITH UNPAIRED: $solution")
        
        // Eski eşleştirmeyi kaldır  
        engineState.candidateMatches.removeAll { match ->
            match.team1.teamId == targetTeam.teamId || match.team2.teamId == targetTeam.teamId
        }
        
        // Used teams'den displaced team'i çıkar
        engineState.usedTeams.remove(displacedTeam.teamId)
        
        // Yeni eşleştirme 1: 29-25
        engineState.candidateMatches.add(CandidateMatch(
            team1 = searchingTeam,
            team2 = targetTeam,
            isAsymmetricPoints = searchingTeam.points != targetTeam.points
        ))
        
        // Yeni eşleştirme 2: 15-unpaired
        engineState.candidateMatches.add(CandidateMatch(
            team1 = displacedTeam,
            team2 = unpairedPartner,
            isAsymmetricPoints = displacedTeam.points != unpairedPartner.points
        ))
        
        // Used teams güncellemeleri
        engineState.usedTeams.add(searchingTeam.teamId)
        engineState.usedTeams.add(targetTeam.teamId)
        engineState.usedTeams.add(displacedTeam.teamId)
        engineState.usedTeams.add(unpairedPartner.teamId)
        
        android.util.Log.d("EmreSystemCorrect", "✅ DOUBLE MATCH CREATED: ${searchingTeam.currentPosition}-${targetTeam.currentPosition} & ${displacedTeam.currentPosition}-${unpairedPartner.currentPosition}")
        
        return AdvancedBacktrackResult(true, solution)
    }
    
    private fun executeBacktrackSolutionAlternative(
        searchingTeam: EmreTeam,
        targetTeam: EmreTeam,
        displacedTeam: EmreTeam,
        unpairedPartner: EmreTeam,
        engineState: PairingEngineState,
        solution: String
    ): AdvancedBacktrackResult {
        
        android.util.Log.d("EmreSystemCorrect", "⚡ EXECUTING ALTERNATIVE: $solution")
        
        // Eski eşleştirmeyi kaldır
        engineState.candidateMatches.removeAll { match ->
            match.team1.teamId == targetTeam.teamId || match.team2.teamId == targetTeam.teamId
        }
        
        // Used teams'den target team'i çıkar
        engineState.usedTeams.remove(targetTeam.teamId)
        engineState.usedTeams.remove(displacedTeam.teamId)
        
        // Alternatif eşleştirme 1: 25-unpaired
        engineState.candidateMatches.add(CandidateMatch(
            team1 = targetTeam,
            team2 = unpairedPartner,
            isAsymmetricPoints = targetTeam.points != unpairedPartner.points
        ))
        
        // Alternatif eşleştirme 2: 29-15
        engineState.candidateMatches.add(CandidateMatch(
            team1 = searchingTeam,
            team2 = displacedTeam,
            isAsymmetricPoints = searchingTeam.points != displacedTeam.points
        ))
        
        // Used teams güncellemeleri
        engineState.usedTeams.add(searchingTeam.teamId)
        engineState.usedTeams.add(targetTeam.teamId)
        engineState.usedTeams.add(displacedTeam.teamId)
        engineState.usedTeams.add(unpairedPartner.teamId)
        
        android.util.Log.d("EmreSystemCorrect", "✅ ALTERNATIVE DOUBLE MATCH: ${targetTeam.currentPosition}-${unpairedPartner.currentPosition} & ${searchingTeam.currentPosition}-${displacedTeam.currentPosition}")
        
        return AdvancedBacktrackResult(true, solution)
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
                    usedTeams.remove(match.team1.teamId)
                    usedTeams.remove(match.team2.teamId)
                    
                    // ✅ KRİTİK DÜZELTME: BOZULAN TAKIMI DISPLACED QUEUE'YA EKLE
                    val displacedTeam = if (match.team1.id == potentialPartner.id) match.team2 else match.team1
                    displacedTeams.add(displacedTeam.teamId)
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
                usedTeams.add(searchingTeam.teamId)
                usedTeams.add(partner.teamId)
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
    
    /*
     * DEPRECATED FUNCTION REMOVED - Compile error fix
     * Original function: findPartnerForTeam()
     * Replaced by: Hybrid pairing system functions
     */
    
    // Eski PartnerSearchResult silindi - Yeni hibrit sistem kullanıyor
    
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
            usedTeams.remove(match.team1.teamId)
            usedTeams.remove(match.team2.teamId)
            
            // Yeni eşleşmeyi ekle
            candidateMatches.add(
                CandidateMatch(
                    team1 = searchingTeam,
                    team2 = conflictTeam,
                    isAsymmetricPoints = searchingTeam.points != conflictTeam.points
                )
            )
            usedTeams.add(searchingTeam.teamId)
            usedTeams.add(conflictTeam.teamId)
        }
    }
    
    /**
     * İki takımın daha önce oynayıp oynamadığını kontrol et
     */
    private fun hasTeamsPlayedBefore(team1Id: Long, team2Id: Long, matchHistory: Set<Pair<Long, Long>>): Boolean {
        // 🔥 CRITICAL FIX: Normalized pair lookup (küçük ID önce)
        val normalizedPair = if (team1Id < team2Id) {
            Pair(team1Id, team2Id)
        } else {
            Pair(team2Id, team1Id)
        }
        val hasPlayed = normalizedPair in matchHistory
        
        // 🔍 DETAILED DEBUG LOG - Match history kontrolü
        android.util.Log.d("EmreSystemCorrect", "🔍 CHECKING MATCH HISTORY: TeamID $team1Id vs TeamID $team2Id")
        android.util.Log.d("EmreSystemCorrect", "🔍 Match History size: ${matchHistory.size}")
        android.util.Log.d("EmreSystemCorrect", "🔍 Looking for normalized pair: $normalizedPair")
        
        // 🆕 DEBUG: Match history sample göster (ilk 3 pair)
        if (matchHistory.size > 0) {
            val sample = matchHistory.take(3)
            android.util.Log.d("EmreSystemCorrect", "🔍 MATCH HISTORY SAMPLE: $sample")
        }
        
        if (hasPlayed) {
            android.util.Log.w("EmreSystemCorrect", "🚫 DUPLICATE DETECTED: TeamID $team1Id and $team2Id have played before!")
            android.util.Log.w("EmreSystemCorrect", "🚫 Found in history: $normalizedPair in history = ${normalizedPair in matchHistory}")
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
        
        // 🆕 Track processed match IDs to prevent duplicate processing
        val processedMatchIds = mutableSetOf<Long>()
        
        completedMatches.forEach { match ->
            // 🔴 Skip if match already processed
            if (match.id in processedMatchIds) {
                android.util.Log.w("EmreSystemCorrect", "⚠️ SKIPPING DUPLICATE MATCH: Match ID ${match.id} already processed")
                return@forEach
            }
            processedMatchIds.add(match.id)
            // 🆕 CRITICAL FIX: Convert song IDs to stable team IDs for match history
            val teamId1 = songToTeamMap[match.songId1]
            val teamId2 = songToTeamMap[match.songId2]
            
            if (teamId1 != null && teamId2 != null) {
                // 🔥 CRITICAL FIX: Sadece tek yönlü kaydet (küçük ID önce)
                val normalizedPair = if (teamId1 < teamId2) {
                    Pair(teamId1, teamId2)
                } else {
                    Pair(teamId2, teamId1)
                }
                
                // 🔴 KRİTİK FIX: DUPLICATE KONTROLÜ - SADECE DAHA ÖNCE EŞLEŞMİŞ DEĞİLLERLE EŞLEŞİR
                if (!newMatchHistory.contains(normalizedPair)) {
                    newMatchHistory.add(normalizedPair)
                    android.util.Log.d("EmreSystemCorrect", "📝 ADDED TO HISTORY: TeamID $teamId1 vs TeamID $teamId2 (Match ID: ${match.id}, SongIDs: ${match.songId1} vs ${match.songId2})")
                } else {
                    android.util.Log.e("EmreSystemCorrect", "🚫 BLOCKED DUPLICATE: TeamID $teamId1 vs TeamID $teamId2 already in match history! (Match ID: ${match.id})")
                }
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
        
        // 🎯 DOĞRU İKİNCİL PUAN SİSTEMİ: Sadece comprehensive hesaplama kullan
        
        // 🆕 GELIŞMIŞ İKINCIL PUAN HESABI: Aynı puanlı takımların tüm turnuva boyunca birbirleriyle oynadığı maçlar
        android.util.Log.d("EmreSystemCorrect", "🎯 COMPREHENSIVE H2H: Calculating secondary points for ${samePointTeams.size} teams")
        
        // Her aynı puanlı takım için, diğer aynı puanlı takımlara karşı oynadığı tüm maçlardaki performansını hesapla
        samePointTeams.forEach { teamA ->
            samePointTeams.forEach { teamB ->
                if (teamA.id != teamB.id) {
                    // teamA ile teamB arasındaki tüm turnuva maçlarına bak
                    val matchesBetween = completedMatches.filter { match ->
                        match.isCompleted && 
                        ((match.songId1 == teamA.id && match.songId2 == teamB.id) ||
                         (match.songId1 == teamB.id && match.songId2 == teamA.id)) &&
                        match.songId1 != match.songId2 // Bye değil
                    }
                    
                    matchesBetween.forEach { match ->
                        when (match.winnerId) {
                            teamA.id -> {
                                headToHeadPoints[teamA.id] = headToHeadPoints[teamA.id]!! + 1.0
                                android.util.Log.d("EmreSystemCorrect", "⚔️ H2H COMPREHENSIVE: Team ${teamA.id} beats Team ${teamB.id} → +1.0")
                            }
                            teamB.id -> {
                                // teamB kazandı ama teamA'nın puanını etkilemez (0 puan)
                                android.util.Log.d("EmreSystemCorrect", "⚔️ H2H COMPREHENSIVE: Team ${teamB.id} beats Team ${teamA.id} → Team ${teamA.id} gets 0")
                            }
                            null -> {
                                headToHeadPoints[teamA.id] = headToHeadPoints[teamA.id]!! + 0.5
                                android.util.Log.d("EmreSystemCorrect", "⚔️ H2H COMPREHENSIVE: Team ${teamA.id} draws Team ${teamB.id} → +0.5")
                            }
                        }
                    }
                }
            }
        }
        
        // Head-to-head puanlarını logla
        headToHeadPoints.forEach { (teamId, points) ->
            val team = samePointTeams.find { it.id == teamId }
            android.util.Log.d("EmreSystemCorrect", "📈 H2H POINTS: Team ${team?.currentPosition} → ${points}")
        }
        
        // 🧪 TEST LOG 4: HEAD-TO-HEAD TIEBREAKER TEST  
        logHeadToHeadTest(samePointTeams, headToHeadPoints, completedMatches)
        
        // 🆕 COMPLETE TIEBREAKER: Head-to-head puanları, sonra direct H2H, sonra tur öncesi sıralama
        val sortedTeams = samePointTeams.sortedWith(
            compareByDescending<EmreTeam> { headToHeadPoints[it.id] ?: 0.0 } // 1. Head-to-head puan (yüksek önce)
                .thenComparing { team1, team2 ->
                    // 2. Direct head-to-head: Eğer H2H puanları eşitse, direkt maçta kim yendi?
                    val h2h1 = headToHeadPoints[team1.id] ?: 0.0
                    val h2h2 = headToHeadPoints[team2.id] ?: 0.0
                    
                    if (h2h1 == h2h2) {
                        // H2H puanları eşit - direkt maça bakılır
                        val directMatch = completedMatches.find { match ->
                            match.isCompleted &&
                            ((match.songId1 == team1.id && match.songId2 == team2.id) ||
                             (match.songId1 == team2.id && match.songId2 == team1.id)) &&
                            match.winnerId != null // Beraberlik değil
                        }
                        
                        if (directMatch != null) {
                            when (directMatch.winnerId) {
                                team1.id -> {
                                    android.util.Log.d("EmreSystemCorrect", "🥊 DIRECT H2H: Team ${team1.currentPosition} beats Team ${team2.currentPosition}")
                                    -1 // team1 kazandı, team1 üstte
                                }
                                team2.id -> {
                                    android.util.Log.d("EmreSystemCorrect", "🥊 DIRECT H2H: Team ${team2.currentPosition} beats Team ${team1.currentPosition}")
                                    1 // team2 kazandı, team2 üstte
                                }
                                else -> 0 // Beraberlik veya beklenmeyen durum
                            }
                        } else {
                            0 // Direkt maç yok, bir sonraki tiebreaker'a geç
                        }
                    } else {
                        0 // H2H puanları farklı, zaten ilk kriter çözüyor
                    }
                }
                .thenBy { team -> 
                    // 3. EN AZ MAĞLUP KURALI (METİNDEKİ 5d MADDESİ)
                    calculateLossCount(team, completedMatches)
                }
                .thenBy { it.preRoundPosition } // 4. Tur öncesi sıralama (düşük önce = üstte olan)
        )
        
        android.util.Log.d("EmreSystemCorrect", "🏆 TIEBREAKER RESULT:")
        sortedTeams.forEachIndexed { index, team ->
            android.util.Log.d("EmreSystemCorrect", "🥇 RANK ${index + 1}: Team ${team.currentPosition} (H2H: ${headToHeadPoints[team.id]}, PreRound: ${team.preRoundPosition})")
            
            // 🆕 İkincil puanları (H2H) takıma ata - UI gösterimi için
            team.secondaryPoints = headToHeadPoints[team.id] ?: 0.0
        }
        
        return sortedTeams
    }
    
    /**
     * 🆕 EN AZ MAĞLUP KURALI - METİNDEKİ 5d MADDESİ
     * Takımın toplam kaybettiği maç sayısını hesaplar
     * En az mağlup olan takım üstte çıkar
     */
    private fun calculateLossCount(team: EmreTeam, completedMatches: List<Match>): Int {
        val lossCount = completedMatches.count { match ->
            match.isCompleted && 
            (match.songId1 == team.id || match.songId2 == team.id) &&
            match.winnerId != null && 
            match.winnerId != team.id &&
            match.songId1 != match.songId2 // Bye değil
        }
        
        android.util.Log.d("EmreSystemCorrect", "😔 LOSS COUNT: Team ${team.currentPosition} has $lossCount losses")
        return lossCount
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

    // ==========================================
    // YENİ HİBRİT EŞLEŞTİRME ALGORİTMASI
    // ==========================================
    
    /**
     * 🆕 YENİ HİBRİT EŞLEŞTİRME ALGORİTMASI - KULLANICININ TARİF ETTİĞİ SİSTEM
     * 
     * ALGORİTMA KAVRAMI:
     * - EBTG: Eşleştirme Bekleyen Takımlar Grubu
     * - KEAT: Kendine Eşleştirme Arayan Takım
     * - AEG: Aday Eşleştirmeler Grubu
     * - EM: Eşleştirme Motoru
     * - EKG: Eşleşilemeden Kalanlar Grubu 
     * - EBT: Eşleştirmesi Bozulan Takım
     * - PÇT: Partneri Çalınan Takım
     * 
     * ALGORİTMA ADIMLARI:
     * 1. Hibrit Tarama: Bir üstten bir alttan KEAT seçimi
     * 2. KEAT kendinden sonraki/önceki takımlarla uygunluk kontrolü
     * 3. Uygun bulamazsa backtrack: Mevcut eşleştirme bozma
     * 4. PÇT-EBT karar matrisi ile yeniden eşleştirme
     * 5. Recursive backtrack chain ile tüm takımların eşleşmesi
     */
    fun createHybridPairingSystem(state: EmreState): EmrePairingResult {
        android.util.Log.d("EmreSystemCorrect", "🚀 STARTING HYBRID PAIRING SYSTEM: Round ${state.currentRound}")
        
        if (state.isComplete) {
            return EmrePairingResult(
                matches = emptyList(),
                byeTeam = null,
                hasSamePointMatch = false,
                canContinue = false,
                candidateMatches = emptyList()
            )
        }
        
        // Tur öncesi anlık sıralama hafızaya alınır
        val teamsWithPreRound = state.teams.map { team ->
            team.deepCopy().apply {
                preRoundPosition = currentPosition
            }
        }
        
        // Takımları anlık sıra numaralarına göre sırala
        val sortedTeams = teamsWithPreRound.sortedBy { it.currentPosition }
        
        android.util.Log.d("EmreSystemCorrect", "📊 PRE-ROUND POSITIONS: ${sortedTeams.size} teams")
        
        // BYE sistemi - geliştirilmiş kontrol
        val (teamsToMatch, byeTeam) = handleAdvancedByeSystem(sortedTeams)
        
        if (byeTeam != null) {
            android.util.Log.d("EmreSystemCorrect", "🆓 BYE TEAM: ${byeTeam.currentPosition} (TeamID: ${byeTeam.teamId})")
        }
        
        // Hibrit eşleştirme motoru başlatılır
        val pairingResult = executeHybridPairingEngine(teamsToMatch, state.matchHistory, state.currentRound)
        
        // Aynı puanlı kontrol ve turnuva bitiş analizi
        val tournamentAnalysis = analyzeTournamentContinuation(pairingResult.AEG.map { 
            CandidateMatch(it.team1, it.team2, it.isAsymmetricPoints, it.matchNumber) 
        }, state.currentRound)
        
        // ✅ AEG'yi alternating match numbering ile Match'lere convert et
        val convertedMatches = pairingResult.AEG.map { candidateMatch ->
            Match(
                listId = state.teams.firstOrNull()?.song?.listId ?: 0L,
                rankingMethod = "EMRE_CORRECT",
                songId1 = candidateMatch.team1.song.id,
                songId2 = candidateMatch.team2.song.id,
                winnerId = null,
                round = state.currentRound,
                matchNumber = candidateMatch.matchNumber, // 🆕 Alternating match numbering kullan
                isCompleted = false
            )
        }
        
        // 🎯 ALTERNATING MATCH NUMBERING LOG
        convertedMatches.forEach { match ->
            android.util.Log.d("EmreSystemCorrect", "🎯 MATCH #${match.matchNumber}: Song ${match.songId1} vs Song ${match.songId2}")
        }
        
        android.util.Log.d("EmreSystemCorrect", "🎯 HYBRID PAIRING COMPLETE: ${convertedMatches.size} matches converted from AEG")
        
        return EmrePairingResult(
            matches = convertedMatches, // ✅ Artık gerçek Match'ler döndürülüyor
            byeTeam = byeTeam,
            hasSamePointMatch = tournamentAnalysis.hasSamePointMatches,
            canContinue = tournamentAnalysis.canContinue,
            candidateMatches = pairingResult.AEG.map { 
                CandidateMatch(it.team1, it.team2, it.isAsymmetricPoints, it.matchNumber) 
            }
        )
    }
    
    /**
     * 🆕 ADVANCED BYE SİSTEMİ
     * Tek sayıda takımda bye geçmişlik kontrolü ile en uygun bye takımını seçer
     */
    private fun handleAdvancedByeSystem(sortedTeams: List<EmreTeam>): Pair<List<EmreTeam>, EmreTeam?> {
        android.util.Log.d("EmreSystemCorrect", "🔍 ADVANCED BYE CONTROL: ${sortedTeams.size} teams")
        
        if (sortedTeams.size % 2 == 0) {
            // Çift sayıda takım - bye yok
            android.util.Log.d("EmreSystemCorrect", "✅ EVEN TEAMS: No bye needed")
            return Pair(sortedTeams, null)
        }
        
        // Tek sayıda takım - bye gerekli
        android.util.Log.d("EmreSystemCorrect", "⚠️ ODD TEAMS: Bye selection required")
        
        // En alttan başlayarak bye geçmemiş takım ara
        for (i in sortedTeams.indices.reversed()) {
            val candidate = sortedTeams[i]
            if (!candidate.byePassed) {
                android.util.Log.d("EmreSystemCorrect", "🎯 BYE SELECTED: Team ${candidate.currentPosition} (never had bye)")
                val updatedByeTeam = candidate.deepCopy().apply { 
                    byePassed = true
                    byeCount += 1
                }
                val remainingTeams = sortedTeams.filterNot { it.teamId == candidate.teamId }
                return Pair(remainingTeams, updatedByeTeam)
            }
        }
        
        // Tüm takımlar bye geçmiş - en alttakini seç
        val byeTeam = sortedTeams.last().deepCopy().apply { 
            byeCount += 1 
        }
        val remainingTeams = sortedTeams.dropLast(1)
        
        android.util.Log.d("EmreSystemCorrect", "🔄 BYE FALLBACK: Team ${byeTeam.currentPosition} (all teams had bye)")
        return Pair(remainingTeams, byeTeam)
    }
    
    /**
     * 🆕 HİBRİT EŞLEŞTİRME MOTORU DATA CLASS'LARI
     */
    data class HybridPairingState(
        val EBTG: MutableList<EmreTeam> = mutableListOf(),      // Eşleştirme Bekleyen Takımlar Grubu
        val AEG: MutableList<CandidateMatch> = mutableListOf(), // Aday Eşleştirmeler Grubu
        val EKG: MutableList<EmreTeam> = mutableListOf(),       // Eşleşilemeden Kalanlar Grubu
        var currentKEAT: EmreTeam? = null,                      // Kendine Eşleştirme Arayan Takım
        var keatSelectionFromTop: Boolean = true,               // Üstten mi seçiliyor KEAT
        var backtrackDepth: Int = 0,                           // Backtrack derinliği
        val stolenPartners: MutableMap<Long, Long> = mutableMapOf() // TeamID -> Stolen Partner ID (bu tur için)
    )
    
    data class TournamentAnalysis(
        val hasSamePointMatches: Boolean,
        val canContinue: Boolean,
        val asymmetricCount: Int,
        val samePointCount: Int
    )
    
    /**
     * 🆕 ANA HİBRİT EŞLEŞTİRME MOTORU
     */
    private fun executeHybridPairingEngine(
        teams: List<EmreTeam>, 
        matchHistory: Set<Pair<Long, Long>>, 
        currentRound: Int
    ): HybridPairingState {
        
        val totalMatches = teams.size / 2 // 🆕 Toplam maç sayısı hesapla
        android.util.Log.d("EmreSystemCorrect", "⚙️ HYBRID PAIRING ENGINE: ${teams.size} teams to pair")
        android.util.Log.d("EmreSystemCorrect", "🎯 TARGET: $totalMatches pairs needed")
        
        val state = HybridPairingState()
        state.EBTG.addAll(teams.map { it.deepCopy() })
        
        val expectedPairs = teams.size / 2
        android.util.Log.d("EmreSystemCorrect", "🎯 TARGET: $expectedPairs pairs needed")
        
        // Ana hibrit döngü - tüm takımlar eşleşene kadar
        while (state.EBTG.isNotEmpty() && state.AEG.size < expectedPairs) {
            
            // KEAT seçimi - hibrit sistem (üst-alt-üst-alt)
            val keat = selectNextKEAT(state)
            if (keat == null) {
                android.util.Log.e("EmreSystemCorrect", "❌ CRITICAL ERROR: Cannot select KEAT")
                break
            }
            
            android.util.Log.d("EmreSystemCorrect", "👤 KEAT SELECTED: Team ${keat.currentPosition} (TeamID: ${keat.teamId})")
            
            // KEAT'ı EBTG'den çıkar
            state.EBTG.removeAll { it.teamId == keat.teamId }
            state.currentKEAT = keat
            
            // KEAT için partner arama
            val pairingResult = searchPartnerForKEAT(keat, state, matchHistory)
            
            when {
                pairingResult.isSuccess -> {
                    // Başarılı eşleştirme
                    val partner = pairingResult.partner!!
                    val candidateMatch = CandidateMatch(
                        team1 = keat,
                        team2 = partner,
                        isAsymmetricPoints = (keat.points != partner.points)
                    )
                    
                    // AEG'ye hibrit sıralama ile ekle
                    val totalMatches = teams.size / 2 // 🆕 Dinamik toplam maç sayısı
                    addToAEGWithHybridOrdering(state.AEG, candidateMatch, state.keatSelectionFromTop, totalMatches)
                    
                    // Partneri EBTG'den çıkar
                    state.EBTG.removeAll { it.teamId == partner.teamId }
                    
                    android.util.Log.d("EmreSystemCorrect", "✅ SUCCESSFUL PAIRING: ${keat.currentPosition} vs ${partner.currentPosition}")
                }
                
                pairingResult.needsBacktrack -> {
                    // Backtrack gerekli
                    android.util.Log.d("EmreSystemCorrect", "🔄 BACKTRACK REQUIRED for Team ${keat.currentPosition}")
                    
                    val backtrackResult = executeBacktrackChain(keat, state, matchHistory, totalMatches)
                    
                    if (backtrackResult.isSuccess) {
                        android.util.Log.d("EmreSystemCorrect", "✅ BACKTRACK SUCCESSFUL")
                        
                        // Backtrack depth kontrolü
                        state.backtrackDepth++
                        if (state.backtrackDepth >= 10) {
                            android.util.Log.w("EmreSystemCorrect", "⚠️ HIGH BACKTRACK DEPTH: ${state.backtrackDepth}")
                        }
                    } else {
                        android.util.Log.e("EmreSystemCorrect", "❌ BACKTRACK FAILED for Team ${keat.currentPosition}")
                        // KEAT'ı geri EBTG'ye ekle
                        state.EBTG.add(0, keat)
                    }
                }
                
                else -> {
                    android.util.Log.e("EmreSystemCorrect", "❌ NO PAIRING SOLUTION for Team ${keat.currentPosition}")
                    state.EBTG.add(0, keat) // Geri ekle
                }
            }
            
            // State validation - önemli noktalarda
            validateHybridState(state, teams.size)
        }
        
        android.util.Log.d("EmreSystemCorrect", "🏁 PAIRING ENGINE COMPLETE: ${state.AEG.size} pairs created")
        return state
    }
    
    /**
     * 🆕 HİBRİT KEAT SEÇİM SİSTEMİ
     * Bir üstten bir alttan seçim yapar
     */
    private fun selectNextKEAT(state: HybridPairingState): EmreTeam? {
        if (state.EBTG.isEmpty()) return null
        
        val selectedKeat = if (state.keatSelectionFromTop) {
            // En üstteki (en küçük currentPosition)
            state.EBTG.minByOrNull { it.currentPosition }
        } else {
            // En alttaki (en büyük currentPosition)  
            state.EBTG.maxByOrNull { it.currentPosition }
        }
        
        // Toggle pattern
        state.keatSelectionFromTop = !state.keatSelectionFromTop
        
        android.util.Log.d("EmreSystemCorrect", "🎯 KEAT SELECTION: ${if (!state.keatSelectionFromTop) "TOP" else "BOTTOM"} → Team ${selectedKeat?.currentPosition}")
        
        return selectedKeat
    }
    
    /**
     * 🆕 KEAT İÇİN PARTNER ARAMA SİSTEMİ
     */
    data class PartnerSearchResult(
        val isSuccess: Boolean = false,
        val partner: EmreTeam? = null,
        val needsBacktrack: Boolean = false,
        val targetForBacktrack: EmreTeam? = null
    )
    
    private fun searchPartnerForKEAT(
        keat: EmreTeam, 
        state: HybridPairingState, 
        matchHistory: Set<Pair<Long, Long>>
    ): PartnerSearchResult {
        
        android.util.Log.d("EmreSystemCorrect", "🔍 PARTNER SEARCH: Team ${keat.currentPosition}")
        
        // Önce kendi yönünde (üstten seçildiyse aşağıya, alttan seçildiyse yukarıya)
        val searchDirection = !state.keatSelectionFromTop // Toggle edilmiş durumda
        val candidates = if (searchDirection) {
            // Aşağıya doğru ara (currentPosition > keat.currentPosition)
            state.EBTG.filter { it.currentPosition > keat.currentPosition }.sortedBy { it.currentPosition }
        } else {
            // Yukarıya doğru ara (currentPosition < keat.currentPosition)
            state.EBTG.filter { it.currentPosition < keat.currentPosition }.sortedByDescending { it.currentPosition }
        }
        
        android.util.Log.d("EmreSystemCorrect", "🎯 SEARCH DIRECTION: ${if (searchDirection) "DOWNWARD" else "UPWARD"}, ${candidates.size} candidates")
        
        // Öncelikli arama - kendi yönünde
        for (candidate in candidates) {
            if (isValidPairing(keat, candidate, matchHistory, state)) {
                android.util.Log.d("EmreSystemCorrect", "✅ PARTNER FOUND: Team ${candidate.currentPosition}")
                return PartnerSearchResult(isSuccess = true, partner = candidate)
            }
        }
        
        // Kendi yönünde bulamadı - ters yöne bak
        val reverseCandidates = if (!searchDirection) {
            state.EBTG.filter { it.currentPosition > keat.currentPosition }.sortedBy { it.currentPosition }
        } else {
            state.EBTG.filter { it.currentPosition < keat.currentPosition }.sortedByDescending { it.currentPosition }
        }
        
        android.util.Log.d("EmreSystemCorrect", "🔄 REVERSE SEARCH: ${reverseCandidates.size} candidates")
        
        for (candidate in reverseCandidates) {
            if (isValidPairing(keat, candidate, matchHistory, state)) {
                android.util.Log.d("EmreSystemCorrect", "✅ REVERSE PARTNER FOUND: Team ${candidate.currentPosition}")
                return PartnerSearchResult(isSuccess = true, partner = candidate)
            }
        }
        
        // Hiçbir yerde bulamadı - backtrack gerekli
        android.util.Log.d("EmreSystemCorrect", "🚫 NO PARTNER FOUND: Backtrack required")
        return PartnerSearchResult(needsBacktrack = true)
    }
    
    /**
     * 🆕 BACKTRACK CHAIN SİSTEMİ - RECURSİVE
     * KEAT eşleştirme bulamadığında AEG'deki mevcut eşleştirmeleri bozar
     */
    data class BacktrackChainResult(
        val isSuccess: Boolean = false,
        val newPairing: CandidateMatch? = null,
        val displacedTeams: List<EmreTeam> = emptyList()
    )
    
    private fun executeBacktrackChain(
        keat: EmreTeam, 
        state: HybridPairingState, 
        matchHistory: Set<Pair<Long, Long>>,
        totalMatches: Int
    ): BacktrackChainResult {
        
        android.util.Log.d("EmreSystemCorrect", "🔄 EXECUTING BACKTRACK CHAIN: Team ${keat.currentPosition}")
        
        // AEG'deki eşleştirmeleri tarayarak bozulabilecek eşleştirme ara
        for (candidateMatch in state.AEG.toList()) { // toList() ile kopya al
            val EBT = candidateMatch.team1
            val PÇT = candidateMatch.team2
            
            android.util.Log.d("EmreSystemCorrect", "🔍 CHECKING EXISTING MATCH: ${EBT.currentPosition} vs ${PÇT.currentPosition}")
            
            // KEAT ile EBT uyumlu mu?
            val keatEBTCompatible = isValidPairing(keat, EBT, matchHistory, state)
            // KEAT ile PÇT uyumlu mu?
            val keatPÇTCompatible = isValidPairing(keat, PÇT, matchHistory, state)
            
            if (!keatEBTCompatible && !keatPÇTCompatible) {
                android.util.Log.d("EmreSystemCorrect", "❌ NEITHER COMPATIBLE: Skip this match")
                continue
            }
            
            // Bu eşleştirmeyi boz
            state.AEG.remove(candidateMatch)
            android.util.Log.d("EmreSystemCorrect", "💥 BREAKING MATCH: ${EBT.currentPosition} vs ${PÇT.currentPosition}")
            
            // PÇT-EBT karar matrisi
            val decision = makePÇTEBTDecision(keat, EBT, PÇT, state, matchHistory)
            
            when {
                decision.keatPairs == EBT -> {
                    // KEAT-EBT eşleşmesi
                    val newMatch = CandidateMatch(
                        team1 = keat,
                        team2 = EBT,
                        isAsymmetricPoints = (keat.points != EBT.points)
                    )
                    
                    // Stolen partner prevention
                    state.stolenPartners[PÇT.teamId] = EBT.teamId
                    
                    addToAEGWithHybridOrdering(state.AEG, newMatch, state.keatSelectionFromTop, totalMatches)
                    
                    // PÇT'yi EBTG'ye geri ekle (anlık sıralamasına göre)
                    insertPÇTBackToEBTG(PÇT, state)
                    
                    android.util.Log.d("EmreSystemCorrect", "✅ BACKTRACK SUCCESS: KEAT-EBT pairing")
                    return BacktrackChainResult(isSuccess = true, newPairing = newMatch, displacedTeams = listOf(PÇT))
                }
                
                decision.keatPairs == PÇT -> {
                    // KEAT-PÇT eşleşmesi
                    val newMatch = CandidateMatch(
                        team1 = keat,
                        team2 = PÇT,
                        isAsymmetricPoints = (keat.points != PÇT.points)
                    )
                    
                    // Stolen partner prevention
                    state.stolenPartners[EBT.teamId] = PÇT.teamId
                    
                    addToAEGWithHybridOrdering(state.AEG, newMatch, state.keatSelectionFromTop, totalMatches)
                    
                    // EBT'yi EBTG'ye geri ekle
                    insertPÇTBackToEBTG(EBT, state)
                    
                    android.util.Log.d("EmreSystemCorrect", "✅ BACKTRACK SUCCESS: KEAT-PÇT pairing")
                    return BacktrackChainResult(isSuccess = true, newPairing = newMatch, displacedTeams = listOf(EBT))
                }
                
                else -> {
                    // Karar verilemedi - eşleştirmeyi geri yükle
                    state.AEG.add(candidateMatch)
                    android.util.Log.d("EmreSystemCorrect", "🔄 RESTORING MATCH: No decision possible")
                }
            }
        }
        
        android.util.Log.e("EmreSystemCorrect", "❌ BACKTRACK CHAIN FAILED: No suitable match to break")
        return BacktrackChainResult(isSuccess = false)
    }
    
    /**
     * 🆕 PÇT-EBT KARAR MATRİSİ SİSTEMİ
     * KEAT'ın EBT/PÇT ile eşleşme kararını verir
     */
    data class PairingDecision(
        val keatPairs: EmreTeam? = null,
        val reason: String = ""
    )
    
    private fun makePÇTEBTDecision(
        keat: EmreTeam,
        EBT: EmreTeam, 
        PÇT: EmreTeam,
        state: HybridPairingState,
        matchHistory: Set<Pair<Long, Long>>
    ): PairingDecision {
        
        android.util.Log.d("EmreSystemCorrect", "🤔 DECISION MATRIX: KEAT vs EBT/PÇT")
        
        val keatEBTCompatible = isValidPairing(keat, EBT, matchHistory, state)
        val keatPÇTCompatible = isValidPairing(keat, PÇT, matchHistory, state)
        
        when {
            keatEBTCompatible && !keatPÇTCompatible -> {
                android.util.Log.d("EmreSystemCorrect", "📋 DECISION: KEAT-EBT (only EBT compatible)")
                return PairingDecision(keatPairs = EBT, reason = "Only EBT compatible")
            }
            
            !keatEBTCompatible && keatPÇTCompatible -> {
                android.util.Log.d("EmreSystemCorrect", "📋 DECISION: KEAT-PÇT (only PÇT compatible)")
                return PairingDecision(keatPairs = PÇT, reason = "Only PÇT compatible")
            }
            
            keatEBTCompatible && keatPÇTCompatible -> {
                // İkisi de uyumlu - EKG analizi gerekli
                android.util.Log.d("EmreSystemCorrect", "📋 BOTH COMPATIBLE: EKG analysis required")
                
                // EBT'nin EBTG'de (gelecekte) uygun partner bulabilme şansı
                val EBTCanFindPartner = canFindPartnerInEBTG(EBT, state, matchHistory)
                // PÇT'nin EBTG'de (gelecekte) uygun partner bulabilme şansı  
                val PÇTCanFindPartner = canFindPartnerInEBTG(PÇT, state, matchHistory)
                
                when {
                    EBTCanFindPartner && !PÇTCanFindPartner -> {
                        android.util.Log.d("EmreSystemCorrect", "📋 DECISION: KEAT-PÇT (EBT can find partner later)")
                        return PairingDecision(keatPairs = PÇT, reason = "EBT has better future prospects")
                    }
                    
                    !EBTCanFindPartner && PÇTCanFindPartner -> {
                        android.util.Log.d("EmreSystemCorrect", "📋 DECISION: KEAT-EBT (PÇT can find partner later)")
                        return PairingDecision(keatPairs = EBT, reason = "PÇT has better future prospects")
                    }
                    
                    else -> {
                        // Default: KEAT-EBT (kullanıcının algoritmasına göre)
                        android.util.Log.d("EmreSystemCorrect", "📋 DECISION: KEAT-EBT (default choice)")
                        return PairingDecision(keatPairs = EBT, reason = "Default algorithm preference")
                    }
                }
            }
            
            else -> {
                android.util.Log.e("EmreSystemCorrect", "❌ NO COMPATIBLE PAIRING")
                return PairingDecision(keatPairs = null, reason = "No compatible pairing")
            }
        }
    }
    
    /**
     * 🆕 VALIDATION SİSTEMİ
     */
    private fun isValidPairing(
        team1: EmreTeam, 
        team2: EmreTeam, 
        matchHistory: Set<Pair<Long, Long>>,
        state: HybridPairingState
    ): Boolean {
        
        // 1. Duplicate kontrol - match history
        if (hasTeamsPlayedBefore(team1.teamId, team2.teamId, matchHistory)) {
            return false
        }
        
        // 2. Stolen partner prevention (bu tur için)
        val stolenPartner = state.stolenPartners[team1.teamId]
        if (stolenPartner == team2.teamId) {
            android.util.Log.d("EmreSystemCorrect", "🚫 STOLEN PARTNER PREVENTION: ${team1.teamId} cannot pair with stolen partner ${team2.teamId}")
            return false
        }
        
        return true
    }
    
    /**
     * 🆕 EBTG'DE GELECEKTE PARTNER BULABILME ANALİZİ
     */
    private fun canFindPartnerInEBTG(
        team: EmreTeam, 
        state: HybridPairingState, 
        matchHistory: Set<Pair<Long, Long>>
    ): Boolean {
        
        // EBTG'deki takımlarla uyumluluk kontrolü
        for (candidate in state.EBTG) {
            if (isValidPairing(team, candidate, matchHistory, state)) {
                return true
            }
        }
        return false
    }
    
    /**
     * 🆕 PÇT'Yİ EBTG'YE GERİ EKLEME
     * Anlık sıralama pozisyonuna göre doğru yere ekler
     */
    private fun insertPÇTBackToEBTG(pçt: EmreTeam, state: HybridPairingState) {
        // Anlık sıralama pozisyonuna göre doğru yere ekle
        val insertIndex = state.EBTG.indexOfFirst { it.currentPosition > pçt.currentPosition }
        
        if (insertIndex == -1) {
            // En sona ekle
            state.EBTG.add(pçt)
        } else {
            // Doğru pozisyona ekle
            state.EBTG.add(insertIndex, pçt)
        }
        
        android.util.Log.d("EmreSystemCorrect", "🔄 PÇT RESTORED TO EBTG: Team ${pçt.currentPosition} at position $insertIndex")
    }
    
    /**
     * 🆕 AEG'YE HİBRİT SIRALAMA İLE EKLEME
     * Üstten/alttan gelen eşleştirmeleri doğru sırayla ekler
     */
    private fun addToAEGWithHybridOrdering(
        AEG: MutableList<CandidateMatch>, 
        match: CandidateMatch, 
        wasFromTop: Boolean,
        totalMatches: Int = 18 // 🆕 Toplam maç sayısı (36 takım ÷ 2 = 18)
    ) {
        // 🆕 ALTERNATING MATCH NUMBERING SYSTEM - TEMİZ VE BASIT
        if (wasFromTop) {
            // TOP KEAT → 1, 2, 3, 4, 5... sıralı numara
            val topCount = AEG.count { it.matchNumber <= totalMatches / 2 && it.matchNumber > 0 }
            match.matchNumber = topCount + 1
            
            // Üstten gelen eşleştirme - başa yakın ekle
            val insertIndex = AEG.indexOfFirst { 
                it.team1.currentPosition > match.team1.currentPosition && 
                it.team2.currentPosition > match.team2.currentPosition 
            }
            if (insertIndex == -1) AEG.add(match) else AEG.add(insertIndex, match)
        } else {
            // BOTTOM KEAT → 18, 17, 16, 15, 14... azalan numara  
            val bottomCount = AEG.count { it.matchNumber > totalMatches / 2 }
            match.matchNumber = totalMatches - bottomCount
            
            // Alttan gelen eşleştirme - sona yakın ekle  
            AEG.add(match)
        }
        
        android.util.Log.d("EmreSystemCorrect", "📋 AEG UPDATED: Match #${match.matchNumber} - ${match.team1.currentPosition} vs ${match.team2.currentPosition} (${if (wasFromTop) "TOP" else "BOTTOM"} origin)")
    }
    
    /**
     * 🆕 STATE VALİDATİON SİSTEMİ
     */
    private fun validateHybridState(state: HybridPairingState, totalTeams: Int) {
        val pairedTeams = state.AEG.size * 2
        val unpairedTeams = state.EBTG.size
        val totalCounted = pairedTeams + unpairedTeams
        
        if (totalCounted != totalTeams) {
            android.util.Log.e("EmreSystemCorrect", "❌ STATE VALIDATION FAILED: Expected $totalTeams, got $totalCounted")
        }
        
        // Duplicate team kontrolü
        val allTeamIds = mutableSetOf<Long>()
        state.EBTG.forEach { allTeamIds.add(it.teamId) }
        state.AEG.forEach { 
            allTeamIds.add(it.team1.teamId)
            allTeamIds.add(it.team2.teamId)
        }
        
        if (allTeamIds.size != totalTeams) {
            android.util.Log.e("EmreSystemCorrect", "❌ DUPLICATE TEAM DETECTED: ${allTeamIds.size} unique teams instead of $totalTeams")
        }
    }
    
    /**
     * 🆕 TOURNAMENT CONTINUATION ANALYSIS
     * Aynı puanlı eşleştirme analizi ve turnuva bitiş kararı
     */
    private fun analyzeTournamentContinuation(
        candidateMatches: List<CandidateMatch>,
        currentRound: Int
    ): TournamentAnalysis {
        
        android.util.Log.d("EmreSystemCorrect", "🏁 TOURNAMENT ANALYSIS: Round $currentRound, ${candidateMatches.size} matches")
        
        if (candidateMatches.isEmpty()) {
            return TournamentAnalysis(
                hasSamePointMatches = false,
                canContinue = false,
                asymmetricCount = 0,
                samePointCount = 0
            )
        }
        
        var samePointCount = 0
        var asymmetricCount = 0
        
        candidateMatches.forEachIndexed { index, match ->
            val isSamePoint = (match.team1.points == match.team2.points)
            if (isSamePoint) {
                samePointCount++
                android.util.Log.d("EmreSystemCorrect", "⚖️ SAME POINTS: Match ${index + 1}: ${match.team1.currentPosition}(${match.team1.points}p) vs ${match.team2.currentPosition}(${match.team2.points}p)")
            } else {
                asymmetricCount++
                android.util.Log.d("EmreSystemCorrect", "🔺 ASYMMETRIC: Match ${index + 1}: ${match.team1.currentPosition}(${match.team1.points}p) vs ${match.team2.currentPosition}(${match.team2.points}p)")
            }
        }
        
        // İlk tur özel durumu - her zaman oynanır
        val hasSamePointMatches = if (currentRound == 1) {
            android.util.Log.d("EmreSystemCorrect", "🎯 FIRST ROUND: Always continues")
            true
        } else {
            samePointCount > 0
        }
        
        val canContinue = hasSamePointMatches
        
        android.util.Log.d("EmreSystemCorrect", "📊 ANALYSIS RESULT:")
        android.util.Log.d("EmreSystemCorrect", "   Same Point Matches: $samePointCount")
        android.util.Log.d("EmreSystemCorrect", "   Asymmetric Matches: $asymmetricCount")
        android.util.Log.d("EmreSystemCorrect", "   Can Continue: $canContinue")
        android.util.Log.d("EmreSystemCorrect", if (canContinue) "✅ TOURNAMENT CONTINUES" else "🏁 TOURNAMENT ENDS")
        
        return TournamentAnalysis(
            hasSamePointMatches = hasSamePointMatches,
            canContinue = canContinue,
            asymmetricCount = asymmetricCount,
            samePointCount = samePointCount
        )
    }
    
    /**
     * 🆕 CANDIDATE MATCHES TO ACTUAL MATCHES CONVERTER
     * NOT COMPLETE - listId ve rankingMethod parametreleri gerekli
     */
    private fun convertCandidatesToMatches(
        candidateMatches: List<CandidateMatch>, 
        currentRound: Int,
        listId: Long,
        rankingMethod: String
    ): List<Match> {
        return candidateMatches.mapIndexed { index, candidate ->
            Match(
                id = 0, // Database'de auto-generate edilecek
                listId = listId,
                rankingMethod = rankingMethod,
                songId1 = candidate.team1.song.id,
                songId2 = candidate.team2.song.id,
                winnerId = null, // Henüz oynanmadı
                round = currentRound,
                isCompleted = false,
                createdAt = System.currentTimeMillis()
            )
        }
    }
    
    // hasTeamsPlayedBefore fonksiyonu mevcut - line 1784'te tanımlı
    
    // ===================================================================================
    // 🆕 KRİTER SİSTEMİ ENTEGRASYON FONKSİYONLARI
    // ===================================================================================
    
    /**
     * Maç sonuçlarını kriter puanları ile birlikte işle
     */
    fun processMatchResultWithCriteria(
        state: EmreState,
        match: Match,
        criteriaScores: List<CriterionScore>
    ): EmreState {
        android.util.Log.d("EmreSystemCorrect", "🎯 PROCESSING MATCH RESULT WITH CRITERIA")
        android.util.Log.d("EmreSystemCorrect", "📊 Criteria scores: ${criteriaScores.size} entries")
        
        val updatedTeams = state.teams.map { team ->
            val updatedTeam = team.deepCopy()
            
            // Eğer bu takım maçta oynamışsa kriter puanlarını ekle
            val isTeam1 = team.song.id == match.songId1
            val isTeam2 = team.song.id == match.songId2
            
            if (isTeam1 || isTeam2) {
                criteriaScores.forEach { criterionScore ->
                    val score = if (isTeam1) {
                        criterionScore.team1Score ?: 0.0
                    } else {
                        criterionScore.team2Score ?: 0.0
                    }
                    
                    updatedTeam.addCriteriaScore(criterionScore.criterionName, score)
                    android.util.Log.d("EmreSystemCorrect", "📈 ${team.song.name}: ${criterionScore.criterionName} = $score")
                }
                
                // Composite kriter puanını güncelle
                updatedTeam.calculateCompositeCriteriaScore(state.criteriaWeights)
            }
            
            updatedTeam
        }
        
        return state.copy(teams = updatedTeams)
    }
    
    /**
     * Kriter puanları ile gelişmiş sıralama algoritması
     * 
     * Sıralama Kriterleri (öncelik sırası):
     * 1. Ana puan (match wins)
     * 2. Kriter puanı (composite score) - eğer kullanımda ise
     * 3. Tiebreaker (preRoundPosition)
     */
    fun rankTeamsWithCriteria(teams: List<EmreTeam>, state: EmreState): List<EmreTeam> {
        return teams.sortedWith { team1, team2 ->
            // 1. Ana puan karşılaştırması
            val pointComparison = team2.points.compareTo(team1.points)
            if (pointComparison != 0) return@sortedWith pointComparison
            
            // 2. Kriter puanı karşılaştırması (eğer kriteria sistemi aktif ise)
            if (state.useCriteriaForTiebreaking && state.criteriaNames.isNotEmpty()) {
                val criteriaComparison = team2.compositeCriteriaScore.compareTo(team1.compositeCriteriaScore)
                if (criteriaComparison != 0) {
                    android.util.Log.d("EmreSystemCorrect", "🏆 CRITERIA TIEBREAK: ${team2.song.name}(${team2.compositeCriteriaScore}) vs ${team1.song.name}(${team1.compositeCriteriaScore})")
                    return@sortedWith criteriaComparison
                }
            }
            
            // 3. Tiebreaker (preRoundPosition - düşük değer önde)
            team1.preRoundPosition.compareTo(team2.preRoundPosition)
        }.mapIndexed { index, team ->
            team.deepCopy().apply {
                currentPosition = index + 1
            }
        }
    }
    
    /**
     * Hibrit puanlama sistemi: Ana puan + Kriter etkisi
     * 
     * Hibrit Puan = Ana Puan + (Kriter Puanı * Etki Faktörü)
     * 
     * @param state Turnuva durumu
     * @return Hibrit puanlama ile güncellenmiş takımlar
     */
    fun calculateHybridScoring(state: EmreState): List<EmreTeam> {
        if (!state.useCriteriaForTiebreaking || state.criteriaInfluenceFactor <= 0.0) {
            // Kriter sistemi kapalı, normal sıralama
            return state.teams
        }
        
        android.util.Log.d("EmreSystemCorrect", "🔀 CALCULATING HYBRID SCORING (influence: ${state.criteriaInfluenceFactor})")
        
        return state.teams.map { team ->
            val hybridScore = team.points + (team.compositeCriteriaScore * state.criteriaInfluenceFactor / 100.0)
            
            android.util.Log.d("EmreSystemCorrect", "📊 ${team.song.name}: Base=${team.points}, Criteria=${team.compositeCriteriaScore}, Hybrid=${hybridScore}")
            
            team.deepCopy().apply {
                // Hibrit puanı secondaryPoints alanında sakla (UI gösterimi için)
                secondaryPoints = hybridScore
            }
        }
    }
    
    /**
     * Normalize et kriter puanlarını (0-1 arası)
     */
    fun normalizeCriteriaScores(teams: List<EmreTeam>, criteriaNames: List<String>): List<EmreTeam> {
        if (criteriaNames.isEmpty()) return teams
        
        android.util.Log.d("EmreSystemCorrect", "📏 NORMALIZING CRITERIA SCORES")
        
        criteriaNames.forEach { criterionName ->
            val scores = teams.mapNotNull { it.getAverageCriteriaScore(criterionName).takeIf { score -> score > 0.0 } }
            if (scores.isEmpty()) return@forEach
            
            val minScore = scores.minOrNull() ?: 0.0
            val maxScore = scores.maxOrNull() ?: 0.0
            val range = maxScore - minScore
            
            if (range > 0.0) {
                teams.forEach { team ->
                    val originalScore = team.getAverageCriteriaScore(criterionName)
                    val normalizedScore = (originalScore - minScore) / range
                    team.normalizedCriteriaScores[criterionName] = normalizedScore
                    
                    android.util.Log.d("EmreSystemCorrect", "📐 ${team.song.name} $criterionName: $originalScore → $normalizedScore")
                }
            }
        }
        
        return teams
    }

}