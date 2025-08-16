import com.example.ranking.data.Song
import com.example.ranking.data.Match
import com.example.ranking.ranking.EmreSystemCorrect

/**
 * Hızlı Emre Sistemi Test - 6 takım ile DOĞRU algoritma
 */
object EmreQuickTest {
    
    fun main() {
        println("🧪 EMRE USULÜ SİSTEM TESTİ (DOĞRU ALGORİTMA)")
        println("=" * 50)
        
        // 6 takım oluştur
        val songs = listOf(
            Song(1L, "Team1", "", "", 1, 1L),
            Song(2L, "Team2", "", "", 2, 1L),
            Song(3L, "Team3", "", "", 3, 1L),
            Song(4L, "Team4", "", "", 4, 1L),
            Song(5L, "Team5", "", "", 5, 1L),
            Song(6L, "Team6", "", "", 6, 1L)
        )
        
        println("📊 Takımlar:")
        songs.forEach { println("   ${it.name} (ID: ${it.id})") }
        
        // Test 1: Sistem başlatma
        println("\n🔸 Test 1: Sistem Başlatma")
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        println("✅ Turnuva başlatıldı")
        println("   - Takım sayısı: ${state.teams.size}")
        println("   - İlk tur: ${state.currentRound}")
        
        // Test 2: İlk tur eşleştirmeler  
        println("\n🔸 Test 2: İlk Tur Eşleştirmeler")
        val round1 = EmreSystemCorrect.createNextRound(state)
        
        println("✅ İlk tur eşleştirmeleri:")
        round1.matches.forEachIndexed { i, match ->
            val team1 = songs.find { it.id == match.songId1 }?.name
            val team2 = songs.find { it.id == match.songId2 }?.name
            println("   Maç ${i+1}: $team1 vs $team2")
        }
        println("   - Toplam maç: ${round1.matches.size}")
        println("   - Aynı puanlı eşleşme: ${round1.hasSamePointMatch}")
        println("   - Devam edebilir: ${round1.canContinue}")
        
        // Test 3: İlk tur sonuçları
        println("\n🔸 Test 3: İlk Tur Sonuçları")
        val round1Results = round1.matches.mapIndexed { i, match ->
            // Team1, Team4, Team5 kazansın
            val winnerId = when(i) {
                0 -> match.songId1 // Team1 > Team2
                1 -> match.songId2 // Team4 > Team3  
                2 -> match.songId1 // Team5 > Team6
                else -> match.songId1
            }
            match.copy(winnerId = winnerId, isCompleted = true)
        }
        
        round1Results.forEach { match ->
            val team1 = songs.find { it.id == match.songId1 }?.name
            val team2 = songs.find { it.id == match.songId2 }?.name
            val winner = songs.find { it.id == match.winnerId }?.name
            println("   $team1 vs $team2 → Kazanan: $winner")
        }
        
        // Test 4: Sonuçları işleme
        println("\n🔸 Test 4: Sonuçları İşleme")
        state = EmreSystemCorrect.processRoundResults(state, round1Results, round1.byeTeam)
        println("✅ Sonuçlar işlendi")
        
        val sortedTeams = state.teams.sortedBy { it.currentPosition }
        println("   Yeni sıralama:")
        sortedTeams.forEach { team ->
            println("   ${team.currentPosition}. ${team.song.name} - ${team.points} puan")
        }
        
        // Test 5: İkinci tur
        println("\n🔸 Test 5: İkinci Tur")
        val round2 = EmreSystemCorrect.createNextRound(state)
        println("✅ İkinci tur eşleştirmeleri:")
        round2.matches.forEachIndexed { i, match ->
            val team1 = songs.find { it.id == match.songId1 }?.name
            val team2 = songs.find { it.id == match.songId2 }?.name
            val team1Points = state.teams.find { it.id == match.songId1 }?.points ?: 0.0
            val team2Points = state.teams.find { it.id == match.songId2 }?.points ?: 0.0
            val samePoint = if (team1Points == team2Points) "✅ AYNI PUAN" else "❌"
            println("   Maç ${i+1}: $team1 (${team1Points}p) vs $team2 (${team2Points}p) $samePoint")
        }
        println("   - Aynı puanlı eşleşme: ${round2.hasSamePointMatch}")
        println("   - Turnuva devam: ${round2.canContinue}")
        
        // Test 6: Final test
        println("\n🔸 Test 6: Final Sonuçlar")
        val finalResults = EmreSystemCorrect.calculateFinalResults(state)
        println("✅ Final sıralaması:")
        finalResults.forEach { result ->
            val team = songs.find { it.id == result.songId }
            println("   ${result.position}. ${team?.name} - ${result.score} puan")
        }
        
        // Doğruluk kontrolü
        println("\n🎯 DOĞRULUK KONTROLÜ")
        val firstRoundOk = round1.matches.size == 3
        val pairingsOk = round1.matches.any { it.songId1 == 1L && it.songId2 == 2L }
        val pointsOk = finalResults.all { it.score >= 0.0 }
        val positionsOk = finalResults.map { it.position }.sorted() == (1..6).toList()
        
        println("✓ İlk tur eşleştirme: ${if (firstRoundOk) "DOĞRU" else "YANLIŞ"}")
        println("✓ 1-2 eşleştirmesi: ${if (pairingsOk) "DOĞRU" else "YANLIŞ"}")
        println("✓ Puanlama sistemi: ${if (pointsOk) "DOĞRU" else "YANLIŞ"}")
        println("✓ Pozisyon sırası: ${if (positionsOk) "DOĞRU" else "YANLIŞ"}")
        
        val allOk = firstRoundOk && pairingsOk && pointsOk && positionsOk
        println("\n🏆 TEST SONUCU: ${if (allOk) "BAŞARILI ✅" else "BAŞARISIZ ❌"}")
        
        if (!allOk) {
            println("\n❌ HATA DETAYLARI:")
            if (!firstRoundOk) println("   - İlk tur maç sayısı yanlış: ${round1.matches.size} != 3")
            if (!pairingsOk) println("   - 1-2 eşleştirmesi bulunamadı")
            if (!pointsOk) println("   - Puanlama hatası var")
            if (!positionsOk) println("   - Pozisyon sırası bozuk")
        }
    }
    
    private fun getTeamName(songs: List<Song>, id: Long): String {
        return songs.find { it.id == id }?.name ?: "Team$id"
    }
}

fun main() {
    EmreQuickTest.main()
}