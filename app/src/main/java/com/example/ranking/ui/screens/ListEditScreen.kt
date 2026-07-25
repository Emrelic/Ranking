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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.R
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
    var draggedOverColumn by remember { mutableStateOf<Int?>(null) }
    var originalSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    // Her tablo satırının hangi şarkı kaydına ait olduğu (satır sırasıyla senkron).
    // -1 = henüz kaydedilmemiş yeni satır. Kayıt bu eşlemeyle yapılır; önceden
    // indekse göre originalSongs ile eşlenince satır taşıma sonrası veriler
    // yanlış kayıtlara yazılıyordu.
    var rowSongIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var selectedRow by remember { mutableStateOf<Int?>(null) }
    var draggedRow by remember { mutableStateOf<Int?>(null) }
    var draggedOverRow by remember { mutableStateOf<Int?>(null) }
    var showHeaderDesignDialog by remember { mutableStateOf(false) }
    
    // UNIFIED SCROLL STATE - TÜM TABLO İÇİN TEK SCROLL
    val tableScrollState = rememberScrollState()
    
    // Reorder rows function
    fun reorderRows(fromIndex: Int, toIndex: Int) {
        if (fromIndex != toIndex && fromIndex in listData.indices && toIndex in listData.indices) {
            val newListData = listData.toMutableList()
            val draggedRowData = newListData.removeAt(fromIndex)
            newListData.add(toIndex, draggedRowData)
            listData = newListData

            // Satır-şarkı eşlemesi satırla birlikte taşınmalı
            val newIds = rowSongIds.toMutableList()
            if (fromIndex in newIds.indices) {
                val draggedId = newIds.removeAt(fromIndex)
                newIds.add(toIndex.coerceAtMost(newIds.size), draggedId)
                rowSongIds = newIds
            }
            hasUnsavedChanges = true
        }
    }

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
            // Note: State resets handled in UI layer
        }
    }
    
    // Load list data with debug logs
    LaunchedEffect(listId) {
        viewModel.loadListData(listId) { songs ->
            isLoading = false
            originalSongs = songs
            if (songs.isNotEmpty()) {
                // Parse CSV data from first song to get structure
                val firstSong = songs[0]
                if (firstSong.csvData != null) {
                    try {
                        val csvData = JSONObject(firstSong.csvData!!)
                        headers = csvData.keys().asSequence().toList().filter { it != "_displayMode" }
                        
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
                        rowSongIds = songs.map { it.id }
                        listName = songs[0].name // Temporary, should get from list name
                    } catch (e: Exception) {
                        // Fallback to simple structure
                        headers = listOf("Name", "Artist", "Album")
                        listData = songs.map { song ->
                            mapOf(
                                "Name" to song.name,
                                "Artist" to song.artist,
                                "Album" to song.album
                            )
                        }
                        rowSongIds = songs.map { it.id }
                    }
                } else {
                    headers = listOf("Name", "Artist", "Album")
                    listData = songs.map { song ->
                        mapOf(
                            "Name" to song.name,
                            "Artist" to song.artist,
                            "Album" to song.album
                        )
                    }
                    rowSongIds = songs.map { it.id }
                }
            } else {
            }
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
                    contentDescription = stringResource(R.string.common_back),
                    tint = Color.White
                )
            }

            Text(
                stringResource(R.string.list_edit_title),
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
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Header Design Button
                Button(
                    onClick = {
                        showHeaderDesignDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(stringResource(R.string.list_edit_add_header), fontSize = 9.sp, textAlign = TextAlign.Center)
                    }
                }

                // Add Column
                Button(
                    onClick = {
                        if (selectedColumn != null) {
                            // hasUnsavedChanges dialog onaylanınca işaretlenir
                            // (iptal edilirse değişiklik yok)
                            showAddColumnDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    enabled = selectedColumn != null,
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(stringResource(R.string.list_edit_add_column), fontSize = 9.sp, textAlign = TextAlign.Center)
                    }
                }
                
                // Add Row
                Button(
                    onClick = {
                        if (selectedRow != null || listData.isNotEmpty()) {
                            // hasUnsavedChanges dialog onaylanınca işaretlenir
                            showAddRowDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(stringResource(R.string.list_edit_add_row), fontSize = 9.sp, textAlign = TextAlign.Center)
                    }
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
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(stringResource(R.string.common_delete), fontSize = 9.sp, textAlign = TextAlign.Center)
                    }
                }
                
                // KAYDET BUTONU - TOOLBAR'DA
                Button(
                    onClick = {
                        if (hasUnsavedChanges) {
                            isSaving = true
                            // Satırlar şarkı kimliğiyle eşlenir (indeks değil):
                            // satır taşındıysa veri doğru kayda yazılır,
                            // yeni satırlar (-1) yeni kayıt olarak eklenir
                            val updatedSongs = listData.mapIndexed { index, rowData ->
                                Pair(rowSongIds.getOrNull(index) ?: -1L, rowData)
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
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                        Text(
                            text = stringResource(if (isSaving) R.string.list_edit_saving else R.string.common_save),
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center
                        )
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
                        // Header Row with Column Labels
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            // Corner cell (row/column intersection)
                            Surface(
                                modifier = Modifier
                                    .width(50.dp)
                                    .height(48.dp),
                                color = Color(0xFF757575),
                                border = BorderStroke(1.dp, Color(0xFF424242))
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = "#",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Column Labels (A, B, C, D...) + Data Headers
                            Row(
                                modifier = Modifier.horizontalScroll(tableScrollState),
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                headers.forEachIndexed { columnIndex, header ->
                                    val isDragged = draggedColumn == columnIndex
                                    val isDraggedOver = draggedOverColumn == columnIndex && draggedColumn != null
                                    val columnLetter = ('A' + columnIndex).toString()

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        // Column Letter (A, B, C, D...)
                                        Surface(
                                            modifier = Modifier
                                                .width(120.dp)
                                                .height(24.dp)
                                                .pointerInput(columnIndex) {
                                                    detectDragGestures(
                                                        onDragStart = {
                                                            draggedColumn = columnIndex
                                                            draggedOverColumn = columnIndex
                                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        },
                                                        onDragEnd = {
                                                            if (draggedColumn != null && draggedOverColumn != null &&
                                                                draggedColumn != draggedOverColumn) {
                                                                reorderColumns(draggedColumn!!, draggedOverColumn!!)
                                                            }
                                                            draggedColumn = null
                                                            draggedOverColumn = null
                                                        },
                                                        onDragCancel = {
                                                            draggedColumn = null
                                                            draggedOverColumn = null
                                                        },
                                                        onDrag = { change, _ ->
                                                            // Jest scroll konteynerine kaçmasın
                                                            change.consume()
                                                            // change.position sürüklenen hücreye GÖRE yereldir;
                                                            // satır içi konum = başlangıç sütunu ofseti + yerel x.
                                                            // (Eski hata: ofset yok sayılıyor ve 121 dp piksel
                                                            // sanılıyordu -> hedef hep A sütununa çıkıyordu)
                                                            val columnWidthPx = 121.dp.toPx() // 120dp hücre + 1dp boşluk
                                                            val positionInRow = columnIndex * columnWidthPx + change.position.x
                                                            val targetColumn = (positionInRow / columnWidthPx).toInt()
                                                                .coerceIn(0, headers.size - 1)
                                                            if (targetColumn != draggedOverColumn) {
                                                                draggedOverColumn = targetColumn
                                                            }
                                                        }
                                                    )
                                                },
                                            color = when {
                                                isDragged -> Color(0xFFFFC107) // Amber
                                                isDraggedOver -> Color(0xFF4CAF50) // Green
                                                else -> Color(0xFF616161) // Dark grey
                                            },
                                            border = BorderStroke(1.dp, Color(0xFF424242))
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Text(
                                                    text = columnLetter,
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        // Data Header
                                        Surface(
                                            modifier = Modifier
                                                .width(120.dp)
                                                .height(24.dp)
                                                .clickable {
                                                    selectedColumn = if (selectedColumn == columnIndex) null else columnIndex
                                                },
                                            color = if (selectedColumn == columnIndex) Color(0xFFE3F2FD) else Color(0xFF1976D2),
                                            border = BorderStroke(1.dp, Color(0xFF1976D2))
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize().padding(2.dp)
                                            ) {
                                                Text(
                                                    text = header,
                                                    color = if (selectedColumn == columnIndex) Color(0xFF1976D2) else Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Data Rows with Row Numbers
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            itemsIndexed(listData) { rowIndex, row ->
                                val isRowDragged = draggedRow == rowIndex
                                val isRowDraggedOver = draggedOverRow == rowIndex && draggedRow != null
                                val isRowSelected = selectedRow == rowIndex

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    // Row Number (1, 2, 3...)
                                    Surface(
                                        modifier = Modifier
                                            .width(50.dp)
                                            .height(48.dp)
                                            .pointerInput(rowIndex) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        draggedRow = rowIndex
                                                        draggedOverRow = rowIndex
                                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    },
                                                    onDragEnd = {
                                                        if (draggedRow != null && draggedOverRow != null &&
                                                            draggedRow != draggedOverRow) {
                                                            reorderRows(draggedRow!!, draggedOverRow!!)
                                                        }
                                                        draggedRow = null
                                                        draggedOverRow = null
                                                    },
                                                    onDragCancel = {
                                                        draggedRow = null
                                                        draggedOverRow = null
                                                    },
                                                    onDrag = { change, _ ->
                                                        // Jest liste kaydırmasına kaçmasın
                                                        change.consume()
                                                        // change.position sürüklenen satır hücresine göre yereldir;
                                                        // liste içi konum = başlangıç satırı ofseti + yerel y
                                                        val rowHeightPx = 49.dp.toPx() // 48dp hücre + 1dp boşluk
                                                        val positionInList = rowIndex * rowHeightPx + change.position.y
                                                        val targetRow = (positionInList / rowHeightPx).toInt()
                                                            .coerceIn(0, listData.size - 1)
                                                        if (targetRow != draggedOverRow) {
                                                            draggedOverRow = targetRow
                                                        }
                                                    }
                                                )
                                            }
                                            .clickable {
                                                selectedRow = if (selectedRow == rowIndex) null else rowIndex
                                            },
                                        color = when {
                                            isRowDragged -> Color(0xFFFFC107) // Amber
                                            isRowDraggedOver -> Color(0xFF4CAF50) // Green
                                            isRowSelected -> Color(0xFFE3F2FD) // Light blue
                                            else -> Color(0xFF757575) // Grey
                                        },
                                        border = BorderStroke(1.dp, Color(0xFF424242))
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Text(
                                                text = "${rowIndex + 1}",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Data Cells
                                    Row(
                                        modifier = Modifier
                                            .background(
                                                when {
                                                    isRowDragged -> Color(0x30FFC107) // Semi-transparent amber
                                                    isRowDraggedOver -> Color(0x304CAF50) // Semi-transparent green
                                                    isRowSelected -> Color(0x30E3F2FD) // Semi-transparent blue
                                                    else -> Color.Transparent
                                                }
                                            )
                                            .horizontalScroll(tableScrollState),
                                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        headers.forEachIndexed { columnIndex, header ->
                                            val cellValue = row[header] ?: ""
                                            val isEditing = editingCell == Pair(rowIndex, columnIndex)

                                            Surface(
                                                modifier = Modifier
                                                    .width(120.dp)
                                                    .height(48.dp)
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
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.list_edit_loading))
                    }
                }
            }
        }
    }

    // Add Column Dialog
    if (showAddColumnDialog) {
        AddColumnDialog(
            selectedColumnIndex = selectedColumn,
            onDismiss = { showAddColumnDialog = false },
            onAddColumn = { columnName ->
                val insertIndex = if (selectedColumn != null) {
                    selectedColumn!! // Insert before selected column
                } else {
                    headers.size // Insert at end if no column selected
                }

                val newHeaders = headers.toMutableList()
                newHeaders.add(insertIndex, columnName)
                headers = newHeaders

                listData = listData.map { row ->
                    val newRow = mutableMapOf<String, String>()
                    newHeaders.forEach { header ->
                        newRow[header] = if (header == columnName) "" else (row[header] ?: "")
                    }
                    newRow
                }
                hasUnsavedChanges = true
                showAddColumnDialog = false
                selectedColumn = null // Clear selection after adding
            }
        )
    }

    // Add Row Dialog
    if (showAddRowDialog) {
        AddRowDialog(
            headers = headers,
            selectedRowIndex = selectedRow,
            onDismiss = { showAddRowDialog = false },
            onAddRow = { newRowData ->
                val insertIndex = if (selectedRow != null) {
                    selectedRow!! + 1 // Insert after selected row
                } else {
                    listData.size // Insert at end if no row selected
                }

                val newListData = listData.toMutableList()
                newListData.add(insertIndex, newRowData)
                listData = newListData

                // Yeni satır: henüz DB kaydı yok (-1), kayıtta insert edilir
                val newIds = rowSongIds.toMutableList()
                newIds.add(insertIndex.coerceAtMost(newIds.size), -1L)
                rowSongIds = newIds

                hasUnsavedChanges = true
                showAddRowDialog = false
            }
        )
    }

    // Header Design Dialog
    if (showHeaderDesignDialog) {
        HeaderDesignDialog(
            headers = headers,
            onDismiss = { showHeaderDesignDialog = false },
            onHeadersUpdated = { newHeaders ->
                // Update headers while preserving existing data
                val oldHeaders = headers
                headers = newHeaders

                // Remap existing data to new headers
                listData = listData.map { row ->
                    val newRow = mutableMapOf<String, String>()
                    newHeaders.forEachIndexed { index, newHeader ->
                        // If index is within old headers range, use old data
                        val oldValue = if (index < oldHeaders.size) {
                            row[oldHeaders[index]] ?: ""
                        } else {
                            "" // New column, empty value
                        }
                        newRow[newHeader] = oldValue
                    }
                    newRow
                }

                hasUnsavedChanges = true
                showHeaderDesignDialog = false
            }
        )
    }
}

@Composable
fun AddColumnDialog(
    selectedColumnIndex: Int? = null,
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
                    if (selectedColumnIndex != null) {
                        stringResource(R.string.list_edit_new_column_before, ('A' + selectedColumnIndex).toString())
                    } else {
                        stringResource(R.string.list_edit_new_column_end)
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                
                OutlinedTextField(
                    value = columnName,
                    onValueChange = { columnName = it },
                    label = { Text(stringResource(R.string.list_edit_column_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel))
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
                        Text(stringResource(R.string.common_add))
                    }
                }
            }
        }
    }
}

@Composable
fun AddRowDialog(
    headers: List<String>,
    selectedRowIndex: Int? = null,
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
                    if (selectedRowIndex != null) {
                        stringResource(R.string.list_edit_new_row_after, selectedRowIndex + 1)
                    } else {
                        stringResource(R.string.list_edit_new_row_end)
                    },
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
                        Text(stringResource(R.string.common_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onAddRow(rowData) }
                    ) {
                        Text(stringResource(R.string.common_add))
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderDesignDialog(
    headers: List<String>,
    onDismiss: () -> Unit,
    onHeadersUpdated: (List<String>) -> Unit
) {
    var headerInputs by remember(headers) {
        mutableStateOf(headers.mapIndexed { index, header ->
            ('A' + index).toString() to header
        }.toMap())
    }

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
                    stringResource(R.string.list_edit_header_design_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    stringResource(R.string.list_edit_header_design_hint),
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(headers.size) { index ->
                        val columnLetter = ('A' + index).toString()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Column Letter
                            Surface(
                                modifier = Modifier.size(40.dp),
                                color = Color(0xFF616161),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = columnLetter,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Header Input
                            OutlinedTextField(
                                value = headerInputs[columnLetter] ?: "",
                                onValueChange = { newValue ->
                                    headerInputs = headerInputs + (columnLetter to newValue)
                                },
                                label = { Text(stringResource(R.string.list_edit_column_header_label, columnLetter)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text(stringResource(R.string.list_edit_header_placeholder)) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val newHeaders = (0 until headers.size).map { index ->
                                val columnLetter = ('A' + index).toString()
                                headerInputs[columnLetter]?.takeIf { it.isNotBlank() } ?: columnLetter
                            }
                            onHeadersUpdated(newHeaders)
                        }
                    ) {
                        Text(stringResource(R.string.list_edit_apply))
                    }
                }
            }
        }
    }
}