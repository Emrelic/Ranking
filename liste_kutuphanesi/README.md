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

Kalın yazılı sütun (4. sütun) her listede **sıralanacak öğedir**.

## İçe Aktarma

Uygulamada: **Listeler → Yeni Liste → CSV Dosyasından Yükle → dosyayı seç**

Dosyayı seçtikten sonra uygulama başlık satırını okur; 4. sütundaki öğe adları sıralama öğeleri olarak, diğer sütunlar ise öğe özellikleri olarak yüklenir. Ardından istediğiniz sıralama yöntemini (ikili karşılaştırma, İsviçre sistemi, lig, eleme vb.) seçerek çalışmaya başlayabilirsiniz.
