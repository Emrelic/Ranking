package com.example.ranking.ui.screens.ranking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────
// Motor henüz bağlanmadı — bu tipler bu dosyaya özel, koordinatör
// entegrasyonda gerçek motor tiplerinden buraya eşler (oturumlar/ELEME-EKRANI.md).
// ─────────────────────────────────────────────────────────────────────────

data class BracketTeam(
    val songId: Long,
    val name: String,
    val seed: Int,
    val score: Int? = null,
    val isWinner: Boolean = false,
    val isBye: Boolean = false
)

data class BracketMatch(
    val matchId: Long,
    val round: Int,
    val matchNumber: Int,
    val team1: BracketTeam?,
    val team2: BracketTeam?,
    val isCompleted: Boolean,
    val isPlayable: Boolean
)

data class BracketRound(
    val roundNumber: Int,
    val title: String,
    val matches: List<BracketMatch>
)

/**
 * Bir turdaki takım sayısından tur adını türetir.
 * 2→Final, 4→Yarı Final, 8→Çeyrek Final, 16→Son 16, 32→Son 32, 64→Son 64.
 * Bunların dışındaki sayılar (bye içeren düzensiz ilk turlar) "Ön Tur" olur.
 */
fun bracketRoundTitle(teamCountInRound: Int): String = when (teamCountInRound) {
    2 -> "Final"
    4 -> "Yarı Final"
    8 -> "Çeyrek Final"
    16 -> "Son 16"
    32 -> "Son 32"
    64 -> "Son 64"
    else -> "Ön Tur"
}

/**
 * Eleme turnuvasının klasik fikstür ağacı — turlar yan yana sütunlar.
 * 32+ takımlık bracket telefon ekranına sığmadığı için hem yatay hem dikey
 * kaydırılabilir.
 *
 * @param onMatchClick koordinatör bunu oylama ekranına bağlayacak.
 */
@Composable
fun BracketView(
    rounds: List<BracketRound>,
    onMatchClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (rounds.isEmpty()) {
        Text(
            text = "Fikstür henüz oluşmadı.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(16.dp)
        )
        return
    }

    val champion = rounds.last().matches.firstOrNull()?.let { finalMatch ->
        when {
            finalMatch.team1?.isWinner == true -> finalMatch.team1
            finalMatch.team2?.isWinner == true -> finalMatch.team2
            else -> null
        }
    }

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        rounds.forEach { round ->
            BracketRoundColumn(round = round, onMatchClick = onMatchClick)
        }
        if (champion != null) {
            ChampionColumn(champion)
        }
    }
}

@Composable
private fun BracketRoundColumn(
    round: BracketRound,
    onMatchClick: (Long) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.width(170.dp)
    ) {
        Text(
            text = round.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        round.matches.forEach { match ->
            BracketMatchCard(match = match, onClick = { onMatchClick(match.matchId) })
        }
    }
}

@Composable
private fun BracketMatchCard(
    match: BracketMatch,
    onClick: () -> Unit
) {
    val tiklanabilir = match.isPlayable && !match.isCompleted
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (tiklanabilir) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (match.isCompleted)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (tiklanabilir) 0.6f else 0.25f)
        ),
        border = BorderStroke(
            1.dp,
            if (tiklanabilir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            BracketTeamRow(match.team1)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            BracketTeamRow(match.team2)
        }
    }
}

@Composable
private fun BracketTeamRow(team: BracketTeam?) {
    val kazandi = team?.isWinner == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .then(
                if (kazandi)
                    Modifier.background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        RoundedCornerShape(4.dp)
                    )
                else Modifier
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when {
                team == null -> "—"
                team.isBye -> "BYE"
                else -> team.name
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (kazandi) FontWeight.Bold else FontWeight.Normal,
            color = when {
                team == null || team.isBye -> MaterialTheme.colorScheme.onSurfaceVariant
                kazandi -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (team?.score != null) {
            Text(
                text = "${team.score}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (kazandi) FontWeight.Bold else FontWeight.Normal,
                color = if (kazandi) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Şampiyon kutusu, mevcut dokudaki MatchingsList.kt gibi tören amaçlı sabit
// bir vurgu rengi kullanır (bkz. AdvancedMatchCard'daki Color(0xFF388E3C));
// yalnız dekoratif çerçevede, metin/zemin kontrastı MaterialTheme'den gelir.
private val AltinCerceve = Color(0xFFFFD700)

@Composable
private fun ChampionColumn(champion: BracketTeam) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(140.dp)
    ) {
        Text(
            text = "Şampiyon",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Card(
            border = BorderStroke(2.dp, AltinCerceve),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Text(
                text = champion.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Preview verisi — motor olmadan görsel doğrulama
// ─────────────────────────────────────────────────────────────────────────

private fun previewTeam(seed: Int, name: String, score: Int? = null, isWinner: Boolean = false, isBye: Boolean = false) =
    BracketTeam(songId = seed.toLong(), name = name, seed = seed, score = score, isWinner = isWinner, isBye = isBye)

private fun previewRoundsN8(): List<BracketRound> = listOf(
    BracketRound(1, bracketRoundTitle(8), listOf(
        BracketMatch(1, 1, 1, previewTeam(1, "Sil Baştan", 7, true), previewTeam(8, "Yağmurlar", 3), true, true),
        BracketMatch(2, 1, 2, previewTeam(4, "Perdeler", 5, true), previewTeam(5, "Dünya", 5), true, true),
        BracketMatch(3, 1, 3, previewTeam(3, "Od", null), previewTeam(6, "Girdap", null), false, true),
        BracketMatch(4, 1, 4, previewTeam(2, "Koridor", null), previewTeam(7, "Vicdan", null), false, false)
    )),
    BracketRound(2, bracketRoundTitle(4), listOf(
        BracketMatch(5, 2, 5, previewTeam(1, "Sil Baştan", null), previewTeam(4, "Perdeler", null), false, false),
        BracketMatch(6, 2, 6, null, null, false, false)
    )),
    BracketRound(3, bracketRoundTitle(2), listOf(
        BracketMatch(7, 3, 7, null, null, false, false)
    ))
)

private fun previewRoundsN12(): List<BracketRound> = listOf(
    BracketRound(0, "Ön Tur", listOf(
        BracketMatch(1, 0, 1, previewTeam(5, "Ay", 6, true), previewTeam(12, "Yorgun", 2), true, true),
        BracketMatch(2, 0, 2, previewTeam(6, "Bugün", null), previewTeam(11, "Üvey", null), false, true),
        BracketMatch(3, 0, 3, previewTeam(7, "Kalbim", null), previewTeam(10, "Oyunun Sonu", null), false, false),
        BracketMatch(4, 0, 4, previewTeam(8, "Herkes Bilsin İstedim", null), previewTeam(9, "Nefessiz Kaldım", null), false, false)
    )),
    BracketRound(1, bracketRoundTitle(8), listOf(
        BracketMatch(5, 1, 5, previewTeam(1, "Oyunlar", null), previewTeam(8, "—", isBye = true), false, true),
        BracketMatch(6, 1, 6, previewTeam(4, "Artık Kısa Cümleler Kuruyorum", null), null, false, false),
        BracketMatch(7, 1, 7, null, null, false, false),
        BracketMatch(8, 1, 8, null, null, false, false)
    )),
    BracketRound(2, bracketRoundTitle(4), listOf(
        BracketMatch(9, 2, 9, null, null, false, false),
        BracketMatch(10, 2, 10, null, null, false, false)
    )),
    BracketRound(3, bracketRoundTitle(2), listOf(
        BracketMatch(11, 3, 11, null, null, false, false)
    ))
)

@Preview(showBackground = true, widthDp = 900, name = "Bracket n=8")
@Composable
private fun BracketViewPreviewN8() {
    BracketView(rounds = previewRoundsN8(), onMatchClick = {})
}

@Preview(showBackground = true, widthDp = 900, name = "Bracket n=12 (ön turlu)")
@Composable
private fun BracketViewPreviewN12() {
    BracketView(rounds = previewRoundsN12(), onMatchClick = {})
}
