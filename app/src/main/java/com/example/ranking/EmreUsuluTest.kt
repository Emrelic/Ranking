package com.example.ranking

import java.io.File
import kotlin.random.Random

data class TestRankingItem(
    val id: String,
    val category: String,
    val score: Int
)

class EmreUsuluTestRunner {
    
    fun readCsvData(filePath: String): List<TestRankingItem> {
        val items = mutableListOf<TestRankingItem>()
        
        try {
            File(filePath).readLines().drop(1).forEach { line ->
                val parts = line.split(";")
                if (parts.size >= 4) {
                    val id = parts[0].trim()
                    val category = parts[2].trim()
                    val score = parts[3].trim().toIntOrNull() ?: 0
                    items.add(TestRankingItem(id, category, score))
                }
            }
        } catch (e: Exception) {
            println("Dosya okuma hatası: ${e.message}")
        }
        
        return items
    }
    
    fun performEmreUsuluRanking(items: List<TestRankingItem>): List<TestRankingItem> {
        var currentItems = items.toMutableList()
        var consecutiveWins = 0
        var roundCount = 0
        
        println("Başlangıç: ${currentItems.size} öğe")
        
        while (consecutiveWins < 2 && currentItems.size > 1) {
            roundCount++
            println("--- Round $roundCount ---")
            
            // Rastgele karıştır
            currentItems.shuffle()
            
            val pairs = mutableListOf<Pair<TestRankingItem, TestRankingItem>>()
            val winnerItems = mutableListOf<TestRankingItem>()
            
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
        
        println("Algoritma $roundCount round sonra tamamlandı!")
        return currentItems
    }
    
    fun validateRanking(finalRanking: List<TestRankingItem>): Boolean {
        println("=== Sıralama Doğrulama ===")
        
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
    
    fun runTest(filePath: String): String {
        val result = StringBuilder()
        
        result.append("CSV dosyası okunuyor...\n")
        val data = readCsvData(filePath)
        result.append("Toplam ${data.size} öğe okundu\n\n")
        
        // İlk 10 öğeyi göster
        result.append("İlk 10 öğe:\n")
        data.take(10).forEachIndexed { index, item ->
            result.append("${index + 1}. ${item.id}: ${item.category} - ${item.score}\n")
        }
        
        result.append("\nEmre usulü sıralama başlıyor...\n")
        val finalRanking = performEmreUsuluRanking(data)
        
        result.append("\nFinal Sıralama (${finalRanking.size} öğe):\n")
        finalRanking.forEachIndexed { index, item ->
            result.append("${index + 1}. ${item.id}: ${item.category} - ${item.score}\n")
        }
        
        // Doğrulama
        val isValid = validateRanking(finalRanking)
        result.append("\nSıralama geçerli: $isValid\n")
        
        return result.toString()
    }
}