# KRİTERLER SİSTEMİ İSTİŞARE KAYITLARI

## 🎯 GENEL HEDEFLERİMİZ
1. ✅ Ana sayfa redesign: Listeler, Kriterler, Devam Eden Turnuvalar, Arşiv
2. ✅ Kriterler sistemi implementation
3. ⚠️ **YENİ SORUN (2025-09-03)**: Turnuva navigation sorunu

## 📝 SON DURUM RAPORU (2025-09-03 22:50)
### ✅ TAMAMLANAN
- Ana sayfa redesign + 5 kart layout
- Kriterler sistemi tam implementasyonu
- NewTournamentScreen 6 turnuva sistemi
- Smart navigation sistemi
- Database kayıtları çalışıyor

### ⚠️ DEVAM EDEN SORUN
- TournamentRankingScreen açılıyor ama initialize edilmiyor
- Log: "Screen loaded" ve "Initializing tournament" görünüyor
- Ancak sonrasında turnuva UI yüklenmiyor
- Problem muhtemelen TournamentRankingViewModel.initializeTournament()'de
3. Turnuva sürecine kriter entegrasyonu
4. CSV import ve manuel kriter girişi
5. Slider bazlı puanlama sistemi
6. Arşiv entegrasyonu

## 📋 TESPİT EDİLECEK DETAYLAR

### 🔍 **Database & Architecture Soruları:**

1. **Kriterler Database Schema:**
   - Kriter listesi için tablo adı: `criterion_lists` mi?
   - Her kriter için: `criteria` tablosu mu? (id, listId, name, order)
   - Kriter puanlamaları için: `criterion_scores` tablosu? (matchId, criterionId, team1Score, team2Score)

2. **Existing Tables Integration:**
   - Mevcut `Match` tablosuna `criterionListId: Long?` field eklenecek mi?
   - `VotingSession` ile kriter ilişkisi nasıl olacak?

### 🎯 **UI/UX Flow Soruları:**

3. **Ana Sayfa Navigation:**
   - Mevcut `SongListScreen` tamamen değişecek mi?
   - Bottom navigation mı yoksa vertical list mi?

4. **Turnuva Başlatma Flow:**
   - Şu anki: Liste → Usul → Başlat
   - Yeni: Liste → Usul → Kriter (opsiyonel) → Ayarlar → Başlat mi?

5. **Kriter Oylama UI:**
   - Slider component hangi range'de? (0-10, 0-100?)
   - "Değerlendir" checkbox'ı her kriter için ayrı mı?
   - Toplam puan real-time hesaplansın mı?

### ⚙️ **Business Logic Soruları:**

6. **Kriter Zorunluluğu:**
   - "Tüm kriterler oylansın" → incomplete criteria varsa match completion engellensin mi?
   - "Kısmen oylamaya izin ver" → hangi kriterler zorunlu?

7. **Puanlama Sistemi:**
   - 10 puanlı sistemde: Team1=7, Team2=3 ise toplam 10 mu?
   - Null kriterler toplam puana dahil edilmesin mi?
   - Beraberlik durumu: 5-5 mi yoksa farklı threshold?

8. **Integration with Existing Ranking:**
   - Swiss system'deki win/loss/draw ile kriter puanları nasıl birleşecek?
   - Kriter puanları sadece analiz için mi yoksa tournament outcome'u etkiler mi?

### 📦 **Data Management:**

9. **CSV Import:**
   - CSV formatı: "KriterAdı" tek sütun mu?
   - Kriter sıralaması CSV'den mi yoksa sonradan düzenlenebilir mi?

10. **Archive Integration:**
    - Kriter verileri `VotingSession` ile mi link olacak?
    - Tournament bittiğinde kriter skorları da freeze edilecek mi?

---

## 📝 İSTİŞARE KAYITLARI

### [SORU 1] Kriterler Database Schema
**SORU:** 3 tablo (criterion_lists, criteria, criterion_scores) mi yoksa tek tablo JSON formatında mı?
**CEVAP:** Tek tablo yeter
**KARAR:** ✅ TEK TABLO YAPISI - JSON formatında kriterler saklanacak

### [SORU 2] Puanlama Sistemi Tipleri
**SORU:** Kriter puanlama sistemi nasıl çalışsın?
**CEVAP:** İki opsiyon olsun:
1. **AYRI AYRI PUANLAMA:** Her takım ayrı puan alır (örn: 10 üzerinden - Takım1=9, Takım2=7)
2. **KIYASLAMALI PUANLAMA:** Toplam puanı bölüştür (örn: 10 puan toplam - Takım1=7, Takım2=3)

**KARAR:** ✅ İKİ PUANLAMA SİSTEMİ:
- **Ayrı Ayrı:** Elle yazma/dropdown (0-10 arası seçim)
- **Kıyaslamalı:** Slider ile toplam puanı bölüştürme (10 puanı 7-3, 5-5, 8-2 şeklinde)

### [SORU 3] Puan Skalası ve Opsiyonlar
**SORU:** Puan skalası seçimi, UI tipleri, slider adımları ve değerlendirme opsiyonları nasıl olsun?
**CEVAP:** 
- **Puan Skalası:** Kullanıcı seçebilsin (1,2,3,5,10,20,100) + Elle gir opsiyonu (örn: 19)
- **Tam Sayı:** Seçilen puan sisteminde tam sayılar (10 puan → 7-3, 8-2, 6-4)
- **Null Handling:** Girilmeyen kriterler null/null olsun

**TURNUVa AYaRLARI:**
1. **Kriter Var/Yok:** Kriterli/Kritersiz turnuva seçimi
2. **Otomatik Açılım:** Kriter tablosu otomatik açılsın/buton ile açılsın
3. **Zorunluluk:** Mecburi/Opsiyonel kriter oylaması
4. **Kriter Bazlı Zorunluluk:** Her kriter için ayrı mecburi/opsiyonel seçimi
5. **Kısmi Oylama İzni:** Tüm kriterler zorunlu/kısmen oylamaya izin ver

**VARSAYILAN AYARLAR:**
- ✅ Opsiyonel buton ile açılma (otomatik DEĞİL)
- ✅ Opsiyonel oylama (mecburi DEĞİL)
- ✅ Kısmi oylamaya izin ver

**KARAR:** ✅ TAM ÖZELLEŞTİRİLEBİLİR KRİTER SİSTEMİ - Kullanıcı tüm opsiyonları kontrolünde

### [SORU 4] Ana Sayfa UI Tasarımı
**SORU:** Ana sayfa "Listeler, Kriterler, Devam Eden Turnuvalar, Arşiv" nasıl görünsün?
**CEVAP:** 4 tıklanabilir kare dikdörtgen buton olsun
**KARAR:** ✅ 4 BÜYÜK KART TASARIMI - Tıklanabilir dikdörtgen butonlar

### [SORU 5] Mevcut SongListScreen Dönüşümü
**SORU:** Mevcut ana sayfa değişsin mi yoksa "Listeler" butonuna mı dönüştürülsün?
**CEVAP:** Derleyip toplayalım, yeterli ise mevcut ekranı yeni yapıya dönüştürebilirsin
**KARAR:** ✅ MEVCUt EKRANI YENİ YAPIYA DÖNÜŞTÜR - Ana sayfa redesign

### [SORU 6] Navigation Flow Onayı
**SORU:** Ana sayfa → Kriterler → Kriter Listesi → Yeni Kriter Oluştur akışı doğru mu?
**CEVAP:** Bu akış genel olarak doğru. Kriterler butonuna tıklayınca mevcut kriter listeleri görünsün
**KARAR:** ✅ NAVIGATION FLOW ONAYLI - Standard liste yönetimi pattern'i

### [SORU 7] CSV Format Basitliği
**SORU:** CSV formatı nasıl olsun? Ek bilgiler var mı?
**CEVAP:** Kriterler sadece bir listeden ibaret olacak. Şimdilik ek özellik gerekmez.
**KARAR:** ✅ BASİT CSV FORMAT - Sadece kriter isimleri (Melodi, Ritim, Tempo, Vokal)

### [SORU 9] Tournament Setup Flow
**SORU:** Turnuva başlatma akışı tam olarak nasıl değişsin?
**CEVAP:** 
- **YENİ TURNUVA BAŞLAT** butonu olsun
- Turnuva ismi: `{Liste Adı} {Usul} {Tarih} turnuva. {X} kriter listeli` (opsiyonel manuel isim)
- Akış: Liste Seç → Turnuva Adı (opsiyonel) → Usul Seç → Kriter Listesi (opsiyonel) → Kriter Ayarları → Başlat

**KARAR:** ✅ YENİ TURNUVA SETUP FLOW - Detaylı turnuva yapılandırması

### [SORU 10] Match Screen Integration
**SORU:** `RankingScreen`'deki voting UI nasıl değişecek?
**CEVAP:** 
- Mevcut butonlar + **"Kriterler ile Değerlendir"** butonu
- Kriter penceresi: Otomatik açılım ayarı yoksa buton ile açılır
- İkinci pencere: Kriterler satır satır, A-B takım sütunları
- Puanlama: Dropdown seçim VEYA slider ile bölüştürme
- Alt kısım: İki küçük galibiyet butonu + ortada beraberlik
- Kaydırılabilir kriter listesi

**KARAR:** ✅ İKİLİ PENcERE SİSTEMİ - Ana voting + Kriter değerlendirmesi

### [SORU 11] Scoring Integration
**SORU:** Kriter puanları turnuva sonucunu etkiler mi?
**CEVAP:** 
- **Turnuva başında sorulan ayarlar:**
  1. "Turnuva sonucunu etkilesin mi?" → Tiebreaker sisteminde kriter puanı
  2. "Galibiyet oylamalarını kriter puanına göre belirle" / "Sadece referans için"
- Tiebreaker: Kriter puanı > anlık sıralama

**KARAR:** ✅ TURNUVa BAŞINDA AYARLANIR - İki seviyeli etki (sonuç/referans)

### [SORU 12] Data Persistence Stratejisi
**SORU:** Kriter verileri nasıl saklanacak?
**CEVAP:** Kriter puanlarını arşivde saklayalım
**KARAR:** ✅ ARŞİV ENTEGRAsYONU - Kriter skorları kalıcı saklama

### [SORU 8] Database Architecture
**SORU:** Database yapısı nasıl olsun?
**CEVAP:** Stabil praktik olan yapıyı Claude belirleyecek
**KARAR:** ✅ CLAUDE OPTIMAL YAPISI - En uygun database design'ı seç

### [SORU 16] Error Handling  
**SORU:** Kriter sisteminde error durumları nasıl?
**CEVAP:** Kriterler oylanınca kaydedilsin, yarım kalmada program çıkılsa kaybolmasın
**KARAR:** ✅ OTOMATIK KAYDETME - Real-time persistence, crash-safe

### [SORU 13] Tournament Entity Changes
**SORU:** Turnuva sistemi için veri tabloları nasıl olacak?
**CEVAP:** Sağlıklı stabil ise yeni tablo aç, yoksa Claude karar versin
**KARAR:** ✅ YENİ TOURNAMENT TABLOSU - Ayrı Tournament entity (cleaner architecture)

### [SORU 15] UI State Management
**SORU:** Kriter penceresi bellek yönetimi nasıl olacak?
**CEVAP:** Sağlıklı olan yaklaşımı Claude seçecek
**KARAR:** ✅ AYRI CRITERIAVİEWMODEL - Separation of concerns için ayrı ViewModel

### [SORU 14] Criteria Settings Scope
**SORU:** Kriter ayarları kapsamı nasıl olacak?
**CEVAP:** Her turnuva başında ayarlar seçilsin, turnuva boyunca sabit kalsın
**KARAR:** ✅ TURNUVA SEVİYESİ AYARLAR - Tournament-level configuration, maç bazında değişmez

---

## 🎯 FINAL IMPLEMENTATION PLAN

### 📊 **UPDATED DATABASE ARCHITECTURE**
```
1. Tournament (Yeni Tablo)
   - id: Long
   - name: String
   - startDate: String  
   - songListId: Long
   - systemType: String (Swiss/Emre)
   - criterionListId: Long?
   - criteriaSettings: String (JSON)
     {
       "scoringType": "separate|comparative",
       "scoreScale": 10,
       "drawThresholdPercent": [40,60],
       "autoWinnerFromCriteria": true,
       "autoOpenCriteriaPanel": false,
       "mandatoryCriteria": false
     }
   - isCompleted: Boolean

2. Match (Güncellenecek Tablo) 
   - [mevcut fieldlar]
   - tournamentId: Long? (YENİ - cleaner relationships)

3. CriterionList (Yeni Tablo)
   - id: Long
   - name: String
   - criteria: String (JSON array: ["Melodi","Ritim","Tempo"])
   - createdDate: String
   - isActive: Boolean (silme koruması için)

4. CriterionScore (Yeni Tablo)
   - id: Long
   - matchId: Long
   - tournamentId: Long
   - criterionName: String
   - team1Score: Double?
   - team2Score: Double?
   - createdAt: String
```

### 🎯 **IMPLEMENTATION ROADMAP**

#### **PHASE 1: Database Setup & Migration**
1. ✅ Create Tournament, CriterionList, CriterionScore entities
2. ✅ Update Match entity (add tournamentId)
3. ✅ Create DAO interfaces with validation
4. ✅ Database migration: VotingSession → Tournament
5. ✅ Foreign key constraints setup

#### **PHASE 2: Ana Sayfa Redesign**
1. ✅ Transform SongListScreen → MainMenuScreen
2. ✅ 4 büyük kart: Listeler, Kriterler, Devam Eden Turnuvalar, Arşiv
3. ✅ Navigation updates

#### **PHASE 3: Kriterler Yönetimi**
1. ✅ CriterionListScreen (CRUD operations)
2. ✅ CSV import functionality
3. ✅ Manual kriter ekleme/düzenleme

#### **PHASE 4: Tournament Setup Flow**
1. ✅ YeniTurnuvaScreen (Liste→İsim→Usul→Kriter→Ayarlar→Başlat)
2. ✅ Criteria settings configuration
3. ✅ Tournament metadata generation

#### **PHASE 5: Match Voting Integration**  
1. ✅ RankingScreen'e "Kriterler ile Değerlendir" butonu
2. ✅ CriteriaEvaluationScreen (overlay modal)
3. ✅ UI state management: Disable background buttons
4. ✅ Dual voting paths: Kaydet vs Galibiyet butonları
5. ✅ processCriteriaResults() fonksiyonu
6. ✅ Beraberlik algoritması (%threshold calculation)

#### **PHASE 6: Data Persistence & Archive**
1. ✅ Real-time kriter score kaydetme
2. ✅ VotingSession integration
3. ✅ Archive screen kriter verileri görüntüleme

### 🔧 **TECHNICAL DECISIONS**
- **Database:** 3 ayrı tablo (clean architecture)
- **UI State:** Ayrı CriteriaViewModel (separation of concerns)
- **Ayarlar:** Tournament-level configuration
- **Persistence:** Real-time kaydetme + crash-safe
- **Navigation:** Jetpack Navigation component

### 📱 **UI FLOW**
```
Ana Sayfa → [Kriterler] → Kriter Listeleri → CSV/Manuel Ekle
Ana Sayfa → [Listeler] → Liste Seç → Yeni Turnuva Başlat
  → Turnuva Adı → Usul Seç → Kriter Seç → Ayarlar → Başlat
RankingScreen → Galibiyet Oylaması + [Kriterler Değerlendir]
  → Kriter Popup → Puanlama → Kaydet
```

### 🎯 **SUCCESS CRITERIA**
- ✅ 16 soru tamamlandı ve karara bağlandı
- ✅ Tam özelleştirilebilir kriter sistemi
- ✅ Crash-safe veri persistance
- ✅ Ana sayfa redesign
- ✅ Swiss system ile tam entegrasyon

---

## 🔍 **EKSİK DETAY AÇIKLAMALARI (2. TUR)**

### **[DETAY 1] Kriter Puanlama Algoritması**
**AÇIKLAMA:**
- **Kriter Galibi:** Daha fazla puan alan takım
- **Beraberlik Aralığı:** Turnuva başında ayarlanır (örn: %49-51, %40-60)
- **Hesaplama:** Team1_Puan / (Team1_Puan + Team2_Puan) * 100
- **Örnek:** 290/(290+230) = %55.7 → %60-40 ayarında beraberlik
- **NULL Kriterler:** Sıfır gibi, toplam hesaba dahil edilmez

### **[DETAY 2] Swiss Sistem Entegrasyonu**
**AÇIKLAMA:**
- **Kriter Puanları Swiss'de KULLANILMAZ**
- **İki Mod:**
  1. "Sadece referans" → Kullanıcı manuel galip seçer, kriter puanı 290-200 olsa bile
  2. "Otomatik belirleme" → Fazla puan alan otomatik galip, Swiss'e 1 puan
- **Kullanıcı Kontrolü:** Her durumda final karar kullanıcıda

### **[DETAY 4] UI State Synchronization**
**AÇIKLAMA:**
- **Kriter penceresi açıkken:** Arka plan galibiyet butonları DISABLED
- **İki kaydetme yolu:**
  1. Kriter penceresi "Kaydet" → Sadece kriter skorları
  2. Kriter penceresi alt "Galibiyet butonları" → Kriter + Match sonucu + Pencere kapanır
- **Real-time sync:** Kriter verisi ana ekrana anında yansır

### **[DETAY 6] Ana Sayfa Navigation**
**AÇIKLAMA:**
- **"Devam Eden Turnuvalar":** isCompleted = false olan turnuvalar
- **"Arşiv":** isCompleted = true olan turnuvalar
- **Filtering:** Tarih, sistem tipi, kriter varlığına göre filtreleme

### **[DETAY 3] Mevcut Sistemle Entegrasyon**
**KARARLAR:**
1. **Kriter fonksiyonu:** Ayrı `processCriteriaResults()` fonksiyonu oluştur
2. **Database yapısı:** Her iki sistem de tutulsun (`VotingSession` + `Tournament`)
3. **Migration:** Tüm eski turnuvalar yeni sisteme çevrilsin
4. **Match bağlantısı:** `Match.tournamentId` eklensin (cleaner relationships)
5. **Kriter listesi koruması:** Kullanımdaki kriter listesi silinemesin (validation)

### **[DETAY 5] Edge Case Handling**
**AÇIKLAMA:**
- **Kriter Listesi Silme:** "Bu liste X turnuvada kullanılıyor" uyarısı + silme engeli
- **Veri Bütünlüğü:** Foreign key constraints ile referential integrity
- **Graceful Degradation:** Kriter listesi corrupt olsa bile turnuva devam etsin

---

## 🔍 **FINAL DETAYLAR (3. TUR)**

### **[SORU 17] Performance & UX**
**CEVAP:** 27+ kriterlik büyük listeler scroll ile aşağı kaydırılabilir
**KARAR:** ✅ SCROLLABLE KRİTER LİSTESİ - LazyColumn ile optimize edilmiş scroll

### **[SORU 18] Export/Import Requirement**
**CEVAP:** Turnuva bittiğinde arşive kaydedilecek, telefon değişimi/yedekleme/import gerekli değil
**KARAR:** ✅ SADECE ARŞİV SİSTEMİ - Export/Import özelliği skip edildi

### **[SORU 19] Multi-Language**
**CEVAP:** Türkçe karakterler desteklensin  
**KARAR:** ✅ TÜRKÇE KARAKTER DESTEĞİ - UTF-8 encoding, Turkish locale

### **[SORU 20] Tournament Resumption** 
**CEVAP:** Turnuva kapatılıp geri dönülürse kaldığı yerden devam etmeli
**KARAR:** ✅ AUTO-RESUME - State persistence + graceful recovery

### **[SORU 21] Advanced Scoring**
**CEVAP:** Kriterler şimdilik eşit ağırlık, ileride ağırlık sistemi eklenebilir
**KARAR:** ✅ EŞİT AĞIRLIK MVP - Future weight system için extensible design

### **[SORU 22] Error Recovery**
**KARAR:** ✅ CLAUDE OPTİMİZASYONU:
- **Retry mechanism:** 3x retry for network errors
- **Data validation:** Corrupt data detection + recovery
- **Storage management:** Low disk space handling
- **Transaction rollback:** Database consistency guaranteed

---

## 🎯 **FINAL PLAN STATUS**

### ✅ **TAMAMLANAN İSTİŞARE**
- **22 soru** soruldu ve cevaplalandı ✓
- **6 kritik detay** açıklığa kavuşturuldu ✓  
- **Database architecture** detaylandırıldı ✓
- **Algorithm specifications** belirlendi ✓
- **UI/UX flow** planlandı ✓
- **Performance considerations** ele alındı ✓
- **Error handling strategy** belirlendi ✓

### 🚀 **IMPLEMENTATION READY**
- **Complete roadmap:** 6 Phase implementation
- **Database schema:** 4 tablo + migration strategy  
- **Business logic:** Detailed algorithm specifications
- **UI components:** Modal overlay + scrollable lists
- **Integration:** Swiss system compatibility maintained
- **Edge cases:** Comprehensive error handling

### 📋 **CORE FEATURES CONFIRMED**
- ✅ Ana sayfa redesign (4 kart sistemi)
- ✅ Kriterler yönetimi (CSV + manuel)
- ✅ Tournament setup flow (tam özelleştirilebilir)
- ✅ Dual voting system (galibiyet + kriterler)
- ✅ Real-time persistence + crash recovery
- ✅ Archive integration (export skip edildi)

**🎉 PLAN %100 READY FOR IMPLEMENTATION!**

---

## 💾 DEVAMLLIK NOTLARI
- Bu dosya her istişare sonrası güncelleniyor
- Terminal değişse de bu dosyadan kaldığımız yeri bulabiliriz
- Limit dolduğunda buradan devam edebiliriz
- Projede kalıcı kayıt tutuluyor