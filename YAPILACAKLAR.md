# YAPILACAKLAR NOT DEFTERİ

Güncelleme: 2026-09-01 — doküman senkron denetimi (HIBRIT + EMRE_SIRALAMA bağlandıktan sonra).
Önceki tekilleştirme: 2026-07-25 (Faz 0-4 toparlama turu).
Kapsamlı denetim bulguları ve yapılan işlerin kaydı: **ANALIZ_RAPORU.md**

## 🔴 AÇIK MADDELER

### Cihazda doğrulama bekleyenler (2026-07-25 düzeltmeleri)
1. **Tablo Rötuşu**: sütun E→B taşıma, satır taşıyıp kaydetme, kayıt sonrası öğe adlarının bozulmaması
2. **Sihirbaz kısayolları**: liste adımında "Yeni Liste Oluştur" → oluştur → dönüşte otomatik seçim; kriter adımında aynı akış
3. **Image #6 oylama layout'u**: 6 katmanlı düzen, VS popup menüsü, yeşil/sarı/yeşil buton çubuğu — görsel/UX beğeni kontrolü

## 🆕 BUGÜN DOĞAN AÇIK İŞLER (2026-09-01 — iki yeni motor bağlandı)

2026-09-01'de iki sistem daha kullanıcı seçimine açıldı: **HIBRIT** (Hibrit
İsviçre — Kanıt Turlu, `ranking/HibritKanitSistemi.kt`, commit `680e461`) ve
**EMRE_SIRALAMA** (Emre Sıralama Sistemi, `ranking/EmreSiralamaSistemi.kt`,
commit `850c3fe`). İkisi de replay tabanlı: durum diskte tutulmaz, tamamlanmış
maçlardan yeniden hesaplanır — bu yüzden `resumeSession`'ın `else` dalı
(`loadNextMatch()`) onlara YETİYOR, ayrı resume dalı gerekmiyor. Ama
EMRE_CORRECT'e özel yazılmış üç yardımcı katman bu iki motora TAŞINMADI:

Y1. 🟠 **Erken bitirme (BİTİR) yalnız EMRE_CORRECT'te.**
    Buton `RankingScreen.kt`'te `if (method == "EMRE_CORRECT" && !uiState.isComplete)` ile
    kapılı; motor tarafında da `RankingViewModel.erkenBitir()` gövdesi
    `if (currentMethod != "EMRE_CORRECT") return@launch` diye sessizce dönüyor.
    Sonuç: HIBRIT ve EMRE_SIRALAMA'da kullanıcının turnuvayı yarıda bitirme
    yolu YOK — tek çıkış "Sıfırla" (her şeyi siler). Ölçüm bunu acil yapıyor:
    n=200'de HIBRIT ~1.900, EMRE_SIRALAMA 1.365 maç. Not: `erkenBitir()`
    gövdesinin yarım tur işleme adımı `emreState`e bağlı; replay motorlarında
    o adım gereksiz (tamamlanmamış maçları silip `completeRanking()` yeterli),
    yani genelleştirme büyük iş değil.

Y2. 🟠 **Bitirme kararının sayısal dayanağı (keskinlik raporu) iki yeni
    motorda üretilemiyor.** `EmreSystemCorrect.kesinlikRaporu` girdi
    olarak `EmreState` alıyor; HIBRIT/EMRE_SIRALAMA'da `uiState.emreState`
    null → dialog "Şu anki sıralamayla turnuva bitirilecek." kısa metnine
    düşer. Y1'i çözerken bu iki motor için de bir keskinlik ölçüsü tanımlanmalı
    (ikisi de üstünlük/kanıt ilişkisi tutuyor; komşuluk kanıtı oradan
    çıkarılabilir), yoksa kullanıcı körlemesine bitirir.

Y3. 🟡 **Canlı "Puan Durumu" iki yeni motorda yok.**
    `RankingViewModel.calculateCurrentStandings()` yalnız
    `EMRE_CORRECT` ve `LEAGUE`/`SWISS` dallarına sahip; başka yöntemde
    `currentStandings` HİÇ yazılmıyor. Şu an kullanıcı boş tablo görmüyor
    çünkü buton da `method == "LEAGUE" || method == "EMRE_CORRECT"` ile kapılı
    (`RankingScreen.kt`'te iki yerde: buton çubuğu ve VS menüsü) — yani bu bir çökme değil,
    eksik özellik. ⚠️ Butonu açan kişi ÖNCE dalı yazmalı; yoksa dialog boş açılır.

## 🔵 SIRADAKİ ÖZELLİKLER (kullanıcı istedi, sıraya alındı)

### S1. Sesli oylama — "bir / iki / sıfır" komutlarıyla maç sonucu
İstek (2026-08-29): iki takım eşleşmesinde mikrofona **"bir"** deyince 1. öğe,
**"iki"** deyince 2. öğe kazanır, **"sıfır"** deyince beraberlik.

Ön inceleme yapıldı, uygulanabilir:
- Android `SpeechRecognizer` + `RecognizerIntent`, `EXTRA_LANGUAGE = "tr-TR"`
- `androidx.activity.compose` zaten bağımlılıklarda var (izin isteği için gerekli)
- `RECORD_AUDIO` izni AndroidManifest'e EKLENMELİ (şu an yok)

Tasarım notları:
- Mikrofon **açma/kapama düğmesi** olmalı; sürekli dinleme pil yakar ve
  kullanıcı ne zaman dinlendiğini bilmeli
- Her sonuçtan sonra tanıyıcı yeniden başlatılmalı (tek atışlık çalışır)
- Sayılar bazen rakam olarak dönüyor: "bir"/"1", "iki"/"2", "sıfır"/"0"
  varyantlarının hepsi kabul edilmeli
- ⚠️ **Yanlış tetikleme riski**: "bir" Türkçede çok yaygın ("bir şey", "bir daha").
  Yalnız KISA ve TAM eşleşen söylemler kabul edilmeli, cümle içinde geçen
  "bir" oy saymamalı
- Ne duyulduğu ekranda gösterilmeli — kullanıcı yanlış anlaşılmayı görebilsin
- Ekrandan çıkınca tanıyıcı serbest bırakılmalı (`DisposableEffect`)
- Geri alma tuşu zaten var; sesli oylamada yanlış tetiklemenin telafisi o

Durum (2026-09-01 doğrulandı): henüz BAŞLANMADI — `AndroidManifest.xml`'de
`RECORD_AUDIO` izni yok (0 eşleşme), kodda `SpeechRecognizer` hiç geçmiyor
(0 eşleşme). Madde sırada duruyor, ön inceleme notları hâlâ geçerli.

## 🟡 TEKNİK BORÇ (ANALIZ_RAPORU.md Faz 0-4'ten artan)

8. ~~**LEAGUE oturum/persistence**~~ ✅ 2026-08-28: LEAGUE ve MERGE_SORT'a `createOrUpdateSession` eklendi (ikisinde de oturum yoktu; ekrana her giriş oynanmış maçları siliyordu). Kalan: `saveLeagueSettings` hiç çağrılmıyor, lig ayarları UI'ı yok
9. **RankingViewModel bölünmesi**: **2.100+ satır** (2026-09-01 ölçümü: 2.126; denetim anında 1.619 idi — iki yeni motorla büyüdü, dosya hâlâ değişiyor), çok sorumluluk; sistem başına strategy + SessionManager ayrıştırması
10. ~~**initializeRanking'deki sonsuz Flow.collect**~~ ✅ 2026-08-28: tek seferlik okumaya çevrildi. Aynı desen `ResultsViewModel`de iki yerde daha vardı, onlar da kapatıldı

11. **collectAsStateWithLifecycle** geçişi (2026-09-01 ölçümü: 24 `collectAsState()` çağrısının hiçbiri lifecycle-aware değil)
12. **Karar bekleyen kapalı özellikler**: ELIMINATION/FULL_ELIMINATION (tamamla ya da kodu sil), YouTube katmanı (bitir ya da 3 tablo+DAO+ekranı kaldır), SWISS kodunun tamamen silinmesi
13. **Ölü DAO metotları** (~60 adet, çoğu YouTube): kullanım kararı sonrası temizlik
~~14. **MERGE_SORT beraberlik koruması**~~ ✅ 2026-08-31 (`3547cf6`): bekçi `submitDrawResult` başına kondu (MERGE_SORT ise sessizce dönülüyor). Aynı bekçi daha önce de vardı, bir UI yenilemesinde kaybolmuştu; artık `IkiliKarsilastirmaKapsamliTest` davranışı sabitliyor.
15. **Room migration testleri**: cihazlı `MigrationTestHelper` hâlâ kurulmadı — `app/src/androidTest` dizini 2026-09-01'de de YOK; dışa aktarılmış şema JSON'ları yalnız **16/17/18** (1-15 aralığı yok, DB şu an v18). Kısmi telafi aynı gün geldi: paralel bir işçi oturumu STATİK zincir bekçisi yazdı (`app/src/test/.../RoomMigrationZinciriTest.kt` — zincir deliği, `addMigrations` unutulması, `fallbackToDestructiveMigration` sızması ve şema sapması için). Cihazlı testin yerini TUTMAZ; gerçek SQLite üzerinde geçiş denemesi hâlâ yapılmıyor.
16. **ListEditScreen rememberSaveable**: kaydedilmemiş tablo düzenlemeleri ekran döndürmede kayboluyor (kompleks tipler için Saver gerekir)
17. 🟠 **İçe aktarmada "ilk satır başlık mı?" sorulmalı** (2026-08-28, MOTOR TESTLERİ kıtası buldu)
    `CsvReader.parseText` ilk satırı başlık sayıyor. Sayısal listelerde düzeltildi (ilk hücre tam sayıysa veri sayılır), ama **sayısız iki sütunlu listelerde sessiz kayıp sürüyor**:
    ```
    Sezen Aksu,Firuze     ← başlık değil, VERİ. İlk hücre sayı değil, hâlâ yutuluyor.
    MFÖ,Ali Desidero
    ```
    Yapısal olarak belirsiz bir durum — hiçbir sezgi güvenilir çözmez; çözdüğünü iddia eden sezgi sessiz kaybı başka bir sessiz kayıpla değiştirir. Tek dürüst çözüm: içe aktarma ekranında sor, varsayılanı sezgiyle doldur.
    Test: `belgeleme_basliksizIkiSutunluListe_ilkSatirYUTULUYOR` (CsvReaderDeepTest) — bilinçli olarak yeşil, davranışı sabitliyor.

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
