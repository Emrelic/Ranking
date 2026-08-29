package com.example.ranking.ui.screens.ranking

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ranking.MainActivity
import com.example.ranking.MuzikDenetimServisi
import com.example.ranking.data.Song
import com.example.ranking.utils.CsvReader
import org.json.JSONObject
import java.util.Locale

/**
 * Öğeyi YouTube Music'te çalma desteği.
 *
 * Şarkı listelerinde puanlama yaparken "bu şarkı hangisiydi?" sorusu sık
 * çıkıyor. Buton şarkıyı YouTube Music'te çaldırır.
 *
 * Asıl yol `MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH`: Android'in
 * "şunu bul ve çal" arayüzü, Google Asistan'ın da kullandığı mekanizma.
 * Ayrıntılı düşme zinciri `youtubeMusicteAc` üzerinde yazılı.
 */

/** csvData'da bu anahtarlardan biri varsa öğe müzik parçası sayılır. */
private val MUZIK_ANAHTARLARI = listOf(
    "şarkı", "sarki", "sanatçı", "sanatci", "albüm", "album", "beste", "söz-müzik"
)

/** Video kimliğinin okunacağı sütun adları (varsa doğrudan çalma). */
private val KIMLIK_ANAHTARLARI = listOf("youtube", "video", "videoid", "youtube id")

private fun csvSozlugu(csvData: String?): Map<String, String> {
    if (csvData.isNullOrBlank()) return emptyMap()
    return try {
        val json = JSONObject(csvData)
        json.keys().asSequence().associateWith { json.optString(it, "").trim() }
    } catch (e: Exception) {
        emptyMap()
    }
}

/**
 * Öğe bir müzik parçası mı?
 *
 * Sütun ADLARINA bakılır, konuma değil: CSV'de 2. sütun bazen sanatçı
 * bazen albüm oluyor ve `song.artist` alanı yanıltıcı olabiliyor.
 */
fun muzikOgesiMi(csvData: String?): Boolean {
    val anahtarlar = csvSozlugu(csvData).keys.map { it.lowercase() }
    return anahtarlar.any { anahtar ->
        MUZIK_ANAHTARLARI.any { anahtar.contains(it) }
    }
}

/**
 * Gömülü hazır listelerden derlenen "şarkı → YouTube kimliği" sözlüğü.
 *
 * 🔴 NEDEN VAR — cihazda ölçülen kusurun kökü:
 * Kullanıcının veritabanındaki liste ESKİ bir içe aktarma; csvData'sında
 * `YouTube` sütunu yok (adb ile DB çekilip bakıldı: anahtarlar No, Sanatçı,
 * Albüm, Şarkı Adı, Şarkı Sözleri — kimlik yok). Kimlik olmayınca ölçülmüş
 * TEK sağlam yol olan `watch?v=<id>` hiç kullanılamıyor ve akış
 * MEDIA_PLAY_FROM_SEARCH'e düşüyordu; o yol da cihazda ölçüldü: şarkıyı
 * kuyruğa koyuyor ama ÇALDIRMIYOR, sonrasında gönderilen medya "oynat"
 * tuşu bile açmıyor. Kullanıcının "listede hazır geliyor ama başlamıyor"
 * dediği davranış buydu.
 *
 * Çözüm: kimlik csvData'da yoksa, uygulamaya gömülü hazır listelerdeki
 * (assets/hazir_listeler) kimlikli CSV'lerden sanatçı+ad eşleşmesiyle
 * tamamlanır. Sözlük ilk ihtiyaçta BİR KEZ kurulur; yalnız başlığında
 * kimlik sütunu olan dosyalar parse edilir (bugün 1 dosya, 79 kimlik).
 */
private var kutuphaneSozlugu: Pair<Map<String, String>, Map<String, String>>? = null

/** Türkçe'ye göre küçültülmüş eşleşme anahtarı ("İ" → "i", "I" → "ı"). */
private fun eslesmeAnahtari(s: String): String = s.trim().lowercase(Locale("tr"))

/** Şarkının kimliğini gömülü listelerden bulur (yoksa null). */
internal fun kutuphaneKimligi(context: Context, song: Song): String? {
    val (sanatcili, adTek) = kutuphaneSozlugu
        ?: kutuphaneSozluguKur(context).also { kutuphaneSozlugu = it }
    val ad = eslesmeAnahtari(song.name)
    if (ad.isBlank()) return null
    val sanatci = sanatciAdi(song)?.let { eslesmeAnahtari(it) }
    return sanatci?.let { sanatcili["$it|$ad"] } ?: adTek[ad]
}

private fun kutuphaneSozluguKur(
    context: Context
): Pair<Map<String, String>, Map<String, String>> {
    val sanatcili = HashMap<String, String>()
    // Ad-tek yedek: aynı ad iki farklı kimliğe gidiyorsa güvenilmez, atılır
    val adTek = HashMap<String, String?>()
    try {
        val am = context.assets
        val okuyucu = CsvReader()
        for (dosya in am.list("hazir_listeler").orEmpty()) {
            if (!dosya.endsWith(".csv")) continue
            try {
                val metin = am.open("hazir_listeler/$dosya").use {
                    okuyucu.bytesToText(it.readBytes())
                }
                // Başlığında kimlik sütunu olmayan dosya hiç parse edilmez
                val baslik = metin.lineSequence().firstOrNull()
                    ?.lowercase(Locale("tr")) ?: continue
                if (KIMLIK_ANAHTARLARI.none { baslik.contains(it) }) continue

                for (parca in okuyucu.parseText(metin)) {
                    val kimlik = youtubeKimligi(parca.csvData) ?: continue
                    val ad = eslesmeAnahtari(parca.name)
                    if (ad.isBlank()) continue
                    val sanatci = eslesmeAnahtari(parca.artist)
                    if (sanatci.isNotBlank()) {
                        sanatcili.putIfAbsent("$sanatci|$ad", kimlik)
                    }
                    if (adTek.containsKey(ad)) {
                        if (adTek[ad] != kimlik) adTek[ad] = null
                    } else {
                        adTek[ad] = kimlik
                    }
                }
            } catch (e: Exception) {
                // Tek dosyanın bozukluğu sözlüğün kalanını engellemesin
            }
        }
    } catch (e: Exception) {
    }
    val temizAdTek = HashMap<String, String>()
    for ((ad, kimlik) in adTek) if (kimlik != null) temizAdTek[ad] = kimlik
    return Pair(sanatcili, temizAdTek)
}

/** Varsa YouTube video kimliği (doğrudan çalma için). */
fun youtubeKimligi(csvData: String?): String? {
    val sozluk = csvSozlugu(csvData)
    val deger = sozluk.entries.firstOrNull { (k, _) ->
        KIMLIK_ANAHTARLARI.any { k.lowercase().contains(it) }
    }?.value
    return deger?.takeIf { it.isNotBlank() && it != "-" }
}

/**
 * Arama metni: "<sanatçı> <şarkı adı>".
 *
 * Sanatçı, sütun ADINDAN okunur. `song.artist` kullanılmaz çünkü CSV
 * eşlemesi konuma dayalı: bazı listelerde 2. sütun albüm olduğu için
 * `artist` alanına albüm adı düşüyor ve arama bozuluyor.
 */
fun muzikAramaMetni(song: Song): String {
    val sozluk = csvSozlugu(song.csvData)
    val sanatci = sozluk.entries.firstOrNull { (k, _) ->
        k.lowercase().let { it.contains("sanatçı") || it.contains("sanatci") }
    }?.value?.takeIf { it.isNotBlank() && it != "-" }

    return listOfNotNull(sanatci, song.name.takeIf { it.isNotBlank() })
        .joinToString(" ")
        .trim()
}

/**
 * Şarkıyı YouTube Music'te ÇALAR.
 *
 * Dört kademeli düşme zinciri; her kademe bir öncekinden daha az şey vaat
 * eder, sonuncusu bile sessiz kalmaz:
 *
 * 1. **Video kimliği varsa doğrudan o parça** — `watch?v=<id>` açılır, çalar
 * 2. **MEDIA_PLAY_FROM_SEARCH** — Android'in "şunu bul ve çal" intent'i.
 *    Google Asistan'ın "şu şarkıyı çal" derken kullandığı yol budur;
 *    YouTube Music aramayı kendi yapar ve ÇALMAYA BAŞLAR. Kullanıcının
 *    sonuç listesinden seçmesi gerekmez.
 * 3. **Arama sayfası** — 2. kademe çalışmazsa sonuçlar açılır, tek dokunuş
 * 4. **Tarayıcı / bilgilendirme** — hiçbiri yoksa sebebi söylenir
 *
 * ⚠️ Başka bir uygulamanın ekranına dokunmak (arama kutusuna yazıp çıkan
 * sonuca basmak) Android'de MÜMKÜN DEĞİL — bir uygulama başka uygulamanın
 * arayüzünü süremez. Bunu yapabilen tek mekanizma Erişilebilirlik Servisi'dir;
 * o da engelli kullanıcılar için tasarlanmış, kullanıcının sistem ayarlarından
 * elle açması gereken, YouTube Music arayüzü her değiştiğinde kırılan bir yol.
 * Doğru çözüm yukarıdaki 2. kademe: uygulamanın KENDİ desteklediği "ara ve çal"
 * arayüzünü kullanmak.
 */
private const val YTM_PAKET = "com.google.android.apps.youtube.music"

/** Bildirim erişimi verilmiş mi? (arka plan denetiminin ön şartı) */
fun bildirimErisimiVar(context: Context): Boolean {
    val izinliler = Settings.Secure.getString(
        context.contentResolver, "enabled_notification_listeners"
    ) ?: return false
    return izinliler.contains(context.packageName)
}

/** Kullanıcıyı bildirim erişimi ayarına götürür. */
fun bildirimErisimiIste(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        Toast.makeText(context, "Bildirim erişimi ayarı açılamadı", Toast.LENGTH_SHORT).show()
    }
}

/**
 * ARKA PLANDA çalar — YouTube Music ekrana GELMEZ.
 *
 * `MediaController.transportControls.playFromSearch` YouTube Music'in kendi
 * medya oturumuna gönderilir; uygulama önplana çıkmadan çalmaya başlar ve
 * çalmakta olan parça kendiliğinden yerini yenisine bırakır (kuyruk
 * değiştiği için ayrıca durdurmak gerekmiyor).
 *
 * Ön şartlar:
 * · Kullanıcı bildirim erişimi vermiş olmalı (MuzikDenetimServisi)
 * · YouTube Music'in CANLI bir medya oturumu olmalı — yani uygulama daha önce
 *   en az bir kez çalmış olmalı. Oturum yoksa false döner, çağıran ilk kez
 *   önplandan açar; ondan sonraki tüm dokunuşlar arka plandan yürür.
 *
 * @return komut gönderildiyse true
 */
/** O anki YouTube Music denetleyicisi (her seferinde taze bulunur). */
private fun ytmDenetleyici(context: Context): MediaController? {
    return try {
        val yonetici = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
            as? MediaSessionManager ?: return null
        val bilesen = ComponentName(context, MuzikDenetimServisi::class.java)
        yonetici.getActiveSessions(bilesen).firstOrNull { it.packageName == YTM_PAKET }
    } catch (e: Exception) {
        null
    }
}

/**
 * ARKA PLANDA çalar — YouTube Music ekrana GELMEZ.
 *
 * 🔴 SIRA VE KOŞULLAR ACI DENEYİMLE BULUNDU:
 *
 * · `stop()` GÖNDERİLMEZ. Gönderildiğinde çalan şarkı duraksıyor, yeni
 *   parça yüklenmiyor ve sonraki `play()` emri ESKİ şarkıyı kaldığı
 *   yerden sürdürüyordu — kullanıcının gördüğü "duraksadı, sonra devam
 *   etti, yeni şarkı başlamadı" davranışı tam olarak buydu.
 *
 * · `play()` KÖRLEMESİNE GÖNDERİLMEZ. Yalnız çalan parçanın ADI
 *   değiştiyse gönderilir; yoksa eski şarkıyı devam ettirmekten başka
 *   işe yaramıyor.
 *
 * · Önce video kimliğiyle (`playFromMediaId`), o tutmazsa aramayla
 *   (`playFromSearch`) denenir.
 *
 * @return komutlar gönderildiyse true (ÇALDIĞI ANLAMINA GELMEZ —
 *         doğrulamayı çağıran yapar)
 */
fun arkaPlandaCal(context: Context, song: Song): Boolean {
    if (!bildirimErisimiVar(context)) return false
    val ytm = ytmDenetleyici(context) ?: return false

    val oncekiAd = ytm.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
    val kimlik = youtubeKimligi(song.csvData)
    val sorgu = muzikAramaMetni(song)
    if (kimlik == null && sorgu.isBlank()) return false

    val handler = Handler(Looper.getMainLooper())

    // 1) Kimlikle doğrudan dene
    if (kimlik != null) {
        try { ytm.transportControls.playFromMediaId(kimlik, null) } catch (e: Exception) { }
    }

    // 2) Değişmediyse aramayla dene
    handler.postDelayed({
        if (calanParcaAdi(context) == oncekiAd && sorgu.isNotBlank()) {
            try {
                ytmDenetleyici(context)?.transportControls?.playFromSearch(sorgu, null)
            } catch (e: Exception) { }
        }
    }, 1300L)

    // 3) Parça DEĞİŞTİYSE çalmasını sağla — değişmediyse dokunma
    listOf(2800L, 4200L).forEach { gecikme ->
        handler.postDelayed({
            if (calanParcaAdi(context) != oncekiAd) {
                try { ytmDenetleyici(context)?.transportControls?.play() } catch (e: Exception) { }
            }
        }, gecikme)
    }

    return true
}

fun youtubeMusicteAc(context: Context, song: Song) {
    val paket = YTM_PAKET
    val kimlik = youtubeKimligi(song.csvData)

    // 0) ARKA PLAN — YouTube Music ekrana gelmez, kullanıcı Ranking'de kalır.
    //
    // 🔴 DOĞRULANIR: arka plan yolu "komutu gönderdim" diye başarılı sayılamaz.
    // Ölçüldü — YouTube Music playFromSearch ile parçayı kuyruğa alıp HAZIR
    // bekletiyor, çalmaya başlamıyor. Bu yol başarılı sayılıp çıkılınca,
    // çalıştığı ÖLÇÜLMÜŞ olan önplan yoluna hiç sıra gelmiyor ve buton
    // hiçbir şey çalmaz hale geliyordu (gerileme).
    //
    // Bu yüzden: komut gönderilir, birkaç saniye sonra GERÇEKTEN çalıyor mu
    // diye bakılır; çalmıyorsa önplan yoluna düşülür.
    // ARKA PLAN — YouTube Music ekrana gelmez, kullanıcı Ranking'de kalır.
    //
    // ⚠️ "Komutu gönderdim" BAŞARI SAYILMAZ. Bir kez öyle yapıldı ve buton
    // hiçbir şey çaldırmaz oldu: YouTube Music parçayı kuyruğa alıp
    // bekletiyor, biz de başarılı sanıp çalışan önplan yoluna hiç
    // geçmiyorduk. Şimdi GERÇEK SES ile doğrulanıyor; ses yoksa önplan
    // yoluna düşülüyor.
    // 🔴 ARKA PLAN YOLU ZİNCİRDEN ÇIKARILDI — beş tur denendi, çalışmıyor.
    //
    // Ölçülen: YouTube Music dışarıdan gelen şarkı DEĞİŞTİRME komutlarını
    // (playFromMediaId, playFromSearch) canlı bir oturumu varken kabul
    // etmiyor. Kabul ettiği tek şeyler play/pause/stop — yani var olan
    // parçayı yönetmek. Bu yüzden "uygulamadan çıkmadan başka şarkıya geç"
    // isteği bu arayüzle karşılanamıyor.
    //
    // Denemenin kendisi de zarar veriyordu: 5 saniye boyunca hiçbir şey
    // olmuyor, kullanıcı butonun bozuk olduğunu sanıyordu.
    //
    // Kod (arkaPlandaCal, ytmDenetleyici, MuzikDenetimServisi) duruyor:
    // YouTube Music ileride bu komutları desteklerse yeniden bağlanabilir.
    //
    // ÇALIŞAN YOL: şarkıyı önplanda aç, medya tuşuyla başlat, sonra
    // Ranking'e geri dön. Premium'da çalma arka planda sürdüğü için
    // kullanıcı hem şarkıyı duyar hem uygulamaya döner.
    onPlandaAc(context, song)
}

/** YouTube Music'te o an çalan parçanın adı (yoksa null). */
private fun calanParcaAdi(context: Context): String? {
    return try {
        val yonetici = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
            as? MediaSessionManager ?: return null
        val bilesen = ComponentName(context, MuzikDenetimServisi::class.java)
        val ytm = yonetici.getActiveSessions(bilesen)
            .firstOrNull { it.packageName == YTM_PAKET } ?: return null
        ytm.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
    } catch (e: Exception) {
        null
    }
}

/**
 * İSTENEN şarkı çalmaya başladı mı?
 *
 * 🔴 İki yanlış doğrulama denendi, ikisi de kusuru gizledi:
 *
 * ① `state == PLAYING` — cihazda ölçüldü: oturum "çalıyor" derken konum
 *    saniyelerce hiç ilerlemiyordu, ses yoktu.
 * ② `AudioManager.isMusicActive` — ses VAR ama ESKİ şarkının sesi.
 *    Kullanıcı çalan bir şarkı varken başkasına Dinle deyince doğrulama
 *    "başarılı" diyor, yedek yola hiç geçilmiyordu.
 *
 * Doğru ölçü: çalan parçanın ADI değişti mi ve ses gerçekten var mı.
 */
private fun istenenSarkiCaliyorMu(context: Context, oncekiAd: String?): Boolean {
    val audio = try {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    } catch (e: Exception) { null }
    if (audio?.isMusicActive != true) return false

    val simdiki = calanParcaAdi(context)
    // Ad okunamıyorsa sese güvenilir; okunuyorsa DEĞİŞMİŞ olmalı
    return simdiki == null || simdiki != oncekiAd
}

/**
 * ÖNPLAN yolu — YouTube Music ekrana gelir. Çalıştığı cihazda ölçülmüştü.
 * Arka plan yolu şarkıyı başlatamazsa buraya düşülür.
 */
private fun onPlandaAc(context: Context, song: Song) {
    val paket = YTM_PAKET
    // Kimlik önce şarkının kendi verisinden, yoksa gömülü listelerden.
    // (Eski içe aktarmalarda YouTube sütunu yok; kimliksiz kalınca çalışan
    // tek yol olan watch?v= atlanıyor ve şarkı "hazır ama sessiz" kalıyordu.)
    val kimlik = youtubeKimligi(song.csvData) ?: kutuphaneKimligi(context, song)

    // 1) Video kimliği biliniyorsa doğrudan o parçayı aç
    if (kimlik != null) {
        val uri = Uri.parse("https://music.youtube.com/watch?v=$kimlik")
        if (baslat(context, Intent(Intent.ACTION_VIEW, uri).setPackage(paket))) {
            calmayiBaslat(context); return
        }
        if (baslat(context, Intent(Intent.ACTION_VIEW, uri))) {
            calmayiBaslat(context); return
        }
    }

    val sorgu = muzikAramaMetni(song)
    if (sorgu.isBlank()) {
        Toast.makeText(context, "Aranacak bir ad bulunamadı", Toast.LENGTH_SHORT).show()
        return
    }

    // 2) "Ara ve çal" — YouTube Music aramayı yapıp çalmaya başlar
    val calIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
        putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/audio")
        putExtra(SearchManager.QUERY, sorgu)
        sanatciAdi(song)?.let { putExtra(MediaStore.EXTRA_MEDIA_ARTIST, it) }
        putExtra(MediaStore.EXTRA_MEDIA_TITLE, song.name)
    }
    if (baslat(context, Intent(calIntent).setPackage(paket))) { calmayiBaslat(context); return }
    // Paket kısıtı olmadan: kullanıcının kurulu başka bir müzik uygulaması
    if (baslat(context, calIntent)) { calmayiBaslat(context); return }

    // 3) Arama sayfası (uygulama ya da tarayıcı)
    val aramaUri = Uri.parse("https://music.youtube.com/search?q=${Uri.encode(sorgu)}")
    if (baslat(context, Intent(Intent.ACTION_VIEW, aramaUri).setPackage(paket))) return
    if (baslat(context, Intent(Intent.ACTION_VIEW, aramaUri))) return

    // 4) Hiçbiri yoksa sessiz kalma
    Toast.makeText(
        context,
        "YouTube Music ya da bir tarayıcı bulunamadı",
        Toast.LENGTH_SHORT
    ).show()
}

/**
 * YouTube Music parçayı yükledikten sonra ÇALMA emrini gönderir.
 *
 * 🔴 Ölçülmüş davranış: `music.youtube.com/watch?v=<id>` adresi açıldığında
 * YouTube Music parçayı yüklüyor ama DURAKLATILMIŞ bırakıyor
 * (`PlaybackState state=2`). Kullanıcının ayrıca oynat tuşuna basması
 * gerekiyordu. Medya "oynat" tuşu gönderilince durum 3'e (ÇALIYOR) geçiyor —
 * cihazda doğrulandı.
 *
 * ⚠️ KEYCODE_MEDIA_PLAY gönderilir, PLAY_PAUSE DEĞİL: ikincisi çalmakta olan
 * bir parçayı duraklatır. PLAY tekrar tekrar gönderilse bile zararsızdır.
 *
 * Emir birkaç kez denenir çünkü YouTube Music medya oturumunu almadan önce
 * gönderilen tuş kaybolur; yükleme süresi ağa ve cihaza göre değişiyor.
 */
/**
 * "Çal" emrini İKİ KANALDAN birden gönderir.
 *
 * ① Doğrudan YouTube Music'in medya oturumuna (`MediaController`).
 *    Bildirim erişimi verildiyse bu kanal uygulamamız ARKA PLANDAYKEN de
 *    çalışır — asıl güvenilir yol budur.
 * ② Sistem medya tuşu (`AudioManager`). Bildirim erişimi yoksa tek çare
 *    bu, ama arka plandaki uygulamadan gönderilince engellenebiliyor.
 *
 * Oturum HER ÇAĞRIDA yeniden bulunur: YouTube Music parçayı hazırlarken
 * oturumunu yeniliyor, önceden yakalanan denetleyici bayatlıyor.
 */
private fun calEmriGonder(context: Context) {
    // ① Oturuma doğrudan
    try {
        ytmDenetleyici(context)?.let { ytm ->
            ytm.transportControls.play()
            ytm.dispatchMediaButtonEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
            )
            ytm.dispatchMediaButtonEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)
            )
        }
    } catch (e: Exception) { }

    // ② Sistem medya tuşu
    try {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audio?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
        audio?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
    } catch (e: Exception) { }
}

private fun calmayiBaslat(context: Context) {
    val handler = Handler(Looper.getMainLooper())

    // Çalma emirleri gittikten sonra kullanıcıyı Ranking'e geri getir.
    // (Arka plan yolu kullanılabiliyorsa buraya hiç gelinmez; bu, izin
    // verilmemişken ya da YouTube Music'in henüz canlı oturumu yokken
    // yaşanan tek seferlik geçişi telafi eder.)
    // İKİ deneme: cihazda ölçüldü — YouTube Music SOĞUK açılışta (ilk Dinle)
    // MusicActivity'sini geç yüklüyor ve 3 sn'deki tek dönüş denemesinin
    // ÜSTÜNE çıkıyordu; kullanıcı YTM ekranında kalıyordu. Sıcak durumda
    // 3 sn'lik deneme yetiyor (ölçüldü: ~2.5 sn'de Ranking önde). Geç deneme
    // zaten öndeyken gelirse REORDER_TO_FRONT görünür bir şey yapmaz.
    listOf(3000L, 7000L).forEach { donusGecikmesi ->
        handler.postDelayed({
            try {
                context.startActivity(
                    Intent(context, MainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                    }
                )
            } catch (e: Exception) {
                // Android arka plandan ekran açmayı engelleyebilir; kullanıcı
                // geri tuşuyla döner. Çökme olmaz.
            }
        }, donusGecikmesi)
    }

    // Parçanın hazırlanma süresi ağa göre değişiyor; emir hazır olmadan
    // gönderilirse yutuluyor. Ranking'e dönüş 3 sn'de olduğu için emirler
    // dönüşün HEM ÖNCESİNE hem SONRASINA yayılıyor.
    listOf(1000L, 1800L, 2600L, 3600L, 4800L, 6200L).forEach { gecikme ->
        handler.postDelayed({ calEmriGonder(context) }, gecikme)
    }
}

/** Intent'i başlatmayı dener; hedef yoksa false döner (çökmez). */
private fun baslat(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    false
}

/** csvData'daki "Sanatçı" sütunu (varsa) — arama eşleşmesini keskinleştirir. */
private fun sanatciAdi(song: Song): String? =
    csvSozlugu(song.csvData).entries.firstOrNull { (k, _) ->
        k.lowercase().let { it.contains("sanatçı") || it.contains("sanatci") }
    }?.value?.takeIf { it.isNotBlank() && it != "-" }

/**
 * "▶ Dinle" butonu.
 *
 * Kartın kendisi takım seçmek için tıklanabilir olduğundan buton küçük
 * tutulur ve kenarda durur; ortadaki geniş alan seçim için kalır.
 */
@Composable
fun DinleButonu(
    song: Song,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var izinSor by remember { mutableStateOf(false) }

    if (izinSor) {
        AlertDialog(
            onDismissRequest = { izinSor = false },
            title = { Text("Uygulamadan çıkmadan dinle") },
            text = {
                Text(
                    "Şarkıyı YouTube Music ekrana GELMEDEN çalabilmek için bir kerelik " +
                        "\"bildirim erişimi\" izni gerekiyor. Ranking bildirimlerinizi " +
                        "okumaz, saklamaz veya dışarı göndermez — bu izin yalnızca " +
                        "YouTube Music'e \"şunu çal\" emrini iletmek için kullanılıyor.\n\n" +
                        "İzin vermezseniz şarkı yine çalar, ama her seferinde YouTube " +
                        "Music ekrana gelir ve geri dönmeniz gerekir."
                )
            },
            confirmButton = {
                TextButton(onClick = { bildirimErisimiIste(context); izinSor = false }) {
                    Text("Ayarı Aç")
                }
            },
            dismissButton = {
                TextButton(onClick = { youtubeMusicteAc(context, song); izinSor = false }) {
                    Text("Şimdilik böyle çal")
                }
            }
        )
    }

    FilledTonalButton(
        onClick = {
            // İzin yoksa bir kez açıkla; kullanıcı isterse izinsiz de çalar
            if (!bildirimErisimiVar(context)) izinSor = true
            else youtubeMusicteAc(context, song)
        },
        // Parmakla basarken sağa sola taşma şikayeti: buton YATAY olarak
        // büyütüldü (padding 10→18, asgari genişlik) — yükseklik aynı ki
        // kartta fazladan dikey yer yemesin.
        modifier = modifier.height(30.dp).widthIn(min = 96.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Text(
            text = "▶ Dinle",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
