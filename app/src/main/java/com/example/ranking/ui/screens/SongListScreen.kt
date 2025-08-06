package com.example.ranking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.ui.viewmodel.SongListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongListScreen(
    listId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToRanking: (Long, String) -> Unit,
    onNavigateToLeagueSettings: (Long, String) -> Unit = { _, _ -> },
    viewModel: SongListViewModel = viewModel()
) {
    LaunchedEffect(listId) {
        viewModel.loadSongs(listId)
    }
    
    val songs by viewModel.songs.collectAsState()
    val songList by viewModel.songList.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TopAppBar(
            title = { 
                Text(songList?.name ?: "Öğe Listesi") 
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                }
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Liste yükleniyor...",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            Text(
                text = "Toplam ${songs.size} öğe",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Sıralama Yöntemini Seçin:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RankingMethodButton(
                    title = "Direkt Puanlama",
                    description = "Her öğeye 0-100 arası puan verin",
                    onClick = { onNavigateToRanking(listId, "DIRECT_SCORING") }
                )
                
                RankingMethodButton(
                    title = "Lig Sistemi",
                    description = "Öğeler birbiri ile eşleşir, kazanan 2 puan alır",
                    onClick = { onNavigateToLeagueSettings(listId, "LEAGUE") }
                )
                
                RankingMethodButton(
                    title = "Eleme Sistemi",
                    description = "Final, yarı final şeklinde elemeli turnuva",
                    onClick = { onNavigateToRanking(listId, "ELIMINATION") }
                )
                
                RankingMethodButton(
                    title = "İsviçre Sistemi",
                    description = "Eşit puanlı rakiplerle eşleşme sistemi",
                    onClick = { onNavigateToRanking(listId, "SWISS") }
                )
                
                RankingMethodButton(
                    title = "Emre Usulü",
                    description = "İkili karşılaştırma ile sıralama",
                    onClick = { onNavigateToRanking(listId, "EMRE") }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Öğe Listesi:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn {
                items(songs) { song ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = song.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (song.artist.isNotBlank()) {
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (song.album.isNotBlank()) {
                                Text(
                                    text = "Albüm: ${song.album}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            if (song.trackNumber > 0) {
                                Text(
                                    text = "Track: ${song.trackNumber}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankingMethodButton(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}