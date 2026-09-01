# CsvReader — RFC-4180 UÇ DURUM SINAVI RAPORU

**Sınavı yapan:** OPUS HAZIR KITA 106 (`ranking-1f [ca4c3f]`)
**Tarih:** 2026-09-02
**Görevi veren:** koordinatör (`ranking-7d`)
**Hedef:** `app/src/main/java/com/example/ranking/utils/CsvReader.kt`
**Üretilen dosya:** `app/src/test/java/com/example/ranking/CsvReaderUcDurumSinaviTest.kt`

## Sonuç

```
33 test · 0 hata · 0 atlanan   (testDebugUnitTest, BUILD SUCCESSFUL 1m13s)
```

Sınırlara uyuldu: yalnız `app/src/test/` altına **tek yeni dosya**; `CsvReader.kt`'ye
**dokunulmadı**. Gradle koşumu filo kilit protokolüyle yapıldı (69 dk kuyrukta beklendi,
koşum bitiminde kilit hemen bırakıldı).

**Test stili:** kusurlar KIRMIZI test yerine `belgeleme_` önekli YEŞİL testle
sabitlendi — evin mevcut idiomu (`belgeleme_bosAdliSatirSessizceDusuyor`) ve
kırmızı test tüm filonun koşumunu kalıcı kırmızıya boyardı. Koordinatör onayladı.
Bu testlerin yeşil olması "sorun yok" demek DEĞİL; mevcut kusurlu davranışın
sessizce değişmesini engelliyor.

**Tekrar yok:** `CsvReaderTest` (11 test) ve `CsvReaderDeepTest` (52 test) baştan
sona okundu. Onlarda kapsanan hiçbir senaryo tekrarlanmadı — bu dosya yalnız
o ikisinde BULUNMAYAN boşlukları vuruyor.

> **Bir dahaki denetimi yapana:** aşağıdaki [✅ SAĞLAM ÇIKANLAR](#-sağlam-çıkanlar)
> tablosu kusur listesi kadar önemli. Emoji/ZWJ, NFD→NFC, 36 bin karakterlik
> hücre, karışık satır sonları, 1000 satırlık dosya — hepsi ölçüldü ve temiz
> çıktı. **Aynı taşı ikinci kez kaldırma;** vaktini K1-K9'a ayır.

---

# 🔴 KUSURLAR

Şiddet sırasına göre. Hiçbiri düzeltilmedi; karar koordinatörde.

## K1 — Tek sütunlu dosyada BAŞLIK SATIRI ÖĞE SAYILIYOR ⬛ yüksek

**Test:** `belgeleme_tekSutunluDosyada_baslikVeriSayiliyor`
**Yer:** `CsvReader.kt` — `parseText`, `hasHeader` hesabı

```kotlin
val hasHeader = ilkSatir.size >= 2 && ilkSatir[0].toIntOrNull() == null
```

`size >= 2` şartı yüzünden **tek sütunlu dosyada başlık tespiti hiç çalışmıyor**.

```
Şarkı          ← sütun adı
Firuze
Gülümse
```
→ 3 öğe geliyor, **ilk öğenin adı "Şarkı"**. 100 şarkılık listeden 101 öğe çıkar
ve turnuvanın 1. sırasında bir sütun adı yarışır. Sessiz; hiçbir uyarı yok.

Not: 4. sütun sözleşmesi tek sütunlu dosyada geçerli değil, ama başlık sorunu
tek sütunlu listelerin *hepsini* etkiliyor.

## K2 — Başlığı BOŞ olan sütunun değeri csvData'dan TÜMÜYLE düşüyor ⬛ orta-yüksek

**Test:** `belgeleme_csvData_basligiBosSutun_degeriTumuylaDUSURUYOR`
**Yer:** `mapRowToSong`

```kotlin
if (i < parts.size && headers[i].isNotBlank()) { json.put(headers[i], parts[i]) }
```

```
No,A,,Ad
1,a,https://gorsel/1.jpg,Test
```
→ csvData 3 anahtar; **görsel URL'si hiçbir yerde yok.** Excel'den gelen
dosyalarda görsel/URL sütununun başlıksız olması yaygın; kütüphanenin 742
görselli listesi bu yoldan geçiyorsa risk gerçek. Sessiz kayıp.

## K3 — Kapanmamış tırnak, geri kalan TÜM satırları yutuyor ⬛ yüksek etki

**Test:** `belgeleme_kapanmamisTirnak_geriKalanSatirlariYutar`

```
Sanatçı,Şarkı
Queen,"Bohemian
ABBA,Mamma Mia
Queen,Radio Gaga
```
→ **1 öğe.** Kalan iki satır aynı hücrenin içine düşüyor.

Bu RFC-4180'e göre *doğru* davranış — ama kullanıcı için felaket sessizliği:
tek bir kapatılmamış tırnak, 50 satırlık listeyi 1 öğeye indiriyor ve içe
aktarma "başarılı" diyor. **Öneri (kod değişikliği değil, ürün kararı):** dosya
sonunda `inQuotes == true` kaldıysa içe aktarmada uyarı gösterilsin.

İyi haber: veri KAYBOLMUYOR, tek hücrede toplanıyor
(`kapanmamisTirnak_dosyaSonunda_veriKaybolmaz` — son alan sağlam okunuyor).

## K4 — İlk satırda ayraç yoksa başlık tespiti çöküyor ⬛ orta

**Test:** `belgeleme_ilkSatirdaAyracYoksa_baslikTespitiCokuyor`

Başlık kararı yalnız **İLK satırın** hücre sayısına bakıyor:

```
Şarkı Listesi                       ← tek hücre (dosya başlığı gibi yazılmış)
1,Sezen Aksu,Gülümse,Firuze
2,MFÖ,Ele Güne,Diday
```
→ Başlık öğe olur (3 öğe) **ve TÜM satırlar csvData'sız kalır** — o liste için
tablo görünümü tümden kaybolur. Tek satırlık bir sapma dosyanın tamamını
düz metne indiriyor.

## K5 — Tekrarlı başlık adı önceki sütunu EZİYOR ⬛ orta

**Test:** `belgeleme_csvData_tekrarliBaslikAdi_oncekiSutunuEZIYOR`

csvData bir `JSONObject`; aynı adlı iki başlıkta ikincisi birincinin değerini
eziyor.

```
No,Not,Ad,Not
1,ILK_DEGER,Test,IKINCI_DEGER
```
→ csvData'da 3 anahtar, `Not = "IKINCI_DEGER"`, **`ILK_DEGER` hiçbir yerde yok.**
Elle hazırlanan listelerde iki "Not" / iki "Puan" sütunu olağan.

## K6 — Harften sonra gelen tırnak virgülü koruyamıyor (asimetri) ⬛ orta

**Test:** `belgeleme_metinSonrasiTirnak_virguluKoruyamiyor`
**Yer:** `parseRows` — `ch == '"' && currentField.isBlank()`

Şart `isBlank()` olduğu için **boşluktan sonraki tırnak alanı açıyor**
(mevcut `alanBasindakiBosluktanSonraTirnak_tirnakliSayilir` bunu bilerek
istiyor), ama **harften sonraki tırnak açmıyor**:

```
1,x,y,Grup "Ali, Veli"
```
→ virgül ayraç sayılıyor, satır bölünüyor, **ad = `Grup "Ali`**. Yarım kalmış bir
öğe adı listeye giriyor.

## K7 — Tırnaklı alandaki baş/son boşluk trim ediliyor ⬛ düşük (RFC sapması)

**Test:** `belgeleme_tirnakliAlandakiBasSonBoslugu_trimEdiliyor`

RFC-4180'de tırnak içi boşluk anlamlıdır. `parseText` her hücreye koşulsuz
`trim()` uyguluyor: `"   Ali   "` → `Ali`. Bu proje için muhtemelen İSTENEN
davranış (kullanıcı CSV'lerinde boşluk çöp), ama sözleşme yazılı değildi —
artık sabit.

## K8 — Tırnak içi CRLF'te CR hücrede kalıyor ⬛ düşük

**Test:** `belgeleme_tirnakIciCRLF_crKarakteriHucredeKaliyor`

Tırnak içi satır sonu normalize edilmiyor: CRLF'li dosyada çok satırlı hücre
`"Pink\r\nFloyd"` içeriyor. Tek satırlık bir Compose `Text`'te ham CR görünmez
kutu/kayma üretebilir. Mevcut `tirnakIciSatirSonu_CRLF` testi yalnız "Pink ve
Floyd geçiyor mu" diye bakıyor, CR'nin akıbetini sabitlemiyordu — artık sabit.

## K9 — Tek sütunlu dosyada csvData hiç üretilmiyor ⬛ düşük

**Test:** `belgeleme_csvData_tekSutunluDosyadaHIC_URETILMIYOR`

K1'in yan etkisi: başlık tespit edilmediği için `csvData` null kalıyor →
o liste için tablo görünümü yok. K1 çözülürse bu da kendiliğinden düzelir.

---

# ✅ SAĞLAM ÇIKANLAR

Sınav bu alanlarda **hiçbir kusur bulamadı**:

| Alan | Test | Sonuç |
|---|---|---|
| csvData kayıpsızlığı (9 sütun, tam satır) | `csvData_dokuzSutunluTamSatir_tumSutunlarGeriOkunabiliyor` | 9/9 sütun birebir geri okunuyor (URL'deki `?x=1&y=2` dahil) |
| csvData — tırnak/virgül/satır sonu JSON turu | `csvData_tirnakliVeVirgulluDegerJsonaKacisliYaziliyor` | kaçışlı yazılıp birebir geri okunuyor |
| csvData — eksik alanlı satır | `csvData_basliktanEksikAlanliSatirdaAnahtarHicYok` | anahtar hiç yazılmıyor (okuyan `has()` kontrolü yapmalı) |
| Yalnız başlık satırı içeren dosya | `yalnizBaslikSatiriIcerenDosya_bosListeDoner_patlamaz` | boş liste, LF/CRLF/newline'sız üç biçimde de |
| Karışık satır sonları (LF + CRLF + CR aynı dosyada) | `karisikSatirSonlari_LF_CRLF_CR_ayniDosyada` | 4/4 öğe doğru |
| Dosya sonu satır sonu · arka arkaya boş satır | `dosyaSonundakiSatirSonu_hayaletSatirUretmez` | hayalet satır yok |
| Kapanmamış tırnakta son alan | `kapanmamisTirnak_dosyaSonunda_veriKaybolmaz` | veri kaybolmuyor, çökme yok |
| Alan ortasındaki tırnak (`5" plak`) | `belgeleme_alanOrtasindakiTirnak_duzMetinSayilir` | harfi harfine korunuyor |
| Kapanış tırnağından sonraki metin (`"ab"cd`) | `belgeleme_kapanisTirnagindanSonrakiMetin_bitisikYaziliyor` | hoşgörülü birleştirme, çökme yok |
| **Emoji — vekil çift (surrogate pair)** | `emoji_vekilCift_bozulmadanGecer` | 😊 bölünmüyor |
| **Emoji — ZWJ dizisi (👨‍👩‍👧)** | `emoji_zwjDizisi_parcalanmaz` | 3 vekil çift + 2 ZWJ tam korunuyor |
| Emoji + tırnak içi virgül | `emoji_tirnakIciVirgulle_birlikte` | 🇹🇷 bayrağı + virgül tek alan |
| Emoji — csvData JSON turu | `emoji_csvDataJsonundanGeriOkunabiliyor` | 🎵 sağ çıkıyor |
| Emoji — bayt düzeyi UTF-8 doğrulaması | `emoji_baytDuzeyinde_utf8OlarakCozulur` | `isValidUtf8` true, cp1254'e düşmüyor |
| **NFD → NFC normalizasyonu** | `nfd_ayrisikTurkceHarf_nfcYeToplanir` | `Gülümse` → `Gülümse` (7 karakter) |
| NFD — başlık adı da normalize | `nfd_baslikAdiDaNormalizeEdilir_jsonAnahtariBirlesikOlur` | JSON anahtarı birleşik biçimde |
| **Çok uzun hücre (şarkı sözü, ~36 bin karakter)** | `cokUzunHucre_sarkiSozuBoyutu_kayipsizOkunur` | kayıpsız; 1199 satır sonu korunuyor; `""` kaçışları çözülüyor |
| 50 bin karakterlik hücre sonrası satır | `cokUzunHucre_sonrakiSatirlariBozmaz` | sonraki satır sağlam |
| 1000 satırlık dosya | `binSatirlikDosya_butunlukKorunur` | 1000/1000, tırnaklı albüm alanları dahil |
| Ad **daima** 4. sütundan (sözleşme) | `ad_daimaDorduncuSutundan_baslikAdlariOnemsiz` | başlıkta "Şarkı" 2. sütun olsa bile ad 4. sütundan — konumsal sözleşme sabitlendi |
| Boş / sayısal olmayan ilk hücre | `bosIlkHucre_...`, `sayisalOlmayanIlkHucre_...` | trackNumber 0'a düşüyor, satır kaybolmuyor |
| `""` ile yazılmış boş ad | `bosTirnakliAd_satirSessizceDusuyor` | boş ad kuralına takılıp düşüyor (mevcut sözleşmeyle tutarlı) |

---

# Öncelik önerisi (karar koordinatörde)

1. **K1** — tek sütunlu dosyada başlık yutma. En görünür kullanıcı hatası; tek
   satırlık düzeltme değil (tek sütunlu dosyada başlık olup olmadığı yapısal
   olarak belirsiz — `belgeleme_basliksizIkiSutunluListe_...` ile aynı sınıf
   sorun, çözümü muhtemelen içe aktarmada kullanıcıya sormak).
2. **K2** — başlıksız sütun kaybı. Düzeltmesi ucuz: boş başlığa `"Sütun ${i+1}"`
   gibi yedek ad ver. Görselli listeleri koruyor.
3. **K5** — tekrarlı başlık. K2 ile aynı yerde, aynı ucuzlukta (`Not (2)`).
4. **K3** — kapanmamış tırnak uyarısı. Kod değil, ürün kararı: içe aktarma
   sonunda "dosyada kapatılmamış tırnak var, N öğe okundu" uyarısı.
5. **K6** — tırnak asimetrisi. Dikkat: `isBlank()` → `isEmpty()` yapmak mevcut
   `alanBasindakiBosluktanSonraTirnak_tirnakliSayilir` testini KIRAR. İki
   davranış birbirini dışlıyor; bilinçli seçim gerekir.
6. **K7 · K8 · K9** — düşük şiddet, K7 muhtemelen istenen davranış.

---

## Not: sınav sırasında uyulan filo kuralı

`oturumlar/GRADLE-KURALI.md` mesajla ulaştığında bu oturumun kuralsız başlattığı
bir gradle koşumu uçuştaydı; **iptal edildi**, kilit protokolüne geçildi
(`ranking-81` → `ranking-80` kuyruğu, 69 dk bekleme), tek çağrıda koşuldu,
kilit hemen bırakıldı. Kilit kırılmadı, ölü kilit görülmedi.
