# ŞARKI ZENGİNLEŞTİRME — oturum şartnamesi

| alan | değer |
|---|---|
| **AD** | ŞARKI ZENGİNLEŞTİRME |
| **MODEL** | (açan oturumun modeli) |
| **DİZİN** | `C:/Users/emrem/OneDrive/Belgeler/Projeler/Ranking` |
| **BRANCH** | `ileri-tusu-asagida-crash-fix` |
| **ClaudEmre** | HAYIR — bu bir işçi oturumu, `/claudemre-basla` çağırma |

---

## 🔴 EN BAŞTA OKU — BU İŞİN KIRMIZI ÇİZGİSİ

Kullanıcı başlangıçta **şarkı sözlerinin ilk dört dizesini ve nakaratını**
istedi. **BU YAPILAMAZ** ve senin işin değil:

```
🔴 ŞARKI SÖZÜ YAZMAK YASAK — tek dize bile
🔴 Nakarat yazmak YASAK
🔴 Sözden ALINTI yapmak YASAK — tırnak içinde bile
🔴 Sözü "kendi kelimelerinle yeniden yazmak" da YASAK
```

Şarkı sözleri telif hakkıyla korunuyor. Bu bir kapasite kısıtı değil,
uyulan bir kural — ve senin oturumun için de geçerli. Kullanıcı ısrar
ederse **bana bildir**, kendi başına yapma.

✅ **Yapman gereken, sözün YERİNE geçecek telifsiz bilgi üretmek.**
Şarkının NE HAKKINDA olduğunu *kendi betimleyici cümlenle* yazmak
serbesttir; şarkının KENDİ KELİMELERİNİ yazmak değildir.

Fark:
```
❌ "Vazgeçtim dünyadan, tek varlığım sensin"        ← ALINTI, YASAK
✅ Konu: "Dünyevi her şeyden vazgeçip tek bir kişiye  ← BETİMLEME, serbest
    bağlanma; teslimiyet ve adanmışlık"
```

---

## GÖREV

`liste_kutuphanesi/02_sebnem_ferah_sarkilari.csv` dosyasına **dört yeni
sütun** ekle. Dosya şu an 85 satır, sütunlar: `No,Albüm,Yıl,Şarkı,Tür`

Hedef başlık satırı:
```
No,Albüm,Yıl,Şarkı,Tür,Konu,Duygu Tonu,Süre,Söz-Müzik
```

| sütun | ne yazılır | örnek |
|---|---|---|
| **Konu** | Şarkının teması, KENDİ cümlenle, 4-10 kelime | `Ayrılık sonrası kendini yeniden toparlama` |
| **Duygu Tonu** | 1-3 kelimelik duygu etiketi | `Öfkeli-umutlu` · `Melankolik` · `İsyankâr` |
| **Süre** | dk:sn biçiminde | `4:32` |
| **Söz-Müzik** | söz ve müzik kime ait | `Şebnem Ferah` · `Şebnem Ferah / Ozan Doğulu` |

---

## 🔴 UYDURMA YASAĞI — bu işin en büyük riski

Bu görev seni uydurmaya **çok** teşvik eder: 85 şarkı var, hepsini
bilmiyorsun, ve makul görünen bir cümle yazmak kolay.

```
🔴 Bilmediğin şarkının konusunu TAHMİN ETME
🔴 Süreyi YUVARLAMA/UYDURMA — kaynaktan oku ya da "-" yaz
🔴 Besteciyi "muhtemelen kendisidir" diye YAZMA
```

Bulamadığın her hücreye **`-`** yaz. Uygulama `-` değerli hücreleri
karta zaten basmıyor — yani boş bırakmak zarar vermez, **uydurmak verir.**

Raporunda **kaç hücreyi dolduramadığını SAYIYLA** yaz. "Bulunamadı" bir
sonuçtur ve değerlidir; uydurulmuş bir cevap ise listeyi zehirler.

### Kaynak önceliği
1. Türkçe Wikipedia albüm sayfaları (şarkı süreleri genelde orada)
2. Discogs / MusicBrainz (süre + künye için güvenilir)
3. Sanatçının resmi kaynakları
4. Şarkı hakkında yazılmış **eleştiri/inceleme yazıları** (Konu sütunu için)

⚠️ Konu sütununu şarkı sözü sitelerinden **sözü okuyup** çıkarabilirsin —
ama çıktın **betimleme** olmalı, alıntı değil.

---

## ÇALIŞMA DÜZENİ

```
① Önce dosyayı oku, mevcut 85 satırı ANLA
② Albüm albüm ilerle — her albüm bitince dosyayı YAZ ve COMMIT ET
   (hepsini bitirip tek seferde yazma; yarıda kalırsa hepsi uçar)
③ Her commit'te YALNIZ kendi dosyalarını ver:
   git commit -F - -- liste_kutuphanesi/02_sebnem_ferah_sarkilari.csv \
                      app/src/main/assets/hazir_listeler/02_sebnem_ferah_sarkilari.csv
④ İlerlemeni oturumlar/SARKI-ZENGINLESTIRME-ILERLEME.md dosyasına yaz
```

### 🔴 SENİN DOSYALARIN — başkasına dokunma
```
✅ liste_kutuphanesi/02_sebnem_ferah_sarkilari.csv
✅ app/src/main/assets/hazir_listeler/02_sebnem_ferah_sarkilari.csv   (birebir aynı kopya)
✅ liste_kutuphanesi/README.md                (yalnız 2. satırın sütun listesi)
✅ oturumlar/SARKI-ZENGINLESTIRME-ILERLEME.md
❌ BAŞKA HİÇBİR DOSYA — Kotlin kaynakları koordinatörde
```

⚠️ Ben (koordinatör) şu an `HazirListeler.kt`, `ItemImage.kt`,
`TeamSelectionPanel.kt` üzerinde çalışıyorum. Onlara dokunma.

### CSV biçim kuralları
```
UTF-8, BOM yok · virgül ayraçlı · her satır 9 alan
Alan içinde virgül varsa çift tırnak: "Ayrılık, yalnızlık ve dönüş"
Türkçe karakterler bozulmayacak
İki dosya BİREBİR aynı olacak (diff -q ile doğrula)
```

---

## TESLİM

İş bitince bana (koordinatör) şunları bildir:
```
① kaç satırın kaç hücresi dolduruldu — SAYIYLA (ör. 85×4=340 hücrenin 297'si)
② hangi sütunda kaç "-" kaldı ve NİÇİN
③ şüphe duyduğun, doğrulayamadığın kayıtlar
④ diff -q çıktısı: iki CSV aynı mı
```

⚠️ **Aksaklık raporu beklemez** — takıldığın yerde işin bitmesini bekleme,
o anda bildir.
