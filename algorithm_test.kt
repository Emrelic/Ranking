/**
 * Geliştirilmiş İsviçre Sistemi Algorithm Test
 * 8 takımlı örnek turnuva ile duplicate pairing testi
 */

fun main() {
    println("=== Geliştirilmiş İsviçre Sistemi Test ===")
    println("8 takımlı turnuva simülasyonu\n")
    
    // 8 takım oluştur
    val teams = listOf(
        "1. Şarkı A", "2. Şarkı B", "3. Şarkı C", "4. Şarkı D",
        "5. Şarkı E", "6. Şarkı F", "7. Şarkı G", "8. Şarkı H"
    )
    
    // Takım puanları (başlangıçta 0)
    val points = mutableMapOf<String, Double>()
    teams.forEach { points[it] = 0.0 }
    
    // Match history - oynanan eşleşmeler
    val matchHistory = mutableSetOf<Pair<String, String>>()
    val allMatches = mutableListOf<String>()
    
    println("Başlangıç sıralaması:")
    teams.forEachIndexed { index, team -> 
        println("${index + 1}. $team - ${points[team]} puan")
    }
    
    // 7 tur simüle et
    for (round in 1..7) {
        println("\n" + "=".repeat(50))
        println("TUR $round")
        println("=".repeat(50))
        
        // Takımları puana göre sırala
        val sortedTeams = teams.sortedWith(compareByDescending<String> { points[it]!! }
            .thenBy { teams.indexOf(it) })
        
        println("\nO anki sıralama:")
        sortedTeams.forEachIndexed { index, team -> 
            println("${index + 1}. $team - ${points[team]} puan")
        }
        
        // Eşleştirme yap
        val roundMatches = createPairings(sortedTeams, matchHistory)
        
        if (roundMatches.isEmpty()) {
            println("\n❌ Eşleştirme yapılamadı - turnuva bitti")
            break
        }
        
        println("\n$round. Tur Eşleştirmeleri:")
        roundMatches.forEachIndexed { index, match ->
            println("Maç ${index + 1}: ${match.first} vs ${match.second}")
            allMatches.add("T$round: ${match.first} vs ${match.second}")
        }
        
        // Rastgele sonuçlar üret ve puanları güncelle
        roundMatches.forEach { match ->
            val winner = if ((0..1).random() == 0) match.first else match.second
            points[winner] = points[winner]!! + 1.0
            println("Kazanan: $winner")
            
            // Match history'ye ekle
            matchHistory.add(Pair(match.first, match.second))
            matchHistory.add(Pair(match.second, match.first))
        }
    }
    
    println("\n" + "=".repeat(60))
    println("TURNUVA SONUÇLARI")
    println("=".repeat(60))
    
    val finalStandings = teams.sortedWith(compareByDescending<String> { points[it]!! }
        .thenBy { teams.indexOf(it) })
    
    println("\nFinal Sıralaması:")
    finalStandings.forEachIndexed { index, team -> 
        println("${index + 1}. $team - ${points[team]} puan")
    }
    
    println("\nTüm Maçlar:")
    allMatches.forEach { println(it) }
    
    println("\nDuplicate Pairing Kontrolü:")
    checkDuplicates(allMatches)
}

fun createPairings(teams: List<String>, matchHistory: Set<Pair<String, String>>): List<Pair<String, String>> {
    val matches = mutableListOf<Pair<String, String>>()
    val usedTeams = mutableSetOf<String>()
    
    var i = 0
    while (i < teams.size) {
        val team1 = teams[i]
        
        if (team1 in usedTeams) {
            i++
            continue
        }
        
        var foundPartner = false
        var j = i + 1
        
        while (j < teams.size && !foundPartner) {
            val team2 = teams[j]
            
            if (team2 in usedTeams) {
                j++
                continue
            }
            
            // Daha önce oynamış mı kontrol et
            val hasPlayedBefore = hasTeamsPlayedBefore(team1, team2, matchHistory)
            
            if (!hasPlayedBefore) {
                matches.add(Pair(team1, team2))
                usedTeams.add(team1)
                usedTeams.add(team2)
                foundPartner = true
                break
            } else {
                j++
            }
        }
        
        if (!foundPartner) {
            usedTeams.add(team1)
            println("⚠️ $team1 bye geçti (partner bulunamadı)")
        }
        
        i++
    }
    
    return matches
}

fun hasTeamsPlayedBefore(team1: String, team2: String, matchHistory: Set<Pair<String, String>>): Boolean {
    return Pair(team1, team2) in matchHistory || Pair(team2, team1) in matchHistory
}

fun checkDuplicates(matches: List<String>) {
    val pairings = mutableMapOf<String, Int>()
    
    matches.forEach { match ->
        val teams = match.substringAfter(": ").split(" vs ").sorted()
        val pairKey = "${teams[0]} vs ${teams[1]}"
        pairings[pairKey] = (pairings[pairKey] ?: 0) + 1
    }
    
    val duplicates = pairings.filter { it.value > 1 }
    
    if (duplicates.isEmpty()) {
        println("✅ Duplicate pairing YOK - Algoritma doğru çalışıyor!")
    } else {
        println("❌ Duplicate pairing BULUNDU:")
        duplicates.forEach { (pair, count) ->
            println("  $pair - $count kez oynadı")
        }
    }
}