package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.PairwiseComparisonSort
import org.junit.Assert.*
import org.junit.Test

/**
 * İkili Karşılaştırmalı Sıralama (binary insertion sort) testleri.
 * Deterministik tercih: küçük ID her zaman kazanır → beklenen sıra 1,2,3,...,n
 */
class PairwiseComparisonSortTest {

    private fun makeSongs(count: Int, shuffleSeed: Long? = null): List<Song> {
        val songs = (1..count).map { i -> Song(id = i.toLong(), name = "Item$i", listId = 1L) }
        return if (shuffleSeed != null) songs.shuffled(java.util.Random(shuffleSeed)) else songs
    }

    /** Tüm sıralamayı simüle eder; (karşılaştırma sayısı, final sıra) döndürür. */
    private fun simulateFullSort(songs: List<Song>, winnerPicker: (Long, Long) -> Long): Pair<Int, List<Long>> {
        val completed = mutableListOf<Match>()
        var nextId = 1L
        var guard = 0
        val guardLimit = songs.size * songs.size + 10

        while (guard++ < guardLimit) {
            val state = PairwiseComparisonSort.computeState(songs, completed)
            val next = state.nextComparison ?: return Pair(state.comparisonsDone, state.sortedIds)
            completed.add(
                Match(
                    id = nextId++,
                    listId = 1L,
                    rankingMethod = PairwiseComparisonSort.METHOD,
                    songId1 = next.first,
                    songId2 = next.second,
                    winnerId = winnerPicker(next.first, next.second),
                    isCompleted = true
                )
            )
        }
        fail("Sıralama $guardLimit adımda bitmedi - sonsuz döngü!")
        error("unreachable")
    }

    @Test
    fun testSortsCorrectlyOrderedInput() {
        val songs = makeSongs(10)
        val (_, sorted) = simulateFullSort(songs) { a, b -> minOf(a, b) }
        assertEquals((1L..10L).toList(), sorted)
    }

    @Test
    fun testSortsShuffledInput() {
        val songs = makeSongs(20, shuffleSeed = 42L)
        val (_, sorted) = simulateFullSort(songs) { a, b -> minOf(a, b) }
        assertEquals("Karışık girdi tam sıralanmalı", (1L..20L).toList(), sorted)
    }

    @Test
    fun testReverseOrderPreference() {
        // Büyük ID kazanırsa sıra tersine dönmeli
        val songs = makeSongs(12)
        val (_, sorted) = simulateFullSort(songs) { a, b -> maxOf(a, b) }
        assertEquals((12L downTo 1L).toList(), sorted)
    }

    @Test
    fun testComparisonCountIsNLogN() {
        // 80 öğe: lig usulü 3160 maç isterdi; n·log2(n) ≈ 500'ü aşmamalı
        val songs = makeSongs(80, shuffleSeed = 7L)
        val (comparisons, sorted) = simulateFullSort(songs) { a, b -> minOf(a, b) }
        assertEquals(80, sorted.size)
        val estimate = PairwiseComparisonSort.estimatedTotalComparisons(80)
        assertTrue(
            "Karşılaştırma sayısı ($comparisons) tahmini üst sınırı ($estimate) aşmamalı",
            comparisons <= estimate
        )
        assertTrue("80 öğe için 600'den az soru sorulmalı, soruldu: $comparisons", comparisons < 600)
    }

    @Test
    fun testResumeFromPartialState() {
        // Yarıda kalan sıralama, kayıtlı maçlardan aynen devam etmeli
        val songs = makeSongs(15, shuffleSeed = 3L)
        val completed = mutableListOf<Match>()
        var nextId = 1L

        // İlk 10 karşılaştırmayı yap
        repeat(10) {
            val state = PairwiseComparisonSort.computeState(songs, completed)
            val next = state.nextComparison ?: return@repeat
            completed.add(
                Match(
                    id = nextId++, listId = 1L, rankingMethod = PairwiseComparisonSort.METHOD,
                    songId1 = next.first, songId2 = next.second,
                    winnerId = minOf(next.first, next.second), isCompleted = true
                )
            )
        }

        // "Uygulama yeniden açıldı": aynı kayıtlarla durum yeniden kurulur
        val resumed = PairwiseComparisonSort.computeState(songs, completed)
        assertEquals("Devam eden durumda 10 karşılaştırma sayılmalı", 10, resumed.comparisonsDone)

        // Kaldığı yerden bitir
        var guard = 0
        while (guard++ < 500) {
            val state = PairwiseComparisonSort.computeState(songs, completed)
            val next = state.nextComparison ?: break
            completed.add(
                Match(
                    id = nextId++, listId = 1L, rankingMethod = PairwiseComparisonSort.METHOD,
                    songId1 = next.first, songId2 = next.second,
                    winnerId = minOf(next.first, next.second), isCompleted = true
                )
            )
        }
        val final = PairwiseComparisonSort.computeState(songs, completed)
        assertTrue(final.isComplete)
        assertEquals((1L..15L).toList(), final.sortedIds)
    }

    @Test
    fun testUndoLastComparison() {
        // Son cevabın silinmesi, aynı sorunun tekrar sorulmasına yol açmalı
        val songs = makeSongs(8, shuffleSeed = 5L)
        val completed = mutableListOf<Match>()
        var nextId = 1L
        repeat(5) {
            val state = PairwiseComparisonSort.computeState(songs, completed)
            val next = state.nextComparison ?: return@repeat
            completed.add(
                Match(
                    id = nextId++, listId = 1L, rankingMethod = PairwiseComparisonSort.METHOD,
                    songId1 = next.first, songId2 = next.second,
                    winnerId = minOf(next.first, next.second), isCompleted = true
                )
            )
        }

        val beforeUndo = PairwiseComparisonSort.computeState(songs, completed)
        val lastMatch = completed.removeAt(completed.size - 1)
        val afterUndo = PairwiseComparisonSort.computeState(songs, completed)

        assertEquals(beforeUndo.comparisonsDone - 1, afterUndo.comparisonsDone)
        // Geri alınan karşılaştırma tekrar sorulmalı
        val reasked = afterUndo.nextComparison!!
        val samePair = setOf(reasked.first, reasked.second) == setOf(lastMatch.songId1, lastMatch.songId2)
        assertTrue("Geri alınan soru tekrar sorulmalı", samePair)
    }

    @Test
    fun testCalculateResults() {
        val songs = makeSongs(10, shuffleSeed = 9L)
        val completed = mutableListOf<Match>()
        var nextId = 1L
        var guard = 0
        while (guard++ < 200) {
            val state = PairwiseComparisonSort.computeState(songs, completed)
            val next = state.nextComparison ?: break
            completed.add(
                Match(
                    id = nextId++, listId = 1L, rankingMethod = PairwiseComparisonSort.METHOD,
                    songId1 = next.first, songId2 = next.second,
                    winnerId = minOf(next.first, next.second), isCompleted = true
                )
            )
        }

        val results = PairwiseComparisonSort.calculateResults(songs, completed)
        assertEquals(10, results.size)
        assertEquals("En iyi öğe 1. sırada olmalı", 1L, results.first { it.position == 1 }.songId)
        assertEquals("En kötü öğe 10. sırada olmalı", 10L, results.first { it.position == 10 }.songId)
        assertEquals((1..10).toList(), results.map { it.position }.sorted())
    }

    @Test
    fun testEdgeCases() {
        // Boş liste
        assertTrue(PairwiseComparisonSort.computeState(emptyList(), emptyList()).isComplete)
        // Tek öğe: hiç soru sorulmadan tamam
        val single = PairwiseComparisonSort.computeState(makeSongs(1), emptyList())
        assertTrue(single.isComplete)
        assertEquals(listOf(1L), single.sortedIds)
        // İki öğe: tek soru yeter
        val songs2 = makeSongs(2)
        val (comparisons, sorted) = simulateFullSort(songs2) { a, b -> minOf(a, b) }
        assertEquals(1, comparisons)
        assertEquals(listOf(1L, 2L), sorted)
    }
}
