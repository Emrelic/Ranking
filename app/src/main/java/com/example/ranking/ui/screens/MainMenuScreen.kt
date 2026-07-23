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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.R
import com.example.ranking.ui.viewmodel.MainMenuViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(
    onNavigateToLists: () -> Unit,
    onNavigateToCriteria: () -> Unit,
    onNavigateToActiveTournaments: () -> Unit,
    onNavigateToArchive: () -> Unit,
    onNavigateToNewTournament: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToYouTubeAnalysis: () -> Unit = {},
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
            text = stringResource(R.string.main_menu_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(R.string.main_menu_subtitle),
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
                    title = stringResource(R.string.main_menu_new_tournament),
                    description = stringResource(R.string.main_menu_new_tournament_desc),
                    icon = Icons.Default.Add,
                    onClick = onNavigateToNewTournament,
                    modifier = Modifier.weight(1f)
                )
                
                MenuCard(
                    title = stringResource(R.string.main_menu_lists),
                    description = stringResource(R.string.main_menu_lists_desc, songListsCount),
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
                    title = stringResource(R.string.main_menu_criteria),
                    description = stringResource(R.string.main_menu_criteria_desc, criterionListsCount),
                    icon = Icons.Default.Star,
                    onClick = onNavigateToCriteria,
                    modifier = Modifier.weight(1f)
                )
                
                MenuCard(
                    title = stringResource(R.string.main_menu_active_tournaments),
                    description = stringResource(R.string.main_menu_active_tournaments_desc, activeTournamentsCount),
                    icon = Icons.Default.PlayArrow,
                    onClick = onNavigateToActiveTournaments,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Third Row - YouTube Music Analysis  
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MenuCard(
                    title = stringResource(R.string.main_menu_youtube_analysis),
                    description = stringResource(R.string.main_menu_youtube_analysis_desc),
                    icon = Icons.Default.PlayArrow,
                    onClick = onNavigateToYouTubeAnalysis,
                    modifier = Modifier.weight(1f),
                    backgroundColor = MaterialTheme.colorScheme.tertiaryContainer
                )
                
                MenuCard(
                    title = stringResource(R.string.main_menu_archive),
                    description = stringResource(R.string.main_menu_archive_desc, completedTournamentsCount),
                    icon = Icons.Default.List,
                    onClick = onNavigateToArchive,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Fourth Row  
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MenuCard(
                    title = stringResource(R.string.main_menu_about),
                    description = stringResource(R.string.main_menu_about_desc),
                    icon = Icons.Default.Info,
                    onClick = onNavigateToAbout,
                    modifier = Modifier.weight(1f)
                )
                
                // Empty space for symmetry
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Footer info
        Text(
            text = stringResource(R.string.main_menu_footer),
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
    modifier: Modifier = Modifier,
    backgroundColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface
) {
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
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