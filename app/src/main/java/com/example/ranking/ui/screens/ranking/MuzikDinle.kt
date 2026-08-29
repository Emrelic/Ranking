package com.example.ranking.ui.screens.ranking

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ranking.data.Song
import org.json.JSONObject

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
fun youtubeMusicteAc(context: Context, song: Song) {
    val paket = "com.google.android.apps.youtube.music"
    val kimlik = youtubeKimligi(song.csvData)

    // 1) Video kimliği biliniyorsa doğrudan o parçayı aç
    if (kimlik != null) {
        val uri = Uri.parse("https://music.youtube.com/watch?v=$kimlik")
        if (baslat(context, Intent(Intent.ACTION_VIEW, uri).setPackage(paket))) return
        if (baslat(context, Intent(Intent.ACTION_VIEW, uri))) return
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
    if (baslat(context, Intent(calIntent).setPackage(paket))) return
    // Paket kısıtı olmadan: kullanıcının kurulu başka bir müzik uygulaması
    if (baslat(context, calIntent)) return

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
    FilledTonalButton(
        onClick = { youtubeMusicteAc(context, song) },
        modifier = modifier.height(30.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
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
