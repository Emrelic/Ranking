package com.example.ranking.ranking

import com.example.ranking.data.EmrePairingMethod
import com.example.ranking.data.Match
import com.example.ranking.data.Song
import kotlin.math.floor

object EmrePairingEngine {
    
    /**
     * İlk tur için farklı eşleştirme metodları
     */
    fun createFirstRoundMatches(
        songs: List<Song>,
        method: EmrePairingMethod
    ): List<Match> {
        val sortedSongs = when (method) {
            EmrePairingMethod.SEQUENTIAL -> songs.sortedBy { it.id } // CSV sırasına göre (ID insertion order)
            EmrePairingMethod.RANDOM -> songs.shuffled() // Gelişigüzel karıştır
            EmrePairingMethod.ALPHABETICAL -> songs.sortedBy { it.name } // Alfabetik sıra
            EmrePairingMethod.SPLIT_HALF -> songs.sortedBy { it.id } // Sıra ile ama farklı eşleştirme
        }
        
        return when (method) {
            EmrePairingMethod.SPLIT_HALF -> createSplitHalfMatches(sortedSongs)
            else -> createSequentialMatches(sortedSongs)
        }
    }
    
    /**
     * Sıralı eşleştirme: 1-2, 3-4, 5-6...
     */
    private fun createSequentialMatches(songs: List<Song>): List<Match> {
        val matches = mutableListOf<Match>()
        val pairs = songs.size / 2
        
        for (i in 0 until pairs) {
            val song1 = songs[i * 2]
            val song2 = songs[i * 2 + 1]
            
            matches.add(
                Match(
                    listId = song1.listId,
                    rankingMethod = "EMRE",
                    songId1 = song1.id,
                    songId2 = song2.id,
                    winnerId = null,
                    round = 1
                )
            )
        }
        
        return matches
    }
    
    /**
     * Yarı-yarıya eşleştirme: 1-19, 2-20, 3-21...
     * Eğer tek sayıda takım varsa son takım bye geçer
     */
    private fun createSplitHalfMatches(songs: List<Song>): List<Match> {
        val matches = mutableListOf<Match>()
        val totalSongs = songs.size
        
        if (totalSongs <= 1) return matches
        
        // Çift sayıda takım varsa normal yarıya böl
        // Tek sayıda takım varsa son takım bye geçer
        val matchCount = totalSongs / 2
        val halfPoint = floor(totalSongs / 2.0).toInt()
        
        for (i in 0 until matchCount) {
            val song1 = songs[i]
            val song2Index = i + halfPoint
            
            // Tek sayıda takım varsa ve son eşleştirmede ikinci takım olmayabilir
            if (song2Index < totalSongs) {
                val song2 = songs[song2Index]
                
                matches.add(
                    Match(
                        listId = song1.listId,
                        rankingMethod = "EMRE",
                        songId1 = song1.id,
                        songId2 = song2.id,
                        winnerId = null,
                        round = 1
                    )
                )
            }
        }
        
        return matches
    }
    
    /**
     * Eşleştirme metodunun açıklamasını döndür
     */
    fun getMethodDescription(method: EmrePairingMethod): String {
        return when (method) {
            EmrePairingMethod.SEQUENTIAL -> "CSV listesi sırasına göre: 1-2, 3-4, 5-6..."
            EmrePairingMethod.RANDOM -> "Gelişigüzel karıştırarak eşleştirme"
            EmrePairingMethod.ALPHABETICAL -> "Alfabetik sıraya göre: A-B, C-D, E-F..."
            EmrePairingMethod.SPLIT_HALF -> "Yarı-yarıya: 1-19, 2-20, 3-21... (CSV sırası)"
        }
    }
    
    /**
     * Eşleştirme metodunun adını döndür
     */
    fun getMethodName(method: EmrePairingMethod): String {
        return when (method) {
            EmrePairingMethod.SEQUENTIAL -> "Sıra Numarasına Göre"
            EmrePairingMethod.RANDOM -> "Gelişigüzel"
            EmrePairingMethod.ALPHABETICAL -> "Alfabetik Sıra"
            EmrePairingMethod.SPLIT_HALF -> "Yarı-Yarıya"
        }
    }
    
    /**
     * Bye geçen takımı belirle (tek sayıda takım varsa)
     */
    fun getByeTeam(songs: List<Song>, method: EmrePairingMethod): Song? {
        if (songs.size % 2 == 0) return null
        
        return when (method) {
            EmrePairingMethod.SEQUENTIAL,
            EmrePairingMethod.ALPHABETICAL,
            EmrePairingMethod.RANDOM -> songs.last() // En son takım bye geçer
            EmrePairingMethod.SPLIT_HALF -> songs.last() // En son takım bye geçer
        }
    }
}