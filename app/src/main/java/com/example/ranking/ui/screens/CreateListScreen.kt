package com.example.ranking.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
    var selectedOption by remember { mutableStateOf("manual") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
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
            //111
            Spacer(modifier = Modifier.height(16.dp))
            
            when (selectedOption) {
                "manual" -> {
                    Text(
                        text = "Her satıra bir öğe yazın (Sanatçı - Albüm - Öğe veya Sanatçı - Öğe formatında)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualSongs,
                        onValueChange = { manualSongs = it },
                        label = { Text("Öğe Listesi") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        placeholder = { Text("Örnek:\nAdele - 25 - Hello\nColdplay - X&Y - Fix You\nEd Sheeran - ÷ - Shape of You") },
                        maxLines = 10
                    )
                }
                "csv" -> {
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "CSV Formatı:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "• Dört sütun: A=Öğe Numarası, B=Sanatçı, C=Albüm, D=Öğe Adı\n• Üç sütun: Sanatçı, Albüm, Öğe Adı\n• İki sütun: Sanatçı, Öğe Adı\n• Tek sütun: Sanatçı - Öğe Adı formatında",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
            
            // Button enabled durumunu hesapla ve logla
            val isButtonEnabled = !isLoading && listName.isNotBlank() && 
                         ((selectedOption == "manual" && manualSongs.isNotBlank()) || 
                          (selectedOption == "csv" && selectedFileUri != null))
            
            // Debug log
            LaunchedEffect(isButtonEnabled, listName, selectedOption, selectedFileUri, manualSongs) {
                Log.d("CreateListScreen", "Button durumu: enabled=$isButtonEnabled, " +
                    "isLoading=$isLoading, listName='$listName', selectedOption='$selectedOption', " +
                    "selectedFileUri=$selectedFileUri, manualSongs='$manualSongs'")
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