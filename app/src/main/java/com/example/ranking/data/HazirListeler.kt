package com.example.ranking.data

/**
 * Uygulamaya gömülü hazır liste kütüphanesi.
 * CSV dosyaları app/src/main/assets/hazir_listeler/ altında bulunur.
 * Buradaki katalog, ana menüdeki "Hazır Listeler" ekranında kategorilere
 * göre gruplanmış olarak gösterilir; kullanıcı tek dokunuşla içe aktarır.
 */

data class HazirListe(
    val dosya: String,        // assets/hazir_listeler/ altındaki dosya adı
    val ad: String,           // Kullanıcıya gösterilen ad
    val ogeSayisi: Int,       // Öğe sayısı (bilgi amaçlı)
    val gorselli: Boolean,    // Öğelerde görsel var mı
    val aciklama: String      // Kısa açıklama (kart alt satırı)
)

data class HazirListeKategorisi(
    val ad: String,
    val liste: List<HazirListe>
)

object HazirListeler {

    val kategoriler: List<HazirListeKategorisi> = listOf(
        HazirListeKategorisi(
            "Tarih",
            listOf(
                HazirListe("01_osmanli_padisahlari.csv", "Osmanlı Padişahları", 36, false, "Tahta çıkış ve saltanat süreleriyle 36 padişah"),
                HazirListe("22_imparatorluklar.csv", "İmparatorluklar", 52, false, "Kuruluş, başkent ve sınırlarıyla imparatorluklar"),
                HazirListe("23_savaslar.csv", "Savaşlar", 55, false, "Taraflar ve sonuçlarıyla önemli savaşlar"),
                HazirListe("24_kumandanlar.csv", "Kumandanlar", 50, true, "Ünlü zaferleriyle askeri komutanlar"),
                HazirListe("25_devlet_adamlari.csv", "Devlet Adamları", 50, true, "Görev ve icraatlarıyla devlet adamları")
            )
        ),
        HazirListeKategorisi(
            "Coğrafya",
            listOf(
                HazirListe("03_ulkeler.csv", "Ülkeler", 195, true, "Bayrak, nüfus, ekonomi ve tarihleriyle ülkeler"),
                HazirListe("04_denizler.csv", "Denizler", 44, false, "Yüzölçümü ve derinlikleriyle denizler"),
                HazirListe("05_daglar.csv", "Dağlar", 55, false, "Yükseklik ve konumlarıyla dağlar"),
                HazirListe("06_sehirler.csv", "Şehirler", 90, false, "Nüfus ve özellikleriyle dünya şehirleri"),
                HazirListe("07_goller.csv", "Göller", 45, false, "Yüzölçümü ve derinlikleriyle göller"),
                HazirListe("08_nehirler.csv", "Nehirler", 55, false, "Uzunluk ve güzergâhlarıyla nehirler"),
                HazirListe("09_ovalar.csv", "Ovalar", 30, false, "Türkiye'nin önemli ovaları")
            )
        ),
        HazirListeKategorisi(
            "Bilim ve Doğa",
            listOf(
                HazirListe("10_bilim_insanlari.csv", "Bilim İnsanları", 65, false, "Alan ve katkılarıyla bilim insanları"),
                HazirListe("13_elementler.csv", "Elementler", 118, false, "Periyodik tablonun 118 elementi"),
                HazirListe("14_hayvanlar.csv", "Hayvanlar", 65, false, "Sınıf, takım ve yaşam alanlarıyla hayvanlar"),
                HazirListe("18_kuslar.csv", "Kuşlar", 60, true, "Familya, kanat açıklığı ve beslenmeleriyle kuşlar"),
                HazirListe("19_baliklar.csv", "Balıklar", 50, false, "Su tipi, boy ve beslenmeleriyle balıklar"),
                HazirListe("20_bitkiler.csv", "Bitkiler", 55, true, "Familya, tip ve kullanımlarıyla bitkiler"),
                HazirListe("21_mikroplar_hastaliklar.csv", "Mikroplar ve Hastalıklar", 45, false, "Etken, bulaşma ve belirtileriyle hastalıklar"),
                HazirListe("15_yiyecekler.csv", "Yiyecekler", 60, false, "Kalori, protein ve lif değerleriyle yiyecekler")
            )
        ),
        HazirListeKategorisi(
            "Sanat ve Kültür",
            listOf(
                HazirListe("11_ressamlar.csv", "Ressamlar", 56, false, "Akım ve eserleriyle ressamlar"),
                HazirListe("12_filozoflar.csv", "Filozoflar", 56, false, "Akım ve eserleriyle filozoflar"),
                HazirListe("26_filmler.csv", "Filmler", 55, true, "Sinema tarihinin önemli filmleri"),
                HazirListe("27_resimler.csv", "Tablolar", 50, true, "Dünyaca ünlü tablolar"),
                HazirListe("28_fotograflar.csv", "Fotoğraflar", 40, true, "Tarihe geçen fotoğraflar"),
                HazirListe("29_heykeller.csv", "Heykeller", 27, true, "Ünlü heykeller ve anıtlar"),
                HazirListe("30_yapilar.csv", "Yapılar", 70, true, "Mimari açıdan önemli yapılar"),
                HazirListe("31_muzik_aletleri.csv", "Müzik Aletleri", 35, true, "Türk ve dünya müzik aletleri"),
                HazirListe("02_sebnem_ferah_sarkilari.csv", "Şebnem Ferah Şarkıları", 85, false, "Albümleriyle Şebnem Ferah şarkıları"),
                HazirListe("32_sebnem_ferah_sozleriyle.csv", "Şebnem Ferah — Sözleriyle", 80, false, "Şarkı sözleri okunabilir 80 şarkı")
            )
        ),
        HazirListeKategorisi(
            "Teknoloji ve Spor",
            listOf(
                HazirListe("17_otomobil_markalari.csv", "Otomobil Markaları", 50, false, "Kuruluş ve grup bilgileriyle markalar"),
                HazirListe("16_avrupa_futbol_kulupleri.csv", "Avrupa Futbol Kulüpleri", 55, true, "Kuruluş ve kupalarıyla kulüpler")
            )
        )
    )

    val toplamListe: Int get() = kategoriler.sumOf { it.liste.size }
    val assetKlasoru = "hazir_listeler"
}
