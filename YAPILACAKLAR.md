# YAPILACAKLAR NOT DEFTERİ

Güncelleme: 2026-07-25 (Faz 0-4 toparlama turu sonrası tekilleştirildi).
Kapsamlı denetim bulguları ve yapılan işlerin kaydı: **ANALIZ_RAPORU.md**

## 🔴 AÇIK MADDELER

### Yeni Turnuva Süreci Kısayolları — 2025-09-18'den beri açık
1. **Yeni Liste Ekle kısayolu**: Sihirbazın liste adımından CreateListScreen'e geçiş + dönüşte yeni listenin otomatik seçili gelmesi
2. **Yeni Kriter Listesi Ekle kısayolu**: Kriter adımından CreateCriteriaScreen'e geçiş + dönüşte otomatik seçim
3. **Navigation/State altyapısı** (1-2 için): yeni kayıt ID'sinin navigation argument olarak taşınması, "+" FAB butonları

### Oylama Ekranı
4. **EKRAN_GORUNTULERI.md Image #6** (2025-09-23): 5/6 bölümlü sabit oylama layout'u — "implementasyon gerekli" durumunda bekliyor

### Tablo Rötuşu — doğrulama bekliyor
5. **Cihazda test**: 2026-07-25'te düzeltilen sütun/satır drag-drop ve Kaydet akışı gerçek cihazda doğrulanmalı (özellikle: sütun E→B taşıma, satır taşıyıp kaydetme, kayıt sonrası öğe adlarının bozulmaması)

## 🟡 TEKNİK BORÇ (ANALIZ_RAPORU.md Faz 0-4'ten artan)

8. **LEAGUE oturum/persistence**: Lig turnuvaları session kaydı olmadığı için "Devam Et" akışına bağlanamıyor (bağlanırsa initializeLeague maçları silip yeniden kurar — önce oturum yönetimi LEAGUE'e genişletilmeli). `saveLeagueSettings` de hiç çağrılmıyor; lig ayarları UI'ı yok
9. **RankingViewModel bölünmesi**: ~1500 satır, çok sorumluluk; sistem başına strategy + SessionManager ayrıştırması
10. **initializeRanking'deki sonsuz Flow.collect**: liste her değiştiğinde init yeniden koşuyor; `first()` ile tek seferlik okumaya çevrilmeli
11. **collectAsStateWithLifecycle** geçişi (22 çağrı lifecycle-aware değil)
12. **Karar bekleyen kapalı özellikler**: ELIMINATION/FULL_ELIMINATION (tamamla ya da kodu sil), YouTube katmanı (bitir ya da 3 tablo+DAO+ekranı kaldır), SWISS kodunun tamamen silinmesi
13. **Ölü DAO metotları** (~60 adet, çoğu YouTube): kullanım kararı sonrası temizlik
14. **MERGE_SORT beraberlik koruması**: `submitDrawResult` MERGE_SORT'ta çağrılırsa sıralama sessizce keyfileşir; VM seviyesinde engellenmeli
15. **Room migration testleri**: 1-15 şema JSON'ları yok, MigrationTestHelper kurulmadı (androidTest hiç yok)
16. **ListEditScreen rememberSaveable**: kaydedilmemiş tablo düzenlemeleri ekran döndürmede kayboluyor (kompleks tipler için Saver gerekir)

## 💡 FİKİR DEPOSU
(Gelecek için fikirler burada)

---

## 📋 KULLANIM
- **"ynd" komutu**: Yeni madde ekle (Format: [Madde açıklaması] → ynd; tarih damgası otomatik)

## ✅ ARŞIV — TAMAMLANANLAR
- **[2026-07-25] Tablo Rötuşu üçlüsü** (2025-09-16'dan beri açıktı):
  sütun/satır drag-drop hedef hesabı düzeltildi (yerel koordinat + dp/px
  karışıklığı + scroll'un jesti çalması), Kaydet satır-şarkı kimlik
  eşlemesiyle güvenli hale getirildi (satır taşıma sonrası yanlış kayda
  yazma + adların "No" sütunuyla ezilmesi giderildi, yeni satırlar artık
  gerçekten kaydediliyor), buton etiketleri i18n turunda eklendi
- **[2026-07-25] Faz 0-4 toparlama turu** (ayrıntı: ANALIZ_RAPORU.md ve git log `002eccb..`):
  derleme düzeltmesi, CSV kütüphane senkronu (1811 öğe) + kulüp armaları,
  crash rotaları + DB v17/v18 migration'ları, isResuming, Emre çift puanlama +
  tiebreaker + numaralandırma düzeltmeleri, kriter dialogunun kayıt yapması,
  kırık sistemlerin UI'dan çıkarılması, ~1000 satır ölü kod temizliği,
  ana thread IO düzeltmeleri, i18n tamamlama, 7 yeni regresyon testi,
  CLAUDE.md yeniden yazımı
- [2025-09-25] Liste yükleme ve Takım kartı crash sorunları
- [2025-09-22] Oylama ekranı kapsamlı redesign
- [2025-09-21] VS satırı buton sistemi
- [2025-09-19] Kriter değerlendirme sayfası yeni tasarım
- [2025-09-17] Oylama ekranı yeniden yapılandırma; Kriter sistemi implementasyonu
