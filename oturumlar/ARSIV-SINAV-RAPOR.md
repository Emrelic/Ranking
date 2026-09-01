# ARŞİV/SERİALİZER BÜTÜNLÜK SINAVI — rapor (ranking-f0)

Görev: koordinatör `ranking-7d` (kendini `ranking-0c` diye tanıtıyor).
Commit: `e8294db`. Yalnız YENİ test dosyaları eklendi, hiçbir üretim
dosyasına dokunulmadı.

## SAYIM (kendi ölçümüm)

| dosya | test | geçti | kırık |
|---|---|---|---|
| `SwissJsonSerializerRoundTripTest.kt` | 15 | 15 | 0 |
| `ArsivSozlesmeTest.kt` | 8 | 8 | 0 |
| **toplam** | **23** | **23** | **0** |

Koşum: `./gradlew testDebugUnitTest --tests "com.example.ranking.SwissJsonSerializerRoundTripTest" --tests "com.example.ranking.ArsivSozlesmeTest"`
→ BUILD SUCCESSFUL, 1m46s. XML sonuçları (`app/build/test-results/testDebugUnitTest/TEST-*.xml`)
`tests="15" ... failures="0" errors="0"` ve `tests="8" ... failures="0" errors="0"`.

⚠️ Tam paket (306+ test) AYRICA koşulmadı — gradle kilidi şu an kıt kaynak
(FİLO KURALI), ve bu iki dosya yalnız EKLEME, hiçbir üretim dosyasına
dokunmadığı için mevcut testleri etkilemesi yapısal olarak mümkün değil.

## KAPSAM 1 — SwissStateSerializer + SwissFixtureSerializer (utils/)

Test edilen: `serializeStandings`/`deserializeStandings`,
`serializePairingHistory`/`deserializePairingHistory`,
`serializeRoundHistory`/`deserializeRoundHistory`,
`serializeFixtureData`/`deserializeFixtureData`,
`serializeLiveStandings`/`deserializeLiveStandings`.
Boş veri, tipik veri, 64 takımlı büyük fikstür (2016 ikili), negatif/ondalıklı
puan, Türkçe+özel karakterli şarkı adı (`RankingEntry.songName`) — hepsi
birebir gidiş-dönüş yapıyor.

### 🔴 Bulunan GERÇEK kayıplar (kusur değil, "ulaşılamayan yol"da belgelendi)

Bu iki serileştirici **hâlâ canlı**: `RankingRepository.saveSwissState` /
`loadSwissState` (satır 320-343) ve `saveSwissFixture` (satır 425-434),
`RankingViewModel.kt` içinde method="SWISS" turnuvalarında çağrılıyor
(satır 498, 786, 1495, 1855 civarı). Ama CLAUDE.md bu yöntemi UI'dan
gizlenmiş işaretliyor — normal kullanıcı SWISS'i seçemiyor, dolayısıyla bu
kod yolu şu an ULAŞILAMAZ. Yöntem yeniden açılırsa aşağıdakiler gerçek veri
kaybına dönüşür:

1. `SwissStateSerializer.serializeRoundHistory`/`deserializeRoundHistory`:
   yalnız `id, songId1, songId2, winnerId, round` yazılıyor/okunuyor.
   Round-trip sonrası **kaybolan alanlar**: `listId` → 0'a düşer (kod yorumu
   "Will be set by the caller" diyor, kasıtlı ama round-trip'i bozan bir
   varsayım), `score1`/`score2` → null, `groupId` → null, `matchNumber` → 0,
   `tournamentId` → null, `createdAt` → deserialize ANINDAKİ
   `System.currentTimeMillis()`'e sıfırlanıyor (orijinal değer DEĞİL).
   Test: `belgeleme_roundHistory_matchIn_listId_skor_grup_turnuvaId_alanlariKAYBOLUR`.

2. `SwissFixtureSerializer.serializeMatch`/`deserializeMatch`:
   `matchNumber` alanı HİÇ ele alınmıyor — round-trip sonrası her zaman 0.
   Test: `belgeleme_fixtureData_matchNumberSerializeMatchTarafindanYazilmiyor`.
   (Bu serileştirici Match'in DİĞER tüm alanlarını — listId, rankingMethod,
   skor, round, groupId, isCompleted, createdAt — birebir koruyor; yalnız
   matchNumber eksik.)

**Önerilen düzeltme** (karar koordinatörde): `serializeRoundHistory`'ye
listId/score/groupId/matchNumber/tournamentId eklemek, `serializeMatch`'e
matchNumber eklemek. SWISS yöntemi zaten gizli olduğu için ACİL değil, ama
"gizli kod = güvenli kod" varsayımı yanlış olur diye kayda geçirildi.

## KAPSAM 2 — Archive sözleşmesi (ResultsViewModel ↔ ArchiveViewModel)

`ResultsViewModel.kt` (yazma: `saveArchive`, satır ~302-317) ve
`ArchiveViewModel.kt` (okuma: `selectArchive`, satır 96-135) SADECE OKUNDU;
bu ikisi `suspend` + `AndroidViewModel` içinde olduğu için JVM testinden
DOĞRUDAN çağrılamıyor — Gson adımları testte BİREBİR KOPYALANDI (ESKI-
MOTORLAR-SINAV-GOREV.md'nin "SADECE OKU, kopyalama testi yaz" deseniyle
aynı yöntem), ama veri TİPLERİ gerçek production sınıflarından alındı.

### 🟡 Gözlem — dört veri sınıfı çiftlenmiş, sessiz ayrışmaya açık
`ArchivableResult`/`ArchiveResult`, `ArchivableMatch`/`ArchiveMatch`,
`ArchivableLeagueSettings`/`ArchiveLeagueSettings`, ve `LeagueTableEntry`
(HER İKİ dosyada da AYRI tanımlı) — dört çift, sekiz sınıf, birebir aynı
alan adı/tipi. Bugün kusur YOK (round-trip tam çalışıyor), ama bu bir kopya-
kod deseni: biri değişip diğeri değişmezse Gson **hatasız ama YANLIŞ**
(eksik alan → null/varsayılan) veri üretir, çökme olmadan. Testler gerçek
sınıfları kullandığı için böyle bir gelecekteki ayrışmayı KIRMIZI test
olarak yakalayacak — bu, testin asıl kazandırdığı regresyon güvencesi.

### ✅ Doğrulanan (pozisyon/puan kayıpsızlığı — görevin asıl istediği)
- 3 ve 100 öğelik `finalResults` listelerinde **pozisyon sırası ve score
  değeri** (ondalık dahil, `1e-9` toleransla) birebir korunuyor.
- `matchResults`: null skor/winnerId (oynanmamış maç) ve dolu skor/kazanan
  ayrı ayrı test edildi, ikisi de doğru.
- `leagueTable`: yalnız LEAGUE yönteminde dolu geliyor; `null` olduğunda
  okuma tarafı **boş liste** döndürüyor (null DEĞİL) — bu davranış testle
  sabitlendi, ArchiveScreen bu yüzden `.isEmpty()` güvenle kullanabilir.
- `leagueSettings`: null/dolu iki durum da doğru.
- Türkçe karakter + özel karakter (tire, tırnak, apostrof, yeni satır)
  `songName`/`artist`/`album` alanlarında bozulmadan geri geliyor.
- `Archive.finalResults`/`matchResults` alanlarının GERÇEKTEN geçerli JSON
  metni olduğu ayrıca `org.json` ile çapraz doğrulandı (iki farklı JSON
  kütüphanesiyle).

## KAPSANAMAYAN (dürüst liste)
- Tam test paketi (306+) bu oturumda AYRICA koşulmadı (yalnız yeni 23 test
  koşuldu) — gradle kilidi kıt kaynak, üretim koduna dokunulmadığı için
  riski yapısal olarak sıfıra yakın görüyorum ama ÖLÇMEDİM.
- `ResultsViewModel.saveArchive` ve `ArchiveViewModel.selectArchive`
  fonksiyonlarının KENDİSİ (coroutine/DB/Application bağımlılığı yüzünden)
  çağrılmadı — yalnız içindeki Gson mantığı kopyalanarak sınandı. Fonksiyon
  gövdesi değişip Gson çağrıları farklılaşırsa bu testler ONU YAKALAMAZ.
- `RankingRepository.saveSwissState`/`loadSwissState`/`saveSwissFixture`
  bizzat çağrılmadı (DB/Room gerektiriyor); yalnız çağırdıkları
  serileştiricilerin JSON sözleşmesi test edildi.
- Room migration/DB düzeyinde Archive tablosu şeması test edilmedi (bu
  görevin kapsamı değildi — "arşivlenen sonuç geri okununca kayıpsız mı"
  sorusu JSON katmanında cevaplandı).

## GRADLE KİLİDİ NOTU
Bu görev sırasında filo çapında bir kilit sistemi (`oturumlar/GRADLE-KURALI.md`)
kuruldu ve birkaç geçiş sancısı yaşandı (retroaktif kilit denemem geçersiz
sayıldı, bir ara kilitsiz koşum iptal edildi — ayrıntı sohbet geçmişinde).
Bu koşum kilit protokolüne TAM uyularak alındı ve hemen bırakıldı.
