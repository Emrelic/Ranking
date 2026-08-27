# ELEME EKRANI — şartname

| alan | değer |
|---|---|
| AD | ELEME EKRANI |
| DİZİN | `C:/Users/emrem/OneDrive/Belgeler/Projeler/Ranking` |
| BRANCH | `ileri-tusu-asagida-crash-fix` |
| ClaudEmre | HAYIR — işçi oturumu |
| KOORDİNATÖR | `ranking-a3 [7558ae]` |

🔴 **ÖNCE `oturumlar/ORTAK.md` OKU.** (Saf-Kotlin kuralı SENİN için
geçerli değil — sen Compose yazıyorsun. Diğer kurallar geçerli.)

## SENİN DOSYALARIN
```
✅ app/src/main/java/com/example/ranking/ui/screens/ranking/BracketView.kt        (YENİ)
✅ app/src/main/java/com/example/ranking/ui/screens/ranking/GroupStandingsView.kt (YENİ)
✅ oturumlar/ELEME-EKRANI-ILERLEME.md                                             (YENİ)
```
🔴 `RankingScreen.kt`, `StandingsDialog.kt`, `MatchingsList.kt`,
`TeamSelectionPanel.kt` KOORDİNATÖRDE — açma. Sen **bağımsız, yeniden
kullanılabilir bileşenler** yazıyorsun; koordinatör onları ekrana takacak.

## NİÇİN BU İŞ

Eleme usulünün puanlama ekranı şu an tek satırlık bir stub
(`RankingScreen.kt:395` `EliminationContent`). Motoru başka bir kıta
yazıyor; senin işin onu **gösterecek** ekranı hazırlamak.

⚠️ Motor henüz bitmedi. **Motoru BEKLEME** — bileşenlerini kendi veri
tiplerinle yaz, koordinatör bağlarken uyarlar. Aşağıdaki tipleri KENDİ
dosyanda tanımla (motorun tipini import ETME, henüz yok):

```kotlin
// BracketView.kt içinde tanımla
data class BracketTeam(
    val songId: Long, val name: String, val seed: Int,
    val score: Int? = null, val isWinner: Boolean = false,
    val isBye: Boolean = false
)
data class BracketMatch(
    val matchId: Long, val round: Int, val matchNumber: Int,
    val team1: BracketTeam?, val team2: BracketTeam?,
    val isCompleted: Boolean, val isPlayable: Boolean
)
data class BracketRound(
    val roundNumber: Int, val title: String,   // "Çeyrek Final", "Yarı Final"...
    val matches: List<BracketMatch>
)
```

## ① BracketView.kt — fikstür ağacı

Eleme turnuvasının klasik ağaç görünümü.
```
🔴 YATAY KAYDIRILABİLİR — 32 takımlık bracket telefona sığmaz
   Column(horizontalScroll) + turlar yan yana sütunlar
🔴 Dikey de kaydırılabilir olacak
Her tur bir sütun: Ön Tur → 1. Tur → Çeyrek → Yarı → Final
Maç kartı: iki takım alt alta, kazanan KALIN + vurgulu zemin
Oynanmamış maç soluk; oynanabilir maç (iki takımı belli) tıklanabilir
BYE geçen takım "BYE" etiketiyle, rakip kutusu boş
Şampiyon en sağda, altın çerçeveli
onMatchClick: (Long) -> Unit  ← koordinatör bunu oylamaya bağlayacak
```

Tur adları takım sayısından türetilsin:
```
2 takım kaldıysa  → "Final"
4  → "Yarı Final"      8  → "Çeyrek Final"
16 → "Son 16"          32 → "Son 32"
ön tur → "Ön Tur"
```

## ② GroupStandingsView.kt — grup puan tablosu

Grup + eleme kipinde grup içi lig tablosu.
```
Her grup ayrı kart: "A Grubu", "B Grubu"...
Sütunlar: # · Takım · O · G · B · M · A(averaj) · P
🔴 Tur atlayan üst k takım YEŞİL şeritle ayrılsın,
   elenenler soluk — "kim geçiyor" tek bakışta okunmalı
Yatay kaydırma: uzun takım adları kırpılmasın (TextOverflow.Ellipsis)
```

## TASARIM KURALLARI — mevcut dokuya uy
```
Material3 · MaterialTheme.colorScheme (sabit renk YAZMA, tema kırılır)
Mevcut örnekleri OKU: ui/screens/ranking/StandingsDialog.kt ve
  MatchingsList.kt — kart biçimi, tipografi, aralıklar oradan
Türkçe metinler · uzun adlar maxLines + Ellipsis
🔴 @Preview YAZ — bileşeni motor olmadan görebilmeliyiz
   n=8 ve n=12 (ön turlu) için ayrı preview
```

## DERLEME
```
./gradlew :app:compileDebugKotlin
```
Bu yeter — senin dosyaların için birim testi beklemiyorum, `@Preview`
görsel doğrulama sayılır.

## TESLİM
Koordinatöre (`ranking-a3 [7558ae]`): kaç bileşen · kaç preview ·
derleme temiz mi · hangi ekran boyutunda taşma gördün.
