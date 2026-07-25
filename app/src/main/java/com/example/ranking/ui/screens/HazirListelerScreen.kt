package com.example.ranking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.R
import com.example.ranking.data.HazirListe
import com.example.ranking.data.HazirListeler
import com.example.ranking.ui.viewmodel.HazirListelerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HazirListelerScreen(
    onNavigateBack: () -> Unit,
    onListImported: (Long) -> Unit,
    viewModel: HazirListelerViewModel = viewModel()
) {
    val importState by viewModel.importState.collectAsState()

    // İçe aktarma sonucu dialoglarını yönet
    when (val state = importState) {
        is HazirListelerViewModel.ImportState.Importing -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text(stringResource(R.string.hazir_importing_title)) },
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.hazir_importing_text, state.ad))
                    }
                },
                confirmButton = { }
            )
        }
        is HazirListelerViewModel.ImportState.Success -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearState() },
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text(stringResource(R.string.hazir_success_title)) },
                text = { Text(stringResource(R.string.hazir_success_text, state.ad, state.ogeSayisi)) },
                confirmButton = {
                    TextButton(onClick = {
                        val id = state.listId
                        viewModel.clearState()
                        onListImported(id)
                    }) { Text(stringResource(R.string.hazir_open_list)) }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.clearState() }) { Text(stringResource(R.string.common_close)) }
                }
            )
        }
        is HazirListelerViewModel.ImportState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearState() },
                title = { Text(stringResource(R.string.common_error)) },
                text = { Text(state.mesaj) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearState() }) { Text(stringResource(R.string.common_ok)) }
                }
            )
        }
        else -> {}
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.hazir_title)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            text = stringResource(R.string.hazir_info, HazirListeler.toplamListe),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            HazirListeler.kategoriler.forEach { kategori ->
                item(key = "kat_${kategori.ad}") {
                    Text(
                        text = kategori.ad,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                items(kategori.liste, key = { it.dosya }) { liste ->
                    HazirListeCard(
                        liste = liste,
                        onClick = { viewModel.importList(liste) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HazirListeCard(
    liste: HazirListe,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = liste.ad,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (liste.gorselli) {
                        Text(" 🖼", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Text(
                    text = liste.aciklama,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = stringResource(R.string.common_item_count, liste.ogeSayisi),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
