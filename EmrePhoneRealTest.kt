import com.example.ranking.data.Song
import com.example.ranking.data.Match
import com.example.ranking.ranking.EmreSystemCorrect

/**
 * 79 Öğeli Liste İçin Gerçek Emre Usulü Testi
 * CSV'deki rakamları kullanarak büyük sayı kazanır mantığıyla test
 */
object EmrePhoneRealTest {
    
    fun main() {
        println("=== 79 ÖĞELİ LİSTE EMRE USULÜ TESTİ ===")
        println("Büyük rakam kazanır mantığıyla puanlama")
        
        // 79 takım simülasyonu (örnek CSV verisi)
        val songs = generateSampleSongs(79)
        
        println("\n🏁 BAŞLANGIÇ:")
        println("Toplam ${songs.size} takım")
        songs.take(5).forEach { println("  ${it.name} (ID: ${it.id}) - Başlangıç sırası: ${it.position}") }
        println("  ...")
        songs.takeLast(5).forEach { println("  ${it.name} (ID: ${it.id}) - Başlangıç sırası: ${it.position}") }
        
        // Turnuvayı başlat
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        
        var roundNumber = 1
        var maxRounds = 20 // Güvenlik için
        
        println("\n" + "=".repeat(60))
        println("🎮 EMRE USULÜ TURNUVA BAŞLIYOR")
        println("=".repeat(60))
        
        while (roundNumber <= maxRounds) {
            println("\n--- TUR $roundNumber ---")
            
            // Eşleştirmeleri oluştur
            val pairingResult = EmreSystemCorrect.createNextRound(state)
            
            println("📋 Eşleştirmeler:")
            if (pairingResult.matches.isEmpty()) {
                println("  Eşleştirme bulunamadı - Turnuva bitti!")
                break
            }
            
            pairingResult.matches.forEachIndexed { index, match ->
                val team1 = songs.find { it.id == match.songId1 }
                val team2 = songs.find { it.id == match.songId2 }
                val team1Score = team1?.position ?: 0
                val team2Score = team2?.position ?: 0
                val team1Points = state.teams.find { it.id == match.songId1 }?.points ?: 0.0
                val team2Points = state.teams.find { it.id == match.songId2 }?.points ?: 0.0
                
                val samePointIcon = if (team1Points == team2Points) "✅" else "❌"
                println("  Maç ${index + 1}: ${team1?.name}($team1Score) vs ${team2?.name}($team2Score) | Puanlar: ${team1Points} vs ${team2Points} $samePointIcon")
            }
            
            // Bye geçen var mı?
            pairingResult.byeTeam?.let { bye ->
                val team = songs.find { it.id == bye.id }
                println("  🎯 BYE GEÇTİ: ${team?.name} (+1 puan)")
            }
            
            println("\n🔍 Durum kontrolü:")
            println("  Aynı puanlı eşleşme var mı: ${pairingResult.hasSamePointMatch}")
            println("  Turnuva devam edebilir: ${pairingResult.canContinue}")
            
            if (!pairingResult.canContinue) {
                println("🏁 TURNUVA BİTTİ: Hiçbir eşleşme aynı puanlı değil!")
                break
            }
            
            // Maç sonuçlarını simüle et (büyük rakam kazanır)
            val completedMatches = pairingResult.matches.map { match ->
                val team1 = songs.find { it.id == match.songId1 }
                val team2 = songs.find { it.id == match.songId2 }
                val team1Score = team1?.position ?: 0
                val team2Score = team2?.position ?: 0
                
                val winnerId = if (team1Score > team2Score) {
                    match.songId1  // Team1 büyük, kazandı
                } else if (team2Score > team1Score) {
                    match.songId2  // Team2 büyük, kazandı
                } else {
                    null  // Beraberlik
                }
                
                match.copy(winnerId = winnerId, isCompleted = true)
            }
            
            println("\n🏆 TUR $roundNumber SONUÇLARI:")
            completedMatches.forEach { match ->
                val team1 = songs.find { it.id == match.songId1 }
                val team2 = songs.find { it.id == match.songId2 }
                val winner = if (match.winnerId != null) {
                    songs.find { it.id == match.winnerId }
                } else null
                
                val result = when {
                    winner != null -> "Kazanan: ${winner.name}"
                    else -> "Beraberlik"
                }
                println("  ${team1?.name} vs ${team2?.name} → $result")
            }
            
            // Sonuçları işle
            state = EmreSystemCorrect.processRoundResults(state, completedMatches, pairingResult.byeTeam)
            
            // Mevcut sıralamayı göster
            println("\n📊 TUR $roundNumber SONRASI SIRALAMA:")
            val topTeams = state.teams.sortedBy { it.currentPosition }.take(10)
            topTeams.forEach { team ->
                val originalTeam = songs.find { it.id == team.id }
                val byeIndicator = if (team.byePassed) " [BYE]" else ""
                println("  ${team.currentPosition}. ${team.song.name} - ${team.points} puan (Orijinal: ${originalTeam?.position})$byeIndicator")
            }
            
            if (state.teams.size > 10) {
                println("  ...")
                val bottomTeams = state.teams.sortedBy { it.currentPosition }.takeLast(5)
                bottomTeams.forEach { team ->
                    val originalTeam = songs.find { it.id == team.id }
                    val byeIndicator = if (team.byePassed) " [BYE]" else ""
                    println("  ${team.currentPosition}. ${team.song.name} - ${team.points} puan (Orijinal: ${originalTeam?.position})$byeIndicator")
                }
            }
            
            roundNumber++
        }
        
        // Final sonuçları
        println("\n" + "=".repeat(60))
        println("🏆 FİNAL SONUÇLARI")
        println("=".repeat(60))
        
        val finalResults = EmreSystemCorrect.calculateFinalResults(state)
        
        println("🥇 ÜST 10:")
        finalResults.take(10).forEach { result ->
            val team = songs.find { it.id == result.songId }
            println("${result.position}. ${team?.name} - ${result.score} puan (Orijinal sıra: ${team?.position})")
        }
        
        println("\n🥉 ALT 10:")
        finalResults.takeLast(10).forEach { result ->
            val team = songs.find { it.id == result.songId }
            println("${result.position}. ${team?.name} - ${result.score} puan (Orijinal sıra: ${team?.position})")
        }
        
        // Doğruluk kontrolü
        println("\n" + "=".repeat(60))
        println("✅ DOĞRULUK KONTROLLERİ")
        println("=".repeat(60))
        
        // Yüksek orijinal sayılar üstte mi?
        val topResults = finalResults.take(20)
        val highNumbersInTop = topResults.count { result ->
            val team = songs.find { it.id == result.songId }
            (team?.position ?: 0) >= 60 // Yüksek sayılar (60+)
        }
        
        println("✓ Üst 20'de yüksek sayılar: $highNumbersInTop/20")
        println("✓ Toplam tur sayısı: ${roundNumber - 1}")
        println("✓ Algoritma başarıyla tamamlandı: ${roundNumber <= maxRounds}")
        
        val success = highNumbersInTop >= 15 && roundNumber <= maxRounds
        println("\n🎯 TEST SONUCU: ${if (success) "BAŞARILI ✅" else "BAŞARISIZ ❌"}")
        println("   Yüksek sayılar beklendiği gibi üst sıralarda!")
    }
    
    private fun generateSampleSongs(count: Int): List<Song> {
        return (1..count).map { i ->
            Song(
                id = i.toLong(),
                name = "Item$i",
                artist = "Artist$i",
                album = "Album$i",
                position = i,
                listId = 1L
            )
        }
    }
}

fun main() {
    EmrePhoneRealTest.main()
}