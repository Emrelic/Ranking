package com.example.ranking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.ui.viewmodel.CriteriaViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CriteriaScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCreateCriteria: () -> Unit,
    onNavigateToEditCriteria: (Long) -> Unit,
    viewModel: CriteriaViewModel = viewModel()
) {
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    if (hasError) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Kriter sayfası yüklenirken hata oluştu",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onNavigateBack) {
                Text("Ana Menüye Dön")
            }
        }
        return
    }
    val criterionLists by viewModel.criterionLists.collectAsState()
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            isLoading = false
        } catch (e: Exception) {
            loadError = "Veriler yüklenirken hata oluştu: ${e.message}"
            isLoading = false
        }
    }
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var listToDelete by remember { mutableStateOf<com.example.ranking.data.CriterionList?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Kriterler") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToCreateCriteria) {
                        Icon(Icons.Default.Add, contentDescription = "Yeni Kriter Listesi")
                    }
                }
            )
        }
    ) { paddingValues ->
        // Loading state
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        // Error state
        else if (loadError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = loadError!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onNavigateBack) {
                        Text("Geri Dön")
                    }
                }
            }
        }
        // Empty state
        else if (criterionLists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Henüz kriter listesi oluşturulmamış",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onNavigateToCreateCriteria) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("İlk Kriter Listenizi Oluşturun")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Toplam ${criterionLists.size} kriter listesi",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(criterionLists) { criterionList ->
                        CriterionListCard(
                            criterionList = criterionList,
                            onEdit = { onNavigateToEditCriteria(criterionList.id) },
                            onDelete = {
                                listToDelete = criterionList
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }

        // Delete confirmation dialog
        if (showDeleteDialog && listToDelete != null) {
            AlertDialog(
                onDismissRequest = { 
                    showDeleteDialog = false
                    deleteError = null
                },
                title = { Text("Kriter Listesi Silme") },
                text = { 
                    Column {
                        Text("'${listToDelete!!.name}' kriter listesini silmek istediğinizden emin misiniz?")
                        if (deleteError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = deleteError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteCriterionList(
                                criterionList = listToDelete!!,
                                onSuccess = {
                                    showDeleteDialog = false
                                    listToDelete = null
                                    deleteError = null
                                },
                                onError = { error ->
                                    deleteError = error
                                }
                            )
                        }
                    ) {
                        Text("Sil", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showDeleteDialog = false
                        deleteError = null
                    }) {
                        Text("İptal")
                    }
                }
            )
        }
    }
}

@Composable
private fun CriterionListCard(
    criterionList: com.example.ranking.data.CriterionList,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val criteria = try {
        if (criterionList.criteria.isBlank()) {
            emptyList<String>()
        } else {
            Gson().fromJson<List<String>>(
                criterionList.criteria,
                object : TypeToken<List<String>>() {}.type
            ) ?: emptyList()
        }
    } catch (e: Exception) {
        emptyList<String>()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onEdit
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = criterionList.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${criteria.size} kriter",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Oluşturulma: ${criterionList.createdDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Düzenle",
                            tint = MaterialTheme.colorScheme.primary
                        )
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
            
            if (criteria.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Kriterler: ${criteria.take(3).joinToString(", ")}${if (criteria.size > 3) "..." else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}