package com.example.ranking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.ui.viewmodel.LeagueSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeagueSettingsScreen(
    listId: Long,
    method: String,
    onNavigateBack: () -> Unit,
    onNavigateToRanking: (Long, String) -> Unit,
    viewModel: LeagueSettingsViewModel = viewModel()
) {
    LaunchedEffect(listId, method) {
        viewModel.initializeSettings(listId, method)
    }
    
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TopAppBar(
            title = { 
                Text("Lig Ayarları")
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                }
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Skor Sistemi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = uiState.useScores,
                        onCheckedChange = { viewModel.updateUseScores(it) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Skor girilsin mi?")
                }
                
                if (uiState.useScores) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "Not: Skor girildiğinde, skorlara göre galibiyet/beraberlik otomatik belirlenecek.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                HorizontalDivider()
                
                Text(
                    text = "Puan Sistemi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Galibiyet:")
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = uiState.winPoints.toString(),
                            onValueChange = { 
                                it.toIntOrNull()?.let { points -> 
                                    viewModel.updateWinPoints(points)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("puan")
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Beraberlik:")
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = uiState.drawPoints.toString(),
                            onValueChange = { 
                                it.toIntOrNull()?.let { points -> 
                                    viewModel.updateDrawPoints(points)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp),
                            singleLine = true,
                            enabled = uiState.allowDraws
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("puan")
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = uiState.allowDraws,
                        onCheckedChange = { viewModel.updateAllowDraws(it) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Berabere sonuçlara izin verilsin mi?")
                }
                
                if (!uiState.allowDraws) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "Uyarı: Beraberlik kapalı olduğunda her maçta bir kazanan seçilmelidir.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                viewModel.saveSettings()
                onNavigateToRanking(listId, method)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Ayarları Kaydet ve Devam Et")
        }
    }
}