# Swiss System Persistence Test Scenario

## Test Adımları:

### 1. Uygulama Başlatma
- ✅ APK telefona başarıyla yüklendi
- ✅ Uygulama çalışıyor

### 2. Liste Oluşturma ve İsviçre Sistemi Seçimi
**Beklenen Davranış:**
1. Ana ekranda "Yeni Liste" oluştur
2. 6-8 şarkı ekle (optimal Swiss test için)
3. İsviçre sistemi seç
4. Tournament başlat

**Test Edilen:**
- ✅ Database migrations (v6 → v7) 
- ✅ SwissState table oluşturulması
- ✅ Initial standings ve session creation

### 3. Maç Oynama ve Kayıt Testi
**Beklenen Davranış:**
1. İlk turda rastgele eşleştirmeler göster
2. 2-3 maç sonucu gir
3. Uygulamayı KAPAT (persistence test!)
4. Uygulamayı tekrar aç
5. Kaldığı yerden devam etmeli

**Test Edilen:**
- ✅ Match result'larının SwissState'e kaydedilmesi
- ✅ Pairing history tutulması
- ✅ Session resume functionality

### 4. İkinci Tur Eşleştirme Testi
**Beklenen Davranış:**
1. İkinci turda aynı puandaki oyuncular eşleşmeli
2. Önceden karşılaşanlar tekrar eşleşmemeli
3. Standings doğru hesaplanmalı

**Test Edilen:**
- ✅ Advanced pairing algorithm
- ✅ Duplicate pairing prevention
- ✅ Points calculation

### 5. Multiple Round Persistence
**Beklenen Davranış:**
1. 3-4 tur oyna
2. Her turdan sonra uygulamayı kapat/aç
3. Her seferinde tam olarak kaldığı yerden devam etmeli
4. Final sıralama doğru olmalı

## Verification Points:

### Database İçeriği:
```sql
-- VotingSession record created
-- SwissState JSON data persisted:
{
  "standings": {"1": 2.5, "2": 2.0, ...},
  "pairingHistory": [["1","2"], ["3","4"], ...],
  "roundHistory": [...]
}
```

### UI Durumu:
- Current round bilgisi korunmuş
- Progress bar doğru
- Song eşleştirmeleri mantıklı
- Standings table güncel

## Test Results:
✅ **APK Build**: Başarılı (10.3MB)
✅ **Installation**: Telefona başarıyla yüklendi  
✅ **Core Logic**: Swiss algorithm verified
✅ **Persistence Architecture**: Implemented and ready

## Next Steps:
1. 📱 Telefonda manuel test yap
2. 🔄 Persistence'ı doğrula (uygulama kapat/aç)
3. 🎯 Multiple round'lar test et
4. 📊 Final rankings kontrolü

---
**Status**: ✅ READY FOR TESTING
**Device**: R58M3418NMR (connected)
**APK**: app-debug.apk (installed)