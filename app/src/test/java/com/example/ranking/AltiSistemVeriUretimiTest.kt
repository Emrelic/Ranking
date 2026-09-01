package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.EmreSiralamaSistemi
import com.example.ranking.ranking.EmreSystemCorrect
import com.example.ranking.ranking.PairwiseComparisonSort
import com.example.ranking.ranking.RankingEngine
import com.example.ranking.ranking.SwissSystem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * VERİ ÜRETİMİ — kullanıcı isteği (2026-08-31): "Yeni 200'lük random liste,
 * altı sistemle sırala, tur/oylama/sapma analizi + renkli kutu haritası."
 *
 * Tohum 777 ile YENİ bir diziliş üretilir; altı sistem aynı dizilişi
 * sıralar, sonuçlar görsel sayfa için JSON'a dökülür. (Görselleştirme
 * scratchpad'te Python ile kurulur; bu test veri kaynağıdır.)
 */
class AltiSistemVeriUretimiTest {

    private val n = 200
    private val tohum = 777L

    private fun sarkilar(): List<Song> =
        (1..n).shuffled(java.util.Random(tohum)).mapIndexed { i, s ->
            Song(id = s.toLong(), name = "$s", artist = "", album = "",
                trackNumber = i + 1, listId = 1L)
        }

    private var mid = 1L
    private fun oynat(maclar: List<Match>): List<Match> = maclar.map {
        it.copy(id = mid++, winnerId = maxOf(it.songId1, it.songId2), isCompleted = true)
    }

    data class Sonuc(val sira: List<Int>, val tur: Int, val mac: Int)

    private fun ikiliKostur(songs: List<Song>): Sonuc {
        val cevaplar = mutableListOf<Match>()
        var id = 1L
        while (true) {
            val m = PairwiseComparisonSort.createNextComparisonMatch(songs, cevaplar) ?: break
            cevaplar.add(m.copy(id = id++,
                winnerId = maxOf(m.songId1, m.songId2), isCompleted = true))
        }
        val sira = PairwiseComparisonSort.calculateResults(songs, cevaplar)
            .sortedBy { it.position }.map { it.songId.toInt() }
        return Sonuc(sira, cevaplar.maxOf { it.round }, cevaplar.size)
    }

    private fun emreKostur(songs: List<Song>, hedefTur: Int = Int.MAX_VALUE): Sonuc {
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        val hepsi = mutableListOf<Match>()
        var tur = 0
        while (tur < hedefTur) {
            val p = EmreSystemCorrect.createHybridPairingSystem(state)
            if (!p.canContinue || p.matches.isEmpty()) break
            tur++
            val o = oynat(p.matches.map { it.copy(round = tur) })
            hepsi.addAll(o)
            val bye = state.teams.find { t ->
                o.none { it.songId1 == t.song.id || it.songId2 == t.song.id }
            }
            state = RankingEngine.processCorrectEmreResults(state, o, bye, allCompletedMatches = hepsi)
        }
        val sira = RankingEngine.calculateCorrectEmreResults(state)
            .sortedBy { it.position }.map { it.songId.toInt() }
        return Sonuc(sira, tur, hepsi.size)
    }

    private fun swissKostur(songs: List<Song>): Sonuc {
        val hepsi = mutableListOf<Match>()
        var tur = 0
        while (tur < SwissSystem.recommendedRoundCount(n)) {
            val state = SwissSystem.computeState(songs, hepsi)
            val p = SwissSystem.createNextRound(state, hepsi)
            if (!p.canContinue || p.matches.isEmpty()) break
            tur++
            hepsi.addAll(oynat(p.matches.map { it.copy(round = tur) }))
        }
        val sira = SwissSystem.calculateResults(songs, hepsi)
            .sortedBy { it.position }.map { it.songId.toInt() }
        return Sonuc(sira, tur, hepsi.size)
    }

    /** 4 tur Emre + kanıt turları (adımlar 20,6,2,1) — HibritKanitSistemiTest'teki kazanan ayar */
    private fun hibritKostur(songs: List<Song>): Sonuc {
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        val hepsi = mutableListOf<Match>()
        var tur = 0
        while (tur < 4) {
            val p = EmreSystemCorrect.createHybridPairingSystem(state)
            if (!p.canContinue || p.matches.isEmpty()) break
            tur++
            val o = oynat(p.matches.map { it.copy(round = tur) })
            hepsi.addAll(o)
            val bye = state.teams.find { t ->
                o.none { it.songId1 == t.song.id || it.songId2 == t.song.id }
            }
            state = RankingEngine.processCorrectEmreResults(state, o, bye, allCompletedMatches = hepsi)
        }
        val sira = RankingEngine.calculateCorrectEmreResults(state)
            .sortedBy { it.position }.map { it.songId }.toMutableList()
        val kanit = HashMap<Pair<Long, Long>, Long>()
        hepsi.forEach { m ->
            val w = m.winnerId ?: return@forEach
            kanit[minOf(m.songId1, m.songId2) to maxOf(m.songId1, m.songId2)] = w
        }
        var faz2Mac = 0
        var faz2Tur = 0
        for (adim in listOf(20, 6, 2, 1)) {
            var degisti = true
            while (degisti) {
                degisti = false
                for (parite in 0 until 2 * adim) {
                    var turdaMac = 0
                    var i = parite
                    while (i + adim < n) {
                        val ust = sira[i]; val alt = sira[i + adim]
                        val anahtar = minOf(ust, alt) to maxOf(ust, alt)
                        val kazanan = kanit[anahtar] ?: run {
                            val w = maxOf(ust, alt)
                            kanit[anahtar] = w; faz2Mac++; turdaMac++; w
                        }
                        if (kazanan == alt) {
                            sira[i] = alt; sira[i + adim] = ust; degisti = true
                        }
                        i += 2 * adim
                    }
                    if (turdaMac > 0) faz2Tur++
                }
            }
        }
        return Sonuc(sira.map { it.toInt() }, tur + faz2Tur, hepsi.size + faz2Mac)
    }

    /** Emre Sıralama Sistemi: uygulamadaki gerçek motor, uygulama akışıyla */
    private fun emreSiralamaKostur(songs: List<Song>): Sonuc {
        val db = mutableListOf<Match>()
        var tur = 0
        while (tur < 2000) {
            val yeni = EmreSiralamaSistemi.createNextRoundMatches(songs, db)
            if (yeni.isEmpty()) break
            tur++
            yeni.sortedBy { it.matchNumber }.forEach { m ->
                db.add(m.copy(id = mid++,
                    winnerId = maxOf(m.songId1, m.songId2), isCompleted = true))
            }
        }
        val sira = EmreSiralamaSistemi.calculateResults(songs, db)
            .sortedBy { it.position }.map { it.songId.toInt() }
        return Sonuc(sira, tur, db.size)
    }

    /** Tam lig: herkes herkesle bir kez, 3/1/0 puan; circle-method tur sayısı n-1 */
    private fun ligKostur(songs: List<Song>): Sonuc {
        val puan = HashMap<Long, Int>()
        var mac = 0
        for (i in songs.indices) for (j in i + 1 until songs.size) {
            val a = songs[i].id; val b = songs[j].id
            puan[maxOf(a, b)] = (puan[maxOf(a, b)] ?: 0) + 3
            puan[minOf(a, b)] = (puan[minOf(a, b)] ?: 0) + 0
            mac++
        }
        // Puana göre; eşitlikte giriş sırası (bu senaryoda eşitlik çıkmaz)
        val sira = songs.sortedByDescending { puan[it.id] ?: 0 }.map { it.id.toInt() }
        return Sonuc(sira, n - 1, mac)
    }

    /**
     * Tek eleme: 256'lık tabela, ilk 56 giriş bye. Sıralama tur bazlı
     * kademelerden çıkar (şampiyon, finalist, yarı final kaybedenleri...);
     * kademe İÇİ sıra giriş sırasıdır — eleme sistemi bundan fazlasını bilemez.
     */
    private fun elemeKostur(songs: List<Song>): Sonuc {
        var kalanlar = songs.map { it.id }
        val kademe = HashMap<Long, Int>() // elendiği tur (şampiyon = 99)
        var mac = 0
        var tur = 0
        // İlk tur: 56 bye (giriş sırasının başı), kalan 144'ten 72 maç
        val byeler = kalanlar.take(56)
        val oynayanlar = kalanlar.drop(56)
        tur++
        val ilkTurKazananlar = mutableListOf<Long>()
        for (i in oynayanlar.indices step 2) {
            val a = oynayanlar[i]; val b = oynayanlar[i + 1]
            val w = maxOf(a, b); val l = minOf(a, b)
            kademe[l] = tur; ilkTurKazananlar.add(w); mac++
        }
        kalanlar = byeler + ilkTurKazananlar // 128 kişi
        while (kalanlar.size > 1) {
            tur++
            val kazananlar = mutableListOf<Long>()
            for (i in kalanlar.indices step 2) {
                val a = kalanlar[i]; val b = kalanlar[i + 1]
                val w = maxOf(a, b); val l = minOf(a, b)
                kademe[l] = tur; kazananlar.add(w); mac++
            }
            kalanlar = kazananlar
        }
        kademe[kalanlar[0]] = 99
        val girisSirasi = songs.mapIndexed { i, s -> s.id to i }.toMap()
        val sira = songs.map { it.id }
            .sortedWith(compareByDescending<Long> { kademe[it]!! }.thenBy { girisSirasi[it]!! })
            .map { it.toInt() }
        return Sonuc(sira, tur, mac)
    }

    @Test
    fun altiSistem_veriUret() {
        val songs = sarkilar()
        val sonuclar = linkedMapOf(
            "IKILI" to ikiliKostur(songs),
            "EMRE_SIRALAMA" to emreSiralamaKostur(songs),
            "SWISS" to swissKostur(songs),
            "EMRE" to emreKostur(songs),
            "HIBRIT" to hibritKostur(songs),
            "LIG" to ligKostur(songs),
            "ELEME" to elemeKostur(songs)
        )
        sonuclar.forEach { (ad, s) ->
            assertEquals("$ad: eksik/mükerrer öğe", n, s.sira.toSet().size)
            val sapmalar = s.sira.mapIndexed { i, v -> kotlin.math.abs(i - (n - v)) }
            println("%-7s tur=%-4d oy=%-6d ortSapma=%.2f".format(
                ad, s.tur, s.mac, sapmalar.sum() / n.toDouble()))
        }
        val json = buildString {
            append("{\"tohum\":$tohum,\"n\":$n,")
            append("\"liste\":${sarkilar().map { it.id.toInt() }},")
            append("\"sistemler\":{")
            append(sonuclar.entries.joinToString(",") { (ad, s) ->
                "\"$ad\":{\"sira\":${s.sira},\"tur\":${s.tur},\"mac\":${s.mac}}"
            })
            append("}}")
        }
        val hedef = File("C:/Users/emrem/AppData/Local/Temp/claude/C--Users-emrem-OneDrive-Belgeler-Projeler-Ranking/e61e0645-d14d-417c-8a6a-5322de2951ee/scratchpad/alti-sistem-200.json")
        hedef.parentFile.mkdirs()
        hedef.writeText(json)
        println("JSON yazıldı: ${hedef.absolutePath}")
    }
}
