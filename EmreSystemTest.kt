import com.example.ranking.data.Song
import com.example.ranking.data.Match
import com.example.ranking.ranking.EmreSystem

/**
 * Gelişmiş Emre Usulü Sıralama Sistemi Test
 * 80 takımlı kapsamlı test senaryosu
 */
object EmreSystemTest {
    
    fun main() {
        println("=== GELİŞMİŞ EMRE USULÜ SIRALAMA SİSTEMİ TEST ===")
        println("80 takım ile kapsamlı test başlatılıyor...")
        
        // 80 takım oluştur
        val teams = createTestTeams(80)
        
        // Test senaryolarını çalıştır
        runBasicScenario(teams)
        println()
        runOddTeamsScenario(createTestTeams(79)) // Tek sayıda takım
        println()
        runSmallGroupScenario(createTestTeams(8)) // Küçük grup
    }
    
    private fun createTestTeams(count: Int): List<Song> {
        return (1..count).map { i ->
            Song(
                id = i.toLong(),
                name = "Team$i",
                artist = "Artist$i",
                album = "Album$i", 
                trackNumber = i,
                listId = 1L
            )
        }
    }
    
    private fun runBasicScenario(teams: List<Song>) {
        println("=== 80 TAKIM TEMEL SENARYO ===")
        
        // Turnuvayı başlat
        var state = EmreSystem.initializeEmreTournament(teams)
        var roundNumber = 1
        
        while (!state.isComplete && roundNumber <= 20) { // Maksimum 20 tur koruma
            println("\n--- TUR $roundNumber ---")
            println("Aktif takım sayısı: ${state.teams.size}")
            
            // Sonraki turu oluştur
            val pairingResult = EmreSystem.createNextRound(state)
            
            if (!pairingResult.canContinue) {
                println("✅ Turnuva tamamlandı! Tüm eşleşmeler bitmiş.")
                break
            }
            
            println("Oluşturulan eşleşme sayısı: ${pairingResult.matches.size}")
            
            // Bye geçen takım var mı?
            pairingResult.byeTeam?.let { bye ->
                println("🔄 Bye geçen: ${bye.song.name} (+1 puan)")
            }
            
            // Rastgele sonuçlar oluştur (simülasyon için)
            val simulatedMatches = simulateRoundResults(pairingResult.matches)
            
            // Sonuçları işle
            state = EmreSystem.processRoundResults(state, simulatedMatches, pairingResult.byeTeam)
            
            // Durumu göster
            showCurrentStandings(state, 10) // İlk 10 sıra
            
            roundNumber++
        }
        
        // Final sonuçları
        println("\n=== FİNAL SONUÇLARI ===")
        val finalResults = EmreSystem.calculateFinalResults(state)
        
        println("İlk 10 sıralama:")
        finalResults.take(10).forEach { result ->
            val team = teams.find { it.id == result.songId }
            println("${result.position}. ${team?.name} - ${result.score} puan")
        }
        
        if (finalResults.size > 20) {
            println("\nSon 10 sıralama:")
            finalResults.takeLast(10).forEach { result ->
                val team = teams.find { it.id == result.songId }
                println("${result.position}. ${team?.name} - ${result.score} puan")
            }
        }
        
        println("\nToplam tur sayısı: ${roundNumber - 1}")
        println("Turnuva durumu: ${EmreSystem.checkTournamentStatus(state)}")
    }
    
    private fun runOddTeamsScenario(teams: List<Song>) {
        println("=== 79 TAKIM (TEK SAYI) SENARYO ===")
        
        var state = EmreSystem.initializeEmreTournament(teams)
        var roundNumber = 1
        var totalByeCount = 0
        
        // İlk birkaç turu simüle et
        repeat(3) {
            val pairingResult = EmreSystem.createNextRound(state)
            
            pairingResult.byeTeam?.let { 
                totalByeCount++
                println("Tur $roundNumber - Bye: ${it.song.name}")
            }
            
            val simulatedMatches = simulateRoundResults(pairingResult.matches)
            state = EmreSystem.processRoundResults(state, simulatedMatches, pairingResult.byeTeam)
            
            roundNumber++
        }
        
        println("Toplam bye geçen: $totalByeCount")
        println("✅ Tek sayıda takım senaryosu tamamlandı")
    }
    
    private fun runSmallGroupScenario(teams: List<Song>) {
        println("=== 8 TAKIM KÜÇÜK GRUP SENARYO ===")
        
        var state = EmreSystem.initializeEmreTournament(teams)
        var roundNumber = 1
        
        while (!state.isComplete && roundNumber <= 10) {
            println("\n--- Tur $roundNumber ---")
            
            val pairingResult = EmreSystem.createNextRound(state)
            
            if (!pairingResult.canContinue) {
                println("Turnuva bitti!")
                break
            }
            
            // Tüm eşleşmeleri göster
            pairingResult.matches.forEachIndexed { index, match ->
                val team1 = teams.find { it.id == match.songId1 }?.name
                val team2 = teams.find { it.id == match.songId2 }?.name
                println("Eşleşme ${index + 1}: $team1 vs $team2")
            }
            
            val simulatedMatches = simulateRoundResults(pairingResult.matches)
            state = EmreSystem.processRoundResults(state, simulatedMatches, pairingResult.byeTeam)
            
            showCurrentStandings(state)
            
            roundNumber++
        }
        
        val finalResults = EmreSystem.calculateFinalResults(state)
        println("\n--- Final Sıralama ---")
        finalResults.forEach { result ->
            val team = teams.find { it.id == result.songId }
            println("${result.position}. ${team?.name} - ${result.score} puan")
        }
        
        println("\n✅ Küçük grup senaryosu tamamlandı")
    }
    
    private fun simulateRoundResults(matches: List<Match>): List<Match> {
        // Rastgele sonuçlar oluştur (gerçek uygulamada kullanıcı seçer)
        return matches.map { match ->
            val random = kotlin.random.Random.nextDouble()
            val winnerId = when {
                random < 0.45 -> match.songId1 // %45 team1 kazanır
                random < 0.90 -> match.songId2 // %45 team2 kazanır
                else -> null // %10 beraberlik
            }
            
            match.copy(winnerId = winnerId)
        }
    }
    
    private fun showCurrentStandings(state: EmreSystem.EmreState, limit: Int = state.teams.size) {
        val sortedTeams = state.teams.sortedWith(
            compareByDescending<EmreSystem.EmreTeam> { it.points }
                .thenBy { it.originalOrder }
        ).take(limit)
        
        println("Güncel durum:")
        sortedTeams.forEachIndexed { index, team ->
            val byeIndicator = if (team.byePassed) " [BYE]" else ""
            println("  ${index + 1}. ${team.song.name} - ${team.points} puan$byeIndicator")
        }
    }
}

// Ana fonksiyon
fun main() {
    EmreSystemTest.main()
}