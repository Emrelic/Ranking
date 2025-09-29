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

## 📝 ÖZET - KRİTER SİSTEMİ İSTİŞARE KAYITLARI

### 🎯 KARARLAŞTIRILMIŞ ÖZELLIKLER
1. **Ana sayfa redesign**: 4 büyük kart sistemi (Listeler, Kriterler, Devam Eden Turnuvalar, Arşiv) ✅
2. **Kriterler yönetimi**: CSV import + manuel kriter girişi ✅
3. **İki puanlama sistemi**: Ayrı ayrı (dropdown) + Kıyaslamalı (slider) ✅
4. **Turnuva entegrasyonu**: Tournament-level ayarlar + real-time kaydetme ✅
5. **UI/UX flow**: İkili pencere sistemi (voting + kriter değerlendirmesi) ✅

### 🗄️ DATABASE ARCHITECTURE (FINAL)
```
- Tournament: Ana turnuva tablosu + JSON criteriaSettings
- CriterionList: Kriter listeleri + JSON criteria array
- CriterionScore: Maç bazlı kriter puanları
- Match: tournamentId field eklendi
```

### 🎯 IMPLEMENTATION STATUS
- ✅ **6 Phase** implementasyon tamamlandı
- ✅ **22 soru/cevap** değerlendirme tamamlandı
- ✅ **Swiss sistem entegrasyonu** korundu
- ✅ **Real-time persistence** + crash recovery
- ✅ **Archive integration** tamamlandı

**🎉 SİSTEM %100 HAZIR VE ÇALIŞIR DURUMDA**

---

**Not**: Bu dosyadaki detaylı istişare kayıtları (400+ satır) token optimizasyonu için kısaltıldı.
Tam detaylar için ARCHIVED_NOTES.md dosyasına bakabilirsiniz.