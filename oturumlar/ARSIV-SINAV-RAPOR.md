# ARŞİV/SERİALİZER BÜTÜNLÜK SINAVI — rapor (ranking-f0)

Görev: koordinatör `ranking-7d` (kendini `ranking-0c` diye tanıtıyor).
Commit: `e8294db`. Yalnız YENİ test dosyaları eklendi, hiçbir üretim
dosyasına dokunulmadı.

## SAYIM (kendi ölçümüm)

| dosya | test | geçti | kırık |
|---|---|---|---|
| `SwissJsonSerializerRoundTripTest.kt` | 15 | 15 | 0 |
| `ArsivSozlesmeTest.kt` | 8 | 8 | 0 |
| **toplam** | **23** | **23** | **0** |

Koşum: `./gradlew testDebugUnitTest --tests "com.example.ranking.SwissJsonSerializerRoundTripTest" --tests "com.example.ranking.ArsivSozlesmeTest"`
→ BUILD SUCCESSFUL, 1m46s. XML sonuçları (`app/build/test-results/testDebugUnitTest/TEST-*.xml`)
`tests="15" ... failures="0" errors="0"` ve `tests="8" ... failures="0" errors="0"`.

⚠️ Tam paket (306+ test) AYRICA koşulmadı — gradle kilidi şu an kıt kaynak
(FİLO KURALI), ve bu iki dosya yalnız EKLEME, hiçbir üretim dosyasına
dokunmadığı için mevcut testleri etkilemesi yapısal olarak mümkün değil.

## KAPSAM 1 — SwissStateSerializer + SwissFixtureSerializer (utils/)

Test edilen: `serializeStandings`/`deserializeStandings`,
`serializePairingHistory`/`deserializePairingHistory`,
`serializeRoundHistory`/`deserializeRoundHistory`,
`serializeFixtureData`/`deserializeFixtureData`,
`serializeLiveStandings`/`deserializeLiveStandings`.
Boş veri, tipik veri, 64 takımlı büyük fikstür (2016 ikili), negatif/ondalıklı
puan, Türkçe+özel karakterli şarkı adı (`RankingEntry.songName`) — hepsi
birebir gidiş-dönüş yapıyor.

### 🔴 Bulunan GERÇEK kayıplar (kusur değil, "ulaşılamayan yol"da belgelendi)

Bu iki serileştirici **hâlâ canlı**: `RankingRepository.saveSwissState` /
`loadSwissState` (satır 320-343) ve `saveSwissFixture` (satır 425-434),
`RankingViewModel.kt` içinde method="SWISS" turnuvalarında çağrılıyor
(satır 498, 786, 1495, 1855 civarı). Ama CLAUDE.md bu yöntemi UI'dan
gizlenmiş işaretliyor — normal kullanıcı SWISS'i seçemiyor, dolayısıyla bu
kod yolu şu an ULAŞILAMAZ. Yöntem yeniden açılırsa aşağıdakiler gerçek veri
kaybına dönüşür:

1. `SwissStateSerializer.serializeRoundHistory`/`deserializeRoundHistory`:
   yalnız `id, songId1, songId2, winnerId, round` yazılıyor/okunuyor.
   Round-trip sonrası **kaybolan alanlar**: `listId` → 0'a düşer (kod yorumu
   "Will be set by the caller" diyor, kasıtlı ama round-trip'i bozan bir
   varsayım), `score1`/`score2` → null, `groupId` → null, `matchNumber` → 0,
   `tournamentId` → null, `createdAt` → deserialize ANINDAKİ
   `System.currentTimeMillis()`'e sıfırlanıyor (orijinal değer DEĞİL).
   Test: `belgeleme_roundHistory_matchIn_listId_skor_grup_turnuvaId_alanlariKAYBOLUR`.

2. `SwissFixtureSerializer.serializeMatch`/`deserializeMatch`:
   `matchNumber` alanı HİÇ ele alınmıyor — round-trip sonrası her zaman 0.
   Test: `belgeleme_fixtureData_matchNumberSerializeMatchTarafindanYazilmiyor`.
   (Bu serileştirici Match'in DİĞER tüm alanlarını — listId, rankingMethod,
   skor, round, groupId, isCompleted, createdAt — birebir koruyor; yalnız
   matchNumber eksik.)

**Önerilen düzeltme** (karar koordinatörde): `serializeRoundHistory`'ye
listId/score/groupId/matchNumber/tournamentId eklemek, `serializeMatch`'e
matchNumber eklemek. SWISS yöntemi zaten gizli olduğu için ACİL değil, ama
"gizli kod = güvenli kod" varsayımı yanlış olur diye kayda geçirildi.

## KAPSAM 2 — Archive sözleşmesi (ResultsViewModel ↔ ArchiveViewModel)

`ResultsViewModel.kt` (yazma: `saveArchive`, satır ~302-317) ve
`ArchiveViewModel.kt` (okuma: `selectArchive`, satır 96-135) SADECE OKUNDU;
bu ikisi `suspend` + `AndroidViewModel` içinde olduğu için JVM testinden
DOĞRUDAN çağrılamıyor — Gson adımları testte BİREBİR KOPYALANDI (ESKI-
MOTORLAR-SINAV-GOREV.md'nin "SADECE OKU, kopyalama testi yaz" deseniyle
aynı yöntem), ama veri TİPLERİ gerçek production sınıflarından alındı.

### 🟡 Gözlem — dört veri sınıfı çiftlenmiş, sessiz ayrışmaya açık
`ArchivableResult`/`ArchiveResult`, `ArchivableMatch`/`ArchiveMatch`,
`ArchivableLeagueSettings`/`ArchiveLeagueSettings`, ve `LeagueTableEntry`
(HER İKİ dosyada da AYRI tanımlı) — dört çift, sekiz sınıf, birebir aynı
alan adı/tipi. Bugün kusur YOK (round-trip tam çalışıyor), ama bu bir kopya-
kod deseni: biri değişip diğeri değişmezse Gson **hatasız ama YANLIŞ**
(eksik alan → null/varsayılan) veri üretir, çökme olmadan. Testler gerçek
sınıfları kullandığı için böyle bir gelecekteki ayrışmayı KIRMIZI test
olarak yakalayacak — bu, testin asıl kazandırdığı regresyon güvencesi.

### ✅ Doğrulanan (pozisyon/puan kayıpsızlığı — görevin asıl istediği)
- 3 ve 100 öğelik `finalResults` listelerinde **pozisyon sırası ve score
  değeri** (ondalık dahil, `1e-9` toleransla) birebir korunuyor.
- `matchResults`: null skor/winnerId (oynanmamış maç) ve dolu skor/kazanan
  ayrı ayrı test edildi, ikisi de doğru.
- `leagueTable`: yalnız LEAGUE yönteminde dolu geliyor; `null` olduğunda
  okuma tarafı **boş liste** döndürüyor (null DEĞİL) — bu davranış testle
  sabitlendi, ArchiveScreen bu yüzden `.isEmpty()` güvenle kullanabilir.
- `leagueSettings`: null/dolu iki durum da doğru.
- Türkçe karakter + özel karakter (tire, tırnak, apostrof, yeni satır)
  `songName`/`artist`/`album` alanlarında bozulmadan geri geliyor.
- `Archive.finalResults`/`matchResults` alanlarının GERÇEKTEN geçerli JSON
  metni olduğu ayrıca `org.json` ile çapraz doğrulandı (iki farklı JSON
  kütüphanesiyle).

## KAPSANAMAYAN (dürüst liste)
- Tam test paketi (306+) bu oturumda AYRICA koşulmadı (yalnız yeni 23 test
  koşuldu) — gradle kilidi kıt kaynak, üretim koduna dokunulmadığı için
  riski yapısal olarak sıfıra yakın görüyorum ama ÖLÇMEDİM.
- `ResultsViewModel.saveArchive` ve `ArchiveViewModel.selectArchive`
  fonksiyonlarının KENDİSİ (coroutine/DB/Application bağımlılığı yüzünden)
  çağrılmadı — yalnız içindeki Gson mantığı kopyalanarak sınandı. Fonksiyon
  gövdesi değişip Gson çağrıları farklılaşırsa bu testler ONU YAKALAMAZ.
- `RankingRepository.saveSwissState`/`loadSwissState`/`saveSwissFixture`
  bizzat çağrılmadı (DB/Room gerektiriyor); yalnız çağırdıkları
  serileştiricilerin JSON sözleşmesi test edildi.
- Room migration/DB düzeyinde Archive tablosu şeması test edilmedi (bu
  görevin kapsamı değildi — "arşivlenen sonuç geri okununca kayıpsız mı"
  sorusu JSON katmanında cevaplandı).

## EK NOT — ranking-07'nin bulgusu, kendim OKUYARAK doğruladım (ÖLÇÜLDÜ)

`ranking-7d` kapandıktan sonra `ranking-07` (başka bir işçi) bir bulgu
paylaştı; kod okumasıyla DOĞRULADIM (B10: aktaran hatanın sahibi olur, bu
yüzden kendi ölçümüm olarak ayrı işaretliyorum). Kendi kapsamımın (JSON
gidiş-dönüşü) DIŞINDA olduğu için DÜZELTMEDİM, yalnız kayda geçiriyorum —
SWISS motor/entegrasyon sınavının (ESKI-MOTORLAR-SINAV-GOREV.md kapsamı)
konusu.

**Doğrulanan mekanizma:**
1. `RankingViewModel.initializeSwiss()` (satır 502-523), satır 519:
   `RankingEngine.createSwissMatches(songs, 1, emptyList())` — yalnız 1. tur
   için.
2. `RankingEngine.createSwissMatches` (satır 505-527), 1. tur dalı:
   satır 509 `shuffledSongs = songs.shuffled()` (ORTAK.md'nin yasakladığı
   rastgelelik, replay/determinizmi kırar); satır 510
   `half = shuffledSongs.size / 2`; döngü (satır 512-524)
   `for (i in 0 until half) { if (i + half < shuffledSongs.size) {...} }`.
   **TEK sayılı `songs.size`'da son indeks hiç seçilmiyor**: örn. size=9 →
   half=4, kullanılan indeksler {0..7}, indeks 8 hiçbir Match'e girmiyor,
   bye kaydı da yok — o öğe turdan SESSİZCE düşüyor (ne maç ne puan).
   Bu satırlarda `Match(...)` çağrısına `matchNumber` hiç verilmiyor →
   Match.kt'deki varsayılan (0) kalıyor, yani 1. turun TÜM maçları
   matchNumber=0.
3. Round 2+ ise FARKLI bir motordan geliyor: `RankingViewModel` satır
   776-777 `SwissSystem.computeState` + `SwissSystem.createNextRound`
   çağırıyor; `SwissSystem.kt` satır 232 `matchNumber = index + 1` atıyor.
   Sonuç hesaplaması da (satır 945) yine YENİ `SwissSystem.calculateResults`
   kullanıyor.

**Doğrulanan sonuç:** aynı SWISS turnuvasında 1. tur eski/kırık motordan
(rastgele, bye'sız, matchNumber=0), 2+ turlar yeni motordan (deterministik,
bye'lı, matchNumber=1..N) geliyor — üç farklı kod yolu (eski üretim / yeni
üretim / yeni sonuç hesabı) aynı turnuvada karışık çalışıyor.

🔴 **ÖNEMLİ — SWISS CANLI, ama CLAUDE.md'nin gizli sandığı YANLIŞ (kendi
hatam, düzeltiyorum):** İlk yazımda "CLAUDE.md hâlâ SWISS'i UI'dan gizli
sanıyor" dedim — bu YANLIŞTI, `ranking-07` düzeltti (teşekkürler). CLAUDE.md
GÜNCEL: SWISS artık aktif yöntemler tablosunda ("🟢 2026-08-28'de YENİ
motorla geri açıldı ... 59 test") ve "UI'dan gizlenmiş" listesinde DEĞİL.
Ben session başındaki ESKİ bir bağlam kopyasına bakmışım, dosyayı o anda
diskten yeniden okumadım — B10/B4 dersi burada bana da işledi.

Asıl belge sorunu FARKLI (ranking-07'nin ANALIZ_RAPORU 11.2'de yazdığı
gibi): CLAUDE.md SWISS'i "gizli" SANMIYOR, tersine **fazla iyimser tarif
ediyor** — "adil bye rotasyonu, 59 test" ifadesi yalnız 2+ turlar
(`SwissSystem`) için doğru; 1. tur o motora hiç uğramıyor. Yani doğru
ifade: "belge SWISS'i sağlam ilan ediyor, oysa giriş noktası (round 1) o
motora bağlı değil" — "belge SWISS'i gizli sanıyor" DEĞİL.

`NewTournamentScreen.kt:431-441` `systemTypes` listesinde `"SWISS"` FİİLEN
VAR — kullanıcı BUGÜN turnuva sihirbazından "İsviçre"yi SEÇEBİLİYOR.
`RankingViewModel.kt:122`de de SWISS aktif yöntemler kümesinde. Bu kısım
DOĞRULANDI ve DEĞİŞMİYOR.

Bu, ranking-07/8e/diğer oturumların "üretim tarafı kusuru" dediği şeyi
**canlı kullanıcı etkisine** çeviriyor: SWISS'i seçen ve tek sayılı bir
liste kullanan gerçek bir kullanıcı, 1. turda bir öğeyi sessizce kaybediyor
(o öğenin hiç maçı/puanı olmuyor) ve o öğe **sonuç sıralamasında hak
etmediği hâlde en altta** çıkıyor (sonuç motoru `SwissSystem.calculateResults`
tüm maç geçmişini — eksik 1. tur dahil — okuyor, `RankingViewModel.kt:945`).
Ayrıca `shuffled()` yüzünden hangi öğenin düşeceği her turnuva başlatmada
DEĞİŞİYOR (determinizm yok).

Bu bulgu benim kapsamımın (arşiv JSON) dışında ama CİDDİYETİ yüzünden
BURADA vurgulanıyor: bu bir "gizli/ulaşılamayan kod" kusuru DEĞİL, bugün
gerçek kullanıcıyı etkileyebilecek bir yanlış-sonuç kusuru. Düzeltme:
`RankingViewModel.initializeSwiss()` (satır 519) round 1 için de
`RankingEngine.createSwissMatches` yerine `SwissSystem.computeState` +
`createNextRound` kullanmalı (round 2+ ile aynı motor). Karar ve uygulama
koordinatörde/SWISS motor sınavı kapsamında.

## GRADLE KİLİDİ NOTU
Bu görev sırasında filo çapında bir kilit sistemi (`oturumlar/GRADLE-KURALI.md`)
kuruldu ve birkaç geçiş sancısı yaşandı (retroaktif kilit denemem geçersiz
sayıldı, bir ara kilitsiz koşum iptal edildi — ayrıntı sohbet geçmişinde).
Bu koşum kilit protokolüne TAM uyularak alındı ve hemen bırakıldı.
