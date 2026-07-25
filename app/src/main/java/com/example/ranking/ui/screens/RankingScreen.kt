package com.example.ranking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.R
import com.example.ranking.data.Song
import com.example.ranking.ui.screens.ranking.CriteriaEvaluationDialog
import com.example.ranking.ui.screens.ranking.HIDDEN_CSV_KEYS
import com.example.ranking.ui.screens.ranking.ItemImage
import com.example.ranking.ui.screens.ranking.extractImageUrl
import com.example.ranking.ui.screens.ranking.MatchingsListContent
import com.example.ranking.ui.screens.ranking.methodTitle
import com.example.ranking.ui.screens.ranking.ScoreInputDialog
import com.example.ranking.ui.screens.ranking.StandingsDialog
import com.example.ranking.ui.screens.ranking.TeamSelectionPanel
import com.example.ranking.ui.viewmodel.RankingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(
    listId: Long,
    method: String,
    pairingMethodName: String = "SEQUENTIAL",
    isResuming: Boolean = false, // DEVAM EDEN TURNUVA: false = yeni, true = devam eden
    onNavigateBack: () -> Unit,
    onNavigateToResults: (Long, String) -> Unit,
    viewModel: RankingViewModel = viewModel()
) {
    LaunchedEffect(listId, method, pairingMethodName, isResuming) {
        // YENİ TURNUVA: Eski verileri temizle, DEVAM EDEN: Mevcut verileri koru
        viewModel.initializeRanking(listId, method, pairingMethodName, forceNew = !isResuming)
    }

    val uiState by viewModel.uiState.collectAsState()
    var showCriteriaDialog by remember { mutableStateOf(false) }
    var showStandingsDialog by remember { mutableStateOf(false) }
    var showScoreDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    // Turnuvanın kriter ayarları (panel otomatik açılış + zorunlu kriter için)
    var criteriaSettingsMap by remember { mutableStateOf<Map<String, Any>?>(null) }
    LaunchedEffect(uiState.activeTournamentId) {
        criteriaSettingsMap = uiState.activeTournamentId
            ?.let { viewModel.getCriteriaSettingsForTournament(it) }
    }
    val autoOpenCriteriaPanel = criteriaSettingsMap?.get("autoOpenCriteriaPanel") as? Boolean ?: false
    val mandatoryCriteria = criteriaSettingsMap?.get("mandatoryCriteria") as? Boolean ?: false

    // "Kriter panelini otomatik aç": yeni maç ekrana gelince dialog kendiliğinden açılır
    LaunchedEffect(uiState.currentMatch?.id, autoOpenCriteriaPanel) {
        if (autoOpenCriteriaPanel && uiState.currentMatch != null && !uiState.isComplete &&
            method in listOf("LEAGUE", "SWISS", "EMRE_CORRECT")
        ) {
            showCriteriaDialog = true
        }
    }

    // Sıfırlama onayı - tek dokunuşla turnuva silinmesini engeller
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text(stringResource(R.string.ranking_reset_confirm_title)) },
            text = { Text(stringResource(R.string.ranking_reset_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmDialog = false
                        viewModel.deleteCurrentSession()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.ranking_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text(stringResource(R.string.ranking_cancel))
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Ana içerik - Dialog açıkken gizle
        if (!showCriteriaDialog) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
        TopAppBar(
            title = {
                Text(methodTitle(method))
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
            },
            actions = {
                // Köşeleri yuvarlatılmış dikdörtgen butonlar - sayfanın en tepesinde
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Session management buttons
                    if (uiState.hasActiveSession) {
                        Button(
                            onClick = { viewModel.pauseSession() },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(stringResource(R.string.ranking_pause), fontSize = 10.sp)
                        }

                        Button(
                            onClick = { showResetConfirmDialog = true },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(stringResource(R.string.ranking_reset), fontSize = 10.sp)
                        }
                    }

                    if (method == "LEAGUE" || method == "EMRE_CORRECT") {
                        var showStandings by remember { mutableStateOf(false) }
                        Button(
                            onClick = { showStandings = !showStandings },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Text(stringResource(if (showStandings) R.string.ranking_matches else R.string.ranking_points), fontSize = 10.sp)
                        }

                        if (showStandings) {
                            StandingsDialog(
                                uiState = uiState,
                                method = method,
                                onDismiss = { showStandings = false }
                            )
                        }
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Error handling
        uiState.error?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = stringResource(R.string.ranking_error, error),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        when (method) {
            "DIRECT_SCORING" -> DirectScoringContent(
                uiState = uiState,
                allSongs = uiState.allSongs,
                onScoreSubmit = viewModel::submitDirectScore,
                onScoreUpdate = viewModel::updateScoreInSession,
                onComplete = { onNavigateToResults(listId, method) }
            )
            "LEAGUE", "SWISS", "EMRE_CORRECT", "MERGE_SORT" -> MatchBasedContent(
                uiState = uiState,
                method = method,
                viewModel = viewModel,
                onMatchResult = viewModel::submitMatchResult,
                onMatchResultWithScore = viewModel::submitMatchResultWithScore,
                onComplete = { onNavigateToResults(listId, method) },
                showCriteriaDialog = showCriteriaDialog,
                onShowCriteriaDialog = { showCriteriaDialog = it },
                onShowStandingsDialog = { showStandingsDialog = it },
                showScoreDialog = showScoreDialog,
                onShowScoreDialog = { showScoreDialog = it },
                onPauseSession = { viewModel.pauseSession() },
                onResetRequest = { showResetConfirmDialog = true },
                mandatoryCriteria = mandatoryCriteria
            )
            "ELIMINATION" -> EliminationContent(
                uiState = uiState,
                onMatchResult = viewModel::submitMatchResult,
                onComplete = { onNavigateToResults(listId, method) }
            )
            "FULL_ELIMINATION" -> EliminationContent(
                uiState = uiState,
                onMatchResult = viewModel::submitMatchResult,
                onComplete = { onNavigateToResults(listId, method) }
            )
        }
            }
        }

        // Kriter Değerlendirme Dialogu - Ana içeriğin üstünde
        if (showCriteriaDialog) {
            val currentMatch = when (method) {
                "LEAGUE", "SWISS", "EMRE_CORRECT" -> uiState.currentMatch
                else -> null
            }
            currentMatch?.let { match ->
                CriteriaEvaluationDialog(
                    match = match,
                    song1 = uiState.song1,
                    song2 = uiState.song2,
                    // Kriterler turnuva kaydından okunur. Maçlarda tournamentId
                    // olmadığı için asıl kaynak uiState.activeTournamentId'dir;
                    // eski listId fallback'i yanlış kayda bakıp kullanıcının
                    // kriterleri yerine varsayılan kriterleri gösteriyordu.
                    tournamentId = uiState.activeTournamentId
                        ?: match.tournamentId
                        ?: match.listId,
                    onDismiss = { showCriteriaDialog = false },
                    onSave = { criteriaScores, winner ->
                        showCriteriaDialog = false
                        // Kriter puanlarını kalıcı kaydet
                        viewModel.saveCriteriaScores(match, criteriaScores)
                        // Kazanan seçimine göre maç sonucunu işle
                        // (dialog "team1_wins" / "team2_wins" / "draw" / "save_only" yayar)
                        when (winner) {
                            "team1_wins" -> viewModel.submitMatchResult(match.id, match.songId1)
                            "team2_wins" -> viewModel.submitMatchResult(match.id, match.songId2)
                            "draw" -> viewModel.submitMatchResult(match.id, null)
                            // "save_only": sonuç girilmez, kullanıcı puanlama
                            // ekranından kazananı ayrıca seçer
                        }
                    }
                )
            }
        }

        // Standings Dialog with TMB buttons
        if (showStandingsDialog) {
            StandingsDialog(
                uiState = uiState,
                method = method,
                onDismiss = { showStandingsDialog = false },
                onMatchResult = viewModel::submitMatchResult,
                onShowCriteriaDialog = { showCriteriaDialog = it }
            )
        }

        // Score Input Dialog
        if (showScoreDialog) {
            ScoreInputDialog(
                match = when (method) {
                    "LEAGUE", "SWISS", "EMRE_CORRECT" -> uiState.currentMatch
                    else -> null
                },
                song1 = uiState.song1,
                song2 = uiState.song2,
                onDismiss = { showScoreDialog = false },
                onSave = { team1Score, team2Score ->
                    showScoreDialog = false
                    // Skor girişini işle
                    when {
                        team1Score > team2Score -> uiState.song1?.id?.let { viewModel.submitMatchResult(uiState.currentMatch?.id ?: 0, it) }
                        team2Score > team1Score -> uiState.song2?.id?.let { viewModel.submitMatchResult(uiState.currentMatch?.id ?: 0, it) }
                        else -> {
                            // Beraberlik durumu - skor kaydet
                            viewModel.submitDrawResult(uiState.currentMatch?.id ?: 0, team1Score, team2Score)
                        }
                    }
                }
            )
        }
    }
}

// YENİ 6 KATMANLI TASARIM TAMAMLANDI - Eksik fonksiyonlar ekleniyor

@Composable
internal fun TeamCardContent(
    song: Song,
    modifier: Modifier = Modifier
) {
    val csvData = song.csvData

    // Parse JSON outside composable scope
    val jsonData = remember(csvData) {
        if (csvData != null && csvData.isNotEmpty()) {
            try {
                val data = org.json.JSONObject(csvData)
                val keys = data.keys().asSequence().toList().filter { it !in HIDDEN_CSV_KEYS }
                keys.map { key -> key to data.optString(key, "") }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
    val imageUrl = remember(csvData) { extractImageUrl(csvData) }

    if (jsonData != null) {
        // CSV data display - restore original LazyColumn with proper constraint handling
        LazyColumn(
            modifier = modifier.heightIn(max = 300.dp), // Add max height to prevent infinite constraint
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (imageUrl != null) {
                item {
                    ItemImage(
                        imageUrl = imageUrl,
                        contentDescription = song.name,
                        height = 100.dp
                    )
                }
            }
            items(jsonData) { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = key,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    } else {
        // Simple song info
        Column {
            Text(
                text = song.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            song.artist?.let { artist ->
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EliminationContent(
    uiState: RankingViewModel.RankingUiState,
    onMatchResult: (Long, Long?) -> Unit,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.ranking_elimination_system))
    }
}

@Composable
private fun DirectScoringContent(
    uiState: RankingViewModel.RankingUiState,
    allSongs: List<Song> = emptyList(),
    onScoreSubmit: (Long, Double) -> Unit,
    onScoreUpdate: (Long, Double) -> Unit = { _, _ -> },
    onComplete: () -> Unit
) {
    if (uiState.isComplete) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.ranking_scoring_complete),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onComplete) {
                Text(stringResource(R.string.ranking_view_results))
            }
        }
        return
    }

    uiState.currentSong?.let { song ->
        var scoreText by remember { mutableStateOf("") }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                progress = { uiState.progress },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.ranking_progress_count, uiState.currentIndex + 1, uiState.totalCount),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TeamCardContent(
                        song = song,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.ranking_direct_score_question),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = scoreText,
                onValueChange = {
                    if (it.all { char -> char.isDigit() || char == '.' }) {
                        scoreText = it
                    }
                },
                label = { Text(stringResource(R.string.ranking_score_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(0.5f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val score = scoreText.toDoubleOrNull()
                    if (score != null && score in 0.0..100.0) {
                        onScoreSubmit(song.id, score)
                        scoreText = ""
                    }
                },
                enabled = scoreText.toDoubleOrNull()?.let { it in 0.0..100.0 } == true,
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Text(stringResource(R.string.ranking_save_score))
            }

            // Show completed scores if there are any
            if (uiState.hasActiveSession && uiState.currentIndex > 0) {
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = stringResource(R.string.ranking_given_scores),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp)
                ) {
                    items(uiState.completedScores.toList()) { (songId, score) ->
                        val song = allSongs.find { it.id == songId }
                        song?.let {
                            CompletedScoreItem(
                                song = it,
                                score = score,
                                onScoreUpdate = onScoreUpdate
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedScoreItem(
    song: Song,
    score: Double,
    onScoreUpdate: (Long, Double) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editScore by remember { mutableStateOf(score.toString()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TeamCardContent(
                song = song,
                modifier = Modifier
                    .weight(1f) // Take available width for table display
            )

            if (isEditing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = editScore,
                        onValueChange = {
                            if (it.all { char -> char.isDigit() || char == '.' }) {
                                editScore = it
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(80.dp),
                        singleLine = true
                    )

                    IconButton(
                        onClick = {
                            val newScore = editScore.toDoubleOrNull()
                            if (newScore != null && newScore in 0.0..100.0) {
                                onScoreUpdate(song.id, newScore)
                                isEditing = false
                            }
                        }
                    ) {
                        Text("✓", style = MaterialTheme.typography.bodyLarge)
                    }

                    IconButton(
                        onClick = {
                            editScore = score.toString()
                            isEditing = false
                        }
                    ) {
                        Text("✗", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = score.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = { isEditing = true }
                    ) {
                        Text("✏️")
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchBasedContent(
    uiState: RankingViewModel.RankingUiState,
    method: String,
    viewModel: RankingViewModel = viewModel(),
    onMatchResult: (Long, Long?) -> Unit,
    onMatchResultWithScore: (Long, Long?, Int?, Int?) -> Unit = { id, winner, _, _ -> onMatchResult(id, winner) },
    onComplete: () -> Unit,
    showCriteriaDialog: Boolean = false,
    onShowCriteriaDialog: (Boolean) -> Unit = {},
    onShowStandingsDialog: (Boolean) -> Unit = {},
    showScoreDialog: Boolean = false,
    onShowScoreDialog: (Boolean) -> Unit = {},
    onPauseSession: () -> Unit = {},
    onResetRequest: () -> Unit = {},
    // "Kriter değerlendirmesi zorunlu" ayarı: sonuç girişleri (takım seçimi,
    // beraberlik) doğrudan işlenmez, kriter dialoguna yönlendirilir
    mandatoryCriteria: Boolean = false
) {
    // Eşleştirmeler listesini göster (EMRE_CORRECT için) - currentMatch yoksa
    if (method == "EMRE_CORRECT" && uiState.showMatchingsList && uiState.currentMatch == null) {
        MatchingsListContent(
            uiState = uiState,
            viewModel = viewModel,
            method = method
        )
        return
    }

    if (uiState.isComplete) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.ranking_matches_complete),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onComplete) {
                Text(stringResource(R.string.ranking_view_results))
            }
        }
        return
    }

    uiState.currentMatch?.let { match ->
        val useScores = uiState.leagueSettings?.useScores ?: false
        var showVsMenu by remember { mutableStateOf(false) }

        // IMAGE #6: 6 KATMANLI SABİT LAYOUT
        // progress / Takım1 başlık / Takım1 scroll / orta buton çubuğu (ekran
        // ortasında) / Takım2 başlık / Takım2 scroll (ekran dibine kadar)
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. SABİT: TUR İLERLEME ÇUBUĞU
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.ranking_progress_count, uiState.completedMatches + 1, uiState.totalMatches),
                        style = MaterialTheme.typography.bodySmall
                    )

                    // Son maç sonucunu geri alma (tur kapanana kadar)
                    if (uiState.canUndo) {
                        TextButton(
                            onClick = { viewModel.undoLastMatch() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(stringResource(R.string.ranking_undo_last_match), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (method == "SWISS" || method == "EMRE_CORRECT") {
                        Text(
                            text = stringResource(R.string.ranking_round, match.round),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. SABİT: İLK TAKIM BAŞLIĞI - mavi, sıkıştırılmış
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = uiState.song1?.name?.uppercase() ?: stringResource(R.string.ranking_team1_fallback),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            // 3. SCROLL PENCERESİ: İLK TAKIM TABLOSU (her yöne kaydırılabilir)
            TeamSelectionPanel(
                song = uiState.song1,
                method = method,
                emreState = uiState.emreState,
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                onClick = {
                    if (mandatoryCriteria) {
                        // Zorunlu kriter: sonuç kriter dialogu üzerinden verilir
                        onShowCriteriaDialog(true)
                    } else if (!useScores) {
                        uiState.song1?.id?.let { songId ->
                            onMatchResult(match.id, songId)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Available space'in yarısını al
            )

            // 4. SABİT: ORTA BUTON ÇUBUĞU (Image #6) - tam ekran ortasında
            // BERABERLIK (büyük, yeşil) | VS (sarı, popup) | SKOR GİR + KRİTER (yeşil)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                // BERABERLIK - sol, büyük, yeşil
                // İkili karşılaştırmada beraberlik yok: her soruda kazanan seçilmeli
                if (method != "MERGE_SORT") {
                    Button(
                        onClick = {
                            if (mandatoryCriteria) {
                                // Zorunlu kriter: beraberlik de dialog üzerinden verilir
                                onShowCriteriaDialog(true)
                            } else {
                                onMatchResult(match.id, null) // null = beraberlik
                            }
                        },
                        modifier = Modifier
                            .weight(2f)
                            .fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF388E3C),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 0.dp, bottomEnd = 0.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ranking_draw_button),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // VS - ortada, sarı; üst işlemler popup menüde
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Button(
                        onClick = { showVsMenu = true },
                        modifier = Modifier.fillMaxSize(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFC107),
                            contentColor = Color.Black
                        ),
                        shape = if (method == "MERGE_SORT")
                            RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 0.dp, bottomEnd = 0.dp)
                        else
                            RectangleShape,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ranking_vs),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    DropdownMenu(
                        expanded = showVsMenu,
                        onDismissRequest = { showVsMenu = false }
                    ) {
                        if (uiState.hasActiveSession) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ranking_pause)) },
                                onClick = {
                                    showVsMenu = false
                                    onPauseSession()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ranking_reset)) },
                                onClick = {
                                    showVsMenu = false
                                    onResetRequest()
                                }
                            )
                        }
                        if (method == "LEAGUE" || method == "EMRE_CORRECT") {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ranking_standings_menu)) },
                                onClick = {
                                    showVsMenu = false
                                    onShowStandingsDialog(true)
                                }
                            )
                        }
                        if (uiState.canUndo) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ranking_undo_last_match)) },
                                onClick = {
                                    showVsMenu = false
                                    viewModel.undoLastMatch()
                                }
                            )
                        }
                    }
                }

                // SKOR GİR - sağda, yeşil
                Button(
                    onClick = { onShowScoreDialog(true) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF388E3C),
                        contentColor = Color.White
                    ),
                    shape = RectangleShape,
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ranking_process_score),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                // KRİTER - sağ uç, yeşil
                Button(
                    onClick = { onShowCriteriaDialog(true) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF388E3C),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ranking_criteria_button),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 5. SABİT: İKİNCİ TAKIM BAŞLIĞI - mavi, sıkıştırılmış
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = uiState.song2?.name?.uppercase() ?: stringResource(R.string.ranking_team2_fallback),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            // 6. SCROLL PENCERESİ: İKİNCİ TAKIM TABLOSU - ekran dibine kadar
            TeamSelectionPanel(
                song = uiState.song2,
                method = method,
                emreState = uiState.emreState,
                borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                onClick = {
                    if (mandatoryCriteria) {
                        // Zorunlu kriter: sonuç kriter dialogu üzerinden verilir
                        onShowCriteriaDialog(true)
                    } else if (!useScores) {
                        uiState.song2?.id?.let { songId ->
                            onMatchResult(match.id, songId)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Available space'in yarısını al
            )
        }
    }
}
