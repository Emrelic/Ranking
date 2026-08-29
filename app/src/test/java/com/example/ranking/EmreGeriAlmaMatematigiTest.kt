package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.EmreSystemCorrect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GERİ ALMA MATEMATİĞİ — nokta atışı testler (kullanıcı sorusu üzerine).
 *
 * Emre300SayiListesiTest geri al/tablodan değiştir akışının SONUCU
 * DEĞİŞTİRMEDİĞİNİ (nihai kayıt aynıysa sıralama aynı) kanıtlıyor. Buradaki
 * testler tersini kanıtlar: geri alınıp FARKLI sonuç yazılınca puanlar eski
 * sonucun HİÇBİR izini taşımamalı — galibiyetin 1'i silinmeli, beraberliğin
 * 0.5'leri kalmamalı — ve bye'lı (tek sayılı) listede kasa yine tutmalı.
 *
 * Uygulama akışıyla ilişki: geri al = maç kaydını oynanmamışa çevirmek,
 * tablodan değiştir = tamamlanmış kaydın kazananını güncellemek. Tur
 * kapanışı (processRoundResults) yalnız NİHAİ kayıtları işlediği için bu
 * testler kayıt değiştirme senaryolarının puan matematiğini doğrudan sınar.
 * (ViewModel'in tur kapanışında aynı turu iki kez İŞLEMEMESİ ayrı bir
 * korumadır ve EmreFixRegressionTest'te zaten test edilir.)
 */
class EmreGeriAlmaMatematigiTest {

    private fun songs(n: Int): List<Song> =
        (1..n).map { Song(id = it.toLong(), name = "Takim$it", listId = 1L) }

    private fun puan(state: EmreSystemCorrect.EmreState, id: Long): Double =
        state.teams.first { it.id == id }.points

    /** İlk turun eşleştirmesini kurar, sonuçları verilen kayıtlarla işler. */
    private fun ilkTuruIsle(
        n: Int,
        sonucla: (List<Match>) -> List<Match>
    ): Triple<EmreSystemCorrect.EmreState, List<Match>, EmreSystemCorrect.EmreTeam?> {
        val state = EmreSystemCorrect.initializeEmreTournament(songs(n))
        val pairing = EmreSystemCorrect.createHybridPairingSystem(state)
        assertTrue(pairing.canContinue)
        var id = 1L
        val kayitlar = sonucla(pairing.matches.map { it.copy(id = id++) })
        val yeni = EmreSystemCorrect.processRoundResults(
            state, kayitlar, pairing.byeTeam, kayitlar
        )
        return Triple(yeni, kayitlar, pairing.byeTeam)
    }

    // ==========================================================
    // ① Geri al + rakibe çevir: eski kazananın 1 puanı SİLİNMELİ
    // ==========================================================
    @Test
    fun geriAlinipRakibeCevrilince_eskiKazananinPuaniSilinir() {
        val (state, kayitlar, _) = ilkTuruIsle(6) { maclar ->
            maclar.mapIndexed { i, m ->
                if (i == 0) {
                    // Kullanıcı akışı: önce takım1'e oy verildi...
                    var kayit = m.copy(winnerId = m.songId1, isCompleted = true)
                    // ...GERİ ALINDI (oynanmamış: sonuç da puan da yok)...
                    kayit = kayit.copy(winnerId = null, isCompleted = false)
                    // ...sonra takım2'ye oy verildi.
                    kayit.copy(winnerId = kayit.songId2, isCompleted = true)
                } else {
                    m.copy(winnerId = m.songId1, isCompleted = true)
                }
            }
        }
        val m0 = kayitlar[0]
        assertEquals("Eski kazananın puanı silinmemiş", 0.0, puan(state, m0.songId1), 1e-9)
        assertEquals("Yeni kazanan 1 puan almalı", 1.0, puan(state, m0.songId2), 1e-9)
    }

    // ==========================================================
    // ② Beraberlik geri alınıp galibiyete çevrilince 0.5'ler kalmamalı
    // ==========================================================
    @Test
    fun beraberlikGeriAlinipGalibiyeteCevrilince_yarimPuanlarKalmaz() {
        val (state, kayitlar, _) = ilkTuruIsle(6) { maclar ->
            maclar.mapIndexed { i, m ->
                if (i == 0) {
                    var kayit = m.copy(winnerId = null, isCompleted = true)   // beraberlik oyu
                    kayit = kayit.copy(isCompleted = false)                   // geri al
                    kayit.copy(winnerId = kayit.songId1, isCompleted = true)  // galibiyete çevir
                } else {
                    m.copy(winnerId = null, isCompleted = true)
                }
            }
        }
        val m0 = kayitlar[0]
        assertEquals("Kazanan tam 1 almalı (0.5 kalıntısı yok)", 1.0, puan(state, m0.songId1), 1e-9)
        assertEquals("Kaybeden 0 almalı (0.5 kalıntısı yok)", 0.0, puan(state, m0.songId2), 1e-9)
    }

    // ==========================================================
    // ③ Galibiyet TABLODAN beraberliğe çevrilince 1 silinip 0.5+0.5 yazılmalı
    // ==========================================================
    @Test
    fun galibiyetTablodanBeraberligeCevrilince_birPuanSilinirYarimlarYazilir() {
        val (state, kayitlar, _) = ilkTuruIsle(6) { maclar ->
            maclar.mapIndexed { i, m ->
                if (i == 0) {
                    // Tablodan değiştirme: kayıt tamamlanmış KALIR, kazanan değişir
                    val kayit = m.copy(winnerId = m.songId1, isCompleted = true)
                    kayit.copy(winnerId = null)
                } else {
                    m.copy(winnerId = m.songId1, isCompleted = true)
                }
            }
        }
        val m0 = kayitlar[0]
        assertEquals(0.5, puan(state, m0.songId1), 1e-9)
        assertEquals(0.5, puan(state, m0.songId2), 1e-9)
    }

    // ==========================================================
    // ④ TEK SAYILI (bye'lı) turnuvada kaotik geri almayla kasa denetimi:
    //    toplam puan == oynanan maç + bye sayısı, her turda
    // ==========================================================
    @Test
    fun tekSayiliListede_geriAlmaliKosumda_kasaHerTurTutar() {
        var state = EmreSystemCorrect.initializeEmreTournament(songs(21))
        val tumMaclar = mutableListOf<Match>()
        var byeSayisi = 0
        var id = 1L
        var tur = 0

        while (tur < 200) {
            val pairing = EmreSystemCorrect.createHybridPairingSystem(state)
            if (!pairing.canContinue || pairing.matches.isEmpty()) break
            tur++
            if (pairing.byeTeam != null) byeSayisi++

            val kayitlar = pairing.matches.map { it.copy(id = id++) }.mapIndexed { i, m ->
                // Her turun ilk maçı: yanlış oy → geri al → doğru oy (kaos)
                val kazanan = maxOf(m.songId1, m.songId2) // deterministik: büyük id kazanır
                if (i == 0) {
                    var kayit = m.copy(winnerId = minOf(m.songId1, m.songId2), isCompleted = true)
                    kayit = kayit.copy(winnerId = null, isCompleted = false)
                    kayit.copy(winnerId = kazanan, isCompleted = true)
                } else if (i == 1) {
                    m.copy(winnerId = null, isCompleted = true) // beraberlik de karışsın
                } else {
                    m.copy(winnerId = kazanan, isCompleted = true)
                }
            }
            tumMaclar.addAll(kayitlar)
            state = EmreSystemCorrect.processRoundResults(
                state, kayitlar, pairing.byeTeam, tumMaclar.toList()
            )

            // KASA: galibiyet 1 · beraberlik 0.5+0.5 · bye 1
            val toplam = state.teams.sumOf { it.points }
            assertEquals(
                "Tur $tur sonunda kasa tutmuyor (maç=${tumMaclar.size}, bye=$byeSayisi)",
                (tumMaclar.size + byeSayisi).toDouble(),
                toplam,
                1e-9
            )
        }
        assertTrue("Tek sayılı turnuva hiç tur oynamadı", tur > 0)

        // Takım takım bağımsız yeniden hesap (bye'lar dahil edilemez —
        // bye puanı maç kaydı üretmez; fark tam bye toplamı olmalı)
        val maclardanPuan = HashMap<Long, Double>()
        tumMaclar.forEach { m ->
            when (m.winnerId) {
                null -> {
                    maclardanPuan.merge(m.songId1, 0.5, Double::plus)
                    maclardanPuan.merge(m.songId2, 0.5, Double::plus)
                }
                else -> maclardanPuan.merge(m.winnerId!!, 1.0, Double::plus)
            }
        }
        val byeFarki = state.teams.sumOf { t -> t.points - (maclardanPuan[t.id] ?: 0.0) }
        assertEquals("Bye puanları toplamı bye sayısına eşit olmalı", byeSayisi.toDouble(), byeFarki, 1e-9)
        state.teams.forEach { t ->
            val fark = t.points - (maclardanPuan[t.id] ?: 0.0)
            assertTrue(
                "Takım ${t.id}: maç dışı puanı ($fark) bye adedinin dışında",
                fark >= -1e-9 && kotlin.math.abs(fark - Math.round(fark)) < 1e-9
            )
        }
    }

    // ==========================================================
    // ⑤ Geri alıp FARKLI sonuç yazmak SONRAKİ turun eşleştirmesini
    //    meşru şekilde değiştirir — ama kasa yine tutar ve kırmızı
    //    çizgi yine korunur (farklı-sonuçlu kaotik koşum denetimi)
    // ==========================================================
    @Test
    fun farkliSonuclaGeriAlma_turnuvayiBozmaz_kasaVeKirmiziCizgiKorunur() {
        var state = EmreSystemCorrect.initializeEmreTournament(songs(16))
        val tumMaclar = mutableListOf<Match>()
        var id = 1L
        var tur = 0
        while (tur < 100) {
            val pairing = EmreSystemCorrect.createHybridPairingSystem(state)
            if (!pairing.canContinue || pairing.matches.isEmpty()) break
            tur++
            val kayitlar = pairing.matches.map { it.copy(id = id++) }.mapIndexed { i, m ->
                // İlk maçta "önce büyük kazanacaktı, geri alındı, KÜÇÜK kazandı":
                // nihai sonuç temiz koşumdan FARKLI — turnuva başka yöne akar.
                if (i == 0) {
                    var kayit = m.copy(winnerId = maxOf(m.songId1, m.songId2), isCompleted = true)
                    kayit = kayit.copy(winnerId = null, isCompleted = false)
                    kayit.copy(winnerId = minOf(kayit.songId1, kayit.songId2), isCompleted = true)
                } else {
                    m.copy(winnerId = maxOf(m.songId1, m.songId2), isCompleted = true)
                }
            }
            tumMaclar.addAll(kayitlar)
            state = EmreSystemCorrect.processRoundResults(state, kayitlar, pairing.byeTeam, tumMaclar.toList())

            assertEquals(
                "Tur $tur: kasa tutmuyor",
                tumMaclar.size.toDouble(),
                state.teams.sumOf { it.points },
                1e-9
            )
        }
        assertTrue(tur > 0)

        // Kırmızı çizgi değişen sonuçlarla da korunmalı
        val gorulen = mutableSetOf<Pair<Long, Long>>()
        tumMaclar.forEach { m ->
            val cift = if (m.songId1 < m.songId2) m.songId1 to m.songId2 else m.songId2 to m.songId1
            assertTrue("Tekrar eşleşme: $cift", gorulen.add(cift))
        }

        // Final: takım kaybı yok, pozisyonlar boşluksuz
        val sonuc = EmreSystemCorrect.calculateFinalResults(state)
        assertEquals(16, sonuc.size)
        assertEquals((1..16).toList(), sonuc.sortedBy { it.position }.map { it.position })
    }

    // ==========================================================
    // ⑥ TURNUVA BİTİMİ: motor "bitti" dedikten sonra tekrar çağrılırsa
    //    yeni tur üretmemeli ve puanlara dokunmamalı
    // ==========================================================
    @Test
    fun turnuvaBittiktenSonra_motorYeniTurUretmez_puanlarDegismez() {
        var state = EmreSystemCorrect.initializeEmreTournament(songs(8))
        val tumMaclar = mutableListOf<Match>()
        var id = 1L
        var tur = 0
        while (tur < 100) {
            val pairing = EmreSystemCorrect.createHybridPairingSystem(state)
            if (!pairing.canContinue || pairing.matches.isEmpty()) break
            tur++
            val kayitlar = pairing.matches.map {
                it.copy(id = id++, winnerId = minOf(it.songId1, it.songId2), isCompleted = true)
            }
            tumMaclar.addAll(kayitlar)
            state = EmreSystemCorrect.processRoundResults(state, kayitlar, pairing.byeTeam, tumMaclar.toList())
        }
        assertTrue("Turnuva hiç oynamadı", tur > 0)

        val puanlarOnce = state.teams.associate { it.id to it.points }

        // Bittikten sonra üst üste üç çağrı: hep "devam edilemez", maç yok
        repeat(3) { deneme ->
            val tekrar = EmreSystemCorrect.createHybridPairingSystem(state)
            assertFalse("Bitmiş turnuva ${deneme + 1}. denemede devam etti", tekrar.canContinue)
            assertTrue("Bitmiş turnuva maç üretti", tekrar.matches.isEmpty())
        }

        // Eşleştirme denemeleri puanlara dokunmamış olmalı
        state.teams.forEach { t ->
            assertEquals("Bitiş sonrası puan değişti: takım ${t.id}", puanlarOnce.getValue(t.id), t.points, 1e-9)
        }
    }
}
