package com.example.ranking.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.ui.viewmodel.ListEditViewModel
import com.example.ranking.data.Song
import org.json.JSONObject
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListEditScreen(
    listId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ListEditViewModel = viewModel()
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    
    // State variables
    var isLoading by remember { mutableStateOf(true) }
    var listData by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var headers by remember { mutableStateOf<List<String>>(emptyList()) }
    var listName by remember { mutableStateOf("") }
    var showAddColumnDialog by remember { mutableStateOf(false) }
    var showAddRowDialog by remember { mutableStateOf(false) }
    var editingCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var editingText by remember { mutableStateOf("") }
    var selectedColumn by remember { mutableStateOf<Int?>(null) }
    var draggedColumn by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var draggedOverColumn by remember { mutableStateOf<Int?>(null) }
    var originalSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    
    // UNIFIED SCROLL STATE - TÜM TABLO İÇİN TEK SCROLL
    val tableScrollState = rememberScrollState()
    
    // Reorder columns function
    fun reorderColumns(fromIndex: Int, toIndex: Int) {
        if (fromIndex != toIndex && fromIndex in headers.indices && toIndex in headers.indices) {
            val newHeaders = headers.toMutableList()
            val draggedHeader = newHeaders.removeAt(fromIndex)
            newHeaders.add(toIndex, draggedHeader)
            headers = newHeaders
            
            // Reorder data in all rows
            val newListData = listData.map { row ->
                val newRow = mutableMapOf<String, String>()
                newHeaders.forEach { header ->
                    newRow[header] = row[header] ?: ""
                }
                newRow
            }
            listData = newListData
            hasUnsavedChanges = true
            
            // Reset drag states
            draggedColumn = null
            draggedOverColumn = null
            dragOffset = Offset.Zero
        }
    }
    
    // Load list data with debug logs
    LaunchedEffect(listId) {
        Log.d("ListEditScreen", "🔄 LaunchedEffect started for listId: $listId")
        viewModel.loadListData(listId) { songs ->
            Log.d("ListEditScreen", "📥 Callback received: ${songs.size} songs")
            isLoading = false
            originalSongs = songs
            if (songs.isNotEmpty()) {
                Log.d("ListEditScreen", "✅ Songs not empty, processing...")
                // Parse CSV data from first song to get structure
                val firstSong = songs[0]
                Log.d("ListEditScreen", "🎵 First song: ${firstSong.name}, csvData: ${firstSong.csvData?.take(100)}")
                if (firstSong.csvData != null) {
                    try {
                        val csvData = JSONObject(firstSong.csvData!!)
                        headers = csvData.keys().asSequence().toList().filter { it != "_displayMode" }
                        Log.d("ListEditScreen", "📊 Headers extracted: $headers")
                        
                        // Build table data
                        listData = songs.map { song ->
                            val songData = mutableMapOf<String, String>()
                            if (song.csvData != null) {
                                val json = JSONObject(song.csvData!!)
                                headers.forEach { header ->
                                    songData[header] = json.optString(header, "")
                                }
                            } else {
                                songData["Name"] = song.name
                                songData["Artist"] = song.artist
                                songData["Album"] = song.album
                            }
                            songData
                        }
                        Log.d("ListEditScreen", "🗂️ ListData built: ${listData.size} rows, first row: ${listData.firstOrNull()}")
                        listName = songs[0].name // Temporary, should get from list name
                    } catch (e: Exception) {
                        Log.e("ListEditScreen", "❌ JSON parsing error: ${e.message}")
                        // Fallback to simple structure
                        headers = listOf("Name", "Artist", "Album")
                        listData = songs.map { song ->
                            mapOf(
                                "Name" to song.name,
                                "Artist" to song.artist,
                                "Album" to song.album
                            )
                        }
                        Log.d("ListEditScreen", "🔄 Fallback data: ${listData.size} rows")
                    }
                } else {
                    Log.d("ListEditScreen", "⚠️ No CSV data, using fallback")
                    headers = listOf("Name", "Artist", "Album")
                    listData = songs.map { song ->
                        mapOf(
                            "Name" to song.name,
                            "Artist" to song.artist,
                            "Album" to song.album
                        )
                    }
                    Log.d("ListEditScreen", "🔄 Fallback data: ${listData.size} rows")
                }
            } else {
                Log.w("ListEditScreen", "⚠️ Songs list is empty")
            }
            Log.d("ListEditScreen", "🏁 Final state: isLoading=$isLoading, headers=${headers.size}, listData=${listData.size}")
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Bar - Fixed Height
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color(0xFF1976D2))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Geri",
                    tint = Color.White
                )
            }
            
            Text(
                "Liste Rötuşlama",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
        
        // WORKING TABLE - FILLS REMAINING SPACE  
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(8.dp)
        ) {
            // Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Add Column
                Button(
                    onClick = {
                        showAddColumnDialog = true
                        hasUnsavedChanges = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier
                        .height(36.dp)
                        .width(70.dp)
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("+Sütun", fontSize = 10.sp)
                }
                
                // Add Row
                Button(
                    onClick = {
                        showAddRowDialog = true
                        hasUnsavedChanges = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                    modifier = Modifier
                        .height(36.dp)
                        .width(70.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("+Satır", fontSize = 10.sp)
                }
                
                // Delete Column
                Button(
                    onClick = { 
                        selectedColumn?.let { colIndex ->
                            if (headers.size > 1) {
                                val headerToRemove = headers[colIndex]
                                headers = headers.filterIndexed { index, _ -> index != colIndex }
                                listData = listData.map { row ->
                                    row.filterKeys { it != headerToRemove }
                                }
                                selectedColumn = null
                                hasUnsavedChanges = true
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                    enabled = selectedColumn != null,
                    modifier = Modifier
                        .height(36.dp)
                        .width(65.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("-Sütun", fontSize = 10.sp)
                }
                
                // KAYDET BUTONU - TOOLBAR'DA
                Button(
                    onClick = {
                        if (hasUnsavedChanges) {
                            isSaving = true
                            val updatedSongs = originalSongs.mapIndexed { index, song ->
                                if (index < listData.size) {
                                    val rowData = listData[index]
                                    Pair(song.id, rowData)
                                } else {
                                    Pair(song.id, mapOf<String, String>())
                                }
                            }
                            
                            viewModel.saveListChanges(
                                listId = listId,
                                updatedSongs = updatedSongs,
                                headers = headers, // Sütun sırası bilgisi
                                displayMode = "table",
                                onSuccess = {
                                    hasUnsavedChanges = false
                                    isSaving = false
                                },
                                onError = { error ->
                                    isSaving = false
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasUnsavedChanges) Color(0xFFFF9800) else Color(0xFFBDBDBD)
                    ),
                    enabled = hasUnsavedChanges && !isSaving,
                    modifier = Modifier
                        .height(36.dp)
                        .width(if (isSaving) 85.dp else 65.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("...", fontSize = 10.sp)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Kaydet", fontSize = 10.sp)
                    }
                }
            }
            
            // REAL TABLE
            Card(
                modifier = Modifier.fillMaxSize(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                if (headers.isNotEmpty() && listData.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        // Header Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(tableScrollState),
                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            headers.forEachIndexed { columnIndex, header ->
                                val isDragged = draggedColumn == columnIndex
                                val isDraggedOver = draggedOverColumn == columnIndex && draggedColumn != null
                                
                                Surface(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .height(48.dp)
                                        .pointerInput(columnIndex) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    draggedColumn = columnIndex
                                                    draggedOverColumn = columnIndex // Start with current column
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    Log.d("DragDrop", "🎯 Drag başladı - Column: $columnIndex")
                                                },
                                                onDragEnd = {
                                                    Log.d("DragDrop", "🎯 Drag bitti - From: $draggedColumn, To: $draggedOverColumn")
                                                    if (draggedColumn != null && draggedOverColumn != null &&
                                                        draggedColumn != draggedOverColumn) {
                                                        reorderColumns(draggedColumn!!, draggedOverColumn!!)
                                                        Log.d("DragDrop", "✅ Sütun yeniden sıralandı")
                                                    }
                                                    // Reset all drag states
                                                    draggedColumn = null
                                                    draggedOverColumn = null
                                                    dragOffset = Offset.Zero
                                                },
                                                onDrag = { change, offset ->
                                                    dragOffset += offset

                                                    // Calculate target column with scroll offset consideration
                                                    val columnWidth = 121f // 120dp + 1dp spacing
                                                    val scrollOffset = tableScrollState.value

                                                    // Total X position = scroll position + current drag position
                                                    val totalX = scrollOffset + change.position.x

                                                    // Calculate target column index
                                                    val targetColumn = (totalX / columnWidth).toInt()
                                                        .coerceIn(0, headers.size - 1)

                                                    // Only update if target changed
                                                    if (targetColumn != draggedOverColumn) {
                                                        draggedOverColumn = targetColumn
                                                        Log.d("DragDrop", "📍 Column $columnIndex → Column $targetColumn (x=${totalX.toInt()}, scroll=$scrollOffset)")
                                                    }
                                                }
                                            )
                                        }
                                        .clickable { 
                                            selectedColumn = if (selectedColumn == columnIndex) null else columnIndex
                                        },
                                    color = when {
                                        isDragged -> Color(0xFFFFC107) // Dragged column - amber
                                        isDraggedOver -> Color(0xFF4CAF50) // Drop target - green
                                        selectedColumn == columnIndex -> Color(0xFFE3F2FD)
                                        else -> Color(0xFF1976D2)
                                    },
                                    border = BorderStroke(
                                        width = if (isDragged || isDraggedOver) 2.dp else 1.dp,
                                        color = if (isDragged || isDraggedOver) Color.Black else Color(0xFF1976D2)
                                    )
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize().padding(4.dp)
                                    ) {
                                        Text(
                                            text = header,
                                            color = when {
                                                isDragged -> Color.Black // Dragged column - black text
                                                isDraggedOver -> Color.White // Drop target - white text
                                                selectedColumn == columnIndex -> Color(0xFF1976D2)
                                                else -> Color.White
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Data Rows
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            itemsIndexed(listData) { rowIndex, row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(tableScrollState),
                                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    headers.forEachIndexed { columnIndex, header ->
                                        val cellValue = row[header] ?: ""
                                        val isEditing = editingCell == Pair(rowIndex, columnIndex)
                                        
                                        Surface(
                                            modifier = Modifier
                                                .width(120.dp)
                                                .height(40.dp)
                                                .clickable {
                                                    if (isEditing) {
                                                        val updatedRow = row.toMutableMap()
                                                        updatedRow[header] = editingText
                                                        val updatedListData = listData.toMutableList()
                                                        updatedListData[rowIndex] = updatedRow
                                                        listData = updatedListData
                                                        hasUnsavedChanges = true
                                                        editingCell = null
                                                    } else {
                                                        editingCell = Pair(rowIndex, columnIndex)
                                                        editingText = cellValue
                                                    }
                                                },
                                            color = if (isEditing) Color(0xFFFFF3E0) else Color.White,
                                            border = BorderStroke(0.5.dp, Color(0xFFE0E0E0))
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.CenterStart,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                if (isEditing) {
                                                    BasicTextField(
                                                        value = editingText,
                                                        onValueChange = { editingText = it },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        singleLine = true
                                                    )
                                                } else {
                                                    Text(
                                                        text = cellValue,
                                                        fontSize = 11.sp,
                                                        maxLines = 2,
                                                        color = Color.Black
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Liste yükleniyor...")
                    }
                }
            }  
        }
    }
    
    // Add Column Dialog
    if (showAddColumnDialog) {
        AddColumnDialog(
            onDismiss = { showAddColumnDialog = false },
            onAddColumn = { columnName ->
                headers = headers + columnName
                listData = listData.map { row ->
                    row + (columnName to "")
                }
                hasUnsavedChanges = true
                showAddColumnDialog = false
            }
        )
    }
    
    // Add Row Dialog
    if (showAddRowDialog) {
        AddRowDialog(
            headers = headers,
            onDismiss = { showAddRowDialog = false },
            onAddRow = { newRowData ->
                listData = listData + newRowData
                hasUnsavedChanges = true
                showAddRowDialog = false
            }
        )
    }
}

@Composable
fun AddColumnDialog(
    onDismiss: () -> Unit,
    onAddColumn: (String) -> Unit
) {
    var columnName by remember { mutableStateOf("") }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Yeni Sütun Ekle",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                OutlinedTextField(
                    value = columnName,
                    onValueChange = { columnName = it },
                    label = { Text("Sütun Adı") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("İptal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { 
                            if (columnName.isNotBlank()) {
                                onAddColumn(columnName.trim())
                            }
                        },
                        enabled = columnName.isNotBlank()
                    ) {
                        Text("Ekle")
                    }
                }
            }
        }
    }
}

@Composable
fun AddRowDialog(
    headers: List<String>,
    onDismiss: () -> Unit,
    onAddRow: (Map<String, String>) -> Unit
) {
    var rowData by remember { mutableStateOf(headers.associateWith { "" }) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Yeni Satır Ekle",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(headers) { header ->
                        OutlinedTextField(
                            value = rowData[header] ?: "",
                            onValueChange = { newValue ->
                                rowData = rowData + (header to newValue)
                            },
                            label = { Text(header) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("İptal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onAddRow(rowData) }
                    ) {
                        Text("Ekle")
                    }
                }
            }
        }
    }
}