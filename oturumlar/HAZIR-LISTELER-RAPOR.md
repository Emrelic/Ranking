# RAPOR — Hazır Listeler: Denetim + Senkron Bekçisi + Zenginleştirme

**İşçi:** OPUS HAZIR KITA 101 (oturum `ranking-80`) · **Koordinatör:** `ranking-7d`
**Görev dosyası:** `oturumlar/HAZIR-LISTELER-GOREV.md`
**Branch:** `ileri-tusu-asagida-crash-fix`

---

## Rakamlarla özet

| ölçü | değer |
|---|---|
| Yeni test dosyası | 1 (`HazirListelerSenkronTest.kt`) |
| Yeni test fonksiyonu | 10 |
| Geçen test | 10 / 10 (son koşum) |
| Denetlenen mevcut liste | 34 |
| Doldurulan eksik YouTube kimliği | 6 / 6 (hepsi oembed ile doğrulandı) |
| Yeni liste | 6 |
| Yeni öğe | 241 |
| **Kütüphanenin son hâli** | **40 liste · 2475 öğe · 742 görselli öğe** |

---

## İş 1 — Senkron bekçisi testi

`app/src/test/java/com/example/ranking/HazirListelerSenkronTest.kt`

Hazır liste kütüphanesinin dört ayrı "gerçek" kaynağını birbirinden **bağımsız**
okuyup karşılaştırır:

① `liste_kutuphanesi/` CSV'leri → ② `app/src/main/assets/hazir_listeler/`
→ ③ `data/HazirListeler.kt` katalogu → ④ `liste_kutuphanesi/README.md`

10 test:

| test | ne denetler |
|---|---|
| `dizinlerBulunurVeBosDegil` | Depo kökü bulunuyor mu, dizinler dolu mu |
| `herAssetCsvParseEdilirVeBosAdIcermez` | Her CSV `CsvReader.parseText`'ten geçiyor, 0 öğe üretmiyor, boş ad bırakmıyor |
| `assetKopyasiKutuphaneKaynagiylaBirebirAyni` | Kaynak ↔ assets **bayt bayt** aynı; **iki yönlü** (yetim kopya + kopyalanmamış kaynak) |
| `katalogdakiHerDosyaGercektenVar` | Katalogdaki her kaydın dosyası diskte var |
| `katalogOgeSayilariCsvIleAyni` | Katalogdaki öğe sayısı == CSV'nin **gerçek** (parse edilmiş) öğe sayısı |
| `yetimAssetVeMukerrerKatalogKaydiYok` | Katalogsuz asset yok, mükerrer katalog kaydı yok, sayılar eşit |
| `readmeTablosuDosyalarVeKatalogaUyar` | README tablosundaki dosya adı + satır sayısı katalogla aynı (iki yönlü) |
| `readmeOzetSayilariGercekleAyni` | README özet cümlesindeki liste / öğe / görselli sayıları ölçülen gerçekle aynı |
| `mukerrerOgeAdlariYalnizcaBilinenlerdenIbaret` | Yeni mükerrer öğe adı çıkarsa uyarır (bilinenler beyaz listede) |
| `herCsvEnAzDortSutunlu` | Biçim kuralı: 4. sütun sıralanacak öğedir |

**Ölçülen yol sorunu:** görev "çalışan yolu ölç, varsayma" diyordu. Gradle birim
testini `app/` dizininde koşturuyor, IDE bazen depo kökünde. Test bunu varsaymak
yerine çalışma dizininden **yukarı yürüyerek** hem `liste_kutuphanesi` hem
`app/src/main/assets/hazir_listeler` içeren ilk dizini depo kökü kabul ediyor;
ikisinde de çalışır.

**Not:** Bu dosya bir ara `emptySet()` tip çıkarımı yüzünden derlenmedi ve
paylaşılan test kaynak setinde bütün işçileri durdurdu; koordinatör tek satırlık
düzeltmeyi (`orEmpty()`) yaptı. Ders: test dosyasını derlendiğini görmeden bırakma.

---

## İş 2 — Mevcut liste denetimi

### Liste 02 — eksik YouTube kimlikleri (6/6 çözüldü)

Kimlikler web'den bulundu ve **YouTube oembed** ile doğrulandı (dönen başlık +
kanal makul mü diye bakıldı). Uydurma yok, doğrulanamayan yok:

| şarkı | kimlik | oembed başlığı | kanal |
|---|---|---|---|
| Yemen Türküsü | `AZWc3QdX_7I` | Şebnem Ferah - Yemen Türküsü | Casper Crasper |
| Her Şey İnsanlar İçin | `-w-f_BdJCsE` | Şebnem Ferah - Her Şey İnsanlar İçin (Kelimeler Yetse) | Pasaj Müzik |
| Değirmenler | `hVtShuZ6I_0` | Şebnem Ferah - Değirmenler / Bülent Ortaçgil Tribute (Official audio) | Ada Müzik |
| Gönülçelen | `bYtplSQ_7qg` | Şebnem Ferah - Gönülçelen (Balans ve Manevra Orijinal Film Müzikleri) | Pasaj Müzik |
| Ünzile | `71LvpM4QZRY` | Şebnem Ferah - Ünzile (Onno Tunç Şarkıları - 2007) | Gloss Musik GmbH |
| Özgürce Yaşa | `G2nXKQ-Zz2s` | Şebnem Ferah & Hayko Cepkin & Badem & TNK & Aylin Aslım - Özgürce Yaşa (Official Video) | paga play |

Liste 02'de artık **0** eksik kimlik var. Liste 32'de (Sözleriyle) eksik kimlik
zaten yoktu — kontrol edildi.

⚠️ Yemen Türküsü'nün kimliği resmî bir sanatçı/plak kanalında değil, adı doğru
eşleşen bir kullanıcı kanalında. Başlık doğrulandı ama kanal resmî değil; resmî
bir yükleme bulunursa değiştirilebilir.

### Listeler 33 / 34 — "Beklenen Sıra" doğru mu

Üç sütun da bağımsız hesapla denetlendi, **hata bulunmadı**:

- `Beklenen Sıra`: sayıya göre azalan sıralamada her satırın gerçek yeriyle birebir aynı (100 ve 200 satır).
- `Grup`: 33'te 1-25/26-50/51-75/76-100, 34'te 1-50/51-100/101-150/151-200 — hepsi doğru aralıkta.
- `Basamak`: 1/2/3 basamaklı etiketleri doğru.
- Sayı kümeleri tam ve tekrarsız: 1-100 (toplam 5050) ve 1-200 (toplam 20100).

### Genel biçim denetimi

- 34 dosyanın **hepsi** CsvReader'dan geçiyor, boş öğe adı yok (test kapsamında sürekli denetleniyor).
- Kaynak ↔ assets 34/34 **bayt bayt** aynıydı; ayrışma yoktu.
- Satır sayısı ↔ katalog ↔ README üçü de tutarlıydı (34 liste / 2234 öğe).
- Liste 32 tırnaklı çok satırlı hücre içeriyor (şarkı sözleri): dosyada 3209 satır
  var ama **80 kayıt**. Kayıt sayan her denetim RFC-4180 parser'dan geçmeli;
  `wc -l` yanlış cevap verir. Senkron testi bunu doğru sayıyor.

---

## İş 3 — Zenginleştirme: 6 yeni liste (241 öğe)

Hepsi kaynak + assets + katalog + README olmak üzere **dört yerde** aynı commit'te.

| # | dosya | öğe | kategori | ek sütunlar (kıyas/eğitim değeri) |
|---|---|---|---|---|
| 35 | `35_dunya_kupasi_turnuvalari.csv` | 23 | Teknoloji ve Spor | Şampiyon, Ev Sahibi, Finalist, Final Skoru, Takım Sayısı, Gol Kralı, Kıta |
| 36 | `36_nobel_edebiyat_odulleri.csv` | 51 | Sanat ve Kültür | Ülke, Yıl, Yazdığı Dil, Öne Çıkan Eseri, Başlıca Türü |
| 37 | `37_gunes_sistemi.csv` | 35 | Bilim ve Doğa | Tür, Çap (km), Yörünge, Keşif Yılı, Uydu Sayısı, Uzaklık, Kâşifi |
| 38 | `38_klasik_besteciler.csv` | 52 | Sanat ve Kültür | Dönem, Yaşadığı Yıllar, Ülke, Ünlü Eseri, Türü, Doğum Yeri |
| 39 | `39_yaz_olimpiyatlari.csv` | 30 | Teknoloji ve Spor | Ev Sahibi Ülke, Yıl, Şehir, Katılan Ülke, Madalya Lideri, Kıta, Öne Çıkan Özelliği |
| 40 | `40_programlama_dilleri.csv` | 50 | Teknoloji ve Spor | Paradigma, Çıkış Yılı, Tasarımcı, Tipleme, Kullanım Alanı, Çalışma Biçimi |

Kapsam seçimi bilerek **tam küme**: 1930-2026 arası **bütün** Dünya Kupaları,
1896-2024 arası **bütün** yaz olimpiyatları, 1975-2025 arası **bütün** Nobel
Edebiyat ödülleri. Böylece "eksik mi seçilmiş" tartışması olmuyor ve listeler
kendi başına referans değeri taşıyor.

Telifli uzun metin (şarkı sözü, şiir tam metni) **konmadı**.

### Doğrulama — ne ölçüldü, ne bulundu

Yeni listelerin sayısal sütunları uydurulmadı; kaynakla karşılaştırıldı ve
**dört hata yakalanıp düzeltildi**:

| ne denetlendi | yöntem | sonuç |
|---|---|---|
| Yaz olimpiyatları "Katılan Ülke Sayısı" — 30 turnuvanın **hepsi** | Her turnuvanın Wikipedia infobox'ı tek tek okundu | **3 hata düzeltildi**: 1900 (24→**26**), 1904 (12→**13**), 1988 (159→**160**). Kalan 27 değer doğru çıktı. |
| Dünya Kupası finalleri — 22 satırın hepsi (yıl/şampiyon/finalist/skor) | FIFA Dünya Kupası finalleri listesiyle satır satır karşılaştırıldı | Hepsi doğru. **Eksik bulundu:** 2026 Dünya Kupası oynanmış (İspanya 1-0 Arjantin, uzatma) — 23. satır olarak eklendi. |
| Nobel Edebiyat — kapsam | Ödül listesi kontrol edildi | **Eksik bulundu:** 2025 ödülü verilmiş (László Krasznahorkai, Macaristan) — 51. satır olarak eklendi. |
| Güneş sistemi çapları | Wikipedia madde değerleriyle karşılaştırıldı | **1 hata düzeltildi**: Haumea 1632 → **1544 km** (küresel olmadığı için ortalama çap kullanılır). Diğerleri NASA/Wikipedia değerleriyle uyumlu. |
| Olimpiyat "Madalya Sıralaması Lideri" — 30 satırın 5'i (1912, 1956, 1972, 2008, 2024) | İlgili madalya tablosu maddeleri okundu | 5/5 doğru (ABD 26 altın · SSCB 37 · SSCB 50 · Çin 48 · ABD 40). Kalan 25 satır örnekleme dışında kaldı. |

Ders: bir sütunun "bildiğim doğru" hâli ile kaynaktaki hâli aynı değil. Denetlenen
altı değerin ikisi ilk turda yanlış çıktığı için o sütunun **tamamı** tek tek
okundu; başka türlü 3 hata listede kalırdı.

---

## Ne yapılamadı / açık kalanlar

1. **Kaynağa karşı okunmayan sütunlar var.** Olimpiyat katılımcı sayıları,
   Dünya Kupası finalleri, Nobel kapsamı ve gezegen çapları tek tek denetlendi
   (yukarıdaki tablo); madalya liderleri 30 satırın 5'inde örneklendi. Ama
   besteci doğum yerleri/ünlü eserleri ve programlama dili tasarımcıları
   **tek tek kaynağa karşı okunmadı** — yaygın kabul gören bilgilerdir, ancak
   aynı titizlikte doğrulanmadı. Bunu "test edildi" diye yazmıyorum;
   denetlenmesi gereken açık iş olarak bırakıyorum.
2. **Görsel sütunu yok.** 6 yeni listenin hiçbirine görsel adresi eklenmedi;
   her adresin tek tek doğrulanması gerekirdi ve süre yetmedi. Görselli öğe
   sayısı bu yüzden 742'de sabit kaldı.
3. **CLAUDE.md güncellenmedi** — koordinatörün dosyası olduğu için elleyip
   ayrışma yaratmadım. İçindeki "31 liste, 1811 öğe" **yanlış**; doğrusu artık
   **40 liste, 2475 öğe, 742 görselli**. Koordinatör bu commit'ten sonra
   güncelleyecek (aramızda konuşuldu).
4. **Yemen Türküsü kimliği resmî kanal değil** (yukarıda not edildi).
5. Yeni listelerde **mükerrer öğe adı yok**; `BILINEN_MUKERRERLER` beyaz listesi
   bu yüzden boş bırakıldı — ilk meşru mükerrer çıktığında oraya yazılacak.

---

## Senkron testinin son durumu

`./gradlew :app:testDebugUnitTest --tests "*HazirListelerSenkronTest*"`
→ **10 test, 10 geçti** (40 listenin tamamı üzerinde).
