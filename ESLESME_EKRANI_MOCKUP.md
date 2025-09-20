# EŞLEŞME EKRANI TASARIM MOCKUP

## 📱 MEVCUT TASARIM ANALİZİ

### Mevcut Yapı:
```
1. Başlık: "X. Tur Eşleştirmeleri"
2. Sayaç: "X Eşleştirme Oluşturuldu"
3. LazyColumn: Scrollable eşleştirme kartları
   - Card elevation: 4.dp
   - Padding: 12.dp
   - Clickable: selectMatch(match) fonksiyonu
   - İçerik:
     * Eşleşme numarası (alternating numbering)
     * Row layout ile iki takım yan yana
     * "vs" metni ortada
4. Alt buton: "Puanlama Ekranına Geç"
```

### Mevcut Takım Kartı Yapısı:
```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    // Takım 1
    Column(Modifier.weight(1f)) {
        Text(song1?.title ?: "Bilinmeyen")
        Text(song1?.artist ?: "", fontSize = 12.sp)
    }

    // VS
    Text("vs", fontWeight = FontWeight.Bold)

    // Takım 2
    Column(Modifier.weight(1f)) {
        Text(song2?.title ?: "Bilinmeyen")
        Text(song2?.artist ?: "", fontSize = 12.sp)
    }
}
```

## 🎨 YENİ TASARIM ÖNERİLERİ

### VERSIYON A: BÜYÜK KARTLAR
```
┌─────────────────────────────────────┐
│ 📱 EŞLEŞTIRME A VERSİYONU           │
├─────────────────────────────────────┤
│ "1. Tur Eşleştirmeleri" (Büyük)     │
│ "8 Eşleştirme" (Küçük, sağda)       │
├─────────────────────────────────────┤
│ ┌─ BÜYÜK CARD (3x daha büyük) ───┐   │
│ │                                │   │
│ │ ┌──────────────┐ "1.EŞLEŞME" ┐ │   │
│ │ │   TAKIM 1    │              │ │   │
│ │ │ ┌──────────┐ │    "VS"      │ │   │
│ │ │ │   ŞKL    │ │              │ │   │
│ │ │ │ Resim    │ │ ┌──────────┐ │ │   │
│ │ │ │ Alanı    │ │ │ TAKIM 2  │ │ │   │
│ │ │ └──────────┘ │ │ ┌──────┐ │ │ │   │
│ │ │ "Şarkı Adı"  │ │ │ ŞKL  │ │ │ │   │
│ │ │ "Sanatçı"    │ │ │Resim │ │ │ │   │
│ │ │ "Puan: 2.5"  │ │ │ Alan │ │ │ │   │
│ │ └──────────────┘ │ │      │ │ │ │   │
│ │                  │ └──────┘ │ │ │   │
│ │                  │"Şarkı 2" │ │ │   │
│ │                  │"Sanatçı2"│ │ │   │
│ │                  │"Puan:1.5"│ │ │   │
│ │                  └──────────┘ │ │   │
│ └────────────────────────────────┘   │
├─────────────────────────────────────┤
│ [Büyük Tıklama Alanı - Oyla]        │
└─────────────────────────────────────┘
```

### VERSIYON B: KOMPAKT + DETAY
```
┌─────────────────────────────────────┐
│ 📱 EŞLEŞTIRME B VERSİYONU           │
├─────────────────────────────────────┤
│ ┌─ KOMPAKT CARD ─────────────────┐   │
│ │ 1.EŞLEŞME      [Detay Aç ▼] │   │
│ │ ─────────────────────────────   │   │
│ │ [T1: Şarkı] vs [T2: Şarkı]   │   │
│ │ Puan: 2.5   vs  Puan: 1.5    │   │
│ └─────────────────────────────────┘   │
│ ┌─ AÇILMIŞ DETAY CARD ───────────┐   │
│ │ 2.EŞLEŞME      [Detay Kapat ▲]│   │
│ │ ═══════════════════════════════   │   │
│ │ ┌─ TAKIM 1 ────┐   ┌─ TAKIM 2─┐ │   │
│ │ │ 📸 Resim     │VS │ 📸 Resim │ │   │
│ │ │ Şarkı Adı    │   │ Şarkı Adı│ │   │
│ │ │ Sanatçı      │   │ Sanatçı  │ │   │
│ │ │ Puan: 2.5    │   │ Puan: 1.5│ │   │
│ │ │ [🎵 Oynat]   │   │[🎵 Oynat]│ │   │
│ │ └──────────────┘   └──────────┘ │   │
│ │ [Bu Eşleşmeyi Oyla]            │   │
│ └─────────────────────────────────┘   │
└─────────────────────────────────────┘
```

## 🔧 TEKNİK ÖZELLİKLER

### Versiyon A Özellikleri:
- **3x Büyük kartlar**: 420dp yükseklik (140dp'den büyük)
- **Resim alanları**: Şarkı/takım resimleri için yer
- **Puan gösterimi**: Mevcut puan durumu
- **Tek tıklama**: Tüm kart tıklanabilir
- **VS merkez**: Büyük VS yazısı ortada

### Versiyon B Özellikleri:
- **Katlanabilir kartlar**: Expand/collapse sistem
- **Kompakt görünüm**: Çoklu eşleştirme gösterimi
- **Detay modu**: Açıldığında tam bilgi
- **Oynatma butonları**: Direkt şarkı oynatma
- **Seçmeli oylama**: Sadece açık kart oylanabilir

## 📋 MOCKUP YAPILACAKLAR

### 1. Android Studio Layout Editor Açılışı:
- File → New → XML → Layout XML File
- Name: `matching_screen_mockup_a.xml`
- Name: `matching_screen_mockup_b.xml`

### 2. Temel Layout Componentleri:
- **ScrollView** → Ana container
- **LinearLayout** → Dikey sıralama
- **CardView** → Eşleştirme kartları
- **RelativeLayout** → Takım kartları için
- **ImageView** → Resim alanları
- **TextView** → Metin alanları
- **Button** → İnteraktif butonlar

### 3. Renk Sistemi:
- **Takım 1**: Açık mavi (#E3F2FD)
- **Takım 2**: Açık yeşil (#F1F8E9)
- **VS**: Gri (#9E9E9E)
- **Card**: Beyaz (#FFFFFF)
- **Border**: Siyah (#000000)

### 4. Boyut Standartları:
- **Card Height A**: 420dp
- **Card Height B Closed**: 80dp
- **Card Height B Open**: 300dp
- **Padding**: 16dp
- **Margin**: 12dp
- **Text Sizes**: 16sp (başlık), 14sp (şarkı), 12sp (sanatçı)

## 🎯 SONRAKİ ADIMLAR

1. **XML Mockup oluştur** → A ve B versiyonları
2. **Design Preview kontrol et** → Layout Editor'de görsel
3. **Gerçek data ile test et** → Mock data ile deneme
4. **Kullanıcı feedback al** → Hangi versiyon tercih edilir
5. **Seçilen versiyonu implement et** → RankingScreen.kt'ye uygula
