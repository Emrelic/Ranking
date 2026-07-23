package com.example.ranking.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.RankingResult
import com.example.ranking.data.Song
import kotlin.math.ceil
import kotlin.math.log2

/**
 * İkili Karşılaştırmalı Sıralama (binary insertion sort).
 *
 * Kullanıcıya "hangisi daha iyi?" soruları sorarak listeyi tam sıralar.
 * Her yeni öğe, o ana kadar sıralanmış listeye ikili arama ile yerleştirilir:
 * n öğe için toplam ~n·log2(n) karşılaştırma yeter (80 öğe ≈ 500 soru,
 * lig usulündeki 3.160 maça kıyasla).
 *
 * DURUM YÖNETİMİ: Ayrı bir durum tablosu tutulmaz. Tamamlanmış
 * karşılaştırmalar (matches tablosu, kayıt sırasına göre) deterministik
 * olarak baştan oynatılır (replay); algoritmanın soracağı bir sonraki soru
 * bu oynatmadan türetilir. Böylece devam etme/geri alma, maç kayıtlarının
 * doğal sonucu olur.
 *
 * Beraberlik yoktur: her karşılaştırmada kazanan seçilmelidir.
 * (Kayıtta winnerId=null görülürse öğe "kaybetti" sayılır.)
 */
object PairwiseComparisonSort {

    const val METHOD = "MERGE_SORT"

    data class SortState(
        val sortedIds: List<Long>,              // En iyiden en kötüye, şu ana kadar yerleşenler
        val nextComparison: Pair<Long, Long>?,  // Sorulacak soru (yerleşen aday, sıralı listedeki rakip); null = tamamlandı
        val comparisonsDone: Int,
        val totalItems: Int,
        val insertedCount: Int
    ) {
        val isComplete: Boolean get() = nextComparison == null
    }

    /** Tahmini toplam karşılaştırma sayısı (ilerleme çubuğu için üst sınır). */
    fun estimatedTotalComparisons(n: Int): Int {
        if (n <= 1) return 0
        var total = 0
        for (k in 1 until n) {
            total += ceil(log2((k + 1).toDouble())).toInt().coerceAtLeast(1)
        }
        return total
    }

    /**
     * Tamamlanmış karşılaştırmaları baştan oynatarak güncel durumu kurar.
     * completedMatches: bu listenin MERGE_SORT yöntemine ait TAMAMLANMIŞ maçları.
     */
    fun computeState(songs: List<Song>, completedMatches: List<Match>): SortState {
        val ids = songs.map { it.id }
        if (ids.isEmpty()) {
            return SortState(emptyList(), null, 0, 0, 0)
        }
        if (ids.size == 1) {
            return SortState(ids, null, 0, 1, 1)
        }

        // Kayıt sırası = cevap sırası (autogenerate id artan)
        val answers = completedMatches.filter { it.isCompleted }.sortedBy { it.id }.toMutableList()
        var consumed = 0

        val sorted = mutableListOf(ids[0])
        var index = 1

        while (index < ids.size) {
            val candidate = ids[index]
            var low = 0
            var high = sorted.size

            while (low < high) {
                val mid = (low + high) / 2
                val opponent = sorted[mid]

                // Bu karşılaştırmanın cevabını ara (normalde sıradaki kayıttır;
                // veri anomalilerine karşı kalan kayıtlar içinde de aranır)
                val answerIndex = answers.indexOfFirst { m ->
                    (m.songId1 == candidate && m.songId2 == opponent) ||
                    (m.songId1 == opponent && m.songId2 == candidate)
                }

                if (answerIndex == -1) {
                    // Cevap yok - sorulacak soru bu
                    return SortState(
                        sortedIds = sorted.toList(),
                        nextComparison = Pair(candidate, opponent),
                        comparisonsDone = consumed,
                        totalItems = ids.size,
                        insertedCount = index
                    )
                }

                val answer = answers.removeAt(answerIndex)
                consumed++

                if (answer.winnerId == candidate) {
                    high = mid          // Aday daha iyi - üst yarıya
                } else {
                    low = mid + 1       // Aday daha kötü (veya beraberlik) - alt yarıya
                }
            }

            sorted.add(low, candidate)
            index++
        }

        return SortState(
            sortedIds = sorted.toList(),
            nextComparison = null,
            comparisonsDone = consumed,
            totalItems = ids.size,
            insertedCount = ids.size
        )
    }

    /**
     * Sorulacak sıradaki karşılaştırmayı Match kaydı olarak üretir (null = sıralama bitti).
     */
    fun createNextComparisonMatch(songs: List<Song>, completedMatches: List<Match>): Match? {
        val state = computeState(songs, completedMatches)
        val (songId1, songId2) = state.nextComparison ?: return null
        return Match(
            listId = songs.first().listId,
            rankingMethod = METHOD,
            songId1 = songId1,
            songId2 = songId2,
            winnerId = null,
            round = state.insertedCount, // Tur = kaçıncı öğenin yerleştirildiği
            matchNumber = state.comparisonsDone + 1,
            isCompleted = false
        )
    }

    /**
     * Final sonuçları: sıralı listedeki konum → pozisyon; skor = üstte olduğu öğe sayısı.
     * Sıralama tamamlanmamışsa mevcut kısmi sıralamaya göre üretir
     * (yerleşmemiş öğeler listenin sonuna orijinal sırayla eklenir).
     */
    fun calculateResults(songs: List<Song>, completedMatches: List<Match>): List<RankingResult> {
        val state = computeState(songs, completedMatches)
        val placed = state.sortedIds
        val unplaced = songs.map { it.id }.filter { it !in placed.toSet() }
        val finalOrder = placed + unplaced
        val n = finalOrder.size
        val listId = songs.firstOrNull()?.listId ?: 0L

        return finalOrder.mapIndexed { index, songId ->
            RankingResult(
                songId = songId,
                listId = listId,
                rankingMethod = METHOD,
                score = (n - index).toDouble(),
                position = index + 1
            )
        }
    }
}
