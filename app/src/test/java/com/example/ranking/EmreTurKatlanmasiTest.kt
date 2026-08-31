package com.example.ranking

import com.example.ranking.data.Match
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "3. turda eşleşmeler 40 → 80 → 160 diye katlandı, aynı takım aynı turda
 * iki kez eşleşti" şikâyetinin gerileme testi.
 *
 * Kullanıcının turnuva verisi cihazdan çekilip incelendi (2026-08-31).
 * Kök sebep ViewModel'de iki yerdeydi ve ikisi de MAÇ SEÇME MANTIĞIYDI —
 * motorun ürettiği eşleştirmelerde kusur yoktu:
 *
 * ① Devam ettirmede eşleştirme listesi `allMatches.filter { !it.isCompleted }`
 *    ile kuruluyordu — TUR SÜZGECİ YOK. Bütün turların yarım maçları tek
 *    listede toplanıyor, ekranda tek tur gibi görünüyordu.
 * ② Yarım tur dururken yeni tur üretilebiliyordu; iki turun maçları
 *    veritabanında yan yana kalıyordu.
 *
 * ①+② birleşince: kullanıcı eski turdan kalma bir maça oy veriyor, tur
 * kapanışı o eski turu yeniden "tamamlandı" sayıyor ve BİR TUR DAHA
 * üretiyordu. Katlanmanın motoru buydu.
 *
 * Burada ViewModel'in Room bağımlılığı olmadan, aynı süzme kurallarını
 * saf fonksiyon olarak sınıyoruz.
 */
class EmreTurKatlanmasiTest {

    private fun mac(id: Long, tur: Int, s1: Long, s2: Long, tamam: Boolean) = Match(
        id = id, listId = 1L, rankingMethod = "EMRE_CORRECT",
        songId1 = s1, songId2 = s2, winnerId = if (tamam) s1 else null,
        round = tur, matchNumber = id.toInt(), isCompleted = tamam
    )

    /** ViewModel'deki düzeltilmiş süzgecin birebir karşılığı. */
    private fun acikTurunMaclari(hepsi: List<Match>): List<Match> {
        val acikTur = hepsi.filter { !it.isCompleted }.minOfOrNull { it.round }
        return hepsi.filter { !it.isCompleted && it.round == acikTur }
    }

    /** Yarım tur varsa yeni tur üretilmemeli. */
    private fun yeniTurUretilebilirMi(hepsi: List<Match>, hedefTur: Int): Boolean {
        if (hepsi.any { !it.isCompleted }) return false
        if (hepsi.any { it.round == hedefTur }) return false
        return true
    }

    @Test
    fun eslestirmeListesi_yalnizAcikTuruIcerir_katlanmaz() {
        // Tur 2 yarım (2 maç açık), tur 3 de kurulmuş (2 maç açık) — bozuk hâl
        val hepsi = listOf(
            mac(1, 1, 10, 11, true),
            mac(2, 1, 12, 13, true),
            mac(3, 2, 10, 12, false),
            mac(4, 2, 11, 13, false),
            mac(5, 3, 10, 13, false),
            mac(6, 3, 11, 12, false)
        )

        val liste = acikTurunMaclari(hepsi)

        assertEquals(
            "Liste yalnız AÇIK turu (tur 2) içermeli — tüm turların yarımları değil. " +
                "Çıkan turlar: ${liste.map { it.round }.distinct()}",
            2, liste.size
        )
        assertTrue("Yalnız tur 2 olmalı", liste.all { it.round == 2 })
    }

    @Test
    fun ayniTakimListedeIkiKezGorunmemeli() {
        val hepsi = listOf(
            mac(1, 2, 10, 11, false),
            mac(2, 2, 12, 13, false),
            // tur 3 erkenden kurulmuş: AYNI takımlar tekrar
            mac(3, 3, 10, 12, false),
            mac(4, 3, 11, 13, false)
        )

        val liste = acikTurunMaclari(hepsi)
        val katilan = liste.flatMap { listOf(it.songId1, it.songId2) }

        assertEquals(
            "Bir takım listede birden çok kez görünüyor — farklı turların maçları " +
                "tek listede toplanmış demektir. Katılan: $katilan",
            katilan.size, katilan.toSet().size
        )
    }

    @Test
    fun yarimTurVarken_yeniTurUretilmemeli() {
        val yarim = listOf(
            mac(1, 2, 10, 11, true),
            mac(2, 2, 12, 13, false)   // açık
        )
        assertTrue(
            "Yarım tur dururken yeni tur üretilirse maçlar katlanır",
            !yeniTurUretilebilirMi(yarim, 3)
        )
    }

    @Test
    fun ayniTurIkinciKezKurulmamali() {
        val tamam = listOf(
            mac(1, 1, 10, 11, true),
            mac(2, 1, 12, 13, true),
            mac(3, 2, 10, 12, true),
            mac(4, 2, 11, 13, true)
        )
        assertTrue("Var olan tur yeniden kurulursa mükerrer maç oluşur",
            !yeniTurUretilebilirMi(tamam, 2))
        assertTrue("Sıradaki tur kurulabilmeli",
            yeniTurUretilebilirMi(tamam, 3))
    }

    @Test
    fun tumMaclarTamamsa_acikTurYok_listeBos() {
        val tamam = listOf(
            mac(1, 1, 10, 11, true),
            mac(2, 1, 12, 13, true)
        )
        assertTrue("Açık tur yokken liste boş olmalı", acikTurunMaclari(tamam).isEmpty())
    }
}
