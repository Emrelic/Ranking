# 🔴 FİLO KURALI — GRADLE SERT SIRASI (2026-09-01, koordinatör ranking-0c)

Aynı anda YALNIZ BİR oturum gradle koşar. Sebep ölçüldü: paralel koşumlar
yalnız yavaşlatmıyor, birbirinin build çıktısını BOZUYOR
(`Could not delete app\build\tmp\kotlin-classes\debug\com`, çoklu Kotlin
daemon çökmesi, 11-13 dk kuyruklar; OneDrive dosya tutması da çarpan).

## Kilit protokolü (mkdir atomiktir)

Gradle koşmadan ÖNCE:
```bash
cd C:/Users/emrem/OneDrive/Belgeler/Projeler/Ranking
until mkdir .gradle-kilit 2>/dev/null; do sleep 20; done
echo "<oturum-adin> $(date)" > .gradle-kilit/sahip.txt
```

Koşum bitince (BAŞARISIZ olsa da) HEMEN:
```bash
rm -rf .gradle-kilit
```

## Kurallar
- GERİYE DÖNÜK KİLİT ALINMAZ: kilitsiz başlamış bir koşum uçuştayken kilidi
  almak garantiyi vermez (8e tespiti).
- KURALI ÖĞRENEN, KİLİTSİZ UÇAN KOŞUMUNU İPTAL EDER — "bitsin bari" en
  pahalı seçenek (ölçüldü: 8e'nin koşumu 26 dk sonra "Could not delete"
  ile düştü; o 26 dakika çöpe gitti).
- sahip.txt'e TAHMİNİ SÜRE de yaz ("~15 dk") — bekleyen planlayabilsin.
- "tasklist ile java süreci kontrolü" maddesi BİLEREK YOK: Gradle daemon
  koşum bitince de saatlerce bellekte yaşar, kontrol sürekli yanlış
  pozitif verir. Tek güvenilir sinyal kilidin kendisidir; istisnasız uygula.
- Kilidi alan, gradle işini BİTİRİR BİTİRMEZ bırakır — kilit altında test
  yazmak/düşünmek YASAK; yalnız koşum süresi.
- Bekleyen, `sahip.txt`e bakabilir. Damga **30 dakikadan eskiyse** kilit
  ölü sayılır: kır (`rm -rf`), kırdığını sahibine ve koordinatöre bildir.
- Koşumları birleştir: tek `testDebugUnitTest --tests "*Senin*"` çağrısına
  sığdır, arka arkaya küçük koşumlar yapma.
- `.gradle-kilit` git'e GİRMEZ (zaten build klasörü gibi geçici).
- TEK SEFERLİK (2026-09-02 gecesi): koordinatör sıradaki kilit slotunda
  `./gradlew --stop` ile kural öncesi ZOMBİ daemon'ları temizleyecek —
  kilit yeni koşumları sıralıyor ama eski daemon'lar ortak app/build'e
  yazmaya devam ediyordu (6a ölçtü: kilit altındayken bile 81'in xml'i
  test-results'a düştü). Temizlikten sonra bu sorun kapanmış olmalı.
- OneDrive/build sorunu için kalıcı öneri YAPILACAKLAR'a yazıldı
  (build dizinini senkron dışına almak — kullanıcı kararı).

---

## Ek kural — YARIM DOSYA FİLOYU BLOKE EDER (2026-09-02, işçi 103)

Ölçülen olay: bir oturumun yarım kalmış `HazirListelerSenkronTest.kt` dosyası
(tip çıkarımı hatası) yüzünden `:app:compileDebugUnitTestKotlin` düştü ve
**11 dk 35 sn süren bir koşum boşa gitti**. Test derlemesi MODÜL GENELİDİR:
bir işçinin derlenmeyen tek dosyası, kilit kuyruğunda bekleyen HERKESİ vurur.

Kural:
- Derlenmeyeceğini bildiğin yarım dosyayı `app/src/test/` altında `.kt`
  uzantısıyla BIRAKMA. Ya tek seferde tam yaz, ya da bitene kadar
  `.kt.wip` uzantısında tut (Kotlin derleyicisi görmez), bitince `.kt` yap.
- Kilidi almadan önce kendi dosyanın derlendiğinden emin ol; kilit süresi
  ortak kaynaktır, derleme hatası ayıklamak için kullanılmaz.
- Derleme hatası SENİN dosyandan gelmiyorsa: kilidi bırak, sahibine haber ver,
  kuyruğu tıkama.
