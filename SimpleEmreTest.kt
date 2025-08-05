import java.io.File
import kotlin.random.Random

data class Item(val id: String, val category: String, val score: Int)

fun main() {
    val filePath = """C:\Users\ikizler1\OneDrive\Desktop\şebnem randomize 10000 bnlik.csv"""
    
    println("=== EMRE USULÜ TEST ===")
    println("CSV dosyası okunuyor...")
    
    // CSV'yi oku
    val items = mutableListOf<Item>()
    try {
        File(filePath).readLines().drop(1).forEach { line ->
            val parts = line.split(";")
            if (parts.size >= 4) {
                val id = parts[0].trim()
                val category = parts[2].trim()
                val score = parts[3].trim().toIntOrNull() ?: 0
                items.add(Item(id, category, score))
            }
        }
    } catch (e: Exception) {
        println("HATA: ${e.message}")
        return
    }
    
    println("Toplam ${items.size} öğe okundu")
    
    // İlk 5 ve son 5 öğeyi göster
    println("\nİlk 5 öğe:")
    items.take(5).forEach { println("${it.id}: ${it.category} - ${it.score}") }
    println("\nSon 5 öğe:")
    items.takeLast(5).forEach { println("${it.id}: ${it.category} - ${it.score}") }
    
    // Emre usulü başlat
    println("\n=== EMRE USULÜ ALGORITMA BAŞLIYOR ===")
    var currentItems = items.toMutableList()
    var consecutiveWins = 0
    var roundCount = 0
    
    println("Başlangıç: ${currentItems.size} öğe")
    
    while (consecutiveWins < 2 && currentItems.size > 1) {
        roundCount++
        println("\n--- Round $roundCount ---")
        
        // Karıştır
        currentItems.shuffle()
        
        val pairs = mutableListOf<Pair<Item, Item>>()
        val winners = mutableListOf<Item>()
        
        // Çiftleri oluştur
        for (i in 0 until currentItems.size - 1 step 2) {
            pairs.add(currentItems[i] to currentItems[i + 1])
        }
        
        // Tek öğe varsa otomatik geçer
        if (currentItems.size % 2 == 1) {
            winners.add(currentItems.last())
            println("Tek öğe otomatik geçti: ${currentItems.last().id}")
        }
        
        // Eşleşmeleri değerlendir
        var allFirstWins = true
        pairs.forEachIndexed { index, (first, second) ->
            val winner = when {
                first.score > second.score -> first
                second.score > first.score -> {
                    allFirstWins = false
                    second
                }
                else -> {
                    val randomWinner = if (Random.nextBoolean()) first else second
                    if (randomWinner == second) allFirstWins = false
                    randomWinner
                }
            }
            
            winners.add(winner)
            println("Eşleşme ${index + 1}: ${first.id}(${first.score}) vs ${second.id}(${second.score}) -> ${winner.id}(${winner.score})")
        }
        
        // Sonlanma kriteri kontrolü
        if (allFirstWins && pairs.isNotEmpty()) {
            consecutiveWins++
            println("✓ Bu roundda tüm birinci öğeler kazandı! Ardışık: $consecutiveWins")
        } else {
            consecutiveWins = 0
            if (pairs.isNotEmpty()) {
                println("✗ Birinci öğeler hepsi kazanmadı. Sayaç sıfırlandı.")
            }
        }
        
        currentItems = winners
        println("Round sonu: ${currentItems.size} öğe kaldı")
        
        // Güvenlik kontrolü (sonsuz döngü önleme)
        if (roundCount > 50) {
            println("UYARI: 50 round geçildi, algoritma durduruluyor")
            break
        }
    }
    
    println("\n=== ALGORITMA TAMAMLANDI ===")
    println("Toplam round sayısı: $roundCount")
    println("Ardışık kazanma sayısı: $consecutiveWins")
    
    // Final sonuçları
    println("\nFinal Sıralama (${currentItems.size} öğe):")
    currentItems.forEachIndexed { index, item ->
        println("${index + 1}. ${item.id}: ${item.category} - ${item.score}")
    }
    
    // Doğrulama
    println("\n=== SIRALAMA DOĞRULAMA ===")
    var isValid = true
    
    if (currentItems.size <= 1) {
        println("✓ Tek öğe kaldı, sıralama geçerli")
    } else {
        for (i in 0 until currentItems.size - 1) {
            val current = currentItems[i]
            val next = currentItems[i + 1]
            
            if (current.score < next.score) {
                println("✗ HATA: ${current.id}(${current.score}) < ${next.id}(${next.score})")
                isValid = false
            }
        }
        
        if (isValid) {
            println("✓ Sıralama doğru: Yüksek skorlar üstte!")
        }
    }
    
    println("\n=== ÖZET ===")
    println("Başlangıç öğe sayısı: ${items.size}")
    println("Final öğe sayısı: ${currentItems.size}")
    println("Round sayısı: $roundCount")
    println("Sıralama geçerli: $isValid")
    println("Sonlanma kriteri: ${if (consecutiveWins >= 2) "2 ardışık kazanma" else "Manuel durduruldu"}")
}