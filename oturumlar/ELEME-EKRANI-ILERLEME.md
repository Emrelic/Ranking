# ELEME EKRANI — ilerleme (ranking-3a [b78aba])

## Durum: bileşenler yazıldı, derleme BAŞKA dosyadaki hatadan dolayı doğrulanamadı

## Yapılanlar
- `BracketView.kt` (YENİ) — fikstür ağacı
  - Veri tipleri: `BracketTeam`, `BracketMatch`, `BracketRound` (şartnamedeki
    tanımla birebir)
  - `bracketRoundTitle(teamCount)` yardımcı fonksiyonu: 2→Final, 4→Yarı Final,
    8→Çeyrek Final, 16→Son 16, 32→Son 32, 64→Son 64, diğerleri→Ön Tur
  - Yatay + dikey kaydırma (`horizontalScroll` + `verticalScroll`)
  - Kazanan takım kalın + vurgulu zemin, BYE etiketli, oynanmamış maç soluk,
    oynanabilir maç tıklanabilir (`onMatchClick: (Long) -> Unit`)
  - Şampiyon sütunu en sağda, altın çerçeveli (bkz. "tasarım notu" altta)
  - 2 `@Preview`: n=8 (düzenli tek eleme) ve n=12 (ön turlu, bye içeren)

- `GroupStandingsView.kt` (YENİ) — grup puan tablosu
  - Veri tipleri: `GroupStandingEntry`, `BracketGroup`
  - Her grup ayrı kart, sütunlar # · Takım · O · G · B · M · A · P
  - Tur atlayan üst k takım yeşil şeritle ayrılıyor (`entry.advances`),
    elenenler soluk zemin
  - Uzun takım adı → `maxLines=1` + `TextOverflow.Ellipsis`, tablo ayrıca
    yatay kaydırılabilir
  - 2 `@Preview`: n=8 (2 grup) ve n=12 (3 grup, biri uzun isimli satır içeriyor
    — Ellipsis'i görsel doğrulamak için)

## Tasarım notu — sabit renk istisnası (2 yer)
`ORTAK.md`/şartname "sabit renk yazma, MaterialTheme.colorScheme kullan"
diyor, ama şartname aynı zamanda "şampiyon altın çerçeveli" ve "tur atlayan
YEŞİL şeritle ayrılsın" diye açıkça renk adı veriyor. Bu ikisi anlam taşıyan
(altın=şampiyonluk, yeşil=geçti) evrensel renkler; MaterialTheme.primary bu
projede MAVİ, dolayısıyla ona bağlarsam "geçti/kazandı" anlamı kaybolur.
Mevcut dokuda da emsali var: `MatchingsList.kt` → `AdvancedMatchCard` içinde
`Color(0xFF388E3C)` (yeşil, takım2 vurgusu) zaten sabit kullanılıyor.

Bu yüzden İKİ yerde bilinçli sabit renk kullandım, ikisi de yalnız dekoratif
çerçeve/şerit — metin ve zemin kontrastları hâlâ `MaterialTheme.colorScheme`den:
```
BracketView.kt          AltinCerceve = Color(0xFFFFD700)   — şampiyon çerçevesi
GroupStandingsView.kt   GecisSeridi  = Color(0xFF4CAF50)    — geçiş şeridi
```
Koordinatör başka türlü isterse (örn. tema paletine `success`/`gold` token'ı
eklensin) değiştiririm — şimdilik en yakın çözüm bu.

## Derleme
```
./gradlew :app:compileDebugKotlin
```
SONUÇ: **BUILD SUCCESSFUL** (koordinatörün CriteriaEvaluationDialog.kt
düzeltmesinden sonra). Uyarılar dışında hata yok; bu iki dosya için de
sıfır hata/uyarı.

## Rakamlar
- 2 yeni dosya, 2 bileşen (`BracketView`, `GroupStandingsView`)
- 5 `@Preview` fonksiyonu: `BracketView` için n=8, n=12 (ön turlu),
  n=32 (telefon genişliği, `widthDp=360`); `GroupStandingsView` için
  n=8 (2 grup), n=12 (3 grup, biri uzun isimli — Ellipsis testi)
- Birim testi YOK — şartname zaten istemiyor, `@Preview` görsel doğrulama
  yerine geçiyor

## ⚠️ Taşma testi — SAYIYLA dürüst rapor
**Emulator/cihaz erişimim yok** (bu oturumda Android Studio/AVD çalıştıramıyorum,
yalnız `gradlew` derleyicisine erişimim var). Bu yüzden "32 takımlık bracket'te
telefonda yatay kaydırma çalışıyor" iddiasını GÖREREK doğrulayamadım — bu
ölçülmemiş bir şey, "çalışıyor" diye yazmıyorum.

Yapabildiğim: `BracketViewPreviewN32Phone` adında `widthDp=360, heightDp=640`
(tipik telefon boyutu) bir `@Preview` ekledim, 32→16→8→4→2→1 maçlık tam bir
bracket üretiyor. Derleyici bunu hatasız derledi. Kod incelemesiyle
söyleyebileceğim: `BracketView` kök `Row`'u `Modifier.horizontalScroll(...)
.verticalScroll(...)` içinde, sütun genişlikleri sabit (170.dp/140.dp) —
Compose'da bu desen içerik genişliği ekran genişliğini aştığında otomatik
kaydırma sağlar (yaygın, iyi bilinen bir desen), ama BUNU CİHAZDA GÖRMEDİM.
Koordinatör ya da emulator erişimi olan biri `BracketViewPreviewN32Phone`'u
Android Studio'da açıp gözle doğrulamalı.
