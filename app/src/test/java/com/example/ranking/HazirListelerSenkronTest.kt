package com.example.ranking

import com.example.ranking.data.HazirListe
import com.example.ranking.data.HazirListeler
import com.example.ranking.utils.CsvReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * HAZIR LİSTE SENKRON BEKÇİSİ
 *
 * Hazır liste kütüphanesinin DÖRT ayrı "gerçek" kaynağı var:
 *
 *   ① liste_kutuphanesi (CSV kaynakları)                               (kaynak)
 *   ② app/src/main/assets/hazir_listeler            (uygulamaya gömülen kopya)
 *   ③ data/HazirListeler.kt                        (katalog: ad + öğe sayısı)
 *   ④ liste_kutuphanesi/README.md                  (doküman tablosu)
 *
 * Bunlar bir kez ayrıştı ve ortada üç farklı öğe sayısı ("909 / 1019 / 1811")
 * dolaştı. Bu test o ayrışmanın bir daha SESSİZCE olmasını engeller: dördü
 * birbirinden bağımsız okunur ve karşılaştırılır.
 *
 * Saf JVM testi — Android bağımlılığı yok, dosyalar diskten okunur.
 */
class HazirListelerSenkronTest {

    private val reader = CsvReader()

    // ---------------------------------------------------------------- yollar

    /**
     * Depo kökünü çalışma dizininden YUKARI yürüyerek bulur.
     *
     * Gradle birim testini modül dizininde (app/) koşturur, IDE bazen depo
     * kökünde koşturur. İkisini de varsaymak yerine ölçüyoruz: hem
     * liste_kutuphanesi hem app/src/main/assets/hazir_listeler içeren ilk
     * üst dizin depo köküdür.
     */
    private val depoKoku: File by lazy {
        var dizin: File? = File("").absoluteFile
        while (dizin != null) {
            val kutuphane = File(dizin, KUTUPHANE_YOLU)
            val assets = File(dizin, ASSET_YOLU)
            if (kutuphane.isDirectory && assets.isDirectory) return@lazy dizin
            dizin = dizin.parentFile
        }
        fail(
            "Depo kökü bulunamadı. Çalışma dizini: " + File("").absolutePath +
                " — '" + KUTUPHANE_YOLU + "' ve '" + ASSET_YOLU + "' içeren bir üst dizin yok."
        )
        error("ulaşılmaz")
    }

    private val assetDizini: File get() = File(depoKoku, ASSET_YOLU)
    private val kutuphaneDizini: File get() = File(depoKoku, KUTUPHANE_YOLU)

    private fun assetDosyalari(): List<File> =
        assetDizini.listFiles { f -> f.isFile && f.name.endsWith(".csv") }
            ?.sortedBy { it.name }
            ?: emptyList()

    private fun kutuphaneDosyalari(): List<File> =
        kutuphaneDizini.listFiles { f -> f.isFile && f.name.endsWith(".csv") }
            ?.sortedBy { it.name }
            ?: emptyList()

    private fun katalogKayitlari(): List<HazirListe> =
        HazirListeler.kategoriler.flatMap { it.liste }

    private fun ogeSayisi(dosya: File): Int =
        reader.parseText(dosya.readText(Charsets.UTF_8)).size

    // ------------------------------------------------------------- testler

    @Test
    fun dizinlerBulunurVeBosDegil() {
        assertTrue("assets/hazir_listeler dizini yok: " + assetDizini, assetDizini.isDirectory)
        assertTrue("liste_kutuphanesi dizini yok: " + kutuphaneDizini, kutuphaneDizini.isDirectory)
        assertTrue("assets altında hiç CSV yok", assetDosyalari().isNotEmpty())
    }

    /** ② Her gömülü CSV parser'dan geçer, öğe üretir, boş ad bırakmaz. */
    @Test
    fun herAssetCsvParseEdilirVeBosAdIcermez() {
        val hatalar = mutableListOf<String>()
        for (dosya in assetDosyalari()) {
            val ogeler = try {
                reader.parseText(dosya.readText(Charsets.UTF_8))
            } catch (e: Exception) {
                hatalar += dosya.name + ": parse hatası — " + e.javaClass.simpleName + ": " + e.message
                continue
            }
            if (ogeler.isEmpty()) {
                hatalar += dosya.name + ": 0 öğe üretti (dosya boş ya da tamamen başlık)"
                continue
            }
            val bosAd = ogeler.count { it.name.isBlank() }
            if (bosAd > 0) hatalar += dosya.name + ": " + bosAd + " öğenin adı boş"
        }
        assertTrue("Bozuk hazır liste dosyaları:\n" + hatalar.joinToString("\n"), hatalar.isEmpty())
    }

    /** ①② Kaynak ile gömülü kopya BİREBİR aynı olmalı (iki yönlü). */
    @Test
    fun assetKopyasiKutuphaneKaynagiylaBirebirAyni() {
        val hatalar = mutableListOf<String>()
        for (asset in assetDosyalari()) {
            val kaynak = File(kutuphaneDizini, asset.name)
            if (!kaynak.isFile) {
                hatalar += asset.name + ": assets'te var, liste_kutuphanesi'nde YOK"
                continue
            }
            if (!asset.readBytes().contentEquals(kaynak.readBytes())) {
                hatalar += asset.name + ": içerik FARKLI (assets " + asset.length() +
                    " bayt / kaynak " + kaynak.length() + " bayt) — kaynağı assets'e kopyala"
            }
        }
        val assetAdlari = assetDosyalari().map { it.name }.toSet()
        for (kaynak in kutuphaneDosyalari()) {
            if (kaynak.name !in assetAdlari) {
                hatalar += kaynak.name + ": liste_kutuphanesi'nde var, assets'e KOPYALANMAMIŞ"
            }
        }
        assertTrue("Kaynak ↔ assets ayrışması:\n" + hatalar.joinToString("\n"), hatalar.isEmpty())
    }

    /** ②③ Katalogdaki her kayıt gerçek bir dosyaya karşılık gelir. */
    @Test
    fun katalogdakiHerDosyaGercektenVar() {
        val eksik = katalogKayitlari()
            .filter { !File(assetDizini, it.dosya).isFile }
            .map { it.ad + " → " + it.dosya }
        assertTrue(
            "Katalogda kayıtlı ama assets'te olmayan dosyalar:\n" + eksik.joinToString("\n"),
            eksik.isEmpty()
        )
    }

    /** ②③ Katalogdaki öğe sayısı CSV'nin GERÇEK öğe sayısına eşit. */
    @Test
    fun katalogOgeSayilariCsvIleAyni() {
        val hatalar = mutableListOf<String>()
        for (kayit in katalogKayitlari()) {
            val dosya = File(assetDizini, kayit.dosya)
            if (!dosya.isFile) continue // ayrı testte raporlanıyor
            val gercek = ogeSayisi(dosya)
            if (gercek != kayit.ogeSayisi) {
                hatalar += kayit.dosya + ": katalog " + kayit.ogeSayisi +
                    " diyor, CSV " + gercek + " öğe içeriyor"
            }
        }
        assertTrue("Katalog ↔ CSV öğe sayısı ayrışması:\n" + hatalar.joinToString("\n"), hatalar.isEmpty())
    }

    /** ②③ Yetim asset (katalogda kaydı olmayan) ve mükerrer katalog kaydı yok. */
    @Test
    fun yetimAssetVeMukerrerKatalogKaydiYok() {
        val katalog = katalogKayitlari()
        val katalogDosyalari = katalog.map { it.dosya }

        val mukerrer = katalogDosyalari.groupingBy { it }.eachCount().filter { it.value > 1 }
        assertTrue("Katalogda aynı dosya birden çok kez kayıtlı: " + mukerrer, mukerrer.isEmpty())

        val katalogKumesi = katalogDosyalari.toSet()
        val yetim = assetDosyalari().map { it.name }.filter { it !in katalogKumesi }
        assertTrue(
            "Assets'te olup HazirListeler.kt katalogunda kaydı olmayan dosyalar " +
                "(kullanıcı bunları uygulamada göremez):\n" + yetim.joinToString("\n"),
            yetim.isEmpty()
        )

        assertEquals(
            "Katalog kaydı sayısı ile assets dosya sayısı farklı",
            assetDosyalari().size, katalog.size
        )
    }

    /** ④ README tablosu dosya adı + öğe sayısı bakımından katalogla tutarlı. */
    @Test
    fun readmeTablosuDosyalarVeKatalogaUyar() {
        val readme = File(kutuphaneDizini, "README.md")
        assertTrue("README bulunamadı: " + readme, readme.isFile)

        // | 1 | 01_osmanli_padisahlari.csv | 36 | ... |
        val satirDeseni = Regex("^\\|\\s*\\d+\\s*\\|\\s*([0-9A-Za-z_.]+\\.csv)\\s*\\|\\s*(\\d+)\\s*\\|")
        val readmeSatirlari = readme.readLines()
            .mapNotNull { satirDeseni.find(it.trim()) }
            .associate { it.groupValues[1] to it.groupValues[2].toInt() }

        assertTrue(
            "README'de liste tablosu satırı bulunamadı — tablo biçimi mi değişti?",
            readmeSatirlari.isNotEmpty()
        )

        val hatalar = mutableListOf<String>()
        for (kayit in katalogKayitlari()) {
            val readmeSayisi = readmeSatirlari[kayit.dosya]
            when {
                readmeSayisi == null -> hatalar += kayit.dosya + ": katalogda var, README tablosunda YOK"
                readmeSayisi != kayit.ogeSayisi ->
                    hatalar += kayit.dosya + ": README " + readmeSayisi +
                        " diyor, katalog " + kayit.ogeSayisi + " diyor"
            }
        }
        val katalogDosyalari = katalogKayitlari().map { it.dosya }.toSet()
        for (dosya in readmeSatirlari.keys) {
            if (dosya !in katalogDosyalari) hatalar += dosya + ": README tablosunda var, katalogda YOK"
        }

        assertTrue("README ↔ katalog ayrışması:\n" + hatalar.joinToString("\n"), hatalar.isEmpty())
    }

    /** ④ README özet cümlesindeki liste / öğe / görselli sayıları gerçeği söylüyor mu. */
    @Test
    fun readmeOzetSayilariGercekleAyni() {
        val readme = File(kutuphaneDizini, "README.md").readText(Charsets.UTF_8)

        val gercekListe = katalogKayitlari().size
        val gercekOge = katalogKayitlari().sumOf { kayit ->
            val dosya = File(assetDizini, kayit.dosya)
            if (dosya.isFile) ogeSayisi(dosya) else 0
        }
        // Görselli LİSTELERİN öğe toplamı — README'deki "N tanesi görselli" bu sayıdır.
        val gorselliOge = katalogKayitlari().filter { it.gorselli }.sumOf { kayit ->
            val dosya = File(assetDizini, kayit.dosya)
            if (dosya.isFile) ogeSayisi(dosya) else 0
        }

        val ozet = Regex("Toplam\\s+\\*\\*(\\d+) liste\\*\\*,\\s+\\*\\*(\\d+) öğe\\*\\*").find(readme)
        assertTrue("README özet cümlesi bulunamadı ('Toplam **N liste**, **M öğe**')", ozet != null)
        assertEquals("README özet: liste sayısı", gercekListe, ozet!!.groupValues[1].toInt())
        assertEquals("README özet: toplam öğe sayısı", gercekOge, ozet.groupValues[2].toInt())

        val gorselOzet = Regex("\\*\\*(\\d+) tanesi görselli\\*\\*").find(readme)
        assertTrue("README görsel özeti bulunamadı ('**N tanesi görselli**')", gorselOzet != null)
        assertEquals(
            "README özet: görselli öğe sayısı",
            gorselliOge, gorselOzet!!.groupValues[1].toInt()
        )
    }

    /**
     * Mükerrer öğe adları. Bazı listelerde meşru olabilir (aynı adlı iki şarkı),
     * bu yüzden koşulsuz hata yok: yalnızca BİLİNENLERİN dışına çıkılırsa patlar.
     * Yeni bir mükerrer çıkarsa ya veri hatasıdır ya da bilerek eklenmiştir —
     * ikinci durumda BILINEN_MUKERRERLER'e yazılır.
     */
    @Test
    fun mukerrerOgeAdlariYalnizcaBilinenlerdenIbaret() {
        val bulunan = mutableMapOf<String, List<String>>()
        for (dosya in assetDosyalari()) {
            val adlar = reader.parseText(dosya.readText(Charsets.UTF_8)).map { it.name }
            val tekrar = adlar.groupingBy { it }.eachCount().filter { it.value > 1 }.keys.sorted()
            if (tekrar.isNotEmpty()) bulunan[dosya.name] = tekrar
        }

        // (koordinatör düzeltmesi: elvis + destructuring tip çıkarımı çöküyordu)
        val beklenmeyen = bulunan.filter { (dosya, adlar) ->
            adlar.toSet() != BILINEN_MUKERRERLER[dosya].orEmpty()
        }
        assertTrue(
            "Beklenmeyen mükerrer öğe adı. Veri hatasıysa CSV'yi düzelt, " +
                "kasıtlıysa BILINEN_MUKERRERLER'e ekle:\n" +
                beklenmeyen.entries.joinToString("\n") { it.key + ": " + it.value },
            beklenmeyen.isEmpty()
        )
    }

    /** Biçim kuralı: 4. sütun sıralanacak öğedir, yani her dosya en az 4 sütunlu. */
    @Test
    fun herCsvEnAzDortSutunlu() {
        val hatalar = mutableListOf<String>()
        for (dosya in assetDosyalari()) {
            val baslik = dosya.readText(Charsets.UTF_8).removePrefix("﻿")
                .lineSequence().firstOrNull { it.isNotBlank() } ?: ""
            val sutunSayisi = baslik.split(',').size
            if (sutunSayisi < 4) {
                hatalar += dosya.name + ": başlık satırında " + sutunSayisi +
                    " sütun var, en az 4 gerekli"
            }
        }
        assertTrue(
            "Biçim kuralı ihlali (4. sütun = sıralanacak öğe):\n" + hatalar.joinToString("\n"),
            hatalar.isEmpty()
        )
    }

    private companion object {
        const val ASSET_YOLU = "app/src/main/assets/hazir_listeler"
        const val KUTUPHANE_YOLU = "liste_kutuphanesi"

        /** Dosya → o dosyada MEŞRU sayılan mükerrer öğe adları. */
        val BILINEN_MUKERRERLER: Map<String, Set<String>> = emptyMap()
    }
}
