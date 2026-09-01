# EMRE_CORRECT — Uzun Koşum Değişmezleri Sınavı (RAPOR)

İşçi: OPUS HAZIR KITA 103 (`ranking-6a [686cfc]`) · Koordinatör: `ranking-7d`
Tarih: 2026-09-02 · Motor koduna DOKUNULMADI (yalnız `app/src/test/` altına yeni dosya).

**Sonuç: 9 test / 9 YEŞİL, 0 kırmızı.** Dosya:
`app/src/test/java/com/example/ranking/EmreCorrectDegismezlerTest.kt`
Koşum: `./gradlew testDebugUnitTest --tests "*EmreCorrectDegismezlerTest*"` → BUILD SUCCESSFUL (1 dk 8 sn, toplam test süresi 5.6 sn).

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

### 🟠 1. n=12 aykırılığı: turnuva %36 kanıtla, 4 turda bitiyor
Komşuları n=11 (7 tur, %80) ve n=13 (11 tur, %100) iken **n=12 yalnız 4 turda
24 maçla bitiyor ve komşuluk sınırlarının ancak %36'sı kanıtlı** kalıyor —
yani sıralamanın üçte ikisi tiebreaker tahmini. Bu, kullanıcının "12 öğe girdim,
4 turda bitti ve sıralama tuhaf" diyeceği tipik vaka.
Sebep hipotezi (doğrulanmadı): 12'de puan grupları öyle dağılıyor ki hiçbir aday
eşleşme aynı puanlı kalmıyor, `analyzeTournamentContinuation` turnuvayı kapatıyor.
**Öneri:** bitiş kuralına "kanıt eşiği" eklemek (ör. keskinlik < %60 ise kanıt turu
zorla) — HİBRİT'teki kanıt turu mantığı burada da uygulanabilir. Ölçüm testte hazır:
`turSayisiVeBitis_tekCiftUclari_olculur`.

### 🟡 2. CLAUDE.md'deki "n=3 → 1 tur" ölçümü artık geçerli değil
CLAUDE.md (Geliştirilmiş İsviçre bölümü) "Ölçüldü: **n=3 → 1 tur**, n=5 → 2 tur"
diyor. Bugünkü ölçüm: **n=3 → 2 tur (2 maç, 2 bye)**, n=5 → 2 tur (doğru).
Motor mu değişti, doküman mı eskidi belirlenmeli; ikisinden biri düzeltilmeli
(dokümantasyon kararı koordinatörün).

### 🟡 3. Beraberlik turnuvayı KISALTIYOR (sezgiye aykırı)
Aynı n'de berabersiz → her 3. maç berabere:
n=16: 9 tur/72 maç → **7 tur/56 maç** · n=41: 22/440 → **19/380** · n=80: 41/1640 → **40/1600**.
Yarım puanlar puan uzayını genişletiyor, "aynı puanlı eşleşme" bulmak zorlaşıyor ve
bitiş kuralı erken tetikleniyor. Dahası bitiş, beraberlik DESENİNE aşırı duyarlı:
n=9 → berabersiz 4 tur, her 3. berabere 4 tur, her 4. berabere **8 tur**.
Beraberliğin serbest olduğu kullanımda (kullanıcı "eşit" diyebiliyor) turnuva
uzunluğu öngörülemez oluyor. Kanıt/keskinlik eşiği (bulgu 1) bunu da düzeltir.

### ℹ️ 4. Verimlilik kaydı (kıyas için)
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
