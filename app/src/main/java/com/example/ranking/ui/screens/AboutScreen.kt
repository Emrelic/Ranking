package com.example.ranking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("Hakkında") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Başlık
            Text(
                text = "Ranking Pro",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Gelişmiş Sıralama ve Değerlendirme Sistemi",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Sistem tanımı
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Ranking Sistemi Nedir?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Liste şeklinde içeri atılmış veya teker teker girilmiş çeşitli öğeleri sıralamak, puanlamak, birbiri arasında kıyaslamak veya turnuva şeklinde eşleşmelerini ve karşılaşmalarını sağlamak, puan durumlarını, eşleştirmelerini yönetmek, kriterlere göre değerlendirilmesini sağlamak üzere oluşturulmuş bir sistemdir.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Justify
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Kullanım amaçları
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Kullanım Amaçları",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val purposes = listOf(
                        "1. Sportif Karşılaşma - Turnuva ve lig yönetimi",
                        "2. Sübjektif Değerlendirme - Kişisel tercih sıralamaları", 
                        "3. Kriter Değerlendirmesi - Çok kriterli karar verme",
                        "4. Farkındalık - Öz farkındalık geliştirme",
                        "5. Eğitim - Eğitim amaçlı sıralama ve karşılaştırma",
                        "6. Hafıza Tazeleme - Bilgi pekiştirme",
                        "7. Matematiksel Sıralama - Sayısal veri analizi",
                        "8. Dikkat ve Odaklanma - Konsantrasyon geliştirme",
                        "9. Eğlence - Eğlenceli aktiviteler"
                    )
                    
                    purposes.forEach { purpose ->
                        Text(
                            text = purpose,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Örnek kullanımlar
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Örnek Kullanımlar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val examples = listOf(
                        "🏆 Sportif: 18 takımın turnuva eşleşmeleri ve puan durumları",
                        "🎵 Müzik: Şarkıcının 80 şarkısını en iyiden kötüye sıralama",
                        "👑 Tarih: Padişahları belirli kriterlere göre değerlendirme",
                        "🔢 Matematik: Rastgele sayıları sıralama ve tutarlılık testi",
                        "🌍 Eğitim: Ülkeleri ekonomik göstergelere göre sıralama",
                        "⭐ Estetik: Artistleri güzellik sıralamasına sokma",
                        "⚽ Spor: Futbolcu preferanslarını kıyaslama",
                        "💰 Finans: BIST 100 hisselerini analiz etme",
                        "🏪 Ticaret: Ürün tercihlerini kriter bazlı değerlendirme"
                    )
                    
                    examples.forEach { example ->
                        Text(
                            text = example,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Footer
            Text(
                text = "Bu uygulama çeşitli sıralama ve değerlendirme ihtiyaçlarınız için tasarlanmıştır.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}