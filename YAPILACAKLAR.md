# YAPILACAKLAR NOT DEFTERİ

Güncelleme: 2026-09-01 — doküman senkron denetimi (HIBRIT + EMRE_SIRALAMA bağlandıktan sonra).
Önceki tekilleştirme: 2026-07-25 (Faz 0-4 toparlama turu).
Kapsamlı denetim bulguları ve yapılan işlerin kaydı: **ANALIZ_RAPORU.md**

## 🔴 AÇIK MADDELER

### Cihazda doğrulama bekleyenler (2026-07-25 düzeltmeleri)
1. **Tablo Rötuşu**: sütun E→B taşıma, satır taşıyıp kaydetme, kayıt sonrası öğe adlarının bozulmaması
2. **Sihirbaz kısayolları**: liste adımında "Yeni Liste Oluştur" → oluştur → dönüşte otomatik seçim; kriter adımında aynı akış
3. **Image #6 oylama layout'u**: 6 katmanlı düzen, VS popup menüsü, yeşil/sarı/yeşil buton çubuğu — görsel/UX beğeni kontrolü

## 🆕 BUGÜN DOĞAN İŞLER VE DURUMLARI (2026-09-01 — iki yeni motor bağlandı)

2026-09-01'de iki sistem daha kullanıcı seçimine açıldı: **HIBRIT** (Hibrit
İsviçre — Kanıt Turlu, `ranking/HibritKanitSistemi.kt`, commit `680e461`) ve
**EMRE_SIRALAMA** (Emre Sıralama Sistemi, `ranking/EmreSiralamaSistemi.kt`,
commit `850c3fe`). İkisi de replay tabanlı: durum diskte tutulmaz, tamamlanmış
maçlardan yeniden hesaplanır — bu yüzden `resumeSession`'ın `else` dalı
(`loadNextMatch()`) onlara yetiyor, ayrı resume dalı gerekmiyor.

Bağlanma sırasında EMRE_CORRECT'e özel yazılmış üç yardımcı katmanın
taşınmadığı görüldü (Y1-Y3). **Üçü de aynı gün kapatıldı**; kayıt, cihazda
sınanacakları için duruyor.

- [x] **Y1 ✅ Erken bitirme (BİTİR) artık üç motorda.** Önceden buton da
  `RankingViewModel.erkenBitir()` gövdesi de `EMRE_CORRECT` dışındaki her
  yöntemde sessizce kapalıydı; HIBRIT (~1.900 maç) ve EMRE_SIRALAMA (1.365 maç)
  turnuvalarında kullanıcının yarıda bitirme yolu yoktu, tek çıkış "Sıfırla"ydı.
  Şimdi buton `method in listOf("EMRE_CORRECT", "HIBRIT", "EMRE_SIRALAMA")` ile
  çiziliyor (aynı `currentRound > ceil(log2 n)` şartıyla) ve `erkenBitir()`
  replay motorları için ayrı, daha basit yol izliyor: yarım turu ayrıca işlemeye
  gerek yok, oynanmamış maçlar silinip `completeRanking()` çağrılıyor.
  → Cihazda sınanacak: `oturumlar/CIHAZ-TEST-PROTOKOLU.md` 2.2.c ve 2.3.d.

- [x] **Y2 ✅ Bitirme kararının sayısal dayanağı yeni motorlarda da var.**
  `EmreSystemCorrect.kesinlikRaporu` girdi olarak `EmreState` aldığı için
  HIBRIT/EMRE_SIRALAMA'da rapor üretilemiyordu (dialog dayanaksız kısa metne
  düşüyordu). Her iki motora kendi `kesinlikYuzdesi(songs, completedMatches)`
  fonksiyonu yazıldı; sonuç `uiState.kesinlikYuzde` üzerinden bitirme
  dialogunda gösteriliyor ("komşu sıraların bu kadarı kanıtlı").
  → Cihazda sınanacak: protokol 2.2.c ve 2.3.d.

- [x] **Y3 ✅ Canlı "Puan Durumu" dört yöntemde.** `calculateCurrentStandings()`
  yalnız EMRE_CORRECT ve LEAGUE/SWISS dallarına sahipti; yeni motorlarda
  `currentStandings` hiç yazılmıyordu (buton da kapalı olduğu için kullanıcı boş
  tablo görmüyordu). Şimdi HIBRIT/EMRE_SIRALAMA için ayrı dal var: galibiyet,
  beraberlik ve oynanan maç sayımı maçlardan, **sıra motorun kendi
  sıralamasından** (`calculateResults`) geliyor — bu sistemlerde puan eşleştirme
  aracı değil, yalnız bilgilendirme. Buton `LEAGUE, EMRE_CORRECT, HIBRIT,
  EMRE_SIRALAMA` için çiziliyor.
  → Cihazda sınanacak: protokol 2.2.d ve 2.3.e.
  ⚠️ Kalan küçük tutarsızlık: **SWISS'in puan durumu hesaplanıyor ama butonu
  yok** (buton listesinde SWISS yok, VM dalı var). Karar gerekiyor: butona SWISS
  de eklensin mi, yoksa dal mı sadeleşsin.

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
12. **Karar bekleyen kapalı özellikler**: ELIMINATION/FULL_ELIMINATION (tamamla ya da kodu sil), YouTube katmanı (bitir ya da 3 tablo + DAO + ekranı kaldır). ⚠️ Bu maddedeki **"SWISS kodunun tamamen silinmesi" kararı GEÇERSİZ** (2026-09-01 doğrulandı): SWISS 2026-08-28'de yeni `ranking/SwissSystem.kt` motoruyla GERİ AÇILDI — sihirbazın sistem adımında 5. kart olarak seçilebiliyor (`NewTournamentScreen.systemTypes`), tekrarsız tam eşleştirme geri izlemeyle garanti ediliyor, bye adil rotasyonla dağıtılıyor, 59 testi var. Silinecek kod değil, **desteklenen yöntem**; cihaz sınavı `oturumlar/CIHAZ-TEST-PROTOKOLU.md` 2.5'te.
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
