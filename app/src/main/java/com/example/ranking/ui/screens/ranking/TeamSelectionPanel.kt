package com.example.ranking.ui.screens.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ranking.data.Song
import com.example.ranking.ranking.EmreSystemCorrect

/**
 * Bir değerin "uzun metin" sayılması için gereken karakter sayısı.
 *
 * Şarkı sözü, özet, biyografi gibi alanlar etiket/değer satırına sığmaz:
 * 670 karakterlik bir söz, satırın %58'lik değer sütununa sıkıştırılınca
 * kart okunamaz hâle geliyordu. Bu eşiği aşan (ya da satır sonu içeren)
 * değerler kendi kaydırılabilir panelinde gösterilir.
 */
private const val UZUN_METIN_ESIGI = 90

/** Uzun metin panelinin en fazla kaplayacağı yükseklik; gerisi kaydırılır. */
private val UZUN_METIN_YUKSEKLIGI = 170.dp

// MatchBasedContent içindeki iki takım paneli (song1/song2) için ortak bileşen.
// Fark gösteren her şey parametre: takım, çerçeve rengi ve tıklama davranışı.
@Composable
internal fun TeamSelectionPanel(
    song: Song?,
    method: String,
    emreState: EmreSystemCorrect.EmreState?,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp) // Çerçeve için boşluk
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                RoundedCornerShape(12.dp)
            )
            .border(
                3.dp,
                borderColor,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
    ) {
        song?.let { team ->
            // PUAN ROZETİ - SAĞ ALT KÖŞE (TÜM USULLER)
            val currentPoints = when (method) {
                "EMRE_CORRECT" -> {
                    if (emreState?.teams?.isNotEmpty() == true) {
                        emreState.teams.find { it.song.id == team.id }?.points ?: 0.0
                    } else {
                        0.0
                    }
                }
                else -> 0.0 // Diğer usuller için genişletilebilir
            }

            if (currentPoints > 0.0 || method == "EMRE_CORRECT") {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 8.dp, y = 8.dp)  // Offset - merkezini köşeye yerleştir
                        .size(32.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (currentPoints % 1.0 == 0.0) "${currentPoints.toInt()}" else "${currentPoints}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            // CSV data'yı önceden hesapla
            val csvData = team.csvData
            val detayRows = remember(csvData, team.name) { cardDetailRows(csvData, team.name) }
            val imageUrl = remember(csvData) { extractImageUrl(csvData) }

            // Kısa öznitelikler ile uzun metinler AYRILIR: ikisi aynı satır
            // biçiminde gösterilince uzun olan kartı ezip kısa olanları
            // okunmaz hâle getiriyor.
            val kisaSatirlar = remember(detayRows) {
                detayRows.filter { (_, deger) ->
                    deger.length <= UZUN_METIN_ESIGI && !deger.contains('\n')
                }
            }
            val uzunMetinler = remember(detayRows) {
                detayRows.filter { (_, deger) ->
                    deger.length > UZUN_METIN_ESIGI || deger.contains('\n')
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {

                // BAŞLIK ŞERİDİ — öğenin adı kartın kendi başlık çubuğunda.
                //
                // Ad eskiden hem kartın DIŞINDAKİ ayrı başlık bloğunda hem de
                // kartın içinde yazılıyordu; aynı bilgi iki kez görünüyor ve
                // dar ekranda yer yiyordu. Dıştaki blok kaldırıldı, ad buraya
                // taşındı: kartın üstüne yapışık, tam genişlikte mavi şerit.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            // Üst köşeler kart çerçevesine oturur (12dp - 3dp kenarlık)
                            RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = team.name.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (imageUrl != null) {
                    item {
                        ItemImage(
                            imageUrl = imageUrl,
                            contentDescription = team.name,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                // "▶ Dinle" — yalnız müzik listelerinde.
                // Puanlama sırasında "bu şarkı hangisiydi?" sorusunun cevabı.
                if (muzikOgesiMi(csvData)) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            DinleButonu(song = team)
                        }
                    }
                }

                if (kisaSatirlar.isNotEmpty()) {
                    item {
                        HorizontalDivider(
                            color = borderColor.copy(alpha = 0.35f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    items(kisaSatirlar) { (key, value) ->
                        DetayRow(key = key, value = value)
                    }
                }

                // Uzun metinler kendi kaydırılabilir panellerinde
                items(uzunMetinler) { (baslik, metin) ->
                    UzunMetinPaneli(
                        baslik = baslik,
                        metin = metin,
                        vurguRengi = borderColor
                    )
                }

                if (detayRows.isEmpty()) {
                    // CSV'siz öğelerde sanatçı/albüm alanları alt bilgi olur
                    val altBilgi = listOfNotNull(
                        team.artist.takeIf { it.isNotBlank() },
                        team.album.takeIf { it.isNotBlank() }
                    )
                    if (altBilgi.isNotEmpty()) {
                        item {
                            Text(
                                text = altBilgi.joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } // LazyColumn
            } // Column (başlık şeridi + içerik)
        }
    }
}

/**
 * Uzun metin (şarkı sözü, özet, biyografi) için KENDİ KAYDIRMASI olan panel.
 *
 * Yükseklik sınırlıdır; metin sığmazsa panelin içinde kaydırılarak okunur,
 * kartın kendisi uzayıp diğer bilgileri ekrandan atmaz.
 *
 * ⚠️ `heightIn(max = ...)` şart: bu panel LazyColumn öğesinin içinde duruyor
 * ve orada gelen yükseklik kısıtı SONSUZ. Sınır konmazsa `verticalScroll`
 * ölçüm sırasında sonsuz yükseklikle karşılaşır.
 */
@Composable
private fun UzunMetinPaneli(
    baslik: String,
    metin: String,
    vurguRengi: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                RoundedCornerShape(10.dp)
            )
            .border(
                1.dp,
                vurguRengi.copy(alpha = 0.30f),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = baslik,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = vurguRengi
            )
            // Kaydırma ipucu: panel içeriği sığmadığında kullanıcı metnin
            // devamı olduğunu bilmezse okumayı denemiyor
            Text(
                text = "↕ kaydır",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        HorizontalDivider(
            color = vurguRengi.copy(alpha = 0.20f),
            thickness = 1.dp,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = UZUN_METIN_YUKSEKLIGI)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = metin,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )
        }
    }
}

/**
 * Kart detay satırı: solda soluk etiket, sağda okunur değer.
 * İkisi eşit ağırlıkta yazılırsa göz hangisinin bilgi olduğunu seçemez.
 */
@Composable
private fun DetayRow(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f)
        )
    }
}
