package com.example.ranking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.R
import com.example.ranking.ui.viewmodel.SongListViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
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
    val tabs = listOf(
        stringResource(R.string.list_view_tab_cards),
        stringResource(R.string.list_view_tab_table)
    )

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text(songList?.name ?: stringResource(R.string.list_view_title_fallback))
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
            },
            actions = {
                if (onNavigateToEdit != null && songs.isNotEmpty()) {
                    IconButton(onClick = onNavigateToEdit) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.common_edit))
                    }
                }
            }
        )

        if (songs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.list_view_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Liste bilgisi - Compact header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = songList?.name ?: stringResource(R.string.list_view_list_fallback),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.list_view_total_items, songs.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }

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

            // Content based on selected tab - FULL SCREEN
            if (selectedTabIndex == 0) {
                // Team Cards View
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                }
            } else {
                // Table View - FULL SCREEN SEAMLESS TABLE like ListEditScreen
                val songsWithCsvData = songs.filter { it.csvData != null }

                if (songsWithCsvData.isNotEmpty() && songsWithCsvData.first().csvData != null) {
                    // All CSV songs have structured data - show full screen table
                    FullScreenTableDisplay(csvData = songsWithCsvData.first().csvData!!, allSongs = songs)
                } else {
                    // No CSV data available
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.list_view_no_table_data))
                    }
                }
            }
        }
    }
}

// FULL SCREEN SEAMLESS TABLE - Like ListEditScreen
@Composable
private fun FullScreenTableDisplay(csvData: String, allSongs: List<com.example.ranking.data.Song>) {
    // Extract headers from the first song's CSV data
    val headers = remember(csvData) {
        try {
            val jsonObject = JSONObject(csvData)
            jsonObject.keys().asSequence().toList().filter { !it.startsWith("_") }
        } catch (e: Exception) {
            listOf("Name")
        }
    }

    // FULL SCREEN TABLE - NO PADDING, NO CARD WRAPPER
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        val tableScrollState = rememberScrollState()

        // Header Row - Full width, no padding
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(tableScrollState),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            headers.forEachIndexed { columnIndex, header ->
                Surface(
                    modifier = Modifier
                        .width(120.dp)
                        .height(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    ) {
                        Text(
                            text = header,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
            }
        }

        // Data Rows - Full screen vertical scroll
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            allSongs.forEachIndexed { rowIndex, song ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(tableScrollState),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    headers.forEachIndexed { columnIndex, header ->
                        val cellValue = song.csvData?.let { csvData ->
                            try {
                                val jsonObject = JSONObject(csvData)
                                jsonObject.optString(header, if (header == "Name" || header.contains("Ulke")) song.name else "")
                            } catch (e: Exception) {
                                if (header == "Name" || header.contains("Ulke")) song.name else ""
                            }
                        } ?: (if (header == "Name" || header.contains("Ulke")) song.name else "")

                        Surface(
                            modifier = Modifier
                                .width(120.dp)
                                .height(40.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize().padding(4.dp)
                            ) {
                                // Handle multi-line values
                                val lines = cellValue.split(Regex("\\n|;|,")).filter { it.trim().isNotBlank() }
                                if (lines.size > 1) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        lines.take(3).forEach { line -> // Show max 3 lines
                                            Text(
                                                text = line.trim(),
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1
                                            )
                                        }
                                        if (lines.size > 3) {
                                            Text("...", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                } else {
                                    Text(
                                        text = cellValue,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2
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