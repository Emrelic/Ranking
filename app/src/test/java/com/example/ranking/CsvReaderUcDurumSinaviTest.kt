package com.example.ranking

import com.example.ranking.utils.CsvReader
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * CSV OKUYUCU — RFC-4180 UÇ DURUM SINAVI
 *
 * Bu dosya `CsvReaderTest` ve `CsvReaderDeepTest` üzerine EK'tir; oradaki
 * senaryolar (tırnak içi virgül, "" kaçışı, ayraç tespiti, BOM/bayt düzeyi,
 * mojibake, gerçek asset dosyaları) burada TEKRAR EDİLMEZ.
 *
 * Buradaki kapsam, o iki dosyada bulunmayan boşluklar:
 *  ① csvData kayıpsızlığı — tekrarlı/boş başlık, tek sütun, eksik alan
 *  ② başlık tespitinin çöktüğü yapılar — yalnız başlık satırı, tek sütunlu dosya
 *  ③ karışık satır sonları ve tırnak içi CR'nin akıbeti
 *  ④ bozuk tırnak kullanımı (kapanmamış, kapanıştan sonra metin, alan ortasında)
 *  ⑤ emoji / vekil çift (surrogate pair) ve NFD → NFC normalizasyonu
 *  ⑥ şarkı sözü boyutunda çok uzun hücre
 *
 * `belgeleme_` önekli testler KUSURU ya da tartışmalı sözleşmeyi SABİTLER
 * (evdeki mevcut idiom — bkz. `belgeleme_bosAdliSatirSessizceDusuyor`).
 * Yeşil geçmeleri "sorun yok" demek değildir; mevcut davranışın sessizce
 * değişmesini engellemek içindir. Kusur listesi:
 * `oturumlar/CSVREADER-SINAV-RAPOR.md`
 */
class CsvReaderUcDurumSinaviTest {

    private val reader = CsvReader()

    // ==========================================================
    // ① csvData KAYIPSIZLIĞI
    // ==========================================================

    @Test
    fun belgeleme_csvData_tekrarliBaslikAdi_oncekiSutunuEZIYOR() {
        // KUSUR: csvData bir JSONObject; aynı adlı iki başlık varsa ikincisi
        // birincinin değerini eziyor. Kullanıcının elle hazırladığı listede
        // iki "Not" sütunu olması olağan — biri sessizce kayboluyor.
        val songs = reader.parseText("No,Not,Ad,Not\n1,ILK_DEGER,Test,IKINCI_DEGER")

        assertEquals(1, songs.size)
        val json = JSONObject(songs[0].csvData ?: "{}")
        assertEquals("Tekrarlı başlık tek anahtara düşüyor", 3, json.length())
        assertEquals("Sonraki sütun öncekini eziyor", "IKINCI_DEGER", json.getString("Not"))
        assertFalse(
            "ILK_DEGER csvData'nın hiçbir yerinde yok — sessiz kayıp",
            (songs[0].csvData ?: "").contains("ILK_DEGER")
        )
    }

    @Test
    fun belgeleme_csvData_basligiBosSutun_degeriTumuylaDUSURUYOR() {
        // KUSUR: mapRowToSong `headers[i].isNotBlank()` şartıyla başlığı boş olan
        // sütunu JSON'a hiç yazmıyor. Excel'den gelen dosyalarda görsel/URL sütunu
        // sık sık başlıksız olur; verisi tablo görünümünden tamamen düşer.
        val songs = reader.parseText("No,A,,Ad\n1,a,https://gorsel/1.jpg,Test")

        val json = JSONObject(songs[0].csvData ?: "{}")
        assertEquals("Başlıksız sütun JSON'a girmiyor", 3, json.length())
        assertFalse(
            "Başlıksız sütunun değeri tümüyle kayıp",
            (songs[0].csvData ?: "").contains("gorsel")
        )
    }

    @Test
    fun belgeleme_csvData_tekSutunluDosyadaHIC_URETILMIYOR() {
        // KUSUR/SÖZLEŞME: csvData yalnız başlık varsa üretiliyor. Tek sütunlu dosyada
        // başlık tespiti hiç çalışmadığı (bkz. aşağıdaki başlık testleri) için
        // csvData null kalıyor → o liste için tablo görünümü tümden yok.
        val songs = reader.parseText("Firuze\nGülümse\nHadi Bakalım")

        assertEquals(3, songs.size)
        assertTrue("Tek sütunlu dosyada csvData üretilmiyor", songs.all { it.csvData == null })
    }

    @Test
    fun csvData_basliktanEksikAlanliSatirdaAnahtarHicYok() {
        // Satırda başlıktan AZ alan varsa eksik anahtar JSON'a hiç yazılmıyor
        // (boş string olarak da yazılmıyor). Okuyan taraf `has()` kontrolü yapmalı.
        val songs = reader.parseText("No,A,B,Ad,Yıl\n1,a,b,Test")

        val json = JSONObject(songs[0].csvData ?: "{}")
        assertEquals("Yalnız var olan alanlar yazılıyor", 4, json.length())
        assertFalse("Eksik sütun anahtarı hiç yok", json.has("Yıl"))
        assertEquals("Test", json.getString("Ad"))
    }

    @Test
    fun csvData_dokuzSutunluTamSatir_tumSutunlarGeriOkunabiliyor() {
        // Kayıpsızlık ana testi: başlık sayısı = alan sayısı olan düzgün bir satırda
        // HİÇBİR sütun kaybolmamalı, değerler birebir geri okunabilmeli.
        val basliklar = listOf("No", "Sanatçı", "Albüm", "Ad", "Yıl", "Süre", "Tür", "Görsel", "Not")
        val degerler = listOf(
            "1", "Sezen Aksu", "Gülümse", "Firuze", "1991",
            "4:12", "Pop", "https://a/b.jpg?x=1&y=2", "İlk kayıt"
        )
        val songs = reader.parseText(
            basliklar.joinToString(",") + "\n" + degerler.joinToString(",")
        )

        assertEquals(1, songs.size)
        val json = JSONObject(songs[0].csvData ?: "{}")
        assertEquals("Dokuz sütunun tamamı JSON'da olmalı", 9, json.length())
        basliklar.forEachIndexed { i, baslik ->
            assertEquals("'$baslik' sütunu kayıpsız geri okunmalı", degerler[i], json.getString(baslik))
        }
    }

    @Test
    fun csvData_tirnakliVeVirgulluDegerJsonaKacisliYaziliyor() {
        // Alan içindeki virgül, tırnak ve satır sonu JSON'a girip geri okunabilmeli.
        val songs = reader.parseText(
            "No,A,B,Ad\n1,\"Beatles, The\",\"A \"\"B\"\" C\",\"İki\nSatır\""
        )

        val json = JSONObject(songs[0].csvData ?: "{}")
        assertEquals("Beatles, The", json.getString("A"))
        assertEquals("A \"B\" C", json.getString("B"))
        assertEquals("İki\nSatır", json.getString("Ad"))
    }

    // ==========================================================
    // ② BAŞLIK TESPİTİNİN ÇÖKTÜĞÜ YAPILAR
    // ==========================================================

    @Test
    fun yalnizBaslikSatiriIcerenDosya_bosListeDoner_patlamaz() {
        assertTrue("Yalnız başlık satırı boş liste vermeli", reader.parseText("No,Sanatçı,Albüm,Ad").isEmpty())
        assertTrue("Sondaki newline de değiştirmemeli", reader.parseText("No,Sanatçı,Albüm,Ad\n").isEmpty())
        assertTrue("CRLF'li hâli de aynı", reader.parseText("No,Sanatçı,Albüm,Ad\r\n").isEmpty())
    }

    @Test
    fun belgeleme_tekSutunluDosyada_baslikVeriSayiliyor() {
        // KUSUR: başlık tespiti `ilkSatir.size >= 2` şartına bağlı. Tek sütunlu bir
        // dosyada başlık satırı ("Şarkı") ÖĞE olarak listeye giriyor — 100 şarkılık
        // listeden 101 öğe çıkıyor ve ilk sıradaki öğe bir sütun adı oluyor.
        val songs = reader.parseText("Şarkı\nFiruze\nGülümse")

        assertEquals("Başlık da öğe sayılıyor", 3, songs.size)
        assertEquals("Sütun adı ilk öğe olarak listeye giriyor", "Şarkı", songs[0].name)
    }

    @Test
    fun belgeleme_ilkSatirdaAyracYoksa_baslikTespitiCokuyor() {
        // KUSUR: başlık kararı yalnız İLK satırın hücre sayısına bakıyor. Tek hücreli
        // bir başlık satırının ardından çok sütunlu veri gelirse (elle düzenlenmiş
        // dosyalarda olur) başlık hem öğe sayılıyor hem de TÜM satırlar csvData'sız kalıyor.
        val songs = reader.parseText("Şarkı Listesi\n1,Sezen Aksu,Gülümse,Firuze\n2,MFÖ,Ele Güne,Diday")

        assertEquals("Başlık satırı öğe olmuş", 3, songs.size)
        assertEquals("Şarkı Listesi", songs[0].name)
        assertTrue("Başlık yok sayıldığı için csvData hiç üretilmiyor", songs.all { it.csvData == null })
    }

    @Test
    fun ad_daimaDorduncuSutundan_baslikAdlariOnemsiz() {
        // SÖZLEŞME: 4+ sütunlu dosyada `name` KONUMSAL olarak 4. sütundan alınır.
        // Başlıkta "Ad"/"Şarkı" nerede yazarsa yazsın parser ona bakmaz.
        val songs = reader.parseText("Sıra,Şarkı,Albüm,Sanatçı\n1,Firuze,Gülümse,Sezen Aksu")

        assertEquals(1, songs.size)
        assertEquals(
            "Başlıkta 'Şarkı' 2. sütun olsa da ad 4. sütundan alınıyor",
            "Sezen Aksu", songs[0].name
        )
        assertEquals("Firuze", songs[0].artist)
    }

    @Test
    fun bosIlkHucre_trackNumberSifirOlur_satirDusmez() {
        val songs = reader.parseText("No,A,B,Ad\n,a,b,Test\n2,c,d,Test2")

        assertEquals(2, songs.size)
        assertEquals("Boş numara 0'a düşmeli, satır kaybolmamalı", 0, songs[0].trackNumber)
        assertEquals("Test", songs[0].name)
        assertEquals(2, songs[1].trackNumber)
    }

    @Test
    fun sayisalOlmayanIlkHucre_trackNumberSifir_adBozulmaz() {
        val songs = reader.parseText("No,A,B,Ad\nA-1,a,b,Test")

        assertEquals(1, songs.size)
        assertEquals(0, songs[0].trackNumber)
        assertEquals("Test", songs[0].name)
    }

    // ==========================================================
    // ③ SATIR SONLARI
    // ==========================================================

    @Test
    fun karisikSatirSonlari_LF_CRLF_CR_ayniDosyada() {
        val songs = reader.parseText("No,A,B,Ad\r\n1,x,y,Bir\n2,x,y,İki\r3,x,y,Üç\r\n4,x,y,Dört")

        assertEquals("Karışık satır sonları dört öğe vermeli", 4, songs.size)
        assertEquals(listOf("Bir", "İki", "Üç", "Dört"), songs.map { it.name })
    }

    @Test
    fun belgeleme_tirnakIciCRLF_crKarakteriHucredeKaliyor() {
        // KUSUR (düşük şiddet): tırnak içindeki satır sonu normalize edilmiyor;
        // CRLF'li bir dosyada çok satırlı hücre "\r\n" içeriyor. Tek satırlık bir
        // Compose Text'te ham CR görünmez bir kutu/kayma üretebilir.
        // (Mevcut `tirnakIciSatirSonu_CRLF` testi yalnız "Pink ve Floyd geçiyor mu"
        // diye bakıyor; CR'nin akıbetini SABİTLEMİYOR — burada sabitleniyor.)
        val songs = reader.parseText("Sanatçı,Şarkı\r\n\"Pink\r\nFloyd\",Time\r\n")

        assertEquals(1, songs.size)
        assertEquals("Tırnak içi CRLF ham hâliyle korunuyor", "Pink\r\nFloyd", songs[0].artist)
    }

    @Test
    fun tirnakIciLF_temizKaliyor_crBulasmiyor() {
        val songs = reader.parseText("Sanatçı,Şarkı\n\"Pink\nFloyd\",Time\n")

        assertEquals("Pink\nFloyd", songs[0].artist)
    }

    @Test
    fun dosyaSonundakiSatirSonu_hayaletSatirUretmez() {
        val lfli = reader.parseText("No,A,B,Ad\n1,x,y,Tek\n")
        val crlfli = reader.parseText("No,A,B,Ad\r\n1,x,y,Tek\r\n")
        val cokBos = reader.parseText("No,A,B,Ad\n1,x,y,Tek\n\n\n")

        assertEquals("LF ile biten dosyada tek öğe", 1, lfli.size)
        assertEquals("CRLF ile biten dosyada tek öğe", 1, crlfli.size)
        assertEquals("Arka arkaya boş satırlar hayalet öğe üretmemeli", 1, cokBos.size)
    }

    // ==========================================================
    // ④ BOZUK TIRNAK KULLANIMI
    // ==========================================================

    @Test
    fun kapanmamisTirnak_dosyaSonunda_veriKaybolmaz() {
        // Kullanıcı dosyası tırnağı kapatmadan bitebilir. Parser çökmemeli,
        // son alan da kaybolmamalı.
        val songs = reader.parseText("Sanatçı,Şarkı\nQueen,\"Bohemian Rhapsody")

        assertEquals(1, songs.size)
        assertEquals("Kapanmamış tırnaklı alan yine de okunmalı", "Bohemian Rhapsody", songs[0].name)
        assertEquals("Queen", songs[0].artist)
    }

    @Test
    fun belgeleme_kapanmamisTirnak_geriKalanSatirlariYutar() {
        // BELGELEME: kapanmamış tırnaktan SONRAKİ satırlar aynı hücrenin içine
        // düşüyor (RFC-4180'e göre doğru davranış, ama kullanıcı için sürpriz:
        // "50 satırlık listemden 1 öğe geldi").
        val songs = reader.parseText("Sanatçı,Şarkı\nQueen,\"Bohemian\nABBA,Mamma Mia\nQueen,Radio Gaga")

        assertEquals("Açık tırnak sonrası tüm satırlar tek hücrede toplanıyor", 1, songs.size)
        assertTrue(
            "Sonraki satırlar yutulmuş olmalı, bulunan: '${songs[0].name}'",
            songs[0].name.contains("Mamma Mia")
        )
    }

    @Test
    fun belgeleme_kapanisTirnagindanSonrakiMetin_bitisikYaziliyor() {
        // RFC-4180 bunu geçersiz sayar; parser hoşgörülü davranıp birleştiriyor.
        val songs = reader.parseText("No,A,B,Ad\n1,x,y,\"ab\"cd")

        assertEquals("abcd", songs[0].name)
    }

    @Test
    fun belgeleme_alanOrtasindakiTirnak_duzMetinSayilir() {
        // Alan başında olmayan tırnak, tırnaklı alan AÇMAZ; harfi harfine kalır.
        val songs = reader.parseText("No,A,B,Ad\n1,x,y,5\" plak")

        assertEquals("5\" plak", songs[0].name)
    }

    @Test
    fun belgeleme_metinSonrasiTirnak_virguluKoruyamiyor() {
        // KUSUR (asimetri): alan başındaki BOŞLUKTAN sonra gelen tırnak alanı
        // tırnaklı açıyor (`alanBasindakiBosluktanSonraTirnak_tirnakliSayilir`),
        // ama HARFTEN sonra gelen tırnak açmıyor. Böyle bir alandaki virgül
        // ayraç sayılıp satırı bölüyor ve "ad" yanlış parçadan geliyor.
        val songs = reader.parseText("No,A,B,Ad\n1,x,y,Grup \"Ali, Veli\"")

        assertEquals(1, songs.size)
        assertEquals(
            "Virgül ayraç sayıldı, ad yarıda kesildi",
            "Grup \"Ali", songs[0].name
        )
    }

    @Test
    fun belgeleme_tirnakliAlandakiBasSonBoslugu_trimEdiliyor() {
        // KUSUR (RFC-4180 sapması): tırnaklı alanda boşluk anlamlıdır ve korunmalıdır;
        // parseText her hücreye koşulsuz `trim()` uyguluyor.
        val songs = reader.parseText("No,A,B,Ad\n1,x,y,\"   Ali   \"")

        assertEquals("Tırnak içi boşluk korunmuyor", "Ali", songs[0].name)
    }

    @Test
    fun bosTirnakliAd_satirSessizceDusuyor() {
        // "" ile yazılmış boş ad, boş ad kuralına takılıp satırı düşürüyor.
        val songs = reader.parseText("No,A,B,Ad\n1,a,b,\"\"\n2,a,b,Var")

        assertEquals(1, songs.size)
        assertEquals("Var", songs[0].name)
    }

    // ==========================================================
    // ⑤ EMOJİ · VEKİL ÇİFT · NORMALİZASYON
    // ==========================================================

    @Test
    fun emoji_vekilCift_bozulmadanGecer() {
        // 😊 U+1F60A tek bir vekil çift (surrogate pair). Karakter karakter dolaşan
        // durum makinesi çiftleri bölmemeli.
        val gulumseme = "😊"
        val songs = reader.parseText("No,A,B,Ad\n1,x,y,Mutlu $gulumseme Şarkı")

        assertEquals(1, songs.size)
        assertEquals("Mutlu $gulumseme Şarkı", songs[0].name)
    }

    @Test
    fun emoji_zwjDizisi_parcalanmaz() {
        // 👨‍👩‍👧 — ZWJ (U+200D) ile birleşmiş aile emojisi: üç vekil çift + iki ZWJ.
        val zwj = '\u200D'
        val aile = "👨$zwj👩$zwj👧"
        val songs = reader.parseText("No,A,B,Ad\n1,x,y,$aile")

        assertEquals(aile, songs[0].name)
        assertEquals("ZWJ karakterleri de korunmalı", 2, songs[0].name.count { it == zwj })
    }

    @Test
    fun emoji_tirnakIciVirgulle_birlikte() {
        val bayrak = "🇹🇷"
        val songs = reader.parseText("No,A,B,Ad\n1,x,y,\"$bayrak, Türkiye\"")

        assertEquals("$bayrak, Türkiye", songs[0].name)
    }

    @Test
    fun emoji_csvDataJsonundanGeriOkunabiliyor() {
        val nota = "🎵"
        val songs = reader.parseText("No,A,B,Ad,Simge\n1,x,y,Test,$nota")

        val json = JSONObject(songs[0].csvData ?: "{}")
        assertEquals("Emoji JSON turundan sağ çıkmalı", nota, json.getString("Simge"))
    }

    @Test
    fun emoji_baytDuzeyinde_utf8OlarakCozulur() {
        // Emoji baytları geçerli UTF-8'dir; katı doğrulayıcı bunu cp1254'e düşürmemeli.
        val metin = "No,A,B,Ad\n1,x,y,Şarkı 🎸"
        val bytes = metin.toByteArray(StandardCharsets.UTF_8)

        assertTrue("Emoji içeren metin geçerli UTF-8 sayılmalı", reader.isValidUtf8(bytes))
        val songs = reader.parseText(reader.bytesToText(bytes))
        assertEquals("Şarkı 🎸", songs[0].name)
    }

    @Test
    fun nfd_ayrisikTurkceHarf_nfcYeToplanir() {
        // "ü" ayrışık yazımı (u + U+0308). NFC birleşik biçime toplamalı ki
        // aynı öğe iki farklı bayt dizisiyle iki ayrı öğe gibi görünmesin.
        val ayrisik = "Gu\u0308lu\u0308mse"
        val songs = reader.parseText("No,A,B,Ad\n1,x,y,$ayrisik")

        assertEquals("NFC birleşik biçim beklenir", "Gülümse", songs[0].name)
        assertEquals("Birleşik hâlde 7 karakter olmalı", 7, songs[0].name.length)
    }

    @Test
    fun nfd_baslikAdiDaNormalizeEdilir_jsonAnahtariBirlesikOlur() {
        val ayrisikBaslik = "Su\u0308re"
        val songs = reader.parseText("No,A,B,Ad,$ayrisikBaslik\n1,x,y,Test,4:12")

        val json = JSONObject(songs[0].csvData ?: "{}")
        assertTrue("Anahtar NFC biçimde olmalı", json.has("Süre"))
        assertEquals("4:12", json.getString("Süre"))
    }

    // ==========================================================
    // ⑥ ÇOK UZUN HÜCRE (şarkı sözü boyutu)
    // ==========================================================

    @Test
    fun cokUzunHucre_sarkiSozuBoyutu_kayipsizOkunur() {
        // Tam şarkı sözü ~40 bin karakter; içinde virgül, satır sonu ve tırnak var.
        val kita = "Bir sabah uyandım, kalbimde \"sen\" vardı;\nrüzgâr esti, gitti.\n"
        val soz = buildString { repeat(600) { append(kita) } }
        val kacisli = soz.replace("\"", "\"\"")
        val songs = reader.parseText("No,A,B,Ad,Söz\n1,x,y,Test,\"$kacisli\"")

        assertEquals(1, songs.size)
        val json = JSONObject(songs[0].csvData ?: "{}")
        val okunan = json.getString("Söz")
        assertTrue("Hücre 30 binden uzun olmalı, bulunan: ${okunan.length}", okunan.length > 30_000)
        assertEquals("Uzun hücre kayıpsız okunmalı", soz.trim(), okunan)
        assertEquals("Satır sonu sayısı korunmalı", 1199, okunan.count { it == '\n' })
    }

    @Test
    fun cokUzunHucre_sonrakiSatirlariBozmaz() {
        val uzun = "x".repeat(50_000)
        val songs = reader.parseText("No,A,B,Ad\n1,x,y,\"$uzun\"\n2,x,y,Kısa")

        assertEquals(2, songs.size)
        assertEquals(50_000, songs[0].name.length)
        assertEquals("Uzun hücreden sonraki satır sağlam kalmalı", "Kısa", songs[1].name)
    }

    @Test
    fun binSatirlikDosya_butunlukKorunur() {
        val metin = buildString {
            append("No,Sanatçı,Albüm,Ad\n")
            for (i in 1..1000) append("$i,Sanatçı $i,\"Albüm, $i\",Şarkı $i\n")
        }
        val songs = reader.parseText(metin)

        assertEquals(1000, songs.size)
        assertEquals("Şarkı 1", songs.first().name)
        assertEquals("Şarkı 1000", songs.last().name)
        assertEquals("Albüm, 500", songs[499].album)
        assertEquals(1000, songs.last().trackNumber)
    }
}
