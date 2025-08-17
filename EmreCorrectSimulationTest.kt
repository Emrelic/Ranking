import com.example.ranking.data.Song
import com.example.ranking.data.Match
import com.example.ranking.ranking.EmreSystemCorrect

/**
 * Emre Usulü Algoritması Simülasyon Testi
 * 6 takım ile gerçek Emre Usulü kurallarını test eder
 */
object EmreCorrectSimulationTest {
    
    fun main() {
        println("=== EMRE USULÜ ALGORİTMA SİMÜLASYON TESTİ ===")
        println("(6 takım ile detaylı test)")
        
        // 6 takım oluştur (Team1=1, Team2=2, ..., Team6=6 ID'li)
        val songs = listOf(
            Song(1L, "Team1", "", "", 1, 1L),
            Song(2L, "Team2", "", "", 2, 1L),
            Song(3L, "Team3", "", "", 3, 1L),
            Song(4L, "Team4", "", "", 4, 1L),
            Song(5L, "Team5", "", "", 5, 1L),
            Song(6L, "Team6", "", "", 6, 1L)
        )
        
        println("\n🏁 BAŞLANGIÇ DURUMU:")
        songs.forEach { println("${it.name} (ID: ${it.id}) - 0 puan") }
        
        // Turnuvayı başlat
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        printCurrentStandings(state, "Başlangıç Sıralaması")
        
        // TUR 1: İlk eşleştirmeler (1-2, 3-4, 5-6)
        println("\n" + "=".repeat(50))
        println("🎮 TUR 1 - İlk Eşleştirmeler")
        println("=".repeat(50))
        
        val round1 = EmreSystemCorrect.createNextRound(state)
        
        println("📋 Eşleştirmeler:")
        round1.matches.forEachIndexed { index, match ->
            val team1 = getTeamName(songs, match.songId1)
            val team2 = getTeamName(songs, match.songId2)
            println("  Maç ${index + 1}: $team1 vs $team2")
        }
        
        println("\n🔍 Aynı puanlı eşleşme kontrolü: ${round1.hasSamePointMatch}")
        println("▶️ Turnuva devam edebilir: ${round1.canContinue}")
        
        // TUR 1 Sonuçları: Team1 > Team2, Team4 > Team3, Team5 > Team6
        val round1Results = listOf(
            round1.matches[0].copy(winnerId = round1.matches[0].songId1, isCompleted = true), // Team1 > Team2
            round1.matches[1].copy(winnerId = round1.matches[1].songId2, isCompleted = true), // Team4 > Team3
            round1.matches[2].copy(winnerId = round1.matches[2].songId1, isCompleted = true)  // Team5 > Team6
        )
        
        println("\n🏆 TUR 1 SONUÇLARI:")
        round1Results.forEach { match ->
            val team1 = getTeamName(songs, match.songId1)
            val team2 = getTeamName(songs, match.songId2)
            val winner = getTeamName(songs, match.winnerId!!)
            println("  $team1 vs $team2 → Kazanan: $winner")
        }
        
        // Sonuçları işle
        state = EmreSystemCorrect.processRoundResults(state, round1Results, round1.byeTeam)
        printCurrentStandings(state, "TUR 1 Sonrası")
        
        // TUR 2: Yeni sıraya göre eşleştirmeler
        println("\n" + "=".repeat(50))
        println("🎮 TUR 2 - Yeni Sıraya Göre Eşleştirmeler")
        println("=".repeat(50))
        
        val round2 = EmreSystemCorrect.createNextRound(state)
        
        println("📋 Eşleştirmeler (Puan bazlı):")
        round2.matches.forEachIndexed { index, match ->
            val team1 = getTeamName(songs, match.songId1)
            val team2 = getTeamName(songs, match.songId2)
            val team1Points = state.teams.find { it.id == match.songId1 }?.points ?: 0.0
            val team2Points = state.teams.find { it.id == match.songId2 }?.points ?: 0.0
            val samePointIcon = if (team1Points == team2Points) "✅ AYNI PUAN" else "❌ Farklı puan"
            println("  Maç ${index + 1}: $team1 (${team1Points}p) vs $team2 (${team2Points}p) - $samePointIcon")
        }
        
        println("\n🔍 Aynı puanlı eşleşme var mı: ${round2.hasSamePointMatch}")
        println("▶️ Turnuva devam edebilir: ${round2.canContinue}")
        
        if (round2.hasSamePointMatch) {
            println("\n✅ DOĞRU: Aynı puanlı takımlar eşleşti, tur oynanacak!")
            
            // TUR 2 Sonuçları simüle et
            val round2Results = round2.matches.mapIndexed { index, match ->
                // İlk takım kazansın
                match.copy(winnerId = match.songId1, isCompleted = true)
            }
            
            println("\n🏆 TUR 2 SONUÇLARI:")
            round2Results.forEach { match ->
                val team1 = getTeamName(songs, match.songId1)
                val team2 = getTeamName(songs, match.songId2)
                val winner = getTeamName(songs, match.winnerId!!)
                println("  $team1 vs $team2 → Kazanan: $winner")
            }
            
            // Sonuçları işle
            state = EmreSystemCorrect.processRoundResults(state, round2Results, round2.byeTeam)
            printCurrentStandings(state, "TUR 2 Sonrası")
            
            // TUR 3 Kontrol
            println("\n" + "=".repeat(50))
            println("🎮 TUR 3 - Bitiş Kontrol")
            println("=".repeat(50))
            
            val round3 = EmreSystemCorrect.createNextRound(state)
            
            if (round3.hasSamePointMatch) {
                println("▶️ Hâlâ aynı puanlı eşleşmeler var, devam edilecek...")
            } else {
                println("🏁 HİÇBİR EŞLEŞME AYNI PUAN DEĞİL - TURNUVA BİTTİ!")
            }
            
        } else {
            println("\n🏁 TURNUVA BİTTİ: Hiçbir eşleşme aynı puanlı değil!")
        }
        
        // Final Sonuçları
        println("\n" + "=".repeat(50))
        println("🏆 FİNAL SONUÇLARI")
        println("=".repeat(50))
        
        val finalResults = EmreSystemCorrect.calculateFinalResults(state)
        finalResults.forEach { result ->
            val team = songs.find { it.id == result.songId }
            println("${result.position}. ${team?.name} - ${result.score} puan")
        }
        
        // DOĞRULUK KONTROLLERİ
        println("\n" + "=".repeat(50))
        println("✅ DOĞRULUK KONTROLLERİ")
        println("=".repeat(50))
        
        // 1. İlk tur 1-2, 3-4, 5-6 eşleştirmesi yapıldı mı?
        val firstRoundCorrect = round1.matches.size == 3 &&
            round1.matches.any { it.songId1 == 1L && it.songId2 == 2L } &&
            round1.matches.any { it.songId1 == 3L && it.songId2 == 4L } &&
            round1.matches.any { it.songId1 == 5L && it.songId2 == 6L }
        
        println("✓ İlk tur sıralı eşleştirme (1-2, 3-4, 5-6): ${if (firstRoundCorrect) "DOĞRU" else "YANLIŞ"}")
        
        // 2. Galipler yukarı çıktı mı?
        val winnersOnTop = finalResults.take(3).all { result ->
            val teamId = result.songId
            teamId == 1L || teamId == 4L || teamId == 5L // Kazanan takımlar
        }
        
        println("✓ Galipler yukarıda: ${if (winnersOnTop) "DOĞRU" else "YANLIŞ"}")
        
        // 3. Puanlama doğru mu?
        val correctPoints = finalResults.all { result ->
            result.score >= 0.0 && result.score <= 3.0 // Maksimum 3 tur olabilir
        }
        
        println("✓ Puanlama sistemi: ${if (correctPoints) "DOĞRU" else "YANLIŞ"}")
        
        println("\n🎯 TEST SONUCU: Emre Usulü algoritması ${if (firstRoundCorrect && winnersOnTop && correctPoints) "BAŞARILI ✅" else "BAŞARISIZ ❌"}")
    }
    
    private fun printCurrentStandings(state: EmreSystemCorrect.EmreState, title: String) {
        println("\n📊 $title:")
        val sortedTeams = state.teams.sortedBy { it.currentPosition }
        
        sortedTeams.forEach { team ->
            val byeIndicator = if (team.byePassed) " [BYE]" else ""
            println("  ${team.currentPosition}. ${team.song.name} - ${team.points} puan$byeIndicator")
        }
    }
    
    private fun getTeamName(songs: List<Song>, id: Long): String {
        return songs.find { it.id == id }?.name ?: "Team$id"
    }
}

fun main() {
    EmreCorrectSimulationTest.main()
}