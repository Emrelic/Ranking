# YAPILACAKLAR NOT DEFTERİ

Güncelleme: 2026-07-25 (Faz 0-4 toparlama turu sonrası tekilleştirildi).
Kapsamlı denetim bulguları ve yapılan işlerin kaydı: **ANALIZ_RAPORU.md**

## 🔴 AÇIK MADDELER

### Cihazda doğrulama bekleyenler (2026-07-25 düzeltmeleri)
1. **Tablo Rötuşu**: sütun E→B taşıma, satır taşıyıp kaydetme, kayıt sonrası öğe adlarının bozulmaması
2. **Sihirbaz kısayolları**: liste adımında "Yeni Liste Oluştur" → oluştur → dönüşte otomatik seçim; kriter adımında aynı akış
3. **Image #6 oylama layout'u**: 6 katmanlı düzen, VS popup menüsü, yeşil/sarı/yeşil buton çubuğu — görsel/UX beğeni kontrolü

## 🟡 TEKNİK BORÇ (ANALIZ_RAPORU.md Faz 0-4'ten artan)

8. ~~**LEAGUE oturum/persistence**~~ ✅ 2026-08-28: LEAGUE ve MERGE_SORT'a `createOrUpdateSession` eklendi (ikisinde de oturum yoktu; ekrana her giriş oynanmış maçları siliyordu). Kalan: `saveLeagueSettings` hiç çağrılmıyor, lig ayarları UI'ı yok
9. **RankingViewModel bölünmesi**: ~1500 satır, çok sorumluluk; sistem başına strategy + SessionManager ayrıştırması
10. ~~**initializeRanking'deki sonsuz Flow.collect**~~ ✅ 2026-08-28: tek seferlik okumaya çevrildi. Aynı desen `ResultsViewModel`de iki yerde daha vardı, onlar da kapatıldı

14. 🟠 **İçe aktarmada "ilk satır başlık mı?" sorulmalı** (2026-08-28, MOTOR TESTLERİ kıtası buldu)
    `CsvReader.parseText` ilk satırı başlık sayıyor. Sayısal listelerde düzeltildi (ilk hücre tam sayıysa veri sayılır), ama **sayısız iki sütunlu listelerde sessiz kayıp sürüyor**:
    ```
    Sezen Aksu,Firuze     ← başlık değil, VERİ. İlk hücre sayı değil, hâlâ yutuluyor.
    MFÖ,Ali Desidero
    ```
    Yapısal olarak belirsiz bir durum — hiçbir sezgi güvenilir çözmez; çözdüğünü iddia eden sezgi sessiz kaybı başka bir sessiz kayıpla değiştirir. Tek dürüst çözüm: içe aktarma ekranında sor, varsayılanı sezgiyle doldur.
    Test: `belgeleme_basliksizIkiSutunluListe_ilkSatirYUTULUYOR` (CsvReaderDeepTest) — bilinçli olarak yeşil, davranışı sabitliyor.
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
- **[2026-07-25] Image #6 oylama layout'u** (2025-09-23'ten beri açıktı):
  6 katmanlı sabit düzen (progress / mavi takım başlıkları / iki scroll
  penceresi / ekran ortasında buton çubuğu), BERABERLIK|VS|SKOR GİR|KRİTER
  yeşil/sarı/yeşil çubuk, VS popup menüsü (Duraklat/Sıfırla/Puan/Geri Al)
- **[2026-07-25] Sihirbaz kısayolları** (2025-09-18'den beri açıktı):
  liste ve kriter adımlarına "Yeni ... Oluştur" butonları; oluşturma
  ekranından savedStateHandle ile id dönüşü ve otomatik seçim; seçimlerin
  id'si rememberSaveable'da — kısayola gidip dönünce ve ekran döndürmede
  seçim kaybolmuyor
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
