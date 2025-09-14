package com.example.ranking.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.ui.viewmodel.CreateListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateListScreen(
    onNavigateBack: () -> Unit,
    onListCreated: (Long) -> Unit,
    viewModel: CreateListViewModel = viewModel()
) {
    val context = LocalContext.current
    var listName by remember { mutableStateOf("") }
    var manualSongs by remember { mutableStateOf("") }
    var selectedOption by remember { mutableStateOf("manual") } // Only manual option now
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // CSV parsing settings
    var csvDelimiter by remember { mutableStateOf(",") }
    var showCsvSettings by remember { mutableStateOf(false) }
    var displayMode by remember { mutableStateOf("cards") } // "cards" or "table"
    
    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        Log.d("CreateListScreen", "CSV dosyası seçildi: $uri")
        selectedFileUri = uri
        errorMessage = null
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TopAppBar(
            title = { Text("Yeni Liste Oluştur") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                }
            }
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp)
        ) {
            OutlinedTextField(
                value = listName,
                onValueChange = { listName = it },
                label = { Text("Liste Adı") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                isError = listName.isBlank() && (selectedOption == "manual" && manualSongs.isNotBlank() || selectedOption == "csv" && selectedFileUri != null),
                supportingText = {
                    if (listName.isBlank() && (selectedOption == "manual" && manualSongs.isNotBlank() || selectedOption == "csv" && selectedFileUri != null)) {
                        Text(
                            text = "Lütfen liste adını boş bırakmayın",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Öğeleri nasıl eklemek istiyorsunuz?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedOption == "manual",
                    onClick = { selectedOption = "manual" }
                )
                Text(
                    text = "Manuel Giriş",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedOption == "csv",
                    onClick = { selectedOption = "csv" }
                )
                Text(
                    text = "CSV Dosyasından Yükle",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            when (selectedOption) {
                "manual" -> {
                    Column {
                        Text(
                            text = "Desteklenen formatlar:\n• Her satıra bir öğe\n• Tablo formatı (CSV): başlık satırı + veri satırları\n• Excel'den kopyala-yapıştır (tab ayrımlı)\n• Format: Sanatçı - Albüm - Öğe veya Sanatçı - Öğe",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // CSV Settings Toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = showCsvSettings,
                                onCheckedChange = { showCsvSettings = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tablo formatı ayarları")
                        }
                        
                        // CSV Settings Panel
                        AnimatedVisibility(visible = showCsvSettings) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = "Ayırıcı Karakter",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            onClick = { csvDelimiter = "," },
                                            label = { Text("Virgül (,)") },
                                            selected = csvDelimiter == ","
                                        )
                                        FilterChip(
                                            onClick = { csvDelimiter = ";" },
                                            label = { Text("Noktalı virgül (;)") },
                                            selected = csvDelimiter == ";"
                                        )
                                        FilterChip(
                                            onClick = { csvDelimiter = "\t" },
                                            label = { Text("Tab") },
                                            selected = csvDelimiter == "\t"
                                        )
                                        FilterChip(
                                            onClick = { csvDelimiter = "|" },
                                            label = { Text("Pipe (|)") },
                                            selected = csvDelimiter == "|"
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Text(
                                        text = "Görüntüleme Modu",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            onClick = { displayMode = "cards" },
                                            label = { Text("Takım Kartları") },
                                            selected = displayMode == "cards"
                                        )
                                        FilterChip(
                                            onClick = { displayMode = "table" },
                                            label = { Text("Tablo Formatı") },
                                            selected = displayMode == "table"
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Text(
                                        text = "Tablo örneği:\nÜlke${csvDelimiter}Kıta${csvDelimiter}Nüfus${csvDelimiter}GSYİH\nTürkiye${csvDelimiter}Asya${csvDelimiter}84 Milyon${csvDelimiter}819 Milyar",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = manualSongs,
                            onValueChange = { manualSongs = it },
                            label = { Text("Öğe Listesi") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            placeholder = { Text("Basit liste örneği:\nHello\nFix You\nShape of You\n\nTablo örneği:\nÜlke,Kıta,Nüfus\nTürkiye,Asya,84M\nFransa,Avrupa,68M") },
                            maxLines = 10
                        )
                    }
                }
                
                "csv" -> {
                    Column {
                        Text(
                            text = "CSV dosyanızı seçin. Dosya şu formatlardan birinde olmalı:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "• Başlık satırı + veri satırları\n• Virgül (,) ile ayrılmış\n• Örnek: Ülke,Kıta,Nüfus\\nTürkiye,Asya,84M",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Display Mode seçimi
                        if (showCsvSettings) {
                            Column {
                                Text(
                                    text = "Görüntüleme Modu",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        onClick = { displayMode = "cards" },
                                        label = { Text("Takım Kartları") },
                                        selected = displayMode == "cards"
                                    )
                                    FilterChip(
                                        onClick = { displayMode = "table" },
                                        label = { Text("Tablo Formatı") },
                                        selected = displayMode == "table"
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = showCsvSettings,
                                onCheckedChange = { showCsvSettings = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gelişmiş ayarlar")
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = { csvLauncher.launch("*/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("CSV Dosyası Seç")
                        }
                        
                        selectedFileUri?.let { uri ->
                            Text(
                                text = "Seçilen dosya: ${uri.lastPathSegment}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
            
            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Button enabled durumunu hesapla
            val isButtonEnabled = !isLoading && listName.isNotBlank() && 
                                  ((selectedOption == "manual" && manualSongs.isNotBlank()) ||
                                   (selectedOption == "csv" && selectedFileUri != null))
            
            // Debug log
            LaunchedEffect(isButtonEnabled, listName, selectedOption, manualSongs, selectedFileUri) {
                Log.d("CreateListScreen", "Button durumu: enabled=$isButtonEnabled, " +
                    "isLoading=$isLoading, listName='$listName', option='$selectedOption', " +
                    "manualSongs length=${manualSongs.length}, csvFile=${selectedFileUri?.lastPathSegment}")
            }
            
            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    
                    viewModel.createList(
                        context = context,
                        listName = listName.trim(),
                        option = selectedOption,
                        manualSongs = manualSongs,
                        csvUri = selectedFileUri,
                        csvDelimiter = csvDelimiter,
                        displayMode = displayMode,
                        onSuccess = { listId ->
                            isLoading = false
                            onListCreated(listId)
                        },
                        onError = { error ->
                            isLoading = false
                            errorMessage = error
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isButtonEnabled
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Liste Oluştur")
            }
        }
    }
}