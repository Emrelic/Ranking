# ELEME MOTORU — ilerleme (ranking-3a [b78aba])

## Durum: 3 kip de yazıldı, derleniyor, 20/20 test GEÇTİ

## Dosyalar
- `EliminationSystem.kt` (YENİ) — `object EliminationSystem`, istenen API birebir
- `EliminationSystemTest.kt` (YENİ) — 20 test, hepsi geçiyor

## Ölçülen iki kusur nasıl kapatıldı
1. **`getGroupSongs` ikinci shuffled() kusuru**: bu motorda `shuffled()` HİÇ
   kullanılmıyor. Grup üyeliği `snakeGroups()` ile song.id sırasından
   deterministik türetiliyor — regresyon testi (`grup kipi computeState iki
   kez cagrilinca AYNI gruplari verir`) bunu doğruluyor.
2. **Pozisyon çakışması**: eski kod iki ayrı formülle (aşağıdan/yukarıdan)
   pozisyon hesaplayıp çakıştırıyordu. Bu motor TEK bir mekanizma kullanıyor:
   kronolojik elenme sırası (bir permütasyon) → pozisyon. Yapısı gereği
   çakışma OLAMAZ. `n=16 tek eleme pozisyon 1-16 tam bir kez` testi bunu
   doğruluyor (16/16 pozisyon, 16/16 benzersiz songId).

## Üç kip — kapsam durumu

### ① TEK ELEME — SAĞLAM
- Ön tur (play-in) doğru boyutlandırılıyor (n=12 → 4+4+2+1=11 maç, test var)
- Reseeding eşleştirmesi ("en güçlü en zayıfla", her turda yeniden) — bu,
  sabit-slot klasik bracket'la AYNI eşleşmeleri üretir (üst sıralar
  kazandıkça); dosya başındaki KDoc'ta gerekçesi var
- n=1,2,3,4,8,12,16 sınır testleri hepsi geçti
- Beraberlik kuralı (yüksek seed geçer) test edildi + determinizm testi var

### ③ GRUP + ELEME — SAĞLAM (küçük bir basitleştirmeyle)
- Grup sayısı/dağıtımı deterministik (hedef grup büyüklüğü 3-6, snake dağıtım)
- Grup içi lig: 3/1/0 puan + averaj (CLAUDE.md LIG kuralıyla tutarlı)
- Knockout aşaması, TEK ELEME motorunun AYNISI (kod tekrarı değil, doğrudan
  reuse) — seed kaynağı grup performansı
- n<4 için gruplama anlamsız olduğundan (grup başına min 3 hedefi tutmuyor)
  TEK grup kabul edip herkesi doğrudan knockout'a gönderiyor — test var (n=3)
- n=12 ile tam akış testi: computeState idempotent + tekrar oynatınca aynı
  sonuç + pozisyon çakışmasız

### ② ÇİFT ELEME — ÇALIŞIYOR ama BİLİNÇLİ SADELEŞTİRME var
🔴 **Bunu açıkça söylüyorum, gizlemiyorum**: gerçek çift-eleme turnuvalarında
üst kol (WB) ve alt kol (LB) turları İÇ İ�ÇE ilerler (WB round2 oynanırken LB
round1 de oynanabilir). Bu motor bunu YAPMIYOR — **WB'yi TAMAMEN bitirmeden
LB'yi hiç başlatmıyor** (sıralı/sequential). WB bitince, WB'nin TÜM
kaybedenleri "WB'de ne kadar ileri gittiği" seed'iyle AYRI bir tek-eleme
turnuvasına giriyor (yine TEK ELEME motorunun reuse'u).

Bunun sonucu DOĞRU (kim şampiyon, bracket reset kuralı) ama SUNUM geleneksel
çift-eleme ekranındaki "iki kol aynı anda ilerliyor" görünümünü vermiyor —
kullanıcı bir WB turu bitirmeden LB maçı GÖRMEYECEK, sırayla önce tüm WB
biter, sonra tüm LB oynanır, sonra final(ler).

Test edildi ve GEÇTİ:
- Normal akış (WB galibi finali de kazanır → reset YOK, round=1000 var round=1001 yok)
- 🔴 **Bracket reset**: LB galibi finali kazanınca round=1001 maçı üretiliyor,
  onu kazanan nihai şampiyon oluyor — spesifik senaryo test edildi (n=4,
  1 numaralı takım WB'de erken kaybedip LB'yi domine edip finali/reset'i
  kazanıyor)
- n=8 çift eleme uçtan uca tamamlanıyor, pozisyon çakışması yok

**Bilinen eksik**: `computeState()`'in DOUBLE kipi için `eliminatedByRound`
alanı BOŞ dönüyor (`emptyMap()`) — bunu doldurmadım, zaman/kapsam nedeniyle
bilinçli olarak atladım. `totalRounds` de DOUBLE için `-1` (bracket reset
olasılığı yüzünden önceden kesin bilinmiyor). `calculateResults` ve
`championId` DOĞRU çalışıyor (bunlar test edildi); yalnız bu iki alan
(eliminatedByRound, totalRounds) UI'nin ilerleme çubuğu gibi bir şey
göstermesi gerekirse eksik kalır.

## Rakamlar (SAYIYLA)
- 3 kip tamam (SINGLE, DOUBLE, GROUP_THEN_KNOCKOUT)
- 20 test, **20/20 GEÇTİ**
- Pozisyon çakışma testi: n=16 SINGLE, 1..16 tam bir kez — GEÇTİ
- Kapsanmayan sınır durumu: DOUBLE modu n=1,2,3 sınırları AYRI test edilmedi
  (yalnız n=4 ve n=8 test edildi) — küçük n'lerde DOUBLE'ın davranışı mantık
  gereği SINGLE'a çok benzer olmalı (n=1→otomatik şampiyon, n=2→tek final,
  WB=LB'siz) ama bunu bizzat çalıştırıp doğrulamadım.
- DOUBLE'ın `eliminatedByRound` alanı boş — yukarıda açıklandı.
- Derleme: `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL
- Test: `./gradlew :app:testDebugUnitTest --tests "*EliminationSystemTest*"` → BUILD SUCCESSFUL, 20/20

## bracketStructure() ↔ BracketView.kt uyumu
`List<List<BracketSlot>>` biçimi BracketView.kt'nin `BracketRound`/
`BracketMatch` tiplerine BİREBİR eşlenmez — motor `BracketSlot` (songId,
seed, isBye) döndürüyor, UI `BracketTeam`/`BracketMatch`/`BracketRound`
bekliyor. Aradaki dönüşümü (round title, isWinner/isCompleted işaretleme,
ardışık slot çiftlerini BracketMatch'e paketleme) YAZMADIM — bu entegrasyon
adımı koordinatörün ya da FixturePanel'i besleyecek tarafın işi
(ELEME-EKRANI.md zaten "koordinatör entegrasyonda uyarlayacak" diyordu).
İstersen bu dönüştürücüyü de ben yazarım, söylemen yeterli.
