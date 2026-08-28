package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.RankingEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * LİG MOTORU — DERİN KUSUR ARAMA TESTLERİ
 *
 * Hedef `RankingEngine.createLeagueMatches` (circle-method fikstür) ve
 * `calculateLeagueResults` (3/1/0 + averaj).
 *
 * Kovalananlar:
 *  - fikstür: her çift TAM BİR KEZ (çift devirde TAM İKİ KEZ), turlar dengeli,
 *    tek takımda herkes TAM BİR KEZ bye geçiyor mu
 *  - `points[match.songId1]!!` deseni yetim maç kaydıyla NPE atıyor mu
 *  - averaj: score1/score2 dolu maçlarda doğru mu, null skorlarda ne oluyor
 *  - geçişlilik: taş-kağıt-makas → sortedWith çöküyor mu
 *  - determinizm
 *
 * ⚠️ RankingEngine.kt KOORDİNATÖRÜN dosyası — yalnız okundu, yazılmadı.
 */
class LeagueEngineDeepTest {

    private fun makeSongs(count: Int): List<Song> =
        (1..count).map { i -> Song(id = i.toLong(), name = "Takim$i", listId = 3L) }

    private fun normalize(a: Long, b: Long) = if (a < b) Pair(a, b) else Pair(b, a)

    private fun completed(
        id: Long, s1: Long, s2: Long, winner: Long?,
        score1: Int? = null, score2: Int? = null, round: Int = 1
    ) = Match(
        id = id, listId = 3L, rankingMethod = "LEAGUE",
        songId1 = s1, songId2 = s2, winnerId = winner,
        score1 = score1, score2 = score2, round = round, isCompleted = true
    )

    // ==========================================================
    // ① FİKSTÜR — circle method
    // ==========================================================

    private fun assertTekDevreFikstur(n: Int) {
        val songs = makeSongs(n)
        val matches = RankingEngine.createLeagueMatches(songs)

        // Her çift tam bir kez
        val pairs = matches.map { normalize(it.songId1, it.songId2) }
        assertEquals("n=$n: aynı çift birden fazla kez eşleşti", pairs.size, pairs.toSet().size)
        assertEquals("n=$n: toplam maç sayısı n*(n-1)/2 olmalı", n * (n - 1) / 2, matches.size)

        // BYE takımı (id = -1) fikstüre sızmamalı
        assertFalse(
            "n=$n: BYE takımı (-1) gerçek maça girmiş",
            matches.any { it.songId1 == -1L || it.songId2 == -1L }
        )

        // Tur sayısı: çiftte n-1, tekte n
        val beklenenTur = if (n % 2 == 0) n - 1 else n
        assertEquals("n=$n: tur sayısı", beklenenTur, matches.map { it.round }.distinct().size)
        assertEquals(
            "n=$n: tur numaraları 1..$beklenenTur olmalı",
            (1..beklenenTur).toList(), matches.map { it.round }.distinct().sorted()
        )

        // Her turda bir takım en fazla bir maçta
        matches.groupBy { it.round }.forEach { (round, turMaclari) ->
            val katilanlar = turMaclari.flatMap { listOf(it.songId1, it.songId2) }
            assertEquals(
                "n=$n tur $round: bir takım aynı turda iki maçta",
                katilanlar.size, katilanlar.toSet().size
            )
            val beklenenMac = if (n % 2 == 0) n / 2 else (n - 1) / 2
            assertEquals("n=$n tur $round: maç sayısı", beklenenMac, turMaclari.size)
        }

        // Her takım n-1 maç oynar
        songs.forEach { s ->
            val macSayisi = matches.count { it.songId1 == s.id || it.songId2 == s.id }
            assertEquals("n=$n: takım ${s.id} maç sayısı", n - 1, macSayisi)
        }
    }

    @Test
    fun fikstur_ciftSayi_n4() = assertTekDevreFikstur(4)

    @Test
    fun fikstur_ciftSayi_n6() = assertTekDevreFikstur(6)

    @Test
    fun fikstur_ciftSayi_n8() = assertTekDevreFikstur(8)

    @Test
    fun fikstur_ciftSayi_n12() = assertTekDevreFikstur(12)

    @Test
    fun fikstur_tekSayi_n5() = assertTekDevreFikstur(5)

    @Test
    fun fikstur_tekSayi_n7() = assertTekDevreFikstur(7)

    @Test
    fun fikstur_tekSayi_n9() = assertTekDevreFikstur(9)

    @Test
    fun fikstur_tekSayida_herkesTamBirKezBoştaKalir() {
        // Tek takımda her turda bir takım maç yapmaz; herkes TAM BİR KEZ boşta kalmalı
        listOf(5, 7, 9).forEach { n ->
            val matches = RankingEngine.createLeagueMatches(makeSongs(n))
            val bostaSayisi = mutableMapOf<Long, Int>()
            (1L..n.toLong()).forEach { bostaSayisi[it] = 0 }

            matches.groupBy { it.round }.forEach { (_, turMaclari) ->
                val oynayanlar = turMaclari.flatMap { listOf(it.songId1, it.songId2) }.toSet()
                (1L..n.toLong()).filter { it !in oynayanlar }.forEach {
                    bostaSayisi[it] = (bostaSayisi[it] ?: 0) + 1
                }
            }
            assertEquals(
                "n=$n: bye dağılımı adil değil — $bostaSayisi",
                setOf(1), bostaSayisi.values.toSet()
            )
        }
    }

    @Test
    fun fikstur_ciftDevre_herIkiliTamIkiKez() {
        listOf(4, 5, 6).forEach { n ->
            val matches = RankingEngine.createLeagueMatches(makeSongs(n), doubleRoundRobin = true)
            assertEquals("n=$n: çift devrede maç sayısı n*(n-1)", n * (n - 1), matches.size)

            val sayim = matches.groupingBy { normalize(it.songId1, it.songId2) }.eachCount()
            assertEquals(
                "n=$n: her ikili tam iki kez oynamalı — bulunan ${sayim.values.toSet()}",
                setOf(2), sayim.values.toSet()
            )

            // Rövanşta ev sahibi/deplasman yer değiştirmeli
            val ilkDevre = matches.filter { it.round <= (if (n % 2 == 0) n - 1 else n) }
            ilkDevre.forEach { ilk ->
                val ronvans = matches.firstOrNull {
                    it.songId1 == ilk.songId2 && it.songId2 == ilk.songId1
                }
                assertNotNull("n=$n: ${ilk.songId1}-${ilk.songId2} maçının rövanşı yok", ronvans)
            }
        }
    }

    @Test
    fun fikstur_ciftDevre_turNumaralariCakismiyor() {
        val n = 6
        val matches = RankingEngine.createLeagueMatches(makeSongs(n), doubleRoundRobin = true)
        assertEquals("Çift devrede 2*(n-1) tur olmalı", 2 * (n - 1), matches.map { it.round }.distinct().size)
        matches.groupBy { it.round }.forEach { (round, turMaclari) ->
            val katilanlar = turMaclari.flatMap { listOf(it.songId1, it.songId2) }
            assertEquals(
                "Tur $round: bir takım aynı turda iki maçta",
                katilanlar.size, katilanlar.toSet().size
            )
        }
    }

    @Test
    fun fikstur_sinirDurumlari() {
        assertTrue("0 takımda maç olmamalı", RankingEngine.createLeagueMatches(emptyList()).isEmpty())
        assertTrue("1 takımda maç olmamalı", RankingEngine.createLeagueMatches(makeSongs(1)).isEmpty())

        val iki = RankingEngine.createLeagueMatches(makeSongs(2))
        assertEquals("2 takımda tam 1 maç", 1, iki.size)
        assertEquals(Pair(1L, 2L), normalize(iki[0].songId1, iki[0].songId2))
        assertEquals("2 takımda 1 tur", 1, iki[0].round)

        val ikiCift = RankingEngine.createLeagueMatches(makeSongs(2), doubleRoundRobin = true)
        assertEquals("2 takımda çift devre 2 maç", 2, ikiCift.size)
    }

    @Test
    fun fikstur_deterministik() {
        val songs = makeSongs(9)
        val a = RankingEngine.createLeagueMatches(songs)
        val b = RankingEngine.createLeagueMatches(songs)
        assertEquals(
            "Aynı girdi iki kez aynı fikstürü vermeli",
            a.map { Triple(it.songId1, it.songId2, it.round) },
            b.map { Triple(it.songId1, it.songId2, it.round) }
        )
    }

    // ==========================================================
    // ② PUANLAMA VE AVERAJ
    // ==========================================================

    @Test
    fun puanlama_galibiyet3_beraberlik1_maglubiyet0() {
        val songs = makeSongs(4)
        val matches = listOf(
            completed(1, 1L, 2L, 1L),   // 1 kazandı
            completed(2, 3L, 4L, null)  // berabere
        )
        val results = RankingEngine.calculateLeagueResults(songs, matches).associateBy { it.songId }
        assertEquals("Galibiyet 3 puan", 3.0, results[1L]?.score ?: -1.0, 0.0001)
        assertEquals("Mağlubiyet 0 puan", 0.0, results[2L]?.score ?: -1.0, 0.0001)
        assertEquals("Beraberlik 1 puan", 1.0, results[3L]?.score ?: -1.0, 0.0001)
        assertEquals("Beraberlik 1 puan", 1.0, results[4L]?.score ?: -1.0, 0.0001)
    }

    @Test
    fun puanlama_tamamlanmamisMacSayilmaz() {
        val songs = makeSongs(2)
        val yarim = listOf(completed(1, 1L, 2L, 1L).copy(isCompleted = false))
        val results = RankingEngine.calculateLeagueResults(songs, yarim)
        assertEquals("Tamamlanmamış maç puan üretmemeli", 0.0, results.first { it.songId == 1L }.score, 0.0001)
    }

    @Test
    fun puanlama_pozisyonlar1denNyeTekrarsiz() {
        val songs = makeSongs(6)
        val matches = listOf(
            completed(1, 1L, 2L, 1L), completed(2, 3L, 4L, 3L),
            completed(3, 5L, 6L, null), completed(4, 1L, 3L, 1L)
        )
        val results = RankingEngine.calculateLeagueResults(songs, matches)
        assertEquals(6, results.size)
        assertEquals((1..6).toList(), results.map { it.position }.sorted())
        assertEquals(
            "Hiçbir takım kaybolmamalı",
            (1L..6L).toSet(), results.map { it.songId }.toSet()
        )
        assertEquals("En yüksek puanlı 1. sırada olmalı", 1L, results.first { it.position == 1 }.songId)
    }

    @Test
    fun averaj_puanEsitkenGolFarkiSiralar() {
        val songs = makeSongs(4)
        val matches = listOf(
            completed(1, 1L, 3L, 1L, score1 = 5, score2 = 0),  // 1: +5
            completed(2, 2L, 4L, 2L, score1 = 1, score2 = 0)   // 2: +1
        )
        val results = RankingEngine.calculateLeagueResults(songs, matches)
        assertEquals("Puan eşitken averajı yüksek üstte olmalı", 1L, results[0].songId)
        assertEquals(2L, results[1].songId)
        assertEquals("Averajı kötü olan altta", 3L, results[3].songId)
    }

    @Test
    fun averaj_golFarkiEsitkenAtilanGolSiralar() {
        val songs = makeSongs(4)
        val matches = listOf(
            completed(1, 1L, 3L, 1L, score1 = 4, score2 = 2),  // 1: +2, attığı 4
            completed(2, 2L, 4L, 2L, score1 = 3, score2 = 1)   // 2: +2, attığı 3
        )
        val results = RankingEngine.calculateLeagueResults(songs, matches)
        assertEquals("Averaj eşitken çok gol atan üstte", 1L, results[0].songId)
        assertEquals(2L, results[1].songId)
    }

    @Test
    fun averaj_skorGirilmemisMaclarAverajUretmez() {
        val songs = makeSongs(2)
        val matches = listOf(completed(1, 1L, 2L, 1L)) // score1/score2 null
        val results = RankingEngine.calculateLeagueResults(songs, matches)
        assertEquals("Skorsuz maçta da puan verilmeli", 3.0, results.first { it.songId == 1L }.score, 0.0001)
        assertEquals("Sıralama puanla belirlenmeli", 1L, results[0].songId)
    }

    @Test
    fun averaj_tekTarafliSkor_yokSayilir() {
        // score1 dolu, score2 null → averaja girmemeli (ikisi de gerekli)
        val songs = makeSongs(4)
        val matches = listOf(
            completed(1, 1L, 2L, 1L, score1 = 9, score2 = null),
            completed(2, 3L, 4L, 3L, score1 = 1, score2 = 0)
        )
        val results = RankingEngine.calculateLeagueResults(songs, matches)
        assertEquals(
            "Yarım skorlu maç averaja girmemeli — averajı olan üste çıkmalı",
            3L, results[0].songId
        )
    }

    // ==========================================================
    // ③ VERİ ANOMALİLERİ — `points[...]!!` deseni
    // ==========================================================

    @Test
    fun yetimMac_silinmisOgeIleCokmemeli() {
        // 🔴 `points[match.songId1]!!` deseni: songs listesinde olmayan bir id
        // geldiğinde map'te anahtar yok → NPE.
        // Öğe silinmesi bu projede gerçek bir senaryo (maç kayıtları kalıyor).
        val songs = makeSongs(4)
        val matches = listOf(
            completed(1, 1L, 2L, 1L),
            completed(2, 1L, 999L, 1L)   // 999 silinmiş öğe
        )
        val results = RankingEngine.calculateLeagueResults(songs, matches)
        assertEquals("Yetim maç sonuç listesini bozmamalı", 4, results.size)
        assertEquals(
            "Hayattaki takımların puanı doğru kalmalı",
            (1L..4L).toSet(), results.map { it.songId }.toSet()
        )
    }

    @Test
    fun yetimMac_beraberlikDalindaCokmemeli() {
        val songs = makeSongs(4)
        val matches = listOf(completed(1, 998L, 999L, null)) // iki taraf da silinmiş, berabere
        val results = RankingEngine.calculateLeagueResults(songs, matches)
        assertEquals(4, results.size)
        results.forEach { assertEquals("Yetim maç puan üretmemeli", 0.0, it.score, 0.0001) }
    }

    @Test
    fun yetimMac_averajDalindaCokmemeli() {
        val songs = makeSongs(4)
        val matches = listOf(completed(1, 1L, 999L, 1L, score1 = 3, score2 = 1))
        val results = RankingEngine.calculateLeagueResults(songs, matches)
        assertEquals(4, results.size)
    }

    @Test
    fun bosGirdiler() {
        assertTrue(
            "Takım yoksa sonuç da olmamalı",
            RankingEngine.calculateLeagueResults(emptyList(), emptyList()).isEmpty()
        )
        val results = RankingEngine.calculateLeagueResults(makeSongs(3), emptyList())
        assertEquals("Maç yoksa herkes 0 puanla listelenmeli", 3, results.size)
        results.forEach { assertEquals(0.0, it.score, 0.0001) }
    }

    // ==========================================================
    // ④ GEÇİŞLİLİK VE DETERMİNİZM
    // ==========================================================

    @Test
    fun gecisliik_uclUDongu_cokmuyor() {
        // 1 yener 2, 2 yener 3, 3 yener 1 → hepsi 3 puan, averaj 0
        val songs = makeSongs(3)
        val matches = listOf(
            completed(1, 1L, 2L, 1L, score1 = 1, score2 = 0),
            completed(2, 2L, 3L, 2L, score1 = 1, score2 = 0),
            completed(3, 3L, 1L, 3L, score1 = 1, score2 = 0)
        )
        val results = RankingEngine.calculateLeagueResults(songs, matches)
        assertEquals(3, results.size)
        assertEquals((1..3).toList(), results.map { it.position }.sorted())
        results.forEach { assertEquals("Hepsi 3 puan olmalı", 3.0, it.score, 0.0001) }
    }

    @Test
    fun gecisliik_64TakimDongulSonuclar_cokmuyor() {
        // Büyük döngüsel turnuva: karşılaştırıcı toplam sıralı olmalı,
        // TimSort sözleşme ihlali atmamalı.
        val n = 64
        val songs = makeSongs(n)
        val matches = mutableListOf<Match>()
        var id = 1L
        for (i in 1..n) {
            for (step in 1..(n - 1) / 2) {
                val j = (i - 1 + step) % n + 1
                matches.add(completed(id++, i.toLong(), j.toLong(), i.toLong(), score1 = 1, score2 = 0))
            }
        }
        val results = RankingEngine.calculateLeagueResults(songs, matches)
        assertEquals("Tüm takımlar sonuçta olmalı", n, results.size)
        assertEquals("Pozisyonlar tekrarsız olmalı", n, results.map { it.position }.toSet().size)
    }

    @Test
    fun determinizm_ayniGirdiIkiKez_ayniSonuc() {
        val songs = makeSongs(10)
        val matches = (1..9).map { i ->
            completed(i.toLong(), i.toLong(), (i + 1).toLong(), i.toLong(), score1 = 2, score2 = 1)
        }
        val a = RankingEngine.calculateLeagueResults(songs, matches)
        val b = RankingEngine.calculateLeagueResults(songs, matches)
        assertEquals(
            "Aynı girdi aynı sıralamayı vermeli",
            a.map { it.songId to it.position }, b.map { it.songId to it.position }
        )
    }

    @Test
    fun tamLigSimulasyonu_puanToplamiTutuyor() {
        // n=8 tam lig: her maçta ev sahibi kazansın. Dağıtılan toplam puan
        // 3 * maç sayısı olmalı.
        val n = 8
        val songs = makeSongs(n)
        val fikstur = RankingEngine.createLeagueMatches(songs)
        val oynanmis = fikstur.mapIndexed { i, m ->
            m.copy(id = (i + 1).toLong(), winnerId = m.songId1, isCompleted = true)
        }
        val results = RankingEngine.calculateLeagueResults(songs, oynanmis)
        val toplam = results.sumOf { it.score }
        assertEquals(
            "Toplam puan 3 * maç sayısı olmalı",
            3.0 * oynanmis.size, toplam, 0.0001
        )
        assertEquals(n, results.size)
    }
}
