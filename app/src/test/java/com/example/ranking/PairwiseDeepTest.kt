package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.PairwiseComparisonSort
import org.junit.Assert.*
import org.junit.Test

/**
 * İKİLİ KARŞILAŞTIRMA (MERGE_SORT) — DERİN KUSUR ARAMA TESTLERİ
 *
 * Hedefler:
 *  - replay deseni gerçekten deterministik mi (aynı girdi → aynı çıktı)
 *  - aynı ikili İKİ KEZ sorulmuyor
 *  - soru sayısı estimatedTotalComparisons üst sınırını aşmıyor
 *  - beraberlik (winnerId = null) davranışı SABİTLENİYOR (kod "aday kaybetti" sayar)
 *  - yarım kalmış sıralama, yetim maç, tutarsız kullanıcı → çökme yok
 *  - sınırlar: n = 0, 1, 2, 3
 */
class PairwiseDeepTest {

    private val method = PairwiseComparisonSort.METHOD

    private fun makeSongs(count: Int): List<Song> =
        (1..count).map { i -> Song(id = i.toLong(), name = "Oge$i", listId = 7L) }

    private data class RunResult(
        val order: List<Long>,
        val matches: List<Match>,
        val comparisons: Int,
        val asked: List<Pair<Long, Long>>
    )

    /**
     * Sıralamayı sonuna kadar simüle eder.
     * answer(aday, rakip) → kazanan songId (null verilirse beraberlik kaydedilir).
     */
    private fun runSort(
        songs: List<Song>,
        startMatches: List<Match> = emptyList(),
        answer: (Long, Long) -> Long?
    ): RunResult {
        val matches = startMatches.toMutableList()
        val asked = mutableListOf<Pair<Long, Long>>()
        var nextId = (matches.maxOfOrNull { it.id } ?: 0L) + 1
        var guard = 0
        val guardLimit = songs.size * songs.size + 100

        while (guard++ < guardLimit) {
            val next = PairwiseComparisonSort.createNextComparisonMatch(songs, matches)
                ?: break
            asked.add(Pair(next.songId1, next.songId2))
            matches.add(
                next.copy(
                    id = nextId++,
                    winnerId = answer(next.songId1, next.songId2),
                    isCompleted = true
                )
            )
        }
        assertTrue("Sıralama $guardLimit adımda bitmedi — sonsuz döngü", guard < guardLimit)

        val state = PairwiseComparisonSort.computeState(songs, matches)
        assertTrue("Sıralama tamamlanmadı", state.isComplete)
        return RunResult(state.sortedIds, matches, state.comparisonsDone, asked)
    }

    /** Küçük id daha iyi — tutarlı (geçişli) bir kullanıcı. */
    private val lowerIdIsBetter: (Long, Long) -> Long? = { a, b -> minOf(a, b) }

    // ==========================================================
    // ① SINIR DURUMLARI
    // ==========================================================

    @Test
    fun sinir_sifirOge() {
        val state = PairwiseComparisonSort.computeState(emptyList(), emptyList())
        assertTrue("0 öğede sıralama bitmiş sayılmalı", state.isComplete)
        assertEquals(0, state.totalItems)
        assertTrue(state.sortedIds.isEmpty())
        assertNull(
            "0 öğede soru üretilmemeli (songs.first() çökmemeli)",
            PairwiseComparisonSort.createNextComparisonMatch(emptyList(), emptyList())
        )
        assertTrue(PairwiseComparisonSort.calculateResults(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun sinir_tekOge() {
        val songs = makeSongs(1)
        val state = PairwiseComparisonSort.computeState(songs, emptyList())
        assertTrue("1 öğede sıralama bitmiş olmalı", state.isComplete)
        assertEquals(listOf(1L), state.sortedIds)
        assertNull(PairwiseComparisonSort.createNextComparisonMatch(songs, emptyList()))
        val results = PairwiseComparisonSort.calculateResults(songs, emptyList())
        assertEquals(1, results.size)
        assertEquals(1, results[0].position)
    }

    @Test
    fun sinir_ikiOge_tekSoru() {
        val songs = makeSongs(2)
        val run = runSort(songs, answer = lowerIdIsBetter)
        assertEquals("2 öğede tam olarak 1 soru sorulmalı", 1, run.asked.size)
        assertEquals(listOf(1L, 2L), run.order)
    }

    @Test
    fun sinir_ucOge() {
        val songs = makeSongs(3)
        val run = runSort(songs, answer = lowerIdIsBetter)
        assertEquals(listOf(1L, 2L, 3L), run.order)
        assertTrue("3 öğede soru sayısı 3'ü aşmamalı", run.asked.size <= 3)
    }

    @Test
    fun tahminiSoruSayisi_sinirDegerleri() {
        assertEquals(0, PairwiseComparisonSort.estimatedTotalComparisons(0))
        assertEquals(0, PairwiseComparisonSort.estimatedTotalComparisons(1))
        assertEquals(1, PairwiseComparisonSort.estimatedTotalComparisons(2))
    }

    // ==========================================================
    // ② SORU SAYISI ÜST SINIRI
    // ==========================================================

    private fun assertWithinEstimate(n: Int, label: String, answer: (Long, Long) -> Long?) {
        val songs = makeSongs(n)
        val run = runSort(songs, answer = answer)
        val estimate = PairwiseComparisonSort.estimatedTotalComparisons(n)
        assertTrue(
            "$label (n=$n): ${run.asked.size} soru soruldu, tahmini üst sınır $estimate — SINIR AŞILDI",
            run.asked.size <= estimate
        )
        assertEquals("comparisonsDone sorulan soru sayısıyla tutmalı", run.asked.size, run.comparisons)
    }

    @Test
    fun soruSayisi_n50_tutarliKullanici() = assertWithinEstimate(50, "tutarlı kullanıcı", lowerIdIsBetter)

    @Test
    fun soruSayisi_n50_hepAdayKazanir() = assertWithinEstimate(50, "hep aday kazanır") { a, _ -> a }

    @Test
    fun soruSayisi_n50_hepAdayKaybeder() = assertWithinEstimate(50, "hep aday kaybeder") { _, b -> b }

    @Test
    fun soruSayisi_n50_hepsiBerabere() = assertWithinEstimate(50, "hepsi berabere") { _, _ -> null }

    @Test
    fun soruSayisi_n80_gercekcilikSiniri() = assertWithinEstimate(80, "80 öğelik gerçek liste", lowerIdIsBetter)

    // ==========================================================
    // ③ AYNI İKİLİ İKİ KEZ SORULMAZ
    // ==========================================================

    private fun assertNoDuplicateQuestion(n: Int, answer: (Long, Long) -> Long?) {
        val run = runSort(makeSongs(n), answer = answer)
        val seen = mutableSetOf<Pair<Long, Long>>()
        run.asked.forEach { (a, b) ->
            val key = if (a < b) Pair(a, b) else Pair(b, a)
            assertTrue("AYNI İKİLİ İKİ KEZ SORULDU: $key (n=$n)", seen.add(key))
        }
    }

    @Test
    fun tekrarSoruYok_n10() = assertNoDuplicateQuestion(10, lowerIdIsBetter)

    @Test
    fun tekrarSoruYok_n50_hepsiBerabere() = assertNoDuplicateQuestion(50) { _, _ -> null }

    @Test
    fun tekrarSoruYok_n30_tutarsizKullanici() =
        assertNoDuplicateQuestion(30) { a, b -> if ((a + b) % 3L == 0L) b else a }

    // ==========================================================
    // ④ DETERMİNİZM VE REPLAY
    // ==========================================================

    @Test
    fun determinizm_ayniGirdiIkiKez_ayniCikti() {
        val songs = makeSongs(25)
        val first = runSort(songs, answer = lowerIdIsBetter)
        val second = PairwiseComparisonSort.computeState(songs, first.matches)
        assertEquals("Aynı maç listesi aynı sıralamayı vermeli", first.order, second.sortedIds)
        assertEquals(
            "Aynı maç listesi aynı soru sayısını vermeli",
            first.comparisons, second.comparisonsDone
        )
    }

    @Test
    fun replay_cevaplarKarisikSiradaGelirse_ayniSonuc() {
        val songs = makeSongs(20)
        val run = runSort(songs, answer = lowerIdIsBetter)

        // Kayıt sırası bozulmuş (DB'den farklı sırada gelmiş) ama id'ler aynı
        val shuffled = run.matches.sortedBy { (it.songId1 * 31 + it.songId2) % 97 }
        val replayed = PairwiseComparisonSort.computeState(songs, shuffled)
        assertEquals(
            "REPLAY BOZULDU: kayıt sırası değişince sıralama değişti",
            run.order, replayed.sortedIds
        )
    }

    @Test
    fun replay_yarimBirakipDevamEtmek_ayniSonucuVerir() {
        val songs = makeSongs(15)
        val full = runSort(songs, answer = lowerIdIsBetter)

        // İlk yarıyı verip kaldığı yerden devam et
        val half = full.matches.take(full.matches.size / 2)
        val resumed = runSort(songs, startMatches = half, answer = lowerIdIsBetter)
        assertEquals(
            "Yarıda bırakıp devam edince aynı sıralama çıkmalı",
            full.order, resumed.order
        )
        assertEquals(
            "Yarıda bırakıp devam edince toplam soru sayısı aynı olmalı",
            full.matches.size, resumed.matches.size
        )
    }

    @Test
    fun replay_ogeSirasiDegisirse_eskiCevaplarKullanilir() {
        // Gerçek senaryo: DB öğeleri farklı sırada döndürüyor (sıralama anahtarı değişti).
        // Tutarlı kullanıcıda nihai sıra AYNI olmalı ve hiçbir ikili İKİ KEZ sorulmamalı.
        val songs = makeSongs(12)
        val first = runSort(songs, answer = lowerIdIsBetter)

        val reordered = songs.reversed()
        val second = runSort(reordered, startMatches = first.matches, answer = lowerIdIsBetter)

        assertEquals(
            "Öğe sırası değişince nihai sıralama değişmemeli (tutarlı kullanıcı)",
            first.order, second.order
        )
        val allAsked = (first.asked + second.asked).map { (a, b) -> if (a < b) Pair(a, b) else Pair(b, a) }
        assertEquals(
            "Öğe sırası değişince daha önce sorulan ikili TEKRAR soruldu: " +
                allAsked.groupBy { it }.filter { it.value.size > 1 }.keys,
            allAsked.size, allAsked.toSet().size
        )
    }

    // ==========================================================
    // ⑤ BERABERLİK DAVRANIŞI (belgeleme testi)
    // ==========================================================

    @Test
    fun belgeleme_beraberlik_adayKaybetmisSayilir() {
        // Kod yorumu: "Kayıtta winnerId=null görülürse öğe kaybetti sayılır."
        // Bu test o kararı SABİTLER; davranış değişirse burada kırılır.
        val songs = makeSongs(2)
        val question = PairwiseComparisonSort.createNextComparisonMatch(songs, emptyList())
        assertNotNull(question)
        val candidate = question?.songId1
        val opponent = question?.songId2
        val draw = question?.copy(id = 1L, winnerId = null, isCompleted = true)

        val state = PairwiseComparisonSort.computeState(songs, listOfNotNull(draw))
        assertTrue("Beraberlikten sonra sıralama tamamlanmalı", state.isComplete)
        assertEquals(
            "Beraberlikte aday (soru sahibi) ALTA yerleşmeli — mevcut bilinçli davranış",
            listOf(opponent, candidate), state.sortedIds
        )
    }

    @Test
    fun beraberlik_hepsiBerabere_tumOgelerSiralanir() {
        val songs = makeSongs(16)
        val run = runSort(songs) { _, _ -> null }
        assertEquals("Hepsi berabere olsa da tüm öğeler sıralanmalı", 16, run.order.size)
        assertEquals("Sıralamada tekrar olmamalı", 16, run.order.toSet().size)
    }

    // ==========================================================
    // ⑥ TUTARSIZ KULLANICI VE YARIM SONUÇLAR
    // ==========================================================

    @Test
    fun tutarsizKullanici_AB_BC_CA_cokmuyor() {
        // A>B, B>C, C>A — geçişlilik ihlali
        val songs = makeSongs(3)
        val run = runSort(songs) { a, b ->
            when {
                (a == 1L && b == 2L) || (a == 2L && b == 1L) -> 1L
                (a == 2L && b == 3L) || (a == 3L && b == 2L) -> 2L
                else -> 3L
            }
        }
        assertEquals("Tutarsız cevaplarda da tüm öğeler sıralanmalı", 3, run.order.size)
        assertEquals(setOf(1L, 2L, 3L), run.order.toSet())
    }

    @Test
    fun yarimSiralama_calculateResults_tumOgeleriDondurur() {
        val songs = makeSongs(10)
        val full = runSort(songs, answer = lowerIdIsBetter)
        val partial = full.matches.take(3)

        val results = PairwiseComparisonSort.calculateResults(songs, partial)
        assertEquals("Yarım sıralamada da 10 sonuç dönmeli", 10, results.size)
        assertEquals(
            "Pozisyonlar 1..10 tekrarsız olmalı",
            (1..10).toList(), results.map { it.position }.sorted()
        )
        assertEquals(
            "Hiçbir öğe kaybolmamalı",
            songs.map { it.id }.toSet(), results.map { it.songId }.toSet()
        )
        assertEquals("listId korunmalı", 7L, results.first().listId)
        assertEquals("Yöntem adı MERGE_SORT olmalı", method, results.first().rankingMethod)
    }

    @Test
    fun yarimSiralama_hicCevapYokken_cokmuyor() {
        val songs = makeSongs(5)
        val results = PairwiseComparisonSort.calculateResults(songs, emptyList())
        assertEquals(5, results.size)
        assertEquals((1..5).toList(), results.map { it.position }.sorted())
    }

    // ==========================================================
    // ⑦ VERİ ANOMALİLERİ
    // ==========================================================

    @Test
    fun yetimMacKaydi_cokmuyorVeSonucuBozmuyor() {
        val songs = makeSongs(8)
        val clean = runSort(songs, answer = lowerIdIsBetter)

        // Silinmiş öğelere ait maçlar listeye karışmış
        val orphans = listOf(
            Match(id = 900L, listId = 7L, rankingMethod = method, songId1 = 999L, songId2 = 998L,
                winnerId = 999L, isCompleted = true),
            Match(id = 901L, listId = 7L, rankingMethod = method, songId1 = 1L, songId2 = 997L,
                winnerId = 997L, isCompleted = true)
        )
        val polluted = PairwiseComparisonSort.computeState(songs, clean.matches + orphans)
        assertEquals(
            "Yetim maç kaydı sıralamayı bozmamalı",
            clean.order, polluted.sortedIds
        )
    }

    @Test
    fun ayniIkiliIcinIkiKayit_cokmuyor() {
        val songs = makeSongs(6)
        val clean = runSort(songs, answer = lowerIdIsBetter)
        // Çift kayıt (çift dokunuş) — aynı ikili iki kez kaydedilmiş
        val duplicated = clean.matches + clean.matches.first().copy(id = 500L)
        val state = PairwiseComparisonSort.computeState(songs, duplicated)
        assertEquals("Çift kayıt sıralamayı bozmamalı", clean.order, state.sortedIds)
    }

    @Test
    fun tamamlanmamisMaclarSayilmaz() {
        val songs = makeSongs(4)
        val pending = listOf(
            Match(id = 1L, listId = 7L, rankingMethod = method, songId1 = 1L, songId2 = 2L,
                winnerId = 1L, isCompleted = false)
        )
        val state = PairwiseComparisonSort.computeState(songs, pending)
        assertFalse("Tamamlanmamış maçla sıralama bitmiş sayılamaz", state.isComplete)
        assertEquals("Tamamlanmamış maç sayılmamalı", 0, state.comparisonsDone)
    }

    // ==========================================================
    // ⑧ ÜRETİLEN SORU KAYDININ ALANLARI
    // ==========================================================

    @Test
    fun uretilenSoruKaydi_alanlariDogru() {
        val songs = makeSongs(5)
        val first = PairwiseComparisonSort.createNextComparisonMatch(songs, emptyList())
        assertNotNull(first)
        val m = first ?: return
        assertEquals("listId öğeden alınmalı", 7L, m.listId)
        assertEquals("yöntem MERGE_SORT olmalı", method, m.rankingMethod)
        assertNull("yeni soru tamamlanmamış olmalı", m.winnerId)
        assertFalse(m.isCompleted)
        assertEquals("ilk sorunun matchNumber'ı 1 olmalı", 1, m.matchNumber)
        assertTrue("matchNumber 0 bırakılmamalı", m.matchNumber > 0)

        val answered = m.copy(id = 1L, winnerId = m.songId1, isCompleted = true)
        val second = PairwiseComparisonSort.createNextComparisonMatch(songs, listOf(answered))
        assertEquals("ikinci sorunun matchNumber'ı 2 olmalı", 2, second?.matchNumber)
    }

    @Test
    fun tahminiSoruSayisi_azalmaz() {
        // Üst sınır n büyüdükçe küçülemez
        var prev = 0
        (1..80).forEach { n ->
            val cur = PairwiseComparisonSort.estimatedTotalComparisons(n)
            assertTrue("estimatedTotalComparisons($n)=$cur, önceki $prev'den küçük", cur >= prev)
            prev = cur
        }
    }

    @Test
    fun tekOge_yabanciMaclarlaBileTamamlanmisSayilir() {
        val songs = makeSongs(1)
        val orphan = listOf(
            Match(id = 1L, listId = 7L, rankingMethod = method, songId1 = 99L, songId2 = 98L,
                winnerId = 99L, isCompleted = true)
        )
        val state = PairwiseComparisonSort.computeState(songs, orphan)
        assertTrue("1 öğede yabancı maç varken de sıralama bitmiş sayılmalı", state.isComplete)
        assertEquals(listOf(1L), state.sortedIds)
    }

    @Test
    fun skorlarPozisyonlaTutarli() {
        val songs = makeSongs(12)
        val run = runSort(songs, answer = lowerIdIsBetter)
        val results = PairwiseComparisonSort.calculateResults(songs, run.matches)
        results.sortedBy { it.position }.zipWithNext().forEach { (ust, alt) ->
            assertTrue(
                "Üstteki öğenin skoru alttakinden büyük olmalı (${ust.score} vs ${alt.score})",
                ust.score > alt.score
            )
        }
        assertEquals("En alttaki skor 1 olmalı", 1.0, results.maxOf { it.position }.let { p ->
            results.first { it.position == p }.score
        }, 0.0001)
    }

    @Test
    fun tamSiralama_n50_dogruSonuc() {
        val songs = makeSongs(50)
        val run = runSort(songs, answer = lowerIdIsBetter)
        assertEquals(
            "Tutarlı kullanıcıda sonuç tam sıralı olmalı",
            (1L..50L).toList(), run.order
        )
        val results = PairwiseComparisonSort.calculateResults(songs, run.matches)
        assertEquals("En iyi öğe 1. sırada olmalı", 1L, results.first { it.position == 1 }.songId)
        assertEquals("Skor n-index olmalı", 50.0, results.first { it.position == 1 }.score, 0.0001)
    }
}
