# EMRE_CORRECT — Uzun Koşum Değişmezleri Sınavı (RAPOR)

İşçi: OPUS HAZIR KITA 103 (`ranking-6a [686cfc]`) · Koordinatör: `ranking-7d`
Tarih: 2026-09-02 · Motor koduna DOKUNULMADI (yalnız `app/src/test/` altına yeni dosya).

**Sonuç: 15 test / 15 YEŞİL, 0 kırmızı.** Üç dosya (`app/src/test/java/com/example/ranking/`):

| Dosya | Test | Ne ölçüyor |
|---|---|---|
| `EmreCorrectDegismezlerTest.kt` | 9 | Beraberlikli uzun koşumda puan korunumu, bye adaleti, determinizm, kırmızı çizgi, tur/bitiş tablosu |
| `EmreCorrectN12TaniTest.kt` | 3 | Erken bitişin KAPISI (aday kümesi mi, olabilirlik mi), giriş sırasına duyarlılık, erken bitişin sapma bedeli |
| `EmreCorrectYenidenHesaplamaYoluTest.kt` | 3 | ViewModel'in replay yolu: sağlam yolun korunması + bye çıkarımının kırıldığı iki hâl |

Koşum: `./gradlew testDebugUnitTest --tests "*EmreCorrect*"` → BUILD SUCCESSFUL
(son koşum 2026-09-02 02:55, 1 dk 22 sn).

## Sınavın yeni yükü — neden beraberlik?

Mevcut Emre testlerinin (Emre300SayiListesiTest n=300/299, EmreTurBozulmasiTest n=80,
EmreGeriAlmaMatematigiTest, KesinlikRaporuTest) **hiçbiri beraberlik üretmiyor** —
hepsinde "büyük sayı kazanır". Oysa beraberlik 0.5+0.5 yazar; puan uzayını yarım
adımlara böler, tiebreaker zincirini ve "aynı puanlı eşleşme yoksa turnuva biter"
bitiş kuralını asıl orada zorlar. Bu yüzden değişmezler beraberlikli koşumda ölçüldü
(her 3./4. maç berabere + "hep berabere" uç durumu).

---

## ① PUAN KORUNUMU — her turda, beraberlikli (n=9/16/41/80)

Denetim her tur kapanışında: (a) toplam puan == oynanmış maç + bye,
(b) her takımın puanı maç kayıtları + bye listesinden BAĞIMSIZ yeniden hesaplanıp
motorunkiyle karşılaştırılıyor.

| n | tur | maç | beraberlik | bye | kasa |
|---|---|---|---|---|---|
| 9 | 4 | 16 | 5 | 4 | ✅ her turda tam |
| 16 | 7 | 56 | 18 | 0 | ✅ |
| 41 | 19 | 380 | 126 | 19 | ✅ |
| 80 | 40 | 1600 | 533 | 0 | ✅ |
| 16 (HEP berabere) | 15 | 120 | 120 | 0 | ✅ |

**İhlal yok.** Yarım puanlar kasada birikmiyor, bye 1 puanı çift yazılmıyor,
"hep berabere" uç durumunda (tüm puanlar sürekli eşit, sıralamayı yalnız tiebreaker
belirliyor) motor sonsuz döngüye girmiyor: n=16'da 15 turda tam round-robin'i
(120 maç) tamamlayıp duruyor.

## ② BYE ADALETİ — tek sayılı uzun koşum

| n | tur | toplam bye | min | max | fark |
|---|---|---|---|---|---|
| 9 | 8 | 8 | 0 | 1 | **1** |
| 15 | 8 | 8 | 0 | 1 | **1** |
| 41 | 23 | 23 | 0 | 1 | **1** |
| 81 | 40 | 40 | 0 | 1 | **1** |

Dağılım her boyutta **adil (fark ≤ 1)**; motorun kendi `byeCount` toplamı gerçek bye
sayısıyla birebir. Ayrıca: tek sayıda **her turda tam 1 bye** ve her tur (n−1)/2 maç;
çift sayıda (n=10/16/40/80) **hiçbir turda bye yok** ve her tur n/2 maç — ikisi de assert'li.

## ③ TİEBREAKER DETERMİNİZMİ

n=9/16/41 için aynı maç kümesi sıfırdan iki kez işlendi → **birebir aynı sıra ve
aynı puanlar**. Ek olarak canlı koşumun state'i ile kayıtlı maçların yeniden
işlenmesi karşılaştırıldı → **aynı** ⇒ motorda maç kayıtlarına yansımayan gizli/kalıcı
durum yok (replay güvenli; geri alma ve "devam ettir" akışlarının dayandığı varsayım).
`calculateCorrectEmreResults` üst üste çağrıldığında da sonuç sabit, pozisyonlar 1..n,
öğe kaybı/tekrarı yok.

## ④ TEK EŞLEŞME KURALI (kırmızı çizgi)

n=9/16/41/80 beraberlikli koşumların tamamında: hiçbir ikili iki kez eşleşmedi,
bir takım aynı turda iki maçta görünmedi, maç sayısı round-robin sınırını aşmadı ve
**motorun `matchHistory` boyutu oynanan maç sayısıyla birebir** (hayalet/eksik geçmiş yok).

## ⑤ TUR SAYISI / BİTİŞ DAVRANIŞI (berabersiz, "büyük kazanır")

| n | tur | maç | bye | keskinlik |
|---|---|---|---|---|
| 2 | 1 | 1 | 0 | %100 |
| 3 | **2** | 2 | 2 | %100 |
| 4 | 3 | 6 | 0 | %100 |
| 5 | 2 | 4 | 2 | %75 |
| 6 | 3 | 9 | 0 | %80 |
| 7 | 5 | 15 | 5 | %66 |
| 8 | 4 | 16 | 0 | %71 |
| 9 | 4 | 16 | 4 | %75 |
| 10 | 6 | 30 | 0 | %77 |
| 11 | 7 | 35 | 7 | %80 |
| 12 | **4** | 24 | 0 | **%36** |
| 13 | 11 | 66 | 11 | %100 |
| 14 | 8 | 56 | 0 | %76 |
| 15 | 7 | 49 | 7 | %71 |
| 16 | 9 | 72 | 0 | %73 |
| 17 | 10 | 80 | 10 | %75 |
| 41 | 22 | 440 | 22 | %77 |
| 80 | 41 | 1640 | 0 | %82 |

Her boyutta: en az 1 tur oynandı, tur sayısı n'i aşmadı, turnuva bittikten sonra
motor **yeni maç üretmiyor** (durma garantisi assert'li), kasa ve kırmızı çizgi tuttu.
n=0 ve n=1 uçlarında çökme yok (maç üretilmiyor, n=1'de tek öğe 1. sırada).

---

# BULGULAR (önem sırasıyla) — düzeltme YAPILMADI, karar koordinatörün

### 🔴 1. Bitiş kuralı "olabilirliğe" değil "aday kümesine" bakıyor — turnuva, elinde kanıt üretecek eşleşmeler dururken kapanıyor

**En ağır vaka n=15: turnuva kapandığında 11 tane oynanmamış aynı puanlı çift
duruyor** (n=12'de 8, n=16'da 7). İlk ölçümde n=12 aykırı görünmüştü
(n=11: 7 tur/%80, n=13: 11 tur/%100 iken n=12: 4 tur/%36); tanı koşumu
(`EmreCorrectN12TaniTest`, 2 test/0 hata, 2026-09-02 01:17) aykırılığın
**n=12'ye özgü olmadığını, kuralın kendisinde olduğunu** gösterdi.

Kod düzeyinde doğrulama (bağımsız olarak işçi 106/ranking-1f de teyit etti) —
`EmreSystemCorrect.kt:631-655`, `analyzeTournamentContinuation`:
```kotlin
val samePointCount = candidateMatches.count { it.team1.points == it.team2.points }
val hasSamePointMatches = if (currentRound == 1) true else samePointCount > 0
```
Fonksiyon **yalnız kendisine verilen aday kümesini** sayıyor; durumda oynanmamış
aynı puanlı çift olup olmadığı hiç sorulmuyor. Fonksiyonun kendi doküman yorumu
"en az bir aynı puanlı eşleşme varsa tur oynanır" diyor — **yorum ile kod
arasındaki fark tam olarak kusurun kendisi.** Ayrıca `currentRound == 1` özel
durumu, ilk turu koşulsuz oynatarak kuralı asimetrik yapıyor: bu satır zaten
"aday kümesi yetersiz bir vekildir" itirafıdır ve düzeltmenin yeri de orasıdır.

Motorun iki bitiş kapısı var: **(A)** tekrarsız tam eşleştirme kurulamadı,
**(B)** `analyzeTournamentContinuation` "hiçbir aday eşleşme aynı puanlı değil"
diyor. Ölçüm — kapanış anında **hâlâ oynanmamış aynı puanlı çift** sayısı:

| n | tur | maç | keskinlik | kapı | aday (aynı puanlı) | **kapanışta oynanmamış aynı puanlı çift** |
|---|---|---|---|---|---|---|
| 8 | 4 | 16 | %71 | B | 4 (0) | **3** |
| 9 | 4 | 16 | %75 | B | 4 (0) | **4** |
| 10 | 6 | 30 | %77 | B | 5 (0) | **2** |
| 11 | 7 | 35 | %80 | B | 5 (0) | **3** |
| 12 | 4 | 24 | %36 | B | 6 (0) | **8** |
| 13 | 11 | 66 | %100 | A | 5 (0) | 0 |
| 14 | 8 | 56 | %76 | B | 7 (0) | **5** |
| 15 | 7 | 49 | %71 | B | 7 (0) | **11** |
| 16 | 9 | 72 | %73 | B | 8 (0) | **7** |

Okunuşu: n=13 dışında **her boyutta** turnuva B kapısından kapanıyor ve kapanış
anında oynanmamış aynı puanlı çiftler duruyor (n=15'te 11 tane!). Yani kural
"aynı puanlı eşleşme **mümkün** mü?" diye sormuyor; **açgözlü hibrit eşleştirmenin
o tur ürettiği aday kümesinde** aynı puanlı çift var mı diye soruyor. Eşleştirme
motoru sıralamayı üstten-alttan tarayarak eşleştirdiği için aynı puanlı takımları
farklı puanlı rakiplerle harcayabiliyor; sonra "aynı puanlı kalmadı" denip turnuva
kapanıyor. Tek "temiz" bitiş n=13'te, yani A kapısından (kanıt tükendiği için).

**Giriş sırasına duyarlılık** (aynı n, farklı başlangıç dizilişi):

| n | tohum 1 | tohum 7 | tohum 31 | tohum 777 | tohum 4242 |
|---|---|---|---|---|---|
| 11 | 7 tur/%80 | 7/%70 | 8/%80 | 7/%100 | 7/%80 |
| 12 | **4/%45** | 9/%100 | 8/%81 | 8/%100 | **4/%36** |
| 13 | 7/%58 | 8/%66 | 6/%75 | 8/%75 | 11/%100 |

Aynı boyutta, aynı kurallarla, yalnız listenin sırası değiştiği için sonuç
**4–9 tur ve %36–%100 kanıt** arasında savruluyor. Kullanıcı açısından bu,
"aynı listeyi farklı sırayla girdim, biri 4 turda bitti biri 9" demektir.

**Erken bitişin BEDELİ ölçüldü** (`erkenBitisinBedeli_gercekSapmaOlcumu`,
n=8..16 × 5 tohum; gerçek sıra bilindiği için sapma = her öğenin doğru
sırasından ortalama |kayma|):

| n | ort. keskinlik | ort. sapma | en kötü sapma | tur aralığı |
|---|---|---|---|---|
| 8 | %88 | 0.15 | 0.50 | 4–7 |
| 9 | %77 | 0.27 | 0.67 | 4–9 |
| 10 | %81 | 0.36 | 0.80 | 5–9 |
| 11 | %82 | 0.33 | 0.55 | 7–8 |
| 12 | %72 | 0.50 | **1.17** | 4–9 |
| 13 | %74 | 0.55 | 1.08 | 6–11 |
| 14 | %79 | 0.57 | 0.86 | 7–11 |
| **15** | %73 | **0.91** | **1.33** | 7–10 |
| 16 | %85 | 0.15 | 0.25 | 8–13 |

İki dürüst okuma, ikisi de rapora ait:
1. **Kanıt raporu dürüst.** Keskinliğin %100 çıktığı her tohumda sapma tam
   **0.00**; keskinlik düştükçe sapma tırmanıyor (n=12 tohum 1: %45 → 1.2 sıra,
   tohum 4242: %36 → 1.0; n=15 tohum 777: %64 → 1.3). Yani `kesinlikRaporu()`
   üzerine eşik kurmak sağlam bir zemin — ölçüm bunu destekliyor.
2. **Hatanın büyüklüğü bu boyutlarda ölçülüdür ve abartılmamalıdır:** ortalama
   sapma 0.15–0.91 sıra, en kötü 1.33 sıra (n=15). Kusur "sıralama çöpe gidiyor"
   değil, "**motor kanıt üretebilecekken üretmeden kapanıyor ve sonuç giriş
   sırasına göre savruluyor**"dur. n>16 için ölçüm yapılmadı; büyük listelerde
   bedelin nasıl büyüdüğü açık sorudur (KesinlikRaporuTest n=100 eğrisi ayrı
   bir başlangıç noktası olabilir).

**Öneri (koordinatör kararı):**
1. Bitiş kararını aday kümesinden alıp **duruma** taşımak: "sıralamada aynı puanlı
   ve henüz oynamamış çift VAR MI?" diye sormak (ölçüm zaten testte: kapanışta
   n=15'te 11 çift). Varsa turnuva bitmemeli, eşleştirme o çiftleri öncelemeli.
2. Ya da HİBRİT'teki gibi **kanıt eşiği**: `kesinlikRaporu().genelYuzde` düşükken
   (ör. < %80) kanıt turu zorlamak. Altyapı hazır, motor zaten raporu üretiyor.
Ölçüm testleri: `EmreCorrectN12TaniTest.n12_erkenBitisininKapisi_veKacirilanKanit`
ve `n11_n12_n13_tohumDuyarliligi`.

### 🟠 2. Yeniden hesaplama (replay) yolu bye'ı ÇIKARIMLA buluyor — iki hâlde sessizce yanlış puan yazıyor

Kusur motorda değil, motora giden yolda: `RankingViewModel.completeRanking`
`emreState == null` iken (süreç ölümü sonrası devam / oturum yeniden kurulumu)
nihai sıralamayı maç kayıtlarından yeniden üretir ve bye'ı
`findByeTeamFromMatches` (`RankingViewModel.kt:931-938`) ile **çıkarır**:

```kotlin
if (songs.size % 2 == 0) return null
val playedTeamIds = matches.flatMap { listOf(it.songId1, it.songId2) }.toSet()
val byeSong = songs.find { it.id !in playedTeamIds }
```

Motorun kendi bye kaydı (`EmreTeam.byeCount` / `byePassed`) hiç kullanılmıyor;
"o turda görünmeyen **İLK** öğe bye'dır" varsayımı iki hâlde kırılıyor.
Ölçüm: `EmreCorrectYenidenHesaplamaYoluTest` (3 test / 0 hata, 2026-09-02 02:55).

**(a) Sağlam yol korumaya alındı (assert):** tam kayıtla replay, canlı sonucu
n=9 (4 tur/16 maç/4 bye), n=15 (7/49/7) ve n=41 (22/440/22) için **birebir**
üretiyor — puanlar, sıra ve çıkarılan bye'ların hepsi aynı. Bu sözleşme artık testli.

**(b) Yarım maç kaydı varsa yanlış takıma bye puanı yazılıyor.**
`filter { isCompleted }` yarım maçı süzdüğü için o turda maçı olan iki takım da
"oynamamış" görünür ve çıkarım listede önce geleni bye sayar. Ölçülen vaka
(n=9, tur 2, yarım maç 2–9): **gerçek bye 5 iken çıkarım 2 dedi** → 2 numaralı
öğeye hak etmediği +1 puan; nihai sıralamanın 5. sırası değişti
(canlı `9,8,6,7,3` → replay `9,8,6,7,4`). Yarım maç kayıtlarının ağaçta
kalabildiği "tur katlanması" gerilemesinde zaten belgeli (`EmreTurKatlanmasiTest`).

**(c) Bir öğe silinince geçmişteki bye puanları sessizce yok oluyor.** Tek sayılı
turnuvada bir öğe silinirse `songs.size` çift olur ve fonksiyon **peşinen null**
döner: o ana kadarki bütün bye'lar puansız kalır. Ölçülen vaka (n=9 → 8):
kalan 13 maç + hak edilen 3 bye puanı beklenirken replay toplamı 13.0,
**kayıp 3.0 puan** — üç ayrı öğenin hak ettiği puan siliniyor.

**Öneri (koordinatör kararı):** bye'ı çıkarımla bulmak yerine KAYITTAN okumak —
tur kapanışında bye geçen öğeyi ayırt edilebilir bir satır olarak maç tablosuna
yazmak, böylece replay tahmin etmek zorunda kalmasın. Asgari düzeltme:
`songs.size % 2` varsayımını kaldırıp "o turda oynamamış öğe **tam olarak bir**
ise bye say, değilse bye yazma" demek — (b) ve (c)'de sessiz yanlış puan yerine
eksik puan bırakır, sıralamayı bozmaz.

#### DÜZELTME SONRASI ÖLÇÜM (2026-09-02 03:38, işçi ranking-d4 asgari düzeltmeyi uyguladı)

`findByeTeamFromMatches` artık "oynamayan **tam olarak bir** ise bye"
(`RankingViewModel.kt:932-937`). Testler yeni sözleşmeye çevrildi, 3/3 yeşil:

| Vaka | Düzeltme öncesi | Düzeltme sonrası |
|---|---|---|
| (a) tam kayıt | replay birebir | **birebir** (n=9/15/41 korunuyor) |
| (b) yarım maç | gerçek bye 5 iken **2'ye hayalet +1**, sıra bozuluyordu (`…7,4`) | çıkarım **null**, hayalet puan yok, sıra canlıyla **aynı** (`9,8,6,7,3`) |
| (c) öğe silme | 3 bye puanı sessizce kayıp | hayalet yok ama **3 bye puanı hâlâ yazılamıyor** (replay toplam 13.0) |

🔎 **Düzeltmenin sınırı — kayda değer:** ViewModel'deki yeni doküman yorumu
(c) için "beklenen 16.0" diyor; **ölçüm 13.0 veriyor ve bu doğrudur.** Sebep:
silinen öğenin OYNADIĞI turlarda rakibi de "oynamamış" görünür, yani oynamayan
sayısı 2 olur ve kural gereği bye yazılmaz. Ölçülen vakada silinen öğe (id 3)
kendi bye'ını 1. turda geçmiş; hak edilen 3 bye (tur 2→5, 3→4, 4→1) ise onun
oynadığı turlarda olduğu için hiçbiri yazılamıyor (`yazilan bye=0`).
Yani düzeltme **yanlış puanı kaldırdı** (asıl kusur), **eksik puanı kaldırmadı**.
Tam çözüm için bye'ın kayıttan okunması gerekir; yorumun "beklenen 16.0" ifadesi
düzeltilmeli, aksi halde ileride "düzeltildi ama tutmuyor" izlenimi yaratır.
Test bu sınırı rakamla basıyor (`hala yazilamayan=3`), sabit sayıya değil
davranışa bağlı olduğu için kayıttan okuma yapılınca da geçerli kalır.

### 🟡 3. CLAUDE.md'deki "n=3 → 1 tur" ölçümü artık geçerli değil
CLAUDE.md (Geliştirilmiş İsviçre bölümü) "Ölçüldü: **n=3 → 1 tur**, n=5 → 2 tur"
diyor. Bugünkü ölçüm: **n=3 → 2 tur (2 maç, 2 bye)**, n=5 → 2 tur (doğru).
Motor mu değişti, doküman mı eskidi belirlenmeli; ikisinden biri düzeltilmeli
(dokümantasyon kararı koordinatörün).

### 🟡 4. Beraberlik turnuvayı KISALTIYOR (sezgiye aykırı)
Aynı n'de berabersiz → her 3. maç berabere:
n=16: 9 tur/72 maç → **7 tur/56 maç** · n=41: 22/440 → **19/380** · n=80: 41/1640 → **40/1600**.
Yarım puanlar puan uzayını genişletiyor, "aynı puanlı eşleşme" bulmak zorlaşıyor ve
bitiş kuralı erken tetikleniyor. Dahası bitiş, beraberlik DESENİNE aşırı duyarlı:
n=9 → berabersiz 4 tur, her 3. berabere 4 tur, her 4. berabere **8 tur**.
Beraberliğin serbest olduğu kullanımda (kullanıcı "eşit" diyebiliyor) turnuva
uzunluğu öngörülemez oluyor. Kanıt/keskinlik eşiği (bulgu 1) bunu da düzeltir.

### ℹ️ 5. Verimlilik kaydı (kıyas için)
n=80'de EMRE_CORRECT **1640 maç** harcayıp %82 kanıta ulaşıyor (tam round-robin 3160).
Karşılaştırma: EMRE_SIRALAMA n=200'de 1365 maçla sıfır hata (CLAUDE.md).
EMRE_CORRECT'in maliyeti yüksek, kanıtı eksik — yöntem seçimi ekranında bu fark
kullanıcıya söylenmeye değer.

### ✅ İhlal bulunmayan alanlar
Puan korunumu (beraberlikli ve hep-beraberlikli dahil), bye adaleti, tekrar eşleşme
yasağı, matchHistory tutarlılığı, tiebreaker determinizmi, replay eşdeğerliği,
durma garantisi, n=0/1 uçları.

---

## Filo notu (koordinatöre, süreçle ilgili)
1. İlk koşum, başka bir oturumun yarım kalmış `HazirListelerSenkronTest.kt:261`
   tip çıkarımı hatası yüzünden **11 dk 35 sn sonra** kırıldı: test derlemesi modül
   geneli olduğu için bir işçinin derlenmeyen dosyası **tüm filoyu** bloke ediyor.
   Öneri: derlenmeyen dosyayı commit'lemeden önce sahibinin sorumluluğu olsun;
   ya da yarım dosyalar `.kt.wip` uzantısıyla tutulsun.
2. Gradle kilidi kuralına uyuldu; kilit için toplam **~50 dk + 25 dk** beklendi,
   asıl koşum 1 dk 8 sn sürdü. Kilit protokolüne ölü kilit denetimi (30 dk) eklendi
   ve kullanıldı (script: oturum scratchpad'inde `kosum103.sh`).
