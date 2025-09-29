# EKRAN GÖRÜNTÜLERİ DEPOSU

## 📱 EKRAN GÖRÜNTÜLERİ VE REQUİREMENTLAR

### 🎯 2025-09-16 - Eşleştirme Ekranı Tasarımı

#### [Image #1] - Oylama Ekranı Takım Kartları (Yan Yana Format)
**Tarih**: 2025-09-16
**Açıklama**: Afganistan vs Arnavutluk oylama ekranı - yan yana kart formatı
**Gereksinimler**:
- **Başlık satırı**: Koyu mavi arkaplan (AFGANİSTAN, ARNAVUTLUK)
- **Veri satırları**: İki sütunlu format (Label | Değer)
- **Renk teması**: Açık mavi tonları, alternatif satır renkleri
- **İçerik**: Kıta, Nüfus (Milyon), Yüzölçümü (km²), GSYİH (Milyar USD), Kişi Başına GSYİH (USD)
- **Layout**: Yan yana iki kart, temiz tablo formatı
- **Çerçeve**: İnce çerçeve ile kart sınırları
- **Hedef**: Oylama ekranında TeamCardContent component formatı

#### [Image #2] - Eşleştirme Ekranı Takım Kartları (Alt Alta Format)
**Tarih**: 2025-09-16
**Açıklama**: Afganistan vs Arnavutluk eşleştirme ekranı - alt alta kart formatı + VS ortada
**Gereksinimler**:
- **Başlık satırı**: Koyu mavi arkaplan (AFGANİSTAN, ARNAVUTLUK)
- **Veri satırları**: İki sütunlu format (Label | Değer)
- **Renk teması**: Açık mavi tonları, alternatif satır renkleri
- **Layout**: İlk kart üstte, VS ortada, ikinci kart altta
- **VS Element**: Ortada "vs" yazısı
- **Çerçeve**: İnce çerçeve ile kart sınırları
- **Hedef**: Eşleştirme listesi ekranında MatchCard component formatı

---

## 📋 KULLANIM
- **"*ege" komutu**: Yeni ekran görüntüsü ekle
- **Format**: [Image #X] açıklama + *ege
- **Otomatik işleme**: Görüntü analiz edilir ve gereksinimler listelenir

## 📖 ÖRNEK KULLANIM
```
Kullanıcı: "Bu tasarımı uygula *ege"
Claude: EKRAN_GORUNTULERI.md'ye kaydedildi ve analiz edildi!
```

---

## 📂 GÖRÜNTü KATEGORILER

### 🎨 UI/UX TASARIM
(UI tasarım referansları burada)

### 📊 TABLO FORMATLARI
(Tablo görüntüleri burada)

### 🏆 TURNUVA EKRANLARı
(Turnuva ekranı örnekleri burada)

### 🎯 KRİTER SİSTEMİ EKRANLARı

#### [Image #3] - Kriter Değerlendirme Tam Ekran Dialogu (2025-09-17) ✅ TAMAMLANDI
**Tarih**: 2025-09-17
**Açıklama**: Tam ekran kriter değerlendirme dialogu - Final implementasyon
**Gereksinimler** ✅:
- **Tam ekran layout**: %100 ekran boyutu (fillMaxSize + RoundedCornerShape(0.dp)) ✅
- **Header**: "Kriter Değerlendirmesi" başlık + "Kapat" butonu sağ üstte ✅
- **Takım isimleri**: Mavi/Yeşil renk kodlu takım kartları (primaryContainer/secondaryContainer) ✅
- **Kriter listesi**: Gerçek database'ten alınan kriter isimleri (Tournament→CriterionList) ✅
- **Aktif/pasif switch**: Her kriter için on/off durumu ✅
- **Puanlama sistemi**: Turnuva ayarlarına göre dropdown veya slider ✅
- **Alt butonlar**: Dikdörtgen İptal/Kaydet (40dp height, 4dp radius, 12sp font) ✅
- **Renk farkı**: Takım 1 mavi (#1976D2), Takım 2 yeşil (#388E3C) ✅
- **Border effects**: Aktif kriterler 2dp border, pasif 1dp ✅

#### [Image #4] - Kriter Puanlama Tipleri (2025-09-17) ✅ TAMAMLANDI
**Tarih**: 2025-09-17
**Açıklama**: İki farklı puanlama tipi implementasyonu
**Gereksinimler** ✅:
- **Ayrı Ayrı Puanlama**: ScoreDropdown (1-scoreScale arası seçim) ✅
- **Kıyaslamalı Puanlama**: ComparativeScoring (0-scoreScale Slider) ✅
- **Dinamik scoreScale**: Tournament settings'ten alınan değer (1-100 arası) ✅
- **Real-time feedback**: Anlık puan gösterimi ✅
- **Settings entegrasyonu**: criteriaSettings JSON parse ✅

### ⚙️ AYAR EKRANLARı
(Ayar sayfası tasarımları burada)

### 🎮 OYLAMA EKRANI TASARIMLAR (ARŞIV)

#### [Image #5] - Oylama Ekranı Layout ✅ TAMAMLANDI (Detaylar ARCHIVED_NOTES.md'de)

#### [Image #6] - Oylama Ekranı Final Tasarım Hedefi (2025-09-23)
**Tarih**: 2025-09-23
**Açıklama**: Afganistan vs Arnavutluk oylama ekranı - 5 bölümlü sabit layout sistemi
**Gereksinimler**:
- **Tur ilerleme çubuğu**: En tepede minimal padding (sıkıştırılmış)
- **Takım 1 başlığı**: AFGANİSTAN - sabit, mavi arkaplan
- **Takım 1 scroll penceresi**: Tablo verisi içinde scroll edilebilir (sağa/sola/yukarı/aşağı)
- **Orta buton çubuğu**: BERABERLIK (büyük) | VS (ortada) | SKOR GİR + KRİTER (sağda 2 buton)
- **Takım 2 başlığı**: ARNAVUTLUK - sabit, mavi arkaplan
- **Takım 2 scroll penceresi**: Tablo verisi içinde scroll edilebilir - ekran dibine kadar
- **VS popup sistemi**: VS butonuna tıklayınca üst butonlar (Duraklar/Sıfırla/Fikstür/Puan) popup ile açılır
- **Layout**: 6 katmanlı fixed layout - VS buton çubuğu tam ekran ortasında
- **Renk sistemi**: Takım başlıkları mavi, buton çubuğu yeşil/sarı/yeşil

**Implementation Status**: Yeni talep - implementasyon gerekli
**Technical Location**: RankingScreen.kt MatchBasedContent function - tam yeniden yapılandırma