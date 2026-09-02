# ESKİ MOTORLARIN UZMAN SINAVI — RAPOR

**İşçi:** OPUS HAZIR KITA 102 (`ranking-8e`) · **Şartname:** `oturumlar/ESKI-MOTORLAR-SINAV-GOREV.md`
**Kapsam:** Lig (`RankingEngine`) · Düz İsviçre (`SwissSystem`) · Direkt Puanlama (`RankingEngine`)
**Ana koda DOKUNULMADI.** Yalnız `app/src/test/` altına 4 yeni dosya eklendi.

> ✅ **Koşum yapıldı: 66 test, 66 geçti, 0 kaldı, 0 atlandı** (§ 3 SAYILAR).
> Bilerek bırakılmış kırmızı test YOK.
>
> ✅ **DALGA KAPANDI.** Bulunan **12 kusurun 9'u düzeltildi**
> (B0 · B1a · B1b · B2 · B3 · B4 · B8 · B9 · B10), her biri için regresyon
> bekçisi bırakıldı. Düzeltmeleri koordinatörler yaptı (`ranking-7d` →
> commit `0182c99`, sonra `ranking-d4`); bu sınav bulguyu, ölçümü, hangi
> davranışın kanonik olması gerektiğini, düzeltmenin biçimini ve düzeltme
> sonrası doğrulamayı sağladı.
>
> 🟡 **BACKLOG'A GİDEN 3 KUSUR:** B5 (puanlanmamış ≠ 0 puan) · B6 (mükerrer
> kayıt çift sayılıyor) · B7 (`calculateLeagueResults` `rankingMethod`
> süzmüyor). Üçü de mevcut davranışı kilitleyen YEŞİL testlerle belgeli;
> düzeltilirse o testler kırmızıya dönüp kendilerini güncelletir.

---

## 0. Eklenen dosyalar

| Dosya | Başlık | Test |
|---|---|---|
| `EskiMotorLigSinaviTest.kt` | § A Lig | 20 |
| `EskiMotorSwissSinaviTest.kt` | § B Düz İsviçre + § ⑦ uygulama akışı | 21 |
| `EskiMotorDirektPuanlamaSinaviTest.kt` | § C Direkt Puanlama | 15 |
| `EskiMotorCaprazTutarlilikTest.kt` | § D Çapraz tutarlılık | 10 |
| **TOPLAM** | | **66** |

Mevcut testler ÖNCE okundu (`LeagueEngineDeepTest` 27, `SwissSystemTest` 16,
`RankingEngineYetimMacTest` 22, `LeagueScoringTest` 3 test) ve kapsanan hiçbir
vaka tekrarlanmadı; her dosyanın başında hangi boşluğun doldurulduğu yazılı.
`createDirectScoringResults` için tüm test paketinde **tek bir test bile yoktu**
(arandı: 0 sonuç) — ilk kapsam bu sınavla kuruldu.

---

## 1. BULGULAR — önem sırasıyla

### ✅ B0 — [DÜZELTİLDİ] Yetim maç: motor puan yazmıyordu, canlı tablo yazıyordu

Bir maçın karşı tarafı silinmişse (öğe listeden çıkarılmış, maç kaydı duruyor —
bu projede gerçek senaryo, motorların tamamında ayrı yetim maç koruması var):

| | Davranış |
|---|---|
| `RankingEngine.calculateLeagueResults` | `points[songId2]` null → maçı **tümüyle atlar**, ayakta kalan takım puan almaz |
| `RankingViewModel.calculateCurrentStandings` LEAGUE dalı | `songs` üzerinde döner, karşı tarafın var olup olmadığına **bakmaz** → ayakta kalan takıma galibiyet + 3 puan yazar |

Ölçülen vaka (5 takım; 3 numaralı takım silinmiş 77 ile oynamış):
motor `[1,4,5,2,3]` (3 → 0 puan) · canlı tablo `[1,3,4,5,2]` (3 → 3 puan, 2. sırada).
Yetim maç sayısı arttıkça fark büyüyor (`ayrismaOlcumu_yetimMacSayisiArttikcaFarkBuyuyor`).

**Hüküm: motor haklı** — silinmiş bir rakibe karşı alınan galibiyet
doğrulanamaz, puan yazmak ayakta kalan takımı şişirir. `SwissSystem` ve
`EmreSystemCorrect` de yetim maçı sessizce atlıyordu; ViewModel bu üçlüyle
tutarsız kalan tek yerdi. Koordinatör düzeltmeyi uyguladı
(`RankingViewModel.kt:2018-2019`), LEAGUE/SWISS dalına ve aynı gün eklenen
HIBRIT/EMRE_SIRALAMA puan dalına:
```kotlin
val rakipId = if (birinci) match.songId2 else match.songId1
if (songs.none { it.id == rakipId }) return@forEach
```
Bye kaydı `songId1 == songId2` olduğu için kontrolden etkilenmiyor — bu da ayrı
bir testle kilitlendi (`duzeltmeSonrasi_byeKaydiYetimKontrolundenGecmeli`),
çünkü kontrol yanlış yazılsaydı İsviçre'de bye puanı sessizce kaybolurdu.

Regresyon bekçileri: `duzeltmeSonrasi_yetimMac_ikiHesapAyniSira`,
`duzeltmeSonrasi_cokSayidaYetimMac_ikiHesapAyniSira` (1-4 yetim maçla),
`duzeltmeSonrasi_byeKaydiYetimKontrolundenGecmeli`.

### ✅ B1 — [DÜZELTİLDİ] Lig sıralaması iki ayrı formülle hesaplanıyordu

Sınavın ilk turunda iki ayrışma ölçüldü, koordinatör aynı oturumda düzeltti:

**B1a — tek taraflı skor.** `score1=5, score2=null` girilmiş maçta motor averaja
hiç yazmıyordu, ViewModel boş tarafı 0 sayıp ±5 averaj üretiyordu.
Ölçülen: motor `[1,2,3,4]` · canlı tablo `[1,3,4,2]`.
**Hüküm: motor haklı** — `null`'ı 0 saymak veri uyduruyor; "5-?" maçı "5-0" olup
gerçekte olmayan +5 averaj doğuruyor. ViewModel motorun kuralına çekildi
(`RankingViewModel.kt:2015`).

**B1b — eşit puanda galibiyet bozucusu.** 3/1/0 ölçeğinde 1 galibiyet ile
3 beraberlik aynı puanı verir. ViewModel galibiyet sayısıyla ayırıyordu, motorun
zinciri atılan golde bitip `songs` listesinin geliş sırasına düşüyordu.
Ölçülen: motor `[4,1,2,3,5]` · canlı tablo `[4,2,1,3,5]`.
**Hüküm: ViewModel haklı** — motorunki toplam sıralı değildi, sıra bir kurala
değil listenin geliş sırasına bağlıydı. Motorun zinciri
`… → galibiyet → song.id` yapıldı (`RankingEngine.kt:152-158`).

Regresyon bekçileri: `EskiMotorCaprazTutarlilikTest.duzeltmeSonrasi_*` (3 test)
— düzeltme geri alınırsa bunlar kırmızıya döner.

### ✅ B2 — [DÜZELTİLDİ, Lig'de] Motorun sıralaması `songs` listesinin SIRASINA bağlıydı

Eşitlik zinciri tükendiğinde `sortedWith` kararlılığına düşüyordu; aynı maçlarla
`songs` ters verilince sonuç da tersine dönüyordu (`[1,2,3,4]` → `[4,3,2,1]`).
B1b düzeltmesindeki `.thenBy { it.id }` bunu da kapattı.
Bekçi: `EskiMotorLigSinaviTest.esitlik_girdiSirasindanBagimsiz_regresyonBekcisi`.

**Direkt Puanlamada aynı kusur B3 olarak ayrıca bulundu ve o da düzeltildi.**

### ✅ B10 — [DÜZELTİLDİ] SWISS turnuvasının 1. TURU `SwissSystem`'den gelmiyordu

> Bu bulgu `ranking-fd`'den geldi ve **bu raporun ilk turundaki bir körlüğü
> açığa çıkardı** (§ 4'e bakınız). Kaynak kodundan bağımsız doğrulandı.

Düz İsviçre turnuvası **iki farklı motorla yürüyordu**:

| Tur | Çağrı (düzeltmeden ÖNCE) | Motor |
|---|---|---|
| **1** | `RankingViewModel.initializeSwiss` (:519) → `RankingEngine.createSwissMatches(songs, 1, emptyList())` | **ESKİ** (menüden gizlenmiş) |
| **2+** | `RankingViewModel.createNextSwissRound` → `SwissSystem.computeState` / `createNextRound` | YENİ |

Eski yolun 1. tur dalı (`RankingEngine.kt:509`):
```kotlin
val half = shuffledSongs.size / 2
for (i in 0 until half) { if (i + half < size) pair(songs[i], songs[i + half]) }
```
Kullanılan indeksler `0 .. 2*half-1`. **n TEK ise son indeks (n−1) hiç
kullanılmıyor:** o öğe 1. turda ne maça giriyor, ne bye kaydı alıyor, ne puan.
Projede bye 1 puan ettiği için (CLAUDE.md) o öğe hem maçtan hem puandan mahrum.

**Ölçüm — gerçek akış simülasyonu** (düzeltmeden ÖNCE: 1. tur eski motor +
2+ turlar `SwissSystem`):

| n | tur | bye sayısı | 1. turda düşen öğe |
|---|---|---|---|
| 5 | 3 | **2** | 1 |
| 9 | 4 | **3** | 9 |
| 15 | 4 | **3** | 3 |

Tek takım sayısında **her turda bir bye olmalıyken bye sayısı hep tur sayısından
bir eksik** — eksik olan tur, eski motordan gelen 1. tur.

**Bu tuzağın neden sinsi olduğu:** maç SAYISI doğru çıkıyor. Tam sayı bölmesi
yüzünden n tekken `size/2` zaten `(n−1)/2` ediyor (n=5→2, n=9→4, n=15→7). Yani
maç sayısını denetleyen bir test kusuru YAKALAYAMAZ; yakalayan tek ölçüt
**katılan öğe sayısı** (n yerine n−1).

Aynı satırdan çıkan iki yan bulgu (ikisi de aynı düzeltmeyle kapandı):
* **Determinizm ihlali:** eski yol `songs.shuffled()` kullanıyor — ORTAK.md'nin
  replay kuralına açıkça aykırı ("`shuffled()`, `Random`,
  `System.currentTimeMillis()` kullanma"). Düşen öğe her koşumda değişiyor.
* **`matchNumber` rejim ayrışması:** 1. turun tüm maçları `matchNumber = 0`,
  2. turdan itibaren `SwissSystem` kural 7 gereği 1..N atıyor. Aynı turnuvada
  iki numaralandırma rejimi; CLAUDE.md oylama sırasını `matchNumber ASC` olarak
  tanımladığı için bu sıralamayı da etkileyebilirdi. (Bu boyutu ölçen test,
  sınadığı fonksiyon ana koddan silinince kaldırıldı — bkz. § 5 Temizlik borcu;
  yerini `uygulamaAkisi_ilkTurArtikSwissSystemden_regresyonBekcisi` içindeki
  `matchNumber` 1..N denetimi aldı.)
* **Kusur kopyalanmıştı:** aynı hatalı blok `createSwissMatchesAdvanced`
  (`RankingEngine.kt:571-590`) içinde de birebir duruyordu. Ölü kod olduğu için
  ikisi birden silindi (bkz. § 5 Temizlik borcu).

**Bulgunun zinciri (kimlik kaydı):** `ranking-fd` (OPUS HAZIR KITA 104, eleme
denetimi) kusuru yan yoldan kod okumasıyla buldu ve **"testi yok" statüsüyle**
teslim etti (`ELEME-DENETIM-RAPOR.md` § 11) → `ranking-07` doküman kaydına
aldı → **üçüncü bir oturum** testi yazdı ve commit `dd4fe79` ile depoya soktu.
O dosyanın yazarı oturum adıyla bilinmiyor (bütün commit'ler tek git
kimliğinden atılıyor). Bu raporun ilk yazımında kapsama yanlışlıkla
`ranking-fd`'ye atfedilmişti; kendisi düzeltti, burada düzeltildi.

**Kapsam ayrımı:** kusurun kendisi (düşen öğe, bye kaydının yokluğu,
`shuffled()` etkisi, çift sayıda kusurun olmaması, ikinci giriş noktası)
`SwissBirinciTurGirisNoktasiTest.kt` dosyasında kapsandı (commit `dd4fe79`,
02.09.2026 02:52 — yani **depoda, garanti altında**); ORTAK.md'nin "kapsananı
tekrarlama" kuralı gereği bu sınavda tekrarlanmadı. Bu sınav yalnız orada OLMAYAN iki
ölçümü ekledi: `matchNumber` rejim ayrışması ve gerçek akış bye dağılımı.

**DÜZELTİLDİ.** `initializeSwiss` artık `createNextSwissRound(1)` üzerinden
`SwissSystem`i kullanıyor (`RankingViewModel.kt:515`); eski `createSwissMatches`
1. tur dalı ölü kod oldu. Üç kusur (düşen öğe · `shuffled()` · `matchNumber=0`)
tek değişiklikle kapandı.

**Düzeltme sonrası ölçüm** (`uygulamaAkisi_gercekAkistaHerTurdaBye_regresyonBekcisi`):

| n | tur | bye sayısı | önce | sonra |
|---|---|---|---|---|
| 5 | 3 | 3 | 2 ❌ | **3 ✅** |
| 9 | 4 | 4 | 3 ❌ | **4 ✅** |
| 15 | 4 | 4 | 3 ❌ | **4 ✅** |

Artık tek takım sayısında **her turda bir bye** var ve her turda her öğe bir
kayıtta görünüyor (test bunu da denetliyor).

Bekçiler: `uygulamaAkisi_ilkTurArtikSwissSystemden_regresyonBekcisi`
(n=5,8,9,15 — hiçbir öğe düşmüyor · tek sayıda bye üretiliyor · `matchNumber`
1..N · iki çağrı aynı sonucu veriyor, yani `shuffled()` gitti) ve
`uygulamaAkisi_gercekAkistaHerTurdaBye_regresyonBekcisi`.
Eski ölü dalın kusuru `eskiMotorunOluIlkTurDali_matchNumberAtamiyor_belgelenmisKusur`
ile kayıt altında — biri o dalı yeniden bağlarsa kusurun ne olduğu okunur.

⚠️ **`NewTournamentScreen.kt:431-436` yorumu bu kusuru "düzeltildi" diye geçmiş
zamana koyuyor** ("Eski yolda bye yoktu (tek takım sessizce turdan düşüyordu)…
Yeni motorda bye adil rotasyonla dağıtılıyor"). Yorum yanlış değil, **eksik**:
yeni motor gerçekten bye dağıtıyor ama turnuvanın 1. turu ona uğramıyor.
Düzeltme yapılınca o yorum da güncellenmeli.

### ✅ B3 — [DÜZELTİLDİ] Direkt Puanlamada eşitlik bozucu YOKTU (girdi sırasına bağlıydı)

`createDirectScoringResults` yalnız `sortedByDescending { it.score }`
yapıyordu; eşit puanlarda sıra `songs` listesinin geliş sırasına düşüyordu ve
`songs` ters verilince sonuç da tersine dönüyordu (`[1,2,3,4]` → `[4,3,2,1]`).
Lig'de aynı kusur B2'de kapatılmıştı, burada kalmıştı.
**Düzeltildi:** `sortedWith(compareByDescending<RankingResult> { it.score }.thenBy { it.songId })`
(`RankingEngine.kt:29-31`).
Bekçi: `EskiMotorDirektPuanlamaSinaviTest.esitPuan_girdiSirasindanBagimsiz_regresyonBekcisi`.

### ✅ B4 — [DÜZELTİLDİ] Direkt Puanlamada `NaN` puan BİRİNCİ oluyordu

Motor puanı doğrulamıyordu. Kotlin'in `Double` karşılaştırıcısı NaN'ı en büyük
saydığı için NaN puanlı öğe 1. sıraya çıkıyordu — sessiz ve yanlış bir
birincilik.
**Düzeltildi:** `scores[song.id]?.takeIf { it.isFinite() } ?: 0.0`
(`RankingEngine.kt:17`). Artık `NaN` ve `±Infinity` 0.0 sayılıyor;
`Double.MAX_VALUE` sonlu olduğu için korunuyor.
Bekçiler: `aralikDisi_nanPuanSifirSayiliyor_regresyonBekcisi`,
`aralikDisi_sonsuzDegerlerSifirSayiliyor_regresyonBekcisi`.

### 🟡 B5 — Direkt Puanlamada "puanlanmamış" ile "0 puan" ayırt edilmiyor

`scores[id] ?: 0.0` yüzünden hiç değerlendirilmemiş öğe 0 sayılıyor ve NEGATİF
puan almış öğenin ÜSTÜNE çıkıyor.
Gösteren test: `...puanlanmamisOge_negatifPuanlininUstunde_belgelenmisKusur` (YEŞİL).
Bugünkü UI 0-100 kullanıyorsa zararsız; aralık negatife açılırsa kusur.

### 🟡 B6 — Mükerrer maç kaydı sessizce çift sayılıyor (her iki motorda)

Aynı eşleşmenin iki satırı varsa:
* **Lig:** puan ve averaj iki kez sayılıyor — bir galibiyet 3 yerine **6 puan**.
* **İsviçre:** puan ve `played` iki kez; `opponentIds` bir `Set` olduğu için
  **tekrar eşleşme yasağı BOZULMUYOR** (kırmızı çizgi güvende).
* **İsviçre bye:** mükerrer bye kaydında `byeCount` 2 oluyor → bye rotasyonu o
  takımı haksız yere atlar.
Çökme yok, uyarı yok. 32 takımlık mükerrer sette de çökmüyor (ölçüldü).
Gösteren testler: `EskiMotorLigSinaviTest.mukerrerKayit_*` (3),
`EskiMotorSwissSinaviTest.bozukVeri_mukerrerKayit_*`, `...mukerrerBye_*`.
**Öneri:** kayıt katmanında benzersizlik kısıtı (`listId+method+tournamentId+ikili`)
— motorda tekilleştirme yerine kaynağı kapatmak daha ucuz.

### 🟡 B7 — `calculateLeagueResults` `rankingMethod` SÜZMÜYOR

Yalnız `isCompleted` süzülüyor; kendisine verilen SWISS/EMRE maçı da 3/1/0 ile
sayılıyor. Bugün zararsız (tek çağıran `completeRanking` maçları zaten yönteme
göre çekiyor) ama motor kendini savunmuyor — **`SwissSystem.computeState` ise
süzüyor** (`bozukVeri_yabanciYontemMaciSayilmiyor` ile kilitlendi); iki motor
aynı konuda farklı davranıyor.
Gösteren test: `...yontemSuzgeci_ligMotoruYabanciYontemiDeSayiyor_belgelenmisKusur` (YEŞİL).

### ✅ B8 — [DÜZELTİLDİ] Yabancı `winnerId` puan vermiyor ama averajı YÜRÜTÜYORDU (Lig)

Üçüncü bir takımın id'si `winnerId`e yazılmışsa puan uydurulmuyordu (bu zaten
korunuyordu) **ama skorlar averaja yazılmaya devam ediyordu** — bozuk maç puanı
değil SIRALAMAYI etkiliyordu. Ölçüldü: 3 takımda bozuk `5-0` maçı sırayı
`[1,3,2]` yapıyordu.

**Düzeltildi — İKİ KATMANDA birden.** Bu ayrım kritikti: yalnız motora
uygulansaydı B1a ayrışmasının birebir aynısı yeni bir vaka olarak geri gelirdi
(motor maçı atlar, canlı tablo skoru averaja yazmaya devam ederdi). Uyarı
düzeltme yapılmadan önce iletildi ve kabul edildi:
* motor `RankingEngine.calculateLeagueResults:133` — maç tümüyle atlanıyor
* `RankingViewModel:2026-2028` (LEAGUE/SWISS) ve `:1972-1974`
  (HIBRIT/EMRE_SIRALAMA) — aynı kural, `played`/`lost` de sayılmıyor

Bekçiler: `yabanciKazanan_macTumuyleAtlaniyor_regresyonBekcisi`,
`uyusuyor_yabanciKazananVarken_skorlu` (SKORLU vaka — skorsuz bir vaka bu
düzeltmeyi sınayamaz, fark averajda doğuyor) ve
`yabanciKazanan_kontrolu_beraberligiYemiyor` (kontrol `winnerId == null`'ı
yerse bütün beraberlikler sessizce puansız kalırdı).

İsviçre'deki karşılığı **düzeltilmedi ve kasıtlı:** `SwissSystem`de bozuk maçta
puan/galibiyet uydurulmuyor ama `played` artıyor **ve ikili tekrar-eşleşme
geçmişine yazılıyor** — yani bozuk bir maç o çiftin bir daha eşleşmesini kalıcı
olarak engelliyor (`bozukVeri_yabanciKazanan_*` ile belgeli). Bu, kırmızı
çizgiyi (aynı çift iki kez eşleşmez) koruyan yönde hata yapıyor; bilerek
bırakıldı.

### ✅ B9 — [DÜZELTİLDİ] Lig fikstürü `matchNumber` ATAMIYORDU

`createLeagueMatches` hiç `matchNumber` yazmıyordu; ligin bütün maçları
`matchNumber = 0` doğuyordu. ORTAK.md "matchNumber: oylama sırası — 0 BIRAKMA"
diyor, `SwissSystem` kural 7 gereği 1..N atıyor. Oylama sırasını kurtaran tek
şey `MatchDao` sorgusunun son anahtarı `id ASC` idi.

**Düzeltildi:** tur içi 1..N (`RankingEngine.kt:76`); çift devrede rövanş maçı
orijinalinin tur içi numarasını koruyor (`:99`).
**Ek kazanç:** `RankingViewModel.sonucDuzenlenebilirMi`nin LEAGUE dalı
`o.round == m.round && o.matchNumber > m.matchNumber` karşılaştırmasını
yapıyordu; tüm numaralar 0 olduğu için bu koşul hiçbir zaman doğru olmuyordu.
Artık gerçekten çalışıyor.

Bekçiler: `matchNumber_turIcinde1denNyeAtaniyor_regresyonBekcisi` (n=4,5,6,9,12),
`matchNumber_ciftDevredeTurIciSiraKoruniyor` (n=5,6,13).

---

## 2. ÖLÇÜMLER

### A) LİG — `createLeagueMatches` + `calculateLeagueResults`

* **Circle-method fikstür doğrulandı:** n = 2, 3, 32, 33 (mevcut testlerde en
  büyük n=12'ydi). Her çift tam bir kez, tur başına takım tekrarı yok,
  tur sayısı çiftte n−1 / tekte n, tur başına maç çiftte n/2 tekte (n−1)/2,
  BYE (id=−1) hiçbir gerçek maça sızmıyor.
* **Çift devre (rövanşlı) TEK takım sayısında ilk kez doğrulandı:** n = 5, 7, 9
  (+ kontrol için 6). Her ikili tam iki kez, ev-deplasman **her ikilide yer
  değiştiriyor**, iki devrenin tur numaraları çakışmıyor, tur içi takım tekrarı yok.
* **Puan korunumu (n=9 çift devre, 72 maç):** dağıtılan toplam puan
  = galibiyet×3 + beraberlik×2 — tutuyor.
* **Bozuk veri:** yabancı `winnerId` · mükerrer kayıt · yetim maç · iki tarafı
  da yetim maç · bunların dördü bir arada → **çökme yok**, pozisyonlar 1..n
  tekrarsız. 32 takımlık mükerrer sette de çökme yok.
* **matchNumber:** lig maçlarının **%100'ü** `matchNumber = 0` (bulgu B9).

### B) DÜZ İSVİÇRE — `SwissSystem`

* **Bye adaleti UZUN koşumda ve KARARLI sonuçlarla ölçüldü** (mevcut test n=7 ve
  hepsi-berabereydi; berabere olunca sıralama hiç değişmediği için bye seçimi
  gerçek baskı altına girmiyordu). Tur bütçesi zorlanarak tükenene kadar,
  "küçük id kazanır" deseniyle:

  | n | tur | gerçek maç | bye min | bye max | dağılım |
  |---|---|---|---|---|---|
  | 9 | 7 | 28 | 0 | 1 | 2 takım hiç bye almadı, 7 takım birer |
  | 11 | 11 | 55 | 1 | 1 | **her takım tam bir kez** |
  | 15 | 15 | 105 | 1 | 1 | **her takım tam bir kez** |

  **max(bye) − min(bye) ≤ 1** her üçünde de sağlandı. n=11 ve n=15'te dağılım
  kusursuz (tam rotasyon). n=9'da fark, turnuvanın 9 yerine 7 turda tükenmesinden
  geliyor (aşağıya bakınız) — bye kuralının değil, eşleştirmenin sınırı.

  🔴 **BU ÖLÇÜMÜN SINIRI (sonradan bulundu, bkz. B10):** yukarıdaki tablo
  `SwissSystem` DOĞRUDAN beslenerek üretildi, yani 1. tur da `SwissSystem`'in
  kendi ürettiği turdur. **Uygulamada öyle değil** — 1. tur eski motordan
  geliyor ve orada bye HİÇ üretilmiyor. Gerçek akışta ölçülen bye sayıları:
  n=5→2 (3 turda), n=9→3 (4 turda), n=15→3 (4 turda), yani **her zaman tur
  sayısından bir eksik**. Motorun bye kuralı doğru; uygulamanın motoru çağırma
  biçimi kusurlu.
* **Tekrar eşleşme yasağı:** n = 9, 11, 15 tam koşumlarında ve n = 8, 10, 12, 16
  ağır-geçmiş koşumlarında **üretilen HER eşleşme** geçmişe karşı denetlendi —
  **sıfır ihlal**. Ağır geçmişte kurulabilen tur sayıları: n=8→7, n=10→7,
  n=12→11, n=16→15. Motor eşleştirme bulamadığında sessizce tekrar
  eşleştirmiyor, `canContinue=false` + gerekçe döndürüyor.
* **Tükenme (ulaşılan tur sayısı):**

  | n (tek) | 5 | 7 | 9 | 11 | 15 |
  |---|---|---|---|---|---|
  | ulaşılan tur | 5 | 7 | **7** | 11 | 15 |
  | teorik üst sınır | 5 | 7 | 9 | 11 | 15 |

  | n (çift) | 4 | 6 | 8 | 12 | 16 |
  |---|---|---|---|---|---|
  | ulaşılan tur | 3 | 5 | 7 | 11 | 15 |
  | teorik üst sınır | 3 | 5 | 7 | 11 | 15 |

  Ölçülen tek istisna **n=9: 9 tur mümkünken 7 turda tükeniyor** (n=10 da 11
  yerine 7'de tükeniyor). Bunlar dışında motor teorik üst sınıra ULAŞIYOR.
  Kusur DEĞİL: motor greedy + geri izlemeli, maksimum uzunlukta bir program
  bulmak zorunda değil; ayrıca `recommendedRoundCount` (n=9 için 4, n=10 için 4)
  her durumda fazlasıyla karşılanıyor — yani **uygulamanın gerçek kullanımında
  görünmez**. Yine de ölçülüp kilitlendi ki ileride gerileme fark edilsin.
* **`recommendedRoundCount` uçları:** n = −3→0, 0→0, 1→0, 2→1, 3→2, 4→2, 5→3,
  8→3, 9→4, 16→4, 17→5. Tam 2 kuvvetlerinde (2…4096) kayan nokta yuvarlaması
  ceil'i bir fazlaya itmiyor (motordaki `1e-9` payı çalışıyor).
* **Geri izleme bütçesi (MAX_BACKTRACK = 50.000):** yapısal olarak imkânsız
  BÜYÜK senaryoda (n=14, bir takım herkesle oynamış) motor takılmıyor —
  ölçülen süre **0 ms**, dürüstçe `canContinue=false` + gerekçe. İmkânsızlık
  bütçe dolmadan anlaşılıyor (bkz. § 4: bütçenin gerçekten tükendiği bir
  senaryo üretilemedi).
* **`computeState` determinizmi:** maç listesi düz / ters / farklı anahtarla
  sıralı verildiğinde state **birebir aynı**; `songs` sırası puanları
  değiştirmiyor.
* **Çift takım sayısında bye üretilmiyor** (n = 4, 6, 8, 12, 16 — doğrulandı).

### C) DİREKT PUANLAMA — `createDirectScoringResults`

**Bu fonksiyonun daha önce hiç testi yoktu.** Kurulan kapsam:
* Eşit puanlarda iki çağrı aynı sonucu veriyor (deterministik), ama girdi
  sırasına bağlı (B3).
* Aralık kısıtı **yok**: −50, 250, `MAX_VALUE`, `±Infinity` olduğu gibi kabul
  edilip sıralanıyor; **`NaN` birinciye çıkıyor** (B4).
* Puanlanmamış öğe 0.0 sayılıyor → pozitiflerin altında, **negatiflerin
  üstünde** (B5).
* Sınırlar: 0 öğe → boş · 1 öğe → position 1 · sözlükteki fazladan id sonuca
  sızmıyor · aynı id iki kez (veri bozulması) çökmüyor.
* Pozisyon bütünlüğü: n=50 (çok eşitlikli) ve n=200'de pozisyonlar 1..n
  tekrarsız-boşluksuz, puanlar azalan sırada.

### D) ÇAPRAZ TUTARLILIK — motor vs. canlı puan tablosu

* **Uyuştukları doğrulanan senaryolar:** skorsuz düz lig (n=6), her maçta iki
  skor dolu (n=8), yabancı `winnerId` (n=4), 16 takımlı tam lig.
* **Ölçülen ÜÇ ayrışmanın üçü de bu sınav sırasında düzeltildi:**
  ① tek taraflı skor (B1a) · ② eşit puanda galibiyet bozucusu (B1b) ·
  ③ yetim maç (B0). Üçü de artık `duzeltmeSonrasi_*` regresyon bekçisi.
* **Kalan ayrışma yok** — taranan senaryolarda motor ve canlı tablo birebir aynı
  sırayı veriyor. (Tarama kapsamı § 4'te sınırlarıyla yazılı: ViewModel'in
  gerçek nesnesi değil, formülünün test dosyasındaki kopyası karşılaştırıldı.)

---

## 3. SAYILAR

**Son koşum:** `./gradlew :app:testDebugUnitTest --tests "*EskiMotor*"` ·
GRADLE-KURALI kilidi altında (04:18:13 alındı → 04:19:23 bırakıldı) ·
**BUILD SUCCESSFUL in 1m 6s**

| Dosya | Test | Geçti | Kaldı | Atlandı |
|---|---|---|---|---|
| `EskiMotorLigSinaviTest` | 20 | **20** | 0 | 0 |
| `EskiMotorSwissSinaviTest` | 21 | **21** | 0 | 0 |
| `EskiMotorDirektPuanlamaSinaviTest` | 15 | **15** | 0 | 0 |
| `EskiMotorCaprazTutarlilikTest` | 10 | **10** | 0 | 0 |
| **TOPLAM** | **66** | **66** | **0** | **0** |

Testlerin dağılımı:
* **Regresyon bekçisi: 14.** Bu sınavda bulunup düzeltilen 9 kusurun her biri
  için en az bir bekçi; düzeltme geri alınırsa kırmızıya dönerler ve mesajları
  hangi düzeltmenin düştüğünü söyler.
* **"Belgelenmiş kusur" testi: 5.** Açık kalan B5 · B6 · B7 kusurlarını
  donduruyorlar. YEŞİL olmaları "sorun yok" demek DEĞİL;
  mesajlarında "BULGU DEĞİŞMİŞ … raporu güncelle" yazıyor.
* **Doğruluk / sınır durumu testi: 47.**

**Kapsanan sınır durumları:** 0 öğe · 1 öğe · 2 öğe · tek sayı (bye) · çift
sayı · büyük liste (n=32, 33, 50, 200) · hepsi berabere · yetim maç · iki
tarafı da yetim maç · yabancı `winnerId` · yabancı `winnerId` + skor ·
beraberlik (bozuk-kazanan kontrolüne takılmamalı) · mükerrer kayıt · mükerrer
bye · bye kaydı (öz-eşleşme) · yabancı `rankingMethod` · `NaN` · `±Infinity` ·
`MAX_VALUE` · mükerrer öğe id'si · sözlükte fazladan id · tek taraflı skor.

**Koşum geçmişi:** toplam 5 kilitli koşum. İlk iki koşum, GRADLE-KURALI
yürürlüğe girmeden önce paralel gradle çakışmasıyla düştü
(`Could not delete app\build\tmp\kotlin-classes\debug\com`, 26 dk kayıp) —
test hatası değildi. Kilit protokolünden sonra çakışma olmadı.

---
## 4. KAPSANMAYANLAR (dürüstlük bölümü)

### 🔴 Bu sınavın kendi körlüğü — "ölçüm doğru, ölçülen şey yanlış"

Sınavın ilk turunda **motorlar ölçüldü, uygulamanın motorları NASIL ÇAĞIRDIĞI
ölçülmedi.** § B'nin bütün İsviçre ölçümleri `SwissSystem`'i doğrudan besleyerek
yapıldı; oysa uygulamanın 1. turu o motora hiç uğramıyordu (B10). Sonuç: "bye
adaleti max−min ≤ 1" hükmü motor için doğru, **uygulama için yanlıştı** ve
raporda düzeltilene kadar öyle durdu. (B10 sonradan düzeltildi; o hüküm ancak
düzeltmeden SONRA uygulama için de doğru oldu.)

Kusuru `ranking-fd` yan yoldan buldu; kod okumasıyla bildirdi, ben bağımsız
doğrulayıp iki ek ölçümle kapattım. **Ders:** bir motorun testi geçmesi, o
motorun çağrıldığını göstermez; bir kusurun kod yorumunda "düzeltildi" yazması
da (bkz. `NewTournamentScreen.kt:431`) düzeltmenin devreye girdiğini göstermez.

Bu körlük **SwissSystem'in kendi 59 testinde de var** — hepsi motoru ölçüyor,
giriş noktasını ölçmüyor. Aynı sorunun başka motorlarda da olup olmadığı
(EMRE_CORRECT, HIBRIT, EMRE_SIRALAMA giriş noktaları) **bu sınavda taranmadı**
— tarayacak oturuma açık iş.

### Diğer kapsanmayanlar

* **ViewModel gerçek nesnesiyle çalıştırılmadı.** `calculateCurrentStandings`
  `suspend` + Room + `viewModelScope` bağımlı; JVM birim testinde koşmuyor.
  Formül test dosyasında **yeniden yazıldı** (kopyalama testi, şartnamenin
  istediği yöntem). Risk: ViewModel ileride değişir, kopya güncellenmezse test
  yanlış güvence verir. Kopyanın kaynağı `RankingViewModel.kt:1986-2051` olarak
  dosyaya yazıldı.
* **`SwissSystem` bye adaleti yalnız iki kazanan deseniyle ölçüldü** (küçük id
  kazanır; ayrıca hepsi-berabere mevcut testte var). Rastgele desen bilerek
  kullanılmadı — replay determinizmi kuralı gereği testler rastgelesiz.
* **Geri izleme bütçesinin (50.000) GERÇEKTEN tükendiği bir senaryo
  üretilemedi.** İmkânsızlık bütçe dolmadan anlaşılıyor (n=14 imkânsız senaryo
  **0 ms**'de bitti). Dolayısıyla "bütçe dolunca davranış dürüst mü" sorusunun
  cevabı **ÖLÇÜLMEDİ** — yalnız "imkânsız büyük senaryoda anında ve dürüstçe
  bitiyor" ölçüldü. Şartnamedeki "MAX_BACKTRACK'e çarpıyor mu" sorusunun dürüst
  cevabı: **taranan senaryolarda çarpmadı, çarptığında ne olduğu test edilmedi.**
* **n=9 ve n=10'da motor teorik tur üst sınırına ulaşmıyor** (9 yerine 7,
  11 yerine 7). Sebebi araştırılmadı — greedy çapa seçimi mi, bütçe mi, yoksa
  o boyutlarda maksimum programın gerçekten kurulamaması mı, **ölçülmedi**.
  Uygulamada görünmez (`recommendedRoundCount` n=9 ve n=10 için 4).
* **Lig `matchNumber`'ının UI'daki oylama sırasına etkisi** yalnız kod okumasıyla
  değerlendirildi (`MatchDao.getNextUncompletedMatch` son anahtarı `id ASC`);
  cihazda ölçülmedi.
* **Eleme / tam eleme motorları bu sınavın kapsamı dışındaydı** (şartname A-B-C-D
  başlıkları); onlara dokunulmadı.
* **Direkt Puanlamanın UI tarafındaki puan aralığı doğrulaması okunmadı** — B4/B5
  bulgularının kullanıcıya gerçekten yansıyıp yansımadığı giriş ekranının
  kısıtlarına bağlı, o ekran incelenmedi.

---

## 5. DALGANIN KAPANIŞI

### Düzeltilenler (7) ve kim yaptı

| # | Bulgu | Düzeltme | Bekçi |
|---|---|---|---|
| B0 | Yetim maç: canlı tablo puan yazıyordu | `RankingViewModel:2018-2019` | 3 test |
| B1a | Tek taraflı skor averaj uyduruyordu | `RankingViewModel:2015` | 1 test |
| B1b | Motorun eşitlik zinciri toplam sıralı değildi | `RankingEngine:152-158` | 1 test |
| B2 | Lig sıralaması girdi sırasına bağlıydı | aynı düzeltme | 2 test |
| B3 | Direkt Puanlamada eşitlik bozucu yoktu | `RankingEngine:29-31` | 1 test |
| B4 | `NaN` puan birinciye çıkıyordu | `RankingEngine:17` | 2 test |
| B8 | Yabancı `winnerId` averajı yürütüyordu | motor `:133` + ViewModel `:2026-2028`, `:1972-1974` | 3 test |
| B9 | Lig fikstürü `matchNumber` atamıyordu | `RankingEngine:76`, `:99` | 2 test |
| B10 | SWISS 1. turu eski motordan geliyordu | `RankingViewModel:515` | 2 test |

Düzeltmeleri koordinatörler yaptı (`ranking-7d` → commit `0182c99`; sonra
`ranking-d4`). Bu sınavın payı: kusuru bulmak, ölçmek, hangi davranışın kanonik
olması gerektiğini gerekçelendirmek, düzeltmenin biçimini önermek ve düzeltme
sonrası doğrulamayı bırakmak.

**Sınav sırasında önlenen iki ikincil kusur** (düzeltme yapılmadan önce uyarıldı
ve kabul edildi):
1. **B8 tek katmana uygulansaydı** B1a ayrışması yeni bir vaka olarak geri
   gelirdi (motor maçı atlar, canlı tablo skoru averaja yazmaya devam ederdi).
2. **Yetim maç / bozuk kazanan kontrolleri bye kaydını yeseydi** İsviçre'de bye
   puanı sessizce kaybolurdu (`songId1 == songId2` olduğu için). İki kontrol de
   bye'ı yemeyecek biçimde yazıldı ve ayrı testlerle kilitlendi.

### Açık kalan 3 kusur (backlog)

| # | Bulgu | Durum |
|---|---|---|
| B5 | Puanlanmamış öğe 0 sayılıp negatif puanlının üstüne çıkıyor | UI 0-100 kullandığı sürece zararsız |
| B6 | Mükerrer maç kaydı çift sayılıyor (Lig 6 puan; İsviçre `byeCount` şişiyor) | Kayıt katmanında benzersizlik kısıtı önerildi |
| B7 | `calculateLeagueResults` `rankingMethod` süzmüyor | Tek çağıran zaten süzüyor; motor kendini savunmuyor |

Üçü de **mevcut davranışı kilitleyen YEŞİL testlerle** belgeli. Düzeltilirse o
testler kırmızıya döner ve mesajları "BULGU DEĞİŞMİŞ → raporu güncelle" der;
yani düzeltmeyi yapan kişi testi güncellemesi gerektiğini testin kendisinden
öğrenir. Güncellenecek testler:
`puanlanmamisOge_negatifPuanlininUstunde_belgelenmisKusur` ·
`mukerrerKayit_puanIkiKezSayiliyor_belgelenmisKusur` ·
`bozukVeri_mukerrerKayit_puanIkiKezSayiliyor_belgelenmisKusur` ·
`bozukVeri_mukerrerBye_byeCountIkiKezSayiliyor_belgelenmisKusur` ·
`yontemSuzgeci_ligMotoruYabanciYontemiDeSayiyor_belgelenmisKusur`.

### Temizlik borcu — ✅ ÖDENDİ (koordinatör `ranking-d4`, 02.09.2026)

* **Ölü üçlü silindi:** `RankingEngine.createSwissMatchesWithState` /
  `createSwissMatches` / `createSwissMatchesAdvanced` ana koddan kaldırıldı
  (çağıranları kalmamıştı). Yerine kaldırma notu + ders yazıldı.
* **`SwissBirinciTurGirisNoktasiTest.kt` silindi** — sınadığı fonksiyonlar
  gittiği için kapsaması anlamını yitirdi. Kusurun kaydı bu raporda (§ B10) ve
  `RankingEngine.kt`'deki kaldırma notunda yaşıyor.
* **Bu sınavın `eskiMotorunOluIlkTurDali_matchNumberAtamiyor_belgelenmisKusur`
  testi de çıkarıldı** (silinen fonksiyonu çağırdığı için derlemeyi kırardı);
  yerine dosyada kaldırma notu duruyor. Bekçi
  `uygulamaAkisi_ilkTurArtikSwissSystemden_regresyonBekcisi` yerinde.
* **`NewTournamentScreen.kt` yorumu düzeltildi** — bayatlayan test sayısı
  yerine bu rapora işaret ediyor.

⚠️ **Not:** silinen ölü üçlünün kusuru artık YALNIZ belgede yaşıyor, testte
değil. Biri o kod yolunu yeniden yazarsa aynı hatayı yapmasını engelleyecek bir
test YOK; engelleyecek tek şey `RankingEngine.kt`'deki kaldırma notu ve bu
rapor. Bilinçli bir denge: ölü koda test tutmak da bedel.

### Commit durumu

Bu sınavın 5 dosyası (**4 test + bu rapor**) **commit EDİLMEDİ.** Bu depoda
commit yıldızlı komuta bağlı (`*cmt` / `*tmm`) ve işçi oturumunun kendi kendine
commit yetkisi yok. Dosyalar çalışma ağacında:
```
app/src/test/java/com/example/ranking/EskiMotorLigSinaviTest.kt
app/src/test/java/com/example/ranking/EskiMotorSwissSinaviTest.kt
app/src/test/java/com/example/ranking/EskiMotorDirektPuanlamaSinaviTest.kt
app/src/test/java/com/example/ranking/EskiMotorCaprazTutarlilikTest.kt
oturumlar/ESKI-MOTORLAR-SINAV-RAPOR.md
```
