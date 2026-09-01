# İŞÇİ GÖREVİ — Hazır Listeler: Denetim + Senkron Bekçisi + Zenginleştirme

Sen bu görevi alan İŞÇİ oturumsun. Koordinatör aynı anda ViewModel/motor
katmanında çalışıyor — **ONUN DOSYALARINA DOKUNMA** (aşağıda sınır listesi).
Soru sorma, şartname eksikse RAPOR dosyasına yaz ve makul varsayımla ilerle.

## Bağlam
- Proje: Ranking (Android, Kotlin+Compose+Room) — kök: bu depo.
- Hazır listeler: `liste_kutuphanesi/*.csv` (kaynak) → `app/src/main/assets/hazir_listeler/` (kopya)
  → `app/src/main/java/com/example/ranking/data/HazirListeler.kt` (katalog) → `liste_kutuphanesi/README.md` (tablo).
- 🔴 CSV SENKRON KURALI (CLAUDE.md): dördü TEK commit'te değişir. Üçlü ayrışma
  bir kez yaşandı (909/1019/1811 üç farklı "gerçek").
- CSV biçimi RFC-4180, parser: `app/src/main/java/com/example/ranking/utils/CsvReader.kt`.
  Öğe adı 4. sütundan okunur; tüm sütunlar csvData JSON'una gider.

## İş 1 — SENKRON BEKÇİSİ TESTİ (en değerli iş, önce bu)
`app/src/test/java/com/example/ranking/HazirListelerSenkronTest.kt` yaz:
- `app/src/main/assets/hazir_listeler/` altındaki HER dosyayı dosya yolundan
  oku (JVM testi repo içinde koşar; `File("src/main/assets/...")` ya da
  modül kökünden göreli yol — çalışan yolu ölç, varsayma).
- Her dosya CsvReader.parseText'ten geçmeli; öğe sayısı > 0; boş ad yok;
  aynı listede mükerrer ad varsa RAPORLA (bazı listelerde meşru olabilir —
  testte hard fail yapma, ayrı bir "bilinen mükerrerler" listesi tut).
- HazirListeler.kt katalogundaki her kayıt: dosya gerçekten var VE
  katalogdaki öğe sayısı == CSV'nin gerçek satır sayısı (assert).
- Assets'teki her dosyanın liste_kutuphanesi'nde birebir eşi var (içerik
  karşılaştırması; farklıysa assert mesajı hangi dosya olduğunu söylesin).
- Kataloga kayıtlı olmayan yetim asset / asset'i olmayan katalog kaydı → assert.

## İş 2 — MEVCUT LİSTE DENETİMİ
- 34 dosyayı gez: bozuk satır, kayan sütun, ters tırnak, UTF-8 sorunları.
- Liste 02 (Sezen Aksu?) — 6 şarkının YouTube kimliği eksik: Yemen Türküsü,
  Değirmenler, Gönülçelen, Ünzile, Özgürce Yaşa, Her Şey İnsanlar İçin.
  Kimlikleri web'den bul, oembed ile DOĞRULA (başlık + kanal makul mü),
  CSV'lere işle. Doğrulanamayanı boş bırak ve rapora yaz.
- Sayı/test listeleri (33, 34): "Beklenen Sıra" sütunları gerçekten doğru mu.

## İş 3 — ZENGİNLEŞTİRME (en az 6 yeni liste; çeşitlilik hedefi)
Kurallar: veriler DOĞRULANABİLİR gerçek olmalı (uydurma yok); telifli uzun
metin (şarkı sözü, şiir tam metni) KOYMA; her listeye anlamlı ek sütunlar
(kıyas/eğitim değeri) ekle. Örnek adaylar (kendi seçimini yapabilirsin):
- Dünya ülkeleri (nüfus, başkent, kıta — ~50 büyük ülke)
- Kimya elementleri (sembol, atom no, grup — ilk 60)
- Osmanlı padişahları (saltanat yılları, dönem) — 36
- FIFA Dünya Kupası turnuvaları (yıl, şampiyon, ev sahibi)
- Nobel Edebiyat ödüllü yazarlar (yıl, ülke) — son 50
- Güneş sistemi + önemli uydular (çap, kütle, keşif)
- İstanbul semtleri / Türkiye illeri (plaka, bölge, nüfus)
- Klasik besteciler (dönem, doğum yılı, ülke)
Her yeni liste: `liste_kutuphanesi/NN_ad.csv` + assets kopyası + HazirListeler.kt
kaydı + README tablosu satırı — hepsi TEK commit. Senkron bekçisi testi
yeni listelerle de geçmeli.

## SINIRLAR (koordinatörle çakışma)
DOKUNMA: `ui/viewmodel/`, `ui/screens/` (RankingScreen vs.), `ranking/` motorları,
`repository/`. SENİN alanın: `liste_kutuphanesi/`, `app/src/main/assets/`,
`data/HazirListeler.kt`, `app/src/test/` altına YENİ dosyalar, README'ler.

## Çalışma düzeni
- Her mantıklı adımda commit (Türkçe mesaj, gövdeye kısa gerekçe).
- Derleme: `./gradlew assembleDebug` · test: `./gradlew testDebugUnitTest`
  (JDK ayarı gradle.properties'te hazır; sistem JDK 24 kullanma).
- Bitince RAPOR: `oturumlar/HAZIR-LISTELER-RAPOR.md` — ne yapıldı (rakamla),
  ne yapılamadı (nedeniyle), senkron testinin son durumu.
- Görev bitiminde 3 beep: `powershell -c "[Console]::Beep(800,300); [Console]::Beep(800,300); [Console]::Beep(800,300)"`
