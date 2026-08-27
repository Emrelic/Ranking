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
SONUÇ: **FAILED** — ama hata benim dosyalarımda değil:
```
e: CriteriaEvaluationDialog.kt:196:69 Unresolved reference 'name'
```
Bu dosya benim alanımda değil (`git status` commit'siz değişiklik gösteriyor,
başka bir oturumun elinde olabilir). Derleyici BracketView.kt / GroupStandingsView.kt
için hiçbir hata basmadı — ama modül tek seferde derlendiği için "kendi
dosyalarım kesin temiz" diye SAYIYLA iddia edemiyorum, yalnızca "derleyici
çıktısında bu iki dosya için hata görünmedi" diyebilirim. Koordinatöre bildirdim,
CriteriaEvaluationDialog.kt düzelince tekrar derleyip kesin sonucu buraya yazacağım.

## Rakamlar
- 2 yeni dosya, 2 bileşen (`BracketView`, `GroupStandingsView`)
- 4 `@Preview` fonksiyonu (n=8 ve n=12, her bileşen için birer tane)
- Birim testi YOK — şartname zaten istemiyor, `@Preview` görsel doğrulama
  yerine geçiyor (ama derleme doğrulanana kadar preview'ların GERÇEKTEN
  render olduğunu da göstermiş değilim — Android Studio/emulator erişimim yok,
  yalnız derleyici çıktısına bakabiliyorum)
- Taşma testi: **yapılmadı** — emulator/cihaz erişimim yok, `widthDp=420` ve
  `widthDp=900` preview parametreleriyle yalnız statik kod incelemesi yaptım
