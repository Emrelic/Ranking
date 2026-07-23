package com.example.ranking.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.ui.viewmodel.NewTournamentViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTournamentScreen(
    onNavigateBack: () -> Unit,
    onTournamentCreated: (Long) -> Unit, // Navigate to RankingScreen with tournamentId
    viewModel: NewTournamentViewModel = viewModel()
) {
    val songLists by viewModel.songLists.collectAsState()
    val criterionLists by viewModel.criterionLists.collectAsState()
    
    var currentStep by remember { mutableStateOf(1) }
    var selectedSongList by remember { mutableStateOf<com.example.ranking.data.SongList?>(null) }
    var tournamentName by remember { mutableStateOf("") }
    var selectedSystemType by remember { mutableStateOf("SWISS") }
    var selectedCriterionList by remember { mutableStateOf<com.example.ranking.data.CriterionList?>(null) }
    
    // Criteria settings
    var useCriteria by remember { mutableStateOf(false) }
    var scoringType by remember { mutableStateOf("comparative") }
    var scoreScale by remember { mutableStateOf(10) }
    var drawThresholdMin by remember { mutableStateOf(51) }
    var drawThresholdMax by remember { mutableStateOf(51) }
    var autoWinnerFromCriteria by remember { mutableStateOf(false) }
    var autoOpenCriteriaPanel by remember { mutableStateOf(false) }
    var mandatoryCriteria by remember { mutableStateOf(false) }
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Auto-generate tournament name when song list, system type, or criteria changes
    LaunchedEffect(selectedSongList, selectedSystemType, useCriteria, selectedCriterionList) {
        if (selectedSongList != null) {
            val systemName = when (selectedSystemType) {
                "LEAGUE" -> "lig"
                "SWISS" -> "isviçre"
                "EMRE_CORRECT" -> "geliştirilmiş isviçre"
                "MERGE_SORT" -> "ikili karşılaştırma"
                "DIRECT_SCORING" -> "direkt puanlama"
                "ELIMINATION" -> "eleme"
                "FULL_ELIMINATION" -> "gruplu eleme"
                else -> "turnuva"
            }

            val dateTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())

            val criteriaText = if (useCriteria && selectedCriterionList != null) {
                " - ${selectedCriterionList!!.name}"
            } else ""

            tournamentName = "${selectedSongList!!.name} - $systemName$criteriaText - $dateTime"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // SABİT TOP BAR
            TopAppBar(
                title = { Text("Yeni Turnuva") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
            
            // SCROLL YAPILABIL ORTA ALAN
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Mevcut alanın tamamını kullan, bottom butonlara yer bırak
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()) // SCROLL ÖZELLİĞİ
                        .padding(16.dp)
                ) {
                    // Progress indicator
                    LinearProgressIndicator(
                        progress = currentStep / 5f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Error message
                    if (errorMessage != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text(
                                text = errorMessage!!,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    when (currentStep) {
                        1 -> SongListSelectionStep(
                            songLists = songLists,
                            selectedSongList = selectedSongList,
                            onSongListSelected = { selectedSongList = it }
                        )
                        2 -> SystemTypeSelectionStep(
                            selectedSystemType = selectedSystemType,
                            onSystemTypeSelected = { selectedSystemType = it }
                        )
                        3 -> CriteriaSelectionStep(
                            criterionLists = criterionLists,
                            useCriteria = useCriteria,
                            selectedCriterionList = selectedCriterionList,
                            onUseCriteriaChanged = { useCriteria = it },
                            onCriterionListSelected = { selectedCriterionList = it }
                        )
                        4 -> CriteriaSettingsStep(
                            enabled = useCriteria,
                            scoringType = scoringType,
                            scoreScale = scoreScale,
                            drawThresholdMin = drawThresholdMin,
                            drawThresholdMax = drawThresholdMax,
                            autoWinnerFromCriteria = autoWinnerFromCriteria,
                            autoOpenCriteriaPanel = autoOpenCriteriaPanel,
                            mandatoryCriteria = mandatoryCriteria,
                            onScoringTypeChanged = { scoringType = it },
                            onScoreScaleChanged = { scoreScale = it },
                            onDrawThresholdMinChanged = { drawThresholdMin = it },
                            onDrawThresholdMaxChanged = { drawThresholdMax = it },
                            onAutoWinnerFromCriteriaChanged = { autoWinnerFromCriteria = it },
                            onAutoOpenCriteriaPanelChanged = { autoOpenCriteriaPanel = it },
                            onMandatoryCriteriaChanged = { mandatoryCriteria = it }
                        )
                        5 -> TournamentNameStep(
                            tournamentName = tournamentName,
                            onNameChanged = { tournamentName = it }
                        )
                    }
                    
                    // BOTTOM PADDİNG - BUTONLAR İÇİN ALAN BIRAK
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }

        // SABİT BOTTOM NAVİGASYON BUTONLARI
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // GERİ BUTONU - SOL ALT KÖŞE
                if (currentStep > 1) {
                    Button(
                        onClick = { currentStep-- },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Geri")
                    }
                } else {
                    Spacer(modifier = Modifier.width(80.dp)) // PLACEHOLDER
                }
                
                // İLERİ/BAŞLAT BUTONU - SAĞ ALT KÖŞE
                if (currentStep < 5) {
                    Button(
                        onClick = { currentStep++ },
                        enabled = when (currentStep) {
                            1 -> selectedSongList != null
                            2 -> true
                            3 -> !useCriteria || selectedCriterionList != null
                            4 -> true
                            else -> false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("İleri")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = "İleri", modifier = Modifier.size(18.dp))
                    }
                } else {
                    Button(
                        onClick = {
                            if (selectedSongList != null) {
                                isLoading = true
                                viewModel.createTournament(
                                    songList = selectedSongList!!,
                                    name = tournamentName,
                                    systemType = selectedSystemType,
                                    criterionList = if (useCriteria) selectedCriterionList else null,
                                    criteriaSettings = if (useCriteria) {
                                        mapOf(
                                            "scoringType" to scoringType,
                                            "scoreScale" to scoreScale,
                                            "drawThresholdPercent" to listOf(drawThresholdMin, drawThresholdMax),
                                            "autoWinnerFromCriteria" to autoWinnerFromCriteria,
                                            "autoOpenCriteriaPanel" to autoOpenCriteriaPanel,
                                            "mandatoryCriteria" to mandatoryCriteria
                                        )
                                    } else null,
                                    onSuccess = { tournamentId ->
                                        isLoading = false
                                        onTournamentCreated(tournamentId)
                                    },
                                    onError = { error ->
                                        isLoading = false
                                        errorMessage = error
                                    }
                                )
                            }
                        },
                        enabled = !isLoading && tournamentName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Oluşturuluyor...")
                        } else {
                            Text("Başlat")
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.Check, contentDescription = "Başlat", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongListSelectionStep(
    songLists: List<com.example.ranking.data.SongList>,
    selectedSongList: com.example.ranking.data.SongList?,
    onSongListSelected: (com.example.ranking.data.SongList) -> Unit
) {
    Column {
        Text(
            text = "Liste Seçin",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        LazyColumn(
            modifier = Modifier.heightIn(max = 400.dp), // MAX HEIGHT - SCROLL İÇİN
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(songLists) { songList ->
                Card(
                    onClick = { onSongListSelected(songList) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedSongList == songList) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = songList.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${songList.songCount} öğe",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TournamentNameStep(
    tournamentName: String,
    onNameChanged: (String) -> Unit
) {
    Column {
        Text(
            text = "Turnuva Adı",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        OutlinedTextField(
            value = tournamentName,
            onValueChange = onNameChanged,
            label = { Text("Turnuva adı girin") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
private fun SystemTypeSelectionStep(
    selectedSystemType: String,
    onSystemTypeSelected: (String) -> Unit
) {
    val systemTypes = listOf(
        "DIRECT_SCORING" to "Direkt Puanlama - 0-100 arası puan verme",
        "MERGE_SORT" to "İkili Karşılaştırma - En az soruyla tam sıralama (büyük listeler için önerilen)",
        "LEAGUE" to "Lig Sistemi - Herkes herkesle eşleşir",
        "SWISS" to "İsviçre Sistemi - Puana göre eşleştirme",
        "EMRE_CORRECT" to "Geliştirilmiş İsviçre Sistemi (Önerilen)",
        "ELIMINATION" to "Eleme Usulü - Çift eleme sistemi",
        "FULL_ELIMINATION" to "Gruplu Eleme Usulü - Grup eleme"
        // SINGLE_ELIMINATION ve DOUBLE_ELIMINATION algoritmaları tamamlanana
        // kadar listeden çıkarıldı (yarım özellik kırık deneyim yaratıyordu)
    )
    
    Column {
        Text(
            text = "Turnuva Sistemi",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        LazyColumn(
            modifier = Modifier.heightIn(max = 400.dp), // MAX HEIGHT - SCROLL İÇİN
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(systemTypes) { (type, description) ->
                Card(
                    onClick = { onSystemTypeSelected(type) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedSystemType == type) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        val parts = description.split(" - ")
                        Text(
                            text = parts[0],
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (parts.size > 1) {
                            Text(
                                text = parts[1],
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CriteriaSelectionStep(
    criterionLists: List<com.example.ranking.data.CriterionList>,
    useCriteria: Boolean,
    selectedCriterionList: com.example.ranking.data.CriterionList?,
    onUseCriteriaChanged: (Boolean) -> Unit,
    onCriterionListSelected: (com.example.ranking.data.CriterionList) -> Unit
) {
    Column {
        Text(
            text = "Kriter Sistemi",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = useCriteria,
                    onCheckedChange = onUseCriteriaChanged
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Kriter sistemi kullan",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        
        AnimatedVisibility(visible = useCriteria) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp), // MAX HEIGHT - SCROLL İÇİN
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(criterionLists) { criterionList ->
                        Card(
                            onClick = { onCriterionListSelected(criterionList) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedCriterionList == criterionList) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = criterionList.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                val criteria = try {
                                    Gson().fromJson<List<String>>(
                                        criterionList.criteria,
                                        object : TypeToken<List<String>>() {}.type
                                    ) ?: emptyList()
                                } catch (e: Exception) { emptyList() }
                                
                                Text(
                                    text = "${criteria.size} kriter",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CriteriaSettingsStep(
    enabled: Boolean,
    scoringType: String,
    scoreScale: Int,
    drawThresholdMin: Int,
    drawThresholdMax: Int,
    autoWinnerFromCriteria: Boolean,
    autoOpenCriteriaPanel: Boolean,
    mandatoryCriteria: Boolean,
    onScoringTypeChanged: (String) -> Unit,
    onScoreScaleChanged: (Int) -> Unit,
    onDrawThresholdMinChanged: (Int) -> Unit,
    onDrawThresholdMaxChanged: (Int) -> Unit,
    onAutoWinnerFromCriteriaChanged: (Boolean) -> Unit,
    onAutoOpenCriteriaPanelChanged: (Boolean) -> Unit,
    onMandatoryCriteriaChanged: (Boolean) -> Unit
) {
    Column {
        Text(
            text = "Kriter Ayarları",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        if (!enabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = "Kriter sistemi devre dışı",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }
        
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Scoring Type
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Puanlama Türü",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = scoringType == "comparative",
                                    onClick = { onScoringTypeChanged("comparative") }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = scoringType == "comparative",
                                onClick = { onScoringTypeChanged("comparative") }
                            )
                            Text("Kıyaslamalı (10 puanı böl)")
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = scoringType == "separate",
                                    onClick = { onScoringTypeChanged("separate") }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = scoringType == "separate",
                                onClick = { onScoringTypeChanged("separate") }
                            )
                            Text("Ayrı ayrı (her takıma puan)")
                        }
                    }
                }
            }
            
            // Score Scale
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Puan Skalası: $scoreScale",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = scoreScale.toFloat(),
                        onValueChange = { onScoreScaleChanged(it.toInt()) },
                        valueRange = 5f..20f,
                        steps = 14
                    )
                }
            }
            
            // Draw Threshold
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Beraberlik Eşiği: %$drawThresholdMin - %$drawThresholdMax",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Min %")
                            Slider(
                                value = drawThresholdMin.toFloat(),
                                onValueChange = { onDrawThresholdMinChanged(it.toInt()) },
                                valueRange = 45f..55f
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Max %")
                            Slider(
                                value = drawThresholdMax.toFloat(),
                                onValueChange = { onDrawThresholdMaxChanged(it.toInt()) },
                                valueRange = 45f..55f
                            )
                        }
                    }
                }
            }
            
            // Boolean Settings
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Ek Ayarlar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = autoWinnerFromCriteria,
                            onCheckedChange = onAutoWinnerFromCriteriaChanged
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kriterlerden otomatik kazanan belirle")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = autoOpenCriteriaPanel,
                            onCheckedChange = onAutoOpenCriteriaPanelChanged
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kriter panelini otomatik aç")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = mandatoryCriteria,
                            onCheckedChange = onMandatoryCriteriaChanged
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kriter değerlendirmesi zorunlu")
                    }
                }
            }
        }
    }
}