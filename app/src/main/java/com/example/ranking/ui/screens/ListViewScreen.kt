package com.example.ranking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.ui.viewmodel.SongListViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import org.json.JSONObject
import com.example.ranking.data.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListViewScreen(
    listId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (() -> Unit)? = null,
    viewModel: SongListViewModel = viewModel()
) {
    LaunchedEffect(listId) {
        viewModel.loadSongs(listId)
    }
    
    val songs by viewModel.songs.collectAsState()
    val songList by viewModel.songList.collectAsState()
    
    // Tab state
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Takım Kartları", "Tablo Formatı")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TopAppBar(
            title = { 
                Text(songList?.name ?: "Liste İçeriği") 
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                }
            },
            actions = {
                if (onNavigateToEdit != null && songs.isNotEmpty()) {
                    IconButton(onClick = onNavigateToEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Düzenle")
                    }
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
                    text = "Liste boş",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Liste bilgisi
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = songList?.name ?: "Liste",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Toplam ${songs.size} öğe",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Oluşturulma: ${songList?.createdAt ?: ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Content based on selected tab
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedTabIndex == 0) {
                    // Team Cards View
                    itemsIndexed(songs) { index, song ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(40.dp)
                                )
                                
                                TeamCardContent(
                                    song = song,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                } else {
                    // Table View - Show data in table format if available
                    val songsWithCsvData = songs.filter { it.csvData != null }
                    
                    if (songsWithCsvData.isNotEmpty() && songsWithCsvData.first().csvData != null) {
                        // All CSV songs have structured data - show full table
                        item {
                            FullTableDisplay(csvData = songsWithCsvData.first().csvData!!, allSongs = songs)
                        }
                    } else {
                        // Show individual CSV tables for each song
                        itemsIndexed(songs) { index, song ->
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}.",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(40.dp)
                                    )
                                    
                                    // Show CSV data or regular song card
                                    if (song.csvData != null) {
                                        CsvDataTableLocal(
                                            csvData = song.csvData,
                                            teamPoints = extractPointsFromCsvLocal(song.csvData)
                                        )
                                    } else {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = song.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2
                                            )
                                            if (song.artist.isNotBlank()) {
                                                Text(
                                                    text = song.artist,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper functions
private fun extractDisplayModeFromCsv(csvData: String): String {
    return try {
        val jsonObject = JSONObject(csvData)
        jsonObject.optString("_displayMode", "cards")
    } catch (e: Exception) {
        "cards"
    }
}

private fun parseCsvDataToMap(csvData: String): Map<String, String> {
    return try {
        val jsonObject = JSONObject(csvData)
        val keys = jsonObject.keys().asSequence().toList()
        val result = mutableMapOf<String, String>()
        
        keys.forEach { key ->
            val value = jsonObject.optString(key, "")
            if (value.isNotBlank()) {
                result[key] = value
            }
        }
        
        result
    } catch (e: Exception) {
        emptyMap()
    }
}

@Composable
private fun FullTableDisplay(csvData: String) {
    val parsedData = remember(csvData) {
        parseCsvDataToMap(csvData).filterKeys { !it.startsWith("_") } // Remove metadata
    }
    
    if (parsedData.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF1F8E9) // Light green background
            )
        ) {
            Column {
                // Green header with team name
                val teamName = parsedData.values.firstOrNull() ?: "Team"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF4CAF50)) // Green header
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = teamName.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                // Table rows with alternating colors
                parsedData.entries.drop(1).forEachIndexed { index, (key, value) ->
                    val backgroundColor = when {
                        index % 2 == 0 -> Color(0xFFE8F5E8) // Light green for even rows
                        else -> Color(0xFFC8E6C9) // Medium green for odd rows
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(backgroundColor)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF2E7D32), // Dark green text
                            modifier = Modifier.weight(1.5f)
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B5E20), // Darker green text  
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullTableDisplay(csvData: String, allSongs: List<com.example.ranking.data.Song>) {
    // Extract headers from the first song's CSV data
    val headers = remember(csvData) {
        try {
            val jsonObject = JSONObject(csvData)
            jsonObject.keys().asSequence().toList().filter { !it.startsWith("_") }
        } catch (e: Exception) {
            listOf("Name")
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF1F8E9) // Light green background
        )
    ) {
        Column {
            // Green header with column titles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF4CAF50)) // Green header
                    .padding(12.dp)
            ) {
                headers.forEach { header ->
                    Text(
                        text = header,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // Data rows with alternating colors
            allSongs.forEachIndexed { index, song ->
                val backgroundColor = if (index % 2 == 0) {
                    Color.White
                } else {
                    Color(0xFFF5F5F5) // Light gray for alternating rows
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    headers.forEach { header ->
                        val value = song.csvData?.let { csvData ->
                            try {
                                val jsonObject = JSONObject(csvData)
                                jsonObject.optString(header, if (header == "Name" || header.contains("Ulke")) song.name else "")
                            } catch (e: Exception) {
                                if (header == "Name" || header.contains("Ulke")) song.name else ""
                            }
                        } ?: (if (header == "Name" || header.contains("Ulke")) song.name else "")
                        
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF1B5E20), // Darker green text
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable 
private fun CsvDataTableLocal(csvData: String, teamPoints: Double = 0.0) {
    val parsedData = remember(csvData) {
        parseCsvDataToMap(csvData)
    }
    
    if (parsedData.isNotEmpty()) {
        // Blue table format with header and alternating rows
        Box {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header with team name (first value from CSV)
                val teamName = parsedData.values.firstOrNull() ?: "Team"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1976D2)) // Dark blue header
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = teamName.uppercase(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
                
                // Data rows with alternating blue colors
                parsedData.entries.drop(1).forEachIndexed { index, (key, value) ->
                    val backgroundColor = when {
                        index % 2 == 0 -> Color(0xFFBBDEFB) // Light blue for even rows
                        else -> Color(0xFF90CAF9) // Medium blue for odd rows
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(backgroundColor)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF0D47A1), // Dark blue text
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF0D47A1), // Dark blue text
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // Orange circular point badge at top-right corner
            if (teamPoints > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-8).dp)
                        .size(24.dp)
                        .background(
                            color = Color(0xFFFF9800), // Orange
                            shape = androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${teamPoints.toInt()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun extractPointsFromCsvLocal(csvData: String): Double {
    return try {
        val jsonObject = JSONObject(csvData)
        // Possible point field names (in order of preference)
        val pointFields = listOf("Puan", "puan", "Point", "point", "Points", "points", "Score", "score", "Skor", "skor")
        
        for (field in pointFields) {
            val value = jsonObject.optString(field, "")
            if (value.isNotBlank()) {
                return value.toDoubleOrNull() ?: 0.0
            }
        }
        0.0
    } catch (e: Exception) {
        0.0
    }
}