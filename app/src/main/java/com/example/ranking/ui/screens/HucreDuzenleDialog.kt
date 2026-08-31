package com.example.ranking.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Excel hücresi gibi içerik düzenleme dialogu.
 *
 * Tablodaki satır içi kutu TEK SATIRLIK — şarkı sözü gibi uzun metinler
 * orada düzenlenemiyordu. Bu dialog çok satırlı: söz, özet, açıklama gibi
 * alanlar rahatça yazılıp veritabanına kaydedilir.
 *
 * İki ekrandan çağrılır:
 *  - ListViewScreen (liste görüntüleme): kaydet → DOĞRUDAN veritabanına
 *  - ListEditScreen (Tablo Rötuşu): kaydet → ekranın kendi "kaydet"
 *    akışına (toplu kayıt düğmesiyle diske iner)
 */
@Composable
fun HucreDuzenleDialog(
    sutunAdi: String,
    ogeAdi: String,
    ilkDeger: String,
    onKaydet: (String) -> Unit,
    onKapat: () -> Unit
) {
    var deger by remember { mutableStateOf(ilkDeger) }

    AlertDialog(
        onDismissRequest = onKapat,
        title = {
            Column {
                Text(sutunAdi, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = ogeAdi,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            OutlinedTextField(
                value = deger,
                onValueChange = { deger = it },
                modifier = Modifier
                    .fillMaxWidth()
                    // Uzun metin: büyür ama ekranı taşırmaz, içi kayar
                    .heightIn(min = 56.dp, max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                placeholder = { Text("Değer girin ('-' = boş göster)") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onKaydet(deger) }) { Text("Kaydet") }
        },
        dismissButton = {
            TextButton(onClick = onKapat) { Text("İptal") }
        },
        modifier = Modifier.padding(4.dp)
    )
}
