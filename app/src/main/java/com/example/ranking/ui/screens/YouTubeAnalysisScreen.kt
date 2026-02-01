package com.example.ranking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.ranking.ui.viewmodel.YouTubeAnalysisViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeAnalysisScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRanking: (Long, String) -> Unit,
    viewModel: YouTubeAnalysisViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text("YouTube Müzik Analizi")
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
        
        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Artist Search Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Şarkıcı Analizi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::updateSearchQuery,
                        label = { Text("Şarkıcı Adı") },
                        placeholder = { Text("Örn: Sezen Aksu") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                viewModel.searchArtist()
                            }
                        ),
                        singleLine = true
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.maxResults.toString(),
                            onValueChange = { newValue ->
                                newValue.toIntOrNull()?.let { 
                                    if (it in 1..50) viewModel.updateMaxResults(it) 
                                }
                            },
                            label = { Text("Max Şarkı") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            singleLine = true
                        )
                        
                        Button(
                            onClick = { 
                                keyboardController?.hide()
                                viewModel.searchArtist() 
                            },
                            enabled = uiState.searchQuery.isNotBlank() && !uiState.isLoading,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("🔍 Analiz Et")
                            }
                        }
                    }
                }
            }
            
            // Results Section
            when {
                uiState.error != null -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "Hata: ${uiState.error}",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                uiState.tracks.isNotEmpty() -> {
                    ResultsSection(
                        tracks = uiState.tracks,
                        onCreatePlaylist = viewModel::createPlaylist,
                        onCreateRanking = { tracks ->
                            val listId = viewModel.createRankingList(tracks)
                            if (listId > 0) {
                                onNavigateToRanking(listId, "EMRE_CORRECT")
                            }
                        },
                        isCreatingPlaylist = uiState.isCreatingPlaylist,
                        isCreatingRanking = uiState.isCreatingRanking
                    )
                }
                
                uiState.hasSearched && uiState.tracks.isEmpty() && !uiState.isLoading -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = "Bu şarkıcı için sonuç bulunamadı.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsSection(
    tracks: List<com.example.ranking.data.YouTubeTrack>,
    onCreatePlaylist: () -> Unit,
    onCreateRanking: (List<com.example.ranking.data.YouTubeTrack>) -> Unit,
    isCreatingPlaylist: Boolean,
    isCreatingRanking: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📊 Analiz Sonuçları (${tracks.size} şarkı)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "Toplam: ${tracks.sumOf { it.viewCount / 1_000_000 }}M görüntülenme",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onCreateRanking(tracks) },
                    enabled = !isCreatingRanking,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isCreatingRanking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(16.dp)
                        )
                        Text("Ranking Sistemi")
                    }
                }
                
                OutlinedButton(
                    onClick = onCreatePlaylist,
                    enabled = !isCreatingPlaylist,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isCreatingPlaylist) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text("💾 Kaydet")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Track List
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tracks.take(10)) { track -> // Show top 10 for preview
                    TrackItem(
                        track = track,
                        position = tracks.indexOf(track) + 1
                    )
                }
                
                if (tracks.size > 10) {
                    item {
                        Text(
                            text = "... ve ${tracks.size - 10} şarkı daha",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackItem(
    track: com.example.ranking.data.YouTubeTrack,
    position: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Position
            Surface(
                modifier = Modifier.size(32.dp),
                shape = MaterialTheme.shapes.small,
                color = when {
                    position <= 3 -> Color(0xFFFFD700) // Gold
                    position <= 5 -> Color(0xFFC0C0C0) // Silver
                    else -> MaterialTheme.colorScheme.secondaryContainer
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = position.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (position <= 5) Color.Black else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            
            // Thumbnail
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp, 36.dp)
            )
            
            // Track Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                
                Text(
                    text = "${track.viewCount / 1_000_000}M görüntülenme",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}