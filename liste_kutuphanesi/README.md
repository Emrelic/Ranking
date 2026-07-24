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
| 1 | 01_osmanli_padisahlari.csv | 36 | No, Tahta Çıkış, Saltanat Süresi, **Padişah**, Tahttan İniş, Yaşadığı Yıllar |
| 2 | 02_sebnem_ferah_sarkilari.csv | 42 | No, Albüm, Yıl, **Şarkı** |
| 3 | 03_ulkeler.csv | 40 | No, Kıta, Nüfus (milyon), **Ülke**, Yüzölçümü (bin km²), GSYH (milyar $), Kişi Başı Gelir ($) |
| 4 | 04_denizler.csv | 20 | No, Bağlı Okyanus, Yüzölçümü (bin km²), **Deniz**, Ortalama Derinlik (m), Kıyı Bölgesi |
| 5 | 05_daglar.csv | 25 | No, Ülke, Yükseklik (m), **Dağ**, Sıradağ/Bölge, Kıta |
| 6 | 06_sehirler.csv | 30 | No, Ülke, Nüfus (milyon), **Şehir**, Kıta, Bilinen Özelliği |
| 7 | 07_goller.csv | 20 | No, Ülke/Bölge, Yüzölçümü (km²), **Göl**, Tür, Maks Derinlik (m) |
| 8 | 08_nehirler.csv | 20 | No, Kıta, Uzunluk (km), **Nehir**, Döküldüğü Yer, Geçtiği Ülkeler |
| 9 | 09_ovalar.csv | 15 | No, Bölge, İl, **Ova**, Sulayan Akarsu, Başlıca Ürünler |
| 10 | 10_bilim_insanlari.csv | 30 | No, Alan, Yaşadığı Yıllar, **Bilim İnsanı**, Ülke, Önemli Katkısı |
| 11 | 11_ressamlar.csv | 25 | No, Akım, Yaşadığı Yıllar, **Ressam**, Ülke, Ünlü Eseri |
| 12 | 12_filozoflar.csv | 30 | No, Akım, Yaşadığı Yıllar, **Filozof**, Ülke/Şehir, Önemli Eseri |
| 13 | 13_elementler.csv | 36 | No (atom numarası), Sembol, Grup, **Element**, Atom Ağırlığı, Oda Sıcaklığındaki Hali, Keşif Yılı |
| 14 | 14_hayvanlar.csv | 30 | No, Sınıf, Takım, **Hayvan**, Anavatan/Yaşam Alanı, Beslenme, Ortalama Ömür (yıl) |
| 15 | 15_yiyecekler.csv | 30 | No, Kalori (100g), Protein (g), **Yiyecek**, Lif (g), Kategori |
| 16 | 16_avrupa_futbol_kulupleri.csv | 30 | No, Ülke, Kuruluş Yılı, **Kulüp**, Şehir, Şampiyonlar Ligi Kupası |
| 17 | 17_otomobil_markalari.csv | 25 | No, Ülke, Kuruluş Yılı, **Marka**, Ana Şirket/Grup, Segment |
| 18 | 18_kuslar.csv | 30 | No, Takım, Yaşam Alanı, **Kuş**, Familya, Kanat Açıklığı (cm), Ağırlık (g), Beslenme, Göçmen mi, Anavatan |
| 19 | 19_baliklar.csv | 30 | No, Sınıf, Su Tipi, **Balık**, Familya, Ortalama Boy (cm), Ağırlık (kg), Beslenme, Yaşam Bölgesi |
| 20 | 20_bitkiler.csv | 30 | No, Familya, Tip, **Bitki**, Anavatan, Ortalama Boy, Çiçeklenme Mevsimi, Başlıca Kullanım, Ömür |
| 21 | 21_mikroplar_hastaliklar.csv | 30 | No, Mikrop Tipi, Bulaşma Yolu, **Hastalık**, Etken Mikroorganizma, Etkilediği Sistem, Başlıca Belirtiler, Korunma/Tedavi, Keşif/İlk Tanım Yılı |
| 22 | 22_imparatorluklar.csv | 30 | No, Kuruluş Yılı, Süre (yıl), **İmparatorluk**, Yıkılış Yılı, Başkent, En Geniş Sınır (milyon km²), Kurucu, Bölge |
| 23 | 23_savaslar.csv | 30 | No, Yıl, Taraflar, **Savaş**, Kazanan, Yer, Süre, Sonuç/Önemi |
| 24 | 24_kumandanlar.csv | 30 | No, Yaşadığı Yıllar, Ülke/Devlet, **Kumandan**, Ünlü Zaferi, Askeri Alanı, Önemli Özelliği |
| 25 | 25_devlet_adamlari.csv | 30 | No, Yaşadığı Yıllar, Ülke, **Devlet Adamı**, Görevi, Dönem, Başlıca İcraatı |

Toplam **25 liste**, **754 öğe**.

Kalın yazılı sütun (4. sütun) her listede **sıralanacak öğedir**.

## İçe Aktarma

Uygulamada: **Listeler → Yeni Liste → CSV Dosyasından Yükle → dosyayı seç**

Dosyayı seçtikten sonra uygulama başlık satırını okur; 4. sütundaki öğe adları sıralama öğeleri olarak, diğer sütunlar ise öğe özellikleri olarak yüklenir. Ardından istediğiniz sıralama yöntemini (ikili karşılaştırma, İsviçre sistemi, lig, eleme vb.) seçerek çalışmaya başlayabilirsiniz.
