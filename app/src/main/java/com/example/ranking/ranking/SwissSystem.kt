package com.example.ranking.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.RankingResult
import com.example.ranking.data.Song
import kotlin.math.ceil
import kotlin.math.ln

/**
 * İsviçre Sistemi (Swiss System) - sıfırdan yazılmış motor.
 *
 * `RankingEngine.kt:520-618`deki eski `createSwissMatchesAdvanced` şu kusurlarla
 * menüden gizlenmişti: bye yok (tek takımda son takım hiç eşleşmiyordu), puan
 * grubunda tek kalan takım da düşüyordu, "eşleşme bulunamazsa ilk ikisini eşleştir"
 * diyerek tekrar eşleşmeye izin veriyordu, matchNumber hiç atanmıyordu, ilk tur
 * `shuffled()` ile rastgeleydi. Bu dosya bunların hiçbirini yapmaz.
 *
 * KURALLAR (oturumlar/SWISS-MOTORU.md):
 * 1. İki takım birbiriyle YALNIZ BİR KEZ eşleşir (kırmızı çizgi).
 * 2. Her turda TAM eşleştirme: çift takımda n/2 maç, tek takımda (n-1)/2 maç + 1 bye.
 * 3. Bye: en alttan başlayarak bye geçmemiş ilk takım; herkes geçtiyse en az
 *    bye geçmiş, en alttaki takım (adil rotasyon).
 * 4. Puan: galibiyet 1, beraberlik 0.5, bye 1.
 * 5. Eşleştirme aynı puan grubundan başlar; grup tekse komşu gruba taşar (float).
 * 6. Tur sayısı varsayılan ceil(log2(n)); tekrarsız tam eşleştirme kurulamazsa
 *    turnuva ERKEN biter (kural 1 asla çiğnenmez).
 * 7. matchNumber: en üst sıradaki eşleşme 1, en alttaki eşleşme N olacak şekilde
 *    ASC (oylama üstten alta doğru ilerler).
 *
 * DURUM YÖNETİMİ — replay deseni (bkz. PairwiseComparisonSort.kt, ORTAK.md):
 * Ayrı bir durum tablosu YOK. `computeState` tamamlanmış SWISS maçlarını baştan
 * oynatarak güncel durumu kurar; aynı girdi → aynı çıktı. Bye de bir Match kaydı
 * olarak saklanır (aşağıya bakınız) — yoksa replay bye geçmişini hatırlayamaz.
 *
 * BYE KAYIT KURALI: bye bir Match satırı olarak, `songId1 == songId2 == byeTeamId`,
 * `winnerId = byeTeamId`, `isCompleted = true` şeklinde tutulur (oy istemez, motor
 * onu üretirken doğrudan tamamlanmış yazar). Gerçek bir maçta iki taraf asla aynı
 * id olamayacağı için bu kendiyle-eşleşme kendine özgü ve çakışmasız bir bye
 * imzasıdır. Entegrasyon eden taraf (koordinatör): bu satırı oy ekranında GÖSTERME,
 * zaten `isCompleted=true` geliyor.
 */
object SwissSystem {

    const val METHOD = "SWISS"

    data class SwissTeam(
        val song: Song,
        val points: Double = 0.0,
        val played: Int = 0,
        val won: Int = 0,
        val drawn: Int = 0,
        val lost: Int = 0,
        val byeCount: Int = 0,
        val opponentIds: Set<Long> = emptySet()
    ) {
        val id: Long get() = song.id
    }

    data class SwissState(
        val teams: List<SwissTeam>,
        val currentRound: Int,
        val maxRounds: Int,
        val isComplete: Boolean
    )

    data class PairingResult(
        val matches: List<Match>,
        val byeTeam: SwissTeam?,
        val canContinue: Boolean,
        val reason: String
    )

    /** Geri izlemeli eşleştirme aramasında en çok bu kadar aday denenir (§ güvenlik sayacı). */
    private const val MAX_BACKTRACK_ATTEMPTS = 50_000

    private class MutableStats {
        var points = 0.0
        var played = 0
        var won = 0
        var drawn = 0
        var lost = 0
        var byeCount = 0
        val opponents = mutableSetOf<Long>()
    }

    /** Önerilen tur sayısı: ceil(log2(n)). n<=1 için 0 (oynanacak maç yok). */
    fun recommendedRoundCount(teamCount: Int): Int {
        if (teamCount <= 1) return 0
        val exact = ln(teamCount.toDouble()) / ln(2.0)
        return ceil(exact - 1e-9).toInt().coerceAtLeast(1)
    }

    /**
     * Tamamlanmış SWISS maçlarını baştan oynatarak güncel durumu kurar (replay).
     * `completedMatches`: TÜM tamamlanmış maçlar olabilir; burada yalnız
     * `rankingMethod == METHOD` ve `isCompleted == true` olanlar süzülür.
     *
     * 🔴 YETİM MAÇ: taraflardan biri artık `songs` içinde yoksa (öğe silinmiş)
     * bu maç ne geçmişe ne puana yazılır — çökmez, sessizce atlanır (EmreSystemCorrect
     * ile aynı kural).
     */
    fun computeState(songs: List<Song>, completedMatches: List<Match>): SwissState {
        val maxRounds = recommendedRoundCount(songs.size)

        if (songs.isEmpty()) {
            return SwissState(emptyList(), currentRound = 1, maxRounds = 0, isComplete = true)
        }
        if (songs.size == 1) {
            return SwissState(
                teams = listOf(SwissTeam(songs[0])),
                currentRound = 1,
                maxRounds = maxRounds,
                isComplete = true
            )
        }

        val statsById = songs.associate { it.id to MutableStats() }
        val relevant = completedMatches.filter { it.isCompleted && it.rankingMethod == METHOD }

        var maxRoundSeen = 0
        relevant.forEach { m ->
            if (m.round > maxRoundSeen) maxRoundSeen = m.round

            val isBye = m.songId1 == m.songId2
            if (isBye) {
                val stat = statsById[m.songId1] ?: return@forEach
                stat.points += 1.0
                stat.byeCount += 1
                stat.played += 1
                return@forEach
            }

            val s1 = statsById[m.songId1]
            val s2 = statsById[m.songId2]
            if (s1 == null || s2 == null) return@forEach // yetim maç: silinmiş öğe

            s1.opponents.add(m.songId2)
            s2.opponents.add(m.songId1)
            s1.played += 1
            s2.played += 1

            when (m.winnerId) {
                m.songId1 -> { s1.points += 1.0; s1.won += 1; s2.lost += 1 }
                m.songId2 -> { s2.points += 1.0; s2.won += 1; s1.lost += 1 }
                null -> { s1.points += 0.5; s2.points += 0.5; s1.drawn += 1; s2.drawn += 1 }
                // Tanımsız winnerId (ne songId1 ne songId2 ne null): bozuk veri —
                // uydurup puan/galibiyet ATANMAZ, yalnız oynandı/rakip bilgisi kalır.
            }
        }

        val teams = songs.map { s ->
            val st = statsById.getValue(s.id)
            SwissTeam(
                song = s,
                points = st.points,
                played = st.played,
                won = st.won,
                drawn = st.drawn,
                lost = st.lost,
                byeCount = st.byeCount,
                opponentIds = st.opponents.toSet()
            )
        }

        val currentRound = maxRoundSeen + 1
        val isComplete = maxRounds > 0 && currentRound > maxRounds

        return SwissState(teams, currentRound, maxRounds, isComplete)
    }

    /**
     * Sıradaki turun eşleştirmesi. `canContinue=false` ise turnuva biter — ya tur
     * bütçesi (maxRounds) dolduğu için, ya da tekrarsız tam eşleştirme kurulamadığı
     * için (kural 1 hiçbir zaman çiğnenmez, "tekrar eşleştir" ÇÖZÜM DEĞİLDİR).
     */
    fun createNextRound(state: SwissState, completedMatches: List<Match>): PairingResult {
        if (state.teams.size < 2) {
            return PairingResult(
                matches = emptyList(), byeTeam = null,
                canContinue = false, reason = "Yetersiz takım sayısı (en az 2 gerekir, mevcut: ${state.teams.size})"
            )
        }
        if (state.isComplete) {
            return PairingResult(
                matches = emptyList(), byeTeam = null,
                canContinue = false, reason = "Turnuva zaten tamamlandı (${state.maxRounds} tur oynandı)"
            )
        }

        val buchholz = computeBuchholz(state.teams)
        val standings = state.teams.sortedWith(standingsComparator(buchholz))

        val (toPair, byeTeam) = selectByeTeam(standings)
        val history = buildHistory(state.teams)

        val pairs = pairWithBacktracking(toPair, history)
            ?: return PairingResult(
                matches = emptyList(),
                byeTeam = byeTeam,
                canContinue = false,
                reason = "Tekrarsız tam eşleştirme kurulamadı (n=${toPair.size}, tur=${state.currentRound}) " +
                    "— kural 1 gereği tekrar eşleştirme yapılmadı, turnuva dürüstçe bitirildi"
            )

        val listId = state.teams.first().song.listId
        val matches = mutableListOf<Match>()

        if (byeTeam != null) {
            matches += Match(
                listId = listId,
                rankingMethod = METHOD,
                songId1 = byeTeam.id,
                songId2 = byeTeam.id,
                winnerId = byeTeam.id,
                round = state.currentRound,
                matchNumber = 0,
                isCompleted = true
            )
        }

        // pairWithBacktracking, anchor'ı her adımda sıradaki EN ÜST sıralı takımdan
        // seçtiği için döndürdüğü liste ZATEN üstten-alta sıralıdır — ayrıca
        // sıralamaya gerek yok. matchNumber = 1..N bu sırayla verilir (kural 7).
        pairs.forEachIndexed { index, pair ->
            matches += Match(
                listId = listId,
                rankingMethod = METHOD,
                songId1 = pair.first.id,
                songId2 = pair.second.id,
                winnerId = null,
                round = state.currentRound,
                matchNumber = index + 1,
                isCompleted = false
            )
        }

        return PairingResult(matches = matches, byeTeam = byeTeam, canContinue = true, reason = "")
    }

    /**
     * Final sıralama: puan → Buchholz → galibiyet sayısı → aralarındaki maç
     * (yalnız tam eşit KOMŞULARDA, geçişsiz olduğu için son bir geçiş olarak) → id.
     */
    fun calculateResults(songs: List<Song>, completedMatches: List<Match>): List<RankingResult> {
        val state = computeState(songs, completedMatches)
        if (state.teams.isEmpty()) return emptyList()

        val buchholz = computeBuchholz(state.teams)
        val relevant = completedMatches.filter {
            it.isCompleted && it.rankingMethod == METHOD && it.songId1 != it.songId2
        }

        val sorted = state.teams.sortedWith(standingsComparator(buchholz)).toMutableList()

        // 🔴 TOPLAM SIRALI karşılaştırıcı yukarıda; "aralarındaki maç" kriteri
        // BURADA yok çünkü ikili/geçişsiz: A yener B, B yener C, C yener A ise
        // A>B>C>A döngüsü kurulur ve sortedWith içine konsaydı Kotlin/TimSort
        // "Comparison method violates its general contract!" atardı (bu projede
        // EMRE_CORRECT'te tam bu hata ölçülüp düzeltildi, bkz. EmreSystemCorrect.kt).
        // Çözüm aynı: yalnız TAM EŞİT komşu ikililerde son bir geçiş olarak uygula.
        var i = 0
        var safety = sorted.size * sorted.size + 10
        while (i < sorted.size - 1 && safety-- > 0) {
            val upper = sorted[i]
            val lower = sorted[i + 1]
            val fullyTied = upper.points == lower.points &&
                (buchholz[upper.id] ?: 0.0) == (buchholz[lower.id] ?: 0.0) &&
                upper.won == lower.won

            if (fullyTied) {
                val direct = relevant.find { m ->
                    m.winnerId != null &&
                        ((m.songId1 == upper.id && m.songId2 == lower.id) ||
                            (m.songId1 == lower.id && m.songId2 == upper.id))
                }
                if (direct?.winnerId == lower.id) {
                    sorted[i] = lower
                    sorted[i + 1] = upper
                    if (i > 0) {
                        i--
                        continue
                    }
                }
            }
            i++
        }

        return sorted.mapIndexed { index, team ->
            RankingResult(
                songId = team.id,
                listId = team.song.listId,
                rankingMethod = METHOD,
                score = team.points,
                position = index + 1
            )
        }
    }

    // ==========================================
    // YARDIMCI FONKSİYONLAR
    // ==========================================

    /** Toplam sıralı (transitive) anahtar: puan → Buchholz → galibiyet → id (son çare). */
    private fun standingsComparator(buchholz: Map<Long, Double>): Comparator<SwissTeam> =
        compareByDescending<SwissTeam> { it.points }
            .thenByDescending { buchholz[it.id] ?: 0.0 }
            .thenByDescending { it.won }
            .thenBy { it.id }

    /** Buchholz: bir takımın rakiplerinin ANLIK toplam puanı. */
    private fun computeBuchholz(teams: List<SwissTeam>): Map<Long, Double> {
        val pointsById = teams.associate { it.id to it.points }
        return teams.associate { team ->
            team.id to team.opponentIds.sumOf { pointsById[it] ?: 0.0 }
        }
    }

    /**
     * Bye seçimi: tek sayıda takımda en alttan başlayarak bye geçmemiş ilk takım.
     * Herkes en az bir kez bye geçtiyse, en az bye geçmiş takımlardan en alttaki.
     * `standings` en yüksekten en düşüğe SIRALI verilmelidir.
     */
    private fun selectByeTeam(standings: List<SwissTeam>): Pair<List<SwissTeam>, SwissTeam?> {
        if (standings.size % 2 == 0) return Pair(standings, null)

        for (i in standings.indices.reversed()) {
            if (standings[i].byeCount == 0) {
                val bye = standings[i]
                return Pair(standings.filterNot { it.id == bye.id }, bye)
            }
        }
        val minBye = standings.minOf { it.byeCount }
        val bye = standings.last { it.byeCount == minBye }
        return Pair(standings.filterNot { it.id == bye.id }, bye)
    }

    /** Şimdiye kadar oynanmış (bye HARİÇ) ikililer, normalize edilmiş (küçük id önce). */
    private fun buildHistory(teams: List<SwissTeam>): Set<Pair<Long, Long>> {
        val history = mutableSetOf<Pair<Long, Long>>()
        teams.forEach { team ->
            team.opponentIds.forEach { oppId ->
                history.add(normalizedPair(team.id, oppId))
            }
        }
        return history
    }

    private fun normalizedPair(a: Long, b: Long): Pair<Long, Long> =
        if (a < b) Pair(a, b) else Pair(b, a)

    private class BacktrackBudget(var remaining: Int)

    /**
     * `teams` (standings sırasında, çift sayıda) için tekrarsız tam eşleştirme arar.
     * Her adımda en üst sıradaki takımı "çapa" alıp sıradaki ilk uygun (daha önce
     * oynamadığı) rakibi dener — bu doğal olarak aynı puan grubundan başlar, grup
     * tükenirse komşu gruba taşar (kural 5). Uygun rakip yoksa GERİ İZLER: bir
     * önceki eşleşmeyi geri alıp başka bir eşi dener.
     *
     * Güvenlik sayacı ([MAX_BACKTRACK_ATTEMPTS]): aday deneme sayısı bunu aşarsa
     * arama durdurulur ve null döner — turnuva dürüstçe biter, sonsuz/üstel
     * patlamaya girilmez.
     *
     * Dönen liste ZATEN üstten-alta sıralıdır (bkz. çağıran yerdeki not).
     */
    private fun pairWithBacktracking(
        teams: List<SwissTeam>,
        history: Set<Pair<Long, Long>>
    ): List<Pair<SwissTeam, SwissTeam>>? {
        if (teams.isEmpty()) return emptyList()
        if (teams.size % 2 != 0) return null // çağıran taraf her zaman çift sayı vermeli

        val budget = BacktrackBudget(MAX_BACKTRACK_ATTEMPTS)
        return backtrackMatch(teams, history, budget)
    }

    private fun backtrackMatch(
        remaining: List<SwissTeam>,
        history: Set<Pair<Long, Long>>,
        budget: BacktrackBudget
    ): List<Pair<SwissTeam, SwissTeam>>? {
        if (remaining.isEmpty()) return emptyList()

        val anchor = remaining[0]
        val rest = remaining.subList(1, remaining.size)

        for (candidate in rest) {
            if (budget.remaining <= 0) return null
            budget.remaining--

            if (normalizedPair(anchor.id, candidate.id) in history) continue

            val nextRemaining = rest.filterNot { it.id == candidate.id }
            val subResult = backtrackMatch(nextRemaining, history, budget)
            if (subResult != null) {
                return listOf(anchor to candidate) + subResult
            }
        }
        return null
    }
}
