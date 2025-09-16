package com.example.ranking.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
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
    var drawThresholdMin by remember { mutableStateOf(50) }
    var drawThresholdMax by remember { mutableStateOf(50) }
    var autoWinnerFromCriteria by remember { mutableStateOf(false) }
    var autoOpenCriteriaPanel by remember { mutableStateOf(false) }
    var mandatoryCriteria by remember { mutableStateOf(false) }
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Generate tournament name automatically
    LaunchedEffect(selectedSongList, selectedSystemType, selectedCriterionList) {
        if (selectedSongList != null) {
            val systemName = when (selectedSystemType) {
                "DIRECT_SCORING" -> "Direkt Puanlama"
                "LEAGUE" -> "Lig"
                "ELIMINATION" -> "Ön Eleme"
                "FULL_ELIMINATION" -> "Tam Eleme"
                "SWISS" -> "İsviçre"
                "EMRE_CORRECT" -> "Geliştirilmiş İsviçre"
                else -> selectedSystemType
            }
            val date = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                .format(java.util.Date())
            val criteriaText = if (useCriteria && selectedCriterionList != null) {
                val criteria = try {
                    Gson().fromJson<List<String>>(
                        selectedCriterionList!!.criteria,
                        object : TypeToken<List<String>>() {}.type
                    ) ?: emptyList()
                } catch (e: Exception) { emptyList() }
                ". ${criteria.size} kriter listeli"
            } else ""
            
            tournamentName = "${selectedSongList!!.name} $systemName $date turnuva$criteriaText"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("Yeni Turnuva") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                }
            },
            actions = {
                if (currentStep > 1) {
                    TextButton(onClick = { currentStep-- }) {
                        Text("Geri")
                    }
                }
                if (currentStep < 5) {
                    TextButton(
                        onClick = { currentStep++ },
                        enabled = when (currentStep) {
                            1 -> selectedSongList != null
                            2 -> tournamentName.isNotBlank()
                            3 -> true
                            4 -> !useCriteria || selectedCriterionList != null
                            else -> false
                        }
                    ) {
                        Text("İleri")
                    }
                } else {
                    TextButton(
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
                        enabled = !isLoading && selectedSongList != null && tournamentName.isNotBlank()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text("Turnuva Başlat")
                        }
                    }
                }
            }
        )

        // Progress indicator
        LinearProgressIndicator(
            progress = currentStep / 5f,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Error message
        if (errorMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = errorMessage!!,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Step content
        when (currentStep) {
            1 -> SongListSelectionStep(
                songLists = songLists,
                selectedSongList = selectedSongList,
                onSongListSelected = { selectedSongList = it }
            )
            2 -> TournamentNameStep(
                tournamentName = tournamentName,
                onNameChanged = { tournamentName = it }
            )
            3 -> SystemTypeSelectionStep(
                selectedSystemType = selectedSystemType,
                onSystemTypeSelected = { selectedSystemType = it }
            )
            4 -> CriteriaSelectionStep(
                criterionLists = criterionLists,
                useCriteria = useCriteria,
                selectedCriterionList = selectedCriterionList,
                onUseCriteriaChanged = { useCriteria = it },
                onCriterionListSelected = { selectedCriterionList = it }
            )
            5 -> CriteriaSettingsStep(
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
        }
    }
}

@Composable
private fun SongListSelectionStep(
    songLists: List<com.example.ranking.data.SongList>,
    selectedSongList: com.example.ranking.data.SongList?,
    onSongListSelected: (com.example.ranking.data.SongList) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "1. Liste Seçin",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Turnuvaya katılacak öğelerin bulunduğu listeyi seçin",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (songLists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Henüz liste oluşturulmamış",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn {
                items(songLists) { songList ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .selectable(
                                selected = selectedSongList?.id == songList.id,
                                onClick = { onSongListSelected(songList) }
                            ),
                        colors = if (selectedSongList?.id == songList.id) {
                            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        } else {
                            CardDefaults.cardColors()
                        }
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
}

@Composable
private fun TournamentNameStep(
    tournamentName: String,
    onNameChanged: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "2. Turnuva Adı",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Turnuvanızın adını düzenleyebilir veya otomatik oluşturulan adı kullanabilirsiniz",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = tournamentName,
            onValueChange = onNameChanged,
            label = { Text("Turnuva Adı") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 3
        )
    }
}

@Composable
private fun SystemTypeSelectionStep(
    selectedSystemType: String,
    onSystemTypeSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "3. Turnuva Sistemi",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Turnuvanızda kullanılacak eşleştirme sistemini seçin",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        val systems = listOf(
            "DIRECT_SCORING" to Pair("Direkt Puanlama", "Her öğeye 0-100 arası puan verin"),
            "LEAGUE" to Pair("Lig Sistemi", "Öğeler birbiri ile eşleşir, kazanan 2 puan alır"),
            "ELIMINATION" to Pair("Ön Eleme + Gruplu Eleme", "Önce gruplar, sonra elemeli turnuva"),
            "FULL_ELIMINATION" to Pair("Tam Eleme Sistemi", "Tamamı elemeli turnuva sistemi"),
            "SWISS" to Pair("İsviçre Sistemi", "Eşit puanlı rakiplerle eşleşme sistemi"),
            "EMRE_CORRECT" to Pair("Geliştirilmiş İsviçre Sistemi", "Puan bazlı eşleştirme ile adil sıralama - İlk tur eşleştirme seçeneği")
        )

        systems.forEach { (value, info) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .selectable(
                        selected = selectedSystemType == value,
                        onClick = { onSystemTypeSelected(value) }
                    ),
                colors = if (selectedSystemType == value) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                } else {
                    CardDefaults.cardColors()
                }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = info.first,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = info.second,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "4. Kriter Sistemi (Opsiyonel)",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Değerlendirme kriterleri kullanmak isterseniz bir kriter listesi seçin",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = useCriteria,
                onCheckedChange = onUseCriteriaChanged
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Kriter sistemi kullan")
        }

        AnimatedVisibility(visible = useCriteria) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))

                if (criterionLists.isEmpty()) {
                    Card {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Henüz kriter listesi oluşturulmamış",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.height(300.dp)
                    ) {
                        items(criterionLists) { criterionList ->
                            val criteria = try {
                                Gson().fromJson<List<String>>(
                                    criterionList.criteria,
                                    object : TypeToken<List<String>>() {}.type
                                ) ?: emptyList()
                            } catch (e: Exception) {
                                emptyList<String>()
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .selectable(
                                        selected = selectedCriterionList?.id == criterionList.id,
                                        onClick = { onCriterionListSelected(criterionList) }
                                    ),
                                colors = if (selectedCriterionList?.id == criterionList.id) {
                                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                } else {
                                    CardDefaults.cardColors()
                                }
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = criterionList.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${criteria.size} kriter",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (criteria.isNotEmpty()) {
                                        Text(
                                            text = criteria.take(3).joinToString(", ") + if (criteria.size > 3) "..." else "",
                                            style = MaterialTheme.typography.bodySmall,
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "5. Kriter Ayarları",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (enabled) "Kriter sisteminin detaylı ayarlarını yapın" else "Kriter sistemi kullanılmıyor",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (!enabled) {
            Card {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Kriter sistemi devre dışı",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Scoring Type
            Text(
                text = "Puanlama Sistemi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Row {
                FilterChip(
                    onClick = { onScoringTypeChanged("separate") },
                    label = { Text("Ayrı Ayrı") },
                    selected = scoringType == "separate",
                    modifier = Modifier.padding(end = 8.dp)
                )
                FilterChip(
                    onClick = { onScoringTypeChanged("comparative") },
                    label = { Text("Kıyaslamalı") },
                    selected = scoringType == "comparative"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Score Scale
            Text(
                text = "Puan Skalası",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Preset scale options
            val presetScales = listOf(1, 2, 3, 4, 5, 6, 7, 10, 20, 100)
            var customScale by remember { mutableStateOf("") }
            var useCustomScale by remember { mutableStateOf(false) }

            // Update useCustomScale based on current scoreScale
            LaunchedEffect(scoreScale) {
                useCustomScale = scoreScale !in presetScales
                if (useCustomScale) {
                    customScale = scoreScale.toString()
                }
            }

            // Preset buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetScales.chunked(5).forEach { chunk ->
                    Column {
                        chunk.forEach { scale ->
                            FilterChip(
                                onClick = {
                                    onScoreScaleChanged(scale)
                                    useCustomScale = false
                                },
                                label = { Text(scale.toString()) },
                                selected = scoreScale == scale && !useCustomScale,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Custom scale input
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    onClick = {
                        useCustomScale = !useCustomScale
                        if (useCustomScale && customScale.isBlank()) {
                            customScale = scoreScale.toString()
                        } else if (!useCustomScale) {
                            // Select closest preset
                            val closest = presetScales.minByOrNull { kotlin.math.abs(it - scoreScale) } ?: 10
                            onScoreScaleChanged(closest)
                        }
                    },
                    label = { Text("Başka") },
                    selected = useCustomScale,
                    modifier = Modifier.padding(end = 8.dp)
                )

                if (useCustomScale) {
                    OutlinedTextField(
                        value = customScale,
                        onValueChange = { newValue ->
                            customScale = newValue
                            val intValue = newValue.toIntOrNull()
                            if (intValue != null && intValue in 1..999) {
                                onScoreScaleChanged(intValue)
                            }
                        },
                        label = { Text("Özel Skala") },
                        modifier = Modifier.width(120.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        )
                    )
                }
            }

            Text(
                text = "Seçilen: $scoreScale",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Draw Threshold
            Text(
                text = "Beraberlik Aralığı",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Eğer takımlar %$drawThresholdMin-%$drawThresholdMax arasında puan alırsa berabere sayılır",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column {
                // Main slider for position (where the center of the range is)
                Text(
                    text = "Merkez Pozisyon: %${(drawThresholdMin + drawThresholdMax) / 2}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = ((drawThresholdMin + drawThresholdMax) / 2).toFloat(),
                    onValueChange = { centerValue ->
                        val center = centerValue.toInt()
                        val halfRange = (drawThresholdMax - drawThresholdMin) / 2
                        val newMin = (center - halfRange).coerceIn(0, 100 - halfRange * 2)
                        val newMax = (center + halfRange).coerceIn(halfRange * 2, 100)
                        onDrawThresholdMinChanged(newMin)
                        onDrawThresholdMaxChanged(newMax)
                    },
                    valueRange = 0f..100f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Range width slider
                Text(
                    text = "Aralık Genişliği: ${drawThresholdMax - drawThresholdMin} puan",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = (drawThresholdMax - drawThresholdMin).toFloat(),
                    onValueChange = { rangeWidth ->
                        val range = rangeWidth.toInt()
                        val center = (drawThresholdMin + drawThresholdMax) / 2
                        val halfRange = range / 2
                        val newMin = (center - halfRange).coerceIn(0, 100 - range)
                        val newMax = (center + halfRange).coerceIn(range, 100)
                        onDrawThresholdMinChanged(newMin)
                        onDrawThresholdMaxChanged(newMax)
                    },
                    valueRange = 0f..50f, // Maximum 50 point range
                    modifier = Modifier.fillMaxWidth()
                )

                // Visual representation
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Örnek Sonuçlar:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "• ${drawThresholdMin - 1}%-${drawThresholdMax + 1}% → İlk takım galip",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• $drawThresholdMin%-$drawThresholdMax% → Beraberlik",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "• ${drawThresholdMax + 1}%-${drawThresholdMin - 1}% → İkinci takım galip",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Quick presets
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val presets = listOf(
                        "50-50" to Pair(50, 50),
                        "45-55" to Pair(45, 55),
                        "40-60" to Pair(40, 60),
                        "30-70" to Pair(30, 70)
                    )

                    presets.forEach { (label, range) ->
                        FilterChip(
                            onClick = {
                                onDrawThresholdMinChanged(range.first)
                                onDrawThresholdMaxChanged(range.second)
                            },
                            label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                            selected = drawThresholdMin == range.first && drawThresholdMax == range.second
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Options
            SettingsSwitch(
                title = "Galibi kriter puanına göre belirle",
                description = "Fazla puan alan otomatik galip olur",
                checked = autoWinnerFromCriteria,
                onCheckedChange = onAutoWinnerFromCriteriaChanged
            )

            SettingsSwitch(
                title = "Kriter paneli otomatik açılsın",
                description = "Her maçta kriter paneli otomatik açılır",
                checked = autoOpenCriteriaPanel,
                onCheckedChange = onAutoOpenCriteriaPanelChanged
            )

            SettingsSwitch(
                title = "Kriter oylaması mecburi",
                description = "Tüm kriterler doldurulmadan maç tamamlanamaz",
                checked = mandatoryCriteria,
                onCheckedChange = onMandatoryCriteriaChanged
            )
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}