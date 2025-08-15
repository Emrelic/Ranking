import com.example.ranking.data.Song
import com.example.ranking.data.Match
import com.example.ranking.ranking.EmreSystemCorrect
import kotlin.random.Random

/**
 * Telefon Verisi ile Emre Usulü Test
 * 79 takım - gerçek telefon listesi simülasyonu
 */
object EmrePhoneTest {
    
    // Telefondaki 79 liste için gerçekçi skorlar (0-100 arası)
    private val phoneScores = generatePhoneScores()
    
    private fun generatePhoneScores(): List<Int> {
        // Gerçekçi dağılım için farklı kategorilerde skorlar
        val scores = mutableListOf<Int>()
        
        // Çok iyi performans (90-100): 8 takım
        repeat(8) { scores.add(Random.nextInt(90, 101)) }
        
        // İyi performans (80-89): 12 takım  
        repeat(12) { scores.add(Random.nextInt(80, 90)) }
        
        // Orta üstü (70-79): 15 takım
        repeat(15) { scores.add(Random.nextInt(70, 80)) }
        
        // Orta (60-69): 18 takım
        repeat(18) { scores.add(Random.nextInt(60, 70)) }
        
        // Orta altı (50-59): 14 takım
        repeat(14) { scores.add(Random.nextInt(50, 60)) }
        
        // Kötü (30-49): 10 takım
        repeat(10) { scores.add(Random.nextInt(30, 50)) }
        
        // Çok kötü (10-29): 2 takım
        repeat(2) { scores.add(Random.nextInt(10, 30)) }
        
        return scores.shuffled()
    }
    
    fun main() {
        println("=== TELEFON VERİSİ İLE EMRE USULÜ TEST ===")
        println("79 takım - Gerçekçi performans skorları")
        
        // 79 takım oluştur
        val songs = phoneScores.mapIndexed { index, score ->
            Song(
                id = (index + 1).toLong(),
                name = "Liste${index + 1}",
                artist = "Sanatçı${index + 1}",
                album = "Albüm${index + 1}",
                trackNumber = index + 1,
                listId = 1L
            )
        }
        
        // Başlangıç durumunu göster
        println("\nBaşlangıç sıralaması (performans skoruna göre ilk 15):")
        val initialRanking = songs.mapIndexed { index, song ->
            Pair(song, phoneScores[index])
        }.sortedByDescending { it.second }
        
        initialRanking.take(15).forEachIndexed { index, (song, score) ->
            println("${index + 1}. ${song.name} - Performans: $score")
        }
        
        println("\nEn düşük performanslı 10 takım:")
        initialRanking.takeLast(10).forEachIndexed { index, (song, score) ->
            val position = initialRanking.size - 10 + index + 1
            println("$position. ${song.name} - Performans: $score")
        }
        
        // Turnuvayı başlat
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        var roundNumber = 1
        
        while (roundNumber <= 15) { // Maksimum 15 tur (79 takım için yeterli)
            println("\n" + "=".repeat(50))
            println("TUR $roundNumber")
            println("=".repeat(50))
            
            // Mevcut sıralamayı göster (ilk 10)
            val currentTop10 = state.teams.sortedBy { it.currentPosition }.take(10)
            println("Mevcut ilk 10 sıralama:")
            currentTop10.forEach { team ->
                val performance = phoneScores[team.song.trackNumber - 1]
                println("  ${team.currentPosition}. ${team.song.name} - ${team.points} puan (Perf: $performance)")
            }
            
            // Sonraki turu oluştur
            val pairingResult = EmreSystemCorrect.createNextRound(state)
            
            if (!pairingResult.canContinue) {
                println("\n🏁 TURNUVA TAMAMLANDI!")
                println("Hiçbir aynı puanlı eşleşme kalmadı.")
                break
            }
            
            println("\nEşleşme bilgileri:")
            println("- Toplam eşleşme: ${pairingResult.matches.size}")
            println("- Aynı puanlı eşleşme var mı: ${if (pairingResult.hasSamePointMatch) "✅ Evet" else "❌ Hayır"}")
            
            // Bye geçen takım
            pairingResult.byeTeam?.let { bye ->
                val performance = phoneScores[bye.song.trackNumber - 1]
                println("- 🔄 Bye geçen: ${bye.song.name} (Perf: $performance) (+1 puan)")
            }
            
            // İlk 5 ve son 2 eşleşmeyi göster
            println("\nİlk 5 eşleşme:")
            pairingResult.matches.take(5).forEachIndexed { index, match ->
                val team1 = songs.find { it.id == match.songId1 }!!
                val team2 = songs.find { it.id == match.songId2 }!!
                val perf1 = phoneScores[team1.trackNumber - 1]
                val perf2 = phoneScores[team2.trackNumber - 1]
                val points1 = state.teams.find { it.id == match.songId1 }?.points ?: 0.0
                val points2 = state.teams.find { it.id == match.songId2 }?.points ?: 0.0
                val samePoint = if (points1 == points2) "✅" else "❌"
                val perfDiff = kotlin.math.abs(perf1 - perf2)
                
                println("  ${index + 1}. ${team1.name}(P:$perf1/${points1}p) vs ${team2.name}(P:$perf2/${points2}p) $samePoint [Fark:$perfDiff]")
            }
            
            if (pairingResult.matches.size > 5) {
                println("Son 2 eşleşme:")
                pairingResult.matches.takeLast(2).forEachIndexed { index, match ->
                    val team1 = songs.find { it.id == match.songId1 }!!
                    val team2 = songs.find { it.id == match.songId2 }!!
                    val perf1 = phoneScores[team1.trackNumber - 1]
                    val perf2 = phoneScores[team2.trackNumber - 1]
                    val points1 = state.teams.find { it.id == match.songId1 }?.points ?: 0.0
                    val points2 = state.teams.find { it.id == match.songId2 }?.points ?: 0.0
                    val samePoint = if (points1 == points2) "✅" else "❌"
                    
                    println("  ${pairingResult.matches.size - 1 + index}. ${team1.name}(P:$perf1/${points1}p) vs ${team2.name}(P:$perf2/${points2}p) $samePoint")
                }
            }
            
            // Maç sonuçlarını simüle et (yüksek performans avantajlı)
            val simulatedMatches = pairingResult.matches.map { match ->
                val team1 = songs.find { it.id == match.songId1 }!!
                val team2 = songs.find { it.id == match.songId2 }!!
                val perf1 = phoneScores[team1.trackNumber - 1]
                val perf2 = phoneScores[team2.trackNumber - 1]
                
                // Performans farkına göre kazanma olasılığı
                val perfDiff = perf1 - perf2
                val team1WinChance = when {
                    perfDiff >= 20 -> 0.85  // Çok büyük fark
                    perfDiff >= 10 -> 0.75  // Büyük fark
                    perfDiff >= 5 -> 0.65   // Orta fark
                    perfDiff > 0 -> 0.55    // Küçük fark
                    perfDiff == 0 -> 0.33   // Eşit (beraberlik olasılığı yüksek)
                    perfDiff >= -5 -> 0.45  // Küçük dezavantaj
                    perfDiff >= -10 -> 0.35 // Orta dezavantaj  
                    perfDiff >= -20 -> 0.25 // Büyük dezavantaj
                    else -> 0.15            // Çok büyük dezavantaj
                }
                
                val random = Random.nextDouble()
                val winnerId = when {
                    random < team1WinChance -> match.songId1
                    random < team1WinChance + (if (perfDiff == 0) 0.34 else 0.2) -> null // Beraberlik
                    else -> match.songId2
                }
                
                match.copy(winnerId = winnerId)
            }
            
            // Sonuçları işle
            state = EmreSystemCorrect.processRoundResults(state, simulatedMatches, pairingResult.byeTeam)
            
            // Tur sonu istatistikleri
            val winCount = simulatedMatches.count { it.winnerId == it.songId1 || it.winnerId == it.songId2 }
            val drawCount = simulatedMatches.count { it.winnerId == null }
            
            println("\nTur sonu istatistikleri:")
            println("- Kazanan-kaybeden: $winCount maç")
            println("- Beraberlik: $drawCount maç")
            
            roundNumber++
        }
        
        // Final analizi
        println("\n" + "=".repeat(50))
        println("FİNAL ANALİZİ")
        println("=".repeat(50))
        
        val finalResults = EmreSystemCorrect.calculateFinalResults(state)
        
        // İlk 15 takım
        println("\nEmre Usulü Final Sıralaması (İlk 15):")
        finalResults.take(15).forEach { result ->
            val team = songs.find { it.id == result.songId }!!
            val performance = phoneScores[team.trackNumber - 1]
            val originalRank = initialRanking.indexOfFirst { it.first.id == team.id } + 1
            val rankChange = originalRank - result.position
            val changeIndicator = when {
                rankChange > 0 -> "⬆️+$rankChange"
                rankChange < 0 -> "⬇️$rankChange"
                else -> "➡️0"
            }
            
            println("${result.position}. ${team.name} - ${result.score} puan (Perf: $performance) [Orijinal: $originalRank] $changeIndicator")
        }
        
        // Son 10 takım
        println("\nSon 10 Takım:")
        finalResults.takeLast(10).forEach { result ->
            val team = songs.find { it.id == result.songId }!!
            val performance = phoneScores[team.trackNumber - 1]
            val originalRank = initialRanking.indexOfFirst { it.first.id == team.id } + 1
            val rankChange = originalRank - result.position
            val changeIndicator = when {
                rankChange > 0 -> "⬆️+$rankChange"
                rankChange < 0 -> "⬇️$rankChange"
                else -> "➡️0"
            }
            
            println("${result.position}. ${team.name} - ${result.score} puan (Perf: $performance) [Orijinal: $originalRank] $changeIndicator")
        }
        
        // Korelasyon analizi
        val correlationData = finalResults.map { result ->
            val team = songs.find { it.id == result.songId }!!
            val performance = phoneScores[team.trackNumber - 1]
            val originalRank = initialRanking.indexOfFirst { it.first.id == team.id } + 1
            Triple(performance, originalRank, result.position)
        }
        
        // Performans vs Final sıralama korelasyonu
        val avgPerformance = correlationData.map { it.first }.average()
        val avgFinalRank = correlationData.map { it.third }.average()
        
        val numerator = correlationData.sumOf { (perf, _, finalRank) ->
            (perf - avgPerformance) * (finalRank - avgFinalRank)
        }
        val denominator = kotlin.math.sqrt(
            correlationData.sumOf { (perf, _, _) -> (perf - avgPerformance) * (perf - avgPerformance) } *
            correlationData.sumOf { (_, _, finalRank) -> (finalRank - avgFinalRank) * (finalRank - avgFinalRank) }
        )
        
        val perfCorrelation = if (denominator != 0.0) -numerator / denominator else 0.0 // Negatif çünkü yüksek perf = düşük sıra
        
        println("\n=== SONUÇ ANALİZİ ===")
        println("Toplam tur sayısı: ${roundNumber - 1}")
        println("Performans vs Emre Usulü Sıralaması Korelasyonu: %.3f".format(perfCorrelation))
        println("(1.0 = mükemmel uyum, 0.0 = hiç uyum yok)")
        
        if (perfCorrelation > 0.7) {
            println("✅ MÜKEMMEL: Emre usulü gerçek performansı çok iyi yansıtıyor!")
        } else if (perfCorrelation > 0.5) {
            println("✅ İYİ: Emre usulü performansı iyi yansıtıyor!")
        } else if (perfCorrelation > 0.3) {
            println("⚠️ ORTA: Emre usulü performansı kısmen yansıtıyor.")
        } else {
            println("❌ ZAYIF: Emre usulü performansı zayıf yansıtıyor.")
        }
        
        // En büyük değişiklikler
        val biggestChanges = correlationData.map { (perf, orig, final) ->
            val change = orig - final
            Triple(songs.find { phoneScores[it.trackNumber - 1] == perf }!!.name, change, perf)
        }.sortedByDescending { kotlin.math.abs(it.second) }.take(5)
        
        println("\nEn büyük sıralama değişiklikleri:")
        biggestChanges.forEach { (name, change, perf) ->
            val direction = if (change > 0) "⬆️ Yükseldi" else "⬇️ Düştü"
            println("$name (Perf: $perf) - $direction ${kotlin.math.abs(change)} sıra")
        }
    }
}

fun main() {
    EmrePhoneTest.main()
}