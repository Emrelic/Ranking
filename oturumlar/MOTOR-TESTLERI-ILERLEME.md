# MOTOR TESTLERİ — ilerleme defteri

Oturum: MOTOR TESTLERİ (işçi) · Model: Opus 5 · Koordinatör: `ranking-a3 [7558ae]`
Branch: `ileri-tusu-asagida-crash-fix`

Amaç: mevcut motorları (EMRE_CORRECT, MERGE_SORT, CsvReader) **kırmak**.
Geçen test değersiz, kırılan test altın. Kusur bulunca DÜZELTİLMEZ — bildirilir.

---

## SAYIM (son koşum)

| dosya | test | geçti | kırık |
|---|---|---|---|
| `EmreSystemDeepTest.kt` | 44 | 44 | 0 |
| `PairwiseDeepTest.kt` | 27 | 27 | 0 |
| `CsvReaderDeepTest.kt` | 37 | 37 | 0 |
| **toplam** | **108** | **108** | **0** |

⚠️ Bu "hiç kusur yoktu" demek DEĞİL. Üç kusur bulundu, koordinatöre bildirildi,
koordinatör düzeltti (commit `78fc364`), testler ondan sonra yeşile döndü.
Aşağıdaki üç testin yeşilliği düzeltmelerin doğrulamasıdır.

Motor dosyalarına **hiç yazılmadı** (yalnız okundu).

---

## BULUNAN KUSURLAR

### 🔴 KUSUR #1 — Yetim maç hayalet puan üretiyordu
**Yer:** `EmreSystemCorrect.processRoundResults`
**Test:** `EmreSystemDeepTest.sinir_yetimMacKaydi_cokmuyor` (ölçüldü: KIRIK → düzeltildi)

Silinmiş bir öğeye ait maçta `songToTeamMap[songId]` null dönüyor ve maç
**eşleşme geçmişine yazılmıyordu**. Ama puan bloğu bu kontrolden bağımsızdı ve
**puanı veriyordu**. Aynı maç aynı anda "hiç oynanmadı" ve "kazanıldı" sayılıyordu.
Ölçüm: beklenen 1.0 puan, bulunan 2.0.
Etki: öğe silinmiş listede takım puanı şişer, eşleştirme grupları kayar.

### 🔴 KUSUR #2 — Tiebreaker karşılaştırıcısı geçişsizdi, TimSort çöküyordu
**Yer:** `EmreSystemCorrect.applySamePointTiebreaker`
**Testler:** `gecisliik_63TakimDuzenliTurnuva_*`, `gecisliik_41TakimDuzenliTurnuva_*`

Eski zincir: H2H → **direkt maç** → en az mağlubiyet → tur öncesi sıralama.
2. kriter yalnız H2H eşitken devreye giriyordu; H2H eşit + sonuçlar döngüsel
olduğunda karşılaştırıcı geçişsiz oluyordu. `sortedWith` → java TimSort, n≥32'de
sözleşmeyi denetler ve `IllegalArgumentException: Comparison method violates its
general contract!` atar → **puan tablosu ekranı çökerdi.**

Tetiklenme koşulu ayrı bir JVM denemesiyle daraltıldı:
- **çöküyor:** aynı puanlı n=41 ve n=63 "düzenli turnuva" (herkes tam (n-1)/2
  galibiyet → H2H ve mağlubiyet herkeste eşit, sonuçlar yoğun döngüsel)
- **çökmüyor:** dairesel (circulant) düzenli desen — fazla muntazam
- **çökmüyor:** 33 takım / 11 ayrık taş-kağıt-makas üçlüsü — döngü seyrek

Yani "veri bağımlı" değil, **yoğun döngüye bağlı**. Ölçüm kesin.

### 🔴 KUSUR #3 — Gömülü listelerde BOM temizlenmiyordu
**Yer:** `CsvReader.parseText` / `RankingRepository.importPreparedList`
**Test:** `CsvReaderDeepTest.bomKarakteri_parseTextTarafindanTemizlenir`

`readCsvFromUri` BOM'u BAYT düzeyinde siliyor, ama gömülü hazır listeler
`readBytes().toString(UTF_8)` ile okunuyor — o yolda BOM kalıyordu. BOM'lu
kaydedilmiş bir asset'te ilk başlık anahtarı `"No"` yerine `"﻿No"` olur ve
tablo görünümünde ilk sütun kaybolur. (Şu an disktekilerin hiçbirinde BOM YOK —
kontrol edildi; risk, CSV'lerin elle düzenlenmesinden geliyor.)

### 🟡 Gözlem — "direkt maç" kriteri yalnız döngüsel durumda konuşuyordu
H2H puanı zaten grup içi galibiyetleri sayıyor. İki takımın H2H'i eşitken
aralarında kesin sonuçlu maç varsa, bu ancak grup içinde başka galibiyetlerle
dengelendiğinde olur — ve tam bu durum döngüyü doğurur. Kriter 2 pratikte
kriter 1'in çözemediği yerde değil, kriter 1'in **çelişkili** olduğu yerde
çalışıyordu.

---

## BELGELENEN (kusur değil ama bilinmesi gereken) DAVRANIŞLAR

Bunlar kırık değil; testler mevcut davranışı **sabitliyor** ki sessizce değişmesin.

- `PairwiseComparisonSort`: beraberlik (`winnerId = null`) geldiğinde **aday
  kaybetmiş** sayılıyor ve alta yerleşiyor. Test: `beraberlik_adayKaybetmisSayilir_*`
- `CsvReader`: ilk satır 2+ alanlıysa **koşulsuz başlık** sayılıyor. Başlıksız
  çok sütunlu bir dosyanın ilk öğesi sessizce yutuluyor.
  Test: `basliksizCokSutunluDosya_ilkSatirKAYBOLUYOR`
- `CsvReader`: 4. sütunu boş olan satır uyarısız düşürülüyor.
  Test: `bosAdliSatirSessizceDusuyor`
- `CsvReader`: 9 sütunlu bir dosyada 3 alanlı bozuk satır, 3-sütun kalıbına
  düşüp adı 3. alandan alıyor (sessiz veri kayması).
  Test: `bozukSatir_dorttenAzSutun_sessizceKaydiriyor`

---

## GERÇEK VERİ DOĞRULAMASI

`diskteki_tumHazirListeler_ayristirilabiliyor` testi diskteki **31 CSV'nin
tamamını** `importPreparedList` ile birebir aynı şekilde okuyor
(`readBytes().toString(UTF_8)` + `parseText`) ve şunları doğruluyor:
- toplam **1854 veri satırı → 1854 öğe** (satır kaybı yok)
- hiçbir öğenin adı boş değil
- öğe adı 4. sütundan geliyor (gömülü örneklerle ayrıca sınandı:
  Osmanlı padişahları, elementler, filmler, Şebnem Ferah)

Şebnem Ferah listesindeki tırnaklı-virgüllü gerçek alan
(`"Şebnem Ferah, Sezen Aksu / Şebnem Ferah"`) tek parça kalıyor.

---

## KAPSANAMAYANLAR (dürüst liste)

- `CsvReader.detectEncodingAndRemoveBOM` (BOM bayt tespiti, **windows-1254
  Türkçe fallback**) private ve yalnız `readCsvFromUri(Context, Uri)` yolundan
  çağrılıyor → JVM birim testinden erişilemedi. **Bayt düzeyi encoding tespiti
  TEST EDİLMEDİ.**
- Emre motorunda backtrack zinciri (PÇT-EBT karar matrisi) ve KEAT seçim
  bayrağı **doğrudan** test edilmedi; yalnız dış davranışından (tekrarsız tam
  eşleştirme, maç numaraları) doğrulandı.
- `RankingViewModel` / `RankingScreen` entegrasyonu benim dosyam değil.
- Eşitlik bozma son geçişinin güvenlik sayacı dolduğunda ne olduğu ayrıca
  zorlanmadı; yalnız "takım kaybolmuyor + deterministik" doğrulandı.

---

## SIRADAKİ

Koordinatör yeni iş verdi: `SwissSystem.kt` (ranking-5e) ve
`EliminationSystem.kt` (ranking-f9) motorları yazılıyor; ortaya çıktıkça
`YeniMotorlarCaprazTest.kt` ile kırmaya çalışacağım. Motorlar henüz commit
edilmedi.

---

# İKİNCİ GEÇİŞ — tur kapanışı, çift puanlama, küçük turnuvalar

## SAYIM (ikinci koşum)

| dosya | test | geçti | kırık |
|---|---|---|---|
| `EmreSystemDeepTest.kt` | 56 | 56 | 0 |
| `PairwiseDeepTest.kt` | 30 | 30 | 0 |
| `CsvReaderDeepTest.kt` | 41 | 41 | 0 |
| **toplam** | **127** | **127** | **0** |

## 🔴 BENİM HATAM — dört yanlış beklenti

İlk yazımda dört test kırıldı ve **dördü de motor kusuru değildi, benim
beklentim yanlıştı.** Kayda geçiriyorum ki "test kırıldı = motor bozuk"
sanılmasın:

| test | beklediğim | ölçülen | gerçek |
|---|---|---|---|
| n=3 hepsi berabere | 3 tur (tam round-robin) | 1 tur | bye 1 puan getirdiği için puanlar ayrışıyor, "aynı puanlı eşleşme yok" kuralı turnuvayı bitiriyor — KURALA UYGUN |
| n=5 hepsi berabere | 5 tur | 2 tur | aynı sebep + tekrarsız tam eşleştirme kurulamıyor — KURALA UYGUN |
| yarım maçın ikilisi 2. turda eşleşir | eşleşir | eşleşmiyor | n=4'te o ikilinin tamamlayıcısı oynanmış ikili; yasaklı olduğu için yapısal olarak imkânsız |
| byeTeam geçilmezse aynı takım yine bye geçer | takım 5 | takım 3 | takım 5 mağlubiyeti olmadığı için tiebreaker'da yükseldi; bye en alttakine gitti. Asıl tehlike (byePassed işaretlenmiyor) doğru, testin ifadesi yanlıştı |

Testler düzeltildi; ölçülen davranış kurala uygun olduğu için **kurala göre**
yeniden yazıldı.

## TEHLİKE SÖZLEŞMELERİ (yeni sabitlenenler)

### `belgeleme_turKapanisi_motorIDEMPOTENT_DEGIL`
`processRoundResults` aynı maç listesiyle iki kez çağrılırsa **puanlar ikiye
katlanır**; motor "bu turu işledim mi" diye bakmaz. Maç geçmişi küme olduğu
için katlanmaz — yani puan ile geçmiş ayrışır. Koruma ÇAĞIRANDA olmak zorunda.
Bugün düzeltilen "kaybolan oy" ve "resume'da çift puanlama" kusurlarının ikisi
de bu sözleşmenin korunmamasından geliyordu.

### `turKapanisi_byeGecilmezseByePuaniVeByeIsaretiKaybolur`
Çağıran `byeTeam`'i geçirmezse: bye puanı verilmez **ve** `byePassed`
işaretlenmez → takım "hiç bye geçmemiş" sayılmaya devam eder, ileride ikinci
byeyi alabilir. Karşılaştırmalı olarak doğru çağrının 1 puan + `byePassed=true`
ürettiği de aynı testte sabitlendi.

### Tek sayılı küçük turnuvalarda bitiş (ölçülen)
Hepsi berabere senaryosunda: **n=3 → 1 tur, n=5 → 2 tur.** Tam round-robin'e
ULAŞILMAZ. Sebep: bye 1 puan, beraberlik 0.5 puan; bye alan takım tek başına
yukarı çıkıyor ve "aynı puanlı eşleşme yok" kuralı devreye giriyor.
Çift sayıda böyle bir sapma yok: n=4 hepsi berabere → 3 tur, 6 maç, tam
round-robin.

## CSV — başlık sezgisi (kapatılmadı, görünür bırakıldı)
`belgeleme_basliksizIkiSutunluListe_ilkSatirYUTULUYOR_bkzYAPILACAKLAR`
9a15e55 ile "ilk hücre tam sayıysa veridir" kuralı geldi; sayısal listelerdeki
kaybı kapatıyor. Ama `Sezen Aksu,Firuze` gibi sayısız listelerde ilk satır hâlâ
yutuluyor. 2 sütunlu listede başlık olup olmadığı yapısal olarak belirsiz;
doğru çözüm içe aktarmada sormak → YAPILACAKLAR.md.
