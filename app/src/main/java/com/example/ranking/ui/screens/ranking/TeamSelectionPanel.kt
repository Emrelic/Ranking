package com.example.ranking.ui.screens.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ranking.data.Song
import com.example.ranking.ranking.EmreSystemCorrect

// MatchBasedContent içindeki iki takım paneli (song1/song2) için ortak bileşen.
// Fark gösteren her şey parametre: takım, çerçeve rengi ve tıklama davranışı.
@Composable
internal fun TeamSelectionPanel(
    song: Song?,
    method: String,
    emreState: EmreSystemCorrect.EmreState?,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp) // Çerçeve için boşluk
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                RoundedCornerShape(12.dp)
            )
            .border(
                3.dp,
                borderColor,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
    ) {
        song?.let { team ->
            // PUAN ROZETİ - SAĞ ALT KÖŞE (TÜM USULLER)
            val currentPoints = when (method) {
                "EMRE_CORRECT" -> {
                    if (emreState?.teams?.isNotEmpty() == true) {
                        emreState.teams.find { it.song.id == team.id }?.points ?: 0.0
                    } else {
                        0.0
                    }
                }
                else -> 0.0 // Diğer usuller için genişletilebilir
            }

            if (currentPoints > 0.0 || method == "EMRE_CORRECT") {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 8.dp, y = 8.dp)  // Offset - merkezini köşeye yerleştir
                        .size(32.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (currentPoints % 1.0 == 0.0) "${currentPoints.toInt()}" else "${currentPoints}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            // CSV data'yı önceden hesapla
            val csvData = team.csvData
            val jsonData = remember(csvData) {
                if (csvData != null && csvData.isNotEmpty()) {
                    try {
                        val data = org.json.JSONObject(csvData)
                        val keys = data.keys().asSequence().toList().filter { it !in HIDDEN_CSV_KEYS }
                        keys.map { key -> key to data.optString(key, "") }
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
            }
            val imageUrl = remember(csvData) { extractImageUrl(csvData) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (imageUrl != null) {
                    item {
                        ItemImage(
                            imageUrl = imageUrl,
                            contentDescription = team.name,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                if (jsonData != null) {
                    items(jsonData) { (key, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                } else {
                    item {
                        Column {
                            Text(
                                text = team.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            team.artist?.let { artist ->
                                Text(
                                    text = artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
