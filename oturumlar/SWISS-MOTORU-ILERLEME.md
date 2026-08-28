# SWISS MOTORU — ilerleme günlüğü

Şartname: `oturumlar/SWISS-MOTORU.md`. Koordinatör: `ranking-a3 [7558ae]`.
Görev bana `ranking-5e`den devredildi (o oturumdan ses çıkmamıştı).

## Yapılanlar

`app/src/main/java/com/example/ranking/ranking/SwissSystem.kt` (YENİ, saf Kotlin,
android.*/androidx.* import yok) yazıldı:

- `computeState(songs, completedMatches)` — tam replay: durum ayrı tutulmaz,
  tamamlanmış SWISS maçları baştan oynatılır (PairwiseComparisonSort.kt deseni).
- `createNextRound(state, completedMatches)` — bye seçimi + geri izlemeli
  (backtracking) tam eşleştirme + matchNumber ataması.
- `calculateResults(songs, completedMatches)` — puan → Buchholz → galibiyet →
  (yalnız tam eşit KOMŞULARDA) aralarındaki maç → id.
- `recommendedRoundCount(n)` — ceil(log2(n)), n<=1 için 0.

### Tasarım kararları (şartnameden sapma varsa gerekçeli)

1. **Bye bir Match kaydı olarak saklanıyor**: `songId1 == songId2 == byeTeamId`,
   `winnerId = byeTeamId`, `isCompleted = true`. Gerekçe: `computeState` SAF bir
   replay fonksiyonu (Emre'nin aksine ayrı EmreState taşınmıyor, ORTAK.md bunu
   PairwiseComparisonSort desenine göre istiyor) — bye bir Match satırı olarak
   kalıcı olmazsa, bir sonraki `computeState` çağrısı geçmiş bye'ları HİÇ
   hatırlayamaz (byeCount hep 0 görünür, adil rotasyon bozulur). Bu satır
   `isCompleted=true` geldiği için oy ekranına düşürülmemeli — entegrasyon eden
   tarafın (koordinatör) bilmesi gereken tek nokta bu.
2. **matchNumber sıralaması**: Emre'nin "üstten/alttan alternatif KEAT seçimi"
   yerine, geri izlemeli eşleştirme fonksiyonu zaten anchor'ı her adımda
   standings sırasındaki EN ÜST takımdan seçtiği için döndürdüğü liste doğal
   olarak üstten-alta sıralı geliyor; matchNumber = bu sıradaki index+1.
   Sonuç aynı: matchNumber ASC = oylama üstten (en iyi eşleşme) alta (en
   düşük eşleşme) doğru ilerliyor — ama Emre'deki gibi ayrı bir alternating-
   numbering algoritması KURULMADI, gerek görmedim. İtirazın varsa söyle,
   değiştiririm.
3. **Eşleştirme tercihi**: "aynı puan grubundan başla, tekse komşu gruba taşar"
   kuralı ayrı bir gruplama adımı olarak değil, standings sıralı listede
   ARDIŞIK arama olarak sağlanıyor (anchor'a en yakın sıradaki uygun rakip
   önce denenir) — aynı etkiyi daha az kodla veriyor, ayrıca test edildi.

## Testler — `SwissSystemTest.kt`, 14 test, 14/14 GEÇTİ (kendi ölçümüm)

```
testEmptyList, testSingleTeam, testTwoTeams, testThreeTeamsByeRotates
testSevenTeamsNoOneDropsEachRound, testSevenTeamsByeFairRotation (tam 7 turluk
  bye döngüsü, maxRounds test amaçlı zorlanarak - n=7'nin doğal sınırı 3 tur)
testEightTeamsNoRematchAcrossAllRounds, testEightTeamsEveryoneAlwaysPlays
testMatchNumberAlwaysAssignedAscendingFromTop
testAllDrawsScenarioDoesNotCrash
testOrphanMatchDoesNotCrash, testOrphanByeMatchDoesNotCrash
testReplayIsDeterministic
testSixtyFourTeamsPerformance (ölçülen: tur başına <50ms, sınır 2000ms)
```

Koşum: `./gradlew :app:testDebugUnitTest --tests "*SwissSystemTest*"` ve tam
paket (`--tests` filtresiz) iki kez ayrı ayrı, ikisinde de 0 hata.

### Kapsanmayan / ölçmediğim
- Backtracking güvenlik sayacı (50.000) hiçbir testte dolmadı — n=8/n=64 gibi
  makul senaryolarda hep ilk birkaç denemede çözüm buluyor. Sayacı zorlayan,
  kasıtlı "imkânsız tam eşleştirme" senaryosu (örn. tüm ikili kombinasyonlar
  daha önce oynanmış küçük bir alt küme) yazmadım — spec açıkça istemiyordu.
- 64'ten büyük ölçek denenmedi.

## ⚠️ Anlaşmazlık — koordinatörün "1 test kırık" raporu

Koordinatör tam paket koşumunda (176 test) `testReplayIsDeterministic`'in
kırık çıktığını bildirdi. Kendi tarafımda AYNI komutu (filtresiz tam paket)
2 kez ayrı ayrı çalıştırdım, biri `--rerun` ile cache atlanarak — ikisinde de
0 hata, SwissSystemTest'in 14 testi de temiz. Kodu da elle denetledim:
`shuffled()`/`Random`/`System.currentTimeMillis()` yok; `opponentIds` Kotlin
`mutableSetOf()` ile kuruluyor (LinkedHashSet, ekleme sırası korunur, HashSet
sırasızlığı burada geçerli değil); `assertEquals` zaten `Set.equals()` kullanıyor
(sıradan bağımsız). Dosya hash'lerini de kontrol ettim, üstüne yazılmamış.

En olası açıklama: 3 oturumun aynı `app/build` dizinini paylaşması (koordinatörün
kendisinin belirttiği "Unable to delete directory app\build" sınıfından bir
yarış durumu). Kesin kanıtım yok — koordinatöre bunu yazdım, tam hata mesajı/
stack trace istedim. Kör düzeltme yapmadım; "tamam" demek yerine ölçtüğümü
raporladım.

## TESLİM (özet)
- Fonksiyon sayısı: 4 public (computeState, createNextRound, calculateResults,
  recommendedRoundCount) + ~8 private yardımcı
- Test sayısı: 14, GEÇEN: 14 (kendi ölçümümde; koordinatörün paylaşımlı build
  ortamında 1 anlaşmazlık var, yukarıda ayrıntılı)
- Kapsanmayan sınır durumu: kasıtlı-imkânsız-eşleştirme senaryosu, 64+ ölçek
- Backtracking sayacı hangi n'de doldu: hiçbirinde (ölçtüm, 50.000'e hiç yaklaşmadı)
