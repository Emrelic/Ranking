# ELEME MOTORU — şartname

| alan | değer |
|---|---|
| AD | ELEME MOTORU |
| DİZİN | `C:/Users/emrem/OneDrive/Belgeler/Projeler/Ranking` |
| BRANCH | `ileri-tusu-asagida-crash-fix` |
| ClaudEmre | HAYIR — işçi oturumu |
| KOORDİNATÖR | `ranking-a3 [7558ae]` |

🔴 **ÖNCE `oturumlar/ORTAK.md` OKU.**

## SENİN DOSYALARIN
```
✅ app/src/main/java/com/example/ranking/ranking/EliminationSystem.kt  (YENİ)
✅ app/src/test/java/com/example/ranking/EliminationSystemTest.kt      (YENİ)
✅ oturumlar/ELEME-MOTORU-ILERLEME.md                                  (YENİ)
```

## NİÇİN YENİDEN YAZILIYOR — ölçülmüş kusurlar

`RankingEngine.kt`'deki mevcut eleme kodu (~400 satır) şu an UI'dan
seçilemiyor çünkü bozuk:

1. 🔴 **`getGroupSongs` (:288) İKİNCİ kez `shuffled()` çağırıyor.**
   `createEliminationMatches` (:165) bir kez karıştırıp grupları kurmuş,
   sonuç hesabı ise BAŞKA bir karıştırmayla grup üyeliğini yeniden
   türetiyor. Yani **sonuçtaki gruplar, oynanan maçlarla alakasız.**
   Yorum satırı bunu itiraf ediyor: "Should use same shuffle as
   createEliminationMatches".
2. 🔴 **Pozisyonlar ÇAKIŞIYOR** (`calculateEliminationResults` :337):
   elenenlere `songCount--` ile aşağıdan, knockout sonuçlarına `1..k` ile
   yukarıdan pozisyon veriliyor; ikisi çakışıyor ve aynı pozisyon iki
   takımda görünebiliyor.
3. 🔴 **`createDirectEliminationMatches`** turları önceden üretmeye
   çalışıyordu — kazanan bilinmeden sonraki tur kurulamaz.

## YAPACAĞIN

`object EliminationSystem` yaz. **Üç kip** destekle:

### ① TEK ELEME (single elimination)
```
n güce-2 değilse ÖN TUR (play-in) kur:
  fazla = n - 2^floor(log2(n))
  en alttaki 2*fazla takım ön turda eşleşir, fazla takım elenir
  üstteki takımlar ilk turu BYE geçer  ← klasik seeding
Seeding: 1-n, 2-(n-1)... (en güçlü en zayıfla)
🔴 Tur tur üret: kazananlar belli olmadan sonraki tur KURULMAZ
```

### ② ÇİFT ELEME (double elimination)
```
Üst kol (winners) + alt kol (losers)
Üst koldan düşen alt kola iner; alt kolda ikinci yenilgi = eleme
Final: üst kol galibi vs alt kol galibi
(kural: alt koldan gelen kazanırsa bracket reset — İKİNCİ final oynanır)
```

### ③ GRUP + ELEME (mevcut ELIMINATION'un yerine)
```
n takım → gruplara bölünür → grup içi lig → üst k takım tur atlar
🔴 Grup üyeliği DETERMİNİSTİK olmalı: yılan (snake) dağıtımı kullan
   grup i'ye giden takımlar song.id sırasına göre hesaplanır
   ⚠️ shuffled() YOK — replay'i kırar ve mevcut hatanın ta kendisi
```

### İstenen API
```kotlin
object EliminationSystem {
    enum class Mode { SINGLE, DOUBLE, GROUP_THEN_KNOCKOUT }

    data class BracketSlot(val songId: Long?, val seed: Int, val isBye: Boolean)
    data class EliminationState(
        val mode: Mode, val currentRound: Int, val totalRounds: Int,
        val aliveIds: List<Long>, val eliminatedByRound: Map<Int, List<Long>>,
        val isComplete: Boolean, val championId: Long?
    )
    data class RoundResult(
        val matches: List<Match>, val canContinue: Boolean, val reason: String
    )

    fun computeState(songs: List<Song>, completed: List<Match>, mode: Mode): EliminationState
    fun createNextRound(songs: List<Song>, completed: List<Match>, mode: Mode): RoundResult
    fun calculateResults(songs: List<Song>, completed: List<Match>, mode: Mode): List<RankingResult>

    /** UI'nin fikstür ağacı çizebilmesi için tur tur yapı. */
    fun bracketStructure(songs: List<Song>, completed: List<Match>, mode: Mode): List<List<BracketSlot>>
}
```

### 🔴 POZİSYON KURALI — çakışma OLMAYACAK
```
1. sıra   : şampiyon
2. sıra   : finalde kaybeden
3-4       : yarı finalde kaybedenler
5-8       : çeyrek finalde kaybedenler
...       : her tur bir öncekinin iki katı aralık
```
Yani pozisyon **elendiği turdan** türetilir; geç elenen üstte olur.
Aynı turda elenenler kendi aralarında tiebreaker'la sıralanır
(galibiyet → averaj → id). **Test et: n=16 için 1..16 pozisyonlarının
her biri TAM BİR KEZ geçiyor.**

### Beraberlik
Elemede beraberlik olmaz — `winnerId == null` gelirse deterministik
bir kural uygula (yüksek seed geçer) ve bunu belgele.

## TESTLER — en az bunlar
```
n=1,2,3,4 sınır · n=8 (tam güç-2) · n=12 (ön tur gerekir) · n=16
🔴 n=16 tek eleme: pozisyon 1..16 her biri TAM BİR KEZ
🔴 grup kipi: computeState iki kez çağrılınca AYNI grupları veriyor
   (mevcut hatanın regresyon testi — shuffled() dönmesin)
çift eleme: alt kol galibi finali kazanırsa bracket reset oluyor
yetim maç kaydı → çökmüyor · hepsi berabere → deterministik sonuç
```

## TESLİM
Koordinatöre (`ranking-a3 [7558ae]`): kaç kip tamam · kaç test · kaç GEÇTİ ·
pozisyon çakışma testi sonucu · kapsanmayan sınır durumu.
