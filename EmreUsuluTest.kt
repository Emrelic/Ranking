import java.io.File
import kotlin.random.Random

data class RankingItem(
    val id: String,
    val category: String,
    val score: Int
)

class EmreUsuluRanking {
    
    fun readCsvData(filePath: String): List<RankingItem> {
        val items = mutableListOf<RankingItem>()
        
        try {
            File(filePath).readLines().drop(1).forEach { line ->
                val parts = line.split(";")
                if (parts.size >= 4) {
                    val id = parts[0].trim()
                    val category = parts[2].trim()
                    val score = parts[3].trim().toIntOrNull() ?: 0
                    items.add(RankingItem(id, category, score))
                }
            }
        } catch (e: Exception) {
            println("Dosya okuma hatası: ${e.message}")
        }
        
        return items
    }
    
    fun performRanking(items: List<RankingItem>): List<RankingItem> {
        var currentItems = items.toMutableList()
        var consecutiveWins = 0
        var roundCount = 0
        
        println("Başlangıç: ${currentItems.size} öğe")
        
        while (consecutiveWins < 2 && currentItems.size > 1) {
            roundCount++
            println("\n--- Round $roundCount ---")
            
            // Rastgele karıştır
            currentItems.shuffle()
            
            val pairs = mutableListOf<Pair<RankingItem, RankingItem>>()
            val winnerItems = mutableListOf<RankingItem>()
            
            // Çiftleri oluştur
            for (i in 0 until currentItems.size - 1 step 2) {
                pairs.add(Pair(currentItems[i], currentItems[i + 1]))
            }
            
            // Tek sayıda öğe varsa otomatik geçer
            if (currentItems.size % 2 == 1) {
                winnerItems.add(currentItems.last())
                println("Tek öğe otomatik geçti: ${currentItems.last().id}")
            }
            
            // Eşleşmeleri değerlendir
            var allFirstWins = true
            pairs.forEachIndexed { index, (item1, item2) ->
                val winner = when {
                    item1.score > item2.score -> {
                        item1 // Birinci kazandı
                    }
                    item2.score > item1.score -> {
                        allFirstWins = false
                        item2 // İkinci kazandı
                    }
                    else -> {
                        // Eşitlik durumunda rastgele
                        val randomWinner = if (Random.nextBoolean()) item1 else item2
                        if (randomWinner == item2) allFirstWins = false
                        randomWinner
                    }
                }
                
                winnerItems.add(winner)
                println("Eşleşme ${index + 1}: ${item1.id}(${item1.score}) vs ${item2.id}(${item2.score}) -> Kazanan: ${winner.id}(${winner.score})")
            }
            
            // Sonlanma kriterini kontrol et
            if (allFirstWins && pairs.isNotEmpty()) {
                consecutiveWins++
                println("Bu roundda tüm birinci öğeler kazandı! Ardışık kazanma: $consecutiveWins")
            } else {
                consecutiveWins = 0
                if (pairs.isNotEmpty()) {
                    println("Bu roundda tüm birinci öğeler kazanmadı. Ardışık sayaç sıfırlandı.")
                }
            }
            
            currentItems = winnerItems
            println("Round sonu: ${currentItems.size} öğe kaldı")
        }
        
        println("\nAlgoritma $roundCount round sonra tamamlandı!")
        return currentItems
    }
    
    fun validateRanking(finalRanking: List<RankingItem>): Boolean {
        println("\n=== Sıralama Doğrulama ===")
        
        if (finalRanking.size <= 1) {
            println("Tek öğe kaldı, sıralama geçerli.")
            return true
        }
        
        // Skorları kontrol et (yüksek skorlar daha üstte olmalı)
        for (i in 0 until finalRanking.size - 1) {
            val currentScore = finalRanking[i].score
            val nextScore = finalRanking[i + 1].score
            
            if (currentScore < nextScore) {
                println("HATA: ${finalRanking[i].id}($currentScore) < ${finalRanking[i + 1].id}($nextScore)")
                return false
            }
        }
        
        println("Sıralama doğru: Yüksek skorlar üstte!")
        return true
    }
}

fun main() {
    val filePath = """C:\Users\ikizler1\OneDrive\Desktop\şebnem randomize 10000 bnlik.csv"""
    val emreUsulu = EmreUsuluRanking()
    
    println("CSV dosyası okunuyor...")
    val data = emreUsulu.readCsvData(filePath)
    println("Toplam ${data.size} öğe okundu")
    
    // İlk 10 öğeyi göster
    println("\nİlk 10 öğe:")
    data.take(10).forEachIndexed { index, item ->
        println("${index + 1}. ${item.id}: ${item.category} - ${item.score}")
    }
    
    println("\nEmre usulü sıralama başlıyor...")
    val finalRanking = emreUsulu.performRanking(data)
    
    println("\nFinal Sıralama (${finalRanking.size} öğe):")
    finalRanking.forEachIndexed { index, item ->
        println("${index + 1}. ${item.id}: ${item.category} - ${item.score}")
    }
    
    // Doğrulama
    val isValid = emreUsulu.validateRanking(finalRanking)
    println("\nSıralama geçerli: $isValid")
}