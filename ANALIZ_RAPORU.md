# RANKING PRO — KAPSAMLI SİSTEM ANALİZ RAPORU

> **DURUM GÜNCELLEMESİ (2026-09-01):** Bu rapor 2026-07-25 denetiminin fotoğrafıdır.
> Hangi bulgunun kapandığı, hangisinin açık kaldığı ve iki yeni motorun (HIBRIT,
> EMRE_SIRALAMA) durumu için **Bölüm 11 — Güncelleme Eki**ne bakın.
>
> **DURUM GÜNCELLEMESİ (2026-07-25, aynı gün):** Bölüm 10'daki Faz 0-4 yol
> haritası UYGULANDI (commit'ler: `002eccb`..HEAD). Derleme + tüm birim
> testleri yeşil. Rapordaki bulgular denetim anının (HEAD `f70ea02`)
> fotoğrafıdır; çözülenler için git log'a, artakalan işler için
> YAPILACAKLAR.md "Teknik Borç" bölümüne bakın.

Tarih: 2026-07-25 · Branch: `ileri-tusu-asagida-crash-fix` · HEAD: `f70ea02`
Kapsam: Algoritma, veri (Room), UI/navigasyon, CSV kütüphanesi, dokümantasyon, git durumu.

---

## 0. GENEL DURUM ÖZETİ

| Alan | Durum |
|---|---|
| Derleme | 🔴 **KIRIK** — commit edilmemiş değişiklik derlenmiyor |
| MERGE_SORT (İkili Karşılaştırma) | 🟢 Sağlam, en iyi test edilmiş yöntem |
| DIRECT_SCORING | 🟢 Çalışır |
| EMRE_CORRECT (Geliştirilmiş İsviçre) | 🟡 Büyük ölçüde çalışır, kritik puan hatası var |
| LEAGUE | 🟡 Fikstür doğru, puanlama/ayarlar sorunlu |
| SWISS | 🔴 Birden çok kural ihlali, persistence çalışmıyor |
| ELIMINATION / FULL_ELIMINATION | 🔴 Kırık (yanlış sonuç + boş ekran) |
| Kriter değerlendirme | 🔴 Dialog açılıyor ama **hiçbir veri kaydetmiyor** |
| Veri senkronu (CSV kütüphanesi) | 🔴 4 farklı "gerçek": README 909 / assets 1.019 / kod 1.019 / kütüphane 1.811 |
| Dokümantasyon | 🔴 CLAUDE.md 2025-09-27'de donmuş, birçok iddiası kodla çelişiyor |

---

## 1. ACİL — DERLEME KIRIK

`./gradlew compileDebugKotlin` sonucu:
```
NewTournamentScreen.kt:497: Unresolved reference 'ExpandLess'
NewTournamentScreen.kt:497: Unresolved reference 'ExpandMore'
```
`Icons.Default.ExpandLess/ExpandMore` ikonları `material-icons-extended` paketinde; projede yalnızca çekirdek ikon seti var.
**Çözüm (birini seç):**
- a) `Icons.Default.KeyboardArrowUp` / `KeyboardArrowDown` kullan (bağımlılık eklemeden), veya
- b) `app/build.gradle.kts`'e `androidx.compose.material:material-icons-extended` ekle (APK boyutunu büyütür).

---

## 2. KESİN CRASH NOKTALARI

1. **`fixture/...` rotası NavHost'ta tanımlı değil** — `RankingNavigation.kt:193, 206` navigate ediyor; tetikleyen butonlar `RankingScreen.kt:135` ("Fikstür", LEAGUE/SWISS/EMRE/ELIMINATION'da görünür) ve `ResultsScreen.kt:55`. `FixtureScreen.kt` diye bir dosya yok. Butona basan herkes çöker.
2. **`tournament_results/...` rotası tanımsız** — `RankingNavigation.kt:144`; tetikleyen `TournamentRankingScreen.kt:86, 145`.
3. **DB migration crash (v14 ve öncesinden yükseltmede)** — `MIGRATION_14_15` `songs.viewCount` kolonunu **nullable** ekliyor (`RankingDatabase.kt:370`), ama entity non-null (`Song.kt:21`) ve şema `16.json` NOT NULL bekliyor. Eski sürümden güncelleyen kullanıcıda açılışta `IllegalStateException`. Çözüm: `MIGRATION_16_17` ile `songs` tablosunu yeniden oluştur-kopyala (SQLite ALTER COLUMN desteklemez).
4. **Boş girdiyle liste oluşturma** — `CreateListViewModel.kt:61, 67, 222, 244, 255` boş metin/CSV'de `IndexOutOfBoundsException`.
5. **Map üzerinde `!!`** — `ResultsViewModel.kt:140-141, 355-356` `tableEntries[match.songId1]!!`; silinen şarkının maçı kalırsa (FK yok, bkz. §5.3) NPE.
6. **Swiss sonsuz döngü riski** — `RankingViewModel.kt:474-479` ↔ `createNextSwissRound`: yeni maç üretilemezse sonsuz özyineleme (tüm puan grupları tek elemanlıysa gerçekleşir).

---

## 3. İŞLEVSİZ / SESSİZCE ÇALIŞMAYAN ANA ÖZELLİKLER

1. **Kriter değerlendirmesi hiçbir şey kaydetmiyor.** `RankingScreen.kt:235-253` `onSave` callback'i tamamen boş (sadece yorum satırları). Üstelik dialog `"team1_wins"/"team2_wins"` yayıyor (`CriteriaEvaluationDialog.kt:417, 457`), ekran `"team1"/"team2"` bekliyor — hiçbir branch'e düşmüyor. Kullanıcı 765 satırlık dialogda tüm kriterleri puanlıyor, **hiçbir veri yazılmıyor, maç sonucu işlenmiyor**.
2. **Devam eden turnuva her girişte sıfırlanıyor (VERİ KAYBI).** `isResuming` parametresi hiçbir navigate çağrısında verilmiyor (`RankingNavigation.kt:76, 131, 148`) → her zaman `forceNew=true` → `RankingViewModel.kt:129-141` eski session'ı kapatıp `clearMatches()` ile maçları siliyor.
3. **ELIMINATION / FULL_ELIMINATION seçilebilir ama ekran boş.** `RankingScreen.kt:391-403` tek satır yazıdan ibaret stub; maçlar DB'ye yazılıyor ama gösterilmiyor. SINGLE/DOUBLE_ELIMINATION için verilen "yarım özellik kırık deneyim yaratıyordu" kararı bu ikisine uygulanmamış.
4. **TournamentRankingScreen ("Aktif Turnuvalar → Devam Et") placeholder.** `"Takım A vs Takım B"` (`:213`), `"1. Placeholder Team"` (`:319`); EMRE dışındaki sistemlerde **İngilizce** hata mesajı (`TournamentRankingViewModel.kt:121`).
5. **Lig ayarları hiç kaydedilmiyor.** `saveLeagueSettings` (`RankingRepository.kt:248`) 0 çağrı → `getLeagueSettings` hep `null` → `useScores`, puan ayarları, çift devre vs. fiilen yok.
6. **Swiss persistence çalışmıyor.** `saveSwissState` yazıyor ama `loadSwissState` (`RankingRepository.kt:295`) hiç çağrılmıyor; ayrıca SWISS için `createOrUpdateSession` hiç çağrılmadığından `resumeSession`'ın SWISS dalı (`RankingViewModel.kt:1079-1146`) erişilemez. CLAUDE.md'nin "Swiss persistence tamamlandı" iddiası doğru değil.
7. **YouTube katmanı yazma-only + bozuk.** 39 DAO metodundan 36'sı ölü; `PlaylistTrack.trackId` her zaman 0 → FK ihlali → "oynatma listesi oluştur" her zaman hata (`YouTubeAnalysisViewModel.kt:150, 228`). `createRankingList` coroutine bitmeden `return` ettiği için daima `-1` dönüyor (`:222`).
8. **Emre "ilk sıralama" ekranı ölü.** `showInitialRanking` 11 yerde atanıyor, hepsi `false` → `InitialRankingContent.kt` hiç render edilmiyor.

---

## 4. ALGORİTMA HATALARI

### 4.1 EMRE_CORRECT (ana sistem)
- **KRİTİK — Son tur puanları iki kez işleniyor.** `submitMatchResult` → `updateEmreCorrectStateAfterMatch` turu kapatıp puanları işliyor; ardından akış `loadNextMatch()` (`RankingViewModel.kt:808`) → `createNextEmreRound` → aynı tur **ikinci kez** `processCorrectEmreResults`'a giriyor. Sıralama bozuk kaydedilebiliyor; "bitti" dedikten sonra hayalet tur oluşabiliyor.
- **Tiebreaker zinciri fiilen ölü.** `preRoundPosition` state'e hiç geri yazılmıyor (`EmreSystemCorrect.kt:128-134`); `processRoundResults`'a yalnızca **son turun** maçları veriliyor → head-to-head ve mağlubiyet sayısı hesapları yanlış/işlevsiz (`:694, 735-810`).
- **Alternating numbering ters.** Yön bayrağı seçimden sonra çevrildiği için TOP KEAT N numarasını, BOTTOM KEAT 1'i alıyor (`:286, 406, 418`) — CLAUDE.md şemasının tam tersi. `MatchDao.kt:15`'teki `matchNumber DESC` ile iki hata birbirini görsel olarak gizliyor olabilir; **birlikte** düzeltilmeli.
- **Bye kuralı üç yerde üç farklı.** CLAUDE.md "sadece en alttaki"; kod "bye geçmemiş en alttaki" (`:200-223`); herkes bye geçince fallback sürekli en alttakini seçiyor. Karar verilip hizalanmalı.
- **matchHistory tamamlanmamış maçları da sayıyor** (`:636-652`, `isCompleted` kontrolü dışında) → geçerli eşleşmeler gereksiz yasaklanıyor.
- **Eşleştirme motoru tam eşleştirmeyi kaçırabiliyor** (greedy + tek seviyeli backtrack, sezgisel çıkışlar `:258, 266`) → turnuva matematiksel olarak devam edebilecekken bitebiliyor.
- **Puan durumu tablosu bye puanını göstermiyor** (`RankingViewModel.kt:1385-1443` sadece maçlardan hesaplıyor).

### 4.2 SWISS
- Tek sayıda takımda bir takım **kaybolur** ve bye +1 verilmez (`RankingEngine.kt:441-462`).
- Puan grubu artıkları alt gruba inmiyor → tur başına maç sayısı eksik (`:475-496, 538-595`).
- Fallback **aynı iki takımı ikinci kez eşleştirebiliyor** (`:576-593`) — en kırmızı kural ihlali.
- 1. tur ile sonraki turlar iki farklı koddan geçiyor (`createSwissMatches` vs `createSwissMatchesAdvanced`).

### 4.3 ELIMINATION / FULL_ELIMINATION
- Grup dağılımı **iki farklı `shuffled()`** ile iki kez yapılıyor (`RankingEngine.kt:146` vs `:269-289`) → kimin hangi grupta olduğu maçlarla alakasız, sonuçlar fiilen **rastgele**. ⚠️ **2026-09-02: bu madde DARALDI — bkz. Bölüm 11.1** (ELIMINATION'da giderildi, FULL_ELIMINATION'da sürüyor).
- Knockout zinciri yok: turnuva çeyrek finalde durur (`:228-252`, `RankingViewModel.kt:931-966`); sonuç listesi eksik (`:370-434` tek kazanan ekliyor).
- FULL_ELIMINATION üçlü grup tespiti güvenilmez (`:1403-1429` tüm turların maçlarıyla çağrılıyor); takım hem elenen hem kalifiye listesine girebiliyor (`:825-873`).

### 4.4 Puan ölçeği tutarsızlığı
Aynı uygulamada üç farklı ölçek: Lig 2/1 (`RankingEngine.kt:95-125`), grup sıralaması 3/1 (`:291-312`), Swiss/Emre 1/0.5. CLAUDE.md yalnızca 1/0.5 tanımlıyor. Ligde girilen skorlar (`useScores`) hiçbir yerde kullanılmıyor, averaj tiebreaker'ı yok.

### 4.5 MERGE_SORT (sağlam, küçük notlar)
- Beraberlik "aday kaybetti" sayılıyor (`PairwiseComparisonSort.kt:100-104`); UI beraberlik butonunu gizliyor ama `submitDrawResult` hâlâ açık — kapatılmalı.

---

## 5. VERİ KATMANI SORUNLARI

1. **`EmrePairingSettings` `@Entity` ama `@Database` listesinde yok** (`EmrePairingSettings.kt:13`) → tablo hiç oluşmuyor; sınıfa 0 referans. Silinmeli (enum ayrı dosyaya).
2. **`youtube_tracks.videoId` UNIQUE tutarsızlığı** — migration'da UNIQUE (`RankingDatabase.kt:330`), entity'de değil → taze kurulumda tablo şişer, yükseltilmişte REPLACE olur.
3. **Eksik FK'ler → kalıcı yetim kayıtlar:** `matches.songId1/songId2/tournamentId` FK'siz; `deleteSong` (`RankingRepository.kt:89-97`) maçları silmiyor; turnuva silinince maçlar sonsuza dek kalıyor (`ActiveTournamentsViewModel.kt:37`). §2.5'teki NPE'nin kökü.
4. **`RankingResultDao` Flow ve Sync sürümleri FARKLI `ORDER BY`** (`:9` position ASC vs `:13` score DESC) → ekranlar arası sıralama tutarsızlığı.
5. **Ana thread IO:** `importPreparedList` asset okuma + CSV parse dispatcher'sız (`RankingRepository.kt:110-113`); `CreateListViewModel.kt:175` URI okuma dispatcher'sız + satır başına transaction (195 satır = 195 transaction). CSV'ler büyüyünce ANR riski.
6. **Kullanıcı CSV'si sağlam parser'dan geçmiyor.** RFC-4180 uyumlu `CsvReader` yalnızca hazır listelerde; kullanıcı importu naif `split()` ile (`CreateListViewModel.kt:203+`) — tırnak/virgül/BOM desteklenmiyor; isim sütunu kuralı da farklı (1. sütun vs 4. sütun).
7. **Boş/yanıltıcı migration'lar** (`MIGRATION_8_9`, `MIGRATION_13_14` — yorum "reset" diyor, gövde boş); migration testi ve 1-15 şema JSON'ları yok.
8. **Eksik indeksler:** `archives.method`, bileşik `(listId, rankingMethod)` (matches/ranking_results/voting_sessions/league_settings), `isCompleted`, `criterion_lists.isActive`.
9. **~70 ölü DAO metodu** (YouTube 36 + diğerleri, ayrıntı raporun ekinde); `swiss_states` write-only; `SongList.createdDate` ölü kolon; `getSongListByIdSync`/`getLeagueSettingsSync` "Sync" adlı ama birebir aynı suspend ikizler.

---

## 6. UI / NAVİGASYON SORUNLARI

1. **`new_tournament/{listId}` rotası ölü** — hiçbir yerden navigate edilmiyor; `listId` okunuyor ama ekrana geçirilmiyor (`RankingNavigation.kt:94-105`).
2. **`TournamentRoutingScreen` boş `else`** — turnuva bulunamazsa sonsuz "yükleniyor" (`TournamentRoutingScreen.kt:24-25`).
3. **Production'da sahte demo verisi:** `MatchingsList.kt:368-414` "AFGANİSTAN/ARNAVUTLUK" placeholder'ları; CSV'siz listede kullanıcı Afganistan nüfusu görüyor. `:117`'de kırmızı "startScoring() çağırılmadı mı?" debug metni kullanıcıya gösteriliyor.
4. **`rememberSaveable` hiç yok** — 5 adımlı turnuva sihirbazı (14 `remember`, `NewTournamentScreen.kt:41-58`) ve tablo editörü (`ListEditScreen.kt:45-53`) ekran döndürmede tüm durumu kaybediyor.
5. **`errorMessage` navigasyon kanalı olarak kullanılıyor** — `"REDIRECT_TO_RANKING_SCREEN:..."` (`TournamentRankingViewModel.kt:115`); kullanıcı bir an bu metni hata olarak görüyor.
6. **İç içe aynı-eksen scroll:** `NewTournamentScreen.kt:106` `verticalScroll` içinde 3 LazyColumn (`:289, 381, 589`); diff 600dp'ye büyüterek "İleri" butonuna erişimi zorlaştırıyor (branch adının kaynağı olan sorunla aynı aile).
7. **`RankingViewModel` god-object:** 1.619 satır, ~50 fonksiyon, 30 alanlı UiState, `_uiState` dışında 8 mutable alan; init tüm mantığıyla sonsuz `Flow.collect` içinde (`:152-215`) → liste her değiştiğinde maçlar silinip yeniden üretilebiliyor.
8. **i18n yarım:** `ui/screens/ranking/` alt paketinin tamamı + 8 ekran hiç taşınmamış (AboutScreen 24, MatchingsList 23, TournamentRankingScreen 22 hardcoded metin). Sistem adları **üç ayrı tabloda** (strings.xml, `getMethodTitle`, `NewTournamentScreen.kt:63-72`) tutarsız.
9. **`collectAsStateWithLifecycle` hiç kullanılmıyor** (22 çağrının hepsi `collectAsState`).
10. **31 `!!` kullanımı** — öncelikli riskliler: `ResultsViewModel` map lookup'ları, `RankingViewModel` `emreState!!` (`:674, 728, 1481`), dialog + StateFlow yarışları (`ArchiveScreen.kt:63`, `ListsScreen.kt:145`).
11. **MainActivity `hideSystemUI` aşırı agresif** — 5 lifecycle hook'undan çağrılıyor, deprecated API'ler, etkisiz `window.attributes` mutasyonu (`MainActivity.kt:62-113`); `Scaffold` inner padding yok sayılıyor (`:33`).

---

## 7. CSV KÜTÜPHANESİ VE İÇERİK

**İyi haber:** 31 CSV mekanik olarak kusursuz — 1.842 satırda 0 bozuk satır, 0 boş hücre, tutarlı başlıklar, temiz UTF-8/LF, kesintisiz numaralandırma (29 dahil).

**Sorunlar:**
1. 🔴 **Üç kopya senkron değil:** `liste_kutuphanesi` 1.811 öğe / `assets/hazir_listeler` 1.019 / `README.md` 909 diyor. `HazirListeler.kt`'deki `ogeSayisi` değerleri assets'e göre (örn. "Ülkeler 43" — gerçek 195; "ilk 54 elementi" — gerçek 118). Kopyalamayı yapan Gradle task'ı yok, tamamen manuel → sapma tekrarlayacak.
2. 🔴 **`16_avrupa_futbol_kulupleri.csv` görselleri yanlış:** 55 kulübün görseli kulüp amblemi değil **ülke bayrağı** (55 satırda 17 benzersiz URL; Real Madrid = Barcelona = Atlético = aynı İspanya bayrağı).
3. 🟡 `26_filmler.csv`'de 27/55 görsel tam boy Wikimedia orijinali (mobilde MB'larca indirme); `31_muzik_aletleri`'nde 12 adet 500px sapması.
4. 🟡 Sayısal sütunlarda `-` placeholder (134 hücre, 5 dosya) — sayısal sıralamada sorun çıkarabilir; README'de belgelenmemiş.
5. 🟡 `.gitattributes` yok → Git "LF→CRLF" uyarıları; CSV'lerin LF garantisi risk altında.

---

## 8. COMMIT EDİLMEMİŞ DEĞİŞİKLİKLER (29 dosya)

- **27 CSV (+792 öğe):** içerik temiz, commit'e hazır — ama assets kopyası + `HazirListeler.kt` sayıları + `README.md` **aynı commit'te** güncellenmeli, yoksa kopukluk büyüyor.
- **`strings.xml`:** yapısal olarak hazır; "alt **klişede**" → "alt klasman" yazım hatası ve iki bozuk cümle düzeltilmeli.
- **`NewTournamentScreen.kt`: bu haliyle COMMIT EDİLMEMELİ.**
  - Derlenmiyor (§1).
  - Başlık metni kartlarda 2-3 kez tekrarlanıyor (eski `split(" - ")` mantığı kaldırılmış ama string'ler güncellenmemiş).
  - ~17 hardcoded Türkçe metin eklenmiş — `9d00550` commit'inin strings.xml çalışmasına regresyon.
  - Varsayılan seçim hâlâ `"SWISS"` (`:44`) — "ÖNERİLİ" rozeti MERGE_SORT'ta ama açılışta 3. kart seçili.
  - "Recommended seçenek öne geçmiştir" gibi TR/EN karışık bozuk cümleler; `Modifier.outlineVariant` yanıltıcı isimli gereksiz sarmalayıcı; deprecated `Divider`.
  - Kırık ELIMINATION'a cazip tanıtım metni ekliyor (§3.3 ile çelişki).
- **Öneri: iki ayrı commit** — (1) veri: CSV + assets + HazirListeler.kt + README, (2) UI: düzeltilmiş NewTournamentScreen + strings.xml.

---

## 9. DOKÜMANTASYON ÇÜRÜMESİ

1. **CLAUDE.md 2025-09-27'de donmuş; 13 commit yansımamış.** MERGE_SORT, liste kütüphanesi, Hazır Listeler ekranı, RankingScreen'in 893 satıra bölünmesi, strings.xml taşıması dokümanda yok; verdiği satır numaraları geçersiz; ".reversed() ile sıralama", "Swiss persistence tamamlandı", "TMB butonları" iddiaları kodla çelişiyor.
2. **YAPILACAKLAR.md:** 10 açık madde duruyor (Tablo Rötuşu drag-drop + Kaydet, liste/kriter ekleme kısayolları, navigation/state entegrasyonu, Image #6 layout); yapı bozuk (çift başlık, çift arşiv kaydı).
3. **ARCHIVED_NOTES.md** 5 var olmayan dosyaya işaret ediyor; CLAUDE.md ve KRITERLER_ISTISARE.md'nin yönlendirmeleri boşa çıkıyor.
4. **PROMPT_GUNLUGU.md** 10 aydır dokunulmamış, kendi kurallarını ihlal ediyor — ya arşivlenmeli ya kural silinmeli.
5. **EKRAN_GORUNTULERI.md Image #6** "implementasyon gerekli" derken CLAUDE.md aynı işi "tamamlandı" ilan ediyor — ikisinden biri yanlış.

---

## 10. YOL HARİTASI — DERLEYİP TOPARLAMAK İÇİN ÖNERİLEN SIRA

### Faz 0 — Bugün (derleme + senkron)
1. `NewTournamentScreen.kt:497` ikon hatasını düzelt (KeyboardArrowUp/Down) → build yeşile dönsün.
2. NewTournamentScreen diff'ini toparla: başlık çiftlenmesi, hardcoded string'ler → strings.xml, varsayılan seçim → MERGE_SORT, "klişe" → "klasman", `outlineVariant` sarmalayıcısını sil.
3. CSV senkronu: `liste_kutuphanesi` → `assets/hazir_listeler` kopyala; `HazirListeler.kt` sayı ve açıklamalarını güncelle; `README.md` tablosunu yenile. (İsteğe bağlı: Gradle Copy task'ı ile kalıcı çözüm.)
4. `16_avrupa_futbol_kulupleri.csv` bayrak görsellerine karar ver (amblem bul / Görsel sütununu kaldır); `26_filmler` tam boy URL'leri 330px thumb'a çevir.
5. `.gitattributes` ekle (`*.csv text eol=lf`).
6. İki ayrı commit: veri + UI.

### Faz 1 — Crash'ler ve veri kaybı (1-2 gün)
7. `fixture/` ve `tournament_results/` rotaları: ya ekranları yaz ya butonları kaldır (hızlı çözüm: kaldır).
8. `MIGRATION_16_17`: `songs.viewCount` NOT NULL düzeltmesi + `videoId` unique index.
9. `isResuming` parametresini navigasyona bağla — her girişte turnuva sıfırlanması dursun.
10. `CreateListViewModel` boş girdi guard'ları; `ResultsViewModel` `!!` → güvenli erişim.
11. Emre çift puanlama: tur kapanışını tek yola indir (`submitMatchResult`'ta `loadNextMatch` çağrısını koşulla).
12. Swiss sonsuz döngü guard'ı.

### Faz 2 — Özellik tamamlama / budama kararları (1 hafta)
13. **Kriter dialog'u:** `onSave` implementasyonu + string değerlerini hizala (`team1_wins` ↔ `team1`) + `CriterionScore` kaydı.
14. **Karar: SWISS** — ya düzelt (bye, float, rematch yasağı, persistence) ya da EMRE_CORRECT varken UI'dan kaldır. *(Öneri: kaldır — EMRE_CORRECT zaten geliştirilmiş İsviçre.)*
15. **Karar: ELIMINATION/FULL_ELIMINATION** — ya tamamla (tek shuffle + DB'ye grup kaydı + knockout zinciri + ekran) ya SINGLE/DOUBLE gibi UI'dan çıkar.
16. **Karar: YouTube katmanı** — ya bitir (trackId düzelt, okuma yolu ekle) ya 3 tablo + 3 DAO + ekranı kaldır.
17. Emre tiebreaker'ı canlandır (`preRoundPosition` + tüm maçları geçir) ve numbering/DESC ikilisini birlikte düzelt.
18. Lig: `saveLeagueSettings` bağla, puan ölçeğini tek standarda indir, averaj tiebreaker.
19. TournamentRankingScreen: ya gerçek veriye bağla ya rotayı doğrudan RankingScreen'e yönlendir (redirect hack'i yerine).

### Faz 3 — Temizlik ve sağlamlaştırma (arka plan işi)
20. Ölü kod temizliği: RankingEngine ~15 fonksiyon, TournamentTestProtocol, `EmrePairingSettings`, ~70 DAO metodu, `new_tournament/{listId}` rotası, `showInitialRanking`/InitialRankingContent, MatchingsList placeholder verileri + debug metinleri.
21. i18n tamamlama (8 ekran + ranking alt paketi) ve sistem adlarını tek kaynağa indirme.
22. Ana thread IO düzeltmeleri (`importPreparedList`, `CreateListViewModel`); kullanıcı CSV'sini `CsvReader`'dan geçir.
23. FK'ler + eksik indeksler için migration; `deleteSong`'a maç temizliği.
24. `rememberSaveable` geçişi (sihirbaz + tablo editörü); `collectAsStateWithLifecycle`.
25. RankingViewModel'i parçala (sistem başına strategy + SessionManager).

### Faz 4 — Test ve dokümantasyon
26. Test boşlukları: RankingViewModel (özellikle çift puanlama regresyonu), Swiss/League/Elimination, Room migration testleri (1-15 şema JSON'ları).
27. CLAUDE.md'yi koda göre yeniden yaz; YAPILACAKLAR.md'yi tekilleştir; ARCHIVED_NOTES ve PROMPT_GUNLUGU'nu ya diriltip ya emekli et.

---

*Bu rapor 4 paralel denetim ajanının (algoritma, veri, UI, içerik/doküman) bulgularının birleştirilmiş halidir. Satır numaraları HEAD `f70ea02` + çalışma kopyasına göredir.*

---

## 11. GÜNCELLEME EKİ — 2026-09-01 (doküman senkron denetimi)

Bu ek, raporun gövdesini YENİDEN YAZMAZ; gövde 2026-07-25'teki denetim anının
fotoğrafı olarak kalır. Aşağıdaki maddeler, o fotoğraftaki iddiaların bugünkü
kod karşısındaki durumudur. Her satır kod okunarak doğrulandı (grep + dosya
okuma); "kapandı" denen hiçbir madde varsayımla işaretlenmedi.

### 11.1 Yanlışlanmış ya da daralması gereken iddialar

| Rapordaki madde | Bugünkü durum (2026-09-01 doğrulaması) |
|---|---|
| §0 + §1 "Derleme 🔴 KIRIK — `ExpandLess/ExpandMore` çözümlenmiyor" | Kapandı: bu iki ikona kod tabanında **0 referans** kaldı. |
| §2.1 `fixture/...` rotası tanımsız → butona basan çöker | Kapandı: `fixture/` metnine **0 referans**. |
| §2.2 `tournament_results/...` rotası tanımsız | Kapandı: `tournament_results` metnine **0 referans**. |
| §2.3 DB migration crash (v14 öncesi) | Kapandı: şema **v18**, migration zinciri eksiksiz (fallback yok). |
| §3.1 "Kriter değerlendirmesi hiçbir şey kaydetmiyor" | Kapandı: `RankingViewModel.saveCriteriaScores` `CriterionScore` kayıtlarını `criterionScoreDao().insertCriterionScores` ile yazıyor. |
| §3.2 "`isResuming` hiçbir navigate çağrısında verilmiyor" | Kapandı: rota `ranking/{listId}/{method}?...&isResuming={isResuming}` ve "Devam Et" `isResuming=true` ile gidiyor (`RankingNavigation.kt`). |
| §4.5 "MERGE_SORT'ta `submitDrawResult` hâlâ açık" | Kapandı: `submitDrawResult` başında MERGE_SORT bekçisi var (commit `3547cf6`), regresyon testi `IkiliKarsilastirmaKapsamliTest`. |
| §5.1 `EmrePairingSettings` @Database listesinde yok | Kapandı: dosya silinmiş. |
| §6.3 "MatchingsList'te AFGANİSTAN/ARNAVUTLUK demo verisi + debug metni" | Kapandı: bu metinlere **0 referans**. (Ama `TournamentRankingScreen`'de `"1. Placeholder Team"` DURUYOR — §3.4 kısmen açık.) |
| §4.3 birinci madde: "Grup dağılımı iki farklı `shuffled()` ile iki kez yapılıyor → sonuçlar fiilen rastgele" | **KISMEN geçersiz — madde silinmemeli, DARALTILMALI (2026-09-02).** ELIMINATION'da giderildi: hem `createEliminationMatches` hem `getGroupSongs` artık `sortedBy { it.id }` (`RankingEngine.kt:192` ve `:329`), gerekçesi kodda yazılı, aynı girdi aynı fikstürü veriyor. FULL_ELIMINATION'da DURUYOR: `createAdvancedPreEliminationMatches` hâlâ `shuffled()` (`:930`). Doğru hâli: "ELIMINATION'da giderildi; FULL_ELIMINATION'da sürüyor". |

### 11.2 Ölçümü değişen, hâlâ açık maddeler

- §6.7 **RankingViewModel god-object**: denetimde 1.619 satırdı, bugün **2.126**
  (iki yeni motorun bağlanmasıyla büyüdü). Bölme işi ertelendikçe pahalılaşıyor.
- §6.9 **`collectAsStateWithLifecycle`**: denetimde 22 çağrının hiçbiri
  lifecycle-aware değildi; bugün **24 çağrı, hâlâ 0** lifecycle-aware.
- §5.7 / §10 Faz 4 **Room migration testleri**: `app/src/androidTest` dizini
  hâlâ **yok**; dışa aktarılmış şema JSON'ları yalnız 16/17/18 (1-15 yok).
  Kısmi telafi: aynı gün STATİK bir zincir bekçisi yazıldı
  (`RoomMigrationZinciriTest.kt`, JVM) — cihazlı testin yerini tutmaz.
  Birim testi tarafı ise büyüdü
  (`app/src/test/java/com/example/ranking/`: commit'li **26** dosya + aynı gün
  paralel oturumların eklediği 13 yeni sınav dosyası = 39).
- §7 **CSV kütüphanesi senkronu**: bu denetim turunda BİLEREK ölçülmedi —
  aynı gün başka bir işçi oturumu (`HAZIR-LISTELER-GOREV.md`) o alanda
  çalışıyor; çakışmamak için sayılar oraya bırakıldı.

- §4.3'ün DİĞER iki maddesi (knockout zinciri yok · takım hem elenen hem
  kalifiye listesine girebiliyor) 2026-09-02'de doğrulandı ve sayıya bağlandı:
  eleme denetimi işçisinin ölçümü `oturumlar/ELEME-DENETIM-RAPOR.md`'de
  (n=12'de 12 öğeden yalnız 9'u sonuç alıyor; kazanan ∩ kaybeden kesişimi boş
  değil). Bu iki madde AÇIK. *(Ölçümü ben yapmadım; kodla doğruladığım tek şey
  determinizm iddiasıydı — 11.1'deki satır.)*
- 🔴 **YENİ CANLI BULGU (2026-09-02) — SWISS'in 1. TURU eski motordan geliyor ve
  tek takım sayısında bir takımı sessizce atlıyor.** Bu bölüme önce "denetlenmeli,
  iddia değil" diye yan gözlem olarak girmişti; eleme denetimi işçisi izini sürdü,
  ben de kaynaktan doğruladım. Üç parça:
  1. `createSwissMatchesAdvanced` (`RankingEngine.kt:565`) **ÖLÜ** — tek çağıranı
     `createSwissMatchesWithState` (`:500`), onun da canlı çağıranı yok (kaynak
     ağacında yalnız `RankingViewModel.kt:764`'teki YORUM ve `SwissSystem.kt`
     başlığındaki atıf). §10 Faz 3 temizliğine girer.
  2. `createSwissMatches` (`:505`) **CANLI**: `RankingViewModel.initializeSwiss`
     (`:519`) doğrudan `RankingEngine.createSwissMatches(songs, 1, emptyList())`
     çağırıyor; 2. ve sonraki turlar ise `createNextSwissRound` üzerinden yeni
     `SwissSystem`'den geliyor. Yani **bir SWISS turnuvasının 1. turu ESKİ, geri
     kalanı YENİ motordan**. §4.2'nin eski motorda kusur diye saydığı "1. tur ile
     sonraki turlar iki farklı koddan geçiyor" deseni, motor değişmesine rağmen
     duruyor — SWISS 2026-08-28'de UI'ya geri açıldığı için bu artık canlı yol.
  3. O canlı yolda **bye kusuru** var: `half = n / 2`, döngü `0 until half` ve
     eşleşme `i` ile `i + half` arasında — kullanılan indeksler `0..2*half-1`.
     n TEK ise **son indeks (n-1) hiç kullanılmaz**: o takım 1. turda ne maç
     oynar, ne bye kaydı alır, ne puan. Bu projede bye = 1 puandır; burada bye
     yok, sessiz atlama var. (Aritmetik tek yönlü olduğu için kod okumasıyla
     kesin; **testi yok** — SWISS bu oturumun görev alanı değil, ana koda
     dokunulmadı.)
  ⚠️ **Bu kusur birim sınavlarından KAÇAR:** `ESKI-MOTORLAR-SINAV-RAPOR.md`'nin
  bye adaleti ölçümü (n=9/11/15, max−min ≤ 1) `SwissSystem` üzerinde yapıldı,
  oysa 1. tur SwissSystem'den gelmiyor. Doğru ölçüm yolu `initializeSwiss` → 1.
  tur → tek n. Cihaz sınavında da yakalanır: `CIHAZ-TEST-PROTOKOLU.md` 2.5.c.
  🔺 **ŞİDDET YÜKSELTMESİ (2026-09-02, ikinci işçi doğrulaması + kaynak kontrolü):**
  bu gizli/yarım bir özellikte değil, **kullanıcının bugün seçebildiği bir
  yöntemde**. SWISS `NewTournamentScreen.systemTypes` listesinde 5. kart olarak
  duruyor. Üstelik hemen üstündeki kod yorumu tam bu kusuru "düzeltildi" ilan
  ediyor: "Eski yolda bye yoktu (tek takım sessizce turdan düşüyordu) ... yeni
  motorda ... bye adil rotasyonla dağıtılıyor. 59 test geçiyor." Yorumun
  düzeldi dediği kusur 1. turda hâlâ canlı; 59 test onu görmüyor çünkü hepsi
  `SwissSystem`'i besliyor, uygulamanın giriş noktası ise `RankingEngine`'e
  gidiyor. CLAUDE.md'nin aktif yöntem tablosundaki "🟢 yeni motorla geri açıldı"
  satırı da aynı sebeple giriş noktasıyla ayrışmış (o dosya bu oturumun alanı
  değil — düzeltme sahibinde).
  Ek: aynı hatalı 1. tur bloğu ÖLÜ yoldaki `createSwissMatchesAdvanced` içinde
  de birebir duruyor (kaynakta doğrulandı) — temizlik sırasında ikisi birden
  gitmeli. Düşen öğe `shuffled()` yüzünden her koşumda farklıdır: yazılacak
  test kimlik üzerinden değil **sayı** üzerinden kurulmalı, yoksa yanıp söner.
  🧪 **ÖLÇÜT UYARISI (bir işçinin kendi önerisini geri çekmesiyle çıktı):**
  "1. turda maç sayısı (n-1)/2 mi?" ölçütü bu kusuru **YAKALAMAZ**. Tam sayı
  bölmesi yüzünden n tekken `n/2` zaten `(n-1)/2` ediyor (n=5→2, n=9→4,
  n=15→7), yani maç sayısı DOĞRU çıkıyor ve kusur görünmüyor. Yakalayan tek
  ölçüt **katılan öğe sayısı** (n yerine n-1). Test de cihaz sınavı da bunun
  üzerine kurulmalı.
  📎 **Aynı satırdan çıkan iki yan bulgu** (işçi ölçümü, kaynakta doğrulandı):
  ① SWISS 1. turu **deterministik değil** — eski yol `shuffled()` ile başlıyor,
  aynı liste iki kez başlatılınca farklı fikstür ve farklı düşen öğe çıkıyor.
  ② **Aynı turnuvada iki matchNumber rejimi var:** eski yol 1. turda
  `matchNumber` hiç atamıyor (Match varsayılanı 0, hepsi 0 kalıyor), 2. turdan
  itibaren `SwissSystem` 1..N atıyor (bye kaydı 0). CLAUDE.md oylama sırasını
  "matchNumber ASC" diye tanımladığı için 1. turun oylama sırası fiilen
  tanımsız — hangi maçın önce sorulacağı sorgunun ikincil sırasına kalıyor.
  💡 Rapora geçen ders: **bir kusurun "düzeltildi" diye yazılması, düzeltmenin
  ÇAĞRILDIĞINI göstermez.** `NewTournamentScreen`'deki yorum yanlış değil,
  EKSİK: yeni motor gerçekten bye dağıtıyor, ama turnuvanın 1. turu ona uğramıyor.

### 11.3 Raporun hiç tanımadığı yeni alan: iki yeni motor

Rapor 2026-07-25'te yazıldığında aktif yöntemler MERGE_SORT / EMRE_CORRECT /
LEAGUE / DIRECT_SCORING idi. 2026-09-01'de ikisi daha kullanıcı seçimine açıldı:

| Yöntem | Dosya | Bağlandığı commit | Ölçüm (n=200, tohum 777) |
|---|---|---|---|
| HIBRIT (Hibrit İsviçre — Kanıt Turlu) | `ranking/HibritKanitSistemi.kt` (200 satır) | `680e461` | ~1.900 maç, garantili tam sıralama |
| EMRE_SIRALAMA (Emre Sıralama Sistemi) | `ranking/EmreSiralamaSistemi.kt` (197 satır) | `850c3fe` | 18 tur / 1.365 maç, sıfır hata |

Bu iki motor **bu raporun denetiminden geçmedi**. Bağlanma sırasında görülen
boşluklar YAPILACAKLAR.md'nin "🆕 BUGÜN DOĞAN AÇIK İŞLER" bölümünde Y1-Y3
olarak kayıtlı: erken bitirme (BİTİR) yok, keskinlik raporu üretilemiyor,
canlı puan durumu dalı yazılmamış. Üçü de EMRE_CORRECT'e özel yazılmış
yardımcı katmanların taşınmamasından kaynaklanıyor — motorların kendi
matematiği değil, çevre katman eksikliği.


### 11.4 Yeni bulgu (2026-09-02): EMRE_CORRECT'in bitiş kuralı DURUMA değil, ÜRETİLEN EŞLEŞMEYE bakıyor

Rapor gövdesi §4.1'de Emre'nin "eşleştirme motoru tam eşleştirmeyi kaçırabiliyor →
turnuva matematiksel olarak devam edebilecekken bitebiliyor" diye bir madde
taşıyor. 2026-09-02'de bunun **mekanizması** bulundu ve kaynakta doğrulandı:

- `EmreSystemCorrect.createHybridPairingSystem` önce açgözlü/geri izlemeli
  motoru koşturup bir eşleştirme (`pairingResult.AEG`) üretiyor, sonra
  `candidates` listesini **doğrudan o AEG'den** türetiyor.
- `analyzeTournamentContinuation(candidates, currentRound)` ise yalnız bu
  listeye bakıp `samePointCount = candidates.count { team1.points == team2.points }`
  sayıyor ve `canContinue = samePointCount > 0` diyor (1. tur ayrık).
- Yani soru "**durumda** oynanmamış aynı puanlı çift var mı?" değil,
  "**bu turda kurulan eşleşmelerin içinde** aynı puanlı çift var mı?". Aynı
  puanlı takımlar eşleştirme sırasında farklı puanlı rakiplere harcanırsa,
  sahada kanıt üretecek eşleşme dururken turnuva "aynı puanlı eşleşme kalmadı"
  diyerek kapanıyor.

İşçi ölçümü (EMRE_CORRECT değişmezler sınavı, commit `0102080` — ölçüm bana ait
değil, kod doğrulaması bana ait): n=8..16 aralığında **n=13 dışında hepsi** bu
kapıdan kapanıyor ve kapanış anında oynanmamış aynı puanlı çift duruyor —
n=15'te 11 çift, n=12'de 8, n=16'da 7, n=14'te 5, n=9'da 4, n=8 ve n=11'de 3,
n=10'da 2. Tek temiz bitiş n=13 (0 çift, %100 keskinlik). Giriş sırasına
duyarlılık 5 tohumla n=12'de: 4/9/8/8/4 tur ve %45/%100/%81/%100/%36 kanıt.

**Bu, CLAUDE.md'nin Emre bölümündeki iki iddiayı da geçersiz kılıyor:**
"Çift sayıda sapma yok" (n=12'de 8 çift beklerken kapanıyor) ve erken bitişin
"kusur değil, kuralın sonucu" olduğu savunması — motor kuralın kendisini değil,
kuralın üretilen eşleşme kümesindeki gölgesini uyguluyor. (CLAUDE.md bu
oturumun alanı değil; düzeltme dosyanın sahibinde. Aynı paragraftaki
"n=3 → 1 tur" ayrışması için YAPILACAKLAR madde 18.)

**Öneri (karar koordinatörün):** bitiş kararını aday kümesinden duruma taşımak —
"oynanmamış aynı puanlı çift var mı?" diye state üzerinden sormak; ya da
Hibrit'teki kanıt eşiği yaklaşımını kullanmak (`kesinlikRaporu().genelYuzde`
düşükken kanıt turu zorlamak). Altyapı motorun içinde zaten var.

*Ek sonu — 2026-09-01. Gövdedeki satır numaraları hâlâ HEAD `f70ea02`'ye
göredir ve büyük ölçüde kaymıştır; madde bulmak için satır numarasına değil
sembol adına (fonksiyon/sınıf) güvenin.*
