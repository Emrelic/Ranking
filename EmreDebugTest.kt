import com.example.ranking.data.Song
import com.example.ranking.data.Match
import com.example.ranking.ranking.EmreSystem

/**
 * Emre Sistemi Debug Test - Tekrar eşleşme problemini test et
 */
object EmreDebugTest {
    
    fun main() {
        println("=== EMRE SİSTEMİ DEBUG TEST ===")
        
        // 4 takım ile test (daha kolay debug için)
        val songs = listOf(
            Song(1L, "Team1", "Artist1", "Album1", 1, 1L),
            Song(2L, "Team2", "Artist2", "Album2", 2, 1L),
            Song(3L, "Team3", "Artist3", "Album3", 3, 1L),
            Song(4L, "Team4", "Artist4", "Album4", 4, 1L)
        )
        
        println("Başlangıç takımları:")
        songs.forEach { println("- ${it.name} (ID: ${it.id})") }
        
        // Turnuvayı başlat
        var state = EmreSystem.initializeEmreTournament(songs)
        println("\nBaşlangıç durumu:")
        printState(state)
        
        // İlk tur
        println("\n=== TUR 1 ===")
        val firstRound = EmreSystem.createNextRound(state)
        
        println("İlk tur eşleşmeleri:")
        firstRound.matches.forEachIndexed { index, match ->
            val team1 = songs.find { it.id == match.songId1 }?.name
            val team2 = songs.find { it.id == match.songId2 }?.name
            println("Maç ${index + 1}: $team1 (${match.songId1}) vs $team2 (${match.songId2})")
        }
        
        // İlk tur sonuçları (Team1 ve Team3 kazansın)
        val firstRoundResults = firstRound.matches.mapIndexed { index, match ->
            val winnerId = if (index == 0) match.songId1 else match.songId1 // Her ikisinde de ilk takım kazansın
            match.copy(winnerId = winnerId)
        }
        
        println("\nİlk tur sonuçları:")
        firstRoundResults.forEach { match ->
            val team1 = songs.find { it.id == match.songId1 }?.name
            val team2 = songs.find { it.id == match.songId2 }?.name
            val winner = songs.find { it.id == match.winnerId }?.name
            println("$team1 vs $team2 -> Kazanan: $winner")
        }
        
        // Sonuçları işle
        state = EmreSystem.processRoundResults(state, firstRoundResults, firstRound.byeTeam)
        
        println("\n1. tur sonrası durum:")
        printState(state)
        
        println("\nMatç geçmişi:")
        state.matchHistory.forEach { (id1, id2) ->
            val team1 = songs.find { it.id == id1 }?.name
            val team2 = songs.find { it.id == id2 }?.name
            println("$team1 ($id1) -> $team2 ($id2)")
        }
        
        // İkinci tur
        println("\n=== TUR 2 ===")
        val secondRound = EmreSystem.createNextRound(state)
        
        println("İkinci tur eşleşmeleri:")
        secondRound.matches.forEachIndexed { index, match ->
            val team1 = songs.find { it.id == match.songId1 }?.name
            val team2 = songs.find { it.id == match.songId2 }?.name
            println("Maç ${index + 1}: $team1 (${match.songId1}) vs $team2 (${match.songId2})")
        }
        
        // Tekrar eşleşme kontrolü
        println("\n=== TEKRAR EŞLEŞME KONTROLÜ ===")
        val firstRoundPairs = firstRound.matches.map { setOf(it.songId1, it.songId2) }.toSet()
        val secondRoundPairs = secondRound.matches.map { setOf(it.songId1, it.songId2) }.toSet()
        
        val repeatedPairs = firstRoundPairs.intersect(secondRoundPairs)
        
        if (repeatedPairs.isEmpty()) {
            println("✅ Tekrar eşleşme YOK - Sistem doğru çalışıyor!")
        } else {
            println("❌ TEKRAR EŞLEŞME VAR!")
            repeatedPairs.forEach { pair ->
                val teamNames = pair.map { id -> songs.find { it.id == id }?.name }
                println("Tekrar eden: ${teamNames[0]} vs ${teamNames[1]}")
            }
        }
        
        // Eğer turnuva bittiyse
        if (state.isComplete) {
            println("\n🏁 Turnuva tamamlandı!")
            val finalResults = EmreSystem.calculateFinalResults(state)
            println("Final sıralama:")
            finalResults.forEach { result ->
                val team = songs.find { it.id == result.songId }
                println("${result.position}. ${team?.name} - ${result.score} puan")
            }
        }
    }
    
    private fun printState(state: EmreSystem.EmreState) {
        println("Tur: ${state.currentRound}")
        println("Tamamlandı: ${state.isComplete}")
        println("Takım durumları:")
        
        val sortedTeams = state.teams.sortedWith(
            compareByDescending<EmreSystem.EmreTeam> { it.points }
                .thenBy { it.originalOrder }
        )
        
        sortedTeams.forEach { team ->
            val byeIndicator = if (team.byePassed) " [BYE]" else ""
            println("  ${team.song.name}: ${team.points} puan$byeIndicator")
        }
    }
}

fun main() {
    EmreDebugTest.main()
}