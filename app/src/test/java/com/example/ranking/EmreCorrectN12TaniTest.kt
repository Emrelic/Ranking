package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.EmreSystemCorrect
import com.example.ranking.ranking.RankingEngine
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * n=12 AYKIRILIĞININ TANISI (EmreCorrectDegismezlerTest'in ⑤ ölçümünün devamı).
 *
 * Ölçülen: n=11 → 7 tur / keskinlik %80, n=13 → 11 tur / %100 iken
 * n=12 yalnız 4 turda, %36 keskinlikle bitiyor — sıralamanın üçte ikisi
 * tiebreaker tahmini kalıyor.
 *
 * Bu sınav "neden bitti" sorusunu ayırt eder. Motorun iki ayrı bitiş kapısı var
 * (`createHybridPairingSystem`):
 *   (A) EŞLEŞTİRME KURULAMADI — tekrarsız tam eşleştirme yok (AEG < n/2)
 *   (B) AYNI PUANLI EŞLEŞME YOK — `analyzeTournamentContinuation` kapatıyor
 * Aday eşleşme sayısı bitiş anında n/2'den küçükse (A), değilse (B).
 *
 * Ayrıca bitiş anında HENÜZ OYNANMAMIŞ aynı puanlı çift var mı sayılır: varsa
 * turnuva, elinde kanıt üretecek eşleşme dururken kapanıyor demektir.
 *
 * Motor koduna dokunulmaz; bu dosya yalnız ölçer ve kaydı rapora taşır.
 */
class EmreCorrectN12TaniTest {

    private fun sarkilar(n: Int, tohum: Long = 4242L): List<Song> =
        (1..n).shuffled(java.util.Random(tohum)).mapIndexed { i, deger ->
            Song(
                id = deger.toLong(), name = "Sayi $deger", artist = "", album = "",
                trackNumber = i + 1, listId = 1L
            )
        }

    private data class Bitis(
        val n: Int,
        val tur: Int,
        val mac: Int,
        val keskinlik: Int,
        val kapi: String,
        val adaySayisi: Int,
        val ayniPuanliAday: Int,
        val oynanmamisAyniPuanliCift: Int,
        val puanDagilimi: String
    )

    private fun kosturVeTanila(n: Int, tohum: Long = 4242L): Bitis {
        val songs = sarkilar(n, tohum)
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        val hepsi = mutableListOf<Match>()
        var nextId = 1L
        var tur = 0

        while (tur < 200) {
            val pairing = EmreSystemCorrect.createHybridPairingSystem(state)
            if (!pairing.canContinue || pairing.matches.isEmpty()) break
            tur++
            val turunMaclari = pairing.matches.map {
                it.copy(
                    id = nextId++, round = tur,
                    winnerId = maxOf(it.songId1, it.songId2), isCompleted = true
                )
            }
            hepsi.addAll(turunMaclari)
            state = RankingEngine.processCorrectEmreResults(
                state, turunMaclari, pairing.byeTeam, allCompletedMatches = hepsi.toList()
            )
        }

        // Bitiş anındaki son eşleştirme denemesi
        val son = EmreSystemCorrect.createHybridPairingSystem(state)
        val beklenenCift = (if (n % 2 == 0) n else n - 1) / 2
        val kapi = when {
            son.candidateMatches.size < beklenenCift -> "A: tekrarsiz TAM eslestirme kurulamadi"
            else -> "B: ayni puanli eslesme yok (bitis kurali)"
        }
        val ayniPuanliAday = son.candidateMatches.count { it.team1.points == it.team2.points }

        // Bitiş anında hâlâ oynanmamış AYNI PUANLI çift sayısı
        val takimlar = state.teams
        var oynanmamisAyniPuanli = 0
        for (i in takimlar.indices) {
            for (j in i + 1 until takimlar.size) {
                val a = takimlar[i]; val b = takimlar[j]
                if (a.points != b.points) continue
                val anahtar = if (a.teamId < b.teamId) a.teamId to b.teamId else b.teamId to a.teamId
                if (anahtar !in state.matchHistory) oynanmamisAyniPuanli++
            }
        }

        val dagilim = takimlar.groupingBy { it.points }.eachCount()
            .toList().sortedByDescending { it.first }
            .joinToString(" ") { "${it.first}p:${it.second}" }

        return Bitis(
            n = n, tur = tur, mac = hepsi.size,
            keskinlik = EmreSystemCorrect.kesinlikRaporu(state).genelYuzde,
            kapi = kapi,
            adaySayisi = son.candidateMatches.size,
            ayniPuanliAday = ayniPuanliAday,
            oynanmamisAyniPuanliCift = oynanmamisAyniPuanli,
            puanDagilimi = dagilim
        )
    }

    @Test
    fun n12_erkenBitisininKapisi_veKacirilanKanit() {
        val olcumler = (8..16).map { kosturVeTanila(it) }
        olcumler.forEach { b ->
            println(
                "TANI n=%-3d tur=%-3d mac=%-4d keskinlik=%%%-4d %s | aday=%d (ayniPuanli=%d) | oynanmamis ayniPuanli cift=%d | %s"
                    .format(
                        b.n, b.tur, b.mac, b.keskinlik, b.kapi,
                        b.adaySayisi, b.ayniPuanliAday, b.oynanmamisAyniPuanliCift, b.puanDagilimi
                    )
            )
        }

        val n12 = olcumler.single { it.n == 12 }
        println("--- n=12 ozet: ${n12.kapi}, keskinlik %${n12.keskinlik}, " +
            "kapanista oynanmamis ayni puanli cift = ${n12.oynanmamisAyniPuanliCift}")

        // Ölçüm bozulmasın diye çerçeve assert'leri (davranış iddiası DEĞİL):
        assertTrue("n=12 hic tur oynamadi", n12.tur > 0)
        assertTrue(
            "Bitis kapisi belirlenemedi: ${n12.kapi}",
            n12.kapi.startsWith("A") || n12.kapi.startsWith("B")
        )
    }

    /**
     * Aykırılık listenin sırasına mı bağlı? Aynı n, farklı tohumla (yani farklı
     * başlangıç dizilişiyle) tekrar ölçülür. n=12 her tohumda erken bitiyorsa
     * kusur yapısaldır; yalnız 4242'de bitiyorsa senaryoya özgüdür.
     */
    @Test
    fun n11_n12_n13_tohumDuyarliligi() {
        listOf(11, 12, 13).forEach { n ->
            val satir = listOf(1L, 7L, 31L, 777L, 4242L).map { tohum ->
                val b = kosturVeTanila(n, tohum)
                "tohum=%d:%dtur/%%%d".format(tohum, b.tur, b.keskinlik)
            }
            println("TOHUM n=%-3d %s".format(n, satir.joinToString("  ")))
        }
        assertTrue(true) // ölçüm testi
    }
}
