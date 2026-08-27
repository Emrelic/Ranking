# MOTOR TESTLERİ — şartname

| alan | değer |
|---|---|
| AD | MOTOR TESTLERİ |
| DİZİN | `C:/Users/emrem/OneDrive/Belgeler/Projeler/Ranking` |
| BRANCH | `ileri-tusu-asagida-crash-fix` |
| ClaudEmre | HAYIR — işçi oturumu |
| KOORDİNATÖR | `ranking-a3 [7558ae]` |

🔴 **ÖNCE `oturumlar/ORTAK.md` OKU.**

## SENİN DOSYALARIN
```
✅ app/src/test/java/com/example/ranking/EmreSystemDeepTest.kt      (YENİ)
✅ app/src/test/java/com/example/ranking/PairwiseDeepTest.kt        (YENİ)
✅ app/src/test/java/com/example/ranking/CsvReaderDeepTest.kt       (YENİ)
✅ oturumlar/MOTOR-TESTLERI-ILERLEME.md                             (YENİ)
```
⚠️ Mevcut test dosyalarını (`EmreQuickUnitTest.kt`, `EmreFixRegressionTest.kt`)
DEĞİŞTİRME — yenilerini yaz. Diğer kıtalar kendi testlerini yazıyor;
`SwissSystemTest`, `EliminationSystemTest`, `LeagueSystemTest` SENİN DEĞİL.

## NİÇİN BU İŞ

Bu projede iki motor **üretimde ve güvenilir sayılıyor** ama test kapsamı
dar: `EMRE_CORRECT` (canlı hibrit eşleştirme, 850 satır) ve `MERGE_SORT`
(binary insertion + replay, 161 satır). Bugün bu iki motorda yedi kritik
kusur bulundu — hiçbirini mevcut testler yakalamamıştı.

Senin işin: **bu motorları kırmaya çalışmak.** Test yazmak değil, kusur
aramak. Geçen bir test değersizdir; kırılan bir test altındır.

## ① EmreSystemDeepTest.kt

`ranking/EmreSystemCorrect.kt` OKU (850 satır). Sonra bu kuralların her
birini ayrı testle sına — CLAUDE.md'deki kural listesi:
```
🔴 İki takım birbiriyle YALNIZ BİR KEZ eşleşir  ← en kırmızı çizgi
   n=8,16,32 için TÜM turlar boyunca doğrula
🔴 Her turda tam eşleştirme: çiftte n/2 maç, tekte (n-1)/2 + 1 bye
   HİÇ KİMSE turdan düşmüyor
🔴 Bye rotasyonu: en alttan, bye geçmemiş ilk takım.
   Herkes geçtiyse en az geçmiş, en alttaki.
   Aynı takım ikinci byeyi herkes almadan ALMIYOR
🔴 Puan: galibiyet 1 · beraberlik 0.5 · bye 1
🔴 Tiebreaker zinciri TÜM maç geçmişine bakar:
   H2H puanı → direkt maç → en az mağlubiyet → tur öncesi sıralama
🔴 Bitiş: tekrarsız tam eşleştirme kurulamazsa VEYA hiçbir eşleşme
   aynı puanlı değilse turnuva biter
🔴 matchNumber: üstten 1,2,3... — oylama sırası ASC
```
Ek olarak:
```
GEÇİŞLİLİK: 33+ takım aynı puanda ve aralarında taş-kağıt-makas üçlüsü
  varken sortedWith çöküyor mu? ("Comparison method violates its general
  contract!") — n=64, 4-5. tur senaryosu kur
BAŞARIM: n=64 için createHybridPairingSystem < 2 saniye
DETERMİNİZM: aynı maç listesi iki kez → aynı eşleştirme
```

## ② PairwiseDeepTest.kt

`ranking/PairwiseComparisonSort.kt` OKU (161 satır, replay deseni).
```
n=0,1,2,3 sınır
n=50: soru sayısı estimatedTotalComparisons(50) sınırını AŞMIYOR
replay doğruluğu: cevapları karışık sırada ver, aynı sıralama çıkıyor mu
🔴 aynı ikili İKİ KEZ sorulmuyor
🔴 beraberlik (winnerId=null) geldiğinde ne oluyor — belgele
   (kod "aday kaybetti" sayıyor; bu bilinçli mi, test bunu SABİTLESİN)
yarım kalmış sıralama: calculateResults kısmi sırayla çöküyor mu
yetim maç kaydı → çökmüyor
tutarsız kullanıcı: A>B, B>C, C>A cevapları → çökmüyor, bir sıra üretiyor
```

## ③ CsvReaderDeepTest.kt

`utils/CsvReader.kt` OKU (RFC-4180 durum makinesi). Bu parser hazır liste
kütüphanesinin tamamının geçtiği yer — 31 CSV, 1854 öğe.
```
tırnaklı alan içinde VİRGÜL: "Beatles, The" tek alan kalıyor
tırnaklı alan içinde SATIR SONU
"" ile kaçırılmış tırnak
ayraç tespiti: virgül / noktalı virgül / tab
BOM'lu UTF-8 · windows-1254 Türkçe
🔴 GERÇEK DOSYA TESTİ: app/src/main/assets/hazir_listeler/ altındaki
   CSV'lerden birkaçını metin olarak göm ve parseText ile sına —
   4. sütun (öğe adı) doğru geliyor mu
sütun sayısı 1,2,3,4+ olan satırlar
boş satır · yalnız ayraçtan oluşan satır
```

## KURAL — kusur bulursan
🔴 **Kusuru KENDİN DÜZELTME.** Motor dosyaları senin değil. Testi yaz,
kırıldığını GÖSTER, koordinatöre bildir. Düzeltmeyi koordinatör ya da o
dosyanın sahibi yapar.

⚠️ Kırılan testi `@Ignore` ile susturma. Kırık kalsın ve raporunda
"şu test KIRILIYOR, sebebi şu" diye SAYIYLA bildir. Susturulmuş test,
hiç yazılmamış testten kötüdür.

## TESLİM
Koordinatöre (`ranking-a3 [7558ae]`): kaç test yazdın · kaç GEÇTİ · kaç
KIRILDI · kırılanların her biri için hangi kural ihlal ediliyor.
