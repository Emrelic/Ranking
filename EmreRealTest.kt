import com.example.ranking.data.Song
import com.example.ranking.data.Match
import com.example.ranking.ranking.EmreSystemCorrect
import kotlin.random.Random

/**
 * Gerçek 79 Sayı ile Emre Usulü Test
 * Büyük sayı daha iyi algoritması
 */
object EmreRealTest {
    
    // 79 rastgele sayı (100-999 arası)
    private val testScores = listOf(
        856, 923, 745, 812, 634, 798, 567, 891, 723, 654,
        789, 432, 876, 543, 712, 698, 534, 823, 456, 734,
        612, 887, 523, 756, 645, 834, 567, 723, 812, 456,
        689, 723, 534, 867, 612, 745, 523, 834, 687, 523,
        756, 834, 612, 745, 687, 523, 834, 612, 756, 687,
        523, 834, 612, 756, 687, 834, 523, 612, 756, 687,
        834, 523, 612, 756, 687, 834, 523, 612, 756, 687,
        834, 523, 612, 756, 687, 834, 523, 612, 756
    )
    
    fun main() {
        println("=== 79 TAKIMLI GERÇEK EMRE USULÜ TEST ===")
        println("Büyük sayı = daha iyi performans")
        
        // 79 takım oluştur
        val songs = testScores.mapIndexed { index, score ->
            Song(
                id = (index + 1).toLong(),
                name = "Team${index + 1}",
                artist = "Artist${index + 1}",
                album = "Score: $score",
                trackNumber = index + 1,
                listId = 1L
            )
        }
        
        println("Başlangıç takımları (ilk 10):")
        songs.take(10).forEach { song ->
            val score = testScores[song.trackNumber - 1]
            println("${song.name} - Skor: $score")
        }
        
        println("Son 10 takım:")
        songs.takeLast(10).forEach { song ->
            val score = testScores[song.trackNumber - 1]
            println("${song.name} - Skor: $score")
        }
        
        // Turnuvayı başlat
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        var roundNumber = 1
        
        while (roundNumber <= 10) { // Maksimum 10 tur
            println("\n=== TUR $roundNumber ===")
            
            // Sonraki turu oluştur
            val pairingResult = EmreSystemCorrect.createNextRound(state)
            
            if (!pairingResult.canContinue) {
                println("✅ Turnuva tamamlandı! Hiçbir aynı puanlı eşleşme kalmadı.")
                break
            }
            
            println("Oluşturulan eşleşme sayısı: ${pairingResult.matches.size}")
            
            // Bye geçen takım var mı?
            pairingResult.byeTeam?.let { bye ->
                val byeScore = testScores[bye.song.trackNumber - 1]
                println("🔄 Bye geçen: ${bye.song.name} (Skor: $byeScore) (+1 puan)")
            }
            
            // Aynı puanlı eşleşme kontrolü
            println("Aynı puanlı eşleşme var mı? ${pairingResult.hasSamePointMatch}")
            
            // İlk 5 eşleşmeyi göster
            println("İlk 5 eşleşme:")
            pairingResult.matches.take(5).forEachIndexed { index, match ->
                val team1 = songs.find { it.id == match.songId1 }!!
                val team2 = songs.find { it.id == match.songId2 }!!
                val score1 = testScores[team1.trackNumber - 1]
                val score2 = testScores[team2.trackNumber - 1]
                val team1Points = state.teams.find { it.id == match.songId1 }?.points ?: 0.0
                val team2Points = state.teams.find { it.id == match.songId2 }?.points ?: 0.0
                val samePoint = if (team1Points == team2Points) "✅" else "❌"
                
                println("${index + 1}. ${team1.name}($score1-${team1Points}p) vs ${team2.name}($score2-${team2Points}p) $samePoint")
            }
            
            // Maç sonuçlarını simüle et (büyük sayı kazanır)
            val simulatedMatches = pairingResult.matches.map { match ->
                val team1 = songs.find { it.id == match.songId1 }!!
                val team2 = songs.find { it.id == match.songId2 }!!
                val score1 = testScores[team1.trackNumber - 1]
                val score2 = testScores[team2.trackNumber - 1]
                
                val winnerId = when {
                    score1 > score2 -> match.songId1
                    score2 > score1 -> match.songId2
                    else -> {
                        // Eşit skorlarda rastgele veya beraberlik
                        if (Random.nextBoolean()) match.songId1 else null // %50 kazanır %50 beraberlik
                    }
                }
                
                match.copy(winnerId = winnerId)
            }
            
            // Sonuçları işle
            state = EmreSystemCorrect.processRoundResults(state, simulatedMatches, pairingResult.byeTeam)
            
            // Tur sonu durumu
            println("Tur sonu - En iyi 5 takım:")
            val topTeams = state.teams.sortedBy { it.currentPosition }.take(5)
            topTeams.forEach { team ->
                val originalScore = testScores[team.song.trackNumber - 1]
                println("  ${team.currentPosition}. ${team.song.name} - ${team.points} puan (Orijinal: $originalScore)")
            }
            
            println("En kötü 5 takım:")
            val bottomTeams = state.teams.sortedBy { it.currentPosition }.takeLast(5)
            bottomTeams.forEach { team ->
                val originalScore = testScores[team.song.trackNumber - 1]
                println("  ${team.currentPosition}. ${team.song.name} - ${team.points} puan (Orijinal: $originalScore)")
            }
            
            roundNumber++
        }
        
        // Final sonuçları
        println("\n=== FİNAL SONUÇLARI ===")
        val finalResults = EmreSystemCorrect.calculateFinalResults(state)
        
        println("İlk 10 sıralama:")
        finalResults.take(10).forEach { result ->
            val team = songs.find { it.id == result.songId }!!
            val originalScore = testScores[team.trackNumber - 1]
            println("${result.position}. ${team.name} - ${result.score} puan (Orijinal skor: $originalScore)")
        }
        
        println("\nSon 10 sıralama:")
        finalResults.takeLast(10).forEach { result ->
            val team = songs.find { it.id == result.songId }!!
            val originalScore = testScores[team.trackNumber - 1]
            println("${result.position}. ${team.name} - ${result.score} puan (Orijinal skor: $originalScore)")
        }
        
        // Analiz: Orijinal skorlara göre sıralama vs Emre usulü sıralama
        println("\n=== ANALİZ ===")
        
        // Orijinal skora göre sıralama
        val originalRanking = songs.sortedByDescending { testScores[it.trackNumber - 1] }
        
        println("Orijinal skor sıralaması (ilk 10):")
        originalRanking.take(10).forEachIndexed { index, team ->
            val originalScore = testScores[team.trackNumber - 1]
            val emrePosition = finalResults.find { it.songId == team.id }?.position ?: 0
            val positionDiff = emrePosition - (index + 1)
            val diffIndicator = when {
                positionDiff > 0 -> "⬇️ -$positionDiff"
                positionDiff < 0 -> "⬆️ +${-positionDiff}"
                else -> "➡️ 0"
            }
            println("${index + 1}. ${team.name} (Skor: $originalScore) -> Emre sırası: $emrePosition $diffIndicator")
        }
        
        // Korelasyon analizi
        val correlationData = finalResults.map { result ->
            val team = songs.find { it.id == result.songId }!!
            val originalScore = testScores[team.trackNumber - 1]
            val originalRank = originalRanking.indexOfFirst { it.id == team.id } + 1
            Pair(originalRank, result.position)
        }
        
        // Basit korelasyon hesabı
        val avgOriginal = correlationData.map { it.first }.average()
        val avgEmre = correlationData.map { it.second }.average()
        
        val numerator = correlationData.sumOf { (orig, emre) ->
            (orig - avgOriginal) * (emre - avgEmre)
        }
        val denominator = kotlin.math.sqrt(
            correlationData.sumOf { (orig, _) -> (orig - avgOriginal) * (orig - avgOriginal) } *
            correlationData.sumOf { (_, emre) -> (emre - avgEmre) * (emre - avgEmre) }
        )
        
        val correlation = if (denominator != 0.0) numerator / denominator else 0.0
        
        println("\nKorelasyon analizi:")
        println("Orijinal skor sıralaması vs Emre usulü sıralaması")
        println("Korelasyon katsayısı: %.3f".format(correlation))
        println("(1.0 = mükemmel uyum, 0.0 = hiç uyum yok, -1.0 = tam ters)")
        
        println("\nToplam tur sayısı: ${roundNumber - 1}")
    }
}

fun main() {
    EmreRealTest.main()
}