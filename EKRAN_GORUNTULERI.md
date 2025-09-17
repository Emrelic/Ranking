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

### 🎮 OYLAMA EKRANI YENİ TASARIM (2025-09-17) 🔄 DEVAM EDİYOR

#### [Image #5] - Yeni Oylama Ekranı Layout Hedefi (Implementation Ongoing)
**Tarih**: 2025-09-17
**Açıklama**: Kapsamlı oylama ekranı yeniden tasarımı - TeamVotingPanel implementasyonu
**Gereksinimler**:
- **Header compression**: Fikstür/skor/geri butonları üste sıkıştırılmış layout ⚠️
- **Method label removal**: Usul ibaresi tamamen kaldırılmış ⚠️
- **Progress repositioning**: "Hangisi daha iyi" yazısı progress bar altında ve küçük font ⚠️
- **Dual panel system**: İki ayrı kaydırılabilir team panel'i yan yana ⚠️
- **Team name integration**: Dış takım isimleri ve VS yazısı kaldırılmış ⚠️
- **Centered tie button**: Beraberlik butonu iki panel arasında ortalanmış ⚠️
- **Score badges positioning**: Skor rozetleri her panelin sağ alt köşesinde ⚠️
- **Fixed layout**: Sabit ekran layout'u, altta kriter butonu konumlandırması ⚠️
- **Card styling**: Team-specific colors (Blue: #E3F2FD/#1976D2, Green: #F1F8E9/#388E3C) ⚠️
- **Scrollable content**: Her panel kendi içinde kaydırılabilir LazyColumn ⚠️

**Implementation Status**:
- ✅ TeamVotingPanel composable function oluşturuldu
- ✅ Row-based layout MatchBasedContent'e entegre edildi
- ❌ Build failure: Syntax error line 2357 ("Expecting '}'")
- ⚠️ APK build bekleniyor

**Technical Location**: `RankingScreen.kt` lines 2307-2406 (TeamVotingPanel), lines 546-702 (MatchBasedContent update)