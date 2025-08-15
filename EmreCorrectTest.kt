import com.example.ranking.data.Song
import com.example.ranking.data.Match
import com.example.ranking.ranking.EmreSystemCorrect

/**
 * Doğru Emre Sistemi Test - Tam algoritma testi
 */
object EmreCorrectTest {
    
    fun main() {
        println("=== DOĞRU EMRE USULÜ TEST ===")
        
        // 6 takım ile test (daha kolay takip için)
        val songs = listOf(
            Song(1L, "Team1", "Artist1", "Album1", 1, 1L),
            Song(2L, "Team2", "Artist2", "Album2", 2, 1L),
            Song(3L, "Team3", "Artist3", "Album3", 3, 1L),
            Song(4L, "Team4", "Artist4", "Album4", 4, 1L),
            Song(5L, "Team5", "Artist5", "Album5", 5, 1L),
            Song(6L, "Team6", "Artist6", "Album6", 6, 1L)
        )
        
        println("Başlangıç takımları:")
        songs.forEach { println("${it.name} (ID: ${it.id})") }
        
        // Turnuvayı başlat
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        
        println("\n=== TUR 1 ===")
        println("Başlangıç sıralaması:")
        printCurrentStandings(state)
        
        // İlk tur eşleştirmeleri
        val round1 = EmreSystemCorrect.createNextRound(state)
        
        println("\nİlk tur eşleştirmeleri (1-2, 3-4, 5-6):")
        round1.matches.forEachIndexed { index, match ->
            val team1 = getTeamName(songs, match.songId1)
            val team2 = getTeamName(songs, match.songId2)
            println("Maç ${index + 1}: $team1 vs $team2")
        }
        
        // İlk tur sonuçlarını simüle et
        // Team1 beats Team2, Team4 beats Team3, Team5 beats Team6
        val round1Results = listOf(
            round1.matches[0].copy(winnerId = round1.matches[0].songId1), // Team1 beats Team2
            round1.matches[1].copy(winnerId = round1.matches[1].songId2), // Team4 beats Team3
            round1.matches[2].copy(winnerId = round1.matches[2].songId1)  // Team5 beats Team6
        )
        
        println("\nİlk tur sonuçları:")
        round1Results.forEach { match ->
            val team1 = getTeamName(songs, match.songId1)
            val team2 = getTeamName(songs, match.songId2)
            val winner = getTeamName(songs, match.winnerId!!)
            println("$team1 vs $team2 -> Kazanan: $winner")
        }
        
        // Sonuçları işle ve yeniden sırala
        state = EmreSystemCorrect.processRoundResults(state, round1Results, round1.byeTeam)
        
        println("\n1. tur sonrası EMRE USULÜ sıralaması:")
        println("(Galipler: Team1, Team4, Team5 - Kaybedenler: Team2, Team3, Team6)")
        printCurrentStandings(state)
        
        println("\nMatç geçmişi:")
        state.matchHistory.forEach { (id1, id2) ->
            println("${getTeamName(songs, id1)} -> ${getTeamName(songs, id2)}")
        }
        
        // İkinci tur
        println("\n=== TUR 2 ===")
        val round2 = EmreSystemCorrect.createNextRound(state)
        
        println("İkinci tur eşleştirmeleri (yeni sıraya göre 1-2, 3-4, 5-6):")
        round2.matches.forEachIndexed { index, match ->
            val team1 = getTeamName(songs, match.songId1)
            val team2 = getTeamName(songs, match.songId2)
            val team1Points = state.teams.find { it.id == match.songId1 }?.points ?: 0.0
            val team2Points = state.teams.find { it.id == match.songId2 }?.points ?: 0.0
            val samePoint = if (team1Points == team2Points) "✅ AYNI PUAN" else "❌ Farklı puan"
            println("Maç ${index + 1}: $team1 (${team1Points}p) vs $team2 (${team2Points}p) - $samePoint")
        }
        
        println("\nAynı puanlı eşleşme var mı? ${round2.hasSamePointMatch}")
        println("Turnuva devam edebilir mi? ${round2.canContinue}")
        
        if (round2.hasSamePointMatch) {
            println("✅ En az bir eşleşme aynı puanda - Tur oynanacak!")
        } else {
            println("❌ Hiçbir eşleşme aynı puanda değil - Turnuva biter!")
        }
        
        // Eğer ikinci tur oynanırsa
        if (round2.canContinue) {
            // İkinci tur sonuçlarını simüle et
            val round2Results = round2.matches.mapIndexed { index, match ->
                // İlk takım kazansın
                match.copy(winnerId = match.songId1)
            }
            
            println("\nİkinci tur sonuçları:")
            round2Results.forEach { match ->
                val team1 = getTeamName(songs, match.songId1)
                val team2 = getTeamName(songs, match.songId2)
                val winner = getTeamName(songs, match.winnerId!!)
                println("$team1 vs $team2 -> Kazanan: $winner")
            }
            
            // Sonuçları işle
            state = EmreSystemCorrect.processRoundResults(state, round2Results, round2.byeTeam)
            
            println("\n2. tur sonrası sıralama:")
            printCurrentStandings(state)
        }
        
        // Final sonuçları
        println("\n=== FİNAL SONUÇLARI ===")
        val finalResults = EmreSystemCorrect.calculateFinalResults(state)
        
        finalResults.forEach { result ->
            val team = songs.find { it.id == result.songId }
            println("${result.position}. ${team?.name} - ${result.score} puan")
        }
        
        // Tekrar eşleşme kontrolü
        println("\n=== TEKRAR EŞLEŞME KONTROLÜ ===")
        if (round2.matches.isNotEmpty()) {
            val round1Pairs = round1.matches.map { setOf(it.songId1, it.songId2) }.toSet()
            val round2Pairs = round2.matches.map { setOf(it.songId1, it.songId2) }.toSet()
            
            val repeatedPairs = round1Pairs.intersect(round2Pairs)
            
            if (repeatedPairs.isEmpty()) {
                println("✅ Tekrar eşleşme YOK - Sistem doğru çalışıyor!")
            } else {
                println("❌ TEKRAR EŞLEŞME VAR:")
                repeatedPairs.forEach { pair ->
                    val teamNames = pair.map { id -> getTeamName(songs, id) }
                    println("Tekrar eden: ${teamNames.joinToString(" vs ")}")
                }
            }
        }
    }
    
    private fun printCurrentStandings(state: EmreSystemCorrect.EmreState) {
        val sortedTeams = state.teams.sortedBy { it.currentPosition }
        
        println("Mevcut sıralama:")
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
    EmreCorrectTest.main()
}