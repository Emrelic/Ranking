# KRİTERLER SİSTEMİ İSTİŞARE KAYITLARI

## 🎯 GENEL HEDEFLERİMİZ
1. Ana sayfa redesign: Listeler, Kriterler, Devam Eden Turnuvalar, Arşiv
2. Kriterler sistemi implementation
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

---

## 🚨 LİMİT YAKINLAŞIYOR - KAYDETME DURumu

---

## 🎯 FINAL PLAN
(İstişare bitince final implementation planı buraya yazılacak)

---

## 💾 DEVAMLLIK NOTLARI
- Bu dosya her istişare sonrası güncelleniyor
- Terminal değişse de bu dosyadan kaldığımız yeri bulabiliriz
- Limit dolduğunda buradan devam edebiliriz
- Projede kalıcı kayıt tutuluyor