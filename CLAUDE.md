# Ranking Pro - Gelişmiş Sıralama ve Değerlendirme Sistemi

## Uygulama Hakkında

### Ranking Sistemi Nedir?
Liste şeklinde içeri atılmış veya teker teker girilmiş çeşitli öğeleri sıralamak, puanlamak, birbiri arasında kıyaslamak veya turnuva şeklinde eşleşmelerini ve karşılaşmalarını sağlamak, puan durumlarını, eşleştirmelerini yönetmek, kriterlere göre değerlendirilmesini sağlamak üzere oluşturulmuş bir sistemdir. (Android: Kotlin + Jetpack Compose + Room)

### Kullanım Amaçları
Sportif turnuva/lig yönetimi, sübjektif tercih sıralamaları (şarkı, film, sanatçı...), çok kriterli değerlendirme, eğitim amaçlı sıralamalar (ülkeler, elementler...), matematiksel test sıralamaları, farkındalık ve eğlence. Gömülü hazır liste kütüphanesi: `liste_kutuphanesi/README.md` (31 liste, 1811 öğe, 742 görselli).

---

## Aktif Sıralama Yöntemleri (kullanıcının seçebildiği)

| Kod | Ad | Durum |
|---|---|---|
| MERGE_SORT | İkili Karşılaştırma | 🟢 En sağlam, en iyi testli (binary insertion + replay) |
| HIBRIT | Hibrit İsviçre (Kanıt Turlu) | 🟢 4 tur İsviçre + kanıt turları (adım 20-6-2-1); replay tabanlı motor (`HibritKanitSistemi.kt`); n=200'de ~1.900 maçla garantili tam sıralama (tam Emre 10.200 maç/sapma 5) |
| EMRE_CORRECT | Geliştirilmiş İsviçre (Emre Usulü) | 🟢 Canlı hibrit motor, Faz 1-2 düzeltmeleri uygulandı |
| LEAGUE | Lig | 🟢 Circle-method fikstür, 3/1/0 puan + averaj |
| DIRECT_SCORING | Direkt Puanlama | 🟢 Çalışır |

UI'dan çıkarılmış/gizlenmiş yarım özellikler (kodu duruyor, seçilemez):
- **SWISS**: kural ihlalleri vardı (bye yok, tekrar eşleşme, çalışmayan persistence); EMRE_CORRECT aynı ihtiyacı karşılıyor
- **ELIMINATION / FULL_ELIMINATION**: puanlama ekranı stub, grup dağılımı hatalı
- **SINGLE / DOUBLE_ELIMINATION**: algoritmaları tamamlanmadı
- **YouTube Analizi**: API anahtarı yok, yazılan veri geri okunmuyor (ana menü kartı gizli)

Gerekçeler ve ayrıntılar: **ANALIZ_RAPORU.md** (Faz 2 kararları).

---

## Geliştirilmiş İsviçre Sistemi (Emre Usulü) — Kurallar

- 🔴 **İki takım birbiriyle SADECE BİR KEZ eşleşir** (en kırmızı çizgi)
- Her turda tam eşleştirme: çift takımda n/2 maç, tek takımda (n-1)/2 maç + 1 bye
- **Bye kuralı**: en alttan başlayarak bye geçmemiş ilk takım; herkes bye geçtiyse en az bye geçmiş alttaki takım (adil rotasyon)
- **Puan**: galibiyet 1, beraberlik 0.5, bye 1
- **Tiebreaker zinciri** (TÜM maç geçmişine bakar): H2H puanı → direkt maç → en az mağlubiyet → tur öncesi sıralama
- **Maç numaralandırma**: üstten seçilen eşleşme 1, 2, 3...; alttan seçilen N, N-1... Oylama sırası matchNumber ASC (1 → 2 → 3)
- **Bitiş**: tekrarsız tam eşleştirme kurulamazsa veya hiçbir eşleşme aynı puanlı değilse turnuva biter
- ⚠️ **Tek sayılı turnuvada tam round-robin'e ULAŞILMAZ** (kusur değil, kuralın sonucu): bye 1 puan, beraberlik 0.5 getirdiği için puanlar erken ayrışır ve "aynı puanlı eşleşme yok" kuralı turnuvayı bitirir. Ölçüldü: **n=3 → 1 tur, n=5 → 2 tur**. Çift sayıda sapma yok (n=4 → 3 tur). Kullanıcı "5 öğe girdim, 2 turda bitti" diye şaşırabilir — beklenen davranıştır
- Tur kapanışı TEK yoldan yürür: `updateEmreCorrectStateAfterMatch` tur kapattıysa `loadNextMatch` çağrılmaz (çift puanlama regresyonuna dikkat — testi var)

---

## Ana Dosyalar

```
app/src/main/java/com/example/ranking/
  ranking/EmreSystemCorrect.kt      Emre usulü canlı motor (hibrit eşleştirme)
  ranking/PairwiseComparisonSort.kt İkili karşılaştırma (MERGE_SORT)
  ranking/RankingEngine.kt          Lig/eleme fikstür + sonuç hesapları
  ui/viewmodel/RankingViewModel.kt  Turnuva akışı state yönetimi (~1500 satır, bölünmesi backlog'da)
  ui/screens/RankingScreen.kt       Puanlama ekranı (alt bileşenler ui/screens/ranking/)
  ui/screens/NewTournamentScreen.kt 5 adımlı turnuva sihirbazı
  repository/RankingRepository.kt   Room erişim katmanı (withTransaction)
  utils/CsvReader.kt                RFC-4180 uyumlu CSV parser (hazır listeler bundan geçer)
  data/RankingDatabase.kt           Room DB v18, migration zinciri eksiksiz (fallback YOK)
  data/HazirListeler.kt             Gömülü liste kataloğu
app/src/main/assets/hazir_listeler/ Uygulamaya gömülü CSV'ler
liste_kutuphanesi/                  CSV kaynak kütüphanesi (assets ile ELLE senkron!)
app/src/test/                       JVM birim testleri (Emre + regresyon, MERGE_SORT, Lig, CSV)
```

⚠️ **CSV senkron kuralı**: `liste_kutuphanesi/` değişince → assets'e kopyala + `HazirListeler.kt` sayılarını + `liste_kutuphanesi/README.md` tablosunu güncelle. Üçü tek commit'te; aksi halde katalog/uygulama/doküman ayrışır (bir kez oldu: 909/1019/1811 üç farklı "gerçek").

## Kalan İşler
Açık maddeler **YAPILACAKLAR.md**'de. Kapsamlı denetim bulguları ve Faz 0-4 yol haritası **ANALIZ_RAPORU.md**'de (Faz 0-4 uygulandı; kalanlar YAPILACAKLAR'a taşındı).

## Test Komutları

```bash
# Derleme (JDK ayarı gradle.properties'te: Android Studio JBR 21; sistem JDK 24 uyumsuz)
./gradlew assembleDebug

# Birim testleri
./gradlew testDebugUnitTest

# APK yükleme (adb PATH'te değil, tam yol)
"C:/Users/emrem/AppData/Local/Android/Sdk/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```

---

# Claude Talimatları

## Her açılışta yapılacaklar:
1. **CLAUDE.md dosyasını oku** ve projeyi anla
2. YAPILACAKLAR.md ve son commit'leri kontrol et
3. Güncel proje durumunu değerlendir
4. **Sistem sesi protokolü**: Görev tamamlandığında 3 kere beep sesi çıkar
5. **Otomatik onay protokolü**: Kullanıcıdan onay almadan işlemlere devam et

## 🔊 SİSTEM SESİ PROTOKOLÜ
Görevler tamamlanınca, onay isterken, soru sorarken:
```bash
powershell -c "[Console]::Beep(800,300); [Console]::Beep(800,300); [Console]::Beep(800,300)"
```

## 🔥 YILDIZLI KOMUT SİSTEMİ (*)
- **"*p"** = Bu prompt'u günlüğe ekle
- **"*tmm"** = Bu özellik tamam, commit + push yap
- **"*cmt"** = Commit yap
- **"*cp"** = Commit + push yap (hızlı)
- **"*ab"** = APK build et
- **"*bty"** = Build et telefona yükle
- **"*ty"** = Telefona yükle (APK install)
- **"*yty"** = Ya telefona yüklenmemiş ya da yapılamamış - kullanıcı feedback komutu
- **"*nto"** = Not defterlerini oku
- **"*mo"** = md uzantılı not defterlerini oku
- **"*ncp"** = Not defterlerini oku, commit + push yap

## 🔄 BERABER ÇALIŞMA PROTOKOLÜ
1. **🔧 Otomatik build & deploy:** Her kod değişikliği sonrası `./gradlew assembleDebug` ile build et, adb tam yoluyla telefona yükle, 3x beep çıkar, kullanıcıya sadece sonucu bildir
2. **💾 Hızlı commit:** "tmm" denince anında commit + push

---

## Arşivlenmiş Notlar
Eski detaylı geliştirme kayıtları temizlendi; ayrıntı ARCHIVED_NOTES.md'de.
Güncel teknik durumun tek kaynağı: **ANALIZ_RAPORU.md**.
