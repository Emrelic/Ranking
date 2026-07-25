package com.example.ranking.ui.screens.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.R
import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ui.viewmodel.RankingViewModel
import kotlin.math.roundToInt

@Composable
internal fun CriteriaEvaluationDialog(
    match: Match,
    song1: Song?,
    song2: Song?,
    tournamentId: Long,
    onDismiss: () -> Unit,
    onSave: (Map<String, Pair<Double?, Double?>>, String) -> Unit,
    viewModel: RankingViewModel = viewModel()
) {
    // TAM EKRAN IMMERSIVE MODE - Sistem tuşlarını gizle
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window
        window?.let {
            // Full immersive mode flags
            val insetsController = WindowCompat.getInsetsController(it, view)
            insetsController.apply {
                // Hem status bar hem navigation bar'ı gizle
                hide(WindowInsetsCompat.Type.systemBars())
                hide(WindowInsetsCompat.Type.navigationBars())
                hide(WindowInsetsCompat.Type.statusBars())
                // Sticky immersive - geri dönmesin
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            // Ek window flags - tam immersive için
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Dialog kapandığında sistem barlarını geri göster
    DisposableEffect(Unit) {
        onDispose {
            val window = (view.context as? android.app.Activity)?.window
            window?.let {
                val insetsController = WindowCompat.getInsetsController(it, view)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                insetsController.show(WindowInsetsCompat.Type.navigationBars())
                insetsController.show(WindowInsetsCompat.Type.statusBars())

                // Window flags'leri temizle
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets(0)), // Tüm sistem padding'lerini kaldır
            shape = RoundedCornerShape(0.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            val criteriaScores = remember { mutableStateMapOf<String, Pair<Double?, Double?>>() }
            val expandedCriteria = remember { mutableStateMapOf<String, Boolean>() }
            var criteria by remember { mutableStateOf<List<String>>(emptyList()) }
            var criteriaSettings by remember { mutableStateOf<Map<String, Any>?>(null) }

            LaunchedEffect(tournamentId) {
                criteria = viewModel.getCriteriaForTournament(tournamentId)
                criteriaSettings = viewModel.getCriteriaSettingsForTournament(tournamentId)
            }

            val finalCriteria = if (criteria.isNotEmpty()) criteria else listOf(
                stringResource(R.string.criteria_dialog_default_criterion_1),
                stringResource(R.string.criteria_dialog_default_criterion_2),
                stringResource(R.string.criteria_dialog_default_criterion_3),
                stringResource(R.string.criteria_dialog_default_criterion_4),
                stringResource(R.string.criteria_dialog_default_criterion_5)
            )

            val hasAnyExpanded by remember {
                derivedStateOf { expandedCriteria.values.any { it } }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f))
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f))
                    )
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        CriteriaEvaluationFooter(
                            song1Name = song1?.name,
                            song2Name = song2?.name,
                            criteriaScores = criteriaScores,
                            hasAnyExpanded = hasAnyExpanded,
                            criteriaSettings = criteriaSettings,
                            onDismiss = onDismiss,
                            onSave = onSave
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .statusBarsPadding()
                            .imePadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.criteria_dialog_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.common_close),
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = song1?.name?.uppercase() ?: stringResource(R.string.ranking_team1_fallback),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = song2?.name?.uppercase() ?: stringResource(R.string.ranking_team2_fallback),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(finalCriteria) { criterion ->
                                CriterionBox(
                                    criterionName = criterion,
                                    team1Name = song1?.name ?: stringResource(R.string.common_team1),
                                    team2Name = song2?.name ?: stringResource(R.string.common_team2),
                                    isExpanded = expandedCriteria[criterion] ?: false,
                                    currentScores = criteriaScores[criterion] ?: Pair(null, null),
                                    criteriaSettings = criteriaSettings,
                                    onExpandToggle = { expanded ->
                                        expandedCriteria[criterion] = expanded
                                    },
                                    onScoresChanged = { team1Score, team2Score ->
                                        criteriaScores[criterion] = Pair(team1Score, team2Score)
                                    }
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
private fun CriteriaEvaluationFooter(
    song1Name: String?,
    song2Name: String?,
    criteriaScores: SnapshotStateMap<String, Pair<Double?, Double?>>,
    hasAnyExpanded: Boolean,
    criteriaSettings: Map<String, Any>? = null,
    onDismiss: () -> Unit,
    onSave: (Map<String, Pair<Double?, Double?>>, String) -> Unit
) {
    val team1Total = if (hasAnyExpanded) {
        criteriaScores.values.sumOf { it.first ?: 0.0 }
    } else 0.0
    val team2Total = if (hasAnyExpanded) {
        criteriaScores.values.sumOf { it.second ?: 0.0 }
    } else 0.0

    // "Kriterlerden otomatik kazanan belirle" ayarı + beraberlik eşiği bandı.
    // Eşik, kazanan tarafın toplam içindeki yüzdesi bu bandın içindeyse
    // sonucun "çok yakın" sayılıp beraberlik ilan edilmesini sağlar
    // (örn. 40-60: %55-%45'lik sonuç beraberliktir).
    val autoWinnerEnabled = criteriaSettings?.get("autoWinnerFromCriteria") as? Boolean ?: false
    val thresholds = (criteriaSettings?.get("drawThresholdPercent") as? List<*>)
        ?.mapNotNull { (it as? Number)?.toInt() }
    val drawMin = thresholds?.getOrNull(0) ?: 50
    val drawMax = thresholds?.getOrNull(1) ?: 50

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = song1Name ?: stringResource(R.string.common_team1),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (team1Total % 1.0 == 0.0) "${team1Total.toInt()}" else String.format("%.1f", team1Total),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = song2Name ?: stringResource(R.string.common_team2),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (team2Total % 1.0 == 0.0) "${team2Total.toInt()}" else String.format("%.1f", team2Total),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Otomatik kazanan: kriter toplamlarına göre sonucu hesaplar.
            // Kazanan payı beraberlik eşiği bandındaysa beraberlik yazılır.
            if (autoWinnerEnabled) {
                val totalSum = team1Total + team2Total
                Button(
                    onClick = {
                        val result = if (totalSum <= 0.0) {
                            "draw"
                        } else {
                            val team1Percent = team1Total / totalSum * 100.0
                            when {
                                team1Percent >= drawMin && team1Percent <= drawMax -> "draw"
                                team1Total > team2Total -> "team1_wins"
                                team1Total < team2Total -> "team2_wins"
                                else -> "draw"
                            }
                        }
                        onSave(criteriaScores.toMap(), result)
                    },
                    enabled = totalSum > 0.0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.criteria_dialog_auto_result),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onSave(criteriaScores.toMap(), "team1_wins") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = song1Name ?: stringResource(R.string.common_team1),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.criteria_dialog_won),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Button(
                    onClick = { onSave(criteriaScores.toMap(), "draw") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.common_draw),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                Button(
                    onClick = { onSave(criteriaScores.toMap(), "team2_wins") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = song2Name ?: stringResource(R.string.common_team2),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.criteria_dialog_won),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
                Button(
                    onClick = { onSave(criteriaScores.toMap(), "save_only") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.common_save))
                }
            }
        }
    }
}

@Composable
private fun CriterionBox(
    criterionName: String,
    team1Name: String,
    team2Name: String,
    isExpanded: Boolean,
    currentScores: Pair<Double?, Double?>,
    criteriaSettings: Map<String, Any>?,
    onExpandToggle: (Boolean) -> Unit,
    onScoresChanged: (Double?, Double?) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 0.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(
            width = 2.dp,
            color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        // ARKAPLAN: Ana tema ile uyumlu yarı yarıya renkli
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f))
            )
        }

        Column {
            // Kriter başlığı ve checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandToggle(!isExpanded) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = criterionName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Checkbox(
                    checked = isExpanded,
                    onCheckedChange = onExpandToggle,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            // Açılır pencerecik - Ayarlara göre değişken
            if (isExpanded) {
                // Puanlama tipi sihirbazda seçilen turnuva ayarından gelir.
                // (Eski kod hiç yazılmayan "useSeparateScoring" anahtarına
                // bakıyordu; varsayılan true olduğundan kıyaslamalı slider
                // hiçbir zaman devreye girmiyordu.)
                val scoringType = criteriaSettings?.get("scoringType") as? String ?: "separate"
                val scoreScale = (criteriaSettings?.get("scoreScale") as? Number)?.toInt() ?: 10

                if (scoringType != "comparative") {
                    // AYRI AYRI DEĞERLENDİRME - Dropdown'lar
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // Sol yarı - Takım 1
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = team1Name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                ScoreDropdown(
                                    score = currentScores.first,
                                    maxScore = scoreScale,
                                    onScoreChange = { newScore ->
                                        onScoresChanged(newScore, currentScores.second)
                                    }
                                )
                            }
                        }

                        // Sağ yarı - Takım 2
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = team2Name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                ScoreDropdown(
                                    score = currentScores.second,
                                    maxScore = scoreScale,
                                    onScoreChange = { newScore ->
                                        onScoresChanged(currentScores.first, newScore)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // KIYASLAMALI DEĞERLENDİRME - Slider sistemi
                    ComparativeSlider(
                        team1Name = team1Name,
                        team2Name = team2Name,
                        scoreScale = scoreScale,
                        currentScores = currentScores,
                        onScoresChanged = onScoresChanged
                    )
                }
            }
        }
    }
}

// KIYASLAMALI SLIDER SİSTEMİ
@Composable
private fun ComparativeSlider(
    team1Name: String,
    team2Name: String,
    scoreScale: Int,
    currentScores: Pair<Double?, Double?>,
    onScoresChanged: (Double?, Double?) -> Unit
) {
    // Daha önce puan verildiyse slider oradan başlar; yoksa 50% (eşit bölüşüm)
    var sliderValue by remember {
        mutableStateOf(
            currentScores.first?.let { (it * 100f / scoreScale).toFloat().coerceIn(0f, 100f) } ?: 50f
        )
    }

    // Kriter açılır açılmaz başlangıç bölüşümü (örn. 5-5) toplama yazılır;
    // aksi halde slider'a hiç dokunulmazsa bu kriter toplamda görünmüyordu
    LaunchedEffect(Unit) {
        if (currentScores.first == null && currentScores.second == null) {
            val team1Score = (sliderValue * scoreScale / 100f).roundToInt()
            onScoresChanged(team1Score.toDouble(), (scoreScale - team1Score).toDouble())
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Takım isimleri ve puanlar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Toplam her zaman skalaya eşit kalır (örn. 10 puan -> 3-7)
                val team1Score = (sliderValue * scoreScale / 100f).roundToInt()
                val team2Score = scoreScale - team1Score

                Text(
                    text = "$team1Name: $team1Score",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "$team2Name: $team2Score",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Çubuk - tam puan adımlarıyla (0..skala arası bölüşüm)
            Slider(
                value = sliderValue,
                onValueChange = { newValue ->
                    sliderValue = newValue
                    val team1Score = (newValue * scoreScale / 100f).roundToInt()
                    val team2Score = scoreScale - team1Score
                    onScoresChanged(team1Score.toDouble(), team2Score.toDouble())
                },
                valueRange = 0f..100f,
                steps = (scoreScale - 1).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.tertiary
                )
            )
        }
    }
}

// PUAN DROPDOWN SİSTEMİ

@Composable
private fun ScoreDropdown(
    score: Double?,
    maxScore: Int = 10,
    onScoreChange: (Double?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = score?.toInt()?.toString() ?: stringResource(R.string.criteria_dialog_select),
                style = MaterialTheme.typography.bodyLarge, // BÜYÜK FONT
                fontWeight = FontWeight.Bold // BOLD FONT
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            (0..maxScore).forEach { scoreValue ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = scoreValue.toString(),
                            style = MaterialTheme.typography.bodyLarge, // BÜYÜK FONT
                            fontWeight = FontWeight.Bold // BOLD FONT
                        )
                    },
                    onClick = {
                        onScoreChange(scoreValue.toDouble())
                        expanded = false
                    }
                )
            }
        }
    }
}
