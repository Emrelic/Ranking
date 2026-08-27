# SWISS MOTORU — şartname

| alan | değer |
|---|---|
| AD | SWISS MOTORU |
| DİZİN | `C:/Users/emrem/OneDrive/Belgeler/Projeler/Ranking` |
| BRANCH | `ileri-tusu-asagida-crash-fix` |
| ClaudEmre | HAYIR — işçi oturumu |
| KOORDİNATÖR | `ranking-a3 [7558ae]` |

🔴 **ÖNCE `oturumlar/ORTAK.md` OKU.** Dosya sahipliği, replay deseni ve
saf-Kotlin kuralı orada.

## SENİN DOSYALARIN
```
✅ app/src/main/java/com/example/ranking/ranking/SwissSystem.kt      (YENİ)
✅ app/src/test/java/com/example/ranking/SwissSystemTest.kt          (YENİ)
✅ oturumlar/SWISS-MOTORU-ILERLEME.md                                (YENİ)
```
Başka hiçbir dosyaya yazma. `RankingEngine.kt`'deki eski Swiss kodunu
OKU ama DEĞİŞTİRME — koordinatör onu senin motorunla değiştirecek.

## NİÇİN YENİDEN YAZILIYOR — ölçülmüş kusurlar

`RankingEngine.kt:520-618` `createSwissMatchesAdvanced` şu an:

1. 🔴 **BYE YOK.** `half = size / 2`, döngü `0 until half`. 7 takımda
   half=3 → çiftler (0,3)(1,4)(2,5); **6 numaralı takım hiç eşleşmez ve
   hiç puan almaz.** Sessizce turdan düşer.
2. 🔴 **Puan grubunda tek kalan takım da düşer.** `while (available.size >= 2)`
   döngüsü tek takımı geride bırakır; o takım o tur hiç oynamaz.
3. 🔴 **TEKRAR EŞLEŞME SERBEST.** "If no fresh pairing found, pair the
   first two available" — İsviçre sisteminin en temel kuralını çiğniyor.
4. 🔴 **`matchNumber` hiç atanmıyor** (0 kalıyor) → oylama sırası bozuk.
5. 🟠 **Tur 1 `songs.shuffled()`** — rastgele, replay'i kırar.

## YAPACAĞIN

`object SwissSystem` yaz. Referans mimari: `EmreSystemCorrect.kt` ve
`PairwiseComparisonSort.kt` (ikisini de oku — bu proje motorları böyle yazıyor).

### Kurallar — İsviçre sistemi
```
① İki takım birbiriyle YALNIZ BİR KEZ eşleşir  ← en kırmızı çizgi
② Her turda TAM eşleştirme: çift takımda n/2 maç,
   tek takımda (n-1)/2 maç + 1 BYE
③ BYE: en alttan başlayarak bye geçmemiş ilk takım.
   Herkes bye geçtiyse en az bye geçmiş, en alttaki takım.
④ Puan: galibiyet 1 · beraberlik 0.5 · bye 1
⑤ Eşleştirme: aynı puan grubundan başla; grup tekse
   komşu gruba taşır (float)
⑥ Tur sayısı: ceil(log2(n)) varsayılan, ama tekrarsız tam
   eşleştirme kurulamıyorsa turnuva ERKEN biter
⑦ matchNumber: üstten seçilen eşleşme 1,2,3... (oylama sırası ASC)
```

### 🔴 Kritik: eşleştirme GARANTİLİ olmalı
Açgözlü (greedy) eşleştirme çıkmaza girebilir: son iki takım kalır ve
onlar zaten oynamıştır. Bu durumda "tekrar eşleştir" ÇÖZÜM DEĞİLDİR
(kural ①). Bunun yerine **geri izleme (backtracking)** kur: eşleştirme
kurulamazsa bir önceki seçime dön ve başka eşi dene.

⚠️ Geri izleme üstel patlayabilir. **Bir güvenlik sayacı koy** (örn. en
çok 50.000 deneme); sayaç dolarsa `canContinue = false` döndür ve
turnuvayı dürüstçe bitir — sonsuz döngüye girme. `EmreSystemCorrect.kt`
içindeki `executeHybridPairingEngine` bu sayaç desenini zaten kullanıyor,
ona bak.

### İstenen API
```kotlin
object SwissSystem {
    const val METHOD = "SWISS"

    data class SwissTeam(
        val song: Song, val points: Double,
        val played: Int, val won: Int, val drawn: Int, val lost: Int,
        val byeCount: Int, val opponentIds: Set<Long>
    )
    data class SwissState(
        val teams: List<SwissTeam>, val currentRound: Int,
        val maxRounds: Int, val isComplete: Boolean
    )
    data class PairingResult(
        val matches: List<Match>, val byeTeam: SwissTeam?,
        val canContinue: Boolean, val reason: String
    )

    /** Tamamlanmış maçlardan durumu baştan kurar (replay). */
    fun computeState(songs: List<Song>, completedMatches: List<Match>): SwissState

    /** Sıradaki turun eşleştirmesi. canContinue=false ise turnuva biter. */
    fun createNextRound(state: SwissState, completedMatches: List<Match>): PairingResult

    /** Final sıralama: puan → Buchholz → galibiyet → id */
    fun calculateResults(songs: List<Song>, completedMatches: List<Match>): List<RankingResult>

    fun recommendedRoundCount(teamCount: Int): Int
}
```

### Tiebreaker zinciri (sıralı)
```
① puan  ② Buchholz (rakiplerin puan toplamı)  ③ galibiyet sayısı
④ aralarındaki maç  ⑤ song.id  ← ⑤ deterministik son çare, ATLAMA
```
⚠️ Karşılaştırıcı **geçişli (transitive)** olmalı. "Aralarındaki maç"
kriterini doğrudan `sortedWith`e koyarsan A>B>C>A döngüsü kurulabilir ve
Kotlin `IllegalArgumentException: Comparison method violates its general
contract!` atar. Bunu ya yalnız ikili eşitlik bozmada uygula, ya da toplam
sıralı bir anahtara indirge.

## TESTLER — en az bunlar
```
n=0,1,2,3 sınır · n=7 (tek→bye) · n=8 (çift)
🔴 hiçbir çift İKİ KEZ eşleşmiyor (n=8, tüm turlar boyunca)
🔴 her turda HERKES ya oynuyor ya bye — kimse düşmüyor
🔴 bye rotasyonu adil: aynı takım ikinci byeyi herkes almadan almıyor
hepsi berabere senaryosu · yetim maç kaydı (silinmiş id) → çökmüyor
replay: aynı maç listesi → aynı sonuç (iki kez çağır, eşitle)
n=64 başarım: createNextRound < 2 saniye
```

## TESLİM
Bitince koordinatöre (`ranking-a3 [7558ae]`) yaz:
kaç fonksiyon · kaç test · kaç test GEÇTİ · hangi sınır durumu kapsanmadı ·
geri izleme sayacı hangi n'de doldu (ölçtüysen).
