package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.EmreSystemCorrect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * GELİŞTİRİLMİŞ İSVİÇRE — 300 SAYILIK GERÇEKÇİ SENARYO TESTİ (kullanıcı isteği)
 *
 * Liste: 1..200 arası değerler, 300 öğe — 200 değerin HEPSİ birer kez, bunlardan
 * rastgele (sabit tohumlu) seçilen 100 tanesi İKİNCİ kez (mükerrer). Kural:
 * büyük sayı kazanır; AYNI sayılar eşleşirse BERABERE.
 *
 * Denetlenenler:
 *  ① Puan toplamı muhasebesi — her tur sonunda toplam puan tam olarak
 *    "o ana dek oynanan maç sayısı" olmalı (galibiyet 1+0, beraberlik
 *    0.5+0.5 → her maç kasaya 1 puan koyar; 300 çift takım → bye yok).
 *    Ayrıca her takımın puanı, kendi maç kayıtlarından bağımsız yeniden
 *    hesaplanıp motorun tuttuğu puanla karşılaştırılır.
 *  ② Geri al / tablodan değiştir eşdeğerliği — aynı turnuva bir kez temiz,
 *    bir kez "kaotik" (önce yanlış oy → geri al → doğru oy; bazı maçlar
 *    tablodan değiştirilir) oynanır. Tur kapanışı nihai maç kayıtlarını
 *    işlediği için iki koşunun final sıralaması BİREBİR aynı olmalı.
 *    (Uygulamada geri al = maçı oynanmamışa çevirmek, tablodan değiştirme =
 *    tamamlanmış maçın kazananını güncellemek; ikisi de tur kapanmadan
 *    maç kaydını değiştirir — motor yalnız nihai kayıtları görür.)
 *  ③ Final sıralama — 300 öğenin hepsi sonuçta var mı, pozisyon sırası puanla
 *    tutarlı mı, ve değerler büyükten küçüğe ne kadar sıralı (tam sıralılık
 *    İsviçre'nin GARANTİSİ DEĞİL — sistem "aynı puanlı eşleşme kalmayınca"
 *    biter; test kaliteyi SAYIYLA ölçer ve alt sınırı doğrular).
 */
class Emre300SayiListesiTest {

    /**
     * n=300 turnuvası pahalı (~3 dk/koşum). Veri ve koşumlar DETERMİNİSTİK
     * olduğu için testler arasında paylaşılır: temiz + kaotik toplam İKİ
     * koşum — her testte yeniden oynatmak paketi 13+ dakikaya şişiriyordu.
     */
    companion object {
        private val ortak by lazy { Emre300SayiListesiTest().sayiListesi() }
        private val temizKosum by lazy {
            val (songs, deger) = ortak
            Emre300SayiListesiTest().oynat(songs, deger, kaos = false)
        }
        private val kaotikKosum by lazy {
            val (songs, deger) = ortak
            Emre300SayiListesiTest().oynat(songs, deger, kaos = true)
        }
        private val ortak299 by lazy { Emre300SayiListesiTest().sayiListesi299() }
        private val kosum299 by lazy {
            val (songs, deger) = ortak299
            Emre300SayiListesiTest().oynat(songs, deger, kaos = false)
        }
    }

    // ==========================================================
    // VERİ: 300 sayı (200 tekil + 100 mükerrer), sabit tohum
    // ==========================================================

    /** songId → sayı değeri. Kimlikler 1..300, değerler 1..200. */
    private fun sayiListesi(): Pair<List<Song>, Map<Long, Int>> {
        val rnd = Random(42)
        val degerler = (1..200).toMutableList()          // her değer bir kez
        degerler += (1..200).shuffled(rnd).take(100)     // 100 tanesi ikinci kez
        degerler.shuffle(rnd)                            // giriş sırası rastgele
        check(degerler.size == 300)

        val songs = degerler.mapIndexed { i, deger ->
            Song(id = (i + 1).toLong(), name = "Sayı $deger", listId = 1L)
        }
        val degerHaritasi = degerler.mapIndexed { i, deger ->
            (i + 1).toLong() to deger
        }.toMap()
        return Pair(songs, degerHaritasi)
    }

    /** Büyük sayı kazanır; aynı sayılar BERABERE (kullanıcı kuralı). */
    private fun kazanan(m: Match, deger: Map<Long, Int>): Long? {
        val d1 = deger.getValue(m.songId1)
        val d2 = deger.getValue(m.songId2)
        return when {
            d1 > d2 -> m.songId1
            d2 > d1 -> m.songId2
            else -> null
        }
    }

    /** 299 öğelik TEK SAYILI varyant: 200 değer birer kez + 99'u ikinci kez. */
    private fun sayiListesi299(): Pair<List<Song>, Map<Long, Int>> {
        val rnd = Random(43)
        val degerler = (1..200).toMutableList()
        degerler += (1..200).shuffled(rnd).take(99)
        degerler.shuffle(rnd)
        check(degerler.size == 299)
        val songs = degerler.mapIndexed { i, deger ->
            Song(id = (i + 1).toLong(), name = "Sayı $deger", listId = 1L)
        }
        return Pair(songs, degerler.mapIndexed { i, d -> (i + 1).toLong() to d }.toMap())
    }

    private data class Kosum(
        val finalState: EmreSystemCorrect.EmreState,
        val allMatches: List<Match>,
        val turSayisi: Int,
        /** Tur sırasıyla bye geçen takım id'leri (çift sayıda boş). */
        val byeler: List<Long>
    )

    /**
     * Turnuvayı sonuna kadar oynatır.
     *
     * @param kaos true ise her turda uygulamadaki iki düzeltme akışı taklit
     * edilir (tur kapanmadan): her turun ilk 10 maçında önce YANLIŞ sonuç
     * yazılır, sonra GERİ ALINIR (oynanmamış hale) ve doğru sonuç yazılır;
     * 11-20. maçlarda yanlış sonuç yazılıp TABLODAN DEĞİŞTİRİLİR (kayıt
     * güncellenir, geri alınmaz). Motor tur kapanışında yalnız nihai
     * kayıtları gördüğü için sonuç temiz koşumla aynı olmalıdır.
     */
    private fun oynat(
        songs: List<Song>,
        deger: Map<Long, Int>,
        kaos: Boolean,
        maxTur: Int = 500
    ): Kosum {
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        val allMatches = mutableListOf<Match>()
        val byeler = mutableListOf<Long>()
        var nextId = 1L
        var tur = 0

        while (tur < maxTur) {
            val pairing = EmreSystemCorrect.createHybridPairingSystem(state)
            if (!pairing.canContinue || pairing.matches.isEmpty()) break
            tur++
            pairing.byeTeam?.let { byeler.add(it.id) }

            val turunMaclari = pairing.matches.map { it.copy(id = nextId++) }.toMutableList()

            if (kaos) {
                // ── Uygulama akışının taklidi ──
                // (a) GERİ AL: ilk 10 maça yanlış oy → oynanmamışa çevir → doğru oy.
                for (i in 0 until minOf(10, turunMaclari.size)) {
                    val m = turunMaclari[i]
                    val yanlis = if (kazanan(m, deger) == m.songId1) m.songId2 else m.songId1
                    var kayit = m.copy(winnerId = yanlis, isCompleted = true)   // yanlış oy
                    kayit = kayit.copy(winnerId = null, isCompleted = false)    // GERİ AL
                    turunMaclari[i] = kayit.copy(
                        winnerId = kazanan(kayit, deger), isCompleted = true    // doğru oy
                    )
                }
                // (b) TABLODAN DEĞİŞTİR: 11-20. maçlara yanlış oy, sonra kazanan
                // güncellenir (isCompleted true kalır — dialogdaki davranış).
                for (i in 10 until minOf(20, turunMaclari.size)) {
                    val m = turunMaclari[i]
                    val yanlis = if (kazanan(m, deger) == m.songId1) m.songId2 else m.songId1
                    val kayit = m.copy(winnerId = yanlis, isCompleted = true)   // yanlış oy
                    turunMaclari[i] = kayit.copy(winnerId = kazanan(kayit, deger)) // DEĞİŞTİR
                }
                // (c) Kalan maçlar doğrudan doğru oyla tamamlanır.
                for (i in 20 until turunMaclari.size) {
                    val m = turunMaclari[i]
                    turunMaclari[i] = m.copy(winnerId = kazanan(m, deger), isCompleted = true)
                }
            } else {
                for (i in turunMaclari.indices) {
                    val m = turunMaclari[i]
                    turunMaclari[i] = m.copy(winnerId = kazanan(m, deger), isCompleted = true)
                }
            }

            allMatches.addAll(turunMaclari)
            state = EmreSystemCorrect.processRoundResults(
                state, turunMaclari.toList(), pairing.byeTeam, allMatches.toList()
            )

            // ① PUAN MUHASEBESİ — her tur kapanışında denetlenir
            puanMuhasebesiniDogrula(state, allMatches, byeler)
        }
        return Kosum(state, allMatches, tur, byeler)
    }

    /**
     * ① Toplam puan = oynanan maç sayısı + bye sayısı (her maç kasaya 1 puan
     * koyar, her bye 1 puan). Ayrıca her takımın puanı maç kayıtlarından ve
     * bye listesinden BAĞIMSIZ yeniden hesaplanıp motorunkiyle karşılaştırılır.
     */
    private fun puanMuhasebesiniDogrula(
        state: EmreSystemCorrect.EmreState,
        allMatches: List<Match>,
        byeler: List<Long>
    ) {
        val toplamPuan = state.teams.sumOf { it.points }
        assertEquals(
            "Toplam puan kasası tutmuyor: ${allMatches.size} maç + ${byeler.size} bye ama toplam puan $toplamPuan",
            (allMatches.size + byeler.size).toDouble(),
            toplamPuan,
            1e-9
        )

        val beklenen = HashMap<Long, Double>()
        allMatches.forEach { m ->
            when (m.winnerId) {
                null -> {
                    beklenen.merge(m.songId1, 0.5, Double::plus)
                    beklenen.merge(m.songId2, 0.5, Double::plus)
                }
                else -> beklenen.merge(m.winnerId!!, 1.0, Double::plus)
            }
        }
        byeler.forEach { beklenen.merge(it, 1.0, Double::plus) }
        state.teams.forEach { t ->
            assertEquals(
                "Takım ${t.id} (${t.song.name}) puanı maç+bye kayıtlarıyla uyuşmuyor",
                beklenen[t.id] ?: 0.0,
                t.points,
                1e-9
            )
        }
    }

    // ==========================================================
    // TESTLER
    // ==========================================================

    @Test
    fun tamTurnuva_puanKasasi_veKirmiziCizgi() {
        val (_, deger) = ortak
        val kosum = temizKosum

        assertTrue("Turnuva hiç tur oynamadı", kosum.turSayisi > 0)

        // Kırmızı çizgi: iki takım yalnız bir kez eşleşir
        val gorulen = mutableSetOf<Pair<Long, Long>>()
        kosum.allMatches.forEach { m ->
            val cift = if (m.songId1 < m.songId2) m.songId1 to m.songId2 else m.songId2 to m.songId1
            assertTrue(
                "KIRMIZI ÇİZGİ: ${cift.first}-${cift.second} ikinci kez eşleşti (tur ${m.round})",
                gorulen.add(cift)
            )
        }

        // Aynı sayılar eşleştiyse kayıt gerçekten BERABERE mi
        kosum.allMatches.forEach { m ->
            if (deger.getValue(m.songId1) == deger.getValue(m.songId2)) {
                assertEquals(
                    "Mükerrer çift (${deger[m.songId1]}) eşleşti ama beraberlik yazılmamış",
                    null, m.winnerId
                )
            }
        }

        // 300 çift sayıda takım: hiçbir turda bye olmamalı, her tur 150 maç
        val turBasinaMac = kosum.allMatches.groupBy { it.round }.mapValues { it.value.size }
        turBasinaMac.forEach { (tur, adet) ->
            assertEquals("Tur $tur tam eşleştirme değil ($adet maç)", 150, adet)
        }

        println("== 300 SAYI TEMİZ KOŞUM: ${kosum.turSayisi} tur, ${kosum.allMatches.size} maç ==")
    }

    @Test
    fun geriAl_veTablodanDegistir_sonucuDegistirmez() {
        val temiz = temizKosum
        val kaotik = kaotikKosum

        assertEquals(
            "Geri al/tablodan değiştir akışı TUR SAYISINI değiştirdi",
            temiz.turSayisi, kaotik.turSayisi
        )

        val temizSonuc = EmreSystemCorrect.calculateFinalResults(temiz.finalState)
        val kaotikSonuc = EmreSystemCorrect.calculateFinalResults(kaotik.finalState)

        assertEquals(temizSonuc.size, kaotikSonuc.size)
        temizSonuc.zip(kaotikSonuc).forEach { (a, b) ->
            assertEquals(
                "Pozisyon ${a.position} farklı takım: temiz=${a.songId} kaotik=${b.songId} — " +
                    "geri al / tablodan değiştirme kalıcı iz bıraktı",
                a.songId, b.songId
            )
            assertEquals("Takım ${a.songId} puanı farklı", a.score, b.score, 1e-9)
        }
        println("== GERİ AL / TABLODAN DEĞİŞTİR: ${temizSonuc.size} pozisyonun tamamı birebir aynı ==")
    }

    @Test
    fun finalSiralama_kayipYok_puanTutarli_veBuyuktenKucugeOlcumu() {
        val (_, deger) = ortak
        val kosum = temizKosum
        val sonuc = EmreSystemCorrect.calculateFinalResults(kosum.finalState)

        // Kayıp yok: 300 öğenin hepsi, her biri bir kez
        assertEquals("Sonuç listesinde öğe kaybı", 300, sonuc.size)
        assertEquals("Sonuçta mükerrer takım var", 300, sonuc.map { it.songId }.toSet().size)

        // Pozisyonlar 1..300 boşluksuz
        assertEquals((1..300).toList(), sonuc.sortedBy { it.position }.map { it.position })

        // Puan, pozisyon sırasında hiçbir yerde ARTMAMALI (üstteki alttan az olamaz)
        val sirali = sonuc.sortedBy { it.position }
        sirali.zipWithNext().forEach { (ust, alt) ->
            assertTrue(
                "Puan sırası bozuk: poz ${ust.position} (${ust.score}) < poz ${alt.position} (${alt.score})",
                ust.score >= alt.score - 1e-9
            )
        }

        // Büyükten küçüğe sıralılık ÖLÇÜMÜ
        val degerSirasi = sirali.map { deger.getValue(it.songId) }
        var tersKomsu = 0
        degerSirasi.zipWithNext().forEach { (a, b) -> if (a < b) tersKomsu++ }

        var inversiyon = 0L
        for (i in degerSirasi.indices) {
            for (j in i + 1 until degerSirasi.size) {
                if (degerSirasi[i] < degerSirasi[j]) inversiyon++
            }
        }
        val maxInversiyon = 300L * 299L / 2L
        val sirililikYuzdesi = 100.0 * (1.0 - inversiyon.toDouble() / maxInversiyon)

        // Mükerrer çiftler: aynı sayının iki kopyası puanca ne kadar ayrık?
        val kopyalar = deger.entries.groupBy({ it.value }, { it.key }).filterValues { it.size == 2 }
        val puanlar = sonuc.associate { it.songId to it.score }
        val kopyaPuanFarki = kopyalar.values.map { (a, b) ->
            kotlin.math.abs(puanlar.getValue(a) - puanlar.getValue(b))
        }

        println("== FİNAL SIRALAMA ÖLÇÜMÜ (${kosum.turSayisi} tur) ==")
        println("İlk 10 değer: ${degerSirasi.take(10)}")
        println("Son 10 değer: ${degerSirasi.takeLast(10)}")
        println("Ters komşu çifti: $tersKomsu / 299")
        println("İnversiyon: $inversiyon / $maxInversiyon (sıralılık %${"%.2f".format(sirililikYuzdesi)})")
        println("Mükerrer kopya puan farkı — ort: ${"%.3f".format(kopyaPuanFarki.average())}, en büyük: ${kopyaPuanFarki.max()}")

        // İsviçre tam sıralama GARANTİ ETMEZ (sınırlı tur); ama "büyükten
        // küçüğe" eğilimi çok güçlü olmalı. Alt sınır: %90 sıralılık.
        assertTrue(
            "Sıralama 'büyükten küçüğe' eğiliminden çok uzak: %${"%.2f".format(sirililikYuzdesi)}",
            sirililikYuzdesi >= 90.0
        )
        // En büyük değer(ler) ilk sırada yenilgisiz olmalı: 200'ün kopyaları en üst dilimde
        assertTrue(
            "En büyük sayı (200) ilk 5'te değil: ilk 5 = ${degerSirasi.take(5)}",
            degerSirasi.take(5).contains(200)
        )
    }

    // ==========================================================
    // TEK SAYILI VARYANT: 299 öğe (200 tekil + 99 mükerrer) — BYE devrede
    // ==========================================================

    @Test
    fun tekSayili299_byeKasasi_rotasyon_veSiralama() {
        val (_, deger) = ortak299
        val kosum = kosum299

        assertTrue("299'luk turnuva hiç tur oynamadı", kosum.turSayisi > 0)

        // TEK SAYIDA HER TURDA tam (299-1)/2 = 149 maç + tam 1 bye olmalı
        val turBasinaMac = kosum.allMatches.groupBy { it.round }.mapValues { it.value.size }
        turBasinaMac.forEach { (tur, adet) ->
            assertEquals("Tur $tur tam eşleştirme değil ($adet maç, 149 olmalı)", 149, adet)
        }
        assertEquals(
            "Her turda tam 1 bye olmalı: ${kosum.turSayisi} tur, ${kosum.byeler.size} bye",
            kosum.turSayisi, kosum.byeler.size
        )

        // BYE ROTASYONU ADİL: kimse ikinci bye'ı herkes almadan almaz
        val byeSayaci = kosum.byeler.groupingBy { it }.eachCount()
        val enCok = byeSayaci.values.maxOrNull() ?: 0
        val herkesinByesi = if (byeSayaci.size == 299) (byeSayaci.values.minOrNull() ?: 0) else 0
        assertTrue(
            "Bye rotasyonu adaletsiz: bir takım $enCok bye almışken herkese $herkesinByesi bye düşmüş",
            enCok - herkesinByesi <= 1
        )

        // Kırmızı çizgi
        val gorulen = mutableSetOf<Pair<Long, Long>>()
        kosum.allMatches.forEach { m ->
            val cift = if (m.songId1 < m.songId2) m.songId1 to m.songId2 else m.songId2 to m.songId1
            assertTrue("KIRMIZI ÇİZGİ (299): $cift ikinci kez eşleşti", gorulen.add(cift))
        }

        // Mükerrer eşleşmeler beraberlik mi
        kosum.allMatches.forEach { m ->
            if (deger.getValue(m.songId1) == deger.getValue(m.songId2)) {
                assertEquals("Mükerrer çift beraberlik değil", null, m.winnerId)
            }
        }

        // Final sıralama: kayıp yok + puan monotonluğu + sıralılık ölçümü
        val sonuc = EmreSystemCorrect.calculateFinalResults(kosum.finalState)
        assertEquals(299, sonuc.size)
        assertEquals(299, sonuc.map { it.songId }.toSet().size)
        val sirali = sonuc.sortedBy { it.position }
        sirali.zipWithNext().forEach { (ust, alt) ->
            assertTrue(
                "Puan sırası bozuk: poz ${ust.position} (${ust.score}) < poz ${alt.position} (${alt.score})",
                ust.score >= alt.score - 1e-9
            )
        }

        val degerSirasi = sirali.map { deger.getValue(it.songId) }
        var inversiyon = 0L
        for (i in degerSirasi.indices) {
            for (j in i + 1 until degerSirasi.size) {
                if (degerSirasi[i] < degerSirasi[j]) inversiyon++
            }
        }
        val maxInv = 299L * 298L / 2L
        val yuzde = 100.0 * (1.0 - inversiyon.toDouble() / maxInv)
        println("== 299 TEK SAYILI: ${kosum.turSayisi} tur, ${kosum.allMatches.size} maç, ${kosum.byeler.size} bye ==")
        println("İlk 10: ${degerSirasi.take(10)} · Son 10: ${degerSirasi.takeLast(10)}")
        println("İnversiyon: $inversiyon / $maxInv (sıralılık %${"%.2f".format(yuzde)})")
        assertTrue(
            "299'luk sıralama 'büyükten küçüğe' eğiliminden uzak: %${"%.2f".format(yuzde)}",
            yuzde >= 90.0
        )
        assertTrue(
            "En büyük sayı (200) ilk 5'te değil: ${degerSirasi.take(5)}",
            degerSirasi.take(5).contains(200)
        )
    }
}
