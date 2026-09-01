package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.EmreSiralamaSistemi
import com.example.ranking.ranking.EmreSystemCorrect
import com.example.ranking.ranking.HibritKanitSistemi
import com.example.ranking.ranking.RankingEngine
import com.example.ranking.ranking.SwissSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ÇAPRAZ DÜŞMAN SINAVI — kullanıcı isteği (2026-09-01): "bir yazılım uzmanı
 * hocası gibi en sıkıntılı hataları yakala".
 *
 * Aynı üç bozuk-veri işkencesi TÜM motorlara uygulanır:
 *  ① YABANCI KAZANAN — winnerId ne songId1 ne songId2 (veri bozulması)
 *  ② SİLİNMİŞ ÖĞE — maç kayıtları listede artık olmayan bir öğeye işaret
 *    ediyor (deleteSong maçları siler ama yarış durumu/yedek geri yükleme
 *    gibi yollarla yetim kayıt oluşabilir)
 *  ③ MÜKERRER KAYIT — aynı çiftin maçı iki kez, ikincisi ters sonuçla
 *
 * Beklenti hiçbir motorda ÇÖKME olmaması ve sonucun sözleşmeyi korumasıdır:
 * mevcut listedeki HER öğe TAM BİR kez, benzersiz pozisyonla sıralanır.
 * Ek olarak geri alma determinizmi motor düzeyinde sınanır.
 */
class CaprazDusmanSinaviTest {

    private fun ogeler(n: Int, tohum: Long = 7L): List<Song> =
        (1..n).shuffled(java.util.Random(tohum)).mapIndexed { i, s ->
            Song(id = s.toLong(), name = "$s", artist = "", album = "",
                trackNumber = i + 1, listId = 1L)
        }

    private var mid = 500L
    private fun mac(a: Long, b: Long, w: Long?, round: Int, no: Int = 1, metod: String = "X") =
        Match(id = mid++, listId = 1L, rankingMethod = metod, songId1 = a, songId2 = b,
            winnerId = w, round = round, matchNumber = no, isCompleted = true)

    /** Sözleşme: sonuç, verilen listenin öğelerini tam bir kez ve benzersiz pozisyonla kapsar. */
    private fun sozlesme(etiket: String, songs: List<Song>, sonuc: List<com.example.ranking.data.RankingResult>) {
        assertEquals("$etiket: sonuç sayısı", songs.size, sonuc.size)
        assertEquals("$etiket: öğe kümesi", songs.map { it.id }.toSet(), sonuc.map { it.songId }.toSet())
        assertEquals("$etiket: pozisyonlar", (1..songs.size).toList(), sonuc.map { it.position }.sorted())
    }

    // ---------- ① YABANCI KAZANAN ----------

    @Test
    fun yabanciKazanan_hicbirMotorCokmez() {
        val songs = ogeler(10)
        // 1. tur: 5 maç, birinin kazananı listede OLMAYAN 999
        val bozukTur = listOf(
            mac(songs[0].id, songs[1].id, 999L, 1, 1),
            mac(songs[2].id, songs[3].id, maxOf(songs[2].id, songs[3].id), 1, 2),
            mac(songs[4].id, songs[5].id, maxOf(songs[4].id, songs[5].id), 1, 3),
            mac(songs[6].id, songs[7].id, maxOf(songs[6].id, songs[7].id), 1, 4),
            mac(songs[8].id, songs[9].id, maxOf(songs[8].id, songs[9].id), 1, 5)
        )

        sozlesme("HIBRIT", songs, HibritKanitSistemi.calculateResults(songs, bozukTur))
        sozlesme("EMRE_SIRALAMA", songs, EmreSiralamaSistemi.calculateResults(songs, bozukTur))
        sozlesme("SWISS", songs, SwissSystem.calculateResults(songs, bozukTur))
        sozlesme("LEAGUE", songs, RankingEngine.calculateLeagueResults(songs, bozukTur))

        // EMRE_CORRECT: state akışı üzerinden
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        state = RankingEngine.processCorrectEmreResults(state, bozukTur, null, allCompletedMatches = bozukTur)
        val emreSonuc = RankingEngine.calculateCorrectEmreResults(state)
        sozlesme("EMRE_CORRECT", songs, emreSonuc)

        // Yabancı kazanan turnuvayı KİLİTLEMEMELİ: sıradaki tur üretimi çökmesin
        HibritKanitSistemi.createNextRoundMatches(songs, bozukTur)
        EmreSiralamaSistemi.createNextRoundMatches(songs, bozukTur)
        SwissSystem.createNextRound(SwissSystem.computeState(songs, bozukTur), bozukTur)
        EmreSystemCorrect.createHybridPairingSystem(state)
    }

    // ---------- ② SİLİNMİŞ ÖĞE ----------

    @Test
    fun silinmisOge_kalanlarTutarliSiralanir() {
        val tumu = ogeler(12)
        val silinen = tumu[3]
        val kalanlar = tumu.filter { it.id != silinen.id }
        // Maç arşivi silinen öğeyi İÇEREN kayıtlar taşıyor (yetim kayıt)
        val arsiv = mutableListOf<Match>()
        var no = 1
        for (i in 0 until 12 step 2) {
            val a = tumu[i].id; val b = tumu[i + 1].id
            arsiv.add(mac(a, b, maxOf(a, b), 1, no++))
        }

        sozlesme("HIBRIT", kalanlar, HibritKanitSistemi.calculateResults(kalanlar, arsiv))
        sozlesme("EMRE_SIRALAMA", kalanlar, EmreSiralamaSistemi.calculateResults(kalanlar, arsiv))
        sozlesme("SWISS", kalanlar, SwissSystem.calculateResults(kalanlar, arsiv))
        sozlesme("LEAGUE", kalanlar, RankingEngine.calculateLeagueResults(kalanlar, arsiv))

        // Tur üretimleri de çökmemeli ve silinen öğeye maç AÇMAMALI
        val h = HibritKanitSistemi.createNextRoundMatches(kalanlar, arsiv)
        val e = EmreSiralamaSistemi.createNextRoundMatches(kalanlar, arsiv)
        (h + e).forEach { m ->
            assertTrue("Silinmiş öğeye maç açıldı: ${m.songId1} vs ${m.songId2}",
                m.songId1 != silinen.id && m.songId2 != silinen.id)
        }
    }

    // ---------- ③ MÜKERRER / ÇELİŞKİLİ KAYIT ----------

    @Test
    fun mukerrerCeliskiliKayit_ilkBilgiGecerli_cokusYok() {
        val songs = ogeler(8)
        val a = songs[0].id; val b = songs[1].id
        val arsiv = mutableListOf<Match>()
        var no = 1
        for (i in 0 until 8 step 2) {
            val x = songs[i].id; val y = songs[i + 1].id
            arsiv.add(mac(x, y, maxOf(x, y), 1, no++))
        }
        // Aynı çiftin İKİNCİ, ÇELİŞKİLİ kaydı (küçük kazanmış görünsün)
        arsiv.add(mac(a, b, minOf(a, b), 1, no++))

        sozlesme("HIBRIT", songs, HibritKanitSistemi.calculateResults(songs, arsiv))
        sozlesme("EMRE_SIRALAMA", songs, EmreSiralamaSistemi.calculateResults(songs, arsiv))
        sozlesme("SWISS", songs, SwissSystem.calculateResults(songs, arsiv))
        sozlesme("LEAGUE", songs, RankingEngine.calculateLeagueResults(songs, arsiv))

        // Çelişkili kopya turnuvanın BİTİŞİNİ engellememeli
        val db = arsiv.toMutableList()
        var tur = 1
        while (tur < 500) {
            val yeni = EmreSiralamaSistemi.createNextRoundMatches(songs, db)
            if (yeni.isEmpty()) break
            tur++
            yeni.forEach { m ->
                db.add(m.copy(id = mid++, winnerId = maxOf(m.songId1, m.songId2), isCompleted = true))
            }
        }
        assertTrue("Çelişkili kayıtla turnuva bitmedi", tur < 500)
    }

    // ---------- GERİ ALMA DETERMİNİZMİ (motor düzeyi) ----------

    /**
     * Uygulamadaki geri alma = son tamamlanan maçı "oynanmamış" yapmak.
     * Motor sözleşmesi: geri alınan maç AYNI cevapla yeniden oylanırsa
     * turnuvanın kalanı, hiç geri alınmamış gibi BİREBİR aynı akmalı.
     */
    private fun geriAlmaDeterminizmi(
        etiket: String,
        turUret: (List<Song>, List<Match>) -> List<Match>
    ) {
        val songs = ogeler(16, 42L)
        fun oyna(geriAlKacinci: Int?): List<Int> {
            val db = mutableListOf<Match>()
            var id = 1L
            var sayac = 0
            var tur = 0
            while (tur < 300) {
                val yeni = turUret(songs, db)
                if (yeni.isEmpty()) break
                tur++
                yeni.sortedBy { it.matchNumber }.forEach { m ->
                    db.add(m.copy(id = id++,
                        winnerId = maxOf(m.songId1, m.songId2), isCompleted = true))
                    sayac++
                    if (sayac == geriAlKacinci) {
                        // GERİ AL: son maçı sil ve AYNI cevapla yeniden oyla
                        val son = db.removeAt(db.size - 1)
                        db.add(son.copy(id = id++))
                    }
                }
            }
            return db.map { it.songId1.toInt() * 1000 + it.songId2.toInt() }
        }
        val duz = oyna(null)
        val geriAlmali = oyna(5)
        assertEquals("$etiket: geri alma sonrası akış değişti", duz, geriAlmali)
    }

    @Test
    fun geriAlma_hibrit_akisiDegistirmez() =
        geriAlmaDeterminizmi("HIBRIT") { s, db -> HibritKanitSistemi.createNextRoundMatches(s, db) }

    @Test
    fun geriAlma_emreSiralama_akisiDegistirmez() =
        geriAlmaDeterminizmi("EMRE_SIRALAMA") { s, db -> EmreSiralamaSistemi.createNextRoundMatches(s, db) }

    // ---------- BOŞ VE UÇ GİRDİLER ----------

    @Test
    fun bosArsiv_hicbirMotorCokmez() {
        val songs = ogeler(5)
        sozlesme("HIBRIT", songs, HibritKanitSistemi.calculateResults(songs, emptyList()))
        sozlesme("EMRE_SIRALAMA", songs, EmreSiralamaSistemi.calculateResults(songs, emptyList()))
        sozlesme("SWISS", songs, SwissSystem.calculateResults(songs, emptyList()))
        sozlesme("LEAGUE", songs, RankingEngine.calculateLeagueResults(songs, emptyList()))
    }
}
