package com.example.ranking.ui.screens.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ui.viewmodel.RankingViewModel

@Composable
internal fun MatchingsListContent(
    uiState: RankingViewModel.RankingUiState,
    viewModel: RankingViewModel,
    method: String = "EMRE_CORRECT" // Method parametresi eklendi
) {
    var isAdvancedView by remember { mutableStateOf(true) } // Büyük kartlar varsayılan

    // Debug logging
    android.util.Log.d("RANKING_DEBUG", "MatchingsListContent render - matchingsList.size: ${uiState.matchingsList.size}")
    android.util.Log.d("RANKING_DEBUG", "MatchingsList içeriği: ${uiState.matchingsList.map { "${it.id}:${it.matchNumber}" }}")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 8.dp) // Ekran kenarlarına daha yakın
    ) {
        // Header
        Text(
            text = "Eşleştirmeler - ${uiState.currentRound}. Tur",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Toggle Butonları - Basit/Gelişmiş
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { isAdvancedView = false },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isAdvancedView) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Basit",
                    color = if (!isAdvancedView) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { isAdvancedView = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAdvancedView) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Komplike",
                    color = if (isAdvancedView) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Sayaç bilgisi
        Text(
            text = "${uiState.matchingsList.size} Eşleştirme",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Eşleştirmeler listesi
        if (uiState.matchingsList.isEmpty()) {
            Text(
                text = "Henüz eşleştirme yok - startScoring() çağırılmadı mı?",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Red,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), // Take remaining space, push button to bottom
                verticalArrangement = Arrangement.spacedBy(2.dp) // Kartlar arası spacing azaltıldı
            ) {
                items(uiState.matchingsList) { match ->
                    android.util.Log.d("RANKING_DEBUG", "Rendering match card: ${match.id}:${match.matchNumber}")
                    if (isAdvancedView) {
                        // Büyük görünüm - Büyük tablo kartları
                        AdvancedMatchCard(
                            match = match,
                            songs = uiState.allSongs,
                            method = method,
                            emreState = uiState.emreState,
                            onClick = {
                                // Kart tıklama artık puanlama ekranına geçmiyor
                                android.util.Log.d("RANKING_DEBUG", "AdvancedMatchCard clicked but no action")
                            }
                        )
                    } else {
                        // Küçük görünüm - Küçük kartlar
                        SimpleMatchCard(
                            match = match,
                            songs = uiState.allSongs,
                            onClick = {
                                // Kart tıklama artık puanlama ekranına geçmiyor
                                android.util.Log.d("RANKING_DEBUG", "SimpleMatchCard clicked but no action")
                            }
                        )
                    }
                }
            }

            // Puanlama Ekranına Geç butonu - LazyColumn dışında
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    try {
                        android.util.Log.d("RANKING_DEBUG", "Puanlama Ekranına Geç butonu tıklandı")
                        // İlk maçı seç ve puanlama ekranına geç
                        val firstMatch = uiState.matchingsList.firstOrNull()
                        if (firstMatch != null) {
                            android.util.Log.d("RANKING_DEBUG", "İlk maç seçiliyor: ${firstMatch.id}")
                            viewModel.selectMatch(firstMatch)
                        } else {
                            android.util.Log.e("RANKING_DEBUG", "Hiçbir maç bulunamadı!")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("RANKING_DEBUG", "Puanlama butonu CRASH: ${e.message}", e)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "▶ Puanlama Ekranına Geç",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun parseJsonToMap(jsonString: String): Map<String, String> {
    return try {
        // Basit JSON parsing - bracket'ları temizle ve key-value çiftlerini parse et
        val cleanJson = jsonString.trim().removePrefix("{").removeSuffix("}")
        val pairs = cleanJson.split(",")
        val map = mutableMapOf<String, String>()

        pairs.forEach { pair ->
            val keyValue = pair.split(":")
            if (keyValue.size == 2) {
                val key = keyValue[0].trim().removePrefix("\"").removeSuffix("\"")
                val value = keyValue[1].trim().removePrefix("\"").removeSuffix("\"")
                map[key] = value
            }
        }
        map
    } catch (e: Exception) {
        emptyMap()
    }
}

@Composable
private fun TableRow(
    label: String,
    value: String
) {
    // Resimdeki format: Etiket üstte, veri altında
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally // Center alignment
    ) {
        // Etiket üstte - koyu arka plan ile vurgulu
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color(0xFF0277BD).copy(alpha = 0.1f), // Açık mavi arka plan
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(vertical = 2.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF0277BD),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        // Veri altında - büyük ve siyah, center aligned
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
    }
}

@Composable
internal fun SimpleMatchCard(
    match: Match,
    songs: List<Song>,
    onClick: () -> Unit
) {
    val song1 = songs.find { it.id == match.songId1 }
    val song2 = songs.find { it.id == match.songId2 }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Maç numarası
            Text(
                text = "${match.matchNumber}. Eşleşme",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Basit VS Layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Takım 1
                Text(
                    text = song1?.name ?: "Takım 1",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                // VS
                Text(
                    text = "VS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // Takım 2
                Text(
                    text = song2?.name ?: "Takım 2",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF388E3C),
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun AdvancedMatchCard(
    match: Match,
    songs: List<Song>,
    method: String = "EMRE_CORRECT",
    emreState: com.example.ranking.ranking.EmreSystemCorrect.EmreState? = null,
    onClick: () -> Unit
) {
    val song1 = songs.find { it.id == match.songId1 }
    val song2 = songs.find { it.id == match.songId2 }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }, // Padding tamamen kaldırıldı
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 1.dp, vertical = 2.dp) // Daha da minimal padding
        ) {
            // Maç numarası başlığı
            Text(
                text = "${match.matchNumber}. Eşleşme",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Resimdeki gibi side-by-side tablo formatı - TAM EŞLEŞTİRME
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp), // Daha minimal boşluk
                verticalAlignment = Alignment.Top // Kartları üstten hizala
            ) {
                // Sol takım kartı - AFGANİSTAN formatı (Box ile wrap - puan rozeti için)
                MatchTeamCard(
                    song = song1,
                    placeholderName = "AFGANİSTAN",
                    placeholderRows = listOf(
                        "Kıta" to "Asya",
                        "Nüfus (Milyon)" to "438",
                        "Yüzölçümü (km²)" to "652230",
                        "GSYİH (Milyar USD)" to "18",
                        "Kişi Başına GSYİH (USD)" to "411"
                    ),
                    method = method,
                    emreState = emreState,
                    modifier = Modifier.weight(1f)
                )

                // VS yazısı kartlar arasında - Horizontal ortada
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 2.dp) // Daha az padding
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "V",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "S",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Sağ takım kartı - ARNAVUTLUK formatı (Box ile wrap - puan rozeti için)
                MatchTeamCard(
                    song = song2,
                    placeholderName = "ARNAVUTLUK",
                    placeholderRows = listOf(
                        "Kıta" to "Avrupa",
                        "Nüfus (Milyon)" to "28",
                        "Yüzölçümü (km²)" to "28748",
                        "GSYİH (Milyar USD)" to "28",
                        "Kişi Başına GSYİH (USD)" to "10000"
                    ),
                    method = method,
                    emreState = emreState,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// AdvancedMatchCard içindeki sol/sağ takım gövdesinin ortak hali
@Composable
private fun MatchTeamCard(
    song: Song?,
    placeholderName: String,
    placeholderRows: List<Pair<String, String>>,
    method: String,
    emreState: com.example.ranking.ranking.EmreSystemCorrect.EmreState?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column {
                // Başlık - Ana tema ile uyumlu
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = song?.name?.uppercase() ?: placeholderName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                }

                // Tablo verileri - Resimdeki format
                if (song != null && song.csvData != null) {
                    val csvMap = parseJsonToMap(song.csvData!!)
                    Column(modifier = Modifier.padding(8.dp)) {
                        if (csvMap.isNotEmpty()) {
                            csvMap.forEach { (key, value) ->
                                TableRow(
                                    label = key,
                                    value = value
                                )
                            }
                        } else {
                            TableRow("Name", song.name)
                            TableRow("Artist", song.artist)
                        }
                    }
                } else {
                    // Placeholder data resimdeki gibi
                    Column(modifier = Modifier.padding(8.dp)) {
                        placeholderRows.forEach { (label, value) ->
                            TableRow(label, value)
                        }
                    }
                }
            }
        }

        // PUAN ROZETİ - SAĞ ALT KÖŞE (TÜM USULLER)
        if (song != null) {
            val currentPoints = when (method) {
                "EMRE_CORRECT" -> emreState?.teams?.find { it.song.id == song.id }?.points ?: 0.0
                else -> 0.0 // Diğer usuller için de genişletilebilir
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
        }
    }
}
