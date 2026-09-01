# Liste Kütüphanesi

Bu klasör, Ranking uygulaması için hazırlanmış **eğitici CSV liste kütüphanesidir**. Her dosya, uygulamaya doğrudan içe aktarılabilecek şekilde biçimlendirilmiş, sıralama/kıyaslama/turnuva çalışmalarında kullanılabilecek gerçek verilere dayalı listeler içerir.

## CSV Biçim Kuralları

- İlk satır **başlık satırıdır**.
- **1. sütun**: No (tam sayı)
- **2. ve 3. sütunlar**: En önemli iki özellik (öğe kartında alt satır olarak görünür)
- **4. sütun**: **Sıralanacak öğenin adı** (padişah, şarkı, ülke vb.)
- **5. sütun ve sonrası**: Ek özellikler (tablo görünümünde gösterilir)
- Kodlama: UTF-8, ayraç: virgül, satır sonu: LF
- Değerler yaygın kabul gören yaklaşık değerlerdir; eğitim amaçlıdır.

## Dosyalar

| # | Dosya | Satır | Sütunlar |
|---|-------|-------|----------|
| 1 | 01_osmanli_padisahlari.csv | 36 | No, Tahta Çıkış, Saltanat Süresi, **Padişah**, Tahttan İniş, Yaşadığı Yıllar, Lakabı/Ünvanı, Dönemin Önemli Olayı, Ölüm Şekli |
| 2 | 02_sebnem_ferah_sarkilari.csv | 85 | No, Albüm, Yıl, **Şarkı**, Tür, Konu, Duygu Tonu, Süre, Söz-Müzik, Sanatçı |
| 3 | 03_ulkeler.csv | 195 | No, Kıta, Nüfus (milyon), **Ülke**, Yüzölçümü (bin km²), GSYH (milyar $), Kişi Başı Gelir ($), Bağımsızlık Yılı, Bayrak Kabul Yılı, Başkent, Para Birimi, Resmi Dil, Görsel 🖼 |
| 4 | 04_denizler.csv | 44 | No, Bağlı Okyanus, Yüzölçümü (bin km²), **Deniz**, Ortalama Derinlik (m), Kıyı Bölgesi, En Derin Nokta (m), Kıyıdaş Ülke Sayısı, Tuzluluk (binde) |
| 5 | 05_daglar.csv | 55 | No, Ülke, Yükseklik (m), **Dağ**, Sıradağ/Bölge, Kıta, Dağ Tipi, İlk Tırmanış Yılı, Kalıcı Kar/Buzul |
| 6 | 06_sehirler.csv | 90 | No, Ülke, Nüfus (milyon), **Şehir**, Kıta, Bilinen Özelliği, Ülkenin Başkenti mi, Öne Çıkan Sektör |
| 7 | 07_goller.csv | 45 | No, Ülke/Bölge, Yüzölçümü (km²), **Göl**, Tür, Maks Derinlik (m), Yükseklik (m), Beslenme Kaynağı |
| 8 | 08_nehirler.csv | 55 | No, Kıta, Uzunluk (km), **Nehir**, Döküldüğü Yer, Geçtiği Ülkeler, Kaynağı, Havza Alanı (bin km²) |
| 9 | 09_ovalar.csv | 30 | No, Bölge, İl, **Ova**, Sulayan Akarsu, Başlıca Ürünler, İklim, Tarım Tipi |
| 10 | 10_bilim_insanlari.csv | 65 | No, Alan, Yaşadığı Yıllar, **Bilim İnsanı**, Ülke, Önemli Katkısı, Önemli Ödül, Çalıştığı Kurum |
| 11 | 11_ressamlar.csv | 56 | No, Akım, Yaşadığı Yıllar, **Ressam**, Ülke, Ünlü Eseri, Doğum Yeri, Tarz Özelliği |
| 12 | 12_filozoflar.csv | 56 | No, Akım, Yaşadığı Yıllar, **Filozof**, Ülke/Şehir, Önemli Eseri, Dönem, Temel Kavram |
| 13 | 13_elementler.csv | 118 | No (atom numarası), Sembol, Grup, **Element**, Atom Ağırlığı, Oda Sıcaklığındaki Hali, Keşif Yılı, Periyot, Kullanım Alanı, Ad Kökeni |
| 14 | 14_hayvanlar.csv | 65 | No, Sınıf, Takım, **Hayvan**, Anavatan/Yaşam Alanı, Beslenme, Ortalama Ömür (yıl), Bilimsel Adı, Ortalama Ağırlık, Koruma Durumu |
| 15 | 15_yiyecekler.csv | 60 | No, Kalori (100g), Protein (g), **Yiyecek**, Lif (g), Kategori, Karbonhidrat (g), Yağ (g) |
| 16 | 16_avrupa_futbol_kulupleri.csv | 55 | No, Ülke, Kuruluş Yılı, **Kulüp**, Şehir, Şampiyonlar Ligi Kupası, Lig, Stadyum, Lakap, Görsel 🖼 |
| 17 | 17_otomobil_markalari.csv | 50 | No, Ülke, Kuruluş Yılı, **Marka**, Ana Şirket/Grup, Segment, Merkez Şehir, Ünlü Modeli |
| 18 | 18_kuslar.csv | 60 | No, Takım, Yaşam Alanı, **Kuş**, Familya, Kanat Açıklığı (cm), Ağırlık (g), Beslenme, Göçmen mi, Anavatan, Bilimsel Adı, Yumurta Sayısı, Görsel 🖼 |
| 19 | 19_baliklar.csv | 50 | No, Sınıf, Su Tipi, **Balık**, Familya, Ortalama Boy (cm), Ağırlık (kg), Beslenme, Yaşam Bölgesi, Bilimsel Adı, Ortalama Ömür (yıl), Ticari Değeri |
| 20 | 20_bitkiler.csv | 55 | No, Familya, Tip, **Bitki**, Anavatan, Ortalama Boy, Çiçeklenme Mevsimi, Başlıca Kullanım, Ömür, Bilimsel Adı, Yetişme İklimi, Görsel 🖼 |
| 21 | 21_mikroplar_hastaliklar.csv | 45 | No, Mikrop Tipi, Bulaşma Yolu, **Hastalık**, Etken Mikroorganizma, Etkilediği Sistem, Başlıca Belirtiler, Korunma/Tedavi, Keşif/İlk Tanım Yılı, Kuluçka Süresi, Aşı Var mı |
| 22 | 22_imparatorluklar.csv | 52 | No, Kuruluş Yılı, Süre (yıl), **İmparatorluk**, Yıkılış Yılı, Başkent, En Geniş Sınır (milyon km²), Kurucu, Bölge, Hanedan/Yönetim, Din, En Ünlü Hükümdarı |
| 23 | 23_savaslar.csv | 55 | No, Yıl, Taraflar, **Savaş**, Kazanan, Yer, Süre, Sonuç/Önemi, Savaş Türü, Komutanlar |
| 24 | 24_kumandanlar.csv | 50 | No, Yaşadığı Yıllar, Ülke/Devlet, **Kumandan**, Ünlü Zaferi, Askeri Alanı, Önemli Özelliği, Rütbe/Ünvanı, Katıldığı Önemli Savaşlar, Görsel 🖼 |
| 25 | 25_devlet_adamlari.csv | 50 | No, Yaşadığı Yıllar, Ülke, **Devlet Adamı**, Görevi, Dönem, Başlıca İcraatı, Bağlı Olduğu Yönetim/Partisi, En Bilinen İlkesi/Sözü, Görsel 🖼 |
| 26 | 26_filmler.csv | 55 | No, Yıl, Yönetmen, **Film**, Ülke, Tür, Süre (dk), Önemi, IMDb, Başrol, Görsel 🖼 |
| 27 | 27_resimler.csv | 50 | No, Ressam, Yıl, **Tablo**, Akım, Bulunduğu Müze, Teknik, Konusu, İlginç Bilgi, Görsel 🖼 |
| 28 | 28_fotograflar.csv | 40 | No, Fotoğrafçı, Yıl, **Fotoğraf**, Konu/Olay, Önemi, Çekim Yeri, Renk, Görsel 🖼 |
| 29 | 29_heykeller.csv | 27 | No, Heykeltıraş, Yıl, **Heykel**, Ülke/Şehir, Malzeme, Yükseklik (m), Dönem, İlginç Bilgi, Görsel 🖼 |
| 30 | 30_yapilar.csv | 70 | No, Yapım Yılı, Şehir, **Yapı**, Ülke, Mimar, Mimari Üslup, Kullanım Amacı, Yükseklik/Ölçü, UNESCO Mirası, İlginç Bilgi, Görsel 🖼 |
| 31 | 31_muzik_aletleri.csv | 35 | No, Tür, Çalınış Şekli, **Müzik Aleti**, Anavatan/Kültür, Tel/Perde Sayısı, Malzeme, Kullanıldığı Müzik, Ünlü İcracı, Ses Karakteri, Görsel 🖼 |
| 32 | 32_sebnem_ferah_sozleriyle.csv | 80 | No, Sanatçı, Albüm, **Şarkı Adı**, Şarkı Sözleri |
| 33 | 33_sayilar_1_100_test.csv | 100 | No, Grup, Beklenen Sıra, **Sayı**, Basamak — *sıralama sınavı için* |
| 34 | 34_sayilar_1_200_test.csv | 200 | No, Grup, Beklenen Sıra, **Sayı**, Basamak — *İsviçre varyantı kıyası* |
| 35 | 35_dunya_kupasi_turnuvalari.csv | 23 | No, Şampiyon, Yıl, **Turnuva**, Ev Sahibi, Finalist, Final Skoru, Takım Sayısı, Gol Kralı, Kıta |
| 36 | 36_nobel_edebiyat_odulleri.csv | 51 | No, Ülke, Yıl, **Yazar**, Yazdığı Dil, Öne Çıkan Eseri, Başlıca Türü |
| 37 | 37_gunes_sistemi.csv | 35 | No, Tür, Çap (km), **Gök Cismi**, Yörüngesinde Olduğu, Keşif Yılı, Bilinen Uydu Sayısı, Güneş'e Ortalama Uzaklık (milyon km), Kâşifi |
| 38 | 38_klasik_besteciler.csv | 52 | No, Dönem, Yaşadığı Yıllar, **Besteci**, Ülke, Ünlü Eseri, Başlıca Türü, Doğum Yeri |
| 39 | 39_yaz_olimpiyatlari.csv | 30 | No, Ev Sahibi Ülke, Yıl, **Olimpiyat**, Şehir, Katılan Ülke Sayısı, Madalya Sıralaması Lideri, Kıta, Öne Çıkan Özelliği |
| 40 | 40_programlama_dilleri.csv | 50 | No, Paradigma, Çıkış Yılı, **Dil**, Tasarımcı, Tipleme, Başlıca Kullanım Alanı, Çalışma Biçimi |

Toplam **40 liste**, **2475 öğe** — bunların **742 tanesi görselli** (🖼 işaretli listeler).

## Senkron Kuralı ve Bekçi Testi

Bir listenin **dört** ayrı kaydı vardır ve dördü **tek commit'te** değişir:

1. `liste_kutuphanesi/NN_ad.csv` — kaynak
2. `app/src/main/assets/hazir_listeler/NN_ad.csv` — uygulamaya gömülen birebir kopya
3. `app/src/main/java/com/example/ranking/data/HazirListeler.kt` — katalog kaydı (ad + öğe sayısı)
4. Bu README'nin tablosu ve özet satırı

Bu dördü bir kez ayrıştı ve ortada üç farklı öğe sayısı dolaştı. Artık
`app/src/test/java/com/example/ranking/HazirListelerSenkronTest.kt` dördünü
birbirinden bağımsız okuyup karşılaştırıyor; ayrışma derlemede değil **testte**
patlar:

```
./gradlew :app:testDebugUnitTest --tests "*HazirListelerSenkronTest*"
```

Test ayrıca her CSV'nin parser'dan geçtiğini, boş öğe adı olmadığını, en az
dört sütunlu olduğunu ve beklenmeyen mükerrer öğe adı bulunmadığını denetler.

## Görsel Desteği

🖼 işaretli listelerde son sütun `Görsel` adresidir:
- **Ülkeler ve futbol kulüpleri**: ülke bayrağı (flagcdn.com)
- **Diğerleri**: Wikimedia fotoğrafı (mümkün olduğunca 330px küçük boy/thumbnail adresi kullanılır)

Uygulama bu sütunu otomatik tanır ve öğe kartında resmi gösterir; görsel yüklemek için internet bağlantısı gerekir. Adres yoksa veya `-` ise kart eskisi gibi sadece metinle görünür. `Görsel` sütunu özellik satırı olarak listelenmez.

Not: Veri sütunlarında da `-` görülebilir; "bilinmiyor / uygulanamaz" anlamındadır (örn. bazı dağların İlk Tırmanış Yılı, bazı müzik aletlerinin Tel/Perde Sayısı).

Kalın yazılı sütun (4. sütun) her listede **sıralanacak öğedir**.

## İçe Aktarma

Uygulamada: **Listeler → Yeni Liste → CSV Dosyasından Yükle → dosyayı seç**

Dosyayı seçtikten sonra uygulama başlık satırını okur; 4. sütundaki öğe adları sıralama öğeleri olarak, diğer sütunlar ise öğe özellikleri olarak yüklenir. Ardından istediğiniz sıralama yöntemini (ikili karşılaştırma, İsviçre sistemi, lig, eleme vb.) seçerek çalışmaya başlayabilirsiniz.
