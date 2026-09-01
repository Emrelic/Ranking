# CİHAZ TEST PROTOKOLÜ — Ranking Pro

> Hazırlayan: işçi oturum (doküman senkron kıtası) · Tarih: **2026-09-01**
> Kaynak: kod OKUNARAK yazıldı (koda dokunulmadı) + bugünkü commit mesajları.
> Kapsam: 7 seçilebilir yöntem + Dinle butonu + hazır liste içe aktarma +
> hücre düzenleme.

## Bu belge nasıl kullanılır

- Her satır `- [ ]` ile başlar: telefonda yaptıkça işaretle.
- **BEKLENEN** satırı, o adımdan sonra ekranda ne görmen gerektiğini söyler.
  Gördüğün şey BEKLENEN'den farklıysa adımı işaretleme, aşağıdaki
  **SAPMA** şablonunu doldur.
- ⚠️ işaretli maddeler **bilinen boşluklar**: sapma değil, henüz yapılmamış iş.
  Onaylamak için değil, yanlışlıkla "hata" diye bildirilmemesi için yazıldı.
- 🔴 işaretli maddeler bu turda ÖZELLİKLE sınanması istenenler (yeni düzeltme
  ya da geçmişte kırılmış davranış).

## 0. HAZIRLIK

- [ ] **0.1** Güncel APK kur:
      `./gradlew assembleDebug` → `adb install -r app/build/outputs/apk/debug/app-debug.apk`
      **BEKLENEN:** kurulum `Success`, uygulama açılıyor, ana menü geliyor.
- [ ] **0.2** Ana menüde 7 kart görünüyor: Yeni Turnuva · Listelerim · Kriter
      Listeleri · Devam Eden Turnuvalar · Arşiv · Hakkında · Hazır Listeler.
      **BEKLENEN:** kart altyazılarındaki sayılar gerçeği söylüyor (liste sayısı,
      aktif turnuva sayısı, arşiv sayısı). ⚠️ "YouTube Analizi" kartı GÖRÜNMEMELİ
      (bilerek gizli).
- [ ] **0.3** Test listeleri hazırla (protokolün geri kalanı bunlara dayanır):
      - **L-KÜÇÜK**: elle 5 öğelik liste (A, B, C, D, E) — hızlı tam turnuva için.
      - **L-TEK**: elle 7 öğelik liste — bye / tek sayı davranışı için.
      - **L-SAYI**: Hazır Listeler → **Sayılar 1-100** — *doğruluğu nesnel
        ölçmek için*: her maçta BÜYÜK sayıyı seç, sonuç 100'den 1'e sıralı çıkmalı.
      - **L-MÜZİK**: Hazır Listeler → **Şebnem Ferah Şarkıları** — Dinle butonu için.
      **BEKLENEN:** dördü de "Listelerim"de görünüyor, öğe sayıları doğru.

---

## 1. ÇEKİRDEK TUR (Ç1–Ç13) — her yöntemde aynı adımlar

Aşağıdaki adımlar her yöntem için TEKRARLANIR. Yöntem bölümlerinde yalnız
kutucuklar ve o yönteme özel farklar var; adımların tarifi burada.

**Ç1 — Turnuva açma (kritersiz).** Ana menü → Yeni Turnuva → 5 adımlı sihirbaz:
liste seç → turnuva adı → **sistem seç** → kriter (kapalı) → özet ve başlat.
> **BEKLENEN:** Sistem adımında **7 kart, 1'den 7'ye numaralı**, sırasıyla:
> 1 İkili Karşılaştırma · 2 Emre Sıralama Sistemi · 3 Hibrit İsviçre (Kanıt Turlu)
> · 4 Geliştirilmiş İsviçre · 5 İsviçre Sistemi · 6 Lig Sistemi · 7 Direkt Puanlama.
> İlk dördünde **ÖNERİLİ** rozeti var. Ekran ilk açıldığında **1. kart seçili**.
> Turnuva adı, liste ve sistem seçimine göre kendiliğinden dolmuş olmalı.

**Ç2 — Turnuva açma (KRİTERLİ).** Aynı sihirbaz, kriter adımında kriter
listesi seç ve "kriter panelini otomatik aç" seçeneğini işaretle.
> **BEKLENEN:** puanlama ekranında her yeni maç gelince kriter paneli
> kendiliğinden açılır ve verdiğin kriter puanları kaydedilir (dialogu
> kapatıp Sonuçlar'dan aynı maça bakınca sonuç işlenmiş olmalı).
> ⚠️ İkili Karşılaştırma'da kriter DESTEKLENMEZ: panel açılmamalı, KRİTER
> düğmesi hiç görünmemeli (dialoga verilecek "maç" yok).

**Ç3 — Sihirbazda ekran döndürme.** 3. adımdayken telefonu yatay/dikey çevir.
> **BEKLENEN:** adım numarası, seçili liste, seçili sistem ve turnuva adı
> KAYBOLMAZ. Sihirbaz 1. adıma dönerse bu bir sapmadır.

**Ç4 — Oylama: kazanan seçme.** Başlat → puanlama ekranı → üstteki ya da
alttaki takıma dokun.
> **BEKLENEN:** maç kaydedilir, sonraki maç gelir; sağdaki sayaç
> "**n. Tur / x/y**" biçiminde ilerler. Aynı maç bir daha sorulmaz.

**Ç5 — Buton çubuğu doğru mu?** Ekran ortasındaki çubuğa bak:
`BERABERLIK | VS | SKOR GİR | KRİTER` + tur/maç sayacı.
> **BEKLENEN (maç tabanlı yöntemler):** dördü de var.
> **BEKLENEN (İkili Karşılaştırma):** BERABERLIK **yok**, SKOR GİR **yok**,
> KRİTER **yok** — yalnız sarı VS ve sayaç. (Bilerek: ikili karşılaştırmada
> beraberlik sıralamayı sessizce bozuyor.)

**Ç6 — Beraberlik.** BERABERLIK'a bas.
> **BEKLENEN:** maç berabere kaydedilir (puan tablosunda iki tarafa da 0,5),
> sonraki maç gelir.

**Ç7 — Skorlu sonuç.** SKOR GİR → iki skor yaz → onayla.
> **BEKLENEN:** skor kaydedilir; kazanan, skoru büyük olan taraf olur; eşit skor
> beraberlik sayılır. Ligde bu skorlar **averaja** işler (2.6.c).

**Ç8 — Geri Al (tek adım).** VS (sarı) → menüden **↩ Son Maçı Geri Al**
(ya da varsa çubuktaki Geri Al düğmesi).
> **BEKLENEN:** son oylanan maç geri gelir, sayaç bir azalır, sonucu
> yeniden verilebilir.

**Ç9 — Geri Al (çok adımlı).** Arka arkaya 3 maç oyna, sonra 3 kez Geri Al.
> **BEKLENEN:** üçü de sırayla geri gelir, sayaç doğru geriler. Dördüncü
> basışta buton ya kaybolur ya da hiçbir şey bozulmaz.

**Ç10 — 🔴 Geri Al tur kapanınca KAYBOLMALI.** (Yalnız turlu sistemler: Emre
Sıralama · Hibrit · Geliştirilmiş İsviçre · İsviçre.) Bir turun SON maçını oyna.
> **BEKLENEN:** tur kapanır, yeni tur/eşleşmeler gelir ve **Geri Al seçeneği
> kaybolur**. Görünüp de basınca hiçbir şey olmuyorsa bu bir sapmadır (geçmişte
> tam bu hata vardı: yeni tur eski sonuçlara dayandığı için geri alma
> matematiksel olarak imkânsız).

**Ç11 — Sonuçlar dialogundan sonuç değiştirme.** VS menüsü → **Sonuçlar** →
listeden bir maç seç.
> **BEKLENEN (turlu sistemler):** yalnız **açık turun** tamamlanmış maçları
> değiştirilebilir; kapanmış turların maçları listede görünür ama düzenlenemez.
> **BEKLENEN (Lig):** bir maç ancak iki takımın da DAHA SONRAKİ tamamlanmış maçı
> yoksa değiştirilebilir. Değiştirdikten sonra puan durumu/sıralama anında
> güncellenmeli.

**Ç12 — 🔴 SIFIRLA gerçekten sıfırlıyor mu?** En az 3-4 maç oyna → VS menüsü
(ya da çubuktaki **Sıfırla**) → "Turnuvayı Sıfırla" onayı → **Sıfırla**.
> **BEKLENEN:** turnuva **baştan başlar** — sayaç 1. maça döner, oynanmış hiçbir
> sonuç kalmaz. **Sonra çıkıp tekrar gir** (ana menü → Devam Eden Turnuvalar ya
> da aynı turnuvayı yeniden aç): eski maçlar GERİ GELMEMELİ.
> *(Özellikle önemli: eskiden Sıfırla yalnız oturumu siliyor, maçları bırakıyordu;
> "oturum yok ama maç var → devam et" kurtarma yolu da turnuvayı diriltiyordu.
> İki ayrı doğru düzeltme birbirini etkisiz kılmıştı.)*

**Ç13 — 🔴 Uygulamayı ÖLDÜR ve devam et.** Birkaç maç oyna → uygulamayı son
kullanılanlardan tamamen kapat (ya da `adb shell am force-stop com.example.ranking`)
→ yeniden aç → **Devam Eden Turnuvalar → Devam Et**.
> **BEKLENEN:** **kaldığın MAÇA** dönersin (eşleşme listesine değil), oynanmış
> maçlar duruyor, sayaç doğru yerden devam ediyor. Turnuva sıfırlanıyorsa ya da
> seni "eşleşmeler" ekranında bırakıyorsa bu bir sapmadır.

**Ç14 — Bitiş → Sonuçlar → Arşiv.** Turnuvayı sonuna kadar oyna.
> **BEKLENEN:** sonuç ekranı gelir; Sonuçlar ekranında **3 sekme**: Final
> Sıralama · Puan Durumu · Maç Özeti. Üst çubukta yöntem adı doğru yazar.
> **Arşive Kaydet** → ad ver → "kaydedildi" onayı → ana menü → **Arşiv**
> kartında turnuva görünür ve açılınca sıralama okunur. Turnuva artık
> "Devam Eden Turnuvalar"da OLMAMALI.

---

## 2. YÖNTEM YÖNTEM

### 2.1 İkili Karşılaştırma (MERGE_SORT) — 🟢 en sağlam, referans yöntem

Liste: **L-SAYI (Sayılar 1-100)**. Her soruda **büyük sayıyı** seç.

- [ ] Ç1 turnuva açma
- [ ] Ç3 döndürme
- [ ] Ç4 oylama
- [ ] **Ç5 buton çubuğu — BERABERLIK / SKOR GİR / KRİTER GÖRÜNMEMELİ**
- [ ] Ç8 Geri Al tek adım
- [ ] Ç9 çok adımlı Geri Al
- [ ] Ç11 Sonuçlar dialogu
- [ ] Ç12 Sıfırla
- [ ] Ç13 öldür-devam et
- [ ] Ç14 bitiş + arşiv
- [ ] **2.1.a NESNEL DOĞRULUK:** turnuvayı bitir.
      **BEKLENEN:** final sıralama **100, 99, 98 … 2, 1** — tek bir sapma bile
      olmamalı. (Bu yöntemin tüm masaüstü ölçümleri sıfır hata veriyor; burada
      çıkan hata en ağır bulgudur.)
- [ ] **2.1.b** Soru sayısını not et.
      **BEKLENEN:** n·log₂n civarı (100 öğe için birkaç yüz soru; 5.000 gibi bir
      sayı sapmadır).
- [ ] ⚠️ Bu yöntemde "Puan Durumu" ve "BİTİR" **yok** — beklenen davranış.

### 2.2 Emre Sıralama Sistemi (EMRE_SIRALAMA) — kullanıcı icadı, en verimli turnuva

Liste: **L-SAYI** (nesnel doğruluk için) + bir tur da **L-KÜÇÜK**.

- [ ] Ç1
- [ ] Ç4 oylama
- [ ] Ç5 buton çubuğu (dördü de var)
- [ ] Ç6 beraberlik
- [ ] Ç7 skorlu
- [ ] Ç8 Geri Al
- [ ] Ç9 çok adımlı Geri Al
- [ ] **Ç10 tur kapanınca Geri Al kaybolmalı**
- [ ] Ç11 Sonuçlar dialogu
- [ ] **Ç12 Sıfırla**
- [ ] **Ç13 öldür-devam et**
- [ ] Ç14 bitiş + arşiv
- [ ] **2.2.a NESNEL DOĞRULUK (L-SAYI, hep büyük sayı):**
      **BEKLENEN:** final sıralama 100→1, sıfır hata. (Masaüstü ölçümü n=200'de
      18 tur / 1.365 maç, sıfır hata verdi; cihazda da hatasız olmalı.)
- [ ] **2.2.b Tur/maç sayısı makul mü:** 100 öğede kabaca 15-18 tur.
      **BEKLENEN:** tur sayacı her turda 1 artar, geriye gitmez, atlamaz.
- [ ] **2.2.c 🔴 BUGÜN EKLENDİ — BİTİR düğmesi (özellikle sınanacak).**
      Bu yöntemde erken bitirme bu sabah YOKTU; bugün eklendi, yani taze
      düzeltme — Sıfırla (Ç12) gibi muamele görsün. Oynanan tur sayısı
      ceil(log₂n)'i geçince çubukta **BİTİR** belirmeli (100 öğede 7. turdan
      sonra, 5 öğede 3.).
      **BEKLENEN:** basınca **SIRALAMA KESKİNLİĞİ: %..** yazan dialog çıkar
      ("komşu sıraların bu kadarı kanıtlı"). "Devam Et" → turnuva sürer.
      "Bitir" → oynanmamış maçlar silinir, o ana kadarki kanıtlarla sıralama
      kaydedilir, Sonuçlar ekranı gelir ve turnuva **"Devam Eden Turnuvalar"dan
      düşer**. Dialogda yüzde hiç görünmüyorsa ya da "Bitir" sonrası turnuva
      listede kalıyorsa sapma.
- [ ] **2.2.d 🔴 BUGÜN EKLENDİ — Puan Durumu (özellikle sınanacak).**
      Bu yöntemde puan tablosu da bugün eklendi. Çubuktaki **Puan** düğmesine bas.
      **BEKLENEN:** tablo DOLU gelir (boş çıkarsa sapma); oynanan/galibiyet/
      beraberlik sayıları oynanmış maçlarla tutar; **sıra motorun kendi
      sıralamasıdır** — yani en çok puan toplayan öğe illa 1. sırada olmayabilir,
      sıralamayı üstünlük kanıtları belirler. Bu beklenen davranıştır; puan
      burada yalnız bilgilendirme.
- [ ] **2.2.e** Bitirdikten sonra Puan Durumu ile Final Sıralama'yı karşılaştır.
      **BEKLENEN:** iki liste aynı sırayı gösterir.

### 2.3 Hibrit İsviçre — Kanıt Turlu (HIBRIT)

Liste: **L-SAYI** + **L-TEK** (tek sayı / bye davranışı).

- [ ] Ç1
- [ ] Ç4
- [ ] Ç5
- [ ] Ç6
- [ ] Ç7
- [ ] Ç8
- [ ] Ç9
- [ ] **Ç10**
- [ ] Ç11
- [ ] **Ç12 Sıfırla**
- [ ] **Ç13 öldür-devam et**
- [ ] Ç14
- [ ] **2.3.a NESNEL DOĞRULUK (L-SAYI):** **BEKLENEN:** 100→1, sıfır hata
      (bu sistemin iddiası "garantili tam sıralama").
- [ ] **2.3.b Kanıt turları:** ilk 4 tur İsviçre, sonrası kanıt turları.
      **BEKLENEN:** turlar ilerledikçe eşleşmeler sıralamada birbirine yakın
      öğeler arasında olur; tur sayacı düzgün artar.
- [ ] **2.3.c L-TEK (7 öğe) ile bye:** **BEKLENEN:** her turda bir öğe maç
      yapmaz; aynı öğe üst üste bye geçmemeli.
- [ ] **2.3.d 🔴 BUGÜN EKLENDİ — BİTİR düğmesi (özellikle sınanacak).**
      Hibritte erken bitirme bu sabah YOKTU; bugün eklendi — taze düzeltme,
      Sıfırla (Ç12) gibi muamele görsün. Tur sayısı ceil(log₂n)'i geçince
      çubukta **BİTİR** belirmeli.
      **BEKLENEN:** basınca **SIRALAMA KESKİNLİĞİ: %..** dialogu çıkar;
      "Bitir" → oynanmamış maçlar silinir, o ana kadarki kanıtlarla sıralama
      kaydedilir, turnuva "Devam Eden Turnuvalar"dan düşer. Yüzde görünmüyorsa
      ya da turnuva listede kalıyorsa sapma.
- [ ] **2.3.e 🔴 BUGÜN EKLENDİ — Puan Durumu (özellikle sınanacak).**
      Çubuktaki **Puan** düğmesi.
      **BEKLENEN:** tablo DOLU; galibiyet/beraberlik sayıları maçlarla tutuyor;
      **sıra motorun kendi sıralaması** (en çok puanlı illa 1. olmayabilir —
      beklenen davranış).
- [ ] **2.3.f** Kanıt turları ilerledikçe keskinlik yüzdesine bak (BİTİR
      dialogunu açıp kapatarak).
      **BEKLENEN:** yüzde tur ilerledikçe ARTAR, geriye gitmez; %100'e ulaşınca
      turnuva kendiliğinden biter.

### 2.4 Geliştirilmiş İsviçre — Emre Usulü (EMRE_CORRECT)

Liste: **L-KÜÇÜK (5)**, **L-TEK (7)** ve **L-SAYI**.

- [ ] Ç1
- [ ] Ç2 kriterli turnuva (bu yöntemde ÖZELLİKLE dene)
- [ ] Ç4
- [ ] Ç5
- [ ] Ç6
- [ ] Ç7
- [ ] Ç8
- [ ] Ç9
- [ ] **Ç10**
- [ ] Ç11
- [ ] **Ç12 Sıfırla**
- [ ] **Ç13 öldür-devam et**
- [ ] Ç14
- [ ] **2.4.a Puan Durumu:** çubuktaki **Puan** düğmesine bas.
      **BEKLENEN:** tablo dolu gelir; **bye geçen öğenin +1 puanı görünür**
      (yalnız maçlardan hesaplansaydı bye puanı eksik kalırdı); sıralama puana
      göre, eşitlikte algoritmanın kendi sırasına göre.
- [ ] **2.4.b BİTİR düğmesi:** oynanan tur sayısı ceil(log₂n)'i geçince çubukta
      **BİTİR** belirir (5 öğede 3. turdan sonra, 100 öğede 7.).
      **BEKLENEN:** basınca **keskinlik raporu** çıkar — "Oynanan tur / önerilen
      tur", "SIRALAMA KESKİNLİĞİ %..", üst/orta/alt yüzdeleri ve kaç komşuluğun
      kanıtlı olduğu. "Devam Et" dersen turnuva sürer, "Bitir" dersen sıralama
      kaydedilip biter.
- [ ] **2.4.c BİTİR sonrası tutarlılık:** Bitir dedikten sonra Sonuçlar ekranına
      ve Arşiv'e bak.
      **BEKLENEN:** yarım turun OYNANMIŞ maçları puanlara işlenmiş, oynanmamışlar
      silinmiş; bu turnuva artık "Devam Eden Turnuvalar"da YOK.
- [ ] **2.4.d ⚠️ TEK SAYIDA ERKEN BİTİŞ — SAPMA DEĞİL:** L-KÜÇÜK (5 öğe) ile
      oynadığında turnuva **2 turda** biter; 3 öğede **1 turda**.
      **BEKLENEN:** budur. Bye 1 puan, beraberlik 0,5 getirdiği için puanlar erken
      ayrışır ve "aynı puanlı eşleşme kalmadı" kuralı turnuvayı kapatır. Çift
      sayıda böyle bir kısalma olmaz (4 öğe → 3 tur).
- [ ] **2.4.e 🔴 Çift puanlama gerilemesi:** bir turun son maçını oyna, hemen
      Puan Durumu'na bak.
      **BEKLENEN:** puanlar bir kez işlenmiş olmalı — o turda 1 puan kazanan öğe
      tabloda **2 puan** görünüyorsa ağır sapmadır (bu hata bir kez yaşandı,
      regresyon testi var).
- [ ] **2.4.f Tekrar eşleşme yasağı:** turnuva boyunca aynı iki öğe iki kez
      karşına gelmemeli. **BEKLENEN:** hiç tekrar yok (sistemin en kırmızı kuralı).

### 2.5 İsviçre Sistemi (SWISS) — 🔴 bu turda dikkat

- [ ] **2.5.0 Sistem kartı görünüyor mu?** Sihirbazın sistem adımında
      **5. sırada "İsviçre Sistemi"** kartı var mı?
      **BEKLENEN:** VAR (2026-08-28'de yeni SwissSystem motoruyla geri açıldı).
      ⚠️ Not: **CLAUDE.md hâlâ "SWISS UI'dan çıkarıldı" diyor** — belge kodun
      gerisinde kalmış; koordinatöre bildirildi. Telefonda gördüğün doğrudur.
- [ ] Ç1
- [ ] Ç4
- [ ] Ç5
- [ ] Ç6
- [ ] Ç7
- [ ] Ç8
- [ ] Ç9
- [ ] **Ç10**
- [ ] Ç11
- [ ] **Ç12 Sıfırla**
- [ ] **Ç13 öldür-devam et** (bu yöntemde ÖZELLİKLE: eski kalıcılık yolu çalışmıyordu)
- [ ] Ç14
- [ ] **2.5.a 🔴 Tekrar eşleşme yasağı:** turnuva boyunca aynı iki öğe iki kez
      karşına gelmemeli. **BEKLENEN:** hiç tekrar yok (eski motorda vardı).
- [ ] **2.5.b 🔴 Bye adaleti (L-TEK, 7 öğe):** tam turnuva oyna, kimin kaç kez bye
      geçtiğini izle. **BEKLENEN:** bye sayıları arasında en çok 1 fark; herkes
      bir kez geçmeden kimse ikinci kez geçmemeli.
- [ ] **2.5.c 🔴 Kimse kaybolmuyor:** her turda maç sayısı tek takımda (n-1)/2 +
      1 bye olmalı. **BEKLENEN:** hiçbir öğe sessizce turdan düşmez (eski motorda
      düşüyordu).

### 2.6 Lig (LEAGUE)

Liste: **L-KÜÇÜK (5)** — tam lig 10 maç, hızlı biter.

- [ ] Ç1
- [ ] Ç4
- [ ] Ç5
- [ ] Ç6
- [ ] **Ç7 skorlu (bu yöntemde şart)**
- [ ] Ç8
- [ ] Ç9
- [ ] Ç11 Sonuçlar dialogu (Lig kuralıyla)
- [ ] **Ç12 Sıfırla**
- [ ] **Ç13 öldür-devam et**
- [ ] Ç14
- [ ] **2.6.a Fikstür doğruluğu:** **BEKLENEN:** herkes herkesle **tam bir kez**
      oynar (5 öğe → 10 maç), bir turda aynı takım iki maça çıkmaz.
- [ ] **2.6.b Puan Durumu:** çubuktaki **Puan** düğmesi.
      **BEKLENEN:** tablo DOLU (boş çıkarsa sapma — eskiden bu dal boştu),
      galibiyet **3**, beraberlik **1**, mağlubiyet 0; sıralama
      puan → averaj → atılan → galibiyet zinciriyle.
- [ ] **2.6.c Averaj:** skorlu maçlar gir (ör. biri 5-0, öbürü 1-0 kazansın).
      **BEKLENEN:** eşit puanda averajı yüksek olan üstte.
- [ ] **2.6.d Oylama sırası:** **BEKLENEN:** maçlar tur tur, tur içinde maç
      numarası sırasıyla sorulur; rastgele atlamaz.
- [ ] ⚠️ Lig ayarları ekranı (çift devre, puan ölçeği, skor kullanımı) **YOK** —
      bilinen açık madde, sapma değil.

### 2.7 Direkt Puanlama (DIRECT_SCORING)

- [ ] Ç1
- [ ] Ç3 döndürme
- [ ] **Ç12 Sıfırla**
- [ ] **Ç13 öldür-devam et**
- [ ] Ç14 bitiş + arşiv
- [ ] **2.7.a Puan girişi:** her öğeye tek tek puan ver.
      **BEKLENEN:** öğeler sırayla gelir, verilen puan kaydedilir, sonraki öğeye
      geçilir; ilerleme sayacı doğru artar.
- [ ] **2.7.b Yarıda bırak-dön:** yarısında çık, Devam Eden Turnuvalar'dan dön.
      **BEKLENEN:** verilen puanlar duruyor, kaldığın öğeden devam ediyorsun.
- [ ] **2.7.c Sonuç:** **BEKLENEN:** final sıralama puana göre büyükten küçüğe;
      eşit puanlılar kararlı (hep aynı) sırayla listelenir.
- [ ] ⚠️ Bu yöntemde maç yok: Geri Al, Beraberlik, Sonuçlar dialogu ve Puan
      Durumu **beklenmiyor**.

---

## 3. EK BÖLÜM — "▶ Dinle" butonu (müzik listeleri)

Liste: **L-MÜZİK (Şebnem Ferah Şarkıları)**, herhangi bir maç tabanlı yöntem.

- [ ] **3.1** Puanlama ekranında takım kartında **▶ Dinle** butonu görünüyor.
      **BEKLENEN:** yalnız müzik listelerinde çıkar (öğenin CSV verisinde
      şarkı/sanatçı/YouTube gibi bir anahtar varsa). Ülkeler listesinde ÇIKMAMALI.
- [ ] **3.2 İlk Dinle (YouTube Music tamamen kapalıyken):**
      **BEKLENEN:** doğru şarkı **baştan** çalar.
      ⚠️ Bu ilk seferde uygulamaya **otomatik dönüş çalışmayabilir** — Android'in
      arka plan aktivite engeli (logcat'te ölçüldü). Geri tuşuyla dönmek beklenen
      davranıştır, sapma değil.
- [ ] **3.3 İkinci Dinle (YTM açıkken):**
      **BEKLENEN:** eski şarkı susar, yenisi **baştan** çalar ve ekran ~2-3
      saniyede kendiliğinden Ranking'e döner.
- [ ] **3.4 Kimliksiz şarkı:** eski içe aktarmadan gelen (YouTube sütunu olmayan)
      bir liste ile dene.
      **BEKLENEN:** kimlik gömülü hazır listelerden tamamlanır ve şarkı yine çalar.
      Hiç çalmıyor / kuyruğa girip başlamıyorsa sapma.
- [ ] **3.5** Dinle'den dönünce aynı maçtasın, oy verilmemiş.
      **BEKLENEN:** maç kaybolmaz, sayaç oynamaz.

## 4. EK BÖLÜM — Hazır liste içe aktarma

- [ ] **4.1** Ana menü → **Hazır Listeler**: kategoriler ve kartlar geliyor.
      **BEKLENEN:** kart altyazısındaki öğe sayısı, içe aktardıktan sonra listede
      gördüğün gerçek öğe sayısıyla **birebir aynı**. Fark varsa bu bir sapmadır
      (katalog/CSV ayrışması — geçmişte yaşandı).
- [ ] **4.2** Görselli bir liste aktar (ör. **Ülkeler** ya da **Kuşlar**).
      **BEKLENEN:** puanlama ekranında öğe görselleri yükleniyor; kırık görsel
      ya da boş kutu yok.
- [ ] **4.3** Aynı listeyi **ikinci kez** aktar.
      **BEKLENEN:** ya "zaten var" der ya da ikinci bir kopya oluşturur — ama
      uygulama ÇÖKMEZ ve mevcut liste bozulmaz.
- [ ] **4.4** Aktarılan listeyi aç (Listelerim → liste): sütunlar kaymamış,
      Türkçe karakterler doğru, boş satır yok.
- [ ] **4.5** Büyük liste (Ülkeler, 195 öğe) aktarılırken ekran donuyor mu?
      **BEKLENEN:** birkaç saniyeyi geçmeyen kısa bekleme; "yanıt vermiyor"
      uyarısı çıkarsa sapma.

## 5. EK BÖLÜM — Hücre düzenleme (tablo)

- [ ] **5.1** Listelerim → bir liste → tablo görünümü → bir hücreye **uzun bas**.
      **BEKLENEN:** menü çıkar, içinde **✏ Düzenle** var.
- [ ] **5.2** Düzenle → değeri değiştir → kaydet.
      **BEKLENEN:** yeni değer hücrede görünür; ekrandan çıkıp girince **kalıcı**.
- [ ] **5.3 Çok satırlı kayıt:** şarkı sözü gibi uzun bir alana satır sonu
      ekleyerek kaydet. **BEKLENEN:** satır sonları korunur, metin kırpılmaz.
- [ ] **5.4 Öğe adı bozulmuyor:** düzenleme sonrası öğe adları "No" sütunuyla
      ezilmemiş olmalı (geçmişte olan hata).
- [ ] **5.5 ⚠️ Ekran döndürme:** kaydedilmemiş düzenlemeler ekran döndürmede
      kaybolur — bilinen açık madde, sapma değil.

---

## 6. SAPMA BİLDİRİM ŞABLONU

Bir adım BEKLENEN'den saparsa şunu doldur (koordinatör bunu doğrudan göreve
çevirebiliyor):

```
SAPMA
Adım      : (ör. 2.4.e)
Yöntem    : (ör. Geliştirilmiş İsviçre)
Liste     : (ör. L-KÜÇÜK, 5 öğe)
Yaptım    : (tek cümle, tıklama tıklama)
BEKLENEN  : (protokoldeki satır)
GÖRDÜĞÜM  : (ekranda ne oldu; sayı varsa sayıyla)
Tekrar mı : (her seferinde / bir kez oldu)
Ekran gör.: (varsa)
```

## 7. HIZLI GEÇİŞ ÖZETİ (protokolü bitirince doldur)

| Yöntem | Çekirdek tur | Nesnel doğruluk | Sıfırla | Öldür-devam | Sapma sayısı |
|---|---|---|---|---|---|
| İkili Karşılaştırma | | 100→1 ? | | | |
| Emre Sıralama | | 100→1 ? | | | |
| Hibrit İsviçre | | 100→1 ? | | | |
| Geliştirilmiş İsviçre | | — | | | |
| İsviçre Sistemi | | — | | | |
| Lig | | — | | | |
| Direkt Puanlama | | — | | | |

**Zaman kısıtlıysa önce bunlar — hepsi TAZE düzeltme ya da geçmişte kırılmış davranış:**

1. **Ç12 Sıfırla** ve **Ç13 öldür-devam et** — ikisi de veri kaybıyla sonuçlanmıştı.
2. **Ç10 tur kapanınca Geri Al'ın kaybolması.**
3. **2.2.c / 2.2.d ve 2.3.d / 2.3.e** — BİTİR düğmesi ve Puan Durumu, Emre Sıralama
   ile Hibrit'e **bugün eklendi**; bu protokolün ilk sürümünde daha yoklardı.
