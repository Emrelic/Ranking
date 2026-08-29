package com.example.ranking.ui.screens.ranking

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ranking.ui.viewmodel.RankingViewModel

/**
 * SONUÇLAR dialogu — tamamlanmış eşleşmelerin listesi.
 *
 * Tur bitmeden yapılan oylamalar buradan DEĞİŞTİRİLEBİLİR; ama şartname
 * gereği bir maç ancak HER İKİ takımın da en son eşleşmesiyse düzenlenebilir
 * (kural ViewModel'de: `sonucDuzenlenebilirMi`). Düzenlenemeyen satırlar
 * soluk gösterilir ve anahtar kilitlidir.
 *
 * Sonuç ÜÇ KADEMELİ ANAHTARLA seçilir: sola yatık = SOLDAKİ takım kazanır,
 * orta = beraberlik, sağa yatık = SAĞDAKİ takım kazanır.
 */
@Composable
internal fun MacSonuclariDialog(
    uiState: RankingViewModel.RankingUiState,
    method: String,
    onDismiss: () -> Unit,
    onSonucDegistir: (Long, Long?) -> Unit
) {
    val satirlar = uiState.macSonuclari

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Maç Sonuçları")
                if (satirlar.isNotEmpty()) {
                    Text(
                        text = "${satirlar.size} oylanmış maç · yalnız takımın son maçı değiştirilebilir",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            if (satirlar.isEmpty()) {
                Text(
                    text = "Henüz oylanmış maç yok — ilk sonuç girilince burada görünecek.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(satirlar, key = { it.match.id }) { satir ->
                        MacSonucSatiriGorunumu(
                            satir = satir,
                            method = method,
                            onSonucDegistir = onSonucDegistir
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Kapat") }
        }
    )
}

@Composable
private fun MacSonucSatiriGorunumu(
    satir: RankingViewModel.MacSonucSatiri,
    method: String,
    onSonucDegistir: (Long, Long?) -> Unit
) {
    val m = satir.match
    val ad1 = satir.song1?.name ?: "?"
    val ad2 = satir.song2?.name ?: "?"
    val soluk = if (satir.duzenlenebilir) 1f else 0.45f

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (method == "LEAGUE") "${m.round}. tur · Maç ${m.matchNumber}"
                else "Maç ${m.matchNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!satir.duzenlenebilir) {
                Text(
                    text = "🔒 kilitli",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // SOL takım | üç kademeli anahtar | SAĞ takım
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = ad1,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (m.winnerId == m.songId1) FontWeight.Bold else FontWeight.Normal,
                color = if (m.winnerId == m.songId1) MaterialTheme.colorScheme.primary.copy(alpha = soluk)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = soluk),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            UcKademeliSonucAnahtari(
                kademe = when (m.winnerId) {
                    m.songId1 -> -1
                    m.songId2 -> 1
                    else -> 0
                },
                etkin = satir.duzenlenebilir,
                onKademe = { yeni ->
                    val winnerId = when (yeni) {
                        -1 -> m.songId1
                        1 -> m.songId2
                        else -> null
                    }
                    onSonucDegistir(m.id, winnerId)
                },
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Text(
                text = ad2,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (m.winnerId == m.songId2) FontWeight.Bold else FontWeight.Normal,
                color = if (m.winnerId == m.songId2) MaterialTheme.colorScheme.primary.copy(alpha = soluk)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = soluk),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Üç kademeli sonuç anahtarı.
 *
 * kademe: -1 = SOL takım kazandı (topuz solda), 0 = beraberlik (orta),
 * +1 = SAĞ takım kazandı (topuz sağda). Sol/orta/sağ bölgeye dokunmak
 * topuzu oraya "yatırır" ve sonucu değiştirir.
 */
@Composable
private fun UcKademeliSonucAnahtari(
    kademe: Int,
    etkin: Boolean,
    onKademe: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val genislik = 84.dp
    val yukseklik = 30.dp
    val topuz = 24.dp
    // Topuzun üç durağı: sol kenar / orta / sağ kenar (3dp iç pay)
    val hedefX = when {
        kademe < 0 -> 3.dp
        kademe > 0 -> genislik - topuz - 3.dp
        else -> (genislik - topuz) / 2
    }
    val topuzX by animateDpAsState(targetValue = hedefX, label = "sonucTopuzu")

    val izRengi = MaterialTheme.colorScheme.surfaceVariant
    val topuzRengi = when {
        !etkin -> MaterialTheme.colorScheme.outline
        kademe == 0 -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .width(genislik)
            .height(yukseklik)
            .background(
                color = izRengi.copy(alpha = if (etkin) 1f else 0.5f),
                shape = RoundedCornerShape(yukseklik / 2)
            )
    ) {
        // Orta durak işareti (beraberlik çizgisi) — topuz ortada değilken
        // kullanıcıya "ortası da var" ipucu verir
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 2.dp, height = 12.dp)
                .background(
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    RoundedCornerShape(1.dp)
                )
        )

        // Topuz
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = topuzX)
                .size(topuz)
                .background(topuzRengi, CircleShape)
        )

        // Üç dokunma bölgesi: sol / orta / sağ
        if (etkin) {
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .clickable { if (kademe != -1) onKademe(-1) }
                )
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .clickable { if (kademe != 0) onKademe(0) }
                )
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .clickable { if (kademe != 1) onKademe(1) }
                )
            }
        }
    }
}
