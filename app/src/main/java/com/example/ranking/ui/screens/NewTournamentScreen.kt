package com.example.ranking.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.R
import com.example.ranking.ui.viewmodel.NewTournamentViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTournamentScreen(
    onNavigateBack: () -> Unit,
    onTournamentCreated: (Long) -> Unit, // Navigate to RankingScreen with tournamentId
    // Sihirbaz kısayolları: süreçten çıkmadan yeni liste / kriter listesi oluşturma
    onNavigateToCreateList: () -> Unit = {},
    onNavigateToCreateCriteria: () -> Unit = {},
    // Kısayoldan dönüşte yeni oluşturulan kaydın id'si (otomatik seçim için)
    newlyCreatedListId: Long? = null,
    newlyCreatedCriteriaId: Long? = null,
    onNewListConsumed: () -> Unit = {},
    onNewCriteriaConsumed: () -> Unit = {},
    viewModel: NewTournamentViewModel = viewModel()
) {
    val songLists by viewModel.songLists.collectAsState()
    val criterionLists by viewModel.criterionLists.collectAsState()

    // rememberSaveable: ekran döndürme / process death sihirbaz ilerlemesini sıfırlamasın
    var currentStep by rememberSaveable { mutableStateOf(1) }
    var selectedSongList by remember { mutableStateOf<com.example.ranking.data.SongList?>(null) }
    var tournamentName by rememberSaveable { mutableStateOf("") }
    var selectedSystemType by rememberSaveable { mutableStateOf("MERGE_SORT") }
    var selectedCriterionList by remember { mutableStateOf<com.example.ranking.data.CriterionList?>(null) }

    // Seçim nesneleri Saveable değil; kısayola gidip dönünce veya ekran
    // döndürülünce kaybolmasınlar diye id'leri saklanıp geri yüklenir
    var selectedSongListId by rememberSaveable { mutableStateOf(-1L) }
    var selectedCriterionListId by rememberSaveable { mutableStateOf(-1L) }
    LaunchedEffect(songLists) {
        if (selectedSongList == null && selectedSongListId > 0) {
            selectedSongList = songLists.firstOrNull { it.id == selectedSongListId }
        }
    }
    LaunchedEffect(criterionLists) {
        if (selectedCriterionList == null && selectedCriterionListId > 0) {
            selectedCriterionList = criterionLists.firstOrNull { it.id == selectedCriterionListId }
        }
    }

    // Kısayoldan dönüş: yeni oluşturulan kayıt yüklenince otomatik seç
    LaunchedEffect(newlyCreatedListId, songLists) {
        if (newlyCreatedListId != null) {
            songLists.firstOrNull { it.id == newlyCreatedListId }?.let {
                selectedSongList = it
                selectedSongListId = it.id
                onNewListConsumed()
            }
        }
    }
    LaunchedEffect(newlyCreatedCriteriaId, criterionLists) {
        if (newlyCreatedCriteriaId != null) {
            criterionLists.firstOrNull { it.id == newlyCreatedCriteriaId }?.let {
                selectedCriterionList = it
                selectedCriterionListId = it.id
                onNewCriteriaConsumed()
            }
        }
    }

    // Criteria settings
    var useCriteria by rememberSaveable { mutableStateOf(false) }
    var scoringType by rememberSaveable { mutableStateOf("comparative") }
    var scoreScale by rememberSaveable { mutableStateOf(10) }
    // Beraberlik bandı simetriktir: min = 100 - max (50-50 = band kapalı)
    var drawThresholdMin by rememberSaveable { mutableStateOf(50) }
    var drawThresholdMax by rememberSaveable { mutableStateOf(50) }
    var autoWinnerFromCriteria by rememberSaveable { mutableStateOf(false) }
    var autoOpenCriteriaPanel by rememberSaveable { mutableStateOf(false) }
    var mandatoryCriteria by rememberSaveable { mutableStateOf(false) }
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Auto-generate tournament name when song list, system type, or criteria changes
    LaunchedEffect(selectedSongList, selectedSystemType, useCriteria, selectedCriterionList) {
        if (selectedSongList != null) {
            val systemName = when (selectedSystemType) {
                "LEAGUE" -> "lig"
                "SWISS" -> "isviçre"
                "EMRE_CORRECT" -> "geliştirilmiş isviçre"
                "HIBRIT" -> "hibrit isviçre"
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
                title = { Text(stringResource(R.string.new_tournament_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
                            onSongListSelected = {
                                selectedSongList = it
                                selectedSongListId = it.id
                            },
                            onCreateNewList = onNavigateToCreateList
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
                            onCriterionListSelected = {
                                selectedCriterionList = it
                                selectedCriterionListId = it.id
                            },
                            onCreateNewCriteria = onNavigateToCreateCriteria
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.common_back))
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
                        Text(stringResource(R.string.new_tournament_forward))
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = stringResource(R.string.new_tournament_forward), modifier = Modifier.size(18.dp))
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
                            Text(stringResource(R.string.new_tournament_creating))
                        } else {
                            Text(stringResource(R.string.new_tournament_start))
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.new_tournament_start), modifier = Modifier.size(18.dp))
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
    onSongListSelected: (com.example.ranking.data.SongList) -> Unit,
    onCreateNewList: () -> Unit = {}
) {
    Column {
        Text(
            text = stringResource(R.string.new_tournament_select_list),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Kısayol: süreçten çıkmadan yeni liste oluştur (dönüşte otomatik seçilir)
        OutlinedButton(
            onClick = onCreateNewList,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.new_tournament_create_new_list))
        }

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
                            text = stringResource(R.string.common_item_count, songList.songCount),
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
            text = stringResource(R.string.new_tournament_name_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        OutlinedTextField(
            value = tournamentName,
            onValueChange = onNameChanged,
            label = { Text(stringResource(R.string.new_tournament_name_label)) },
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
    // Yarım/kırık sistemler tamamlanana kadar listeden çıkarıldı
    // (yarım özellik kırık deneyim yaratıyordu):
    // - SINGLE_ELIMINATION, DOUBLE_ELIMINATION: algoritma tamamlanmadı
    // - ELIMINATION, FULL_ELIMINATION: puanlama ekranı stub, grup dağılımı
    //   deterministik değil; yeni EliminationSystem motoru bitince açılacak
    //
    // SWISS 2026-08-28'de GERİ AÇILDI: yeni SwissSystem motoru yazıldı.
    // Eski yolda bye yoktu (tek takım sessizce turdan düşüyordu), tekrar
    // eşleşme serbestti ve matchNumber atanmıyordu. Yeni motorda tekrarsız
    // tam eşleştirme geri izlemeyle garanti ediliyor, bye adil rotasyonla
    // dağıtılıyor. 59 test (14 kendi + 43 çapraz + 2 gerileme) geçiyor.
    val systemTypes = listOf(
        "MERGE_SORT",
        "HIBRIT",
        "EMRE_CORRECT",
        "SWISS",
        "LEAGUE",
        "DIRECT_SCORING"
    )

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.new_tournament_system_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = stringResource(R.string.new_tournament_system_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(systemTypes) { index, type ->
                SystemTypeCard(
                    orderNumber = index + 1,
                    type = type,
                    isSelected = selectedSystemType == type,
                    onClick = { onSystemTypeSelected(type) }
                )
            }
        }
    }
}

@Composable
private fun SystemTypeCard(
    orderNumber: Int,
    type: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val isRecommended = type in setOf("MERGE_SORT", "HIBRIT", "EMRE_CORRECT")

    val title = stringResource(
        when (type) {
            "MERGE_SORT" -> R.string.new_tournament_system_merge_sort_title
            "HIBRIT" -> R.string.new_tournament_system_hibrit_title
            "EMRE_CORRECT" -> R.string.new_tournament_system_emre_title
            "SWISS" -> R.string.new_tournament_system_swiss_title
            "LEAGUE" -> R.string.new_tournament_system_league_title
            "DIRECT_SCORING" -> R.string.new_tournament_system_direct_scoring_title
            "ELIMINATION" -> R.string.new_tournament_system_elimination_title
            else -> R.string.new_tournament_system_full_elimination_title
        }
    )
    val description = stringResource(
        when (type) {
            "MERGE_SORT" -> R.string.new_tournament_system_merge_sort
            "HIBRIT" -> R.string.new_tournament_system_hibrit
            "EMRE_CORRECT" -> R.string.new_tournament_system_emre
            "SWISS" -> R.string.new_tournament_system_swiss
            "LEAGUE" -> R.string.new_tournament_system_league
            "DIRECT_SCORING" -> R.string.new_tournament_system_direct_scoring
            "ELIMINATION" -> R.string.new_tournament_system_elimination
            else -> R.string.new_tournament_system_full_elimination
        }
    )
    val detailInfo = stringResource(
        when (type) {
            "MERGE_SORT" -> R.string.new_tournament_system_merge_sort_detail
            "HIBRIT" -> R.string.new_tournament_system_hibrit_detail
            "EMRE_CORRECT" -> R.string.new_tournament_system_emre_detail
            "SWISS" -> R.string.new_tournament_system_swiss_detail
            "LEAGUE" -> R.string.new_tournament_system_league_detail
            "DIRECT_SCORING" -> R.string.new_tournament_system_direct_scoring_detail
            "ELIMINATION" -> R.string.new_tournament_system_elimination_detail
            else -> R.string.new_tournament_system_full_elimination_detail
        }
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected)
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                else
                    Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            else
                MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 2.dp)
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
                        text = "$orderNumber. $title",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )

                    if (isRecommended) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.new_tournament_recommended_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.new_tournament_detail_toggle),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.new_tournament_selected),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 1.4.em
            )

            if (isExpanded && detailInfo.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = detailInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 1.5.em,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
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
    onCriterionListSelected: (com.example.ranking.data.CriterionList) -> Unit,
    onCreateNewCriteria: () -> Unit = {}
) {
    Column {
        Text(
            text = stringResource(R.string.new_tournament_criteria_title),
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
                    text = stringResource(R.string.new_tournament_use_criteria),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        
        AnimatedVisibility(visible = useCriteria) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))

                // Kısayol: süreçten çıkmadan yeni kriter listesi oluştur
                // (dönüşte otomatik seçilir)
                OutlinedButton(
                    onClick = onCreateNewCriteria,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.new_tournament_create_new_criteria))
                }

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
                                    text = stringResource(R.string.new_tournament_criterion_count, criteria.size),
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
            text = stringResource(R.string.new_tournament_criteria_settings_title),
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
                    text = stringResource(R.string.new_tournament_criteria_disabled),
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
                        text = stringResource(R.string.new_tournament_scoring_type),
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
                            Text(stringResource(R.string.new_tournament_scoring_comparative))
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
                            Text(stringResource(R.string.new_tournament_scoring_separate))
                        }
                    }
                }
            }
            
            // Score Scale
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.new_tournament_score_scale, scoreScale),
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
            
            // Draw Threshold - TEK simetrik slider: 50-50 / 40-60 / 30-70 / 20-80
            // (Eski tasarım iki ayrı min-max slider'dı ve 45-55 aralığına
            // kilitliydi; 40-60 gibi bantlar hiç ayarlanamıyordu)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.new_tournament_draw_threshold, drawThresholdMin, drawThresholdMax),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.new_tournament_draw_threshold_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Slider(
                        value = drawThresholdMax.toFloat().coerceIn(50f, 80f),
                        onValueChange = {
                            val upper = it.toInt()
                            onDrawThresholdMaxChanged(upper)
                            onDrawThresholdMinChanged(100 - upper)
                        },
                        valueRange = 50f..80f,
                        steps = 29
                    )
                }
            }
            
            // Boolean Settings
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.new_tournament_extra_settings),
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
                        Text(stringResource(R.string.new_tournament_auto_winner))
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
                        Text(stringResource(R.string.new_tournament_auto_open_panel))
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
                        Text(stringResource(R.string.new_tournament_mandatory_criteria))
                    }
                }
            }
        }
    }
}