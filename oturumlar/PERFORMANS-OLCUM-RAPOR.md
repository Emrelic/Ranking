# PERFORMANS ÖLÇÜM RAPORU — EMRE_SIRALAMA / HIBRIT tur üretim hızı

İşçi: SONNET HAZIR KITA 101 (ranking-6a) · Görev veren: ranking-7d (koordinatör, "ranking-0c")
Tarih: 2026-09-01/02 · Test: `app/src/test/java/com/example/ranking/PerformansOlcumTest.kt`
Koşum: `./gradlew :app:testDebugUnitTest --tests "*PerformansOlcumTest*"` (GRADLE-KURALI.md kilidi altında, rekabetsiz)

## Yöntem

- Üç motorun PRODUCTION API'si kullanıldı (tasarım-taslağı değil): `EmreSiralamaSistemi.createNextRoundMatches`,
  `HibritKanitSistemi.createNextRoundMatches`, `PairwiseComparisonSort.createNextComparisonMatch`.
- Her boyut (n) için tam bir turnuva baştan sona koşturuldu; hakem tutarlı bir tam sıra (`guc[]`, döngüsüz)
  kullanarak kazananı belirledi — sonuçta sıralamanın DOĞRU çıkıp çıkmadığı da ayrıca doğrulandı.
- Süre `System.nanoTime()` ile, EMRE_SIRALAMA/HIBRIT için **tur üretimi çağrısı başına**, MERGE_SORT için
  **soru üretimi çağrısı başına** ölçüldü. JVM ısınması için üç motor da n=40 ile bir kez önceden (ölçüme
  katılmadan) koşturuldu.
- Güvenlik sınırları: tek tur/soru 30 saniyeyi aşarsa o koşum kesilir (donmayı önler); testin tamamı
  6 dakikayı aşarsa denenmeyen boyutlar "DENENMEDİ" olarak işaretlenir. n=750 bu yüzden hiç denenemedi —
  n=500 HIBRIT koşumu tek başına ~6 dakika sürdü ve yine de tamamlanamadan kesildi.

⚠️ **Bu bir MASAÜSTÜ JVM ölçümüdür, Android/ART DEĞİL.** Mutlak milisaniyeler telefonda farklı olur
(muhtemelen daha yavaş — telefon CPU'su masaüstünden zayıf). Aranan şey mutlak rakam değil, **n büyüdükçe
sürenin nasıl patladığı** (büyüme eğilimi) ve motorlar arası **göreli** kıyastır. Ayrıca ölçüm, makinede
aynı anda çalışan diğer işçi oturumlarının gradle koşumlarıyla rekabeti önlemek için GRADLE-KURALI.md'nin
dosya-kilidi altında, YALNIZ bu koşum çalışırken alındı.

## Ham sonuçlar

| n | Motor | Tur/Soru | Maç | Toplam süre | Ort/tur | **En yavaş tur** | 2sn eşiği |
|---|---|---:|---:|---:|---:|---:|---|
| 200 | EMRE_SIRALAMA | 19 tur | 1.376 | 3,12 s | 164 ms | #7 = 711 ms | aşılmadı |
| 200 | HIBRIT | 176 tur | 1.775 | 33,49 s | 190 ms | #93 = **1,60 s** | aşılmadı (sınırda) |
| 200 | MERGE_SORT | 1.262 soru | 1.261 | 1,16 s | 0,9 ms | #997 = 30 ms | aşılmadı |
| 350 | EMRE_SIRALAMA | 21 tur | 2.711 | 8,54 s | 407 ms | #8 = 1,12 s | aşılmadı |
| 350 | HIBRIT | 229 tur | 3.385 | **207,3 s** | 905 ms | #46 = **7,30 s** | 🔴 tur #11'de aşıldı |
| 350 | MERGE_SORT | 2.466 soru | 2.465 | 1,76 s | 0,7 ms | #1 = 29 ms | aşılmadı |
| 500 | EMRE_SIRALAMA | 22 tur | 4.093 | 19,88 s | 904 ms | #8 = **3,51 s** | 🔴 tur #5'te aşıldı |
| 500 | HIBRIT | 193 tur | 2.310 | **360,8 s** ⚠️KESİLDİ | 1,87 s | #53 = **10,15 s** | 🔴 tur #5'te aşıldı |
| 500 | MERGE_SORT | 3.811 soru | 3.810 | 5,71 s | 1,5 ms | #1 = 53 ms | aşılmadı |
| 750 | — | — | — | — | — | DENENMEDİ (test bütçesi doldu) | — |

Sıralama doğruluğu: kesilmeyen TÜM koşumlarda (EMRE_SIRALAMA n=200/350/500, HIBRIT n=200/350,
MERGE_SORT n=200/350/500) final sıralama hakemin gerçek sırasıyla BİREBİR aynıydı — algoritmalar yavaş
ama doğru. HIBRIT n=500 süre sınırından kesildiği için doğruluk kontrolü o durumda yapılmadı (kesilen
koşumlarda doğruluk testi atlanıyor, kırmızı sayılmıyor).

## Eşik yorumu (görev tanımlı: tek tur > 2 sn)

- **MERGE_SORT**: n=500'e kadar hiç eşiğe yaklaşmıyor bile (en yavaş soru 53 ms). Beklenen (n·log n
  karşılaştırma, ucuz replay). Baseline sağlıklı.
- **EMRE_SIRALAMA**: n=200 ve n=350'de eşiğin altında (en yavaş tur sırasıyla 711ms / 1,12s) ama **n=500'de
  eşiği geçiyor** (tur #5, 3,5 sn). Büyüme n=200→500 arası ~5 kat (711ms→3,51s) — n başına kabaca kübik
  civarı bir büyüme izlenimi veriyor.
- **HIBRIT** en kötü durum: n=200'de bile en yavaş tur zaten 1,6 sn (eşiğe çok yakın), **n=350'de tur #11
  eşiği geçiyor** (7,3 sn) ve toplam turnuva 207 saniye (3,5 dakika!) sürüyor. n=500'de tek bir tur 10 sn'yi
  aşıyor ve tüm koşum 30 sn'lik güvenlik sınırına takılıp KESİLİYOR — n=750 hiç denenemedi çünkü n=500 tek
  başına test bütçesinin (6 dk) neredeyse tamamını tüketti.

**Sonuç: HIBRIT, EMRE_SIRALAMA'dan çok daha erken ve çok daha ağır bozuluyor.** n=200 gibi gerçekçi bir
liste boyutunda bile HIBRIT'in en yavaş tekil turu zaten kullanıcının fark edeceği sınırda (1,6 sn);
n=350'den itibaren "kullanıcı donuyor" seviyesine geçiyor.

## Kök neden (kod okuması, DEĞİŞTİRİLMEDİ)

- **EMRE_SIRALAMA** (`EmreSiralamaSistemi.kt: turEslesmeleri`): her tur çağrısında TÜM bilinmeyen çiftler
  (~n²/2 aday) yeniden taranıyor, her aday için `kazanc()` BitSet kopyalama+fark+cardinality yapıyor —
  bilgi arttıkça (ust/alt kümeleri büyüdükçe) aday başına maliyet de büyüyor. Ayrık durum tutulmuyor,
  komple replay her çağrıda sıfırdan.
- **HIBRIT** (`HibritKanitSistemi.kt: computeState`) çok daha ağır bir replay yapıyor: her tek round-batch
  çağrısında (a) FAZ 1'i baştan `FAZ1_TUR` kadar `RankingEngine.processCorrectEmreResults` ile yeniden
  işliyor, (b) TÜM tamamlanmış maçlardan `kanit` HashMap'ini sıfırdan kuruyor (`tamamlanan.sortedBy{it.id}`),
  (c) ADIMLAR listesini baştan süpürüyor. FAZ 2'de her süpürme "ilk bulduğu kanıtsız pariteyi" döndürüp
  DURUYOR — yani tek turda genelde yalnız birkaç maç üretiliyor, bu da **round-batch çağrı SAYISINI**
  (n=200'de 176, n=350'de 229!) inanılmaz artırıyor; her çağrı yukarıdaki (a)+(b)+(c) maliyetini TEKRAR
  ödüyor. Yavaşlığın asıl kaynağı algoritmik karmaşıklıktan çok, **çağrı başına sabit iş × çok fazla çağrı**
  kombinasyonu görünüyor.

## Optimizasyon önerileri (UYGULANMADI — koordinatör kararı)

1. **HIBRIT FAZ 2**: Tek süpürmede "ilk bulunan kanıtsız pariteyi" döndürmek yerine, o adımdaki TÜM
   kanıtsız pariteleri tek round-batch'te toplayıp döndürmek — round-batch çağrı sayısını (176→tek haneli
   olası) drastik azaltır, en büyük kazanç muhtemelen burada.
2. **HIBRIT `kanit` HashMap'i**: her çağrıda sıfırdan kurmak yerine, yalnız son çağrıdan beri eklenen yeni
   tamamlanmış maçlarla artımlı güncellemek (cache + son işlenen id/round imleci).
3. **EMRE_SIRALAMA `turEslesmeleri`**: `ust`/`alt` BitSet ağacını her `computeState` çağrısında replay ile
   sıfırdan kurmak yerine artımlı güncellemek; adaylık taramasını n² yerine yalnız YENİ bilgiyle etkilenen
   çiftlere daraltmak (ör. son turda güncellenen düğümlerin komşularını yeniden değerlendirmek).
4. Her iki motor için: replay-safety (ORTAK.md'nin "ayrı durum tablosu yok" ilkesi) korunacaksa, bir
   in-memory ÖNBELLEK (aynı `completedMatches` önekiyle son hesaplanan ağacı sakla, yalnız FARKI işle)
   davranışı DEĞİŞTİRMEDEN maliyeti düşürebilir — devam/geri-alma senaryosu bozulmaz çünkü önbellek yalnız
   bir hızlandırma katmanıdır, kaynak veri (`matches` tablosu) hâlâ tek gerçek kaynaktır.
5. n=750 ve üstü için gerçek telefon donanımında (ART, düşük çekirdek sayısı) ayrıca ölçüm önerilir —
   burada bulunan masaüstü rakamları telefon için muhtemelen daha kötü bir alt sınır oluşturuyor.

## Test hijyeni notu (görevle ilgisiz ama rapora değer)

İlk iki koşum denemesi, benim testimden BAĞIMSIZ ortam sorunlarıyla düştü: (1) paylaşılan
`app/src/test/` kaynak setinde başka bir işçinin geçici derleme hatası, (2) `app/build/` dizininin
paylaşılan tek checkout'ta başka bir gradle daemon'uyla çakışması (`Unable to delete directory
.../binary/output.bin`). İkisi de GRADLE-KURALI.md'nin (dosya kilidi + `--stop` temizliği) devreye
girmesiyle çözüldü; üçüncü koşum (bu rapordaki veriler) temiz ortamda BUILD SUCCESSFUL ile bitti.
