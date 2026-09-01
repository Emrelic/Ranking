package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.EmreSystemCorrect
import com.example.ranking.ranking.RankingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EMRE_CORRECT (Geliştirilmiş İsviçre — canlı motor) UZUN KOŞUM DEĞİŞMEZLERİ.
 *
 * Mevcut testlerin kapsadığı yer: Emre300SayiListesiTest (n=300/299 puan kasası
 * + kırmızı çizgi, HEP GALİBİYETLİ koşumda), EmreGeriAlmaMatematigiTest (geri
 * alma muhasebesi), EmreTurBozulmasiTest (n=80 tur bütünlüğü), KesinlikRaporuTest.
 *
 * Buradaki yeni yük:
 * ① BERABERLİKLİ koşumda puan korunumu — her turda, n=9/16/41/80.
 *    Beraberlik 0.5+0.5 yazdığı için puanlar erken eşitlenir; "aynı puanlı
 *    eşleşme" kuralı ve tiebreaker zinciri asıl burada zorlanır.
 * ② Bye adaleti UZUN tek sayılı koşumda (dağılımın min/max farkı ölçülür).
 * ③ Tiebreaker determinizmi: aynı maç kümesi iki kez işlenince birebir aynı sıra.
 * ④ Tek eşleşme kuralı (kırmızı çizgi) beraberlikli koşumda da geçerli.
 * ⑤ Tur sayısı / bitiş davranışı: n'in tek-çift uçları (2..17 + 41, 80).
 *
 * Motor koduna DOKUNULMAZ; kusur bulunursa test belgeler.
 */
class EmreCorrectDegismezlerTest {

    // ==========================================================
    // Yardımcılar
    // ==========================================================

    /** Öğe kimliği = sayı değeri; büyük sayı kazanır (gerçek sıra bilinir). */
    private fun sarkilar(n: Int, tohum: Long = 4242L): List<Song> =
        (1..n).shuffled(java.util.Random(tohum)).mapIndexed { i, deger ->
            Song(
                id = deger.toLong(), name = "Sayi $deger", artist = "", album = "",
                trackNumber = i + 1, listId = 1L
            )
        }

    private data class Kosum(
        val state: EmreSystemCorrect.EmreState,
        val allMatches: List<Match>,
        val turSayisi: Int,
        /** Tur sırasına göre bye geçen öğenin song id'si (yoksa null). */
        val byeler: List<Long?>,
        val turBasinaMac: List<Int>,
        val beraberlikSayisi: Int
    )

    /**
     * Tam turnuva koşumu.
     * @param beraberlikPeriyodu 0 ise beraberlik yok; k>0 ise her k. maç berabere biter.
     */
    private fun kostur(
        songs: List<Song>,
        beraberlikPeriyodu: Int = 0,
        maxTur: Int = 400,
        turBasiDenetim: ((EmreSystemCorrect.EmreState, List<Match>, List<Long?>) -> Unit)? = null
    ): Kosum {
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        val hepsi = mutableListOf<Match>()
        val byeler = mutableListOf<Long?>()
        val turBasinaMac = mutableListOf<Int>()
        var macSayaci = 0
        var beraberlik = 0
        var nextId = 1L
        var tur = 0

        while (tur < maxTur) {
            val pairing = EmreSystemCorrect.createHybridPairingSystem(state)
            if (!pairing.canContinue || pairing.matches.isEmpty()) break
            tur++

            val turunMaclari = pairing.matches.map { m ->
                macSayaci++
                val berabere = beraberlikPeriyodu > 0 && macSayaci % beraberlikPeriyodu == 0
                if (berabere) beraberlik++
                m.copy(
                    id = nextId++,
                    round = tur,
                    winnerId = if (berabere) null else maxOf(m.songId1, m.songId2),
                    isCompleted = true
                )
            }

            hepsi.addAll(turunMaclari)
            byeler.add(pairing.byeTeam?.id)
            turBasinaMac.add(turunMaclari.size)

            state = RankingEngine.processCorrectEmreResults(
                state, turunMaclari, pairing.byeTeam, allCompletedMatches = hepsi.toList()
            )
            turBasiDenetim?.invoke(state, hepsi.toList(), byeler.toList())
        }
        return Kosum(state, hepsi.toList(), tur, byeler.toList(), turBasinaMac.toList(), beraberlik)
    }

    /** ① Toplam puan = oynanmış maç + bye; ayrıca her takım puanı bağımsız yeniden hesaplanır. */
    private fun puanKorunumuDenetle(
        etiket: String,
        state: EmreSystemCorrect.EmreState,
        hepsi: List<Match>,
        byeler: List<Long?>
    ) {
        val byeSayisi = byeler.count { it != null }
        val toplam = state.teams.sumOf { it.points }
        assertEquals(
            "$etiket — kasa tutmuyor: ${hepsi.size} mac + $byeSayisi bye beklenirken toplam puan $toplam",
            (hepsi.size + byeSayisi).toDouble(), toplam, 1e-9
        )

        val beklenen = HashMap<Long, Double>()
        hepsi.forEach { m ->
            val w = m.winnerId
            if (w == null) {
                beklenen.merge(m.songId1, 0.5, Double::plus)
                beklenen.merge(m.songId2, 0.5, Double::plus)
            } else {
                beklenen.merge(w, 1.0, Double::plus)
            }
        }
        byeler.filterNotNull().forEach { beklenen.merge(it, 1.0, Double::plus) }
        state.teams.forEach { t ->
            assertEquals(
                "$etiket — ${t.song.name} puani mac+bye kayitlariyla uyusmuyor",
                beklenen[t.id] ?: 0.0, t.points, 1e-9
            )
        }
    }

    /** ④ Hiçbir ikili iki kez eşleşmemeli + bir takım aynı turda iki maçta olmamalı. */
    private fun tekEslesmeDenetle(etiket: String, hepsi: List<Match>) {
        val gorulen = HashSet<Pair<Long, Long>>()
        hepsi.forEach { m ->
            val cift = if (m.songId1 < m.songId2) m.songId1 to m.songId2 else m.songId2 to m.songId1
            assertTrue(
                "$etiket — KIRMIZI CIZGI ihlali: ${cift.first}-${cift.second} iki kez eslesti (tur ${m.round})",
                gorulen.add(cift)
            )
            assertTrue("$etiket — takim kendisiyle eslesti: ${m.songId1}", m.songId1 != m.songId2)
        }
        hepsi.groupBy { it.round }.forEach { (tur, maclar) ->
            val katilan = maclar.flatMap { listOf(it.songId1, it.songId2) }
            assertEquals(
                "$etiket — tur $tur bir takim birden cok macta: $katilan",
                katilan.size, katilan.toSet().size
            )
        }
    }

    private fun sira(state: EmreSystemCorrect.EmreState): List<Long> =
        RankingEngine.calculateCorrectEmreResults(state).sortedBy { it.position }.map { it.songId }

    // ==========================================================
    // ① PUAN KORUNUMU (beraberlikli) — her turda
    // ==========================================================

    @Test
    fun puanKorunumu_beraberlikliKosum_herTurda_n9_n16_n41_n80() {
        listOf(9, 16, 41, 80).forEach { n ->
            var turSayaci = 0
            val kosum = kostur(sarkilar(n), beraberlikPeriyodu = 3) { st, hepsi, byeler ->
                turSayaci++
                puanKorunumuDenetle("n=$n tur=$turSayaci", st, hepsi, byeler)
            }
            assertTrue("n=$n hic tur oynamadi", kosum.turSayisi > 0)
            assertTrue(
                "n=$n kosumunda hic beraberlik uretilmedi — sinav amacini kaybeder",
                kosum.beraberlikSayisi > 0
            )
            puanKorunumuDenetle("n=$n SON", kosum.state, kosum.allMatches, kosum.byeler)
            println(
                "1 n=%-3d tur=%-3d mac=%-5d beraberlik=%-4d bye=%d".format(
                    n, kosum.turSayisi, kosum.allMatches.size,
                    kosum.beraberlikSayisi, kosum.byeler.count { it != null }
                )
            )
        }
    }

    @Test
    fun puanKorunumu_hepBeraberlik_kasaBozulmaz() {
        // Uç durum: her maç berabere → tüm puanlar sürekli eşit, tiebreaker tek belirleyici
        val n = 16
        var tur = 0
        val kosum = kostur(sarkilar(n), beraberlikPeriyodu = 1) { st, hepsi, byeler ->
            tur++
            puanKorunumuDenetle("hepBeraberlik tur=$tur", st, hepsi, byeler)
        }
        assertEquals("Her mac berabere olmali", kosum.allMatches.size, kosum.beraberlikSayisi)
        tekEslesmeDenetle("hepBeraberlik n=$n", kosum.allMatches)
        println("1 hep-beraberlik n=$n -> tur=${kosum.turSayisi} mac=${kosum.allMatches.size}")
        assertTrue("Turnuva bitmedi (sonsuz dongu)", kosum.turSayisi < 400)
    }

    // ==========================================================
    // ② BYE ADALETİ (tek sayılı uzun koşum)
    // ==========================================================

    @Test
    fun byeAdaleti_tekSayiliUzunKosum_dagilimOlculur() {
        val satirlar = mutableListOf<String>()
        listOf(9, 15, 41, 81).forEach { n ->
            val kosum = kostur(sarkilar(n), beraberlikPeriyodu = 4)

            // Her turda tam bir bye olmalı (tek sayı)
            kosum.byeler.forEachIndexed { i, b ->
                assertNotNull("n=$n tur ${i + 1}: tek sayili turnuvada bye olmali", b)
            }
            // Her tur (n-1)/2 maç
            kosum.turBasinaMac.forEachIndexed { i, m ->
                assertEquals("n=$n tur ${i + 1}: mac sayisi (n-1)/2 olmali", (n - 1) / 2, m)
            }

            val sayimlar = kosum.state.teams.associate { it.song.name to it.byeCount }
            val toplam = sayimlar.values.sum()
            assertEquals(
                "n=$n: motorun byeCount toplami gercek bye sayisiyla uyusmuyor",
                kosum.byeler.count { it != null }, toplam
            )
            val min = sayimlar.values.min()
            val max = sayimlar.values.max()
            satirlar.add("2 n=%-3d tur=%-3d bye toplam=%-3d min=%d max=%d fark=%d"
                .format(n, kosum.turSayisi, toplam, min, max, max - min))
            println(satirlar.last())

            // Adalet: kimse ikinci bye'ını, herkes birincisini almadan alamaz.
            assertTrue(
                "n=$n BYE ADALETSIZ: min=$min max=$max (fark ${max - min} > 1). Dagilim: " +
                    sayimlar.entries.sortedByDescending { it.value }.take(5),
                max - min <= 1
            )
        }
    }

    @Test
    fun ciftSayiliTurnuvada_hicbirTurdaByeOlmaz() {
        listOf(10, 16, 40, 80).forEach { n ->
            val kosum = kostur(sarkilar(n), beraberlikPeriyodu = 5)
            kosum.byeler.forEachIndexed { i, b ->
                assertNull("n=$n tur ${i + 1}: cift sayida bye olmamali (bye=$b)", b)
            }
            kosum.turBasinaMac.forEachIndexed { i, m ->
                assertEquals("n=$n tur ${i + 1}: mac sayisi n/2 olmali", n / 2, m)
            }
            assertEquals("n=$n: hicbir takim bye gecmemeli",
                0, kosum.state.teams.sumOf { it.byeCount })
        }
    }

    // ==========================================================
    // ③ TİEBREAKER DETERMİNİZMİ
    // ==========================================================

    /** Kayıtlı maç kümesini sıfırdan yeniden işler (eşleştirme üretmeden). */
    private fun yenidenIsle(songs: List<Song>, kosum: Kosum): EmreSystemCorrect.EmreState {
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        val birikim = mutableListOf<Match>()
        val turlar = kosum.allMatches.groupBy { it.round }.toSortedMap()
        turlar.forEach { (tur, maclar) ->
            birikim.addAll(maclar)
            val byeId = kosum.byeler.getOrNull(tur - 1)
            val byeTeam = byeId?.let { id -> state.teams.find { it.id == id } }
            state = RankingEngine.processCorrectEmreResults(
                state, maclar, byeTeam, allCompletedMatches = birikim.toList()
            )
        }
        return state
    }

    @Test
    fun tiebreakerDeterminizmi_ayniMacKumesi_ikiKez_ayniSira() {
        listOf(9, 16, 41).forEach { n ->
            val songs = sarkilar(n)
            val kosum = kostur(songs, beraberlikPeriyodu = 3)

            val a = yenidenIsle(songs, kosum)
            val b = yenidenIsle(songs, kosum)

            assertEquals("n=$n: ayni mac kumesi iki kez islenince sira degisti", sira(a), sira(b))
            assertEquals("n=$n: iki islemede puanlar farkli",
                a.teams.sortedBy { it.id }.map { it.points },
                b.teams.sortedBy { it.id }.map { it.points })

            // Canlı koşumun kendisi de yeniden işlemeyle aynı sonucu vermeli
            // (motorda gizli/kalıcı durum yoksa).
            assertEquals(
                "n=$n: canli kosum ile kayitli maclarin yeniden islenmesi FARKLI sira veriyor — " +
                    "motorda mac kayitlarina yansimayan gizli durum var demektir",
                sira(kosum.state), sira(a)
            )
        }
    }

    @Test
    fun finalSonuclar_ustUsteCagrildiginda_degismez() {
        val songs = sarkilar(41)
        val kosum = kostur(songs, beraberlikPeriyodu = 3)
        val ilk = RankingEngine.calculateCorrectEmreResults(kosum.state)
        val ikinci = RankingEngine.calculateCorrectEmreResults(kosum.state)
        assertEquals("Ayni state iki kez hesaplaninca sira degisti",
            ilk.map { it.songId to it.position }, ikinci.map { it.songId to it.position })
        assertEquals("Pozisyonlar 1..n olmali",
            (1..songs.size).toList(), ilk.map { it.position }.sorted())
        assertEquals("Oge kaybi/tekrari var", songs.size, ilk.map { it.songId }.toSet().size)
    }

    // ==========================================================
    // ④ TEK EŞLEŞME KURALI (beraberlikli uzun koşum)
    // ==========================================================

    @Test
    fun tekEslesmeKurali_beraberlikliKosumda_ihlalYok() {
        listOf(9, 16, 41, 80).forEach { n ->
            val kosum = kostur(sarkilar(n), beraberlikPeriyodu = 3)
            tekEslesmeDenetle("n=$n", kosum.allMatches)

            val enCok = n * (n - 1) / 2
            assertTrue(
                "n=$n: mac sayisi ${kosum.allMatches.size} tam round-robin siniri $enCok asti",
                kosum.allMatches.size <= enCok
            )
            // Motorun kendi geçmişi de maç kayıtlarıyla birebir olmalı
            assertEquals(
                "n=$n: motorun matchHistory boyutu oynanan mac sayisiyla uyusmuyor",
                kosum.allMatches.size, kosum.state.matchHistory.size
            )
        }
    }

    // ==========================================================
    // ⑤ TUR SAYISI / BİTİŞ DAVRANIŞI
    // ==========================================================

    @Test
    fun turSayisiVeBitis_tekCiftUclari_olculur() {
        val olcumler = mutableListOf<String>()
        (2..17).plus(listOf(41, 80)).forEach { n ->
            val kosum = kostur(sarkilar(n))
            val kesinlik = EmreSystemCorrect.kesinlikRaporu(kosum.state)
            olcumler.add(
                "5 n=%-3d tur=%-3d mac=%-5d bye=%-3d keskinlik=%d".format(
                    n, kosum.turSayisi, kosum.allMatches.size,
                    kosum.byeler.count { it != null }, kesinlik.genelYuzde
                )
            )
            println(olcumler.last())

            assertTrue("n=$n hic tur oynanmadi", kosum.turSayisi >= 1)
            assertTrue("n=$n tur sayisi n'i asti (${kosum.turSayisi})", kosum.turSayisi <= n)
            tekEslesmeDenetle("n=$n", kosum.allMatches)
            puanKorunumuDenetle("n=$n SON", kosum.state, kosum.allMatches, kosum.byeler)

            // Turnuva bittiğinde motor yeni tur üretmemeli (durma garantisi)
            val son = EmreSystemCorrect.createHybridPairingSystem(kosum.state)
            assertTrue(
                "n=$n: turnuva bitti sayildi ama motor hala ${son.matches.size} mac uretiyor",
                !son.canContinue || son.matches.isEmpty()
            )
        }
        println(olcumler.joinToString("\n"))
    }

    @Test
    fun ucNoktalari_n0_n1_cokmez() {
        val bos = EmreSystemCorrect.initializeEmreTournament(emptyList())
        val p0 = EmreSystemCorrect.createHybridPairingSystem(bos)
        assertTrue("n=0'da mac uretilmemeli", p0.matches.isEmpty())
        assertTrue("n=0'da sonuc bos olmali",
            RankingEngine.calculateCorrectEmreResults(bos).isEmpty())

        val tek = EmreSystemCorrect.initializeEmreTournament(sarkilar(1))
        val p1 = EmreSystemCorrect.createHybridPairingSystem(tek)
        assertTrue("n=1'de mac uretilmemeli", p1.matches.isEmpty())
        assertEquals("n=1'de tek oge 1. sirada olmali",
            1, RankingEngine.calculateCorrectEmreResults(tek).single().position)
    }
}
