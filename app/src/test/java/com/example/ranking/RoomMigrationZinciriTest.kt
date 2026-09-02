package com.example.ranking

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ROOM MIGRATION ZİNCİRİ + ŞEMA SAPMA BEKÇİSİ
 *
 * Bu proje `fallbackToDestructiveMigration`'ı BİLEREK kaldırdı: kapsanmayan bir
 * sürüm geçişinde kullanıcının tüm verisi sessizce siliniyordu. Bedeli şu: artık
 * zincirdeki her halka DOĞRU olmak zorunda, yoksa uygulama açılışta çöker.
 *
 * Cihaz gerektiren `MigrationTestHelper` bu modülde KULLANILAMIYOR (androidTest
 * kaynak seti yok, sqlite sürücüsü test sınıf yolunda değil). Bu yüzden denetim
 * STATİK yapılır: migration zincirinin kendisi, kayıt listesi ve Room'un dışa
 * aktardığı şema JSON'ları (app/schemas/) kaynak metinle karşılaştırılır.
 *
 * Yakalamayı hedeflediği gerçek hata sınıfları:
 *  1) version bump edilip migration yazılmaması / zincirde delik
 *  2) migration tanımlanıp addMigrations'a eklenmemesi
 *  3) fallbackToDestructiveMigration'ın geri sızması (sessiz veri kaybı)
 *  4) entity'ye kolon/tablo/indeks eklenip migration'a yazılmaması
 *     -> taze kurulum ile yükseltilmiş kurulum FARKLI şemaya sahip olur
 *  5) EMRE -> EMRE_CORRECT dönüşümünün yeni "EMRE_SIRALAMA" yöntem koduna
 *     bulaşması (ön ek eşleşmesi kullanılırsa kullanıcının Emre Sıralama
 *     turnuvaları sessizce Geliştirilmiş İsviçre'ye dönerdi)
 *
 * NOT: Bu testler ANA KODA DOKUNMAZ; yalnız kaynağı okur.
 */
class RoomMigrationZinciriTest {

    // ==========================================================
    // Ortak yardımcılar
    // ==========================================================

    /** JVM testi modül kökünden mi depo kökünden mi koşuyor — ölçerek bul. */
    private fun modulDosyasi(goreliYol: String): File {
        val adaylar = listOf(goreliYol, "app/$goreliYol", "../app/$goreliYol")
        return adaylar.map { File(it) }.firstOrNull { it.exists() }
            ?: throw AssertionError(
                "Dosya bulunamadı: $goreliYol — denenen yollar: $adaylar " +
                    "(çalışma dizini: ${File(".").absoluteFile.canonicalPath})"
            )
    }

    private val dbKaynak: String by lazy {
        modulDosyasi("src/main/java/com/example/ranking/data/RankingDatabase.kt").readText()
    }

    /** Yorumları söker: "KALDIRILDI" gibi açıklamalar kod sanılmasın. */
    private fun yorumsuz(kaynak: String): String {
        val blokSuz = kaynak.replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        return blokSuz.lines().joinToString("\n") { satir ->
            val i = satir.indexOf("//")
            if (i >= 0) satir.substring(0, i) else satir
        }
    }

    private val dbKod: String by lazy { yorumsuz(dbKaynak) }

    private val bildirilenVersiyon: Int by lazy {
        val m = Regex("@Database\\s*\\([\\s\\S]*?version\\s*=\\s*(\\d+)").find(dbKod)
            ?: throw AssertionError("@Database bloğunda version okunamadı")
        m.groupValues[1].toInt()
    }

    /** MIGRATION_a_b bildirimi: hem addaki sürümler hem Migration(a, b) argümanları. */
    private data class MigrationBildirimi(
        val ad: String,
        val addanA: Int,
        val addanB: Int,
        val argA: Int,
        val argB: Int
    )

    private val bildirimler: List<MigrationBildirimi> by lazy {
        Regex(
            "val\\s+(MIGRATION_(\\d+)_(\\d+))\\s*=\\s*object\\s*:\\s*" +
                "Migration\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)"
        )
            .findAll(dbKod)
            .map { m ->
                MigrationBildirimi(
                    ad = m.groupValues[1],
                    addanA = m.groupValues[2].toInt(),
                    addanB = m.groupValues[3].toInt(),
                    argA = m.groupValues[4].toInt(),
                    argB = m.groupValues[5].toInt()
                )
            }
            .toList()
    }

    /** Bir migration'ın gövde metni (bir sonraki bildirime / getDatabase'e kadar). */
    private fun migrationGovdesi(ad: String): String {
        val bas = dbKod.indexOf("val $ad")
        assertTrue("$ad bildirimi bulunamadı", bas >= 0)
        val kalan = dbKod.substring(bas + 4)
        val sonrakiBildirim =
            Regex("val\\s+MIGRATION_\\d+_\\d+").find(kalan)?.range?.first ?: Int.MAX_VALUE
        val getDatabase = kalan.indexOf("fun getDatabase").let { if (it < 0) Int.MAX_VALUE else it }
        val son = minOf(sonrakiBildirim, getDatabase, kalan.length)
        return kalan.substring(0, son)
    }

    // ==========================================================
    // 1) ZİNCİR BÜTÜNLÜĞÜ
    // ==========================================================

    @Test
    fun zincir_1den_guncelVersiyona_kesintisiz() {
        assertTrue("En az bir migration bekleniyor", bildirimler.isNotEmpty())

        val halkalar = bildirimler.map { it.addanA to it.addanB }.sortedBy { it.first }

        assertEquals(
            "Zincir 1'den başlamalı — aksi halde eski kurulumlar açılışta çöker",
            1,
            halkalar.first().first
        )
        assertEquals(
            "Zincirin son halkası @Database version'a varmalı (version=$bildirilenVersiyon)",
            bildirilenVersiyon,
            halkalar.last().second
        )

        halkalar.forEach { (a, b) ->
            assertEquals("MIGRATION_${a}_$b tek adım olmalı (b == a+1)", a + 1, b)
        }

        val kapsanan = halkalar.map { it.first }.toSet()
        val beklenen = (1 until bildirilenVersiyon).toSet()
        assertEquals(
            "Zincirde DELİK var — kapsanmayan sürüm(ler): ${(beklenen - kapsanan).sorted()}; " +
                "fazladan/mükerrer: ${(kapsanan - beklenen).sorted()}",
            beklenen,
            kapsanan
        )
        assertEquals(
            "Aynı sürüm için birden fazla migration bildirilmiş: " +
                "${halkalar.groupBy { it }.filterValues { it.size > 1 }.keys}",
            beklenen.size,
            halkalar.size
        )
    }

    @Test
    fun migrationAdi_Migration_argumanlariyla_ayni() {
        // MIGRATION_9_10 = object : Migration(9, 11) sessizce derlenir ama
        // zincirde delik açar; ad ile argüman birbirini doğrulasın.
        bildirimler.forEach { b ->
            assertEquals("${b.ad} adı ile Migration(${b.argA}, ...) uyuşmuyor", b.addanA, b.argA)
            assertEquals("${b.ad} adı ile Migration(..., ${b.argB}) uyuşmuyor", b.addanB, b.argB)
        }
    }

    // ==========================================================
    // 2) KAYIT: tanımlanan her migration addMigrations'ta mı
    // ==========================================================

    @Test
    fun tanimlanan_her_migration_addMigrations_icinde_kayitli() {
        val m = Regex("addMigrations\\(([^)]*)\\)").find(dbKod)
            ?: throw AssertionError("addMigrations(...) çağrısı bulunamadı")
        val kayitli = m.groupValues[1]
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        val tanimli = bildirimler.map { it.ad }.toSet()

        assertEquals(
            "Tanımlanıp KAYDEDİLMEYEN migration (zincir kopar): ${(tanimli - kayitli).sorted()}",
            emptySet<String>(),
            tanimli - kayitli
        )
        assertEquals(
            "Kaydedilip TANIMLANMAYAN migration adı: ${(kayitli - tanimli).sorted()}",
            emptySet<String>(),
            kayitli - tanimli
        )
    }

    // ==========================================================
    // 3) SESSİZ VERİ KAYBI: fallback geri sızmasın
    // ==========================================================

    @Test
    fun fallbackToDestructiveMigration_geri_sizmamis() {
        assertTrue(
            "fallbackToDestructiveMigration KODA GERİ GELMİŞ — kapsanmayan bir sürüm " +
                "geçişinde kullanıcının tüm turnuva geçmişi sessizce silinir " +
                "(CLAUDE.md kırmızı çizgisi). Yorum satırı değil, canlı çağrı bulundu.",
            !dbKod.contains("fallbackToDestructiveMigration")
        )
        // Yorumda anılması serbest ve beklenir (neden kaldırıldığı yazıyor).
        assertTrue(
            "Kaldırma gerekçesi kaynaktan da silinmiş — yorum korunsun ki tekrar eklenmesin",
            dbKaynak.contains("fallbackToDestructiveMigration")
        )
    }

    // ==========================================================
    // 4) EMRE -> EMRE_CORRECT dönüşümü EMRE_SIRALAMA'ya BULAŞMAMALI
    // ==========================================================

    @Test
    fun emreDonusumu_tamEsitlik_EMRE_SIRALAMA_etkilenmez() {
        val emreliIfadeler = Regex("execSQL\\(\\s*\"([^\"]*EMRE[^\"]*)\"")
            .findAll(dbKod)
            .map { it.groupValues[1] }
            .toList()

        assertTrue(
            "EMRE dönüşümü SQL'i bulunamadı — MIGRATION_8_9 kaldırıldıysa bu test güncellensin",
            emreliIfadeler.isNotEmpty()
        )

        val beklenenTablolar = setOf(
            "matches", "ranking_results", "voting_sessions", "league_settings", "archives"
        )
        val gorulenTablolar = mutableSetOf<String>()

        emreliIfadeler.forEach { sql ->
            assertTrue(
                "EMRE dönüşümünde ÖN EK eşleşmesi kullanılmış — 'EMRE_SIRALAMA' " +
                    "turnuvaları da EMRE_CORRECT'e dönerdi: $sql",
                !sql.contains("LIKE", ignoreCase = true) && !sql.contains("EMRE%")
            )

            val where = Regex("WHERE\\s+(\\w+)\\s*=\\s*'([^']*)'").find(sql)
                ?: throw AssertionError("EMRE ifadesinde tam eşitlikli WHERE yok: $sql")
            val aranan = where.groupValues[2]
            assertEquals(
                "WHERE literali TAM 'EMRE' olmalı (bulunan: '$aranan') — $sql",
                "EMRE",
                aranan
            )

            val set = Regex("SET\\s+(\\w+)\\s*=\\s*'([^']*)'").find(sql)
                ?: throw AssertionError("EMRE ifadesinde SET yok: $sql")
            assertEquals(
                "Hedef değer EMRE_CORRECT olmalı — $sql",
                "EMRE_CORRECT",
                set.groupValues[2]
            )

            Regex("UPDATE\\s+(\\w+)").find(sql)?.let { gorulenTablolar += it.groupValues[1] }

            // Anlamsal kanıt: aynı koşul, yöntem kodu ailesinin tamamına uygulanır.
            val dokunulanlar = listOf(
                "EMRE", "EMRE_CORRECT", "EMRE_SIRALAMA", "EMRE_SIRALAMA_V2", "MERGE_SORT", "HIBRIT"
            ).filter { it == aranan }
            assertEquals(
                "Bu koşul yalnız eski 'EMRE' kaydını dönüştürmeli; dokunduğu değerler: $dokunulanlar",
                listOf("EMRE"),
                dokunulanlar
            )
        }

        assertEquals(
            "EMRE dönüşümü bazı tablolarda uygulanmamış — eksik: " +
                "${(beklenenTablolar - gorulenTablolar).sorted()}",
            emptySet<String>(),
            beklenenTablolar - gorulenTablolar
        )
    }

    @Test
    fun yontem_kodu_EMRE_olarak_yeniden_kullanilmamis() {
        // CLAUDE.md: yöntem kodu "EMRE" OLAMAZ — göç onu EMRE_CORRECT'e çevirir.
        val motorDizin = modulDosyasi("src/main/java/com/example/ranking")
        val suclular = motorDizin.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "RankingDatabase.kt" }
            .filter { dosya -> Regex("\"EMRE\"").containsMatchIn(dosya.readText()) }
            .map { it.name }
            .toSet()
        assertEquals(
            "Ana kodda \"EMRE\" yöntem kodu string'i geçiyor — MIGRATION_8_9 bunu " +
                "EMRE_CORRECT'e çevirdiği için bu kod kalıcı olamaz. Dosyalar: $suclular",
            emptySet<String>(),
            suclular
        )
    }

    // ==========================================================
    // 5) ŞEMA DIŞA AKTARIMI
    // ==========================================================

    private fun semaDizini(): File =
        modulDosyasi("schemas/com.example.ranking.data.RankingDatabase")

    private fun semaVarMi(versiyon: Int): Boolean = File(semaDizini(), "$versiyon.json").exists()

    private fun sema(versiyon: Int): JSONObject {
        val dosya = File(semaDizini(), "$versiyon.json")
        assertTrue("Şema dışa aktarılmamış: ${dosya.path}", dosya.exists())
        return JSONObject(dosya.readText()).getJSONObject("database")
    }

    @Test
    fun guncelVersiyonun_semasi_disa_aktarilmis_ve_tutarli() {
        assertTrue(
            "Güncel sürümün şeması yok: $bildirilenVersiyon.json — room.schemaLocation " +
                "ayarlı ama export çalışmamış; bu dosya olmadan hiçbir migration " +
                "gerçek şemaya karşı doğrulanamaz",
            semaVarMi(bildirilenVersiyon)
        )
        assertEquals(
            "Şema dosyasındaki version, @Database version ile uyuşmuyor",
            bildirilenVersiyon,
            sema(bildirilenVersiyon).getInt("version")
        )

        val mevcut = (1..bildirilenVersiyon).filter { semaVarMi(it) }
        val eksik = (1..bildirilenVersiyon).filterNot { semaVarMi(it) }
        // Eski sürümlerin export'u geçmişte tutulmamış; bu bir GERİLEME değil,
        // devralınan boşluk. Testi kırmızı bırakmıyoruz ama görünür kılıyoruz.
        println("[ŞEMA EXPORT DURUMU] mevcut=$mevcut eksik=$eksik")

        assertTrue(
            "Bir önceki sürümün şeması da bulunmalı ki son geçiş farkı denetlenebilsin " +
                "(eksik: ${bildirilenVersiyon - 1}.json)",
            semaVarMi(bildirilenVersiyon - 1)
        )
    }

    // ==========================================================
    // 6) ŞEMA SAPMASI: entity kaynağı - dışa aktarılan şema
    // ==========================================================

    private data class EntityBilgisi(val dosya: String, val tablo: String, val kolonlar: Set<String>)

    /** data class parametre listesindeki `val ad:` isimleri = Room kolon adları. */
    private fun entityleriOku(): List<EntityBilgisi> {
        val dizin = modulDosyasi("src/main/java/com/example/ranking/data")
        val dosyalar = dizin.listFiles { f: File -> f.isFile && f.extension == "kt" }.orEmpty()
        val sonuc = dosyalar.mapNotNull { dosya ->
            val ham = dosya.readText()
            if (!ham.contains("@Entity")) return@mapNotNull null
            val kod = yorumsuz(ham)

            val tablo = Regex("tableName\\s*=\\s*\"([^\"]+)\"").find(kod)?.groupValues?.get(1)
                ?: throw AssertionError("${dosya.name}: @Entity var ama tableName yok")

            val basIdx = kod.indexOf("data class")
            assertTrue("${dosya.name}: data class bulunamadı", basIdx >= 0)
            val acilis = kod.indexOf('(', basIdx)
            assertTrue("${dosya.name}: kurucu parantezi bulunamadı", acilis >= 0)
            var derinlik = 0
            var kapanis = -1
            for (i in acilis until kod.length) {
                when (kod[i]) {
                    '(' -> derinlik++
                    ')' -> {
                        derinlik--
                        if (derinlik == 0) {
                            kapanis = i
                            break
                        }
                    }
                }
            }
            assertTrue("${dosya.name}: kurucu parantezi kapanmıyor", kapanis > acilis)
            val kurucu = kod.substring(acilis, kapanis)
            val kolonlar = Regex("\\bval\\s+(\\w+)\\s*:")
                .findAll(kurucu)
                .map { it.groupValues[1] }
                .toSet()
            assertTrue("${dosya.name}: kurucuda alan bulunamadı", kolonlar.isNotEmpty())

            EntityBilgisi(dosya.name, tablo, kolonlar)
        }
        assertTrue("data paketinde hiç @Entity bulunamadı", sonuc.isNotEmpty())
        return sonuc
    }

    private fun semaTablolari(versiyon: Int): Map<String, Set<String>> {
        val entities = sema(versiyon).getJSONArray("entities")
        return (0 until entities.length()).associate { i ->
            val e = entities.getJSONObject(i)
            val alanlar = e.getJSONArray("fields")
            val kolonlar = (0 until alanlar.length())
                .map { alanlar.getJSONObject(it).getString("columnName") }
                .toSet()
            e.getString("tableName") to kolonlar
        }
    }

    @Test
    fun semaSapmasi_entity_tablolari_ile_sema_ayni() {
        val kaynakTablolar = entityleriOku().map { it.tablo }.toSet()
        val semadaki = semaTablolari(bildirilenVersiyon).keys

        assertEquals(
            "@Entity tanımlı ama şemada YOK (entity @Database entities listesine " +
                "eklenmemiş olabilir): ${(kaynakTablolar - semadaki).sorted()}",
            emptySet<String>(),
            kaynakTablolar - semadaki
        )
        assertEquals(
            "Şemada var ama kaynakta @Entity yok (silinmiş entity, şema bayat): " +
                "${(semadaki - kaynakTablolar).sorted()}",
            emptySet<String>(),
            semadaki - kaynakTablolar
        )
    }

    @Test
    fun semaSapmasi_entity_kolonlari_ile_sema_ayni() {
        val semadaki = semaTablolari(bildirilenVersiyon)
        val sapmalar = mutableListOf<String>()

        entityleriOku().forEach { e ->
            val semaKolon = semadaki[e.tablo] ?: return@forEach
            val fazla = e.kolonlar - semaKolon
            val eksik = semaKolon - e.kolonlar
            if (fazla.isNotEmpty()) {
                sapmalar += "${e.dosya} (${e.tablo}): entity'de VAR şemada YOK -> " +
                    "${fazla.sorted()} — version bump + migration unutulmuş olabilir"
            }
            if (eksik.isNotEmpty()) {
                sapmalar += "${e.dosya} (${e.tablo}): şemada VAR entity'de YOK -> ${eksik.sorted()}"
            }
        }

        assertEquals(
            "ŞEMA SAPMASI (taze kurulum ile yükseltilmiş kurulum farklı şema alır):\n" +
                sapmalar.joinToString("\n"),
            emptyList<String>(),
            sapmalar
        )
    }

    // ==========================================================
    // 7) SÜRÜM FARKI - MIGRATION SQL ÖRTÜŞMESİ
    //    (dışa aktarılmış ardışık şema çiftleri için)
    // ==========================================================

    private fun semaIndeksleri(versiyon: Int): Map<String, String> {
        val entities = sema(versiyon).getJSONArray("entities")
        val sonuc = mutableMapOf<String, String>()
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val idx = e.optJSONArray("indices") ?: continue
            for (j in 0 until idx.length()) {
                val ix = idx.getJSONObject(j)
                val kolonlar = ix.getJSONArray("columnNames")
                val kolonListe = (0 until kolonlar.length()).map { kolonlar.getString(it) }
                sonuc[ix.getString("name")] =
                    "unique=${ix.optBoolean("unique", false)} cols=$kolonListe"
            }
        }
        return sonuc
    }

    /** Ardışık ve İKİSİ de dışa aktarılmış sürüm çiftleri. */
    private fun denetlenebilirGecisler(): List<Pair<Int, Int>> =
        (1 until bildirilenVersiyon)
            .filter { semaVarMi(it) && semaVarMi(it + 1) }
            .map { it to it + 1 }

    @Test
    fun gecisFarki_migration_SQLinde_karsiligini_buluyor() {
        val gecisler = denetlenebilirGecisler()
        assertTrue(
            "Ardışık iki şema export'u yok — geçiş farkı denetlenemiyor",
            gecisler.isNotEmpty()
        )
        println("[DENETLENEN GEÇİŞLER] $gecisler")

        val eksikler = mutableListOf<String>()

        gecisler.forEach { (a, b) ->
            val govde = migrationGovdesi("MIGRATION_${a}_$b")
            val onceki = semaTablolari(a)
            val sonraki = semaTablolari(b)

            (sonraki.keys - onceki.keys).forEach { yeniTablo ->
                if (!govde.contains(yeniTablo)) {
                    eksikler += "v$a->v$b: yeni TABLO '$yeniTablo' migration SQL'inde geçmiyor"
                }
            }

            sonraki.forEach { (tablo, kolonlar) ->
                val eskiKolonlar = onceki[tablo] ?: return@forEach
                (kolonlar - eskiKolonlar).forEach { yeniKolon ->
                    if (!govde.contains(yeniKolon)) {
                        eksikler += "v$a->v$b: '$tablo' tablosuna eklenen '$yeniKolon' kolonu " +
                            "migration SQL'inde geçmiyor — yükseltilen cihazlarda kolon OLUŞMAZ"
                    }
                }
            }

            val eskiIdx = semaIndeksleri(a)
            val yeniIdx = semaIndeksleri(b)
            yeniIdx.forEach { (ad, imza) ->
                if (eskiIdx[ad] != imza && !govde.contains(ad)) {
                    eksikler += "v$a->v$b: indeks '$ad' değişti/eklendi ($imza) ama migration " +
                        "SQL'inde adı geçmiyor — taze kurulumda var, yükseltmede YOK"
                }
            }
            (eskiIdx.keys - yeniIdx.keys).forEach { silinen ->
                if (!govde.contains(silinen)) {
                    eksikler += "v$a->v$b: indeks '$silinen' şemadan kalkmış ama migration " +
                        "onu DROP etmiyor — yükseltilen cihazlarda artık kalır"
                }
            }
        }

        assertEquals(
            "MIGRATION SQL'İ ŞEMA FARKINI KARŞILAMIYOR:\n" + eksikler.joinToString("\n"),
            emptyList<String>(),
            eksikler
        )
    }
}
