package com.example.ranking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ranking.data.EmrePairingMethod
import com.example.ranking.ranking.EmrePairingEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmrePairingSettingsScreen(
    listId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToRanking: (Long, String, EmrePairingMethod) -> Unit
) {
    var selectedMethod by remember { mutableStateOf(EmrePairingMethod.SEQUENTIAL) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TopAppBar(
            title = { 
                Text("Geliştirilmiş İsviçre Sistemi")
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                }
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "İlk tur eşleştirme yöntemini seçin:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(
            modifier = Modifier
                .selectableGroup()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(EmrePairingMethod.values()) { method ->
                PairingMethodOption(
                    method = method,
                    isSelected = selectedMethod == method,
                    onSelected = { selectedMethod = method }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = { 
                onNavigateToRanking(listId, "EMRE_CORRECT", selectedMethod)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Başlat")
        }
    }
}

@Composable
private fun PairingMethodOption(
    method: EmrePairingMethod,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = onSelected
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelected
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = EmrePairingEngine.getMethodName(method),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = EmrePairingEngine.getMethodDescription(method),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}