package com.example.ranking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.ui.viewmodel.ResultsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    listId: Long,
    method: String,
    onNavigateBack: () -> Unit,
    viewModel: ResultsViewModel = viewModel()
) {
    LaunchedEffect(listId, method) {
        viewModel.loadResults(listId, method)
    }
    
    val results by viewModel.results.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TopAppBar(
            title = { 
                Text("${getMethodTitle(method)} Sonuçları")
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                }
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (results.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Henüz sonuç bulunmuyor",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            Text(
                text = "Final Sıralaması",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn {
                itemsIndexed(results) { index, resultWithSong ->
                    ResultCard(
                        position = index + 1,
                        song = resultWithSong.second,
                        score = resultWithSong.first.score,
                        method = method
                    )
                    
                    if (index < results.size - 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    position: Int,
    song: com.example.ranking.data.Song,
    score: Double,
    method: String
) {
    val backgroundColor = when (position) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
        else -> MaterialTheme.colorScheme.surface
    }
    
    val textColor = when (position) {
        1, 2, 3 -> Color.Black
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Position with trophy icon for top 3
            Box(
                modifier = Modifier.width(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (position <= 3) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Trophy",
                        tint = textColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "$position",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Song info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
                if (song.artist.isNotBlank()) {
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
                if (song.album.isNotBlank()) {
                    Text(
                        text = song.album,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.5f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Score
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = formatScore(score, method),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = getScoreLabel(method),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private fun formatScore(score: Double, method: String): String {
    return when (method) {
        "DIRECT_SCORING" -> "${score.toInt()}/100"
        "LEAGUE", "SWISS" -> "${score.toInt()} puan"
        "EMRE" -> "${score.toInt()}"
        else -> score.toString()
    }
}

private fun getScoreLabel(method: String): String {
    return when (method) {
        "DIRECT_SCORING" -> "puan"
        "LEAGUE" -> "lig puanı"
        "SWISS" -> "turnuva puanı"
        "EMRE" -> "sıra puanı"
        else -> ""
    }
}

private fun getMethodTitle(method: String): String {
    return when (method) {
        "DIRECT_SCORING" -> "Direkt Puanlama"
        "LEAGUE" -> "Lig Sistemi"
        "ELIMINATION" -> "Eleme Sistemi"
        "SWISS" -> "İsviçre Sistemi"
        "EMRE" -> "Emre Usulü"
        else -> "Sıralama"
    }
}