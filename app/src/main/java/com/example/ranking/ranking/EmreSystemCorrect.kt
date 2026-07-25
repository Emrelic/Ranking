package com.example.ranking.ranking

import com.example.ranking.data.Song
import com.example.ranking.data.Match
import com.example.ranking.data.RankingResult

/**
 * Geliştirilmiş İsviçre Sistemi (Emre Usulü)
 *
 * Algoritma Kuralları:
 * 1. Başlangıç: Takımlara sabit ID ve sıra numarası atanır
 * 2. İlk tur: 1-2, 3-4, 5-6... eşleştirme, tek sayıda ise son takım bye geçer
 * 3. Puanlama: Galibiyet=1, beraberlik=0.5, mağlubiyet=0, bye=1 puan
 * 4. Yeniden sıralama: Puana göre → tiebreaker → yeni sıra numaraları
 * 5. Sonraki turlar: Sıraya göre ilk eşleşmemiş rakip bulunana kadar arama
 * 6. Aynı puan kontrolü: En az bir eşleşme aynı puanlı ise tur oynanır,
 *    hiçbir eşleşme aynı puanlı değilse turnuva biter
 * 7. Her takım diğer takımla EN FAZLA 1 KEZ oynar (kırmızı çizgi)
 * 8. Tüm takımları kapsayan tekrarsız eşleştirme kurulamıyorsa turnuva biter
 *    (sonsuz döngü koruması)
 */
object EmreSystemCorrect {

    data class EmreTeam(
        val song: Song,
        var points: Double = 0.0,
        var currentPosition: Int = 0,           // Anlık sıra numarası (değişken)
        val teamId: Long,                       // Sabit ID numarası
        var preRoundPosition: Int = 0,          // Tur öncesi anlık sıralama (tiebreaker için)
        var byePassed: Boolean = false,         // Bye geçti mi?
        var byeCount: Int = 0,                  // Kaç kere bye geçti
        var secondaryPoints: Double = 0.0       // İkincil puan (H2H) - UI gösterimi için
    ) {
        val id: Long get() = song.id

        fun deepCopy(): EmreTeam {
            return EmreTeam(
                song = song,
                points = points,
                currentPosition = currentPosition,
                teamId = teamId,
                preRoundPosition = preRoundPosition,
                byePassed = byePassed,
                byeCount = byeCount,
                secondaryPoints = secondaryPoints
            )
        }
    }

    data class EmreState(
        val teams: List<EmreTeam>,
        val matchHistory: Set<Pair<Long, Long>> = emptySet(), // Oynanan eşleşmeler (normalize: küçük ID önce)
        val currentRound: Int = 1,
        val isComplete: Boolean = false
    )

    data class EmrePairingResult(
        val matches: List<Match>,
        val byeTeam: EmreTeam? = null,
        val hasSamePointMatch: Boolean = false,
        val canContinue: Boolean = true,
        val candidateMatches: List<CandidateMatch> = emptyList()
    )

    data class CandidateMatch(
        val team1: EmreTeam,
        val team2: EmreTeam,
        val isAsymmetricPoints: Boolean, // Farklı puanlı mı?
        var matchNumber: Int = 0         // Alternating match numbering için
    )

    /**
     * Emre turnuvası başlat
     */
    fun initializeEmreTournament(songs: List<Song>): EmreState {
        val teams = songs.mapIndexed { index, song ->
            EmreTeam(
                song = song,
                points = 0.0,
                currentPosition = index + 1,
                teamId = (1000 + index).toLong(), // 4 basamaklı sabit TeamID
                preRoundPosition = index + 1,
                byePassed = false,
                byeCount = 0
            )
        }

        return EmreState(
            teams = teams,
            matchHistory = emptySet(),
            currentRound = 1,
            isComplete = false
        )
    }

    // ==========================================
    // HİBRİT EŞLEŞTİRME MOTORU (canlı motor)
    // ==========================================

    /**
     * HİBRİT EŞLEŞTİRME ALGORİTMASI
     *
     * KAVRAMLAR:
     * - EBTG: Eşleştirme Bekleyen Takımlar Grubu
     * - KEAT: Kendine Eşleştirme Arayan Takım
     * - AEG: Aday Eşleştirmeler Grubu
     * - EBT: Eşleştirmesi Bozulan Takım
     * - PÇT: Partneri Çalınan Takım
     *
     * ADIMLAR:
     * 1. Hibrit tarama: Bir üstten bir alttan KEAT seçimi
     * 2. KEAT kendinden sonraki/önceki takımlarla uygunluk kontrolü
     * 3. Uygun bulamazsa backtrack: Mevcut eşleştirme bozma
     * 4. PÇT-EBT karar matrisi ile yeniden eşleştirme
     */
    fun createHybridPairingSystem(state: EmreState): EmrePairingResult {
        if (state.isComplete) {
            return EmrePairingResult(
                matches = emptyList(),
                byeTeam = null,
                hasSamePointMatch = false,
                canContinue = false,
                candidateMatches = emptyList()
            )
        }

        // Tur öncesi anlık sıralama hafızaya alınır (tiebreaker için)
        val teamsWithPreRound = state.teams.map { team ->
            team.deepCopy().apply {
                preRoundPosition = currentPosition
            }
        }

        val sortedTeams = teamsWithPreRound.sortedBy { it.currentPosition }

        // BYE sistemi
        val (teamsToMatch, byeTeam) = handleAdvancedByeSystem(sortedTeams)

        // Hibrit eşleştirme motoru
        val pairingResult = executeHybridPairingEngine(teamsToMatch, state.matchHistory)

        val expectedPairs = teamsToMatch.size / 2
        val candidates = pairingResult.AEG.map {
            CandidateMatch(it.team1, it.team2, it.isAsymmetricPoints, it.matchNumber)
        }

        // SONSUZ DÖNGÜ / KİLİTLENME KORUMASI:
        // Tüm takımları kapsayan tekrarsız eşleştirme kurulamadıysa turnuva biter.
        // (Kırmızı çizgi: iki takım ikinci kez eşleştirilemez; eksik turla devam edilemez.)
        if (pairingResult.AEG.size < expectedPairs) {
            return EmrePairingResult(
                matches = emptyList(),
                byeTeam = byeTeam,
                hasSamePointMatch = false,
                canContinue = false,
                candidateMatches = candidates
            )
        }

        // Aynı puanlı eşleşme kontrolü ve turnuva bitiş analizi
        val analysis = analyzeTournamentContinuation(candidates, state.currentRound)
        if (!analysis.canContinue) {
            return EmrePairingResult(
                matches = emptyList(),
                byeTeam = byeTeam,
                hasSamePointMatch = false,
                canContinue = false,
                candidateMatches = candidates
            )
        }

        // AEG'yi alternating match numbering ile Match'lere çevir
        val convertedMatches = pairingResult.AEG.map { candidateMatch ->
            Match(
                listId = state.teams.firstOrNull()?.song?.listId ?: 0L,
                rankingMethod = "EMRE_CORRECT",
                songId1 = candidateMatch.team1.song.id,
                songId2 = candidateMatch.team2.song.id,
                winnerId = null,
                round = state.currentRound,
                matchNumber = candidateMatch.matchNumber,
                isCompleted = false
            )
        }

        return EmrePairingResult(
            matches = convertedMatches,
            byeTeam = byeTeam,
            hasSamePointMatch = analysis.hasSamePointMatches,
            canContinue = true,
            candidateMatches = candidates
        )
    }

    /**
     * BYE SİSTEMİ
     * Tek sayıda takımda, en alttan başlayarak bye geçmemiş ilk takım bye geçer.
     * Çift sayıda takımda hiçbir takım bye geçemez.
     */
    private fun handleAdvancedByeSystem(sortedTeams: List<EmreTeam>): Pair<List<EmreTeam>, EmreTeam?> {
        if (sortedTeams.size % 2 == 0) {
            return Pair(sortedTeams, null)
        }

        // En alttan başlayarak bye geçmemiş takım ara
        for (i in sortedTeams.indices.reversed()) {
            val candidate = sortedTeams[i]
            if (!candidate.byePassed) {
                val updatedByeTeam = candidate.deepCopy().apply {
                    byePassed = true
                    byeCount += 1
                }
                val remainingTeams = sortedTeams.filterNot { it.teamId == candidate.teamId }
                return Pair(remainingTeams, updatedByeTeam)
            }
        }

        // Tüm takımlar en az bir kez bye geçmiş - adil rotasyon için
        // en az bye geçmiş takımlardan en alttakini seç
        val minByeCount = sortedTeams.minOf { it.byeCount }
        val candidate = sortedTeams.last { it.byeCount == minByeCount }
        val byeTeam = candidate.deepCopy().apply {
            byeCount += 1
        }
        return Pair(sortedTeams.filterNot { it.teamId == candidate.teamId }, byeTeam)
    }

    data class HybridPairingState(
        val EBTG: MutableList<EmreTeam> = mutableListOf(),      // Eşleştirme Bekleyen Takımlar Grubu
        val AEG: MutableList<CandidateMatch> = mutableListOf(), // Aday Eşleştirmeler Grubu
        var keatSelectionFromTop: Boolean = true,               // Üstten mi seçiliyor KEAT
        val stolenPartners: MutableMap<Long, Long> = mutableMapOf() // TeamID -> çalınan partner ID (bu tur için)
    )

    data class TournamentAnalysis(
        val hasSamePointMatches: Boolean,
        val canContinue: Boolean,
        val asymmetricCount: Int,
        val samePointCount: Int
    )

    /**
     * ANA HİBRİT EŞLEŞTİRME MOTORU
     *
     * Döngü iki koşulla sınırlıdır:
     * - Normal bitiş: EBTG boşalır veya beklenen eşleşme sayısına ulaşılır
     * - Güvenlik sınırı: maxIterations aşılırsa döngü durur; eksik eşleştirme
     *   createHybridPairingSystem tarafında turnuva bitişi olarak yorumlanır.
     */
    private fun executeHybridPairingEngine(
        teams: List<EmreTeam>,
        matchHistory: Set<Pair<Long, Long>>
    ): HybridPairingState {
        val totalMatches = teams.size / 2
        val expectedPairs = teams.size / 2

        val state = HybridPairingState()
        state.EBTG.addAll(teams.map { it.deepCopy() })

        var iterations = 0
        val maxIterations = (teams.size * 10).coerceAtLeast(100)
        var consecutiveFailures = 0

        while (state.EBTG.isNotEmpty() && state.AEG.size < expectedPairs && iterations < maxIterations) {
            iterations++

            // Ardışık başarısızlık sayısı EBTG'deki takım sayısını aşarsa,
            // kalan takımların hiçbiri için çözüm yok demektir - döngüyü bitir.
            if (consecutiveFailures > state.EBTG.size + 1) {
                break
            }

            // KEAT seçimi - hibrit sistem (üst-alt-üst-alt)
            val keat = selectNextKEAT(state) ?: break

            state.EBTG.removeAll { it.teamId == keat.teamId }

            val pairingResult = searchPartnerForKEAT(keat, state, matchHistory)

            when {
                pairingResult.isSuccess -> {
                    consecutiveFailures = 0
                    val partner = pairingResult.partner!!
                    val candidateMatch = CandidateMatch(
                        team1 = keat,
                        team2 = partner,
                        isAsymmetricPoints = (keat.points != partner.points)
                    )
                    // selectNextKEAT bayrağı seçimden SONRA çevirir; KEAT'in gerçek
                    // yönü çevrilmiş bayrağın tersidir (TOP KEAT -> 1,2,3...)
                    addToAEGWithHybridOrdering(state.AEG, candidateMatch, !state.keatSelectionFromTop, totalMatches)
                    state.EBTG.removeAll { it.teamId == partner.teamId }
                }

                pairingResult.needsBacktrack -> {
                    val backtrackResult = executeBacktrackChain(keat, state, matchHistory, totalMatches)
                    if (backtrackResult.isSuccess) {
                        consecutiveFailures = 0
                    } else {
                        consecutiveFailures++
                        state.EBTG.add(0, keat)
                    }
                }

                else -> {
                    consecutiveFailures++
                    state.EBTG.add(0, keat)
                }
            }
        }

        return state
    }

    /**
     * HİBRİT KEAT SEÇİM SİSTEMİ - bir üstten bir alttan seçim yapar
     */
    private fun selectNextKEAT(state: HybridPairingState): EmreTeam? {
        if (state.EBTG.isEmpty()) return null

        val selectedKeat = if (state.keatSelectionFromTop) {
            state.EBTG.minByOrNull { it.currentPosition }
        } else {
            state.EBTG.maxByOrNull { it.currentPosition }
        }

        state.keatSelectionFromTop = !state.keatSelectionFromTop
        return selectedKeat
    }

    data class PartnerSearchResult(
        val isSuccess: Boolean = false,
        val partner: EmreTeam? = null,
        val needsBacktrack: Boolean = false
    )

    private fun searchPartnerForKEAT(
        keat: EmreTeam,
        state: HybridPairingState,
        matchHistory: Set<Pair<Long, Long>>
    ): PartnerSearchResult {
        // Önce kendi yönünde ara (üstten seçildiyse aşağıya, alttan seçildiyse yukarıya)
        val searchDirection = !state.keatSelectionFromTop // Toggle edilmiş durumda
        val candidates = if (searchDirection) {
            state.EBTG.filter { it.currentPosition > keat.currentPosition }.sortedBy { it.currentPosition }
        } else {
            state.EBTG.filter { it.currentPosition < keat.currentPosition }.sortedByDescending { it.currentPosition }
        }

        for (candidate in candidates) {
            if (isValidPairing(keat, candidate, matchHistory, state)) {
                return PartnerSearchResult(isSuccess = true, partner = candidate)
            }
        }

        // Kendi yönünde bulamadı - ters yöne bak
        val reverseCandidates = if (!searchDirection) {
            state.EBTG.filter { it.currentPosition > keat.currentPosition }.sortedBy { it.currentPosition }
        } else {
            state.EBTG.filter { it.currentPosition < keat.currentPosition }.sortedByDescending { it.currentPosition }
        }

        for (candidate in reverseCandidates) {
            if (isValidPairing(keat, candidate, matchHistory, state)) {
                return PartnerSearchResult(isSuccess = true, partner = candidate)
            }
        }

        // Hiçbir yerde bulamadı - backtrack gerekli
        return PartnerSearchResult(needsBacktrack = true)
    }

    data class BacktrackChainResult(
        val isSuccess: Boolean = false,
        val newPairing: CandidateMatch? = null,
        val displacedTeams: List<EmreTeam> = emptyList()
    )

    /**
     * BACKTRACK CHAIN - KEAT eşleştirme bulamadığında AEG'deki mevcut eşleştirmeleri bozar
     */
    private fun executeBacktrackChain(
        keat: EmreTeam,
        state: HybridPairingState,
        matchHistory: Set<Pair<Long, Long>>,
        totalMatches: Int
    ): BacktrackChainResult {
        for (candidateMatch in state.AEG.toList()) {
            val ebt = candidateMatch.team1  // Eşleştirmesi Bozulan Takım
            val pct = candidateMatch.team2  // Partneri Çalınan Takım

            val keatEbtCompatible = isValidPairing(keat, ebt, matchHistory, state)
            val keatPctCompatible = isValidPairing(keat, pct, matchHistory, state)

            if (!keatEbtCompatible && !keatPctCompatible) {
                continue
            }

            state.AEG.remove(candidateMatch)

            val decision = makePctEbtDecision(keat, ebt, pct, state, matchHistory)

            when {
                decision.keatPairs == ebt -> {
                    val newMatch = CandidateMatch(
                        team1 = keat,
                        team2 = ebt,
                        isAsymmetricPoints = (keat.points != ebt.points)
                    )
                    state.stolenPartners[pct.teamId] = ebt.teamId
                    addToAEGWithHybridOrdering(state.AEG, newMatch, !state.keatSelectionFromTop, totalMatches)
                    insertTeamBackToEBTG(pct, state)
                    return BacktrackChainResult(isSuccess = true, newPairing = newMatch, displacedTeams = listOf(pct))
                }

                decision.keatPairs == pct -> {
                    val newMatch = CandidateMatch(
                        team1 = keat,
                        team2 = pct,
                        isAsymmetricPoints = (keat.points != pct.points)
                    )
                    state.stolenPartners[ebt.teamId] = pct.teamId
                    addToAEGWithHybridOrdering(state.AEG, newMatch, !state.keatSelectionFromTop, totalMatches)
                    insertTeamBackToEBTG(ebt, state)
                    return BacktrackChainResult(isSuccess = true, newPairing = newMatch, displacedTeams = listOf(ebt))
                }

                else -> {
                    // Karar verilemedi - eşleştirmeyi geri yükle
                    state.AEG.add(candidateMatch)
                }
            }
        }

        return BacktrackChainResult(isSuccess = false)
    }

    data class PairingDecision(
        val keatPairs: EmreTeam? = null,
        val reason: String = ""
    )

    /**
     * PÇT-EBT KARAR MATRİSİ - KEAT'ın EBT/PÇT ile eşleşme kararını verir
     */
    private fun makePctEbtDecision(
        keat: EmreTeam,
        ebt: EmreTeam,
        pct: EmreTeam,
        state: HybridPairingState,
        matchHistory: Set<Pair<Long, Long>>
    ): PairingDecision {
        val keatEbtCompatible = isValidPairing(keat, ebt, matchHistory, state)
        val keatPctCompatible = isValidPairing(keat, pct, matchHistory, state)

        return when {
            keatEbtCompatible && !keatPctCompatible ->
                PairingDecision(keatPairs = ebt, reason = "Only EBT compatible")

            !keatEbtCompatible && keatPctCompatible ->
                PairingDecision(keatPairs = pct, reason = "Only PÇT compatible")

            keatEbtCompatible && keatPctCompatible -> {
                // İkisi de uyumlu - kalan havuzda partner bulma şansına göre karar ver
                val ebtCanFindPartner = canFindPartnerInEBTG(ebt, state, matchHistory)
                val pctCanFindPartner = canFindPartnerInEBTG(pct, state, matchHistory)

                when {
                    ebtCanFindPartner && !pctCanFindPartner ->
                        PairingDecision(keatPairs = pct, reason = "EBT has better future prospects")

                    !ebtCanFindPartner && pctCanFindPartner ->
                        PairingDecision(keatPairs = ebt, reason = "PÇT has better future prospects")

                    else ->
                        PairingDecision(keatPairs = ebt, reason = "Default algorithm preference")
                }
            }

            else -> PairingDecision(keatPairs = null, reason = "No compatible pairing")
        }
    }

    /**
     * Eşleştirme geçerlilik kontrolü:
     * 1. Daha önce oynamamış olmalılar (kırmızı çizgi)
     * 2. Bu turda birinden partner çalınmışsa aynı ikili tekrar kurulamaz (iki yönlü kontrol)
     */
    private fun isValidPairing(
        team1: EmreTeam,
        team2: EmreTeam,
        matchHistory: Set<Pair<Long, Long>>,
        state: HybridPairingState
    ): Boolean {
        if (hasTeamsPlayedBefore(team1.teamId, team2.teamId, matchHistory)) {
            return false
        }

        if (state.stolenPartners[team1.teamId] == team2.teamId ||
            state.stolenPartners[team2.teamId] == team1.teamId
        ) {
            return false
        }

        return true
    }

    private fun canFindPartnerInEBTG(
        team: EmreTeam,
        state: HybridPairingState,
        matchHistory: Set<Pair<Long, Long>>
    ): Boolean {
        return state.EBTG.any { candidate ->
            isValidPairing(team, candidate, matchHistory, state)
        }
    }

    /**
     * Bozulan eşleşmenin açıkta kalan takımını anlık sıralama pozisyonuna göre EBTG'ye geri ekler
     */
    private fun insertTeamBackToEBTG(team: EmreTeam, state: HybridPairingState) {
        val insertIndex = state.EBTG.indexOfFirst { it.currentPosition > team.currentPosition }
        if (insertIndex == -1) {
            state.EBTG.add(team)
        } else {
            state.EBTG.add(insertIndex, team)
        }
    }

    /**
     * AEG'YE HİBRİT SIRALAMA İLE EKLEME - Alternating match numbering:
     * TOP KEAT → 1, 2, 3... artan; BOTTOM KEAT → N, N-1, N-2... azalan.
     * Numara çakışması durumunda ilk boş numara atanır (çakışma imkânsız hale gelir).
     */
    private fun addToAEGWithHybridOrdering(
        AEG: MutableList<CandidateMatch>,
        match: CandidateMatch,
        wasFromTop: Boolean,
        totalMatches: Int
    ) {
        val usedNumbers = AEG.map { it.matchNumber }.toSet()

        match.matchNumber = if (wasFromTop) {
            (1..totalMatches).firstOrNull { it !in usedNumbers }
                ?: ((usedNumbers.maxOrNull() ?: 0) + 1)
        } else {
            (totalMatches downTo 1).firstOrNull { it !in usedNumbers }
                ?: ((usedNumbers.maxOrNull() ?: 0) + 1)
        }

        if (wasFromTop) {
            // Üstten gelen eşleştirme - başa yakın ekle
            val insertIndex = AEG.indexOfFirst {
                it.team1.currentPosition > match.team1.currentPosition &&
                it.team2.currentPosition > match.team2.currentPosition
            }
            if (insertIndex == -1) AEG.add(match) else AEG.add(insertIndex, match)
        } else {
            // Alttan gelen eşleştirme - sona ekle
            AEG.add(match)
        }
    }

    /**
     * TOURNAMENT CONTINUATION ANALYSIS
     * En az bir aynı puanlı eşleşme varsa tur oynanır; yoksa turnuva biter.
     * İlk tur her zaman oynanır.
     */
    private fun analyzeTournamentContinuation(
        candidateMatches: List<CandidateMatch>,
        currentRound: Int
    ): TournamentAnalysis {
        if (candidateMatches.isEmpty()) {
            return TournamentAnalysis(
                hasSamePointMatches = false,
                canContinue = false,
                asymmetricCount = 0,
                samePointCount = 0
            )
        }

        val samePointCount = candidateMatches.count { it.team1.points == it.team2.points }
        val asymmetricCount = candidateMatches.size - samePointCount

        val hasSamePointMatches = if (currentRound == 1) true else samePointCount > 0

        return TournamentAnalysis(
            hasSamePointMatches = hasSamePointMatches,
            canContinue = hasSamePointMatches,
            asymmetricCount = asymmetricCount,
            samePointCount = samePointCount
        )
    }

    // ==========================================
    // SONUÇ İŞLEME VE SIRALAMA
    // ==========================================

    /**
     * İki takımın daha önce oynayıp oynamadığını kontrol et (normalize pair lookup)
     */
    private fun hasTeamsPlayedBefore(team1Id: Long, team2Id: Long, matchHistory: Set<Pair<Long, Long>>): Boolean {
        val normalizedPair = if (team1Id < team2Id) {
            Pair(team1Id, team2Id)
        } else {
            Pair(team2Id, team1Id)
        }
        return normalizedPair in matchHistory
    }

    /**
     * Tur sonuçlarını işle ve sıralamayı yenile
     *
     * @param completedMatches SADECE bu turun tamamlanmış maçları (puanlar bunlardan eklenir)
     * @param allCompletedMatches Turnuvanın TÜM tamamlanmış maçları — tiebreaker zinciri
     * (head-to-head, direkt maç, mağlubiyet sayısı) tüm geçmişe bakmalıdır.
     * Verilmezse geriye dönük uyumluluk için bu turun maçları kullanılır.
     */
    fun processRoundResults(
        state: EmreState,
        completedMatches: List<Match>,
        byeTeam: EmreTeam? = null,
        allCompletedMatches: List<Match> = completedMatches
    ): EmreState {
        val updatedTeams = state.teams.map { it.deepCopy() }.toMutableList()
        val newMatchHistory = state.matchHistory.toMutableSet()

        // Tur öncesi sıralamayı tiebreaker için sabitle (4. kriter):
        // pozisyonlar aşağıda yeniden atanmadan önceki hali "tur öncesi" halidir
        updatedTeams.forEach { it.preRoundPosition = it.currentPosition }

        // Song ID → sabit team ID eşlemesi (match history sabit ID'lerle tutulur)
        val songToTeamMap = state.teams.associate { team -> team.song.id to team.teamId }

        // Bye geçen takıma puan ekle
        byeTeam?.let { bye ->
            val teamIndex = updatedTeams.indexOfFirst { it.id == bye.id }
            if (teamIndex >= 0) {
                val currentTeam = updatedTeams[teamIndex]
                updatedTeams[teamIndex] = currentTeam.copy(
                    points = currentTeam.points + 1.0,
                    byePassed = true,
                    byeCount = currentTeam.byeCount + 1
                )
            }
        }

        // Aynı maçın iki kez işlenmesini engelle
        val processedMatchIds = mutableSetOf<Long>()

        completedMatches.forEach { match ->
            if (match.id in processedMatchIds) {
                return@forEach
            }
            processedMatchIds.add(match.id)

            // Tamamlanmamış/iptal edilmiş maç ne match history'ye girer ne puan
            // üretir; aksi halde hiç oynanmamış ikili "oynadı" sayılıp ileride
            // geçerli eşleştirmeler gereksiz yasaklanır
            if (!match.isCompleted) {
                return@forEach
            }

            val teamId1 = songToTeamMap[match.songId1]
            val teamId2 = songToTeamMap[match.songId2]

            if (teamId1 != null && teamId2 != null) {
                val normalizedPair = if (teamId1 < teamId2) {
                    Pair(teamId1, teamId2)
                } else {
                    Pair(teamId2, teamId1)
                }
                newMatchHistory.add(normalizedPair)
            }

            // Puanları güncelle
            if (match.isCompleted) {
                when (match.winnerId) {
                    match.songId1 -> {
                        val winnerIndex = updatedTeams.indexOfFirst { it.id == match.songId1 }
                        if (winnerIndex >= 0) {
                            updatedTeams[winnerIndex] = updatedTeams[winnerIndex].copy(
                                points = updatedTeams[winnerIndex].points + 1.0
                            )
                        }
                    }
                    match.songId2 -> {
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

        // Emre usulü sıralamayı yenile (tiebreaker TÜM maç geçmişine bakar)
        val reorderedTeams = reorderTeamsEmreStyle(updatedTeams, allCompletedMatches)

        return EmreState(
            teams = reorderedTeams,
            matchHistory = newMatchHistory,
            currentRound = state.currentRound + 1,
            isComplete = false
        )
    }

    /**
     * Takımları Emre usulü kurallarına göre yeniden sırala:
     * 1. Puana göre yüksekten alçağa
     * 2. Eşit puanlı takımlar için tiebreaker zinciri (applySamePointTiebreaker)
     * 3. Yeni sıra numaraları atanır
     */
    private fun reorderTeamsEmreStyle(teams: List<EmreTeam>, completedMatches: List<Match>): List<EmreTeam> {
        val pointGroups = teams.groupBy { it.points }
        val sortedResults = mutableListOf<EmreTeam>()

        pointGroups.keys.sortedDescending().forEach { points ->
            val samePointTeams = pointGroups[points]!!
            if (samePointTeams.size == 1) {
                sortedResults.addAll(samePointTeams)
            } else {
                sortedResults.addAll(applySamePointTiebreaker(samePointTeams, completedMatches))
            }
        }

        return sortedResults.mapIndexed { index, team ->
            team.copy(currentPosition = index + 1)
        }
    }

    /**
     * TIEBREAKER ZİNCİRİ (aynı puanlı takımlar için):
     * 1. Head-to-head puanlar (aynı puanlı takımların kendi aralarındaki maç puanları)
     * 2. Direkt maç sonucu
     * 3. En az mağlubiyet
     * 4. Tur öncesi anlık sıralama (düşük önce)
     */
    private fun applySamePointTiebreaker(samePointTeams: List<EmreTeam>, completedMatches: List<Match>): List<EmreTeam> {
        if (samePointTeams.size <= 1) return samePointTeams

        // Her takım için head-to-head puanları hesapla
        val headToHeadPoints = mutableMapOf<Long, Double>()
        samePointTeams.forEach { team ->
            headToHeadPoints[team.id] = 0.0
        }

        samePointTeams.forEach { teamA ->
            samePointTeams.forEach { teamB ->
                if (teamA.id != teamB.id) {
                    val matchesBetween = completedMatches.filter { match ->
                        match.isCompleted &&
                        ((match.songId1 == teamA.id && match.songId2 == teamB.id) ||
                         (match.songId1 == teamB.id && match.songId2 == teamA.id)) &&
                        match.songId1 != match.songId2 // Bye değil
                    }

                    matchesBetween.forEach { match ->
                        when (match.winnerId) {
                            teamA.id -> headToHeadPoints[teamA.id] = headToHeadPoints[teamA.id]!! + 1.0
                            null -> headToHeadPoints[teamA.id] = headToHeadPoints[teamA.id]!! + 0.5
                        }
                    }
                }
            }
        }

        val sortedTeams = samePointTeams.sortedWith(
            compareByDescending<EmreTeam> { headToHeadPoints[it.id] ?: 0.0 } // 1. H2H puan (yüksek önce)
                .thenComparing { team1, team2 ->
                    // 2. Direkt maç: H2H puanları eşitse, direkt maçta kim yendi?
                    val h2h1 = headToHeadPoints[team1.id] ?: 0.0
                    val h2h2 = headToHeadPoints[team2.id] ?: 0.0

                    if (h2h1 == h2h2) {
                        val directMatch = completedMatches.find { match ->
                            match.isCompleted &&
                            ((match.songId1 == team1.id && match.songId2 == team2.id) ||
                             (match.songId1 == team2.id && match.songId2 == team1.id)) &&
                            match.winnerId != null
                        }
                        when (directMatch?.winnerId) {
                            team1.id -> -1
                            team2.id -> 1
                            else -> 0
                        }
                    } else {
                        0 // İlk kriter zaten çözüyor
                    }
                }
                .thenBy { team -> calculateLossCount(team, completedMatches) } // 3. En az mağlubiyet
                .thenBy { it.preRoundPosition }                               // 4. Tur öncesi sıralama
        )

        // İkincil puanları (H2H) takıma ata - UI gösterimi için
        sortedTeams.forEach { team ->
            team.secondaryPoints = headToHeadPoints[team.id] ?: 0.0
        }

        return sortedTeams
    }

    /**
     * Takımın toplam kaybettiği maç sayısı (en az mağlubiyet kuralı için)
     */
    private fun calculateLossCount(team: EmreTeam, completedMatches: List<Match>): Int {
        return completedMatches.count { match ->
            match.isCompleted &&
            (match.songId1 == team.id || match.songId2 == team.id) &&
            match.winnerId != null &&
            match.winnerId != team.id &&
            match.songId1 != match.songId2 // Bye değil
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
}
