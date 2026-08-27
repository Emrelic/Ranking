# ORTAK KURALLAR — bütün Ranking işçi oturumları

## Proje
`C:/Users/emrem/OneDrive/Belgeler/Projeler/Ranking` · branch `ileri-tusu-asagida-crash-fix`
Android / Kotlin / Jetpack Compose / Room. Türkçe konuş, Türkçe yorum yaz.
⚠️ `/claudemre-basla` ÇAĞIRMA — sen işçisin, koordinatör ranking-a3.

## 🔴 DOSYA SAHİPLİĞİ — en kırmızı çizgi
Yalnız SANA verilen dosyalara dokun. Bu üç dosya KOORDİNATÖRDE, hiçbir
koşulda açıp değiştirme:
```
❌ ranking/RankingEngine.kt
❌ ui/viewmodel/RankingViewModel.kt
❌ ui/screens/RankingScreen.kt
```
Okumak serbest, YAZMAK yasak. Entegrasyonu koordinatör yapar.

## Veri tipleri (com.example.ranking.data)
```kotlin
Song(id: Long, listId: Long, name: String, artist: String, album: String,
     trackNumber: Int, csvData: String?)

Match(id: Long = 0, listId: Long, rankingMethod: String,
      songId1: Long, songId2: Long,
      winnerId: Long?,            // null = beraberlik
      score1: Int? = null, score2: Int? = null,
      round: Int = 1, groupId: Int? = null,
      matchNumber: Int = 0,       // oylama sırası — 0 BIRAKMA
      tournamentId: Long? = null, isCompleted: Boolean = false)

RankingResult(songId: Long, listId: Long, rankingMethod: String,
              score: Double, position: Int)
```

## 🔴 SAF KOTLIN — Android bağımlılığı YOK
Motor dosyaların `android.*` ya da `androidx.*` import ETMEZ. Sebep: JVM
birim testinde koşacaklar. Room/Compose'a dokunma; sadece veri sınıflarını
al, hesapla, döndür.

## 🔴 DURUM YÖNETİMİ — replay deseni
Bu projede motorlar ayrı bir durum tablosu tutmaz: tamamlanmış maçlar
(`matches` tablosu) deterministik olarak baştan oynatılır ve sıradaki adım
bundan türetilir. `PairwiseComparisonSort.kt` bunun en temiz örneği —
BAŞLAMADAN ONU OKU. Aynı deseni izle: aynı girdi → aynı çıktı.
⚠️ `shuffled()`, `Random`, `System.currentTimeMillis()` kullanma — replay'i
kırar. Sıralamaya deterministik bir anahtar gerekiyorsa `song.id` kullan.

## Test
`app/src/test/java/com/example/ranking/` altına kendi test dosyanı yaz.
JUnit4 + `org.junit.Assert`. Mevcut örnek: `EmreQuickUnitTest.kt`.
Koşturma:
```
./gradlew :app:testDebugUnitTest --tests "*SeninTestin*"
```
⚠️ Derleme JDK'sı gradle.properties'te sabit (JBR 21). Sistem JDK 24
uyumsuz — `org.gradle.java.home` satırına DOKUNMA.

## Sınır durumları — hepsini test et
```
0 öğe · 1 öğe · 2 öğe · TEK sayı (bye gerektirir) · ÇİFT sayı
büyük liste (64+) · hepsi berabere · yetim maç (silinmiş öğe id'si)
```
🔴 `!!` KULLANMA. Yetim maç kaydı gerçek bir senaryodur (öğe silinebiliyor);
`map[id]!!` NPE atar. `?: return@forEach` / `?: continue` kullan.

## Çalışma düzeni
```
① Küçük parça bitir → derle → test → COMMIT (pathspec'li, YALNIZ kendi dosyaların)
   git commit -F - -- <senin/dosyan.kt> <senin/testin.kt>
② İlerlemeni oturumlar/<AD>-ILERLEME.md dosyasına yaz (bu da senin dosyan)
③ Hepsini bitirip tek commit atma — yarıda kalırsa hepsi uçar
```

## Rapor
Koordinatöre `SendMessage` ile `ranking-a3 [7558ae]` adresine yaz.
🔴 Aksaklık raporu BEKLEMEZ — takıldığın anda bildir, işin bitmesini bekleme.
Teslimde şunları SAYIYLA ver: kaç fonksiyon, kaç test, kaç test geçti,
hangi sınır durumu kapsanmadı.
⚠️ Ölçmediğin bir şeyi "çalışıyor" diye yazma. "Test etmedim" geçerli bir
cevaptır; uydurulmuş bir "tamam" değildir.
