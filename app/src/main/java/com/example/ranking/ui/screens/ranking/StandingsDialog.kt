package com.example.ranking.ui.screens.ranking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.ranking.R
import com.example.ranking.ui.viewmodel.RankingViewModel

@Composable
internal fun StandingsDialog(
    uiState: RankingViewModel.RankingUiState,
    method: String,
    onDismiss: () -> Unit,
    onMatchResult: (Long, Long?) -> Unit = { _, _ -> },
    onShowCriteriaDialog: (Boolean) -> Unit = { }
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.standings_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.standings_dialog_placeholder))

                Spacer(modifier = Modifier.height(16.dp))

                // Butonlar kaldırıldı - Ana ekranda zaten mevcut
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}
