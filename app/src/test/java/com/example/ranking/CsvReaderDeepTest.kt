package com.example.ranking

import com.example.ranking.utils.CsvReader
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * CSV OKUYUCU — DERİN KUSUR ARAMA TESTLERİ
 *
 * Bu parser hazır liste kütüphanesinin TAMAMININ geçtiği yer (31 CSV, ~1854 öğe).
 * Burada kırılan bir şey, kütüphanenin tamamını zehirler.
 *
 * Kapsam:
 *  - RFC-4180 durum makinesi: tırnak içi virgül / satır sonu / "" kaçışı
 *  - ayraç tespiti (virgül · noktalı virgül · tab)
 *  - GERÇEK asset dosyalarından gömülü metinler → 4. sütun (öğe adı) doğru mu
 *  - sütun sayısı 1,2,3,4+ · boş satır · yalnız ayraçtan oluşan satır
 *  - Türkçe karakter ve mojibake onarımı
 *
 * ⚠️ KAPSANAMAYAN: BOM ve windows-1254 tespiti `detectEncodingAndRemoveBOM`
 * içinde private ve yalnız `readCsvFromUri` (Android Context + Uri) yolundan
 * çağrılıyor. JVM birim testinden erişilemiyor — bkz. bomKarakteri_parseTextTarafindanTemizlenir.
 */
class CsvReaderDeepTest {

    private val reader = CsvReader()

    // ==========================================================
    // ① RFC-4180 DURUM MAKİNESİ
    // ==========================================================

    @Test
    fun tirnakIciVirgul_alanBolunmez() {
        val songs = reader.parseText(
            "No,Sanatçı,Albüm,Şarkı\n1,\"Beatles, The\",\"Sgt. Pepper's, Vol. 1\",\"A Day in the Life, Reprise\""
        )
        assertEquals(1, songs.size)
        assertEquals("Beatles, The", songs[0].artist)
        assertEquals("Sgt. Pepper's, Vol. 1", songs[0].album)
        assertEquals("A Day in the Life, Reprise", songs[0].name)
    }

    @Test
    fun tirnakIciSatirSonu_LF() {
        val songs = reader.parseText("Sanatçı,Şarkı\n\"Pink\nFloyd\",\"Shine On\nYou Crazy Diamond\"")
        assertEquals(1, songs.size)
        assertEquals("Pink\nFloyd", songs[0].artist)
        assertEquals("Shine On\nYou Crazy Diamond", songs[0].name)
    }

    @Test
    fun tirnakIciSatirSonu_CRLF() {
        val songs = reader.parseText("Sanatçı,Şarkı\r\n\"Pink\r\nFloyd\",Time\r\nQueen,Bohemian\r\n")
        assertEquals("CRLF'li dosyada iki öğe olmalı", 2, songs.size)
        assertEquals("Time", songs[0].name)
        assertEquals("Bohemian", songs[1].name)
        assertTrue(
            "Tırnak içindeki CRLF alanda korunmalı, bulunan: '${songs[0].artist}'",
            songs[0].artist.contains("Pink") && songs[0].artist.contains("Floyd")
        )
    }

    @Test
    fun kacirilmisTirnak_ciftTirnakCozulur() {
        val songs = reader.parseText("Sanatçı,Şarkı\nQueen,\"A Kind of \"\"Magic\"\"\"")
        assertEquals(1, songs.size)
        assertEquals("A Kind of \"Magic\"", songs[0].name)
    }

    @Test
    fun kacirilmisTirnak_alaninTamamiTirnak() {
        val songs = reader.parseText("Sanatçı,Şarkı\nX,\"\"\"\"\"\"")
        assertEquals(1, songs.size)
        assertEquals("İki kaçırılmış tırnak tek tırnak çifti vermeli", "\"\"", songs[0].name)
    }

    @Test
    fun bosTirnakliAlan() {
        val songs = reader.parseText("No,Sanatçı,Albüm,Şarkı\n1,\"\",\"\",Test")
        assertEquals(1, songs.size)
        assertEquals("", songs[0].artist)
        assertEquals("Test", songs[0].name)
    }

    @Test
    fun sonSatirNewlineIleBitmiyorsaKaybolmaz() {
        val songs = reader.parseText("Sanatçı,Şarkı\nA,Bir\nB,İki")
        assertEquals("Newline ile bitmeyen son satır kaybolmamalı", 2, songs.size)
        assertEquals("İki", songs[1].name)
    }

    @Test
    fun sondakiAyracBosAlanUretir() {
        val songs = reader.parseText("No,Sanatçı,Albüm,Şarkı,Ek\n1,A,B,Test,")
        assertEquals(1, songs.size)
        assertEquals("Test", songs[0].name)
    }

    // ==========================================================
    // ② AYRAÇ TESPİTİ
    // ==========================================================

    @Test
    fun ayrac_virgul() {
        val songs = reader.parseText("Sanatçı,Şarkı\nTarkan,Şımarık")
        assertEquals("Şımarık", songs[0].name)
    }

    @Test
    fun ayrac_noktaliVirgul() {
        val songs = reader.parseText("Sanatçı;Şarkı\nTarkan;Şımarık\nBarış Manço;Gülpembe")
        assertEquals(2, songs.size)
        assertEquals("Gülpembe", songs[1].name)
    }

    @Test
    fun ayrac_tab() {
        val songs = reader.parseText("Sanatçı\tŞarkı\nTarkan\tŞımarık\nSezen Aksu\tFiruze")
        assertEquals(2, songs.size)
        assertEquals("Firuze", songs[1].name)
        assertEquals("Sezen Aksu", songs[1].artist)
    }

    @Test
    fun ayrac_noktaliVirgulDosyadaVeriIcindeVirgulVar() {
        // Noktalı virgüllü dosyada veri alanları virgül içeriyor — ayraç yine ; olmalı
        val songs = reader.parseText("Sanatçı;Şarkı\nBeatles, The;Hey Jude\nQueen;Bohemian, Rhapsody")
        assertEquals(2, songs.size)
        assertEquals("Beatles, The", songs[0].artist)
        assertEquals("Bohemian, Rhapsody", songs[1].name)
    }

    @Test
    fun ayrac_tespitiTirnakIcindekiAyraclariSaymaz() {
        // Başlık satırındaki tırnaklı alanda 3 noktalı virgül var ama gerçek ayraç virgül
        val songs = reader.parseText("No,\"a;b;c;d\",Albüm,Şarkı\n1,X,Y,Test")
        assertEquals("Tırnak içindeki ayraçlar tespitte sayılmamalı", 1, songs.size)
        assertEquals("Test", songs[0].name)
    }

    // ==========================================================
    // ③ SÜTUN SAYISI KALIPLARI
    // ==========================================================

    @Test
    fun sutun4_dortSutunHaritalamasi() {
        val songs = reader.parseText("No,Sanatçı,Albüm,Şarkı\n7,Sezen Aksu,Gülümse,Firuze")
        assertEquals(7, songs[0].trackNumber)
        assertEquals("Sezen Aksu", songs[0].artist)
        assertEquals("Gülümse", songs[0].album)
        assertEquals("Firuze", songs[0].name)
    }

    @Test
    fun sutun3_ucSutunHaritalamasi() {
        val songs = reader.parseText("Sanatçı,Albüm,Şarkı\nSezen Aksu,Gülümse,Firuze")
        assertEquals(0, songs[0].trackNumber)
        assertEquals("Sezen Aksu", songs[0].artist)
        assertEquals("Gülümse", songs[0].album)
        assertEquals("Firuze", songs[0].name)
    }

    @Test
    fun sutun2_ikiSutunHaritalamasi() {
        val songs = reader.parseText("Sanatçı,Şarkı\nSezen Aksu,Firuze")
        assertEquals("Sezen Aksu", songs[0].artist)
        assertEquals("Firuze", songs[0].name)
    }

    @Test
    fun sutun1_tireKalibiAyristirilir() {
        val songs = reader.parseText("Sezen Aksu - Firuze\nMFÖ - Ali Desidero")
        assertEquals("Tek sütunda başlık atlanmamalı", 2, songs.size)
        assertEquals("Sezen Aksu", songs[0].artist)
        assertEquals("Firuze", songs[0].name)
        assertEquals("Ali Desidero", songs[1].name)
    }

    @Test
    fun sutun1_tiresizDuzMetin() {
        val songs = reader.parseText("Elma\nArmut\nKiraz")
        assertEquals(3, songs.size)
        assertEquals("Elma", songs[0].name)
        assertEquals("", songs[0].artist)
    }

    @Test
    fun sutun9_adHalaDorduncuSutundan() {
        val songs = reader.parseText(
            "No,A,B,Ad,C,D,E,F,G\n1,a1,b1,GERÇEK AD,c1,d1,e1,f1,g1"
        )
        assertEquals(1, songs.size)
        assertEquals("9 sütunlu satırda ad 4. sütundan gelmeli", "GERÇEK AD", songs[0].name)
    }

    @Test
    fun bozukSatir_dorttenAzSutun_sessizceKaydiriyor() {
        // BELGELEME: 9 sütunluk dosyada 3 alanlı bozuk bir satır, 3-sütun kalıbına
        // düşüyor ve "ad" 3. alandan alınıyor. Sessiz veri kayması.
        val songs = reader.parseText(
            "No,A,B,Ad,C,D,E,F,G\n" +
                "1,a1,b1,DOĞRU AD,c1,d1,e1,f1,g1\n" +
                "2,a2,b2"
        )
        assertEquals(2, songs.size)
        assertEquals("DOĞRU AD", songs[0].name)
        assertEquals(
            "Eksik sütunlu satırda ad 3. alandan alınıyor (sessiz kayma)",
            "b2", songs[1].name
        )
    }

    @Test
    fun bosAdliSatirSessizceDusuyor() {
        // BELGELEME: 4. sütunu boş olan satır listeye HİÇ girmiyor, uyarı da yok.
        val songs = reader.parseText(
            "No,A,B,Ad\n1,a,b,Var\n2,a,b,\n3,a,b,Yine Var"
        )
        assertEquals("Adı boş satır sessizce düşürülüyor", 2, songs.size)
        assertEquals(listOf("Var", "Yine Var"), songs.map { it.name })
    }

    @Test
    fun bosSatirlarAtlanir() {
        val songs = reader.parseText("Sanatçı,Şarkı\n\nA,Bir\n\n\nB,İki\n")
        assertEquals(2, songs.size)
    }

    @Test
    fun yalnizAyractanOlusanSatirAtlanir() {
        val songs = reader.parseText("No,A,B,Ad\n1,a,b,Var\n,,,\n2,a,b,Yine Var")
        assertEquals("Yalnız ayraçtan oluşan satır atlanmalı", 2, songs.size)
    }

    @Test
    fun bosMetinVeYalnizBosluk() {
        assertTrue(reader.parseText("").isEmpty())
        assertTrue(reader.parseText("   \n  \n").isEmpty())
    }

    // ==========================================================
    // ④ BAŞLIK DAVRANIŞI VE csvData
    // ==========================================================

    @Test
    fun basliksizCokSutunluDosya_ilkSatirKAYBOLUYOR() {
        // BELGELEME (veri kaybı riski): ilk satır 2+ alanlıysa KOŞULSUZ başlık sayılır.
        // Başlıksız bir dosyanın ilk öğesi sessizce yutulur.
        val songs = reader.parseText("Sezen Aksu,Firuze\nMFÖ,Ali Desidero\nTarkan,Şımarık")
        assertEquals("Başlıksız çok sütunlu dosyada ilk satır yutuluyor", 2, songs.size)
        assertFalse("İlk satır öğe olarak gelmemeli (mevcut davranış)", songs.any { it.name == "Firuze" })
    }

    @Test
    fun csvData_tumSutunlariJsonOlarakSaklar() {
        val songs = reader.parseText(
            "No,Sanatçı,Albüm,Şarkı,Yıl\n1,Sezen Aksu,Gülümse,Firuze,1991"
        )
        val raw = songs[0].csvData
        assertNotNull("csvData üretilmeli", raw)
        val json = JSONObject(raw ?: "{}")
        assertEquals("Firuze", json.getString("Şarkı"))
        assertEquals("1991", json.getString("Yıl"))
        assertEquals("Sezen Aksu", json.getString("Sanatçı"))
    }

    @Test
    fun csvData_baslikSayisindanFazlaAlanDusuyor() {
        // BELGELEME: başlıkta 4, satırda 6 alan varsa fazladan 2 alan csvData'ya girmiyor.
        val songs = reader.parseText("No,A,B,Ad\n1,a,b,Test,fazla1,fazla2")
        val json = JSONObject(songs[0].csvData ?: "{}")
        assertEquals("Başlık sayısı kadar anahtar olmalı", 4, json.length())
        assertEquals("Test", json.getString("Ad"))
    }

    // ==========================================================
    // ⑤ TÜRKÇE KARAKTER VE MOJIBAKE
    // ==========================================================

    @Test
    fun turkceKarakterlerBozulmuyor() {
        val songs = reader.parseText("No,A,B,Ad\n1,x,y,ÇĞİÖŞÜçğıöşü")
        assertEquals("ÇĞİÖŞÜçğıöşü", songs[0].name)
    }

    @Test
    fun mojibakeOnarimi_ciftKodlanmisTurkce() {
        // Cift kodlanmis (mojibake) Turkce harfler, ASCII kacislariyla yazildi:
        //   "s" -> U+00C5 U+009F   ·   "i" -> U+00C4 U+00B1
        val nl = 10.toChar()
        val mojibakeSarki = "" + 197.toChar() + 159.toChar() + "ark" + 196.toChar() + 177.toChar()
        val bozuk = "No,A,B,Ad" + nl + "1,x,y," + mojibakeSarki
        val songs = reader.parseText(bozuk)
        assertEquals(
            "Çift kodlanmış Türkçe onarılmalı, bulunan: '${songs[0].name}'",
            "şarkı", songs[0].name
        )
    }

    @Test
    fun mojibakeOnarimi_gercekIsveccceKarakteriBozmuyor() {
        // "Ångström" içindeki Å tek başına — onarım kalıbı iki karakterlik olduğu için
        // dokunulmamalı. Bozulursa bilimsel/yabancı adlar zarar görür.
        val songs = reader.parseText("No,A,B,Ad\n1,x,y,Ångström")
        assertEquals("Tek başına Å bozulmamalı", "Ångström", songs[0].name)
    }

    @Test
    fun bomKarakteri_parseTextTarafindanTemizlenir() {
        // GERİLEME TESTİ: readCsvFromUri BOM'u BAYT düzeyinde siliyor, ama gömülü
        // hazır listeler RankingRepository.importPreparedList içinde
        // `readBytes().toString(UTF_8)` ile okunuyor — o yolda BOM bayt olarak
        // silinmiyor. BOM'lu kaydedilmiş bir asset'te ilk başlık anahtarı
        // "No" yerine "﻿No" olur ve tablo görünümünde ilk sütun kaybolur.
        // parseText baştaki BOM'u temizlemeli.
        val songs = reader.parseText("﻿No,A,B,Ad\n1,x,y,Test")
        assertEquals(1, songs.size)
        assertEquals("Öğe adı BOM'dan etkilenmemeli", "Test", songs[0].name)
        val json = JSONObject(songs[0].csvData ?: "{}")
        assertTrue(
            "BOM ilk başlık anahtarına yapışmış: ${json.keys().asSequence().toList()}",
            json.has("No")
        )
    }

    // ==========================================================
    // ⑥ GERÇEK ASSET DOSYALARI (gömülü metinler)
    //    Kaynak: app/src/main/assets/hazir_listeler/
    // ==========================================================

    private val osmanliCsv = """
No,Tahta Çıkış,Saltanat Süresi,Padişah,Tahttan İniş,Yaşadığı Yıllar,Lakabı/Ünvanı,Dönemin Önemli Olayı,Ölüm Şekli
1,1299,yaklaşık 27 yıl,Osman Gazi,1326,1258-1326,Kara Osman,Osmanlı Beyliği kuruldu,Eceliyle
2,1326,yaklaşık 36 yıl,Orhan Gazi,1362,1281-1362,Gazi,Bursa fethedildi ve ilk teşkilatlanma,Eceliyle
""".trimIndent()

    private val elementlerCsv = """
No,Sembol,Grup,Element,Atom Ağırlığı,Oda Sıcaklığındaki Hali,Keşif Yılı,Periyot,Kullanım Alanı,Ad Kökeni
1,H,1A,Hidrojen,1.008,Gaz,1766,1,Yakıt ve amonyak üretimi,Yunanca su oluşturan
2,He,8A,Helyum,4.003,Gaz,1868,1,Balon ve soğutma,Yunanca güneş Helios
""".trimIndent()

    private val filmlerCsv = """
No,Yıl,Yönetmen,Film,Ülke,Tür,Süre (dk),Önemi,IMDb,Başrol,Görsel
1,1925,Sergey Ayzenştayn,Potemkin Zırhlısı,SSCB,Tarihi Dram,75,Kurgu ve montaj kuramının temel yapıtı,7.9,Aleksandr Antonov,https://upload.wikimedia.org/wikipedia/commons/thumb/8/85/Vintage_Potemkin.jpg/330px-Vintage_Potemkin.jpg
""".trimIndent()

    /** Bu dosyada TIRNAKLI + VİRGÜLLÜ gerçek alanlar var — en riskli gerçek örnek. */
    private val sebnemCsv = """
No,Albüm,Yıl,Şarkı,Tür,Konu,Duygu Tonu,Süre,Söz-Müzik
1,Kadın,1996,Vazgeçtim Dünyadan,Balad,Tacize uğrayıp terk edilen bir kadının trajik çaresizliği,Trajik-isyankâr,5:37,Şebnem Ferah
2,Kadın,1996,Deli Kızım Uyan,Rock,Ölümcül hastalığa yakalanan kardeşe seslenen bir vedalaşma,Hüzünlü-yakıcı,4:25,"Şebnem Ferah, Sezen Aksu / Şebnem Ferah"
""".trimIndent()

    @Test
    fun gercekAsset_osmanli_adDorduncuSutundan() {
        val songs = reader.parseText(osmanliCsv)
        assertEquals(2, songs.size)
        assertEquals("Osman Gazi", songs[0].name)
        assertEquals("Orhan Gazi", songs[1].name)
        assertEquals(1, songs[0].trackNumber)
        assertEquals(2, songs[1].trackNumber)
    }

    @Test
    fun gercekAsset_elementler_adDorduncuSutundan() {
        val songs = reader.parseText(elementlerCsv)
        assertEquals(2, songs.size)
        assertEquals("Hidrojen", songs[0].name)
        assertEquals("Helyum", songs[1].name)
        val json = JSONObject(songs[0].csvData ?: "{}")
        assertEquals("10 sütunun tamamı csvData'da olmalı", 10, json.length())
        assertEquals("H", json.getString("Sembol"))
    }

    @Test
    fun gercekAsset_filmler_gorselUrlBozulmuyor() {
        val songs = reader.parseText(filmlerCsv)
        assertEquals(1, songs.size)
        assertEquals("Potemkin Zırhlısı", songs[0].name)
        val json = JSONObject(songs[0].csvData ?: "{}")
        assertTrue(
            "Görsel URL bozulmamalı: ${json.optString("Görsel")}",
            json.getString("Görsel").startsWith("https://upload.wikimedia.org/")
        )
    }

    @Test
    fun gercekAsset_sebnem_tirnakliAlanTekParcaKaliyor() {
        val songs = reader.parseText(sebnemCsv)
        assertEquals(2, songs.size)
        assertEquals("Vazgeçtim Dünyadan", songs[0].name)
        assertEquals("Deli Kızım Uyan", songs[1].name)
        val json = JSONObject(songs[1].csvData ?: "{}")
        assertEquals(
            "Tırnaklı alan içindeki virgül alanı bölmemeli",
            "Şebnem Ferah, Sezen Aksu / Şebnem Ferah", json.getString("Söz-Müzik")
        )
        assertEquals("9 sütun korunmalı", 9, json.length())
    }

    @Test
    fun gercekAsset_hepsindeAdBosOlmamali() {
        listOf(osmanliCsv, elementlerCsv, filmlerCsv, sebnemCsv).forEachIndexed { i, csv ->
            val songs = reader.parseText(csv)
            assertTrue("Gömülü asset #$i hiç öğe üretmedi", songs.isNotEmpty())
            songs.forEach { s ->
                assertTrue("Gömülü asset #$i içinde adı boş öğe var", s.name.isNotBlank())
            }
        }
    }

    // ==========================================================
    // ⑦ DİSKTEKİ 31 ASSET DOSYASININ TAMAMI
    //    (dosyalar bulunamazsa test başarısız olur — sessizce geçmez)
    // ==========================================================

    private fun assetsDir(): java.io.File? {
        val candidates = listOf(
            "src/main/assets/hazir_listeler",
            "app/src/main/assets/hazir_listeler",
            "../app/src/main/assets/hazir_listeler"
        )
        return candidates.map { java.io.File(it) }.firstOrNull { it.isDirectory }
    }

    @Test
    fun diskteki_tumHazirListeler_ayristirilabiliyor() {
        val dir = assetsDir()
        assertNotNull(
            "assets/hazir_listeler bulunamadı (çalışma dizini: ${java.io.File(".").absolutePath})",
            dir
        )
        val files = dir?.listFiles { f -> f.name.endsWith(".csv") }?.sortedBy { it.name } ?: emptyList()
        assertTrue("Hiç CSV bulunamadı", files.isNotEmpty())

        var totalItems = 0
        val problems = mutableListOf<String>()

        files.forEach { file ->
            val text = file.readBytes().toString(Charsets.UTF_8) // importPreparedList ile aynı okuma
            val songs = reader.parseText(text)
            totalItems += songs.size

            val dataLines = text.lines().drop(1).count { it.isNotBlank() }
            if (songs.size != dataLines) {
                problems.add("${file.name}: ${dataLines} veri satırı → ${songs.size} öğe (fark ${dataLines - songs.size})")
            }
            songs.forEachIndexed { i, s ->
                if (s.name.isBlank()) problems.add("${file.name}: ${i + 1}. öğenin adı boş")
            }
        }

        assertTrue(
            "Gerçek asset dosyalarında satır kaybı / boş ad var:\n" + problems.joinToString("\n"),
            problems.isEmpty()
        )
        assertTrue("Toplam öğe sayısı beklenenden az: $totalItems", totalItems > 1000)
    }
}
