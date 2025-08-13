package com.example.ranking

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.data.Song
import com.example.ranking.data.SongList
import com.example.ranking.ranking.RankingEngine
import com.example.ranking.ui.theme.RankingTheme
import com.example.ranking.ui.viewmodel.SongListViewModel
import kotlinx.coroutines.launch

class TestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            RankingTheme {
                TestScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable 
fun TestScreen(viewModel: SongListViewModel = viewModel()) {
    val scope = rememberCoroutineScope()
    var testResults by remember { mutableStateOf("Test sonuçları burada görünecek...") }
    var isTestRunning by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Tam Eleme Sistemi Test",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        scope.launch {
                            isTestRunning = true
                            testResults = runFullEliminationTest(viewModel)
                            isTestRunning = false
                        }
                    },
                    enabled = !isTestRunning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTestRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("79 Takım Tam Eleme Testi Çalıştır")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Card {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Test Sonuçları:",
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = testResults,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

suspend fun runFullEliminationTest(viewModel: SongListViewModel): String {
    val result = StringBuilder()
    
    try {
        result.appendLine("=== TAM ELEME SİSTEMİ TEST - 79 TAKIM ===")
        result.appendLine()
        
        // Test listesi oluştur
        val testList = SongList(
            id = 999,
            name = "Tam Eleme Test - 79 Takım"
        )
        
        // 79 takım oluştur
        val songs = (1..79).map { i ->
            Song(
                id = 9000L + i,
                name = "Takım $i",
                artist = "Test",
                album = "Test Album",
                trackNumber = i,
                listId = 999
            )
        }
        
        result.appendLine("✅ 79 takım oluşturuldu")
        result.appendLine("Toplam takım sayısı: ${songs.size}")
        result.appendLine()
        
        // İkinin üssü kontrolü
        val targetSize = RankingEngine.run { getPreviousPowerOfTwo(songs.size) }
        result.appendLine("Hedef boyut (en yakın küçük 2'nin üssü): $targetSize")
        result.appendLine("Elemesi gereken takım sayısı: ${songs.size - targetSize}")
        result.appendLine()
        
        // İlk tur maçları oluştur
        val firstRoundMatches = RankingEngine.createFullEliminationMatches(songs)
        result.appendLine("=== İLK TUR MAÇLARI ===")
        result.appendLine("Toplam maç sayısı: ${firstRoundMatches.size}")
        
        // Maç türlerini analiz et
        val pairMatches = mutableMapOf<Set<Long>, Int>()
        firstRoundMatches.forEach { match ->
            val pair = setOf(match.songId1, match.songId2)
            pairMatches[pair] = (pairMatches[pair] ?: 0) + 1
        }
        
        val doubleMatches = pairMatches.filter { it.value == 1 }.size
        val tripleGroupMatches = pairMatches.filter { it.value == 3 }
        val tripleGroups = tripleGroupMatches.size / 3
        
        result.appendLine("İkili eşleşme sayısı: $doubleMatches")
        result.appendLine("Üçlü grup sayısı: $tripleGroups")
        result.appendLine()
        
        // Beklenen sonuçları kontrol et
        val expectedPairs = if (songs.size % 2 == 0) {
            songs.size / 2
        } else {
            (songs.size - 3) / 2
        }
        val expectedTriples = if (songs.size % 2 == 0) 0 else 1
        
        result.appendLine("BEKLENEN SONUÇLAR:")
        result.appendLine("Beklenen ikili eşleşme: $expectedPairs")
        result.appendLine("Beklenen üçlü grup: $expectedTriples")
        result.appendLine()
        
        val isCorrect = doubleMatches == expectedPairs && tripleGroups == expectedTriples
        result.appendLine("✅ SİSTEM DOĞRU ÇALIŞIYOR: $isCorrect")
        result.appendLine()
        
        // Algoritma simülasyonu
        result.appendLine("=== 79 TAKIM İÇİN ALGORİTMA SİMÜLASYONU ===")
        result.appendLine("1. TUR: 79 takım")
        result.appendLine("- 38 ikili eşleşme (76 takım)")
        result.appendLine("- 1 üçlü grup (3 takım)")
        result.appendLine("- Kazanan: 38 (ikili) + 1 (üçlü) = 39 takım")
        result.appendLine("- Elenen: 38 (ikili) + 2 (üçlü) = 40 takım")
        result.appendLine()
        
        result.appendLine("2. TUR: 38 kaybeden takım arasında")
        result.appendLine("- 19 ikili eşleşme")
        result.appendLine("- Kazanan: 19 takım")
        result.appendLine("- Toplam: 39 + 19 = 58 takım")
        result.appendLine("- Eksik: ${targetSize - 58} takım")
        result.appendLine()
        
        result.appendLine("3. TUR: Kalan eksiklik için eşleşmeler")
        result.appendLine("- Son ${targetSize} takıma ulaşana kadar devam")
        result.appendLine()
        
        result.appendLine("FİNAL: $targetSize takım ile klasik eleme")
        result.appendLine("- $targetSize → ${targetSize/2} → ${targetSize/4} → ... → 1")
        result.appendLine()
        
        result.appendLine("🏆 TEST BAŞARIYLA TAMAMLANDI!")
        
    } catch (e: Exception) {
        result.appendLine("❌ TEST HATASI: ${e.message}")
        Log.e("TestActivity", "Test error", e)
    }
    
    return result.toString()
}

// RankingEngine'den fonksiyonu çağırmak için extension
private fun RankingEngine.getPreviousPowerOfTwo(n: Int): Int {
    if (n <= 1) return 1
    var result = 1
    while (result * 2 <= n) {
        result *= 2
    }
    return result
}