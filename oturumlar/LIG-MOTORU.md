# LİG MOTORU — şartname

| alan | değer |
|---|---|
| AD | LİG MOTORU |
| DİZİN | `C:/Users/emrem/OneDrive/Belgeler/Projeler/Ranking` |
| BRANCH | `ileri-tusu-asagida-crash-fix` |
| ClaudEmre | HAYIR — işçi oturumu |
| KOORDİNATÖR | `ranking-a3 [7558ae]` |

🔴 **ÖNCE `oturumlar/ORTAK.md` OKU.**

## SENİN DOSYALARIN
```
✅ app/src/main/java/com/example/ranking/ranking/LeagueSystem.kt  (YENİ)
✅ app/src/test/java/com/example/ranking/LeagueSystemTest.kt      (YENİ)
✅ oturumlar/LIG-MOTORU-ILERLEME.md                               (YENİ)
```

## DURUM — bu sistem ÇALIŞIYOR, kırık değil

Diğer iki kıtadan farkın bu: lig fikstürü **doğru**. Circle-method
`createLeagueMatches` n=4 ve n=6 için elle izlendi, tüm çiftler tam bir
kez eşleşiyor, turlar dengeli, rövanş numaralandırması doğru.

Senin işin **sağlamlaştırma ve zenginleştirme**, sıfırdan yazma değil.
`RankingEngine.kt:100-145` (`calculateLeagueResults`) ve `:60-99`
(`createLeagueMatches`) OKU — mantığı oradan taşı, kusurları düzelt.

## ÖLÇÜLMÜŞ KUSURLAR

1. 🔴 **Yetim maçta NPE.** `points[match.songId1]!!` (`:110-124`).
   `points` yalnız `songs`'tan doldurulur; maçta silinmiş bir öğenin
   id'si varsa `!!` çöker → "Sıralama tamamlama hatası". `ResultsViewModel`
   aynı yerde `?: return@forEach` ile korunmuş, motor korunmamış.
   Aynı desen `:319`, `:645`, `:796`, `:1149` satırlarında da var.
2. 🟠 **Averaj/tiebreak zayıf.** Puan eşitliğinde ne olacağı belirsiz.
3. 🟠 **Canlı puan durumu dalı boş.** `RankingViewModel`in
   `calculateCurrentStandings` fonksiyonunda LEAGUE dalı boş bir
   `else if` — koordinatör senin motorunla dolduracak.

## YAPACAĞIN

```kotlin
object LeagueSystem {
    const val METHOD = "LEAGUE"

    data class LeagueRow(
        val song: Song, val played: Int,
        val won: Int, val drawn: Int, val lost: Int,
        val goalsFor: Int, val goalsAgainst: Int,
        val goalDiff: Int, val points: Double, val position: Int
    )
    data class LeagueSettings(
        val doubleRoundRobin: Boolean = false,
        val winPoints: Double = 3.0,
        val drawPoints: Double = 1.0,
        val lossPoints: Double = 0.0
    )

    /** Circle-method fikstür. Tek takımda her turda bir bye. */
    fun createFixture(songs: List<Song>, settings: LeagueSettings): List<Match>

    /** Canlı puan durumu — tur ortasında da doğru. */
    fun standings(songs: List<Song>, completed: List<Match>,
                  settings: LeagueSettings): List<LeagueRow>

    fun calculateResults(songs: List<Song>, completed: List<Match>,
                         settings: LeagueSettings): List<RankingResult>
}
```

### Tiebreaker zinciri (sıralı) — yapılandırılabilir değil, SABİT
```
① puan  ② averaj (goalsFor - goalsAgainst)  ③ atılan gol
④ aralarındaki maç sonucu  ⑤ galibiyet sayısı  ⑥ song.id
```
⚠️ ④ karşılaştırıcıyı geçişsiz yapabilir (A>B>C>A döngüsü →
`IllegalArgumentException: Comparison method violates its general contract!`).
Ya yalnız ikili eşitlik bozmada uygula, ya toplam sıralı anahtara indirge.
🔴 Bunun testini yaz: 3 takım taş-kağıt-makas kurmuş olsun, `sortedWith`
çökmesin.

### 🔴 `!!` YASAK
Yetim maç kaydı gerçek bir senaryo. Her `map[id]` erişimi
`?: return@forEach` / `?: continue` ile korunacak. Bunun testini yaz:
maç listesinde `songs`ta olmayan bir id bulunsun, çökmesin.

### Puan sistemi
Varsayılan 3/1/0. `LeagueSettings` üzerinden değiştirilebilir olsun
(bazı turnuvalar 2/1/0 kullanır).

## TESTLER — en az bunlar
```
n=0,1,2 sınır · n=4 çift · n=5 tek (bye) · n=6
🔴 fikstür: her çift TAM BİR KEZ (çift devirli ise TAM İKİ KEZ)
🔴 tek takımda her takım TAM BİR KEZ bye geçiyor
🔴 yetim maç id'si → çökmüyor
🔴 taş-kağıt-makas üçlüsü → sortedWith çökmüyor
averaj doğru hesaplanıyor (score1/score2 dolu maçlarla)
canlı standings tur ortasında doğru
3/1/0 ve 2/1/0 puanlaması
```

## TESLİM
Koordinatöre (`ranking-a3 [7558ae]`): kaç test · kaç GEÇTİ · `!!` kalmadı mı ·
geçişlilik testi sonucu.
