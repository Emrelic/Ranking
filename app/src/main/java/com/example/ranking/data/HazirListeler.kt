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
                HazirListe("22_imparatorluklar.csv", "İmparatorluklar", 30, false, "Kuruluş, başkent ve sınırlarıyla imparatorluklar"),
                HazirListe("23_savaslar.csv", "Savaşlar", 30, false, "Taraflar ve sonuçlarıyla önemli savaşlar"),
                HazirListe("24_kumandanlar.csv", "Kumandanlar", 30, true, "Ünlü zaferleriyle askeri komutanlar"),
                HazirListe("25_devlet_adamlari.csv", "Devlet Adamları", 30, true, "Görev ve icraatlarıyla devlet adamları")
            )
        ),
        HazirListeKategorisi(
            "Coğrafya",
            listOf(
                HazirListe("03_ulkeler.csv", "Ülkeler", 40, true, "Bayrak, nüfus, ekonomi ve tarihleriyle ülkeler"),
                HazirListe("04_denizler.csv", "Denizler", 20, false, "Yüzölçümü ve derinlikleriyle denizler"),
                HazirListe("05_daglar.csv", "Dağlar", 25, false, "Yükseklik ve konumlarıyla dağlar"),
                HazirListe("06_sehirler.csv", "Şehirler", 30, false, "Nüfus ve özellikleriyle dünya şehirleri"),
                HazirListe("07_goller.csv", "Göller", 20, false, "Yüzölçümü ve derinlikleriyle göller"),
                HazirListe("08_nehirler.csv", "Nehirler", 20, false, "Uzunluk ve güzergâhlarıyla nehirler"),
                HazirListe("09_ovalar.csv", "Ovalar", 15, false, "Türkiye'nin önemli ovaları")
            )
        ),
        HazirListeKategorisi(
            "Bilim ve Doğa",
            listOf(
                HazirListe("10_bilim_insanlari.csv", "Bilim İnsanları", 30, false, "Alan ve katkılarıyla bilim insanları"),
                HazirListe("13_elementler.csv", "Elementler", 36, false, "Periyodik tablonun ilk 36 elementi"),
                HazirListe("14_hayvanlar.csv", "Hayvanlar", 30, false, "Sınıf, takım ve yaşam alanlarıyla hayvanlar"),
                HazirListe("18_kuslar.csv", "Kuşlar", 30, true, "Familya, kanat açıklığı ve beslenmeleriyle kuşlar"),
                HazirListe("19_baliklar.csv", "Balıklar", 30, false, "Su tipi, boy ve beslenmeleriyle balıklar"),
                HazirListe("20_bitkiler.csv", "Bitkiler", 30, true, "Familya, tip ve kullanımlarıyla bitkiler"),
                HazirListe("21_mikroplar_hastaliklar.csv", "Mikroplar ve Hastalıklar", 30, false, "Etken, bulaşma ve belirtileriyle hastalıklar"),
                HazirListe("15_yiyecekler.csv", "Yiyecekler", 30, false, "Kalori, protein ve lif değerleriyle yiyecekler")
            )
        ),
        HazirListeKategorisi(
            "Sanat ve Kültür",
            listOf(
                HazirListe("11_ressamlar.csv", "Ressamlar", 25, false, "Akım ve eserleriyle ressamlar"),
                HazirListe("12_filozoflar.csv", "Filozoflar", 30, false, "Akım ve eserleriyle filozoflar"),
                HazirListe("26_filmler.csv", "Filmler", 30, true, "Sinema tarihinin önemli filmleri"),
                HazirListe("27_resimler.csv", "Tablolar", 30, true, "Dünyaca ünlü tablolar"),
                HazirListe("28_fotograflar.csv", "Fotoğraflar", 25, true, "Tarihe geçen fotoğraflar"),
                HazirListe("29_heykeller.csv", "Heykeller", 25, true, "Ünlü heykeller ve anıtlar"),
                HazirListe("30_yapilar.csv", "Yapılar", 40, true, "Mimari açıdan önemli yapılar"),
                HazirListe("31_muzik_aletleri.csv", "Müzik Aletleri", 35, true, "Türk ve dünya müzik aletleri"),
                HazirListe("02_sebnem_ferah_sarkilari.csv", "Şebnem Ferah Şarkıları", 42, false, "Albümleriyle Şebnem Ferah şarkıları")
            )
        ),
        HazirListeKategorisi(
            "Teknoloji ve Spor",
            listOf(
                HazirListe("17_otomobil_markalari.csv", "Otomobil Markaları", 25, false, "Kuruluş ve grup bilgileriyle markalar"),
                HazirListe("16_avrupa_futbol_kulupleri.csv", "Avrupa Futbol Kulüpleri", 30, true, "Kuruluş ve kupalarıyla kulüpler")
            )
        )
    )

    val toplamListe: Int get() = kategoriler.sumOf { it.liste.size }
    val assetKlasoru = "hazir_listeler"
}
