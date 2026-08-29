package com.example.ranking.ui.screens.ranking

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
 * Öğeyi YouTube Music'te açma desteği.
 *
 * Şarkı listelerinde puanlama yaparken "bu şarkı hangisiydi?" sorusu
 * sık çıkıyor. Buton, öğeyi YouTube Music'te aratıp uygulamayı açar.
 *
 * ⚠️ DOĞRUDAN ÇALMA YOK — bilinçli. Bir şarkıyı tek dokunuşla çalmak için
 * YouTube video kimliği gerekir; onu isim üzerinden güvenilir çözmenin tek
 * yolu YouTube Data API'dir ve bu projede API anahtarı yok. Uydurulmuş bir
 * kimlik yanlış şarkıyı açar, ki bu hiç açmamaktan kötüdür. Bu yüzden
 * arama sonuçları açılır: kullanıcı doğru kaydı görüp tek dokunuşla çalar.
 * Listeye "YouTube" sütunu (video kimliği) eklenirse doğrudan çalma
 * `youtubeKimligi` üzerinden kendiliğinden devreye girer.
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
 * YouTube Music'i açar. Kurulu değilse tarayıcıya düşer, o da yoksa
 * kullanıcıya sebebini söyler — sessizce hiçbir şey yapmaz duruma düşmez.
 */
fun youtubeMusicteAc(context: Context, song: Song) {
    val kimlik = youtubeKimligi(song.csvData)
    val adres = if (kimlik != null) {
        // Sütunda video kimliği varsa doğrudan o parça açılır
        "https://music.youtube.com/watch?v=$kimlik"
    } else {
        val sorgu = muzikAramaMetni(song)
        if (sorgu.isBlank()) {
            Toast.makeText(context, "Aranacak bir ad bulunamadı", Toast.LENGTH_SHORT).show()
            return
        }
        "https://music.youtube.com/search?q=${Uri.encode(sorgu)}"
    }

    val uri = Uri.parse(adres)

    // 1) YouTube Music uygulaması
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.youtube.music")
        )
        return
    } catch (e: ActivityNotFoundException) {
        // kurulu değil — sıradaki seçeneğe geç
    }

    // 2) Paket kısıtı olmadan (tarayıcı ya da kullanıcının seçtiği uygulama)
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "YouTube Music ya da bir tarayıcı bulunamadı",
            Toast.LENGTH_SHORT
        ).show()
    }
}

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
