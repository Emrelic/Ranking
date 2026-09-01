package com.example.ranking.ui.screens

import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.example.ranking.ui.screens.ranking.MacSonuclariDialog
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
    var showBitirDialog by remember { mutableStateOf(false) }
    var showResultsDialog by remember { mutableStateOf(false) }

    // Turnuvanın kriter ayarları (panel otomatik açılış + zorunlu kriter için)
    var criteriaSettingsMap by remember { mutableStateOf<Map<String, Any>?>(null) }
    LaunchedEffect(uiState.activeTournamentId) {
        criteriaSettingsMap = uiState.activeTournamentId
            ?.let { viewModel.getCriteriaSettingsForTournament(it) }
    }
    val autoOpenCriteriaPanel = criteriaSettingsMap?.get("autoOpenCriteriaPanel") as? Boolean ?: false

    // Kriter değerlendirmesi maç tabanlı yöntemlerde anlamlı; MERGE_SORT
    // ikili karşılaştırma sorar ve dialoga verilecek bir "maç" yoktur.
    val kriterDesteklenir = method in listOf("LEAGUE", "SWISS", "EMRE_CORRECT", "HIBRIT")
    val kriterMaci = if (kriterDesteklenir) uiState.currentMatch else null

    // 🔴 Dialog ancak GÖSTERİLEBİLİYORSA ana içerik gizlenir. Aksi hâlde
    // (MERGE_SORT'ta KRİTER'e basıldığında) ekran tamamen boşalıyor ve
    // TopAppBar da gizlendiği için geri dönüş yolu kalmıyordu.
    val kriterDialoguGorunur = showCriteriaDialog && kriterMaci != null

    val mandatoryCriteria = (criteriaSettingsMap?.get("mandatoryCriteria") as? Boolean ?: false) &&
        kriterDesteklenir

    // "Kriter panelini otomatik aç": yeni maç ekrana gelince dialog kendiliğinden açılır
    LaunchedEffect(uiState.currentMatch?.id, autoOpenCriteriaPanel) {
        if (autoOpenCriteriaPanel && uiState.currentMatch != null && !uiState.isComplete &&
            method in listOf("LEAGUE", "SWISS", "EMRE_CORRECT", "HIBRIT")
        ) {
            showCriteriaDialog = true
        }
    }

    // Erken bitirme: keskinlik raporu + onay
    if (showBitirDialog) {
        val rapor = uiState.emreState?.let {
            com.example.ranking.ranking.EmreSystemCorrect.kesinlikRaporu(it)
        }
        AlertDialog(
            onDismissRequest = { showBitirDialog = false },
            title = { Text("Turnuvayı bitir?") },
            text = {
                if (rapor != null) {
                    Text(
                        """
                        Oynanan tur: ${rapor.oynananTur} (şampiyon için önerilen: ${rapor.onerilenTur})

                        SIRALAMA KESKİNLİĞİ: %${rapor.genelYuzde}
                        (${rapor.kanitliSinir}/${rapor.toplamSinir} komşuluk kanıtlı)

                          Üst sıralar:  %${rapor.ustYuzde}
                          Orta bölge:  %${rapor.ortaYuzde}
                          Alt sıralar:  %${rapor.altYuzde}

                        Kanıtlı = komşuların puanı farklı ya da aralarında maç oynanmış. Belirsiz komşuluklar tiebreaker tahminiyle sıralanır; devam ederseniz orta bölge keskinleşir.
                        """.trimIndent()
                    )
                } else {
                    Text("Şu anki sıralamayla turnuva bitirilecek.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showBitirDialog = false
                    viewModel.erkenBitir()
                }) { Text("Bitir") }
            },
            dismissButton = {
                TextButton(onClick = { showBitirDialog = false }) { Text("Devam Et") }
            }
        )
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
        if (!kriterDialoguGorunur) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
        TopAppBar(
            title = {
                // Yöntem adı yerine TUR + EŞLEŞME SAYACI (kullanıcı isteği):
                // "2. Tur 25/40". Maç ekranda değilken (liste/bitiş/puanlama
                // dışı) yöntem adına dönülür — sayaç o an anlamsız.
                val aktifMac = uiState.currentMatch
                if (aktifMac != null && !uiState.isComplete) {
                    val sayac = "${(uiState.completedMatches + 1).coerceAtMost(uiState.totalMatches.coerceAtLeast(1))}/${uiState.totalMatches}"
                    val tur = if (method == "SWISS" || method == "EMRE_CORRECT" || method == "HIBRIT") {
                        "${aktifMac.round}. Tur "
                    } else ""
                    Text("$tur$sayac")
                } else {
                    Text(methodTitle(method))
                }
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
                    // GERİ AL — üst çubukta, en solda.
                    // Yanlışlıkla oy veren kullanıcı en çok buraya bakıyor;
                    // ilerleme satırındaki küçük buton fark edilmiyordu.
                    if (uiState.canUndo) {
                        Button(
                            onClick = { viewModel.undoLastMatch() },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text("↩ Geri Al", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // DURAKLAT KALDIRILDI: tek işi hiçbir yerden okunmayan
                    // isPaused bayrağını yazmaktı — oturum zaten her oyda
                    // diske kaydediliyor ve "Devam Eden Turnuvalar"da duruyor.
                    // BİTİR — Emre'de önerilen tur sayısına (ceil log2 n)
                    // ulaşılınca görünür. Turnuva normalde ~n/2 tur sürer ve
                    // kimse sonuna kadar oynamaz; şampiyon log2(n) turda
                    // bellidir, kalan turlar orta sıraları keskinleştirir.
                    // Basınca keskinlik raporu gösterilir, karar kullanıcının.
                    if (method == "EMRE_CORRECT" && !uiState.isComplete) {
                        val takimSayisi = uiState.allSongs.size
                        val onerilenTur = if (takimSayisi > 1)
                            kotlin.math.ceil(kotlin.math.log2(takimSayisi.toDouble())).toInt()
                        else 1
                        if (uiState.currentRound > onerilenTur) {
                            Button(
                                onClick = { showBitirDialog = true },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    contentColor = MaterialTheme.colorScheme.onTertiary
                                )
                            ) {
                                Text("BİTİR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Yerine SONUÇLAR: oylanmış maçlar görülür ve tur bitmeden
                    // (yalnız takımın son maçıysa) sonuç değiştirilebilir.
                    if (method in listOf("LEAGUE", "SWISS", "EMRE_CORRECT", "HIBRIT")) {
                        Button(
                            onClick = {
                                viewModel.macSonuclariniYukle()
                                showResultsDialog = true
                            },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text("Sonuçlar", fontSize = 10.sp)
                        }
                    }

                    if (uiState.hasActiveSession) {
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
            "LEAGUE", "SWISS", "EMRE_CORRECT", "MERGE_SORT", "HIBRIT" -> MatchBasedContent(
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
        if (kriterDialoguGorunur) {
            kriterMaci?.let { match ->
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
                    // Zorunlu kriter modu artık dialogda GERÇEKTEN uygulanıyor:
                    // eksik kriter varsa sonuç butonları basılamaz
                    mandatoryCriteria = mandatoryCriteria,
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

        // Sonuçlar dialogu — oylanmış maçlar, tur bitmeden düzenlenebilir
        if (showResultsDialog) {
            MacSonuclariDialog(
                uiState = uiState,
                method = method,
                onDismiss = { showResultsDialog = false },
                onSonucDegistir = { matchId, winnerId ->
                    viewModel.tamamlanmisSonucuDegistir(matchId, winnerId)
                }
            )
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
                    "LEAGUE", "SWISS", "EMRE_CORRECT", "HIBRIT" -> uiState.currentMatch
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
    onResetRequest: () -> Unit = {},
    // "Kriter değerlendirmesi zorunlu" ayarı: sonuç girişleri (takım seçimi,
    // beraberlik) doğrudan işlenmez, kriter dialoguna yönlendirilir
    mandatoryCriteria: Boolean = false
) {
    // Kriter değerlendirmesi yalnız maç tabanlı yöntemlerde anlamlı;
    // MERGE_SORT ikili karşılaştırma sorar, dialoga verilecek maç yoktur.
    val kriterDesteklenir = method in listOf("LEAGUE", "SWISS", "EMRE_CORRECT", "HIBRIT")

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

        // YATAY MOD: ekran döndürülünce kartlar YAN YANA durur, buton çubuğu
        // aralarında DİKEY bir sütun olur. Manifest'te configChanges
        // orientation'ı kapsadığı için dönüşte activity yeniden yaratılmaz,
        // oylama state'i aynen korunur.
        val yatayMod = LocalConfiguration.current.orientation ==
            Configuration.ORIENTATION_LANDSCAPE

        // IMAGE #6: 6 KATMANLI SABİT LAYOUT (dikey)
        // progress / Takım1 scroll / orta buton çubuğu (ekran ortasında) /
        // Takım2 scroll (ekran dibine kadar) — yatayda 3. ve 6. katman yan yana
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // İLERLEME ÇUBUĞU KALDIRILDI (kullanıcı isteği): kazandırdığı
            // dikey alan kartlara verildi. Tur + eşleşme SAYACI üst çubukta
            // (TopAppBar başlığı, "2. Tur 25/40"); butonların yanında ise
            // bu eşleşmenin NUMARASI gösterilir.
            val turEtiketi: String? = null
            val macSayaci = "Maç ${match.matchNumber}"

            // 2./5. TAKIM BAŞLIKLARI kaldırıldı — ad artık kartın KENDİ başlık
            // şeridinde (bkz. TeamSelectionPanel).

            // Takım panelleri ve orta buton çubuğu — dikeyde alt alta,
            // yatayda YAN YANA (butonlar aralarında dikey sütun olur).
            val takim1Tikla: () -> Unit = {
                if (mandatoryCriteria) {
                    // Zorunlu kriter: sonuç kriter dialogu üzerinden verilir
                    onShowCriteriaDialog(true)
                } else if (!useScores) {
                    uiState.song1?.id?.let { songId -> onMatchResult(match.id, songId) }
                }
            }
            val takim2Tikla: () -> Unit = {
                if (mandatoryCriteria) {
                    onShowCriteriaDialog(true)
                } else if (!useScores) {
                    uiState.song2?.id?.let { songId -> onMatchResult(match.id, songId) }
                }
            }
            val beraberlikTikla: () -> Unit = {
                if (mandatoryCriteria) {
                    // Zorunlu kriter: beraberlik de dialog üzerinden verilir
                    onShowCriteriaDialog(true)
                } else {
                    onMatchResult(match.id, null) // null = beraberlik
                }
            }
            val skorTikla: () -> Unit = {
                if (mandatoryCriteria) {
                    // Zorunlu kriter: skorla sonuç girişi de kriter
                    // dialoguna yönlendirilir (kriter atlanamaz)
                    onShowCriteriaDialog(true)
                } else {
                    onShowScoreDialog(true)
                }
            }
            val vsMenu: @Composable () -> Unit = {
                DropdownMenu(
                    expanded = showVsMenu,
                    onDismissRequest = { showVsMenu = false }
                ) {
                    // Duraklat menüden kaldırıldı: isPaused bayrağını hiçbir
                    // kod okumuyordu, buton hiçbir şey yapmıyordu
                    if (uiState.hasActiveSession) {
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

            if (yatayMod) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    TeamSelectionPanel(
                        song = uiState.song1,
                        method = method,
                        emreState = uiState.emreState,
                        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        onClick = takim1Tikla,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    // ORTA BUTON SÜTUNU — yatayda kartların ARASINDA dikey durur
                    // (112dp: "BERABERLİK" tek satırda sığsın, kırılmasın)
                    Column(
                        modifier = Modifier
                            .width(112.dp)
                            .fillMaxHeight()
                            .padding(horizontal = 4.dp)
                    ) {
                        if (method != "MERGE_SORT") {
                            OrtaButon(
                                text = stringResource(R.string.ranking_draw_button),
                                onClick = beraberlikTikla,
                                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                                modifier = Modifier.fillMaxWidth().weight(1.4f)
                            )
                        }
                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            Button(
                                onClick = { showVsMenu = true },
                                modifier = Modifier.fillMaxSize(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFC107),
                                    contentColor = Color.Black
                                ),
                                shape = if (method == "MERGE_SORT")
                                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                else RectangleShape,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.ranking_vs),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            vsMenu()
                        }
                        if (method != "MERGE_SORT") {
                            OrtaButon(
                                text = stringResource(R.string.ranking_process_score),
                                onClick = skorTikla,
                                shape = RectangleShape,
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            )
                        }
                        if (kriterDesteklenir) {
                            OrtaButon(
                                text = stringResource(R.string.ranking_criteria_button),
                                onClick = { onShowCriteriaDialog(true) },
                                shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            )
                        }
                        // Tur + eşleşme sayacı (ilerleme çubuğunun yerine)
                        TurMacSayaci(
                            turEtiketi = turEtiketi,
                            macSayaci = macSayaci,
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                        )
                    }

                    TeamSelectionPanel(
                        song = uiState.song2,
                        method = method,
                        emreState = uiState.emreState,
                        borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                        onClick = takim2Tikla,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                // 3. SCROLL PENCERESİ: İLK TAKIM TABLOSU (her yöne kaydırılabilir)
                TeamSelectionPanel(
                    song = uiState.song1,
                    method = method,
                    emreState = uiState.emreState,
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    onClick = takim1Tikla,
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
                        OrtaButon(
                            text = stringResource(R.string.ranking_draw_button),
                            onClick = beraberlikTikla,
                            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
                            modifier = Modifier.weight(2f).fillMaxHeight(),
                            textStyle = MaterialTheme.typography.labelLarge
                        )
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
                                RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                            else RectangleShape,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.ranking_vs),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        vsMenu()
                    }

                    // SKOR GİR - sağda, yeşil
                    // İkili karşılaştırmada gizli: skor girişi eşit skora izin verir,
                    // eşit skor beraberlik demektir ve MERGE_SORT'ta beraberlik
                    // "aday kaybetti" sayılıp sıralamayı sessizce keyfileştirir.
                    if (method != "MERGE_SORT") {
                        OrtaButon(
                            text = stringResource(R.string.ranking_process_score),
                            onClick = skorTikla,
                            shape = RectangleShape,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }

                    // KRİTER - sağ uç, yeşil
                    // Yalnız maç tabanlı yöntemlerde: MERGE_SORT'ta dialoga
                    // verilecek maç yok, buton ekranı boşaltıyordu
                    if (kriterDesteklenir) {
                        OrtaButon(
                            text = stringResource(R.string.ranking_criteria_button),
                            onClick = { onShowCriteriaDialog(true) },
                            shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }

                    // Tur + eşleşme sayacı (ilerleme çubuğunun yerine):
                    // "2. Tur / 25/40" — butonların hemen yanında
                    TurMacSayaci(
                        turEtiketi = turEtiketi,
                        macSayaci = macSayaci,
                        modifier = Modifier.fillMaxHeight().padding(start = 4.dp)
                    )
                }

                // 6. SCROLL PENCERESİ: İKİNCİ TAKIM TABLOSU - ekran dibine kadar
                TeamSelectionPanel(
                    song = uiState.song2,
                    method = method,
                    emreState = uiState.emreState,
                    borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                    onClick = takim2Tikla,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // Available space'in yarısını al
                )
            }
        }
    }
}

/** Tur ve eşleşme sayacı — ilerleme çubuğunun yerine, butonların yanında. */
@Composable
private fun TurMacSayaci(
    turEtiketi: String?,
    macSayaci: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (turEtiketi != null) {
            Text(
                text = turEtiketi,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        Text(
            text = macSayaci,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** Orta çubuğun yeşil butonu — dikey/yatay yerleşimde ortak gövde. */
@Composable
private fun OrtaButon(
    text: String,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelMedium
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF388E3C),
            contentColor = Color.White
        ),
        shape = shape,
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        Text(
            text = text,
            style = textStyle,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}
