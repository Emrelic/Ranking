package com.example.ranking.ui.screens.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────
// Motor henüz bağlanmadı — bu tipler bu dosyaya özel, koordinatör
// entegrasyonda gerçek motor tiplerinden buraya eşler (oturumlar/ELEME-EKRANI.md).
// ─────────────────────────────────────────────────────────────────────────

data class GroupStandingEntry(
    val songId: Long,
    val name: String,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val average: Int,
    val points: Int,
    val position: Int,      // grup içi sıra, 1-tabanlı
    val advances: Boolean   // tur atlayan üst k takımdan biri mi
)

data class BracketGroup(
    val groupId: Int,
    val groupName: String,  // "A Grubu"
    val standings: List<GroupStandingEntry>
)

/**
 * Grup + eleme kipinde grup içi lig tablosu. Her grup ayrı kart; tur atlayan
 * üst k takım yeşil şeritle ayrılır, elenenler soluk kalır — "kim geçiyor"
 * tek bakışta okunmalı.
 */
@Composable
fun GroupStandingsView(
    groups: List<BracketGroup>,
    modifier: Modifier = Modifier
) {
    if (groups.isEmpty()) {
        Text(
            text = "Grup puan durumu henüz oluşmadı.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(16.dp)
        )
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        groups.forEach { group -> GroupCard(group) }
    }
}

@Composable
private fun GroupCard(group: BracketGroup) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = group.groupName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Uzun takım adları kırpılmasın diye satır Ellipsis ile korunuyor;
            // yine de dar ekranlarda tabloyu yatay kaydırılabilir tutuyoruz.
            Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                GroupHeaderRow()
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                group.standings.forEach { entry -> GroupStandingRow(entry) }
            }
        }
    }
}

@Composable
private fun GroupHeaderRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HeaderCell("#", 24.dp)
        Text(
            text = "Takım",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .width(140.dp)
                .padding(start = 4.dp)
        )
        listOf("O", "G", "B", "M", "A", "P").forEach { baslik ->
            HeaderCell(baslik, 32.dp, vurgulu = baslik == "P")
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp, vurgulu: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = if (vurgulu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(width)
    )
}

@Composable
private fun GroupStandingRow(entry: GroupStandingEntry) {
    Column {
        Row(
            modifier = Modifier
                .background(
                    if (entry.advances)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(4.dp)
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${entry.position}",
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                fontWeight = if (entry.advances) FontWeight.Bold else FontWeight.Normal,
                color = if (entry.advances) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp)
            )
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (entry.advances) FontWeight.Bold else FontWeight.Normal,
                color = if (entry.advances) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .width(140.dp)
                    .padding(start = 4.dp)
            )
            BodyCell("${entry.played}", 32.dp)
            BodyCell("${entry.won}", 32.dp)
            BodyCell("${entry.drawn}", 32.dp)
            BodyCell("${entry.lost}", 32.dp)
            BodyCell("${entry.average}", 32.dp)
            BodyCell("${entry.points}", 32.dp, vurgulu = true)
        }

        // "Kim geçiyor" çizgisi: tur atlayan son takımın altına ince bir
        // yeşil şerit. Bu, MatchingsList.kt'deki (Color(0xFF388E3C)) gibi
        // anlam taşıyan sabit bir vurgu rengidir — geçiş/elenme evrensel
        // yeşil/soluk kuralına uyar, MaterialTheme.primary marka rengine
        // (mavi) bağlı kalırsa "geçti" anlamı taşımaz.
        if (entry.advances) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 4.dp),
                thickness = 2.dp,
                color = if (androidx.compose.foundation.isSystemInDarkTheme()) GecisSeridiKoyu else GecisSeridiAcik
            )
        }
    }
}

// Koyu/açık için iki ayrı ton: açık zeminde koyu yeşil (yeterli kontrast),
// koyu zeminde daha canlı yeşil (yeterli kontrast).
private val GecisSeridiAcik = androidx.compose.ui.graphics.Color(0xFF2E7D32)
private val GecisSeridiKoyu = androidx.compose.ui.graphics.Color(0xFF66BB6A)

@Composable
private fun BodyCell(text: String, width: Dp, vurgulu: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        fontWeight = if (vurgulu) FontWeight.Bold else FontWeight.Normal,
        color = if (vurgulu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(width)
    )
}

// ─────────────────────────────────────────────────────────────────────────
// Preview verisi — motor olmadan görsel doğrulama
// ─────────────────────────────────────────────────────────────────────────

private fun entry(pos: Int, name: String, o: Int, g: Int, b: Int, m: Int, a: Int, p: Int, gecer: Boolean) =
    GroupStandingEntry(
        songId = pos.toLong(), name = name, played = o, won = g, drawn = b, lost = m,
        average = a, points = p, position = pos, advances = gecer
    )

private fun previewGroupsN8(): List<BracketGroup> = listOf(
    BracketGroup(1, "A Grubu", listOf(
        entry(1, "Sil Baştan", 3, 2, 1, 0, 4, 7, true),
        entry(2, "Perdeler", 3, 2, 0, 1, 2, 6, true),
        entry(3, "Dünya", 3, 1, 0, 2, -1, 3, false),
        entry(4, "Aşk", 3, 0, 1, 2, -5, 1, false)
    )),
    BracketGroup(2, "B Grubu", listOf(
        entry(1, "Od", 3, 3, 0, 0, 6, 9, true),
        entry(2, "Girdap", 3, 1, 1, 1, 0, 4, true),
        entry(3, "Utangaç", 3, 1, 0, 2, -2, 3, false),
        entry(4, "Yarım", 3, 0, 1, 2, -4, 1, false)
    ))
)

private fun previewGroupsN12(): List<BracketGroup> = listOf(
    BracketGroup(1, "A Grubu", listOf(
        entry(1, "Koridor", 3, 3, 0, 0, 5, 9, true),
        entry(2, "Vicdan", 3, 1, 1, 1, 1, 4, true),
        entry(3, "Son Tango", 3, 1, 0, 2, -2, 3, false),
        entry(4, "Kıramazsın", 3, 0, 1, 2, -4, 1, false)
    )),
    BracketGroup(2, "B Grubu", listOf(
        entry(1, "Küllerinden", 3, 2, 1, 0, 3, 7, true),
        entry(2, "Parmak İzi", 3, 2, 0, 1, 2, 6, true),
        entry(3, "Sözde Namus", 3, 1, 0, 2, -1, 3, false),
        entry(4, "Koyu", 3, 0, 1, 2, -4, 1, false)
    )),
    BracketGroup(3, "C Grubu", listOf(
        entry(1, "Başka Bir Yol Var", 3, 3, 0, 0, 6, 9, true),
        entry(2, "Şarkılar Yalan Söylemez", 3, 1, 1, 1, 0, 4, true),
        entry(3, "Çocukken Sahip Olduğum Kırmızı Rugan Ayakkabılar", 3, 1, 0, 2, -2, 3, false),
        entry(4, "Mayın Tarlası", 3, 0, 1, 2, -4, 1, false)
    ))
)

@Preview(showBackground = true, widthDp = 420, name = "Grup Tablosu n=8 (2 grup)")
@Composable
private fun GroupStandingsViewPreviewN8() {
    GroupStandingsView(groups = previewGroupsN8())
}

@Preview(showBackground = true, widthDp = 420, name = "Grup Tablosu n=12 (3 grup)")
@Composable
private fun GroupStandingsViewPreviewN12() {
    GroupStandingsView(groups = previewGroupsN12())
}
