# 🏆 Ranking Uygulaması - Tam Yazım Promptu

Bu prompt'u herhangi bir yapay zekaya (Claude, ChatGPT, vb.) vererek **Ranking Android uygulamasının** tamamını yazdırabilirsiniz.

---

## 📋 Genel Bilgiler

Kotlin ve Jetpack Compose kullanarak Android için geliştirilmiş kapsamlı bir **sıralama/turnuva yönetim uygulaması** yazman gerekiyor. 

### 🎯 Ana Özellikler:
1. **Liste Yönetimi** - CSV import/export, manuel veri girişi
2. **6 Farklı Sıralama Sistemi** - Direct Scoring, League, Swiss, Elimination, Full Elimination, EMRE_CORRECT
3. **Kriter Değerlendirme Sistemi** - Çoklu kriter bazlı puanlama
4. **Turnuva Yönetimi** - Session-based persistence, real-time tracking
5. **Modern UI** - Material Design 3, LazyColumn, drag-drop support

---

## 🏗️ Proje Yapısı

```
app/src/main/java/com/example/ranking/
├── data/                           # Veri modelleri ve DAO'lar
│   ├── entities/                   # Room Entity sınıfları
│   ├── dao/                       # Database Access Objects
│   └── RankingDatabase.kt         # Ana database sınıfı
├── ui/
│   ├── screens/                   # Compose UI ekranları
│   ├── viewmodel/                 # ViewModel sınıfları
│   └── theme/                     # UI teması
├── repository/                    # Data repository pattern
├── ranking/                       # Sıralama algoritmaları
├── utils/                         # Yardımcı sınıflar
├── navigation/                    # Navigation component
└── MainActivity.kt               # Ana aktivite
```

---

## 📱 Ekranlar ve Özellikleri

### 1. **HomeScreen**
- 4 ana kart: Listeler, Kriterler, Devam Eden Turnuvalar, Arşiv
- Modern card-based tasarım
- Navigation hub olarak çalışır

### 2. **ListsScreen** 
- Mevcut listeleri gösterir
- "Yeni Liste Ekle" butonu
- Liste başlatma seçenekleri

### 3. **CreateListScreen**
- Manuel veri girişi
- CSV import (kopyala-yapıştır)
- Delimiter seçimi (comma, semicolon, tab)
- Display mode: Cards vs Table

### 4. **ListViewScreen**
- 2 sekme: "Takım Kartları" ve "Tablo Formatı"
- CSV verilerini parse ederek görüntüleme
- ListEditScreen'e yönlendirme

### 5. **ListEditScreen**
- Interaktif tablo düzenleme
- Drag-drop sütun/satır reordering
- Sütun ekleme/silme
- Satır ekleme/silme
- Real-time edit support

### 6. **RankingScreen** (Ana Turnuva Ekranı)
- 6 farklı sıralama sistemini destekler
- Progress tracking
- Real-time scoring
- Session persistence
- Kriter değerlendirme dialogu
- Standings dialog

### 7. **CriteriaScreen**
- Kriter listelerini yönetir
- JSON formatında kriter saklama

### 8. **CreateCriteriaScreen**
- Yeni kriter listesi oluşturma
- Dinamik kriter ekleme/çıkarma

### 9. **ResultsScreen**
- Turnuva sonuçlarını gösterir
- Arşivleme özelliği

### 10. **ArchiveScreen**
- Arşivlenmiş turnuvaları listeler
- Geçmiş sonuçları görüntüler

---

## 🗄️ Veritabanı Şeması (Room Database)

### Ana Tablolar:

**songs**
```kotlin
@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val artist: String = "",
    val album: String = "",
    val trackNumber: Int = 0,
    val listId: Long,
    val csvData: String? = null // JSON formatted table data
)
```

**song_lists**
```kotlin
@Entity(tableName = "song_lists")
data class SongList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val songCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
```

**matches**
```kotlin
@Entity(tableName = "matches")
data class Match(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val rankingMethod: String,
    val songId1: Long,
    val songId2: Long,
    val winnerId: Long? = null,
    val score1: Int? = null,
    val score2: Int? = null,
    val round: Int = 1,
    val matchNumber: Int = 0,
    val isCompleted: Boolean = false
)
```

**voting_sessions**
```kotlin
@Entity(tableName = "voting_sessions")
data class VotingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val rankingMethod: String,
    val sessionName: String,
    val currentIndex: Int = 0,
    val totalItems: Int = 0,
    val progress: Float = 0f,
    val currentSongId: Long? = null,
    val currentRound: Int = 1,
    val completedMatches: Int = 0,
    val totalMatches: Int = 0,
    val isCompleted: Boolean = false,
    val isPaused: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
```

**swiss_states** (Swiss system için)
```kotlin
@Entity(tableName = "swiss_states")
data class SwissState(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val currentRound: Int,
    val maxRounds: Int,
    val standings: String, // JSON format
    val pairingHistory: String, // JSON format
    val roundHistory: String // JSON format
)
```

**criterion_lists**
```kotlin
@Entity(tableName = "criterion_lists")
data class CriterionList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val criteria: String, // JSON array
    val createdAt: Long = System.currentTimeMillis()
)
```

**tournaments**
```kotlin
@Entity(tableName = "tournaments")
data class Tournament(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDate: String,
    val songListId: Long,
    val systemType: String,
    val criterionListId: Long? = null,
    val criteriaSettings: String? = null, // JSON format
    val isCompleted: Boolean = false
)
```

---

## 🧮 Sıralama Algoritmaları

### 1. **Direct Scoring**
- Her öğeye direkt puan verme
- 0-100 arası serbest puanlama

### 2. **League (Lig Sistemi)**
- Herkes herkesle oynar
- 3 puan kazanma, 1 puan beraberlik

### 3. **Swiss System**
- Benzer puanlı takımları eşleştirme
- Logaritmik tur sayısı

### 4. **Elimination**
- Grup aşaması + knockout
- 2'nin üssü sistemi

### 5. **Full Elimination**
- Direkt eleme sistemi
- Bracket-style tournament

### 6. **EMRE_CORRECT (En Önemli)**
Geliştirilmiş İsviçre Sistemi - En karmaşık algoritma:

```kotlin
object EmreSystemCorrect {
    data class EmreTeam(
        val song: Song,
        var points: Double = 0.0,
        val matchHistory: MutableList<Long> = mutableListOf(),
        var rank: Int = 0
    )
    
    data class EmreState(
        val teams: List<EmreTeam>,
        var currentRound: Int = 1,
        var isComplete: Boolean = false,
        val teamCount: Int
    )
    
    // Alternating match numbering: TOP KEAT→1,2,3... BOTTOM KEAT→18,17,16...
    // Proximity-based pairing algorithm
    // Smart backtrack ve displaced team tracking
    // Duplicate prevention: %100 çalışır duplicate pairing koruması
}
```

---

## 🎨 UI Tasarım Gereksinimleri

### Material Design 3 Theme
```kotlin
@Composable
fun RankingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF2196F3),
            secondary = Color(0xFF4CAF50),
            surface = Color.White
        ),
        content = content
    )
}
```

### Önemli UI Bileşenleri:

**TeamCardContent** - Alt alta tablo formatı:
```kotlin
@Composable
fun TeamCardContent(song: Song, csvData: Map<String, String>?) {
    // JSON parse edip alt alta key-value gösterim
    // Bold başlıklar + normal değerler
    // Mavi tema (#E1F5FE background)
}
```

**CsvDataTable** - İnteraktif tablo:
```kotlin
@Composable
fun CsvDataTable(
    headers: List<String>,
    data: List<Map<String, String>>,
    onCellClick: (Int, String) -> Unit = { _, _ -> },
    clickable: Boolean = false
) {
    // LazyColumn ile performanslı tablo
    // Horizontal scroll support
    // Conditional scrolling (8+ satır varsa)
}
```

**Drag-Drop Support**:
```kotlin
// Sütun drag-drop
.pointerInput(columnIndex) {
    detectDragGestures(
        onDragStart = { /* haptic feedback */ },
        onDragEnd = { /* reorder logic */ },
        onDrag = { /* visual feedback */ }
    )
}
```

---

## 📦 Dependencies (build.gradle.kts)

```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui:1.5.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.4")
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("androidx.navigation:navigation-compose:2.7.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    
    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // JSON handling
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

---

## 🔧 Özel Gereksinimler

### 1. **CSV Parsing**
- Akıllı delimiter detection (comma, semicolon, tab)
- Unicode/Turkish character support
- BOM detection ve kaldırma
- Multi-line cell support

### 2. **Session Persistence**
- Real-time match state saving
- Mid-match resume capability
- Swiss algorithm integrity koruması
- 4-level recovery system

### 3. **Kriter Sistemi**
- Tam ekran dialog (RoundedCornerShape(0.dp))
- İki puanlama tipi: Separate vs Comparative
- Tournament settings entegrasyonu
- Renk koordinasyonu (mavi/yeşil takım sistemi)

### 4. **Error Handling**
- Ultra-defensive crash prevention
- Comprehensive try-catch wrapper'lar
- Safe state restoration
- Graceful fallback systems

---

## 🚀 Başlangıç Talimatları

1. **Yeni Android Studio projesi oluştur** (Empty Activity, Compose)
2. **Dependencies'leri ekle** (yukarıdaki liste)
3. **Veritabanı sınıflarını oluştur** (entities, DAO'lar, database)
4. **Navigation component'i kur**
5. **Ekranları sırasıyla implement et** (HomeScreen'den başla)
6. **Sıralama algoritmalarını ekle** (EMRE_CORRECT en son)
7. **UI polish ve test**

---

## ⚠️ Kritik Notlar

- **EMRE_CORRECT algoritması** en karmaşık bölüm - özel dikkat gerekli
- **Room Database migration** stratejisi planla
- **CSV parsing** Türkçe karakter desteği önemli
- **Memory optimization** büyük listeler için gerekli
- **Crash prevention** tüm kullanıcı etkileşimlerinde uygulanmalı

---

## 📞 Destek

Bu prompt ile yapay zeka tüm uygulamayı yazabilmelidir. Eksik bilgi varsa:
1. **Mevcut dosyaları analiz et**
2. **Benzer pattern'ları takip et**  
3. **Material Design guidelines**'ı uygula
4. **Android best practices**'i kullan

**🎯 Hedef: Tamamen çalışır, production-ready Android uygulaması**