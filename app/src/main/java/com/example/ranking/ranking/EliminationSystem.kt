package com.example.ranking.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.RankingResult
import com.example.ranking.data.Song
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Eleme usulü - sıfırdan yazılmış motor (oturumlar/ELEME-MOTORU.md).
 *
 * `RankingEngine.kt`'deki eski eleme kodu (~400 satır) şu ölçülmüş kusurlarla
 * menüden gizlenmişti:
 * 1. `getGroupSongs` (:288) İKİNCİ kez `shuffled()` çağırıyordu —
 *    `createEliminationMatches` (:165) bir kez karıştırıp grupları kurmuş, sonuç
 *    hesabı BAŞKA bir karıştırmayla grup üyeliğini yeniden türetiyordu. Sonuçtaki
 *    gruplar oynanan maçlarla alakasızdı.
 * 2. `calculateEliminationResults` (:337) pozisyonları ÇAKIŞTIRIYORDU:
 *    elenenlere `songCount--` ile aşağıdan, knockout sonuçlarına `1..k` ile
 *    yukarıdan pozisyon veriliyordu.
 * 3. `createDirectEliminationMatches` turları önceden üretmeye çalışıyordu —
 *    kazanan bilinmeden sonraki tur kurulamaz.
 *
 * Bu dosya bunların hiçbirini yapmaz.
 *
 * DURUM YÖNETİMİ — replay deseni (ORTAK.md, bkz. PairwiseComparisonSort.kt,
 * SwissSystem.kt): ayrı bir durum tablosu YOK. Tüm `compute*`/`create*`/
 * `calculate*` fonksiyonları tamamlanmış `Match` kayıtlarını (`completed`)
 * baştan oynatarak güncel durumu kurar; aynı girdi → aynı çıktı. `shuffled()`,
 * `Random`, `System.currentTimeMillis()` KULLANILMAZ — replay'i kırar.
 *
 * SEED KURALI: tüm modlarda temel seed, `song.id` artan sırasına göre 1'den
 * başlar (deterministik, `ORTAK.md`). GRUP kipinde knockout aşamasının kendi
 * seed'i grup performansından türetilir (bkz. `knockoutSeeds`).
 *
 * SEEDING/EŞLEŞTİRME KURALI: her tur, o turda hayatta kalanlar seed'e göre
 * artan sıralanır ve "en güçlü en zayıfla" eşleştirilir (seed[0] vs
 * seed[son], seed[1] vs seed[sondan bir önceki]...). Bu, klasik sabit-slot
 * bracket'la AYNI eşleşmeleri üretir (üst sıralar kazandıkça): standart
 * "reseeding" turnuva biçimi, birçok profesyonel ligde de kullanılır.
 *
 * BYE / ÖN TUR: `n` güç-2 değilse en alttaki `2*fazla` seed ön turda (round 0)
 * eşleşir, `fazla` kadarı elenir; üstteki `targetSize-fazla` seed ilk turu
 * hiç oynamadan (bye) doğrudan 1. tura girer — bu geçiş bir Match kaydı
 * GEREKTİRMEZ, çünkü kimin bye geçtiği yalnız seed'den deterministik olarak
 * çıkarılabilir (`SwissSystem.kt`'deki öz-eşleşme bye kaydı burada gerekmiyor).
 *
 * ROUND NUMARALANDIRMASI:
 * - TEK ELEME / GRUP+ELEME'nin knockout kısmı: round 0 = ön tur (varsa),
 *   round 1..R = ana braket (R = final).
 * - GRUP+ELEME'nin grup aşaması: round 0, `groupId` dolu (grup içi lig).
 *   Knockout kısmı `baseRound=1` ile başlar (round 0 grup aşamasına ayrılmış).
 * - ÇİFT ELEME: üst kol (WB) round 0(ön tur)/1..R pozitif; alt kol (LB) AYRI
 *   bir tek-eleme turnuvası gibi ele alınır ve round numaraları NEGATİF
 *   saklanır (-1, -2, ...) çakışmayı önlemek için; büyük final
 *   `DOUBLE_GRAND_FINAL_ROUND`, bracket reset `DOUBLE_BRACKET_RESET_ROUND`.
 *   ⚠️ BİLİNÇLİ SADELEŞTİRME: bu motor WB'yi TAMAMEN bitirmeden LB'yi
 *   başlatmaz (geleneksel çift-eleme turları iç içe geçer, bu motor
 *   SIRALI çalışır). LB katılımcıları "WB'de ne kadar ileri gittiği" seed'iyle
 *   kendi aralarında ayrı bir tek-eleme turnuvası oynar. Sonuç (kimin
 *   şampiyon olduğu, bracket reset kuralı) doğru üretilir; yalnız geleneksel
 *   çift-eleme ekranındaki "WB ve LB aynı anda ilerliyor" görünümü bu motorda
 *   yok — WB bitmeden LB maçı ÜRETİLMEZ.
 *
 * POZİSYON KURALI: pozisyon, takımın KRONOLOJİK ELENME SIRASINDAN türetilir
 * (elendiği tur ne kadar geç ise pozisyon o kadar iyi). Bu, ayrı iki formülle
 * (aşağıdan azalan + yukarıdan artan) pozisyon hesaplayıp ÇAKIŞTIRAN eski
 * koddan farklı olarak, TEK bir toplam sıralama (permütasyon) üretir — yapısı
 * gereği çakışma OLAMAZ. Aynı turda elenenler kendi aralarında seed'e göre
 * sıralanır (düşük seed/güçlü seed → iyi pozisyon).
 *
 * BERABERLİK: elemede beraberlik olmaz. `winnerId == null` gelirse
 * (kullanıcı/motor beraberlik kaydetmişse) yüksek seed (küçük seed sayısı)
 * deterministik olarak galip sayılır (`effectiveWinner`).
 */
object EliminationSystem {

    const val METHOD = "ELIMINATION"

    enum class Mode { SINGLE, DOUBLE, GROUP_THEN_KNOCKOUT }

    data class BracketSlot(val songId: Long?, val seed: Int, val isBye: Boolean)

    data class EliminationState(
        val mode: Mode,
        val currentRound: Int,
        val totalRounds: Int,
        val aliveIds: List<Long>,
        val eliminatedByRound: Map<Int, List<Long>>,
        val isComplete: Boolean,
        val championId: Long?
    )

    data class RoundResult(
        val matches: List<Match>,
        val canContinue: Boolean,
        val reason: String
    )

    private const val DOUBLE_GRAND_FINAL_ROUND = 1000
    private const val DOUBLE_BRACKET_RESET_ROUND = 1001

    // ─────────────────────────────────────────────────────────────────
    // GENEL API — mod'a göre dispatch
    // ─────────────────────────────────────────────────────────────────

    fun computeState(songs: List<Song>, completed: List<Match>, mode: Mode): EliminationState = when (mode) {
        Mode.SINGLE -> computeSingleState(songs, completed)
        Mode.GROUP_THEN_KNOCKOUT -> computeGroupState(songs, completed)
        Mode.DOUBLE -> computeDoubleState(songs, completed)
    }

    fun createNextRound(songs: List<Song>, completed: List<Match>, mode: Mode): RoundResult = when (mode) {
        Mode.SINGLE -> createSingleNextRound(songs, completed)
        Mode.GROUP_THEN_KNOCKOUT -> createGroupNextRound(songs, completed)
        Mode.DOUBLE -> createDoubleNextRound(songs, completed)
    }

    fun calculateResults(songs: List<Song>, completed: List<Match>, mode: Mode): List<RankingResult> = when (mode) {
        Mode.SINGLE -> calculateSingleResults(songs, completed)
        Mode.GROUP_THEN_KNOCKOUT -> calculateGroupResults(songs, completed)
        Mode.DOUBLE -> calculateDoubleResults(songs, completed)
    }

    /** UI'nin fikstür ağacı çizebilmesi için tur tur yapı: her round bir düz `BracketSlot`
     * listesi, ardışık ikili (0,1)=maç1, (2,3)=maç2... şeklinde okunur. */
    fun bracketStructure(songs: List<Song>, completed: List<Match>, mode: Mode): List<List<BracketSlot>> = when (mode) {
        Mode.SINGLE -> singleBracketStructure(songs, completed)
        Mode.GROUP_THEN_KNOCKOUT -> groupBracketStructure(songs, completed)
        Mode.DOUBLE -> doubleBracketStructure(songs, completed)
    }

    // ─────────────────────────────────────────────────────────────────
    // ORTAK YARDIMCILAR
    // ─────────────────────────────────────────────────────────────────

    /** song.id artan sırasına göre 1..n seed. shuffled() YOK — replay'i kırar. */
    private fun seeds(songs: List<Song>): Map<Long, Int> =
        songs.sortedBy { it.id }.mapIndexed { i, s -> s.id to (i + 1) }.toMap()

    /** Elemede beraberlik olmaz: winnerId null gelirse yüksek seed (küçük sayı) geçer. */
    private fun effectiveWinner(match: Match, seedOf: Map<Long, Int>): Long {
        match.winnerId?.let { return it }
        val seed1 = seedOf[match.songId1] ?: Int.MAX_VALUE
        val seed2 = seedOf[match.songId2] ?: Int.MAX_VALUE
        return if (seed1 <= seed2) match.songId1 else match.songId2
    }

    /** Seed sırasına göre "en güçlü en zayıfla" eşleştirir: [1,2,3,4] -> (1,4),(2,3). */
    private fun reseedPairs(aliveBySeedAsc: List<Long>): List<Pair<Long, Long>> {
        val n = aliveBySeedAsc.size
        return (0 until n / 2).map { i -> aliveBySeedAsc[i] to aliveBySeedAsc[n - 1 - i] }
    }

    private fun roundTitle(teamsEnteringRound: Int): String = when (teamsEnteringRound) {
        2 -> "Final"
        4 -> "Yarı Final"
        8 -> "Çeyrek Final"
        16 -> "Son 16"
        32 -> "Son 32"
        64 -> "Son 64"
        else -> "Ön Tur"
    }

    // ─────────────────────────────────────────────────────────────────
    // ÇEKİRDEK TEK-ELEME MOTORU — SINGLE ve GROUP'un knockout aşaması,
    // hatta DOUBLE'ın WB/LB kolları BUNU tekrar kullanır.
    // ─────────────────────────────────────────────────────────────────

    private data class KoPlan(val n: Int, val targetSize: Int, val playInPairs: Int, val totalRounds: Int)

    private fun koPlan(n: Int): KoPlan {
        if (n <= 1) return KoPlan(n, n, 0, 0)
        val targetSize = 2.0.pow(floor(log2(n.toDouble()))).toInt()
        val playInPairs = n - targetSize
        val totalRounds = if (targetSize <= 1) 0 else log2(targetSize.toDouble()).roundToInt()
        return KoPlan(n, targetSize, playInPairs, totalRounds)
    }

    private data class KoRoundInfo(val roundLabel: Int, val title: String, val matches: List<Match>)

    private data class KoReplay(
        val plan: KoPlan,
        val rounds: List<KoRoundInfo>,
        val aliveSeeded: List<Long>,
        val eliminationOrder: List<Long>,
        val currentRoundLabel: Int,
        val isComplete: Boolean,
        val championId: Long?,
        val nextRoundMatches: List<Match>,
        val canContinue: Boolean,
        val reason: String
    )

    /**
     * Tur tur tek-eleme motoru. `seedOrder` katılımcıların seed 1..n sırasıyla
     * songId listesidir (çağıran taraf seed kaynağını belirler — global song.id
     * sırası ya da grup performansı). Yalnız `groupId == groupIdTag && round >=
     * baseRound` olan `completed` kayıtlarını bu aşamaya ait sayar.
     */
    private fun knockoutReplay(
        listId: Long,
        seedOrder: List<Long>,
        completed: List<Match>,
        baseRound: Int,
        seedOf: Map<Long, Int>,
        groupIdTag: Int? = null
    ): KoReplay {
        val n = seedOrder.size
        val plan = koPlan(n)
        if (n == 0) {
            return KoReplay(plan, emptyList(), emptyList(), emptyList(), baseRound, true, null, emptyList(), false, "Katılımcı yok")
        }
        if (n == 1) {
            return KoReplay(plan, emptyList(), seedOrder, emptyList(), baseRound, true, seedOrder[0], emptyList(), false, "Tek katılımcı, otomatik şampiyon")
        }

        val completedHere = completed.filter { it.groupId == groupIdTag && it.round >= baseRound }
        val completedByRound = completedHere.groupBy { it.round }

        val roundsInfo = mutableListOf<KoRoundInfo>()
        val eliminationOrder = mutableListOf<Long>()
        var alive = seedOrder.sortedBy { seedOf[it] ?: Int.MAX_VALUE }

        // ── Ön tur (yalnız plan.playInPairs > 0 ise var) ──
        if (plan.playInPairs > 0) {
            val playInRoundLabel = baseRound
            val bottom = alive.takeLast(plan.playInPairs * 2)
            val top = alive.dropLast(plan.playInPairs * 2)
            val pairs = reseedPairs(bottom)
            val playedRaw = completedByRound[playInRoundLabel].orEmpty().sortedBy { it.matchNumber }

            if (playedRaw.size < pairs.size) {
                val fresh = pairs.mapIndexed { idx, (a, b) ->
                    Match(listId = listId, rankingMethod = METHOD, songId1 = a, songId2 = b,
                        winnerId = null, round = playInRoundLabel, groupId = groupIdTag, matchNumber = idx + 1)
                }
                return KoReplay(plan, roundsInfo, alive, eliminationOrder, playInRoundLabel, false, null,
                    fresh, true, "Ön tur oynanmayı bekliyor")
            }

            val winners = mutableListOf<Long>()
            val losers = mutableListOf<Pair<Long, Int>>()
            playedRaw.forEach { m ->
                val w = effectiveWinner(m, seedOf)
                val l = if (w == m.songId1) m.songId2 else m.songId1
                winners += w
                losers += l to (seedOf[l] ?: Int.MAX_VALUE)
            }
            eliminationOrder += losers.sortedByDescending { it.second }.map { it.first }
            roundsInfo += KoRoundInfo(playInRoundLabel, "Ön Tur", playedRaw)
            alive = (top + winners).sortedBy { seedOf[it] ?: Int.MAX_VALUE }
        }

        // ── Ana braket: round baseRound+1 .. baseRound+totalRounds ──
        for (r in 1..plan.totalRounds) {
            val roundLabel = baseRound + r
            if (alive.size <= 1) break
            val teamsEntering = alive.size
            val pairs = reseedPairs(alive)
            val playedRaw = completedByRound[roundLabel].orEmpty().sortedBy { it.matchNumber }

            if (playedRaw.size < pairs.size) {
                val fresh = pairs.mapIndexed { idx, (a, b) ->
                    Match(listId = listId, rankingMethod = METHOD, songId1 = a, songId2 = b,
                        winnerId = null, round = roundLabel, groupId = groupIdTag, matchNumber = idx + 1)
                }
                return KoReplay(plan, roundsInfo, alive, eliminationOrder, roundLabel, false, null,
                    fresh, true, "${roundTitle(teamsEntering)} oynanmayı bekliyor")
            }

            val winners = mutableListOf<Long>()
            val losers = mutableListOf<Pair<Long, Int>>()
            playedRaw.forEach { m ->
                val w = effectiveWinner(m, seedOf)
                val l = if (w == m.songId1) m.songId2 else m.songId1
                winners += w
                losers += l to (seedOf[l] ?: Int.MAX_VALUE)
            }
            eliminationOrder += losers.sortedByDescending { it.second }.map { it.first }
            roundsInfo += KoRoundInfo(roundLabel, roundTitle(teamsEntering), playedRaw)
            alive = winners.sortedBy { seedOf[it] ?: Int.MAX_VALUE }
        }

        return if (alive.size == 1) {
            KoReplay(plan, roundsInfo, alive, eliminationOrder, baseRound + plan.totalRounds, true, alive[0],
                emptyList(), false, "Turnuva tamamlandı")
        } else {
            // Buraya normalde düşülmez (döngü zaten üretilmemiş bir turda return eder) — güvenlik ağı.
            KoReplay(plan, roundsInfo, alive, eliminationOrder, baseRound + plan.totalRounds, false, null,
                emptyList(), false, "Beklenmeyen durum: ${alive.size} takım kaldı ama sıradaki tur üretilmedi")
        }
    }

    private fun eliminatedByRoundOf(replay: KoReplay, seedOf: Map<Long, Int>): Map<Int, List<Long>> =
        replay.rounds.associate { info ->
            info.roundLabel to info.matches.map { m ->
                val w = effectiveWinner(m, seedOf)
                if (w == m.songId1) m.songId2 else m.songId1
            }
        }

    private fun bracketSlotsOf(replay: KoReplay, seedOf: Map<Long, Int>): List<List<BracketSlot>> {
        val allRounds = replay.rounds + if (replay.nextRoundMatches.isNotEmpty())
            listOf(KoRoundInfo(replay.currentRoundLabel, roundTitle(replay.aliveSeeded.size), replay.nextRoundMatches))
        else emptyList()
        return allRounds.map { info ->
            info.matches.flatMap { m ->
                listOf(
                    BracketSlot(m.songId1, seedOf[m.songId1] ?: 0, isBye = false),
                    BracketSlot(m.songId2, seedOf[m.songId2] ?: 0, isBye = false)
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // ① TEK ELEME
    // ─────────────────────────────────────────────────────────────────

    private fun computeSingleState(songs: List<Song>, completed: List<Match>): EliminationState {
        if (songs.isEmpty()) return EliminationState(Mode.SINGLE, 0, 0, emptyList(), emptyMap(), true, null)
        val seedOf = seeds(songs)
        val order = songs.sortedBy { it.id }.map { it.id }
        val replay = knockoutReplay(songs[0].listId, order, completed, baseRound = 0, seedOf = seedOf)
        return EliminationState(
            mode = Mode.SINGLE,
            currentRound = replay.currentRoundLabel,
            totalRounds = replay.plan.totalRounds + if (replay.plan.playInPairs > 0) 1 else 0,
            aliveIds = replay.aliveSeeded,
            eliminatedByRound = eliminatedByRoundOf(replay, seedOf),
            isComplete = replay.isComplete,
            championId = replay.championId
        )
    }

    private fun createSingleNextRound(songs: List<Song>, completed: List<Match>): RoundResult {
        if (songs.isEmpty()) return RoundResult(emptyList(), false, "Katılımcı yok")
        if (songs.size == 1) return RoundResult(emptyList(), false, "Tek katılımcı, maç gerekmez")
        val seedOf = seeds(songs)
        val order = songs.sortedBy { it.id }.map { it.id }
        val replay = knockoutReplay(songs[0].listId, order, completed, baseRound = 0, seedOf = seedOf)
        return RoundResult(replay.nextRoundMatches, replay.canContinue, replay.reason)
    }

    private fun calculateSingleResults(songs: List<Song>, completed: List<Match>): List<RankingResult> {
        if (songs.isEmpty()) return emptyList()
        if (songs.size == 1) {
            return listOf(RankingResult(songId = songs[0].id, listId = songs[0].listId, rankingMethod = METHOD, score = 1.0, position = 1))
        }
        val seedOf = seeds(songs)
        val order = songs.sortedBy { it.id }.map { it.id }
        val replay = knockoutReplay(songs[0].listId, order, completed, baseRound = 0, seedOf = seedOf)
        val ranked = (replay.championId?.let { listOf(it) } ?: emptyList()) + replay.eliminationOrder.reversed()
        val remaining = order.filterNot { it in ranked }.sortedBy { seedOf[it] ?: Int.MAX_VALUE }
        val fullOrder = ranked + remaining
        return fullOrder.mapIndexed { idx, id ->
            RankingResult(songId = id, listId = songs[0].listId, rankingMethod = METHOD,
                score = (fullOrder.size - idx).toDouble(), position = idx + 1)
        }
    }

    private fun singleBracketStructure(songs: List<Song>, completed: List<Match>): List<List<BracketSlot>> {
        if (songs.size < 2) return emptyList()
        val seedOf = seeds(songs)
        val order = songs.sortedBy { it.id }.map { it.id }
        val replay = knockoutReplay(songs[0].listId, order, completed, baseRound = 0, seedOf = seedOf)
        return bracketSlotsOf(replay, seedOf)
    }

    // ─────────────────────────────────────────────────────────────────
    // ③ GRUP + ELEME
    // ─────────────────────────────────────────────────────────────────

    private data class GroupPlan(val groupCount: Int, val advancesPerGroup: Int)

    /** Grup sayısı deterministik: hedef grup büyüklüğü 3-6 aralığı, en az 2 grup. */
    private fun groupPlan(n: Int): GroupPlan {
        if (n < 4) return GroupPlan(groupCount = 1, advancesPerGroup = minOf(2, n))
        var groupCount = maxOf(2, ceil(n / 5.0).toInt())
        while (groupCount > 1 && n / groupCount < 3) groupCount--
        return GroupPlan(groupCount, advancesPerGroup = 2)
    }

    /** Yılan (snake) dağıtımı: grup üyeliği song.id sırasına göre deterministik, shuffled() YOK. */
    private fun snakeGroups(seedOrder: List<Long>, groupCount: Int): List<List<Long>> {
        val groups = List(groupCount) { mutableListOf<Long>() }
        var idx = 0
        var forward = true
        while (idx < seedOrder.size) {
            val range = if (forward) 0 until groupCount else (groupCount - 1) downTo 0
            for (g in range) {
                if (idx >= seedOrder.size) break
                groups[g].add(seedOrder[idx])
                idx++
            }
            forward = !forward
        }
        return groups
    }

    private class MutableStanding {
        var played = 0; var won = 0; var drawn = 0; var lost = 0
        var average = 0; var points = 0
    }

    /** Grup içi lig tablosu: 3/1/0 puan + averaj (CLAUDE.md LIG kuralıyla tutarlı), id/seed tiebreak. */
    private fun computeGroupStandings(members: List<Long>, groupMatches: List<Match>, seedOf: Map<Long, Int>): List<Long> {
        val stats = members.associateWith { MutableStanding() }
        groupMatches.filter { it.isCompleted }.forEach { m ->
            val s1 = stats.getValue(m.songId1)
            val s2 = stats.getValue(m.songId2)
            s1.played++; s2.played++
            val sc1 = m.score1 ?: 0
            val sc2 = m.score2 ?: 0
            s1.average += sc1 - sc2
            s2.average += sc2 - sc1
            when (effectiveWinner(m, seedOf)) {
                m.songId1 -> { s1.won++; s1.points += 3; s2.lost++ }
                else -> { s2.won++; s2.points += 3; s1.lost++ }
            }
        }
        return members.sortedWith(
            compareByDescending<Long> { stats.getValue(it).points }
                .thenByDescending { stats.getValue(it).average }
                .thenBy { seedOf[it] ?: Int.MAX_VALUE }
        )
    }

    private data class GroupPhase(
        val matches: List<Match>,
        val isComplete: Boolean,
        val qualifiers: List<Long>,
        val eliminatedInGroupStage: List<Long>
    )

    private fun groupPhaseReplay(songs: List<Song>, completed: List<Match>, seedOf: Map<Long, Int>): GroupPhase {
        val order = songs.sortedBy { it.id }.map { it.id }
        val n = order.size
        if (n <= 3) {
            // Çok küçük alan: gruplama anlamsız — TEK grup kabul edip herkesi knockout'a gönder.
            return GroupPhase(emptyList(), true, order, emptyList())
        }
        val plan = groupPlan(n)
        val groups = snakeGroups(order, plan.groupCount)
        val listId = songs[0].listId

        val allGroupMatches = mutableListOf<Match>()
        var matchNumber = 1
        groups.forEachIndexed { gi, members ->
            for (i in members.indices) for (j in i + 1 until members.size) {
                allGroupMatches += Match(
                    listId = listId, rankingMethod = METHOD, songId1 = members[i], songId2 = members[j],
                    winnerId = null, round = 0, groupId = gi, matchNumber = matchNumber++
                )
            }
        }

        val playedByGroup = completed.filter { it.round == 0 && it.groupId != null }.groupBy { it.groupId }
        val totalPlayed = playedByGroup.values.sumOf { it.size }
        if (totalPlayed < allGroupMatches.size) {
            return GroupPhase(allGroupMatches, false, emptyList(), emptyList())
        }

        val standingsPerGroup = groups.mapIndexed { gi, members ->
            computeGroupStandings(members, playedByGroup[gi].orEmpty(), seedOf)
        }
        val qualifiers = mutableListOf<Long>()
        for (rank in 0 until plan.advancesPerGroup) {
            standingsPerGroup.forEach { standing -> if (rank < standing.size) qualifiers += standing[rank] }
        }
        val eliminated = mutableListOf<Long>()
        standingsPerGroup.forEach { standing -> eliminated += standing.drop(plan.advancesPerGroup) }

        return GroupPhase(allGroupMatches, true, qualifiers, eliminated)
    }

    private fun knockoutSeeds(qualifiers: List<Long>): Map<Long, Int> =
        qualifiers.mapIndexed { i, id -> id to (i + 1) }.toMap()

    private fun computeGroupState(songs: List<Song>, completed: List<Match>): EliminationState {
        if (songs.isEmpty()) return EliminationState(Mode.GROUP_THEN_KNOCKOUT, 0, 0, emptyList(), emptyMap(), true, null)
        val seedOf = seeds(songs)
        val phase = groupPhaseReplay(songs, completed, seedOf)
        if (!phase.isComplete) {
            // totalRounds henüz bilinmiyor (knockout boyutu qualifiers sayısına bağlı) -> -1 sentineli.
            return EliminationState(Mode.GROUP_THEN_KNOCKOUT, 0, -1, songs.map { it.id }, emptyMap(), false, null)
        }
        val koSeedOf = knockoutSeeds(phase.qualifiers)
        val replay = knockoutReplay(songs[0].listId, phase.qualifiers, completed, baseRound = 1, seedOf = koSeedOf)
        val eliminatedByRound = mutableMapOf<Int, List<Long>>()
        eliminatedByRound[0] = phase.eliminatedInGroupStage
        eliminatedByRound.putAll(eliminatedByRoundOf(replay, koSeedOf))
        return EliminationState(
            mode = Mode.GROUP_THEN_KNOCKOUT,
            currentRound = replay.currentRoundLabel,
            totalRounds = 1 + replay.plan.totalRounds + if (replay.plan.playInPairs > 0) 1 else 0,
            aliveIds = replay.aliveSeeded,
            eliminatedByRound = eliminatedByRound,
            isComplete = replay.isComplete,
            championId = replay.championId
        )
    }

    private fun createGroupNextRound(songs: List<Song>, completed: List<Match>): RoundResult {
        if (songs.size <= 1) return RoundResult(emptyList(), false, "Yetersiz katılımcı")
        val seedOf = seeds(songs)
        val phase = groupPhaseReplay(songs, completed, seedOf)
        if (!phase.isComplete) {
            val played = completed.filter { it.round == 0 && it.groupId != null }
                .map { Triple(it.groupId, it.songId1, it.songId2) }.toSet()
            val pending = phase.matches.filterNot { Triple(it.groupId, it.songId1, it.songId2) in played }
            val playedCount = phase.matches.size - pending.size
            return RoundResult(pending, true, "Grup aşaması oynanmayı bekliyor ($playedCount/${phase.matches.size} tamam)")
        }
        val koSeedOf = knockoutSeeds(phase.qualifiers)
        val replay = knockoutReplay(songs[0].listId, phase.qualifiers, completed, baseRound = 1, seedOf = koSeedOf)
        return RoundResult(replay.nextRoundMatches, replay.canContinue, replay.reason)
    }

    private fun calculateGroupResults(songs: List<Song>, completed: List<Match>): List<RankingResult> {
        if (songs.isEmpty()) return emptyList()
        val seedOf = seeds(songs)
        val phase = groupPhaseReplay(songs, completed, seedOf)
        if (!phase.isComplete) {
            return songs.sortedBy { it.id }.mapIndexed { idx, s ->
                RankingResult(songId = s.id, listId = s.listId, rankingMethod = METHOD,
                    score = (songs.size - idx).toDouble(), position = idx + 1)
            }
        }
        val koSeedOf = knockoutSeeds(phase.qualifiers)
        val replay = knockoutReplay(songs[0].listId, phase.qualifiers, completed, baseRound = 1, seedOf = koSeedOf)
        val knockoutRanked = (replay.championId?.let { listOf(it) } ?: emptyList()) + replay.eliminationOrder.reversed()
        val remainingQualifiers = phase.qualifiers.filterNot { it in knockoutRanked }.sortedBy { koSeedOf[it] ?: Int.MAX_VALUE }
        val fullOrder = knockoutRanked + remainingQualifiers + phase.eliminatedInGroupStage
        return fullOrder.mapIndexed { idx, id ->
            RankingResult(songId = id, listId = songs[0].listId, rankingMethod = METHOD,
                score = (fullOrder.size - idx).toDouble(), position = idx + 1)
        }
    }

    private fun groupBracketStructure(songs: List<Song>, completed: List<Match>): List<List<BracketSlot>> {
        if (songs.isEmpty()) return emptyList()
        val seedOf = seeds(songs)
        val phase = groupPhaseReplay(songs, completed, seedOf)
        if (!phase.isComplete) return emptyList()
        val koSeedOf = knockoutSeeds(phase.qualifiers)
        val replay = knockoutReplay(songs[0].listId, phase.qualifiers, completed, baseRound = 1, seedOf = koSeedOf)
        return bracketSlotsOf(replay, koSeedOf)
    }

    // ─────────────────────────────────────────────────────────────────
    // ② ÇİFT ELEME — bkz. dosya başındaki "BİLİNÇLİ SADELEŞTİRME" notu
    // ─────────────────────────────────────────────────────────────────

    private data class DoubleReplay(
        val championId: Long?,
        val finalOrder: List<Long>,     // TAMAMLANDIYSA: pozisyon 1'den n'e sıralı songId listesi
        val pendingMatches: List<Match>,
        val canContinue: Boolean,
        val reason: String,
        val isComplete: Boolean,
        val currentRoundLabel: Int,
        val rounds: List<KoRoundInfo>   // WB + LB + final(ler), görüntüleme sırasıyla
    )

    private fun doubleReplay(songs: List<Song>, completed: List<Match>): DoubleReplay {
        val n = songs.size
        if (n == 0) return DoubleReplay(null, emptyList(), emptyList(), false, "Katılımcı yok", true, 0, emptyList())
        if (n == 1) return DoubleReplay(songs[0].id, listOf(songs[0].id), emptyList(), false, "Tek katılımcı", true, 0, emptyList())

        val listId = songs[0].listId
        val seedOf = seeds(songs)
        val order = songs.sortedBy { it.id }.map { it.id }

        // ── Üst kol (WB): round >= 0 olan kayıtlar ──
        val wbCompleted = completed.filter { it.round in 0..(DOUBLE_GRAND_FINAL_ROUND - 1) }
        val wb = knockoutReplay(listId, order, wbCompleted, baseRound = 0, seedOf = seedOf)
        if (!wb.isComplete) {
            return DoubleReplay(null, emptyList(), wb.nextRoundMatches, wb.canContinue, "Üst kol: ${wb.reason}", false, wb.currentRoundLabel, wb.rounds)
        }
        val wbChampion = wb.championId!!

        // ── Alt kol (LB): WB'nin tüm kaybedenleri arasında AYRI bir tek-eleme turnuvası.
        // Seed = "WB'de ne kadar ileri gitti" (geç kaybeden = güçlü LB seed). Round'lar
        // negatif saklanır (-1,-2,...), knockoutReplay'i tekrar kullanmak için burada
        // pozitife çevrilip tekrar negatife çevrilir. ──
        // baseRound=1 (0 DEĞİL): LB'nin en düşük turu negatiflendiğinde -1 olsun, WB'nin
        // round 0'ıyla (WB'nin kendi ön turu, varsa) ÇAKIŞMASIN (-0 == 0 olurdu).
        val lbOrder = wb.eliminationOrder.reversed()
        val lbSeedOf = lbOrder.mapIndexed { i, id -> id to (i + 1) }.toMap()
        val lbCompletedPositive = completed.filter { it.round < 0 }.map { it.copy(round = -it.round) }
        val lb = knockoutReplay(listId, lbOrder, lbCompletedPositive, baseRound = 1, seedOf = lbSeedOf)
        if (!lb.isComplete) {
            val negated = lb.nextRoundMatches.map { it.copy(round = -it.round) }
            val lbRoundsNegated = lb.rounds.map { info -> KoRoundInfo(-info.roundLabel, "Alt Kol: ${info.title}", info.matches.map { it.copy(round = -it.round) }) }
            return DoubleReplay(null, emptyList(), negated, lb.canContinue, "Alt kol: ${lb.reason}", false, -lb.currentRoundLabel, wb.rounds + lbRoundsNegated)
        }
        val lbChampion = lb.championId!!
        val lbRoundsNegated = lb.rounds.map { info -> KoRoundInfo(-info.roundLabel, "Alt Kol: ${info.title}", info.matches.map { it.copy(round = -it.round) }) }

        // ── Büyük final: WB galibi vs LB galibi ──
        val grandFinal = completed.filter { it.round == DOUBLE_GRAND_FINAL_ROUND }
        if (grandFinal.isEmpty()) {
            val gf = Match(listId = listId, rankingMethod = METHOD, songId1 = wbChampion, songId2 = lbChampion,
                winnerId = null, round = DOUBLE_GRAND_FINAL_ROUND, matchNumber = 1)
            return DoubleReplay(null, emptyList(), listOf(gf), true, "Büyük final oynanmayı bekliyor",
                false, DOUBLE_GRAND_FINAL_ROUND, wb.rounds + lbRoundsNegated)
        }
        val gfMatch = grandFinal.first()
        val gfWinner = effectiveWinner(gfMatch, seedOf)
        val gfLoser = if (gfWinner == wbChampion) lbChampion else wbChampion

        if (gfWinner == wbChampion) {
            // WB galibi büyük finali de kazandı: reset gerekmez, turnuva biter.
            val rest = lb.eliminationOrder.reversed()
            val finalOrder = listOf(wbChampion, gfLoser) + rest
            return DoubleReplay(wbChampion, finalOrder, emptyList(), false, "Turnuva tamamlandı",
                true, DOUBLE_GRAND_FINAL_ROUND, wb.rounds + lbRoundsNegated + KoRoundInfo(DOUBLE_GRAND_FINAL_ROUND, "Büyük Final", grandFinal))
        }

        // LB galibi büyük finali kazandı: BRACKET RESET — ikinci final oynanmalı.
        val reset = completed.filter { it.round == DOUBLE_BRACKET_RESET_ROUND }
        val gfRoundInfo = KoRoundInfo(DOUBLE_GRAND_FINAL_ROUND, "Büyük Final", grandFinal)
        if (reset.isEmpty()) {
            val resetMatch = Match(listId = listId, rankingMethod = METHOD, songId1 = wbChampion, songId2 = lbChampion,
                winnerId = null, round = DOUBLE_BRACKET_RESET_ROUND, matchNumber = 1)
            return DoubleReplay(null, emptyList(), listOf(resetMatch), true, "Bracket reset: ikinci final oynanmayı bekliyor",
                false, DOUBLE_BRACKET_RESET_ROUND, wb.rounds + lbRoundsNegated + gfRoundInfo)
        }
        val resetMatch = reset.first()
        val resetWinner = effectiveWinner(resetMatch, seedOf)
        val resetLoser = if (resetWinner == wbChampion) lbChampion else wbChampion
        val rest = lb.eliminationOrder.reversed()
        val finalOrder = listOf(resetWinner, resetLoser) + rest
        val resetRoundInfo = KoRoundInfo(DOUBLE_BRACKET_RESET_ROUND, "Bracket Reset — İkinci Final", reset)
        return DoubleReplay(resetWinner, finalOrder, emptyList(), false, "Turnuva tamamlandı (bracket reset)",
            true, DOUBLE_BRACKET_RESET_ROUND, wb.rounds + lbRoundsNegated + gfRoundInfo + resetRoundInfo)
    }

    private fun computeDoubleState(songs: List<Song>, completed: List<Match>): EliminationState {
        if (songs.isEmpty()) return EliminationState(Mode.DOUBLE, 0, 0, emptyList(), emptyMap(), true, null)
        val replay = doubleReplay(songs, completed)
        val alive = if (replay.isComplete) emptyList()
        else songs.map { it.id }.filterNot { id -> replay.rounds.any { r -> r.matches.any { it.isCompleted && (if (effectiveWinner(it, seeds(songs)) == it.songId1) it.songId2 else it.songId1) == id } } }
        return EliminationState(
            mode = Mode.DOUBLE,
            currentRound = replay.currentRoundLabel,
            // Bracket reset olasılığı yüzünden toplam tur sayısı önceden KESİN bilinmez -> -1 sentineli.
            totalRounds = -1,
            aliveIds = alive,
            // Basitleştirme: DOUBLE için tur-bazlı elenme haritası doldurulmadı (ilerleme dosyasında not var).
            eliminatedByRound = emptyMap(),
            isComplete = replay.isComplete,
            championId = replay.championId
        )
    }

    private fun createDoubleNextRound(songs: List<Song>, completed: List<Match>): RoundResult {
        if (songs.size <= 1) return RoundResult(emptyList(), false, "Yetersiz katılımcı")
        val replay = doubleReplay(songs, completed)
        return RoundResult(replay.pendingMatches, replay.canContinue, replay.reason)
    }

    private fun calculateDoubleResults(songs: List<Song>, completed: List<Match>): List<RankingResult> {
        if (songs.isEmpty()) return emptyList()
        if (songs.size == 1) {
            return listOf(RankingResult(songId = songs[0].id, listId = songs[0].listId, rankingMethod = METHOD, score = 1.0, position = 1))
        }
        val replay = doubleReplay(songs, completed)
        if (!replay.isComplete) {
            return songs.sortedBy { it.id }.mapIndexed { idx, s ->
                RankingResult(songId = s.id, listId = s.listId, rankingMethod = METHOD,
                    score = (songs.size - idx).toDouble(), position = idx + 1)
            }
        }
        return replay.finalOrder.mapIndexed { idx, id ->
            RankingResult(songId = id, listId = songs[0].listId, rankingMethod = METHOD,
                score = (replay.finalOrder.size - idx).toDouble(), position = idx + 1)
        }
    }

    private fun doubleBracketStructure(songs: List<Song>, completed: List<Match>): List<List<BracketSlot>> {
        if (songs.size < 2) return emptyList()
        val seedOf = seeds(songs)
        val replay = doubleReplay(songs, completed)
        val allRounds = replay.rounds + if (replay.pendingMatches.isNotEmpty())
            listOf(KoRoundInfo(replay.currentRoundLabel, "Sıradaki", replay.pendingMatches))
        else emptyList()
        return allRounds.map { info ->
            info.matches.flatMap { m ->
                listOf(
                    BracketSlot(m.songId1, seedOf[m.songId1] ?: 0, isBye = false),
                    BracketSlot(m.songId2, seedOf[m.songId2] ?: 0, isBye = false)
                )
            }
        }
    }
}
