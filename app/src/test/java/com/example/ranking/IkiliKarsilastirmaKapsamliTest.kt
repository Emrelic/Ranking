package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.PairwiseComparisonSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * İKİLİ KARŞILAŞTIRMA — kapsamlı aşama sınavı (kullanıcı isteği:
 * "her türlü aşamasına bak, doğru çalıştığından emin olalım").
 *
 * PairwiseDeepTest'in (30 test) ÜSTÜNE, orada kapsanmayan aşamalar:
 *  A) DOĞRULUK GARANTİSİ — tutarlı kullanıcıda sonuç TAM sıralı mı
 *     (çeşitli n, tohum ve düşmanca girişler: sıralı, ters, testere)
 *  B) GERİ ALMA — son cevap silinince sistem tam o soruya dönüyor mu
 *  C) BOZUK KAYIT DAYANIKLILIĞI — yetim/yabancı/mükerrer kayıtlar
 *  D) MAÇ ALANLARI — üretilen Match kaydının round/matchNumber sözleşmesi
 *  E) İLERLEME MONOTONLUĞU — her cevap sayaçları ileri taşıyor mu
 *  F) n=200 MÜHRÜ — 1264 soru + sıfır hata (regresyon sabitlemesi)
 */
class IkiliKarsilastirmaKapsamliTest {

    private fun ogeler(n: Int, tohum: Long? = null): List<Song> {
        val sira = if (tohum == null) (1..n).toList()
                   else (1..n).shuffled(java.util.Random(tohum))
        return sira.mapIndexed { i, s ->
            Song(id = s.toLong(), name = "$s", artist = "", album = "",
                trackNumber = i + 1, listId = 1L)
        }
    }

    /** Büyük id kazanır — nesnel gerçek: n, n-1, ..., 1 */
    private fun sonaKadarOyna(songs: List<Song>): MutableList<Match> {
        val cevaplar = mutableListOf<Match>()
        var id = 1L
        while (true) {
            val m = PairwiseComparisonSort.createNextComparisonMatch(songs, cevaplar) ?: break
            cevaplar.add(m.copy(id = id++,
                winnerId = maxOf(m.songId1, m.songId2), isCompleted = true))
            assertTrue("Soru sayısı patladı (sonsuz döngü?)", cevaplar.size < songs.size * 20)
        }
        return cevaplar
    }

    private fun siralama(songs: List<Song>, cevaplar: List<Match>): List<Int> =
        PairwiseComparisonSort.calculateResults(songs, cevaplar)
            .sortedBy { it.position }.map { it.songId.toInt() }

    // ---------- A) DOĞRULUK GARANTİSİ ----------

    private fun dogrulukSinavi(songs: List<Song>, etiket: String) {
        val n = songs.size
        val cevaplar = sonaKadarOyna(songs)
        val sira = siralama(songs, cevaplar)
        assertEquals("$etiket: sonuç tam sıralı değil", (n downTo 1).toList(), sira)
        assertTrue(
            "$etiket: soru sayısı (${cevaplar.size}) tahmini üst sınırı " +
                "(${PairwiseComparisonSort.estimatedTotalComparisons(n)}) aşdı",
            cevaplar.size <= PairwiseComparisonSort.estimatedTotalComparisons(n)
        )
    }

    @Test fun dogruluk_kucukBoyutlarTumu() {
        // n=2..12 tamamı — sınır bölgesi hatasız olmalı
        for (n in 2..12) dogrulukSinavi(ogeler(n, 5L + n), "n=$n")
    }

    @Test fun dogruluk_zatenSiraliGiris() = dogrulukSinavi(ogeler(60), "artan giriş")

    @Test fun dogruluk_tersSiraliGiris() =
        dogrulukSinavi(ogeler(60).reversed().mapIndexed { i, s ->
            s.copy(trackNumber = i + 1) }, "azalan giriş")

    @Test fun dogruluk_testereGirisi() {
        // 1, 60, 2, 59, ... — ikili aramayı uçlara savuran desen
        val n = 60
        val kaynak = ogeler(n)
        val testere = mutableListOf<Song>()
        var alt = 0; var ust = n - 1
        while (alt <= ust) {
            testere.add(kaynak[alt]); alt++
            if (alt <= ust) { testere.add(kaynak[ust]); ust-- }
        }
        dogrulukSinavi(testere.mapIndexed { i, s -> s.copy(trackNumber = i + 1) }, "testere")
    }

    @Test fun dogruluk_besAyriTohum_n50() {
        for (t in listOf(1L, 13L, 77L, 500L, 9999L))
            dogrulukSinavi(ogeler(50, t), "n=50 tohum=$t")
    }

    // ---------- B) GERİ ALMA ----------

    @Test fun geriAlma_sonCevapSilinince_ayniSoruGeriGelir() {
        val songs = ogeler(30, 42L)
        val cevaplar = mutableListOf<Match>()
        var id = 1L
        repeat(40) {
            val m = PairwiseComparisonSort.createNextComparisonMatch(songs, cevaplar) ?: return@repeat
            val sorulan = Pair(m.songId1, m.songId2)

            // Cevapla → geri al → aynı soru sorulmalı (ViewModel undo yolu:
            // tamamlanmamışlar silinir, son cevap winnerId=null yapılır)
            cevaplar.add(m.copy(id = id++, winnerId = maxOf(m.songId1, m.songId2), isCompleted = true))
            cevaplar.removeAt(cevaplar.size - 1)

            val tekrar = PairwiseComparisonSort.createNextComparisonMatch(songs, cevaplar)!!
            assertEquals("Geri almadan sonra farklı soru geldi (adım $it)",
                sorulan, Pair(tekrar.songId1, tekrar.songId2))

            // Devam etmek için gerçekten cevapla
            cevaplar.add(tekrar.copy(id = id++,
                winnerId = maxOf(tekrar.songId1, tekrar.songId2), isCompleted = true))
        }
    }

    @Test fun geriAlma_farkliCevapla_farkliDalaGirer_yineTamSiralar() {
        // Bir soruyu önce yanlış cevapla, geri al, doğru cevapla:
        // sistem çökmeden tam sıralamaya ulaşmalı
        val songs = ogeler(25, 7L)
        val cevaplar = mutableListOf<Match>()
        var id = 1L
        var geriAlinan = 0
        while (true) {
            val m = PairwiseComparisonSort.createNextComparisonMatch(songs, cevaplar) ?: break
            if (geriAlinan < 5 && cevaplar.size % 7 == 3) {
                // yanlış cevap + geri alma
                cevaplar.add(m.copy(id = id++, winnerId = minOf(m.songId1, m.songId2), isCompleted = true))
                cevaplar.removeAt(cevaplar.size - 1)
                geriAlinan++
            }
            cevaplar.add(m.copy(id = id++, winnerId = maxOf(m.songId1, m.songId2), isCompleted = true))
        }
        assertEquals("5 geri alma yapılmalıydı", 5, geriAlinan)
        assertEquals((25 downTo 1).toList(), siralama(songs, cevaplar))
    }

    // ---------- C) BOZUK KAYIT DAYANIKLILIĞI ----------

    @Test fun bozukKayit_yabanciOgeliCevap_cokertmiyor() {
        val songs = ogeler(10, 3L)
        val cevaplar = sonaKadarOyna(songs)
        // Listede olmayan öğelere ait hayalet kayıt
        cevaplar.add(Match(id = 999L, listId = 1L, rankingMethod = "MERGE_SORT",
            songId1 = 888L, songId2 = 777L, winnerId = 888L, round = 1,
            matchNumber = 999, isCompleted = true))
        assertEquals("Hayalet kayıt sıralamayı bozdu",
            (10 downTo 1).toList(), siralama(songs, cevaplar))
        assertEquals("Hayalet kayıt yeni soru doğurdu",
            null, PairwiseComparisonSort.createNextComparisonMatch(songs, cevaplar))
    }

    @Test fun bozukKayit_mukerrerCevap_deterministikKalir() {
        val songs = ogeler(15, 11L)
        val cevaplar = sonaKadarOyna(songs)
        // İlk cevabın kopyası (aynı çift, aynı kazanan) sona eklenmiş
        val kopya = cevaplar.first().copy(id = 5000L)
        val kirli = (cevaplar + kopya).toMutableList()
        assertEquals("Mükerrer kayıt sonucu değiştirdi",
            siralama(songs, cevaplar), siralama(songs, kirli))
    }

    @Test fun bozukKayit_celiskiliKopya_cokertmiyor() {
        // Aynı çift için TERS kazananlı ikinci kayıt — veri anomalisi.
        // Sistem çökmemeli ve deterministik bir sonuç üretmeli.
        val songs = ogeler(12, 19L)
        val cevaplar = sonaKadarOyna(songs)
        val ilk = cevaplar.first()
        val ters = ilk.copy(id = 6000L,
            winnerId = if (ilk.winnerId == ilk.songId1) ilk.songId2 else ilk.songId1)
        val kirli = cevaplar + ters
        val s1 = siralama(songs, kirli)
        val s2 = siralama(songs, kirli)
        assertEquals("Çelişkili kopyayla determinizm bozuldu", s1, s2)
        assertEquals("Öğe kaybı oldu", 12, s1.toSet().size)
    }

    @Test fun bozukKayit_winnerIdUcuncuBirDeger_adayKaybetmisSayilir() {
        // winnerId ne songId1 ne songId2 — belgelenmiş davranış: aday kaybetti
        val songs = ogeler(5)
        val cevaplar = mutableListOf<Match>()
        var id = 1L
        while (true) {
            val m = PairwiseComparisonSort.createNextComparisonMatch(songs, cevaplar) ?: break
            cevaplar.add(m.copy(id = id++, winnerId = -1L, isCompleted = true))
        }
        val sira = siralama(songs, cevaplar)
        assertEquals("Öğe kaybı", 5, sira.toSet().size)
        assertEquals("Hepsi 'aday kaybetti' ise giriş sırası korunmalı",
            listOf(1, 2, 3, 4, 5), sira)
    }

    // ---------- D) MAÇ ALANLARI SÖZLEŞMESİ ----------

    @Test fun macAlanlari_roundVeMatchNumberSozlesmesi() {
        val songs = ogeler(20, 8L)
        val cevaplar = mutableListOf<Match>()
        var id = 1L
        var oncekiSoruNo = 0
        while (true) {
            val m = PairwiseComparisonSort.createNextComparisonMatch(songs, cevaplar) ?: break
            assertEquals("listId yanlış", 1L, m.listId)
            assertEquals("rankingMethod yanlış", "MERGE_SORT", m.rankingMethod)
            assertEquals("matchNumber = cevaplanmış + 1 olmalı",
                cevaplar.size + 1, m.matchNumber)
            assertTrue("matchNumber geri gitti", m.matchNumber > oncekiSoruNo)
            assertTrue("round (yerleşen öğe sayısı) 1..n-1 aralığında olmalı",
                m.round in 1 until songs.size)
            assertTrue("Soru kendi kendisiyle eşleşme üretti", m.songId1 != m.songId2)
            oncekiSoruNo = m.matchNumber
            cevaplar.add(m.copy(id = id++, winnerId = maxOf(m.songId1, m.songId2), isCompleted = true))
        }
    }

    @Test fun macAlanlari_kaydedilmedenIkiKezIstenirse_ayniSoru() {
        val songs = ogeler(18, 21L)
        val cevaplar = sonaKadarOyna(songs).take(9)
        val a = PairwiseComparisonSort.createNextComparisonMatch(songs, cevaplar)!!
        val b = PairwiseComparisonSort.createNextComparisonMatch(songs, cevaplar)!!
        assertEquals("Kaydedilmemiş soru iki istekte değişti",
            Pair(a.songId1, a.songId2), Pair(b.songId1, b.songId2))
        assertEquals(a.matchNumber, b.matchNumber)
    }

    // ---------- E) İLERLEME MONOTONLUĞU ----------

    @Test fun ilerleme_herCevaptaSayacIleriGider() {
        val songs = ogeler(40, 33L)
        val cevaplar = mutableListOf<Match>()
        var id = 1L
        var oncekiYerlesen = 1   // ilk öğe sorusuz yerleşir
        var oncekiCevap = 0
        while (true) {
            val st = PairwiseComparisonSort.computeState(songs, cevaplar)
            assertTrue("insertedCount geriledi", st.insertedCount >= oncekiYerlesen)
            assertTrue("comparisonsDone geriledi", st.comparisonsDone >= oncekiCevap)
            assertEquals("comparisonsDone cevap sayısına eşit olmalı",
                cevaplar.size, st.comparisonsDone)
            assertTrue("sortedIds içinde mükerrer var",
                st.sortedIds.size == st.sortedIds.toSet().size)
            oncekiYerlesen = st.insertedCount; oncekiCevap = st.comparisonsDone

            if (st.isComplete) break
            val m = PairwiseComparisonSort.createNextComparisonMatch(songs, cevaplar)!!
            cevaplar.add(m.copy(id = id++, winnerId = maxOf(m.songId1, m.songId2), isCompleted = true))
        }
        assertEquals("Bitişte hepsi yerleşmiş olmalı", 40, oncekiYerlesen)
    }

    @Test fun yarimDurum_sonuclarKismiVeButun() {
        val songs = ogeler(30, 55L)
        val tam = sonaKadarOyna(songs)
        val yarim = tam.take(tam.size / 3)
        val sonuc = PairwiseComparisonSort.calculateResults(songs, yarim)
        assertEquals("Yarım durumda da 30 sonuç üretilmeli", 30, sonuc.size)
        assertEquals("Pozisyonlar 1..30 benzersiz olmalı",
            (1..30).toList(), sonuc.map { it.position }.sorted())
        assertEquals("Skorlar 30..1 olmalı",
            (1..30).map { it.toDouble() }.sorted(),
            sonuc.map { it.score }.sorted())
    }

    // ---------- F) n=200 MÜHRÜ ----------

    @Test fun muhur_n200_tohum200_1264soruSifirHata() {
        // Kullanıcıya raporlanan ölçümün kalıcı sabitlemesi: bu sayı değişirse
        // algoritmanın davranışı değişmiş demektir — bilerek mi, sorulmalı.
        val songs = ogeler(200, 200L)
        val cevaplar = sonaKadarOyna(songs)
        assertEquals("Soru sayısı raporlanan 1264'ten saptı", 1264, cevaplar.size)
        assertEquals("Sonuç kusursuz değil", (200 downTo 1).toList(), siralama(songs, cevaplar))
        assertTrue("Üst sınır tutmadı",
            cevaplar.size <= PairwiseComparisonSort.estimatedTotalComparisons(200))
    }
}
