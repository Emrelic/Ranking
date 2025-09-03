package com.example.ranking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.ui.viewmodel.MainMenuViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(
    onNavigateToLists: () -> Unit,
    onNavigateToCriteria: () -> Unit,
    onNavigateToActiveTournaments: () -> Unit,
    onNavigateToArchive: () -> Unit,
    onNavigateToNewTournament: () -> Unit,
    viewModel: MainMenuViewModel = viewModel()
) {
    val activeTournamentsCount by viewModel.activeTournamentsCount.collectAsState()
    val completedTournamentsCount by viewModel.completedTournamentsCount.collectAsState()
    val songListsCount by viewModel.songListsCount.collectAsState()
    val criterionListsCount by viewModel.criterionListsCount.collectAsState()

    // Load data when screen appears
    LaunchedEffect(Unit) {
        viewModel.loadCounts()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // Header
        Text(
            text = "Ranking Sistemi",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Turnuva ve değerlendirme yönetimi",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Main Menu Cards - 2x2 Grid with New Tournament
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // First Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MenuCard(
                    title = "Yeni Turnuva",
                    description = "Kriter sistemi ile",
                    icon = Icons.Default.Add,
                    onClick = onNavigateToNewTournament,
                    modifier = Modifier.weight(1f)
                )
                
                MenuCard(
                    title = "Listeler",
                    description = "$songListsCount liste",
                    icon = Icons.Default.List,
                    onClick = onNavigateToLists,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Second Row  
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MenuCard(
                    title = "Kriterler",
                    description = "$criterionListsCount kriter listesi",
                    icon = Icons.Default.Star,
                    onClick = onNavigateToCriteria,
                    modifier = Modifier.weight(1f)
                )
                
                MenuCard(
                    title = "Devam Eden\nTurnuvalar",
                    description = "$activeTournamentsCount aktif turnuva",
                    icon = Icons.Default.PlayArrow,
                    onClick = onNavigateToActiveTournaments,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Third Row  
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MenuCard(
                    title = "Arşiv",
                    description = "$completedTournamentsCount tamamlanan",
                    icon = Icons.Default.List,
                    onClick = onNavigateToArchive,
                    modifier = Modifier.weight(1f)
                )
                
                // Empty space to maintain balance
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Footer info
        Text(
            text = "Geliştirilmiş İsviçre Sistemi v2.0\nKriterler Sistemi Aktif",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun MenuCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}