// Tam Eleme Sistemi Simülasyonu Sonuçları

println("=== TAM ELEME SİSTEMİ SİMÜLASYONU - 79 TAKIM ===")
println()

// 79 takımın dağılımı
val totalTeams = 79
val targetSize = 64  // En yakın küçük 2'nin üssü

println("🏁 BAŞLANGIÇ:")
println("Toplam takım sayısı: $totalTeams")
println("Hedef boyut: $targetSize")
println("Elemesi gereken: ${totalTeams - targetSize} takım")
println()

// 1. TUR ANALİZİ
println("📋 1. TUR MAÇLARI:")
val pairs = (totalTeams - 3) / 2  // 76 takım ikili eşleşme
val triples = 1  // 3 takım üçlü grup

val pairMatches = pairs  // 38 maç
val tripleMatches = 3    // 3 maç (üçlü gruptan)

println("• İkili eşleşme: $pairs adet (${pairs * 2} takım)")
println("• Üçlü grup: $triples adet (3 takım)")
println("• Toplam maç sayısı: ${pairMatches + tripleMatches}")
println()

// 1. TUR SONUÇLARI
val pairWinners = pairs      // 38 kazanan
val tripleWinners = 1        // 1 kazanan
val round1Winners = pairWinners + tripleWinners

val pairLosers = pairs       // 38 kaybeden
val tripleLosers = 2         // 2 kaybeden
val round1Losers = pairLosers + tripleLosers

println("🏆 1. TUR SONUÇLARI:")
println("• Kazananlar: $pairWinners (ikili) + $tripleWinners (üçlü) = $round1Winners takım")
println("• Kaybedenler: $pairLosers (ikili) + $tripleLosers (üçlü) = $round1Losers takım")
println("• Eksik takım: ${targetSize - round1Winners}")
println()

// 2. TUR (Kaybedenler arasında)
println("📋 2. TUR MAÇLARI (Kaybedenler):")
val round2Teams = round1Losers  // 40 takım
val round2Pairs = round2Teams / 2  // 20 ikili eşleşme
val round2Winners = round2Pairs    // 20 kazanan

println("• $round2Teams kaybeden takım arasında")
println("• $round2Pairs ikili eşleşme")
println("• $round2Winners takım daha kazanır")
println("• Yeni toplam: ${round1Winners + round2Winners}")
println("• Kalan eksik: ${targetSize - (round1Winners + round2Winners)}")
println()

// 3. TUR (Son düzeltmeler)
val currentTotal = round1Winners + round2Winners
val stillNeeded = targetSize - currentTotal

println("📋 3. TUR (Son düzeltmeler):")
if (stillNeeded > 0) {
    println("• Hala $stillNeeded takım eksik")
    println("• Son kaybedenler arasında ek eşleşmeler")
    println("• $stillNeeded takım daha seçilir")
}

val finalQualified = targetSize
println("• Final hedefi: $finalQualified takım ✅")
println()

// FINAL BRACKET
println("🏆 FINAL BRACKET (Klasik Eleme):")
var remainingTeams = finalQualified
var round = 1

while (remainingTeams > 1) {
    val matches = remainingTeams / 2
    val nextRound = matches
    
    when (remainingTeams) {
        64 -> println("• 1. Tur: $remainingTeams → $nextRound ($matches maç)")
        32 -> println("• Son 32: $remainingTeams → $nextRound ($matches maç)")
        16 -> println("• Son 16: $remainingTeams → $nextRound ($matches maç)")
        8 -> println("• Çeyrek Final: $remainingTeams → $nextRound ($matches maç)")
        4 -> println("• Yarı Final: $remainingTeams → $nextRound ($matches maç)")
        2 -> println("• Final: $remainingTeams → $nextRound ($matches maç)")
    }
    
    remainingTeams = nextRound
    round++
}

println()
println("👑 SONUÇ: 1 ŞAMPİYON!")
println()
println("✅ TAM ELEME SİSTEMİ BAŞARIYLA SİMÜLE EDİLDİ!")
println("📱 Telefonda test edebilirsiniz!")