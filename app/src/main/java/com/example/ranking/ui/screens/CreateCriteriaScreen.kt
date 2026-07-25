package com.example.ranking.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.R
import com.example.ranking.ui.viewmodel.CreateCriteriaViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCriteriaScreen(
    onNavigateBack: () -> Unit,
    criteriaListId: Long? = null, // null = create new, not null = edit existing
    viewModel: CreateCriteriaViewModel = viewModel()
) {
    val context = LocalContext.current
    val isEditMode = criteriaListId != null
    
    var listName by remember { mutableStateOf("") }
    var criteria by remember { mutableStateOf(listOf<String>()) }
    var newCriterion by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // CSV file picker
    val csvFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importFromCSV(
                context = context,
                uri = uri,
                onSuccess = { importedCriteria ->
                    criteria = importedCriteria
                    errorMessage = null
                },
                onError = { error ->
                    errorMessage = context.getString(R.string.create_criteria_csv_error, error)
                }
            )
        }
    }

    // Load existing criteria if editing
    LaunchedEffect(criteriaListId) {
        if (isEditMode && criteriaListId != null) {
            viewModel.loadCriterionList(
                id = criteriaListId,
                onSuccess = { criterionList ->
                    listName = criterionList.name
                    criteria = viewModel.parseCriteriaFromJson(criterionList.criteria)
                },
                onError = { error ->
                    errorMessage = error
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TopAppBar(
            title = { Text(stringResource(if (isEditMode) R.string.create_criteria_edit_title else R.string.criteria_new_list)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
            },
            actions = {
                TextButton(
                    onClick = {
                        if (listName.isNotBlank() && criteria.isNotEmpty()) {
                            isLoading = true
                            val currentDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
                            
                            if (isEditMode && criteriaListId != null) {
                                viewModel.updateCriterionList(
                                    id = criteriaListId,
                                    name = listName,
                                    criteria = criteria,
                                    onSuccess = {
                                        isLoading = false
                                        onNavigateBack()
                                    },
                                    onError = { error ->
                                        isLoading = false
                                        errorMessage = error
                                    }
                                )
                            } else {
                                viewModel.createCriterionList(
                                    name = listName,
                                    criteria = criteria,
                                    createdDate = currentDate,
                                    onSuccess = {
                                        isLoading = false
                                        onNavigateBack()
                                    },
                                    onError = { error ->
                                        isLoading = false
                                        errorMessage = error
                                    }
                                )
                            }
                        }
                    },
                    enabled = !isLoading && listName.isNotBlank() && criteria.isNotEmpty()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text(stringResource(if (isEditMode) R.string.create_criteria_update else R.string.create_criteria_create))
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Error message
        if (errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = errorMessage!!,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // List name input
        OutlinedTextField(
            value = listName,
            onValueChange = { listName = it },
            label = { Text(stringResource(R.string.create_list_name_label)) },
            placeholder = { Text(stringResource(R.string.create_criteria_name_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Import buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { csvFilePicker.launch("text/*") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.create_criteria_import_csv))
            }
            
            OutlinedButton(
                onClick = {
                    // Örnek kriterler yükle
                    criteria = listOf(
                        "Melodi",
                        "Ritim", 
                        "Tempo",
                        "Vokal",
                        "Enstrümantasyon",
                        "Uyum",
                        "Orijinallik",
                        "Duygusal Etki"
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.List, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.create_criteria_load_sample))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Manual criterion input
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newCriterion,
                onValueChange = { newCriterion = it },
                label = { Text(stringResource(R.string.create_criteria_new_criterion)) },
                placeholder = { Text(stringResource(R.string.create_criteria_criterion_placeholder)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            
            IconButton(
                onClick = {
                    if (newCriterion.isNotBlank()) {
                        criteria = criteria + newCriterion.trim()
                        newCriterion = ""
                    }
                },
                enabled = newCriterion.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_criteria_add_criterion))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Criteria list
        Text(
            text = stringResource(R.string.create_criteria_count, criteria.size),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (criteria.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
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
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.create_criteria_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(criteria) { index, criterion ->
                    Card {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}. $criterion",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            
                            IconButton(
                                onClick = {
                                    criteria = criteria.toMutableList().apply { removeAt(index) }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.common_delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}