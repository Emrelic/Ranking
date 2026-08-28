package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.EmreSystemCorrect
import org.junit.Assert.*
import org.junit.Test

/**
 * EMRE USULÜ — DERİN KUSUR ARAMA TESTLERİ
 *
 * Amaç test yazmak değil, motoru KIRMAK. Her test CLAUDE.md'deki bir kuralı
 * ya da bilinen bir çökme riskini hedefler:
 *  - iki takım yalnız bir kez eşleşir (kırmızı çizgi)
 *  - her turda tam eşleştirme, kimse turdan düşmez
 *  - bye rotasyonu adil (kimse ikinci byeyi herkes almadan almaz)
 *  - puan: galibiyet 1 · beraberlik 0.5 · bye 1
 *  - tiebreaker zinciri TÜM maç geçmişine bakar
 *  - geçişlilik: aynı puanlı büyük grupta döngüsel sonuçlar sortedWith'i çökertiyor mu
 *  - determinizm ve n=64 başarımı
 *
 * NOT: Kırılan test SUSTURULMAZ. Kırık kalır ve rapora sayıyla girer.
 */
class EmreSystemDeepTest {

    // ==========================================================
    // YARDIMCILAR
    // ==========================================================

    private fun makeSongs(count: Int): List<Song> =
        (1..count).map { i -> Song(id = i.toLong(), name = "Takim$i", listId = 1L) }

    /** Tek turun özeti — doğrulamalar bunun üzerinden yürür. */
    private data class RoundSnapshot(
        val round: Int,
        val matches: List<Match>,
        val byeTeamId: Long?,
        val matchNumbers: List<Int>
    )

    private data class Simulation(
        val finalState: EmreSystemCorrect.EmreState,
        val rounds: List<RoundSnapshot>,
        val allMatches: List<Match>
    )

    /**
     * Deterministik turnuva simülasyonu (replay deseni: rastgelelik yok).
     * winnerPicker maç başına kazanan songId döndürür; null = beraberlik.
     */
    private fun simulate(
        songCount: Int,
        maxRounds: Int = 200,
        winnerPicker: (Match) -> Long?
    ): Simulation {
        var state = EmreSystemCorrect.initializeEmreTournament(makeSongs(songCount))
        val rounds = mutableListOf<RoundSnapshot>()
        val allMatches = mutableListOf<Match>()
        var nextId = 1L

        while (rounds.size < maxRounds) {
            val pairing = EmreSystemCorrect.createHybridPairingSystem(state)
            if (!pairing.canContinue || pairing.matches.isEmpty()) break

            val completed = pairing.matches.map { m ->
                m.copy(id = nextId++, winnerId = winnerPicker(m), isCompleted = true)
            }
            rounds.add(
                RoundSnapshot(
                    round = state.currentRound,
                    matches = completed,
                    byeTeamId = pairing.byeTeam?.id,
                    matchNumbers = pairing.matches.map { it.matchNumber }
                )
            )
            allMatches.addAll(completed)
            state = EmreSystemCorrect.processRoundResults(state, completed, pairing.byeTeam, allMatches.toList())
        }
        return Simulation(state, rounds, allMatches)
    }

    /** Küçük id her zaman kazanır (deterministik, gerçekçi "güçlü favori" senaryosu). */
    private val lowerIdWins: (Match) -> Long? = { m -> minOf(m.songId1, m.songId2) }

    /** Deterministik ama düzensiz sonuç üreticisi — rastgelelik YOK, saf aritmetik. */
    private val mixedResults: (Match) -> Long? = { m ->
        when ((m.songId1 * 7 + m.songId2 * 13 + m.round * 3) % 5) {
            0L, 1L -> m.songId1
            2L, 3L -> m.songId2
            else -> null // beraberlik
        }
    }

    private fun normalize(a: Long, b: Long) = if (a < b) Pair(a, b) else Pair(b, a)

    private fun match(id: Long, s1: Long, s2: Long, winner: Long?, round: Int = 1) = Match(
        id = id,
        listId = 1L,
        rankingMethod = "EMRE_CORRECT",
        songId1 = s1,
        songId2 = s2,
        winnerId = winner,
        round = round,
        matchNumber = id.toInt(),
        isCompleted = true
    )

    // ==========================================================
    // ① KIRMIZI ÇİZGİ — iki takım yalnız bir kez eşleşir
    // ==========================================================

    private fun assertNoRepeatPairing(n: Int, picker: (Match) -> Long?) {
        val sim = simulate(n, winnerPicker = picker)
        assertTrue("n=$n turnuva hiç tur oynamadı", sim.rounds.isNotEmpty())

        val seen = mutableSetOf<Pair<Long, Long>>()
        sim.allMatches.forEach { m ->
            val pair = normalize(m.songId1, m.songId2)
            assertTrue(
                "KIRMIZI ÇİZGİ İHLALİ (n=$n): ${pair.first}-${pair.second} ikilisi ikinci kez eşleşti " +
                    "(tur ${m.round})",
                seen.add(pair)
            )
        }
    }

    @Test
    fun kirmiziCizgi_tekrarEslesmeYok_n8() = assertNoRepeatPairing(8, lowerIdWins)

    @Test
    fun kirmiziCizgi_tekrarEslesmeYok_n16() = assertNoRepeatPairing(16, lowerIdWins)

    @Test
    fun kirmiziCizgi_tekrarEslesmeYok_n32() = assertNoRepeatPairing(32, lowerIdWins)

    @Test
    fun kirmiziCizgi_tekrarEslesmeYok_n16_karisikSonuclar() = assertNoRepeatPairing(16, mixedResults)

    @Test
    fun kirmiziCizgi_tekrarEslesmeYok_n15_tekSayi() = assertNoRepeatPairing(15, mixedResults)

    @Test
    fun kirmiziCizgi_tekrarEslesmeYok_n64() = assertNoRepeatPairing(64, mixedResults)

    /** Herkes berabere: en zorlayıcı senaryo, puanlar hep eşit kalır. */
    @Test
    fun kirmiziCizgi_tekrarEslesmeYok_hepsiBerabere_n16() = assertNoRepeatPairing(16) { null }

    // ==========================================================
    // ② TAM EŞLEŞTİRME — kimse turdan düşmez
    // ==========================================================

    private fun assertFullPairing(n: Int, picker: (Match) -> Long?) {
        val sim = simulate(n, winnerPicker = picker)
        val allIds = (1..n).map { it.toLong() }.toSet()

        sim.rounds.forEach { r ->
            val participants = r.matches.flatMap { listOf(it.songId1, it.songId2) }
            assertEquals(
                "n=$n tur ${r.round}: bir takım aynı turda iki maçta",
                participants.size, participants.toSet().size
            )

            val expectedMatches = if (n % 2 == 0) n / 2 else (n - 1) / 2
            assertEquals(
                "n=$n tur ${r.round}: maç sayısı yanlış (beklenen $expectedMatches)",
                expectedMatches, r.matches.size
            )

            if (n % 2 == 0) {
                assertNull("n=$n tur ${r.round}: çift takımda bye olamaz", r.byeTeamId)
            } else {
                assertNotNull("n=$n tur ${r.round}: tek takımda bye olmalı", r.byeTeamId)
            }

            val covered = participants.toMutableSet()
            r.byeTeamId?.let { covered.add(it) }
            assertEquals(
                "n=$n tur ${r.round}: TURDAN DÜŞEN TAKIM VAR — kapsanmayan: ${allIds - covered}",
                allIds, covered
            )
            r.byeTeamId?.let { bye ->
                assertFalse(
                    "n=$n tur ${r.round}: bye geçen takım aynı turda maç da oynadı",
                    bye in participants
                )
            }
        }
    }

    @Test
    fun tamEslestirme_cift_n8() = assertFullPairing(8, lowerIdWins)

    @Test
    fun tamEslestirme_cift_n16() = assertFullPairing(16, mixedResults)

    @Test
    fun tamEslestirme_tek_n7() = assertFullPairing(7, lowerIdWins)

    @Test
    fun tamEslestirme_tek_n9() = assertFullPairing(9, mixedResults)

    @Test
    fun tamEslestirme_tek_n33() = assertFullPairing(33, mixedResults)

    // ==========================================================
    // ③ BYE ROTASYONU
    // ==========================================================

    @Test
    fun bye_ilkTurdaEnAlttakiTakimaGider() {
        val state = EmreSystemCorrect.initializeEmreTournament(makeSongs(7))
        val pairing = EmreSystemCorrect.createHybridPairingSystem(state)
        assertEquals("İlk turda bye en alttaki (7 numaralı) takıma gitmeli", 7L, pairing.byeTeam?.id)
    }

    @Test
    fun bye_ayniTakimIkinciByeyiHerkesAlmadanALMAZ() {
        val n = 7
        val sim = simulate(n, winnerPicker = mixedResults)
        val byeCounts = mutableMapOf<Long, Int>()

        sim.rounds.forEach { r ->
            val bye = r.byeTeamId
            assertNotNull("tek sayıda takımda her turda bye olmalı (tur ${r.round})", bye)
            if (bye != null) {
                val newCount = (byeCounts[bye] ?: 0) + 1
                // İkinci byeyi almadan önce HERKESİN en az bir byesi olmalı
                if (newCount == 2) {
                    assertEquals(
                        "BYE ROTASYON İHLALİ: takım $bye ikinci byeyi aldı ama " +
                            "hâlâ bye geçmemiş takımlar var: " +
                            ((1..n).map { it.toLong() }.toSet() - byeCounts.keys),
                        n, byeCounts.size
                    )
                }
                byeCounts[bye] = newCount
            }
        }

        val min = byeCounts.values.minOrNull() ?: 0
        val max = byeCounts.values.maxOrNull() ?: 0
        assertTrue(
            "BYE DAĞILIMI ADİL DEĞİL: en az $min, en çok $max bye (dağılım: $byeCounts)",
            max - min <= 1
        )
    }

    @Test
    fun bye_ikinciTurdaByeGecmemisEnAlttakineGider() {
        var state = EmreSystemCorrect.initializeEmreTournament(makeSongs(5))
        val r1 = EmreSystemCorrect.createHybridPairingSystem(state)
        val bye1 = r1.byeTeam
        assertEquals(5L, bye1?.id)

        val completed = r1.matches.map { it.copy(id = it.matchNumber.toLong(), winnerId = it.songId1, isCompleted = true) }
        state = EmreSystemCorrect.processRoundResults(state, completed, bye1, completed)

        val r2 = EmreSystemCorrect.createHybridPairingSystem(state)
        assertNotEquals(
            "Aynı takım üst üste iki kez bye geçemez (bye geçmemiş takım varken)",
            5L, r2.byeTeam?.id
        )
        val byeTeam2 = state.teams.firstOrNull { it.id == r2.byeTeam?.id }
        assertNotNull("bye takımı durumda bulunmalı", byeTeam2)
        assertEquals(
            "Bye, bye geçmemiş takımların EN ALTTAKİNE gitmeli",
            state.teams.filter { !it.byePassed }.maxByOrNull { it.currentPosition }?.id,
            r2.byeTeam?.id
        )
    }

    // ==========================================================
    // ④ PUANLAMA — galibiyet 1 · beraberlik 0.5 · bye 1
    // ==========================================================

    @Test
    fun puan_galibiyetBeraberlikBye() {
        var state = EmreSystemCorrect.initializeEmreTournament(makeSongs(5))
        val r1 = EmreSystemCorrect.createHybridPairingSystem(state)
        // İlk maç kazananlı, ikinci maç berabere
        val completed = r1.matches.sortedBy { it.matchNumber }.mapIndexed { i, m ->
            m.copy(id = (i + 1).toLong(), winnerId = if (i == 0) m.songId1 else null, isCompleted = true)
        }
        state = EmreSystemCorrect.processRoundResults(state, completed, r1.byeTeam, completed)

        val winnerId = completed[0].songId1
        val loserId = completed[0].songId2
        val drawIds = listOf(completed[1].songId1, completed[1].songId2)
        val byeId = r1.byeTeam?.id

        fun pointsOf(id: Long?) = state.teams.firstOrNull { it.id == id }?.points

        assertEquals("Galibiyet 1 puan", 1.0, pointsOf(winnerId) ?: -1.0, 0.0001)
        assertEquals("Mağlubiyet 0 puan", 0.0, pointsOf(loserId) ?: -1.0, 0.0001)
        assertEquals("Beraberlik 0.5 puan", 0.5, pointsOf(drawIds[0]) ?: -1.0, 0.0001)
        assertEquals("Beraberlik 0.5 puan", 0.5, pointsOf(drawIds[1]) ?: -1.0, 0.0001)
        assertEquals("Bye 1 puan", 1.0, pointsOf(byeId) ?: -1.0, 0.0001)
    }

    @Test
    fun puan_turnuvaBoyuncaToplamTutariyor() {
        val n = 9
        val sim = simulate(n, winnerPicker = mixedResults)

        val expected = mutableMapOf<Long, Double>()
        (1..n).forEach { expected[it.toLong()] = 0.0 }
        sim.allMatches.forEach { m ->
            when (m.winnerId) {
                m.songId1 -> expected[m.songId1] = (expected[m.songId1] ?: 0.0) + 1.0
                m.songId2 -> expected[m.songId2] = (expected[m.songId2] ?: 0.0) + 1.0
                null -> {
                    expected[m.songId1] = (expected[m.songId1] ?: 0.0) + 0.5
                    expected[m.songId2] = (expected[m.songId2] ?: 0.0) + 0.5
                }
            }
        }
        sim.rounds.mapNotNull { it.byeTeamId }.forEach { expected[it] = (expected[it] ?: 0.0) + 1.0 }

        sim.finalState.teams.forEach { team ->
            assertEquals(
                "Takım ${team.id} puanı tutmuyor (beklenen ${expected[team.id]}, bulunan ${team.points})",
                expected[team.id] ?: -1.0, team.points, 0.0001
            )
        }
    }

    // ==========================================================
    // ⑤ TIEBREAKER ZİNCİRİ — tüm maç geçmişine bakmalı
    // ==========================================================

    /** Puanlar eşit kalsın diye bu tur maçı YOK; geçmiş yalnız allCompletedMatches'ten geliyor. */
    private fun reorderWithHistory(teamCount: Int, history: List<Match>): List<Long> {
        val state = EmreSystemCorrect.initializeEmreTournament(makeSongs(teamCount))
        val next = EmreSystemCorrect.processRoundResults(state, emptyList(), null, history)
        return next.teams.sortedBy { it.currentPosition }.map { it.id }
    }

    @Test
    fun tiebreaker_1_headToHeadPuaniOnce() {
        // 3 takım 1'i yendi, 4 takım 2'yi yendi → H2H: 3 ve 4 önde
        val history = listOf(
            match(1, 3L, 1L, 3L),
            match(2, 4L, 2L, 4L)
        )
        assertEquals(
            "H2H puanı yüksek olan üste çıkmalı (tiebreaker 1)",
            listOf(3L, 4L, 1L, 2L), reorderWithHistory(4, history)
        )
    }

    @Test
    fun tiebreaker_2_direktMacVe_3_enAzMaglubiyet() {
        // H2H: 1→1.0, 2→1.0, 3→0.0, 4→0.0
        // 1 ile 2 arasında H2H eşit → direkt maç 1'i öne alır (kriter 2)
        // 3 ile 4 arasında H2H eşit, direkt maç yok → 3'ün 1 mağlubiyeti var (kriter 3) → 4 önde
        val history = listOf(
            match(1, 1L, 2L, 1L),
            match(2, 2L, 3L, 2L)
        )
        assertEquals(
            "Kriter 2 (direkt maç) ve kriter 3 (en az mağlubiyet) beklendiği gibi çalışmalı",
            listOf(1L, 2L, 4L, 3L), reorderWithHistory(4, history)
        )
    }

    @Test
    fun tiebreaker_4_turOncesiSiralamaSonKriter() {
        // Hiç maç yok → tüm kriterler eşit → tur öncesi sıralama korunmalı
        assertEquals(
            "Hiçbir kriter ayırmıyorsa tur öncesi sıralama korunur",
            listOf(1L, 2L, 3L, 4L), reorderWithHistory(4, emptyList())
        )
    }

    @Test
    fun tiebreaker_tumGecmiseBakmali_sadeceSonTuraDegil() {
        // Bu turun maçı yok; ayrım YALNIZCA eski turların maçlarından gelebilir.
        val history = listOf(match(1, 4L, 1L, 4L, round = 1))
        val order = reorderWithHistory(4, history)
        assertEquals(
            "Tiebreaker eski turların maçlarını da görmeli — 4 takımı 1'i yendiği için üste çıkmalı",
            4L, order.first()
        )
    }

    // ==========================================================
    // ⑥ GEÇİŞLİLİK ÇÖKMESİ — sortedWith sözleşme ihlali
    // ==========================================================

    /**
     * Aynı puanlı büyük grupta döngüsel (taş-kağıt-makas) sonuçlar.
     * Her takım kendinden sonraki yarıyı yener → H2H herkeste eşit, direkt maç
     * kriteri devreye girer ve karşılaştırıcı GEÇİŞSİZ olur.
     * TimSort n>=32'de sözleşmeyi denetler:
     * "Comparison method violates its general contract!"
     */
    private fun cyclicHistory(n: Int): List<Match> {
        val matches = mutableListOf<Match>()
        var id = 1L
        val half = (n - 1) / 2   // her ikili yalnız bir kez oynasın
        for (i in 0 until n) {
            for (step in 1..half) {
                val j = (i + step) % n
                if (i == j) continue
                // her ikili yalnız bir kez: i < j değil, döngüsel yön belirleyici
                matches.add(match(id++, (i + 1).toLong(), (j + 1).toLong(), (i + 1).toLong()))
            }
        }
        return matches
    }

    @Test
    fun gecisliik_40TakimAyniPuanDongulSonuclar_cokmemeli() {
        val n = 40
        val order = reorderWithHistory(n, cyclicHistory(n))
        assertEquals("Sıralama tüm takımları içermeli", n, order.size)
        assertEquals("Sıralamada tekrar eden takım olmamalı", n, order.toSet().size)
    }

    @Test
    fun gecisliik_64TakimAyniPuanDongulSonuclar_cokmemeli() {
        val n = 64
        val order = reorderWithHistory(n, cyclicHistory(n))
        assertEquals("Sıralama tüm takımları içermeli", n, order.size)
        assertEquals("Sıralamada tekrar eden takım olmamalı", n, order.toSet().size)
    }

    /**
     * DÜZENLİ TURNUVA (regular tournament): n tek, her takım tam (n-1)/2 galibiyet.
     * → H2H puanları HERKESTE eşit, mağlubiyet sayıları HERKESTE eşit.
     * → Tiebreaker zincirinde yalnız "direkt maç" kriteri konuşur ve sonuçlar döngüsel.
     * Karşılaştırıcı geçişsiz olur; n >= 32'de java TimSort bunu denetler ve
     * IllegalArgumentException("Comparison method violates its general contract!") atar.
     *
     * Üçlü döngü çevirmeleri çıkış derecelerini korur, o yüzden düzenlilik bozulmaz.
     * Tohum sabit → test deterministik.
     */
    private fun regularTournamentHistory(n: Int, seed: Long): List<Match> {
        val beat = Array(n) { BooleanArray(n) }
        val half = (n - 1) / 2
        for (i in 0 until n) {
            for (s in 1..half) beat[i][(i + s) % n] = true
        }
        val rnd = java.util.Random(seed)
        repeat(n * 40) {
            val a = rnd.nextInt(n)
            val b = rnd.nextInt(n)
            val c = rnd.nextInt(n)
            if (a != b && b != c && a != c && beat[a][b] && beat[b][c] && beat[c][a]) {
                beat[a][b] = false; beat[b][a] = true
                beat[b][c] = false; beat[c][b] = true
                beat[c][a] = false; beat[a][c] = true
            }
        }
        val matches = mutableListOf<Match>()
        var id = 1L
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val winner = if (beat[i][j]) i + 1 else j + 1
                matches.add(match(id++, (i + 1).toLong(), (j + 1).toLong(), winner.toLong()))
            }
        }
        return matches
    }

    @Test
    fun gecisliik_63TakimDuzenliTurnuva_sortedWithCokmemeli() {
        val n = 63
        val history = regularTournamentHistory(n, seed = 14L)

        // Düzenlilik doğrulaması: herkes tam (n-1)/2 galibiyet almış olmalı
        val wins = mutableMapOf<Long, Int>()
        history.forEach { m -> m.winnerId?.let { wins[it] = (wins[it] ?: 0) + 1 } }
        assertEquals("Kurulum bozuk: düzenli turnuva değil", 1, wins.values.toSet().size)

        val order = reorderWithHistory(n, history)
        assertEquals("Sıralama tüm takımları içermeli", n, order.size)
        assertEquals("Sıralamada tekrar eden takım olmamalı", n, order.toSet().size)
    }

    @Test
    fun gecisliik_41TakimDuzenliTurnuva_sortedWithCokmemeli() {
        val n = 41
        val order = reorderWithHistory(n, regularTournamentHistory(n, seed = 40L))
        assertEquals("Sıralama tüm takımları içermeli", n, order.size)
        assertEquals("Sıralamada tekrar eden takım olmamalı", n, order.toSet().size)
    }

    @Test
    fun gecisliik_dongulVeri_ayniGirdiIkiKez_ayniSira() {
        // Döngüsel veride sıralama "bir şey" üretiyor; ÜRETTİĞİ ŞEY DEĞİŞMEMELİ.
        // (Eşitlik bozma son geçişi takas yapıyor — takas sırası girdiye bağlı
        // kalmalı, çağrıdan çağrıya oynamamalı.)
        val history = regularTournamentHistory(41, seed = 40L)
        val a = reorderWithHistory(41, history)
        val b = reorderWithHistory(41, history)
        assertEquals("Döngüsel veride sıralama deterministik olmalı", a, b)
    }

    @Test
    fun gecisliik_dongulVeri_hicTakimKaybolmuyor() {
        // Eşitlik bozma geçişi güvenlik sayacıyla erken durabilir; durursa bile
        // listeden takım DÜŞMEMELİ.
        listOf(35, 41, 63).forEach { n ->
            val order = reorderWithHistory(n, regularTournamentHistory(n, seed = 3L))
            assertEquals("n=$n: takım kayboldu/çoğaldı", n, order.size)
            assertEquals("n=$n: sıralamada tekrar var", n, order.toSet().size)
            assertEquals(
                "n=$n: sıralama tam takım kümesi olmalı",
                (1L..n.toLong()).toSet(), order.toSet()
            )
        }
    }

    @Test
    fun gecisliik_33TakimUcluDongu_cokmemeli() {
        // 33 takım aynı puanda; aralarında yalnızca üçlü döngüler var
        val n = 33
        val history = mutableListOf<Match>()
        var id = 1L
        var i = 1
        while (i + 2 <= n) {
            history.add(match(id++, i.toLong(), (i + 1).toLong(), i.toLong()))
            history.add(match(id++, (i + 1).toLong(), (i + 2).toLong(), (i + 1).toLong()))
            history.add(match(id++, (i + 2).toLong(), i.toLong(), (i + 2).toLong()))
            i += 3
        }
        val order = reorderWithHistory(n, history)
        assertEquals("Sıralama tüm takımları içermeli", n, order.size)
        assertEquals("Sıralamada tekrar eden takım olmamalı", n, order.toSet().size)
    }

    // ==========================================================
    // ⑦ DETERMİNİZM
    // ==========================================================

    @Test
    fun determinizm_ayniDurumIkiKez_ayniEslestirme() {
        val state = EmreSystemCorrect.initializeEmreTournament(makeSongs(16))
        val a = EmreSystemCorrect.createHybridPairingSystem(state)
        val b = EmreSystemCorrect.createHybridPairingSystem(state)
        assertEquals(
            "Aynı durum iki kez eşleştirilince aynı sonuç çıkmalı",
            a.matches.map { Triple(it.songId1, it.songId2, it.matchNumber) },
            b.matches.map { Triple(it.songId1, it.songId2, it.matchNumber) }
        )
    }

    @Test
    fun determinizm_tumTurnuvaIkiKez_ayniSonuc() {
        val first = simulate(17, winnerPicker = mixedResults)
        val second = simulate(17, winnerPicker = mixedResults)

        assertEquals("Tur sayısı aynı olmalı", first.rounds.size, second.rounds.size)
        assertEquals(
            "Tüm maç listesi aynı olmalı",
            first.allMatches.map { Triple(it.songId1, it.songId2, it.round) },
            second.allMatches.map { Triple(it.songId1, it.songId2, it.round) }
        )
        assertEquals(
            "Final sıralaması aynı olmalı",
            first.finalState.teams.sortedBy { it.currentPosition }.map { it.id },
            second.finalState.teams.sortedBy { it.currentPosition }.map { it.id }
        )
    }

    // ==========================================================
    // ⑧ MAÇ NUMARALARI — oylama sırası
    // ==========================================================

    @Test
    fun macNumaralari_1denNyeKadarTekrarsiz() {
        listOf(8, 15, 16, 32).forEach { n ->
            val sim = simulate(n, winnerPicker = mixedResults)
            sim.rounds.forEach { r ->
                val expected = (1..r.matches.size).toList()
                assertEquals(
                    "n=$n tur ${r.round}: maç numaraları 1..${r.matches.size} olmalı, bulunan ${r.matchNumbers.sorted()}",
                    expected, r.matchNumbers.sorted()
                )
            }
        }
    }

    @Test
    fun macNumaralari_ilkTurUsttenBaslar() {
        val state = EmreSystemCorrect.initializeEmreTournament(makeSongs(8))
        val pairing = EmreSystemCorrect.createHybridPairingSystem(state)
        val first = pairing.matches.firstOrNull { it.matchNumber == 1 }
        assertNotNull("1 numaralı maç olmalı", first)
        val ids = listOfNotNull(first?.songId1, first?.songId2).toSet()
        assertEquals("1 numaralı maç en üstteki iki takım olmalı", setOf(1L, 2L), ids)
    }

    // ==========================================================
    // ⑨ BAŞARIM
    // ==========================================================

    @Test
    fun basarim_n64_ilkEslestirme2SaniyeAlti() {
        val state = EmreSystemCorrect.initializeEmreTournament(makeSongs(64))
        val start = System.nanoTime()
        val pairing = EmreSystemCorrect.createHybridPairingSystem(state)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals("n=64 ilk turda 32 maç olmalı", 32, pairing.matches.size)
        assertTrue("n=64 eşleştirme $ms ms sürdü (sınır 2000 ms)", ms < 2000)
    }

    @Test
    fun basarim_n64_tumTurnuva10SaniyeAlti() {
        val start = System.nanoTime()
        val sim = simulate(64, winnerPicker = mixedResults)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("n=64 turnuvası hiç tur oynamadı", sim.rounds.isNotEmpty())
        assertTrue("n=64 tam turnuva $ms ms sürdü (sınır 10000 ms)", ms < 10000)
    }

    // ==========================================================
    // ⑩ BİTİŞ KOŞULU VE SONLANMA
    // ==========================================================

    @Test
    fun bitis_turnuvaSonsuzaKadarSurmuyor() {
        listOf(4, 5, 8, 9, 16).forEach { n ->
            val sim = simulate(n, maxRounds = 500, winnerPicker = lowerIdWins)
            assertTrue("n=$n turnuva 500 turda bitmedi (sonsuz döngü)", sim.rounds.size < 500)
            // Tam round-robin üst sınırı: n-1 tur (çift), n tur (tek)
            val limit = if (n % 2 == 0) n - 1 else n
            assertTrue(
                "n=$n turnuva $limit turdan fazla sürdü (${sim.rounds.size}) — tekrarsız eşleştirme imkânsız olmalıydı",
                sim.rounds.size <= limit
            )
        }
    }

    @Test
    fun bitis_ayniPuanliEslesmeYoksaTurnuvaBiter() {
        // Küçük id hep kazanır → puanlar hızla ayrışır
        val sim = simulate(8, winnerPicker = lowerIdWins)
        val last = EmreSystemCorrect.createHybridPairingSystem(sim.finalState)
        assertFalse(
            "Aynı puanlı eşleşme kalmadığında turnuva bitmeli",
            last.canContinue && last.matches.isNotEmpty() &&
                last.candidateMatches.any { it.team1.points == it.team2.points }
        )
    }

    // ==========================================================
    // ⑪ SINIR DURUMLARI
    // ==========================================================

    @Test
    fun sinir_sifirOge() {
        val state = EmreSystemCorrect.initializeEmreTournament(emptyList())
        assertEquals(0, state.teams.size)
        val pairing = EmreSystemCorrect.createHybridPairingSystem(state)
        assertTrue("0 öğede maç üretilmemeli", pairing.matches.isEmpty())
        assertFalse("0 öğede turnuva devam edemez", pairing.canContinue)
        assertTrue("0 öğede sonuç listesi boş olmalı", EmreSystemCorrect.calculateFinalResults(state).isEmpty())
    }

    @Test
    fun sinir_tekOge() {
        val state = EmreSystemCorrect.initializeEmreTournament(makeSongs(1))
        val pairing = EmreSystemCorrect.createHybridPairingSystem(state)
        assertTrue("1 öğede maç üretilmemeli", pairing.matches.isEmpty())
        assertFalse("1 öğede turnuva devam edemez", pairing.canContinue)
        assertEquals("1 öğede tek sonuç dönmeli", 1, EmreSystemCorrect.calculateFinalResults(state).size)
    }

    @Test
    fun sinir_ikiOge_tekTurOynanirVeBiter() {
        val sim = simulate(2, winnerPicker = lowerIdWins)
        assertEquals("2 öğede tam olarak 1 tur oynanmalı", 1, sim.rounds.size)
        assertEquals("2 öğede 1 maç olmalı", 1, sim.allMatches.size)
        val results = EmreSystemCorrect.calculateFinalResults(sim.finalState)
        assertEquals(listOf(1L, 2L), results.sortedBy { it.position }.map { it.songId })
    }

    @Test
    fun sinir_ucOge_bye_veTamKapsama() {
        val sim = simulate(3, winnerPicker = mixedResults)
        assertTrue("3 öğede en az 1 tur oynanmalı", sim.rounds.isNotEmpty())
        sim.rounds.forEach { r ->
            assertEquals("3 öğede turda 1 maç olmalı", 1, r.matches.size)
            assertNotNull("3 öğede her turda bye olmalı", r.byeTeamId)
        }
    }

    @Test
    fun sinir_yetimMacKaydi_cokmuyor() {
        val state = EmreSystemCorrect.initializeEmreTournament(makeSongs(4))
        // 999 ve 998 silinmiş öğelerin id'leri — durumda karşılığı yok
        val orphan = listOf(
            match(1, 999L, 998L, 999L),
            match(2, 1L, 997L, 1L),
            match(3, 1L, 2L, 1L)
        )
        val next = EmreSystemCorrect.processRoundResults(state, orphan, null, orphan)
        assertEquals("Yetim maç takım sayısını değiştirmemeli", 4, next.teams.size)

        // 1 numaralı takımın GEÇERLİ tek maçı var (1-2). 997 silinmiş bir öğe.
        // Motor bu maçı match history'ye YAZMIYOR (songToTeamMap null döndüğü için),
        // ama PUANI veriyor. Aynı maç aynı anda "oynanmadı" ve "kazanıldı" sayılıyor.
        assertEquals(
            "YETİM MAÇ TUTARSIZLIĞI: silinmiş öğeye (997) karşı maç eşleşme geçmişine " +
                "yazılmıyor ama puan üretiyor — takım 1 hayalet puan kazandı",
            1.0, next.teams.first { it.id == 1L }.points, 0.0001
        )
        // Geçmiş tarafı da tutarlı olmalı: yalnız 1-2 oynanmış sayılmalı
        assertEquals(
            "Yetim maçlar eşleşme geçmişine yazılmamalı — yalnız 1-2 kaydı olmalı",
            1, next.matchHistory.size
        )
    }

    @Test
    fun sinir_yetimMacGelecekEslestirmeyiYasaklamaz() {
        // Silinmiş öğeye karşı oynanmış maç, hayattaki takımların birbiriyle
        // eşleşmesini engellememeli.
        val state = EmreSystemCorrect.initializeEmreTournament(makeSongs(4))
        val orphan = listOf(match(1, 1L, 997L, 1L), match(2, 3L, 996L, 996L))
        val next = EmreSystemCorrect.processRoundResults(state, orphan, null, orphan)
        val pairing = EmreSystemCorrect.createHybridPairingSystem(next)
        assertEquals("Yetim maçtan sonra da 4 takım 2 maç oynamalı", 2, pairing.matches.size)
        val participants = pairing.matches.flatMap { listOf(it.songId1, it.songId2) }.toSet()
        assertEquals("Hiçbir takım turdan düşmemeli", setOf(1L, 2L, 3L, 4L), participants)
    }

    @Test
    fun sinir_tamamlanmamisMacPuanUretmez() {
        val state = EmreSystemCorrect.initializeEmreTournament(makeSongs(4))
        val notCompleted = listOf(match(1, 1L, 2L, 1L).copy(isCompleted = false))
        val next = EmreSystemCorrect.processRoundResults(state, notCompleted, null, notCompleted)
        assertEquals(
            "Tamamlanmamış maç puan üretmemeli",
            0.0, next.teams.first { it.id == 1L }.points, 0.0001
        )
        // Ve maç geçmişine girmemeli: 1-2 hâlâ eşleşebilmeli
        val pairing = EmreSystemCorrect.createHybridPairingSystem(next)
        assertTrue(
            "Tamamlanmamış maç eşleştirmeyi yasaklamamalı",
            pairing.matches.any { normalize(it.songId1, it.songId2) == Pair(1L, 2L) }
        )
    }

    @Test
    fun sinir_ayniMacIkiKezIslenmez() {
        val state = EmreSystemCorrect.initializeEmreTournament(makeSongs(4))
        val m = match(1, 1L, 2L, 1L)
        val next = EmreSystemCorrect.processRoundResults(state, listOf(m, m), null, listOf(m))
        assertEquals(
            "ÇİFT PUANLAMA: aynı maç iki kez işlendi",
            1.0, next.teams.first { it.id == 1L }.points, 0.0001
        )
    }
}
