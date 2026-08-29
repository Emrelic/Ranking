# GÖREV — "▶ Dinle" butonu: şarkıyı gerçekten çaldır

| alan | değer |
|---|---|
| **DİZİN** | `C:/Users/emrem/OneDrive/Belgeler/Projeler/Ranking` |
| **BRANCH** | `ileri-tusu-asagida-crash-fix` |
| **DOSYAN** | `app/src/main/java/com/example/ranking/ui/screens/ranking/MuzikDinle.kt` (+ gerekirse `AndroidManifest.xml`) |
| **CİHAZ** | Galaxy S10+ (SM-G975F), adb ile bağlı, YouTube Music **Premium** kurulu |
| **adb** | `C:/Users/emrem/AppData/Local/Android/Sdk/platform-tools/adb.exe` (PATH'te değil, tam yol) |

---

## İSTENEN İŞ AKIŞI (kullanıcının kendi sözleriyle)

1. Puanlama ekranında bir şarkı kartındaki **▶ Dinle**'ye basılır
2. O şarkı YouTube Music'te **çalmaya başlar**
3. Ekran **Ranking'e geri döner**, kullanıcı oylamaya devam eder
4. Aynı turda ya da sonraki eşleşmede **başka bir şarkının Dinle**'sine basılır
5. **Çalan şarkı susar**, yeni şarkı **baştan çalmaya başlar**
6. Yine Ranking'e dönülür

**İdeal:** YouTube Music hiç ekrana gelmeden, tamamı arka planda.
**Kabul edilebilir:** Kısa bir geçiş görünür ama 2/3/5/6 çalışır.

---

## 🔴 ŞU ANKİ KUSUR

Kullanıcı: *"Basınca ilgili şarkı listede çalmaya HAZIR olarak geliyor ama
başlamıyor. O aşamada ek bir çaldır komutu lazım."*

Yani şarkı yükleniyor/kuyruğa giriyor, ama oynatma tetiklenmiyor.

---

## ÖLÇÜLMÜŞ GERÇEKLER — bunları TEKRAR DENEME, zaman kaybı

Hepsi bu cihazda `adb shell dumpsys media_session` / `dumpsys audio` ile ölçüldü.

### ✅ ÇALIŞAN
- **`watch?v=<videoId>` intent'i şarkıyı DEĞİŞTİRİYOR.** Ölçüldü:
  çalan "Deli Kızım Uyan" iken B şarkısının adresi gönderildi →
  `description=Şebnem Ferah - Vazgeçtim Dünyadan (Kadın)`, `state=3`,
  `position=934` (baştan başladı). **Bu yol sağlam.**
- **Medya oynat tuşu duraklamış parçayı başlatıyor.** `input keyevent 126`
  ile `state=2` → `state=3` geçişi ölçüldü, konum ilerledi.
- Ranking'e geri dönüş (`FLAG_ACTIVITY_REORDER_TO_FRONT`) çalışıyor —
  kullanıcı doğruladı.
- 80 şarkının 79'unun YouTube video kimliği CSV'de: sütun adı **`YouTube`**,
  `liste_kutuphanesi/32_sebnem_ferah_sozleriyle.csv` ve assets kopyası.
  ("Üvey" şarkısında kimlik yok, `-` yazılı.)

### ❌ ÇALIŞMAYAN — denendi, olmadı
- **`MediaController.transportControls.playFromSearch`** — YouTube Music
  canlı oturumu varken şarkıyı DEĞİŞTİRMİYOR. Boştayken kuyruğa alıyor
  ama çalmıyor.
- **`playFromMediaId(videoId)`** — aynı, değiştirmiyor.
- **`stop()` + `playFromSearch`** — şarkı duraksıyor, yeni parça gelmiyor,
  sonraki `play()` ESKİ şarkıyı kaldığı yerden sürdürüyor. Kullanıcının
  gördüğü "duraksadı sonra devam etti" davranışı buydu.
- **`MEDIA_PLAY_FROM_SEARCH` intent'i** — YouTube Music açılıyor ama
  çalan şarkıyı değiştirmiyor.

### ⚠️ TUZAKLAR — bunlara düşme
1. **`state=PLAYING` ÇALIYOR DEMEK DEĞİL.** Oturum "çalıyor" derken konum
   saniyelerce hiç ilerlemedi (ses yoktu). Doğrulama için konumun
   İLERLEDİĞİNE ya da `AudioManager.isMusicActive`'e bak.
2. **`isMusicActive` de tek başına yetmez.** Çalan ESKİ şarkı olabilir.
   Doğrulama İSTENEN şarkının çaldığını ölçmeli: parça adı (metadata
   `METADATA_KEY_TITLE`) değişti mi?
3. **Körlemesine `play()` GÖNDERME.** Yeni parça yüklenmediyse tek yaptığı
   eski şarkıyı devam ettirmek — kusuru gizler.
4. **Telefon uyursa ölçüm bozulur.** `dumpsys power | grep mWakefulness`
   ile kontrol et; `Dozing` ise ölçüm geçersiz. Önce
   `input keyevent KEYCODE_WAKEUP`.
5. **Denetleyici bayatlıyor.** YouTube Music parçayı hazırlarken medya
   oturumunu yeniliyor. `getActiveSessions` her komutta YENİDEN çağrılmalı.
6. **İçe aktarılmış liste güncellenmiyor.** Kullanıcının veritabanındaki
   kopyada `YouTube` sütunu yoksa kimlik bulunamaz. Kontrol:
   `adb shell "run-as com.example.ranking cat databases/ranking_database"`
   çıktısında `YouTube` metni geçiyor mu.

---

## MEVCUT KOD

`MuzikDinle.kt` içinde:
- `youtubeMusicteAc(context, song)` — giriş noktası, `onPlandaAc`'ı çağırıyor
- `onPlandaAc(...)` — `watch?v=<id>` intent'i + `calmayiBaslat`
- `calmayiBaslat(...)` — 1.0/1.8/2.6/3.6/4.8/6.2 sn'de `calEmriGonder`,
  ayrıca 3 sn'de Ranking'e dönüş
- `calEmriGonder(...)` — iki kanal: oturuma doğrudan (`transportControls.play()`
  + `dispatchMediaButtonEvent`) ve sistem medya tuşu (`AudioManager`)
- `arkaPlandaCal`, `ytmDenetleyici` — arka plan altyapısı, ZİNCİRDE DEĞİL
- `MuzikDenetimServisi` (ayrı dosya) — bildirim erişimi için boş servis;
  **kullanıcı izni VERMİŞ durumda**

---

## SENDEN İSTENEN

1. **ÖNCE ÖLÇ.** Kullanıcı butona bastıktan sonra `dumpsys media_session`
   ve `dumpsys audio` çıktısını al. Şarkı yükleniyor mu, hangi durumda
   takılıyor? Tahminle kod yazma.
2. Takılmayı aş. Kullanıcının önerisi: *"ikinci bir çal komutu ile o
   tıkanıklık geçilebilir."* Makul — ama körlemesine değil, **parça
   değiştikten sonra** gönderilmeli.
3. Her denemeden sonra cihazda **gerçek ses üretimi** ile doğrula
   (`dumpsys audio | grep state:started` ve konumun ilerlemesi).
4. Çalışan sürümü commit et.

## ÇALIŞMA KURALLARI
- Derleme: `./gradlew assembleDebug` (JDK ayarı `gradle.properties`'te)
- Testler: `./gradlew testDebugUnitTest` — **306 test geçiyor, kırma**
- Yalnız kendi dosyalarına dokun
- Commit mesajını Türkçe yaz, NEDEN'i anlat
- Kullanıcıya soru sorarken: ne ölçtün (sayıyla), neyi bulamadın, ondan
  tam olarak ne istiyorsun
