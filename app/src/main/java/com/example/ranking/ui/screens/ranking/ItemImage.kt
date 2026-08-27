package com.example.ranking.ui.screens.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import coil.compose.AsyncImage
import org.json.JSONObject

/** csvData JSON'unda görsel adresini tutan sütun adı. */
const val IMAGE_COLUMN = "Görsel"

/** Öğe kartlarında satır olarak gösterilmemesi gereken teknik anahtarlar. */
val HIDDEN_CSV_KEYS = setOf("name", "_displayMode", IMAGE_COLUMN)

/**
 * Kart başlığında zaten görünen ya da bilgi taşımayan sütunlar.
 * "No" listedeki sıra numarasıdır; karşılaştırmada anlam taşımaz ve
 * kartın en değerli satırını işgal eder.
 */
private val DEGERSIZ_SUTUNLAR = setOf("no", "sıra", "sira", "#")

/**
 * csvData'dan kart detay satırlarını çıkarır.
 * Elenenler: teknik anahtarlar, sıra numarası, kartın başlığıyla aynı
 * değeri taşıyan sütun (öğe adı iki kez yazılmasın) ve boş/"-" değerler.
 */
fun cardDetailRows(csvData: String?, itemName: String): List<Pair<String, String>> {
    if (csvData.isNullOrBlank()) return emptyList()
    return try {
        val data = JSONObject(csvData)
        data.keys().asSequence()
            .filter { it !in HIDDEN_CSV_KEYS }
            .filter { it.trim().lowercase() !in DEGERSIZ_SUTUNLAR }
            .map { key -> key to data.optString(key, "").trim() }
            .filter { (_, value) -> value.isNotBlank() && value != "-" }
            .filterNot { (_, value) -> value.equals(itemName.trim(), ignoreCase = true) }
            .toList()
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * csvData JSON'undan görsel adresini çıkarır.
 * Adres yoksa, boşsa veya "-" ise null döner (görsel gösterilmez).
 */
fun extractImageUrl(csvData: String?): String? {
    if (csvData.isNullOrBlank()) return null
    return try {
        val url = JSONObject(csvData).optString(IMAGE_COLUMN, "").trim()
        if (url.isBlank() || url == "-" || !url.startsWith("http")) null else url
    } catch (e: Exception) {
        null
    }
}

/**
 * Öğe görseli. Adres yoksa hiçbir şey çizilmez (mevcut düzen bozulmaz).
 * Görsel yüklenene kadar yumuşak bir zemin görünür.
 */
@Composable
fun ItemImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp
) {
    if (imageUrl == null) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
