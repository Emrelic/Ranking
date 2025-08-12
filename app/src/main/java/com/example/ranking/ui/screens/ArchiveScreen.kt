package com.example.ranking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.*
import com.example.ranking.ui.viewmodel.ArchiveViewModel
import com.example.ranking.data.Archive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    onNavigateBack: () -> Unit,
    viewModel: ArchiveViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<Long?>(null) }
    
    LaunchedEffect(Unit) {
        viewModel.loadArchives()
    }
    
    // Error handling
    uiState.errorMessage?.let { error ->
        LaunchedEffect(error) {
            viewModel.clearError()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Arşiv") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.selectedArchive != null) {
            // Archive detail view
            ArchiveDetailView(
                archive = uiState.selectedArchive!!,
                archiveResults = uiState.archiveResults,
                archiveLeagueTable = uiState.archiveLeagueTable,
                archiveMatches = uiState.archiveMatches,
                archiveSettings = uiState.archiveSettings,
                onBack = { viewModel.closeArchive() },
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            // Archive list view
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.archives.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.List,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Henüz hiç arşiv yok",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        items(uiState.archives) { archive ->
                            ArchiveListItem(
                                archive = archive,
                                onView = { viewModel.selectArchive(archive) },
                                onDelete = { showDeleteDialog = archive.id }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Delete confirmation dialog
    showDeleteDialog?.let { archiveId ->
        val archive = uiState.archives.find { it.id == archiveId }
        if (archive != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text("Arşivi Sil") },
                text = { Text("'${archive.name}' arşivini silmek istediğinize emin misiniz? Bu işlem geri alınamaz.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteArchive(archive)
                            showDeleteDialog = null
                        }
                    ) {
                        Text("Sil", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = null }) {
                        Text("İptal")
                    }
                }
            )
        }
    }
}

@Composable
fun ArchiveListItem(
    archive: Archive,
    onView: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = archive.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = archive.listName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Sıralama: ${getMethodDisplayName(archive.method)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = dateFormatter.format(archive.archivedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row {
                    IconButton(onClick = onView) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Görüntüle")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete, 
                            contentDescription = "Sil",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${archive.totalSongs} takım",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${archive.completedMatches}/${archive.totalMatches} maç",
                    style = MaterialTheme.typography.bodySmall
                )
                if (archive.isCompleted) {
                    Text(
                        text = "Tamamlandı",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = "Devam ediyor",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveDetailView(
    archive: Archive,
    archiveResults: List<ArchiveViewModel.ArchiveResult>,
    archiveLeagueTable: List<ArchiveViewModel.LeagueTableEntry>,
    archiveMatches: List<ArchiveViewModel.ArchiveMatch>,
    archiveSettings: ArchiveViewModel.ArchiveLeagueSettings?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = if (archive.method == "LEAGUE") {
        listOf("Son Sıralama", "Puan Durumu", "Maç Sonuçları")
    } else {
        listOf("Son Sıralama", "Maç Sonuçları")
    }
    
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(archive.name) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                }
            }
        )
        
        // Archive info
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = archive.listName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Sıralama Yöntemi: ${getMethodDisplayName(archive.method)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Liste: ${archive.listName}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Takım Sayısı: ${archive.totalSongs} takım",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (archive.totalMatches > 0) {
                    Text(
                        text = "Maç Durumu: ${archive.completedMatches}/${archive.totalMatches} maç tamamlandı",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = "Durum: ${if (archive.isCompleted) "Tamamlandı" else "Yarım kaldı"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (archive.isCompleted) Color(0xFF4CAF50) else Color(0xFFFF9800)
                )
                
                archiveSettings?.let { settings ->
                    Text(
                        text = "Ayarlar: ${if (settings.useScores) "Skor girişi" else "Sadece kazanan"}, " +
                                "Kazanma: ${settings.winPoints}pt, Beraberlik: ${settings.drawPoints}pt" +
                                if (settings.doubleRoundRobin) ", Rövanşlı lig" else ", Tek devre",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Tabs
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }
        
        // Tab content
        when (selectedTab) {
            0 -> ArchiveFinalRankings(archiveResults)
            1 -> if (archive.method == "LEAGUE") {
                ArchiveLeagueTable(archiveLeagueTable)
            } else {
                ArchiveMatchSummary(archiveMatches)
            }
            2 -> ArchiveMatchSummary(archiveMatches)
        }
    }
}

@Composable
fun ArchiveFinalRankings(results: List<ArchiveViewModel.ArchiveResult>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(results) { result ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${result.position}.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(40.dp)
                        )
                        Column {
                            Text(
                                text = result.songName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            if (result.artist.isNotEmpty()) {
                                Text(
                                    text = result.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        text = String.format("%.2f", result.score),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ArchiveLeagueTable(table: List<ArchiveViewModel.LeagueTableEntry>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Takım", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                    Text("O", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
                    Text("G", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
                    Text("B", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
                    Text("M", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
                    Text("A", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
                    Text("Y", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
                    Text("P", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
                }
            }
        }
        
        items(table.withIndex().toList()) { (index, entry) ->
            Card {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(2f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(20.dp)
                        )
                        Text(
                            text = entry.teamName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(text = "${entry.played}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.5f))
                    Text(text = "${entry.won}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.5f))
                    Text(text = "${entry.drawn}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.5f))
                    Text(text = "${entry.lost}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.5f))
                    Text(text = "${entry.goalsFor}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.5f))
                    Text(text = "${entry.goalsAgainst}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.5f))
                    Text(
                        text = "${entry.points}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun ArchiveMatchSummary(matches: List<ArchiveViewModel.ArchiveMatch>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(matches.filter { it.isCompleted }) { match ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = match.team1Name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = match.team2Name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (match.score1 != null && match.score2 != null) {
                            Text(
                                text = "${match.score1} - ${match.score2}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            val winnerName = if (match.winnerId == match.team1Id) match.team1Name else match.team2Name
                            Text(
                                text = "Kazanan:",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = winnerName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getMethodDisplayName(method: String): String {
    return when (method) {
        "LEAGUE" -> "Lig"
        "ELIMINATION" -> "Eleme"
        "SWISS" -> "İsviçre"
        "EMRE" -> "Emre Usulü"
        "DIRECT_SCORING" -> "Direkt Puanlama"
        else -> method
    }
}