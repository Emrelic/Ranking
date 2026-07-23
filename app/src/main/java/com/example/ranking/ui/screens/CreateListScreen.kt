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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.R
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
    
    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedFileUri = uri
        errorMessage = null
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.create_list_title)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
                label = { Text(stringResource(R.string.create_list_name_label)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                isError = listName.isBlank() && (selectedOption == "manual" && manualSongs.isNotBlank() || selectedOption == "csv" && selectedFileUri != null),
                supportingText = {
                    if (listName.isBlank() && (selectedOption == "manual" && manualSongs.isNotBlank() || selectedOption == "csv" && selectedFileUri != null)) {
                        Text(
                            text = stringResource(R.string.create_list_name_error),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = stringResource(R.string.create_list_how_add),
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
                    text = stringResource(R.string.create_list_manual),
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
                    text = stringResource(R.string.create_list_csv),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            when (selectedOption) {
                "manual" -> {
                    Column {
                        Text(
                            text = stringResource(R.string.create_list_supported_formats),
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
                            Text(stringResource(R.string.create_list_table_settings))
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
                                        text = stringResource(R.string.create_list_delimiter),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            onClick = { csvDelimiter = "," },
                                            label = { Text(stringResource(R.string.create_list_delimiter_comma)) },
                                            selected = csvDelimiter == ","
                                        )
                                        FilterChip(
                                            onClick = { csvDelimiter = ";" },
                                            label = { Text(stringResource(R.string.create_list_delimiter_semicolon)) },
                                            selected = csvDelimiter == ";"
                                        )
                                        FilterChip(
                                            onClick = { csvDelimiter = "\t" },
                                            label = { Text(stringResource(R.string.create_list_delimiter_tab)) },
                                            selected = csvDelimiter == "\t"
                                        )
                                        FilterChip(
                                            onClick = { csvDelimiter = "|" },
                                            label = { Text(stringResource(R.string.create_list_delimiter_pipe)) },
                                            selected = csvDelimiter == "|"
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Text(
                                        text = stringResource(R.string.create_list_table_example, csvDelimiter),
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
                            label = { Text(stringResource(R.string.create_list_items_label)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            placeholder = { Text(stringResource(R.string.create_list_items_placeholder)) },
                            maxLines = 10
                        )
                    }
                }
                
                "csv" -> {
                    Column {
                        Text(
                            text = stringResource(R.string.create_list_csv_pick_info),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = stringResource(R.string.create_list_csv_format_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = showCsvSettings,
                                onCheckedChange = { showCsvSettings = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.create_list_advanced_settings))
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = { csvLauncher.launch("*/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.create_list_pick_csv))
                        }
                        
                        selectedFileUri?.let { uri ->
                            Text(
                                text = stringResource(R.string.create_list_selected_file, uri.lastPathSegment.toString()),
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
                        displayMode = "cards",
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
                Text(stringResource(R.string.create_list_create))
            }
        }
    }
}