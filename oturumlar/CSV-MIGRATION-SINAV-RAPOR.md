# İŞÇİ RAPORU — Room Migration Zinciri + Şema Sapma Bekçisi

**İşçi:** OPUS HAZIR KITA 105 (oturum: `ranking-81 [d735ed]`)
**Koordinatör:** `ranking-7d [e3f2ce]`
**Tarih:** 2026-09-01
**Durum:** ✅ TAMAMLANDI — 10 test yazıldı, **10/10 YEŞİL** (`BUILD SUCCESSFUL in 2m 20s`)

---

## Kapsam (koordinatörün iki mesajıyla netleşen hâli)

**Bende:** migration zinciri bekçisi + şema sapma bekçisi + `EMRE → EMRE_CORRECT`
dönüşümünün `EMRE_SIRALAMA`'ya bulaşmadığı denetimi.

**Bende DEĞİL:** CsvReader RFC-4180 sınavı — koordinatör ikinci mesajıyla geri alıp
`ranking-1f`'e verdi. Bu rapor CsvReader hakkında hüküm içermez.

**Sınır:** ana koda sıfır yazma. Eklenen tek kod dosyası:
`app/src/test/java/com/example/ranking/RoomMigrationZinciriTest.kt` (yalnız kaynağı
ve `app/schemas/*.json`'ı okur).

---

## Neden statik denetim?

Gerçek migration koşumu (`MigrationTestHelper`) bu modülde **yapılamıyor**:

- `app/src/androidTest` kaynak seti **yok**.
- Test sınıf yolunda `androidx.room:room-testing` ve bir SQLite sürücüsü **yok**
  (`app/build.gradle.kts` test bağımlılıkları: yalnız `junit`).

İkisini de eklemek `build.gradle.kts` değişikliği gerektirir; sınırım gereği
**dokunmadım** (bkz. BULGU-2). Bu yüzden denetim, kaynak metni + Room'un dışa
aktardığı şema JSON'ları üzerinden **statik** yapılır.

---

## Yazılan test: `RoomMigrationZinciriTest.kt` — 10 test, 10/10 yeşil

| # | Test | Ne yakalar |
|---|---|---|
| 1 | `zincir_1den_guncelVersiyona_kesintisiz` | version bump edilip migration yazılmaması, zincirde delik, mükerrer halka, çok adımlı atlama |
| 2 | `migrationAdi_Migration_argumanlariyla_ayni` | `MIGRATION_9_10 = object : Migration(9, 11)` — sessizce derlenir, zincirde delik açar |
| 3 | `tanimlanan_her_migration_addMigrations_icinde_kayitli` | migration yazılıp kaydedilmemesi (çift yönlü küme karşılaştırması) |
| 4 | `fallbackToDestructiveMigration_geri_sizmamis` | sessiz veri kaybının geri gelmesi (yorumları söküp bakar; gerekçe yorumunun silinmesini de uyarır) |
| 5 | `emreDonusumu_tamEsitlik_EMRE_SIRALAMA_etkilenmez` | `LIKE 'EMRE%'` / ön ek eşleşmesi; SET hedefi; 5 tablonun hepsinde uygulanmış mı |
| 6 | `yontem_kodu_EMRE_olarak_yeniden_kullanilmamis` | ana kodda `"EMRE"` yöntem kodunun yeniden doğması (CLAUDE.md kırmızı çizgisi) |
| 7 | `guncelVersiyonun_semasi_disa_aktarilmis_ve_tutarli` | güncel/önceki sürüm şemasının kaybolması; şema `version` ile `@Database version` ayrışması |
| 8 | `semaSapmasi_entity_tablolari_ile_sema_ayni` | entity `entities` listesine eklenmemiş / silinmiş entity'nin şemada kalması |
| 9 | `semaSapmasi_entity_kolonlari_ile_sema_ayni` | entity'ye kolon eklenip version bump + migration unutulması |
| 10 | `gecisFarki_migration_SQLinde_karsiligini_buluyor` | şemada değişen tablo/kolon/indeksin migration SQL'inde karşılığı yok → **taze kurulum ile yükseltilmiş kurulum farklı şema alır** |

Testler yolu **ölçerek** bulur (`src/...`, `app/src/...`, `../app/src/...`) —
JVM testinin çalışma dizini varsayılmaz.

---

## Kaynak okumasından KESİNLEŞEN sonuçlar

### ✅ EMRE → EMRE_CORRECT dönüşümü EMRE_SIRALAMA'ya BULAŞMIYOR (koordinatörün özel talebi)

`MIGRATION_8_9` içindeki beş ifadenin **hepsi tam eşitlik** kullanıyor:

```sql
UPDATE matches          SET rankingMethod = 'EMRE_CORRECT' WHERE rankingMethod = 'EMRE'
UPDATE ranking_results  SET rankingMethod = 'EMRE_CORRECT' WHERE rankingMethod = 'EMRE'
UPDATE voting_sessions  SET rankingMethod = 'EMRE_CORRECT' WHERE rankingMethod = 'EMRE'
UPDATE league_settings  SET rankingMethod = 'EMRE_CORRECT' WHERE rankingMethod = 'EMRE'
UPDATE archives         SET method        = 'EMRE_CORRECT' WHERE method        = 'EMRE'
```

`LIKE` yok, `'EMRE%'` yok. Test bunu iki katmanda doğrular: (a) sözdizimi —
ön ek eşleşmesi kullanılmamış; (b) **anlamsal kanıt** — çıkarılan koşul
`EMRE · EMRE_CORRECT · EMRE_SIRALAMA · EMRE_SIRALAMA_V2 · MERGE_SORT · HIBRIT`
değerlerine uygulanır ve yalnız `EMRE` eşleşmelidir.

Ayrıca beş tablonun **hepsinde** uygulanmış olması ayrıca assert edilir: biri
unutulsaydı o tabloda eski `EMRE` kayıtları öksüz kalırdı.

### ✅ Zincir bütünlüğü
17 migration (`MIGRATION_1_2` … `MIGRATION_17_18`), delik yok, mükerrer yok,
`@Database(version = 18)` ile son halka örtüşüyor, 17'sinin 17'si de
`addMigrations(...)` içinde kayıtlı.

### ✅ `fallbackToDestructiveMigration` kodda yok
Yalnız neden kaldırıldığını anlatan yorum duruyor — test bu ayrımı yorumları
sökerek yapar (yorumdaki anılma hata sayılmaz, hatta korunması istenir).

### ✅ v17 → v18 indeks farkı migration ile birebir örtüşüyor
Şema 18'de v17'ye göre **8 yeni indeks** var; `MIGRATION_17_18` tam 8
`CREATE INDEX IF NOT EXISTS` çalıştırıyor ve adlar birebir aynı:
`index_matches_listId_rankingMethod`, `index_ranking_results_listId_rankingMethod`,
`index_voting_sessions_listId_rankingMethod`, `index_voting_sessions_isCompleted`,
`index_league_settings_listId_rankingMethod`, `index_archives_method`,
`index_criterion_lists_isActive`, `index_tournaments_isCompleted`.

### ✅ Entity ↔ şema eşlemesi temiz kurulabiliyor
`data/` paketinde **@ColumnInfo · @Ignore · @Embedded · TypeConverter YOK** →
Kotlin property adı = SQLite kolon adı. Bu yüzden statik karşılaştırma birebir
güvenilir. 17 entity, 17 tablo; şema 18'de 161 alan.

---

## 🔴 BULGULAR (düzeltme yapılmadı — karar koordinatörün)

### BULGU-1 — Şema export'u v1–v15 için hiç tutulmamış
`app/schemas/com.example.ranking.data.RankingDatabase/` altında **yalnız
16.json, 17.json, 18.json** var. Sonuç: v1→v16 arasındaki 15 halka hiçbir araçla
gerçek şemaya karşı doğrulanamıyor; onlara yalnız statik okumayla güveniyoruz.
Testteki geçiş-farkı denetimi (test 8) bu yüzden yalnız **16→17 ve 17→18** için
koşabiliyor. Test bu durumu `[ŞEMA EXPORT DURUMU] mevcut=... eksik=...` satırıyla
görünür kılar, ama **kırmızıya düşürmez**: bu devralınan bir boşluk, gerileme değil.
Kırmızıya düşürdüğü tek şey, *güncel* ve *bir önceki* sürümün şemasının kaybolması.

**Not:** geçmiş export'lar geriye dönük üretilemez (o sürümdeki entity hâli lazım).
Yapılabilecek: bundan sonra `app/schemas` altındaki JSON'ların commit edilmesini
kural hâline getirmek.

### BULGU-2 — Gerçek migration koşumu altyapısı yok
`androidTest` kaynak seti ve `room-testing` bağımlılığı olmadığı için
`MigrationTestHelper` ile "v17 veritabanı aç → migrate et → şema doğrula"
zinciri **koşturulamıyor**. Statik denetim SQL'in *var* olduğunu gösterir,
*çalıştığını* göstermez (ör. bir `ALTER TABLE`'ın gerçekten geçtiğini).
Düzeltmesi `app/build.gradle.kts`'e bağımlılık eklemeyi gerektirir → sınırım
dışında, dokunmadım.

### BULGU-3 (süreç) — ÇÖZÜLDÜ: tek dosya tüm filonun testini durduruyordu

> **Sonuç:** koordinatör `HazirListelerSenkronTest.kt:261`'i `.orEmpty()` biçimiyle
> düzeltti; paralel koşum sorunu için `oturumlar/GRADLE-KURALI.md` (sert kilit sırası)
> yayınlandı. Aşağıdaki ölçüm o kuralın gerekçesidir.

Koşum sırasında `app/src/test` altında **14 yeni, commit edilmemiş** test dosyası
vardı (farklı işçilerden). `HazirListelerSenkronTest.kt:261` Kotlin tip çıkarımı
hatası verdiği için `compileDebugUnitTestKotlin` düştü ve **modüldeki bütün
testler** koşamadı — kimin yazdığından bağımsız olarak.
Ayrıca aynı anda 17+ `java.exe` süreci vardı; tek koşum **13 dk 27 sn** sürdü ve
ilk 10 dakika tek satır çıktı vermedi (Gradle kilit kuyruğu).

---

## Koşum sonucu — 10/10 YEŞİL

```
./gradlew testDebugUnitTest --tests "*RoomMigrationZinciriTest*"
BUILD SUCCESSFUL in 2m 20s
```

`app/build/test-results/testDebugUnitTest/TEST-com.example.ranking.RoomMigrationZinciriTest.xml`:

```
tests="10" skipped="0" failures="0" errors="0" time="1.485"
```

Testlerin bastığı ölçüm satırları:

```
[DENETLENEN GEÇİŞLER] [(16, 17), (17, 18)]
[ŞEMA EXPORT DURUMU] mevcut=[16, 17, 18] eksik=[1..15]
```

Yani **denetlenebilen iki geçişte de** (16→17, 17→18) migration SQL'i şema farkını
tam karşılıyor; kalan 15 halka BULGU-1 nedeniyle şemaya karşı doğrulanamıyor,
yalnız zincir/kayıt/sözdizimi denetiminden geçiyor.

### Koşuma varmak üç denemeyi aldı (hiçbiri bu testin hatası değil)

| Deneme | Sonuç | Sebep |
|---|---|---|
| 1 | `compileDebugUnitTestKotlin FAILED` (13 dk 27 sn) | başka bir işçinin dosyası: `HazirListelerSenkronTest.kt:261` tip çıkarımı hatası → modüldeki TÜM testler düştü |
| 2 | `compileDebugKotlin FAILED` (11 dk 21 sn) | `java.io.IOException: Could not delete app\build\tmp\kotlin-classes\debug\com` — paralel Gradle koşumları aynı build dizinini bozuyor |
| 3 | ✅ `BUILD SUCCESSFUL` (2 dk 20 sn) | filo kilidi (`oturumlar/GRADLE-KURALI.md`) altında tek başına koştu |

**Ölçülen fark: kilitsiz 11–26 dk ve çoğunlukla bozuk çıktı · kilitli 2 dk 20 sn ve temiz.**
Kilit kuralı bu ölçümle doğrulanmış oldu.
