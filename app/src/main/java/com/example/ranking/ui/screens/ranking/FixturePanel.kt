package com.example.ranking.ui.screens.ranking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ranking.data.Match
import com.example.ranking.data.Song

/**
 * Turnuva fikstür paneli — yönteme göre doğru görünümü seçen genel bileşen.
 *
 * ⚠️ ELIMINATION için motor henüz yok: ham `Match` listesinden BYE durumu
 * güvenilir çıkarılamıyor (RankingEngine.kt'deki mevcut eleme motoru BYE
 * geçen takım için hiç Match satırı üretmiyor — CLAUDE.md'de zaten
 * "motor tamamlanmadı" diye işaretli). Bu yüzden ELIMINATION kolu ham
 * `matches` yerine hazır `eliminationRounds: List<BracketRound>` bekler;
 * motor bitince o listeyi dolduran taraf (koordinatör) bağlar.
 *
 * Diğer yöntemler (LEAGUE, SWISS, EMRE_CORRECT, MERGE_SORT, ...) ham
 * `Match` listesini tur tur gruplar ve basit bir eşleşme listesi gösterir.
 */
@Composable
fun FixturePanel(
    method: String,
    matches: List<Match>,
    songs: List<Song>,
    onMatchClick: (Long) -> Unit,
    eliminationRounds: List<BracketRound> = emptyList(),
    modifier: Modifier = Modifier
) {
    when (method) {
        "ELIMINATION" -> {
            if (eliminationRounds.isNotEmpty()) {
                BracketView(rounds = eliminationRounds, onMatchClick = onMatchClick, modifier = modifier)
            } else {
                Text(
                    text = "Eleme fikstürü henüz oluşmadı — motor sonucu bekleniyor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = modifier.padding(16.dp)
                )
            }
        }
        else -> RoundByRoundFixture(
            matches = matches,
            songs = songs,
            onMatchClick = onMatchClick,
            modifier = modifier
        )
    }
}

/** LEAGUE / SWISS / EMRE_CORRECT / MERGE_SORT ortak görünümü: tur tur eşleşme listesi. */
@Composable
private fun RoundByRoundFixture(
    matches: List<Match>,
    songs: List<Song>,
    onMatchClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (matches.isEmpty()) {
        Text(
            text = "Fikstür henüz oluşmadı.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(16.dp)
        )
        return
    }

    val songById = remember(songs) { songs.associateBy { it.id } }
    val roundGroups = remember(matches) {
        matches
            .groupBy { it.round }
            .toSortedMap()
            .map { (round, roundMatches) ->
                round to roundMatches.sortedBy { it.matchNumber }
            }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        roundGroups.forEach { (round, roundMatches) ->
            item(key = "round-header-$round") {
                Text(
                    text = "$round. Tur",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(roundMatches, key = { it.id }) { match ->
                FixtureMatchRow(
                    match = match,
                    song1 = songById[match.songId1],
                    song2 = songById[match.songId2],
                    onClick = { onMatchClick(match.id) }
                )
            }
        }
    }
}

@Composable
private fun FixtureMatchRow(
    match: Match,
    song1: Song?,
    song2: Song?,
    onClick: () -> Unit
) {
    val tiklanabilir = !match.isCompleted
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (tiklanabilir) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (match.isCompleted)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FixtureTeamLabel(
                name = song1?.name ?: "—",
                kazandi = match.isCompleted && match.winnerId == match.songId1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "VS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            FixtureTeamLabel(
                name = song2?.name ?: "—",
                kazandi = match.isCompleted && match.winnerId == match.songId2,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FixtureTeamLabel(
    name: String,
    kazandi: Boolean,
    textAlign: TextAlign = TextAlign.Start,
    modifier: Modifier = Modifier
) {
    Text(
        text = name,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (kazandi) FontWeight.Bold else FontWeight.Normal,
        color = if (kazandi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier
    )
}

// ─────────────────────────────────────────────────────────────────────────
// Preview verisi — motor olmadan görsel doğrulama
// ─────────────────────────────────────────────────────────────────────────

private fun previewSong(id: Long, name: String) =
    Song(id = id, name = name, listId = 1L)

private fun previewLeagueMatches(): Pair<List<Match>, List<Song>> {
    val songs = listOf(
        previewSong(1, "Sil Baştan"), previewSong(2, "Perdeler"),
        previewSong(3, "Dünya"), previewSong(4, "Aşk")
    )
    val matches = listOf(
        Match(id = 1, listId = 1, rankingMethod = "LEAGUE", songId1 = 1, songId2 = 2, winnerId = 1, score1 = 3, score2 = 1, round = 1, matchNumber = 1, isCompleted = true),
        Match(id = 2, listId = 1, rankingMethod = "LEAGUE", songId1 = 3, songId2 = 4, winnerId = null, round = 1, matchNumber = 2, isCompleted = false),
        Match(id = 3, listId = 1, rankingMethod = "LEAGUE", songId1 = 1, songId2 = 3, winnerId = null, round = 2, matchNumber = 3, isCompleted = false),
        Match(id = 4, listId = 1, rankingMethod = "LEAGUE", songId1 = 2, songId2 = 4, winnerId = null, round = 2, matchNumber = 4, isCompleted = false)
    )
    return matches to songs
}

@Preview(showBackground = true, widthDp = 380, name = "FixturePanel — LEAGUE tur tur")
@Composable
private fun FixturePanelPreviewLeague() {
    val (matches, songs) = previewLeagueMatches()
    FixturePanel(method = "LEAGUE", matches = matches, songs = songs, onMatchClick = {})
}

@Preview(showBackground = true, widthDp = 380, heightDp = 500, name = "FixturePanel — ELIMINATION (BracketView'a devreder)")
@Composable
private fun FixturePanelPreviewElimination() {
    val rounds = listOf(
        BracketRound(1, bracketRoundTitle(4), listOf(
            BracketMatch(1, 1, 1, BracketTeam(1, "Sil Baştan", 1, 3, true), BracketTeam(2, "Perdeler", 2, 1), true, true),
            BracketMatch(2, 1, 2, BracketTeam(3, "Dünya", 3), BracketTeam(4, "Aşk", 4), false, true)
        )),
        BracketRound(2, bracketRoundTitle(2), listOf(
            BracketMatch(3, 2, 3, null, null, false, false)
        ))
    )
    FixturePanel(
        method = "ELIMINATION",
        matches = emptyList(),
        songs = emptyList(),
        onMatchClick = {},
        eliminationRounds = rounds
    )
}

@Preview(showBackground = true, widthDp = 380, name = "FixturePanel — ELIMINATION (motor henüz yok)")
@Composable
private fun FixturePanelPreviewEliminationEmpty() {
    FixturePanel(method = "ELIMINATION", matches = emptyList(), songs = emptyList(), onMatchClick = {})
}
