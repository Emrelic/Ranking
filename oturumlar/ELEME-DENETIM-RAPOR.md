# ELEME SİSTEMLERİ DENETİM RAPORU — eski motorlar (ELIMINATION / FULL_ELIMINATION)

**İşçi:** OPUS HAZIR KITA 104 (oturum `ranking-fd`, Opus) · **Görevi veren:** `ranking-7d` (oturum kapandı; teslim tahtaya M-0007 olarak yazıldı)
**Tarih:** 1-2 Eylül 2026 · **Dal:** `ileri-tusu-asagida-crash-fix`
**Sınır:** ana koda DOKUNULMADI. Yalnız `app/src/test/` altına iki YENİ dosya eklendi.
ViewModel yalnız OKUNDU.

---

## 0. HÜKÜM (tek paragraf)

Eski iki eleme motoru **tamir edilecek durumda değil, emekliye ayrılmalı.** Kusurlar
tek tek yamanabilir cinsten değil; **tasarımın kendisi** "grup aşamasından çıkan takım
sayısı tam olarak ikinin üssü olmalı" varsayımına dayanıyor ve bu varsayım ölçülen
kadroların üçte birinde tutmuyor. Yerine geçecek motor (`EliminationSystem.kt`, 702
satır, 20 testi geçmiş) **zaten yazılmış** ve bu varsayımı yapısal olarak ortadan
kaldırmış (ön tur/play-in). Kalan iş motor yazmak değil, **bağlamak** — ve orada
karşımıza motor değil **ekran** çıkıyor: `EliminationContent` hâlâ tek satırlık stub.

---

## 1. ÖLÇÜM KÜNYESİ

| | |
|---|---|
| Eklenen test dosyaları | `EskiElemeMotoruDenetimTest.kt` (15 test, davranışı sabitler) · `EskiElemeSozlesmeKirmiziTest.kt` (7 test, **bilerek kırmızı**) |
| Koşum | `./gradlew testDebugUnitTest --tests "*EskiEleme*"` (filo gradle kilidi altında) |
| Sonuç | denetim dosyası **15/15 yeşil** · sözleşme dosyası **0/7 yeşil (7 kırmızı, kasıtlı)** |
| Bağımsız doğrulama | `calculateOptimalGroupConfig` algoritması ayrıca Python'da birebir simüle edildi; sayılar örtüştü |

⚠️ Kırmızı dosya süiti kırmızı yapar (koordinatör kararı: `@Ignore` YOK).
Kırmızısız koşum: `--tests "*EskiElemeMotoruDenetimTest*"`.

---

## 2. ELIMINATION — bulgular

### 🔴 A1. Grup yapılandırması 29 kadroda bracket'i kuramıyor (KÖK KUSUR)
`RankingEngine.calculateOptimalGroupConfig` (:233). 3..100 arasında ikinin üssü
olmayan **93 kadronun 29'unda** gruptan çıkan takım sayısı ikinin üssü değil:

```
7, 9, 13, 15, 17, 19, 25, 27, 29, 31, 33, 35, 37, 49, 51, 53, 55,
57, 59, 61, 63, 65, 67, 69, 71, 73, 75, 97, 99
```

Ortak imza: **elenecek takım sayısı TEK.** Sebep tek satırlık: ikinci dal
`eliminationsPerGroup = 2`yi KOŞULSUZ döndürüyor (:245-265); orada hesaplanan
`groupsEliminating2` / `groupsEliminating1` değişkenleri **hiçbir yerde
kullanılmıyor** — "bazı gruplar 1, bazıları 2 eler" fikri yazılmış ama
uygulanmamış. Tek eleme gerektiğinde bir takım FAZLA eleniyor.

### 🔴 A2. "Grup boyutu 3-6" sözü tutulmuyor — n=33'te tek grupta 528 maç
Elenecek takım sayısı 1 olduğunda `groupCount = 1` çıkıyor ve o tek grup **tüm
kadro** oluyor:

| n | grup | grup boyutu | grup maçı |
|---|---|---|---|
| 9 | 1 | 9 | **36** |
| 17 | 1 | 17 | 136 |
| 33 | 1 | 33 | **528** |
| 65 | 1 | 65 | **2080** |

Kullanıcı tarafında görünümü: *"9 şarkı seçtim, 36 maç çıktı"* — ve sonunda yine
bozuk bracket (A1: n=9 → 7 takım kalıyor, hedef 8).

### 🔴 A3. n=12 tam akış: 12 öğeden **9'u** sonuç alıyor, 2·3·4 sıraları boş
n=12 **doğru** yapılandırılan kadrolardan (4 grup × 3, doğru 8 takım çıkıyor) — yani
bu kusur A1'den bağımsız. Ölçülen pozisyonlar: `[1, 5, 6, 7, 8, 9, 10, 11, 12]`.

Sebep zinciri iki katmanlı:
1. **ViewModel** (`createNextEliminationRound`, :1311) knockout'un **yalnız 1. turunu**
   kuruyor. İkinci çağrıda `allMatches.none { it.round > 0 }` artık yanlış olduğu
   için `completeRanking()`e düşüyor — çeyrek final oynanır, turnuva biter.
2. **Motor** yarım bracket'i sorun saymıyor: `calculateDirectEliminationResults`
   (:434) `songs.find { it.id !in eliminated }` ile ayakta kalan DÖRT takımdan
   yalnız BİRİNE 1. sırayı veriyor, diğer üçü sonuç listesine **hiç girmiyor**.

### 🔴 A4. Kadro zaten ikinin üssü olduğunda da aynı yarım bracket
n=8 → 4 maçlık tek tur kurulup turnuva bitiyor; 8 öğeden 5'i sonuç alıyor
(`[1, 5, 6, 7, 8]`). Yani "grup yapılandırmasını düzeltmek" tek başına yetmez.

### 🔴 A5. Tek sayılı bracket'te son takım sessizce düşüyor
`createDirectEliminationMatches` (:274) `i + 1 < songs.size` ile son öğeyi atlıyor:
7 takımdan 6'sı oynuyor, 7. takımın ne maçı var ne bye kaydı ne de elenmiş sayılıyor.
Karşılaştırma: `SwissSystem` bye'ı öz-eşleşme kaydıyla açıkça yazıyor; burada iz yok.

### 🔴 A7. n=7 uçtan uca: gruptan 3 takım çıkıyor, biri büsbütün kayboluyor
A1'in canlı vakası. Grup fikstürü 9 maç (4'lü + 3'lü grup), gruptan **3** takım
çıkıyor (hedef 4), knockout tek maça düşüyor, üçüncü takım (id=5) ne bracket'te ne
sonuç listesinde. Ölçülen: **6/7 sonuç**, 2. sıra hiç dağıtılmıyor.

### 🔴 A8. EN SİNSİSİ — sonuç katmanı oynanan fikstüre değil `songs.size`e bakıyor
`calculateEliminationResults` (:382) grup yapılandırmasını n'den **yeniden türetiyor**
ve grup sıralamasını `round == 0` maçlarından okuyor. O maçlar **hiç yoksa** hata
vermiyor: herkesin puanını 0 kabul edip id sırasına göre dört takımı "gruptan elendi"
sayıyor. Ölçüldü: yalnız knockout maçları verilen n=7 senaryosunda 3, 4, 6, 7
numaralı takımlar **hiç grup maçı oynamadan** 0.0 puanla elenmiş görünüyor.

📌 *Bu, "yanlış sonuç" değil **uydurma sonuç**tur: turnuvanın oynanmamış bir
aşamasından sıralama üretiliyor ve hiçbir yerde uyarı çıkmıyor.*

🔴 **İKİ TESTİN ÇIKTISI BİREBİR AYNI ÇIKTI** — denetimin en keskin tek satırı:

```
KUSUR A7 (9 grup maçı oynandı)  · sonuç 6/7 · [(1,1), (2,3), (6,4), (7,5), (3,6), (4,7)]
KUSUR A8 (HİÇ grup maçı yok)    · sonuç 6/7 · [(1,1), (2,3), (6,4), (7,5), (3,6), (4,7)]
```

Yani n=7'de **bütün grup aşamasını oynamak sonucu hiç değiştirmiyor.** Kullanıcı 9
maç oylasa da oylamasa da aynı sıralamayı alır. Bir turnuva sisteminin verebileceği
en pahalı sessiz hata budur: emek harcanır, çıktı emeğe bakmaz.

### 🟢 A6. Yabancı kazanan id'si çökme YAPMIYOR (ama maçı sessizce yutuyor)
Silinmiş öğe / üçüncü takım id'si `when` dallarının hiçbirine düşmüyor, maç yok
sayılıyor: kimse elenmiyor, iki takım da ayakta kalıyor, yalnız biri sonuç alıyor.
Çökme yok — ama sessizlik de var.

---

## 3. FULL_ELIMINATION — bulgular

### 🔴 B2. `getWinnersAndLosers`: aynı takım hem kazanan hem kaybeden
Üçlü grup (lig usulü) dalı **yalnızca turda tam 3 takım ve tam 3 maç varken**
çalışıyor (:848). Oysa gerçek bir tam-eleme turu **ikili maçlar + son üçlü grup**
karışımıdır; o zaman üçlü grup üç bağımsız ikili maç gibi işleniyor.
Ölçülen (5 takım, üçlüde döngü): `kazananlar=[1,3,4,5]`, `kaybedenler=[2,4,5,3]` →
**kesişim `[3,4,5]`**. ViewModel bu iki listeyle sonraki turu kuruyor (:1389-1407):
elenmiş sayılan takım aynı anda kazanan havuzunda.

🟢 B3: aynı mantık, tur SADECE 3 takımdan ibaretse **doğru** çalışıyor.
Yani kod doğru, **tetikleyen koşul** yanlış.

### 🔴 B4. Tam eleme ilk turdan sonra yarıda kalıyor, ayakta kalanlar sıralanmıyor
n=12: ilk tur 6 takımı eliyor ama hedef yalnız 4 eleme. ViewModel
`eliminatedSoFar >= teamsToEliminate` görüp (:1375) kalan 6'yı hedef 8'e eşit
bulamıyor → `completeRanking()`. Ölçülen: **6/12 sonuç**, pozisyonlar `[7..12]`;
**ayakta kalan 6 takımın hiçbiri sonuç listesinde yok.**

Bunun yapısal sebebi: `createFirstPreEliminationRound` (:941) `targetSize`i hiç
dikkate almıyor — kadroyu ikiye katlayarak yarıya indiriyor. "Hedefe kadar ele"
fikriyle "herkesi eşleştir" uygulaması birbirini tutmuyor.

### 🔴 B5. Final aşaması pozisyonları kadro dışına taşıyor
`mergeAdvancedEliminationResults` (:1085) ön eleme pozisyonlarına final sonuç
**sayısını** ekliyor. n=8 ölçümü: 7 sonuç, pozisyonlar `[1, 3, 4, 8, 9, 10, 11]` —
8 kişilik kadroda **11. sıra** dağıtılmış.

### 🔴 B6. Fikstür rastgele — replay deseni çiğneniyor
`createAdvancedPreEliminationMatches` (:930) `songs.shuffled()` çağırıyor.
Ölçüldü: **20 çağrı → 20 farklı fikstür.** ORTAK.md'nin replay kuralının
(`shuffled()`/`Random`/`currentTimeMillis()` KULLANILMAZ) tek açık ihlali burada.

### 🟡 B7 (LATENT). `identifyTripleGroups` turlar arası sahte üçlü grup kuruyor
`identifyTripleGroups` (:1175) "match1 ile ortak takımı olan maçlar" diye topluyor.
Tek tur içinde doğru çalışır (o turda takımlar birer kez oynar), ama
`getQualifiedTeamsFromMatches` ona **tüm turların maçlarını** veriyor (:1115).
`a-b (t1), a-c (t2), b-d (t2)` üçlüsü sahte bir "grup" olarak tanınıyor — dört
takımlı bir üçlü grup.

**Ama bugün gözlemlenebilir bir yanlış üretmiyor:** sahte grubun tepe takımı
matematiksel olarak zaten en az bir ikili maç kazanmış olmak zorunda, dolayısıyla
`qualified` kümesine zaten girmiş oluyor. Bu yüzden **kusur listesine değil TUZAK
listesine** yazıyorum: kod bugün doğru sonucu **yanlış sebeple** veriyor; ilk
değişiklikte ısırır. (`getTripleGroupLosers` tur bazında çağrıldığı için güvenli.)

### 🟢 B1. `getRemainingTeamsAfterRound` sözleşmesi TUTUYOR
Beraberlikte kimse elenmiyor, tamamlanmamış maç sayılmıyor, verilen turdan sonraki
turlar hesaba katılmıyor. Üçü de test edildi, üçü de geçti.

---

## 4. VİEWMODEL AKIŞI (yalnız okundu, dokunulmadı)

| Yer | Bulgu |
|---|---|
| `createNextEliminationRound` :1311 | Knockout'un yalnız 1. turu kuruluyor; zincir yok (A3'ün birinci halkası) |
| `createNextFullEliminationRound` :1348 | Round ≥ 101 final bracket'i **pratikte ulaşılamaz**: kalan takım sayısının hedefe TAM eşit olması gerekiyor, ilk tur kadroyu yarıya indirdiği için bu ancak tesadüfen olur. n=12 → 6 kalır, hedef 8 → hiç kurulmaz. Yani B5'teki pozisyon kusuru bugün sahada değil, motor testinde görünür |
| `createNextFullEliminationRound` :1416-1426 | "Kaybeden sayısı yetmezse kazananlardan `2×need` aday çek" dalı: yeni elenenler bir ÖNCEKİ turu kazanmış takımlardan seçiliyor — kayıp defteri (kimin kaç kez elendiği) tutulmuyor |
| `RankingScreen.kt:541` `EliminationContent` | **Tek satırlık `Text` stub'ı.** Maç kartı yok, oy düğmesi yok. Motor bağlansa bile kullanıcı boş ekran görür |

---

## 5. ANALIZ_RAPORU.md §4.3 — bir madde BAYAT

> "Grup dağılımı iki farklı `shuffled()` ile iki kez yapılıyor → sonuçlar fiilen rastgele"

**Bu artık geçerli değil.** Hem `createEliminationMatches` (:192) hem `getGroupSongs`
(:329) bugün `sortedBy { it.id }` kullanıyor, kodda gerekçesi de yazılı. Ölçüldü:
aynı girdi aynı fikstür, gruplar bitişik id dilimleri, örtüşme yok (test `A3`).
**ELIMINATION'da grup dağılımı SAĞLAM.** Rastgelelik yalnız FULL_ELIMINATION'da
kaldı (B6).

§4.3'ün diğer iki maddesi (knockout zinciri yok · üçlü grup + hem kazanan hem
kaybeden) **doğrulandı ve sayıya bağlandı.**

Aynı bayatlık `NewTournamentScreen.kt:422-428` yorumunda da vardı — koordinatör
kendi alanı olduğunu söyledi, ben dokunmadım.

---

## 6. YENİ MOTOR: `EliminationSystem.kt` — ne kapsıyor, ne eksik

**Kapsıyor (20 test, hepsi geçiyor):**
- Üç kip: `SINGLE`, `DOUBLE`, `GROUP_THEN_KNOCKOUT`; ortak `computeState` /
  `createNextRound` / `calculateResults` / `bracketStructure` API'si.
- Sınır kadrolar: n = 0, 1, 2, 3, 4, 8, 12, 16.
- **Eski kusurların doğrudan regresyon testleri**: pozisyon çakışması yok
  (n=16'da 1..16 tam bir kez), grup üyeliği deterministik (iki çağrı aynı gruplar),
  yetim maç kaydı üç kipte de çökmüyor, bir takım aynı turda iki maç oynamıyor,
  hepsi beraberlikte deterministik sonuç.
- **A1'in kök kusuru yapısal olarak yok**: knockout çekirdeği ön tur (play-in)
  üretiyor, yani gruptan çıkanın ikinin üssü olma mecburiyeti kalkmış
  (`groupPlan`: ceil(n/5) grup, grup başına 2 çıkar; dağıtım yılan/snake, `song.id`
  sırasına göre deterministik).

**Bağlamak için eksik (ölçülen):**
1. **Ana kodda `EliminationSystem`e TEK BİR ÇAĞRI YOK** — yalnız testlerden
   çağrılıyor. ViewModel hâlâ `RankingEngine`'in eski yollarında.
2. **Puanlama ekranı stub** (`RankingScreen.kt:541`) — en büyük tek kalem.
3. **`bracketStructure()` ↔ `BracketView.kt` tip uyuşmazlığı**: motor
   `List<List<BracketSlot>>` veriyor, UI `BracketRound`/`BracketMatch`/`BracketTeam`
   bekliyor. Dönüştürücü YAZILMAMIŞ (ELEME-MOTORU-ILERLEME.md bunu zaten itiraf
   ediyor).
4. **Negatif round numaraları**: çift elemede alt kol `-1, -2...`, büyük final
   1000/1001 olarak saklanıyor. Bu sayılar DB'ye yazılacak; tur numarasına göre
   sıralayan/filtreleyen her yer (fikstür paneli, "tur X" başlıkları, sonuç
   ekranı) gözden geçirilmeli. Bugün kimse çift elemeyi çağırmadığı için
   ölçülemedi — **entegrasyonun ilk sınavı bu olmalı.**
5. **Kip eşlemesi kararı**: UI'da iki yöntem kodu var (`ELIMINATION`,
   `FULL_ELIMINATION`), motorda üç kip. Hangi kod hangi kipe bağlanacak (ve
   `FULL_ELIMINATION` kaldırılacak mı) — koordinatör kararı.
6. **Test boşlukları**: `DOUBLE` yalnız n=4 ve n=8'de koşulmuş (n=1,2,3 sınırları
   yok — ILERLEME defteri de bunu yazıyor); ViewModel/persistence seviyesinde
   hiçbir eleme testi yok (hepsi motor seviyesinde).

---

## 7. YOL HARİTASI (koordinatörün "emekliye ayır + bağla" ekseninde)

**Adım 1 — Karar kaydı.** `ELIMINATION` ve `FULL_ELIMINATION` yöntem kodlarının
hangi `EliminationSystem.Mode`'a karşılık geleceği yazılsın. Öneri: `ELIMINATION` →
`SINGLE`, gruplu istenirse `GROUP_THEN_KNOCKOUT`; `FULL_ELIMINATION` kodu emekli
(veri göçü: mevcut kayıt varsa `ELIMINATION`e çevrilir; ⚠️ CLAUDE.md'deki
EMRE→EMRE_CORRECT göç dersi burada da geçerli — kod adı yeniden kullanılmasın).

**Adım 2 — Ekran (en büyük kalem).** `EliminationContent` stub'ı, diğer
yöntemlerin kullandığı maç kartı/oy düğmesi bileşenine bağlanmalı. Motor hazır,
ekran yok; sıralama tersine çevrilirse yine boş ekran çıkar.

**Adım 3 — ViewModel yolu.** `initializeElimination` / `createNextEliminationRound`
/ `calculateEliminationResults` çağrıları `EliminationSystem.createNextRound` +
`computeState` + `calculateResults` üçlüsüne çevrilsin. Tur zinciri artık motorda:
"tek tur kurup bitir" hatası tekrarlanamaz (`RoundResult.canContinue`).

**Adım 4 — Negatif round sınavı.** Çift eleme n=8 uçtan uca oynatılıp DB'ye
yazılsın, fikstür/sonuç ekranları negatif turlarla ne yapıyor ölçülsün.
(Bu, entegrasyonun bilinen tek ölçülmemiş riski.)

**Adım 5 — Eski kodun silinmesi.** `RankingEngine.kt`'den eleme bloğu
(:171-498 + :788-1232, ~750 satır) ve `FullEliminationTest.kt` çıkarılsın.
O gün **bu raporun iki test dosyası da silinmelidir** — konusu kalmayacak.
Silmeden önce `EskiElemeSozlesmeKirmiziTest.kt`'deki 7 sözleşme
`EliminationSystemTest`e taşınsın: R1/R3 (her öğe tam bir kez, 1..n),
R4 (bye kaydı), R5 (kazanan∩kaybeden = ∅), R6 (determinizm), R7 (ayakta kalanlar
da sıralanır). **Kusur listesi ölür, sözleşme yaşar.**

---

## 8. BIRAKTIĞIM DOSYALAR

```
app/src/test/java/com/example/ranking/EskiElemeMotoruDenetimTest.kt      15 test · 15 YEŞİL
app/src/test/java/com/example/ranking/EskiElemeSozlesmeKirmiziTest.kt     7 test ·  7 KIRMIZI (kasıtlı)
```

Koşum:
```bash
./gradlew testDebugUnitTest --tests "*EskiElemeMotoruDenetimTest*"
```

Kırmızı dosya, eski motorların **tutması gereken** sözleşmesidir; yeni motor
bağlandığında hepsi yeşile dönmelidir (ya da Adım 5'te taşınmalıdır).

---

## 9. YAPMADIKLARIM (sınır)

- Ana kodda tek satır değiştirmedim; ViewModel'i yalnız okudum.
- `identifyTripleGroups`in sahte grup kusurunu **ayrı testle gösteremedim**
  (fonksiyon `private`, tek tüketicisi bugün yanlış sonuç üretmiyor) — B7'de
  neden gösterilemediğini yazdım, uydurma test yazmadım.
- Çift elemenin negatif round numaralarının DB/UI etkisini ÖLÇMEDİM; yalnız risk
  olarak işaretledim (Adım 4).
- `ANALIZ_RAPORU.md` ve `NewTournamentScreen.kt` yorumundaki bayat cümleleri
  düzeltmedim — koordinatörün ve doküman işçisinin (ranking-07) alanı.

---

## 10. EK — SATIR NUMARALARI HAKKINDA

Yukarıdaki satır numaraları **2 Eylül 2026 ~02:30** itibarıyladır ve `RankingEngine.kt`
o gün başka oturumlarca değiştirildiği için bir kez zaten kaymıştı (ilk yazımda
:174/:300/:915 idi, düzeltildi: :192/:329/:930). **Kalıcı referans fonksiyon adıdır,
satır numarası değil** — numara sapmışsa fonksiyon adıyla arayın.

---

## 11. EK — `ranking-07`'nin SORUSU: `RankingEngine`'deki iki Swiss `shuffled()` ölü mü?

Soru: `createSwissMatches` ve `createSwissMatchesAdvanced` içindeki `shuffled()`
çağrıları, aktif SWISS artık `SwissSystem.kt` motorunu kullandığına göre ölü yolda mı?

**Cevap: İKİSİ DE ÖLÜ DEĞİL — biri ölü, biri CANLI. Ve canlı olanın altında bir kusur var.**

### 11.1 `createSwissMatchesAdvanced` (:574) — ÖLÜ ✅
Tek çağıranı `createSwissMatchesWithState` (:500); onun da tüm kaynak ağacında tek
geçtiği yer `RankingViewModel.kt:764`'teki **yorum satırı** ("Eski yol
`RankingEngine.createSwissMatchesWithState` idi..."). Canlı çağrı yok →
Faz 3 temizliğine girer.

### 11.2 `createSwissMatches` (:509) — 🔴 CANLI, aktif SWISS turnuvasının 1. TURU
`RankingViewModel.kt:519` (`initializeSwiss`) bunu doğrudan çağırıyor:
```kotlin
val matches = RankingEngine.createSwissMatches(songs, 1, emptyList())
```
Ve SWISS **2026-08-28'de UI'ya geri açıldı** (`NewTournamentScreen.kt:431, 441`) —
yani bu yol kullanıcının seçebildiği canlı bir yol. Sonraki turlar
(`createNextSwissRound`, :771) `SwissSystem.computeState/createNextRound` kullanıyor.

⇒ **Bir SWISS turnuvasının 1. turu ESKİ motordan, 2+ turları YENİ motordan geliyor.**
Bu tam olarak `SwissSystem.kt`'nin kendi başlığının ve ANALIZ_RAPORU §4.2'nin eski
motorda kusur diye saydığı desendir ("1. tur ile sonraki turlar iki farklı koddan
geçiyor") — motor değişti, desen kaldı.

### 11.3 🔴 CANLI KUSUR: tek sayılı SWISS'te 1. turda bir takım kayboluyor
`createSwissMatches`in 1. tur dalı:
```kotlin
val half = shuffledSongs.size / 2
for (i in 0 until half) { if (i + half < size) pair(shuffledSongs[i], shuffledSongs[i + half]) }
```
Kullanılan indeksler `0..2*half-1`. **n TEK ise son indeks (`n-1`) hiç kullanılmaz:**
o takım 1. turda ne maç oynar, ne bye kaydı alır, ne de puan. n=5 → 2 maç, 1 takım
boşta ve **kayıtsız**. Bu projenin kuralında bye = 1 puandır (CLAUDE.md); burada bye
yok, sessiz bir atlama var.

**Sınırım:** bu bulgu kod okumasıyla kesindir (aritmetik tek yönlü), ama SWISS benim
görev alanım DEĞİL — test yazmadım, dokunmadım. `ESKI-MOTORLAR-SINAV-GOREV.md`
İsviçre işçisinin (B başlığı) alanına düşer; oradaki "bye adaleti" sınavı
`SwissSystem`i ölçüyor, oysa **1. tur SwissSystem'den gelmiyor** — sınav bu yüzden
kusuru ıskalayabilir. Ölçülmesi gereken: `initializeSwiss` → 1. tur → tek n.

### 11.4 Sonradan eklenen üç kayıt (kaynaktan doğrulandı)

**(a) Aynı hatalı blok İKİ yerde.** `createSwissMatchesAdvanced`in 1. tur dalı
(:571-590), `createSwissMatches`inkiyle **birebir aynı** (aynı `shuffled()`, aynı
`half = size/2`, aynı `for (i in 0 until half)`). Biri canlı biri ölü, ama kusur
kopyalanmış. Temizlikte **ikisi birden** gitmeli — ölü yol diye bırakılan kopya bir
gün canlanır ve kusur geri gelir. (ranking-07 gördü, ben satır satır doğruladım.)

**(b) Kod yorumu kusuru "geçmiş zaman" ilan ediyor.** `NewTournamentScreen.kt:431-436`:
> *"SWISS 2026-08-28'de GERİ AÇILDI... **Eski yolda bye yoktu (tek takım sessizce
> turdan düşüyordu)**... Yeni motorda... bye adil rotasyonla dağıtılıyor."*

Yorum kusuru **adıyla** tarif ediyor ve çözülmüş sayıyor. Oysa o "eski yol" 1. tur
için hâlâ canlı. Yani belge yanlış değil — **eksik**: yeni motor gerçekten bye
dağıtıyor, ama turnuvanın 1. turu ona uğramıyor.
📌 *Bir kusurun "düzeltildi" diye yazılması, düzeltmenin ÇAĞRILDIĞINI göstermez.*

**(c) Benim önerdiğim sınav ölçütlerinden biri ÇÜRÜDÜ.** İsviçre işçisine (OPUS
HAZIR KITA 102) "1. turda maç sayısı (n-1)/2 mi?" diye bakmasını önermiştim;
**bu kontrol kusuru YAKALAMAZ** — tam sayı bölmesi yüzünden n tekken `size/2`
zaten `(n-1)/2` ediyor (n=5→2, n=9→4, n=15→7), yani maç sayısı DOĞRU çıkıyor.
Kusuru yakalayan tek ölçüt **katılan öğe sayısı** (n yerine n-1). 102 bunu düzeltti
ve testi doğru ölçüt üzerine kurdu.
