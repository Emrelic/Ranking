# RAPOR — "▶ Dinle" butonu görevi (2026-08-29, işçi oturum)

**Commit:** `93a0f81` — Dinle butonu: kimliksiz şarkılar için kütüphaneden kimlik tamamlama
**Durum:** Çalışan sürüm telefona KURULU. 306 birim testi geçiyor.

## Kusurun kökü (ölçüldü, tahmin değil)

Kullanıcının veritabanındaki Şebnem Ferah listesi ESKİ bir içe aktarma:
csvData anahtarları `No, Sanatçı, Albüm, Şarkı Adı, Şarkı Sözleri` —
**YouTube sütunu YOK** (adb ile `databases/ranking_database` + WAL çekilip
arandı: 0 eşleşme). Kimlik yok → çalışan tek yol `watch?v=` atlanıyor →
akış `MEDIA_PLAY_FROM_SEARCH`'e düşüyor → o yol cihazda ölçüldü: şarkıyı
kuyruğa koyuyor ama ÇALDIRMIYOR, sonradan `keyevent 126` bile açmıyor.
"Listede hazır geliyor ama başlamıyor" davranışı tam buydu.

Ek ölçüm dersi: `dumpsys media_session`'daki `position` son setState
anlık görüntüsüdür — çalarken donuk görünür. Gerçek ses doğrulaması:
`dumpsys media.audio_flinger` çıktısında herhangi bir `Standby: no`
satırı + parça adı değişimi. (`watch?v=` yolu aslında canlı, soğuk,
duraklatılmış her oturum durumunda adb'den kusursuz çalıştı.)

## Yapılan

`MuzikDinle.kt`:
1. `kutuphaneKimligi()` — kimlik csvData'da yoksa gömülü hazır
   listelerden (assets, yalnız başlığında kimlik sütunu olan CSV'ler)
   sanatçı+ad eşleşmesiyle tamamlanır. Sözlük bir kez kurulur; aynı ada
   iki farklı kimlik düşerse ad-tek yedeğinden atılır.
2. Ranking'e dönüş denemesi 3 sn tek atıştan **3+7 sn iki denemeye**
   çıkarıldı.

## Cihazda doğrulanan (SM-G975F, gerçek sesle)

- İlk Dinle (YTM tamamen kapalıyken): doğru şarkı çalıyor ✓
- İkinci Dinle: eski şarkı susuyor, yenisi **baştan** (pos=0) çalıyor ✓,
  ekran ~2.5 sn'de Ranking'e dönüyor ✓ (şartname 4/5/6 tamam)

## Bilinen sınır (yarım iş DEĞİL, Android engeli)

YTM **soğuk açılışında** (ilk Dinle) Ranking'e otomatik dönüş çalışmıyor:
logcat'te ölçüldü — `Background activity start ...
callingUidHasAnyVisibleWindow: false` (BAL engeli), 3 ve 7 sn denemelerinin
ikisi de yutuldu. Şarkı yine çalıyor; kullanıcı bir kez geri tuşuyla döner.
Sonraki tüm Dinle'lerde sorun yok.

## Denenmiş, İŞE YARAMAZ (tekrar deneme)

- `MEDIA_PLAY_FROM_SEARCH` canlı oturum varken: hiçbir etki yok,
  ardından gelen oynat tuşu da açmıyor (bu oturumda yeniden ölçüldü).
- Donmuş görünen oturuma tek başına PLAY: no-op (YTM kendini zaten
  çalıyor sanıyor).

## Olası sonraki adım (ölçümü yarım kaldı)

`watch?v=` intent'ini YTM'nin **translucent deep link activity**'sine
vermek: `am start -n com.google.android.apps.youtube.music/.deeplink.MusicServiceDeepLinkActivity -a VIEW -d <url>`
adb'den şarkıyı değiştirip ÇALDIRDI (ölçüldü). Uygulama içinden NEW_TASK'sız
gönderilirse YTM'nin tam UI'sı hiç öne gelmeyebilir → "hiç ekrana gelmeden"
ideali. Henüz uygulama içinden doğrulanmadı; sınıf adı YTM sürümüyle
değişebilir, düşme zinciri şart.
