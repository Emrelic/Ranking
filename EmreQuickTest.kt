import com.example.ranking.data.Song
import com.example.ranking.data.Match
import com.example.ranking.ranking.EmreSystem

/**
 * Hızlı Emre Sistemi Test - 4 takım ile
 */
object EmreQuickTest {
    
    fun main() {
        println("=== HIZLI EMRE SİSTEMİ TEST ===")
        
        // 4 takım oluştur
        val songs = listOf(
            Song(1L, "Team1", "Artist1", "Album1", 1, 1L),
            Song(2L, "Team2", "Artist2", "Album2", 2, 1L),
            Song(3L, "Team3", "Artist3", "Album3", 3, 1L),
            Song(4L, "Team4", "Artist4", "Album4", 4, 1L)
        )
        
        println("Takımlar:")
        songs.forEach { println("- ${it.name} (ID: ${it.id})") }
        
        // Turnuvayı başlat
        var state = EmreSystem.initializeEmreTournament(songs)
        
        println("\n=== TUR 1 ===")
        println("Başlangıç durumu:")
        printState(state)
        
        // İlk tur
        val round1 = EmreSystem.createNextRound(state)
        
        println("\nİlk tur eşleşmeleri:")
        round1.matches.forEachIndexed { index, match ->
            println("${index + 1}. ${getTeamName(songs, match.songId1)} vs ${getTeamName(songs, match.songId2)}")
        }
        
        // İlk tur sonuçlarını simüle et (Team1 ve Team3 kazansın)
        val round1Results = round1.matches.mapIndexed { index, match ->
            match.copy(winnerId = match.songId1) // Her zaman ilk takım kazansın
        }
        
        println("\nİlk tur sonuçları:")
        round1Results.forEach { match ->
            val winner = getTeamName(songs, match.winnerId!!)
            val loser = getTeamName(songs, if (match.winnerId == match.songId1) match.songId2 else match.songId1)
            println("${getTeamName(songs, match.songId1)} vs ${getTeamName(songs, match.songId2)} -> Kazanan: $winner")
        }
        
        // Sonuçları işle
        state = EmreSystem.processRoundResults(state, round1Results, round1.byeTeam)
        
        println("\n1. tur sonrası durum:")
        printState(state)
        
        println("\nMatç geçmişi (${state.matchHistory.size} kayıt):")
        state.matchHistory.forEach { (id1, id2) ->
            println("${getTeamName(songs, id1)} -> ${getTeamName(songs, id2)}")
        }
        
        // İkinci tur
        println("\n=== TUR 2 ===")
        val round2 = EmreSystem.createNextRound(state)
        
        println("İkinci tur eşleşmeleri:")
        round2.matches.forEachIndexed { index, match ->
            println("${index + 1}. ${getTeamName(songs, match.songId1)} vs ${getTeamName(songs, match.songId2)}")
        }
        
        // Tekrar eşleşme kontrolü
        println("\n=== KONTROL ===")
        val round1Pairs = round1.matches.map { setOf(it.songId1, it.songId2) }.toSet()
        val round2Pairs = round2.matches.map { setOf(it.songId1, it.songId2) }.toSet()
        
        val repeatedPairs = round1Pairs.intersect(round2Pairs)
        
        if (repeatedPairs.isEmpty()) {
            println("✅ Tekrar eşleşme YOK!")
        } else {
            println("❌ TEKRAR EŞLEŞME VAR:")
            repeatedPairs.forEach { pair ->
                val teamNames = pair.map { id -> getTeamName(songs, id) }
                println("Tekrar eden: ${teamNames.joinToString(" vs ")}")
            }
        }
        
        // Sistem durumu
        println("\nSistem durumu:")
        println("- Turnuva tamamlandı: ${state.isComplete}")
        println("- Sonraki tur mümkün: ${round2.canContinue}")
    }
    
    private fun printState(state: EmreSystem.EmreState) {
        val sortedTeams = state.teams.sortedWith(
            compareByDescending<EmreSystem.EmreTeam> { it.points }
                .thenBy { it.originalOrder }
        )
        
        sortedTeams.forEach { team ->
            val byeIndicator = if (team.byePassed) " [BYE]" else ""
            println("  ${team.song.name}: ${team.points} puan$byeIndicator")
        }
    }
    
    private fun getTeamName(songs: List<Song>, id: Long): String {
        return songs.find { it.id == id }?.name ?: "Team$id"
    }
}

fun main() {
    EmreQuickTest.main()
}