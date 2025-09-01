package com.example.ranking.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song

/**
 * Geliştirilmiş İsviçre Sistemi Test Protokolü ve Raporlama
 * 
 * Metinde belirtilen test kriterlerine göre:
 * a) Mükerrer eşleşme raporu (Team1: 2,3,5,9,11,13...)
 * b) Son aday eşleştirmeler asimetrik analizi
 * c) Her turda kaç eşleştirme yapıldığı (1.tur 18 maç...)
 */
object TournamentTestProtocol {
    
    /**
     * A) MÜKERRER EŞLEŞTİRME RAPORU
     * Her takımın hangi takımlarla eşleştiğini listeler
     * Format: Team1: 2,3,5,9,11,13,15,21,27,32,34
     */
    fun generateMatchHistoryReport(
        emreState: EmreSystemCorrect.EmreState,
        completedMatches: List<Match>
    ): String {
        val report = StringBuilder()
        report.appendLine("═══════════════════════════════════════════════════════════")
        report.appendLine("📊 MÜKERRER EŞLEŞTİRME TEST RAPORU")
        report.appendLine("═══════════════════════════════════════════════════════════")
        report.appendLine()
        
        // Her takım için eşleştirme geçmişi
        emreState.teams.sortedBy { it.currentPosition }.forEach { team ->
            val opponentIds = mutableSetOf<Long>()
            
            // Bu takımın oynadığı tüm maçları bul
            completedMatches.forEach { match ->
                when (team.song.id) {
                    match.songId1 -> {
                        // Song ID'den TeamID'ye çevir
                        val opponentTeam = emreState.teams.find { it.song.id == match.songId2 }
                        opponentTeam?.let { opponentIds.add(it.teamId) }
                    }
                    match.songId2 -> {
                        // Song ID'den TeamID'ye çevir  
                        val opponentTeam = emreState.teams.find { it.song.id == match.songId1 }
                        opponentTeam?.let { opponentIds.add(it.teamId) }
                    }
                }
            }
            
            val opponentsList = opponentIds.sorted().joinToString(",")
            report.appendLine("Team${team.teamId}: $opponentsList")
        }
        
        report.appendLine()
        
        // Duplicate analizi
        val duplicateCount = findDuplicateMatches(completedMatches)
        if (duplicateCount > 0) {
            report.appendLine("❌ DUPLICATE HATASI: $duplicateCount mükerrer eşleştirme bulundu!")
        } else {
            report.appendLine("✅ DUPLICATE TEST: Hiç mükerrer eşleştirme yok")
        }
        
        return report.toString()
    }
    
    /**
     * B) SON ADAY EŞLEŞTİRMELER ASİMETRİK ANALİZİ
     * Turnuva bitiş kriterine göre eşleştirmelerin asimetrik durumu
     */
    fun generateAsymmetricPointsReport(candidateMatches: List<EmreSystemCorrect.CandidateMatch>): String {
        val report = StringBuilder()
        report.appendLine("═══════════════════════════════════════════════════════════")
        report.appendLine("⚖️ ASİMETRİK PUAN ANALİZ RAPORU")
        report.appendLine("═══════════════════════════════════════════════════════════")
        report.appendLine()
        
        val samePointMatches = candidateMatches.filter { !it.isAsymmetricPoints }
        val asymmetricMatches = candidateMatches.filter { it.isAsymmetricPoints }
        
        report.appendLine("📋 TOPLAM EŞLEŞTİRME: ${candidateMatches.size}")
        report.appendLine("⚖️ AYNI PUANLI: ${samePointMatches.size}")
        report.appendLine("📈 ASİMETRİK PUANLI: ${asymmetricMatches.size}")
        report.appendLine()
        
        report.appendLine("🟰 AYNI PUANLI EŞLEŞTİRMELER:")
        samePointMatches.forEachIndexed { index, match ->
            report.appendLine("${index + 1}. Team ${match.team1.currentPosition}(${match.team1.points}p) vs Team ${match.team2.currentPosition}(${match.team2.points}p)")
        }
        report.appendLine()
        
        report.appendLine("📊 ASİMETRİK PUANLI EŞLEŞTİRMELER:")
        asymmetricMatches.forEachIndexed { index, match ->
            val pointDiff = kotlin.math.abs(match.team1.points - match.team2.points)
            report.appendLine("${index + 1}. Team ${match.team1.currentPosition}(${match.team1.points}p) vs Team ${match.team2.currentPosition}(${match.team2.points}p) → Fark: ${pointDiff}p")
        }
        report.appendLine()
        
        // Turnuva bitiş kararı
        val shouldContinue = samePointMatches.isNotEmpty()
        if (shouldContinue) {
            report.appendLine("✅ TURNUVA DEVAMI: ${samePointMatches.size} aynı puanlı eşleştirme mevcut → Tur oynanacak")
        } else {
            report.appendLine("🏁 TURNUVA BİTİRİLMELİ: Hiç aynı puanlı eşleştirme yok → Turnuva sonlandırılacak")
        }
        
        return report.toString()
    }
    
    /**
     * C) TUR BAZINDA MAÇ SAYISI RAPORU  
     * Her turda kaç eşleştirme yapıldığını gösterir
     * Format: 1.tur 18 maç, 2.tur 18 maç...
     */
    fun generateRoundByRoundReport(completedMatches: List<Match>, expectedMatchesPerRound: Int): String {
        val report = StringBuilder()
        report.appendLine("═══════════════════════════════════════════════════════════")
        report.appendLine("🔢 TUR BAZINDA MAÇ SAYISI RAPORU")
        report.appendLine("═══════════════════════════════════════════════════════════")
        report.appendLine()
        
        val matchesByRound = completedMatches.groupBy { it.round }
        val maxRound = matchesByRound.keys.maxOrNull() ?: 0
        
        report.appendLine("📊 BEKLENEN MAÇ/TUR: $expectedMatchesPerRound")
        report.appendLine("📊 TOPLAM TUR SAYISI: $maxRound")
        report.appendLine()
        
        var allRoundsCorrect = true
        
        for (round in 1..maxRound) {
            val roundMatches = matchesByRound[round] ?: emptyList()
            val matchCount = roundMatches.size
            val status = if (matchCount == expectedMatchesPerRound) "✅" else "❌"
            
            if (matchCount != expectedMatchesPerRound) {
                allRoundsCorrect = false
            }
            
            report.appendLine("${round}.tur $matchCount maç $status")
        }
        
        report.appendLine()
        
        if (allRoundsCorrect) {
            report.appendLine("✅ TUR KONTROLÜ: Tüm turlar beklenen maç sayısına sahip")
        } else {
            report.appendLine("❌ TUR HATASI: Bazı turlarda maç sayısı yanlış!")
        }
        
        // Toplam maç istatistikleri
        val totalMatches = completedMatches.size
        val expectedTotal = maxRound * expectedMatchesPerRound
        
        report.appendLine()
        report.appendLine("📈 TOPLAM İSTATİSTİK:")
        report.appendLine("Oynanan: $totalMatches maç")
        report.appendLine("Beklenen: $expectedTotal maç") 
        report.appendLine("Durum: ${if (totalMatches == expectedTotal) "✅ DOĞRU" else "❌ HATA"}")
        
        return report.toString()
    }
    
    /**
     * KAPSAMLI TURNUVA RAPORU
     * Tüm test kriterlerini tek raporda birleştirir
     */
    fun generateComprehensiveReport(
        emreState: EmreSystemCorrect.EmreState,
        completedMatches: List<Match>,
        finalCandidateMatches: List<EmreSystemCorrect.CandidateMatch>
    ): String {
        val report = StringBuilder()
        
        report.appendLine("╔═══════════════════════════════════════════════════════════╗")
        report.appendLine("║         GELİŞTİRİLMİŞ İSVİÇRE SİSTEMİ TEST RAPORU        ║")
        report.appendLine("╚═══════════════════════════════════════════════════════════╝")
        report.appendLine()
        
        // Genel bilgiler
        report.appendLine("🏆 TURNUVA BİLGİLERİ:")
        report.appendLine("Takım Sayısı: ${emreState.teams.size}")
        report.appendLine("Oynanan Tur: ${completedMatches.maxOfOrNull { it.round } ?: 0}")
        report.appendLine("Toplam Maç: ${completedMatches.size}")
        report.appendLine()
        
        // Her tur için maç sayısı
        val expectedMatches = if (emreState.teams.size % 2 == 0) {
            emreState.teams.size / 2
        } else {
            (emreState.teams.size - 1) / 2
        }
        
        report.appendLine(generateRoundByRoundReport(completedMatches, expectedMatches))
        report.appendLine()
        report.appendLine(generateMatchHistoryReport(emreState, completedMatches))
        report.appendLine()
        
        if (finalCandidateMatches.isNotEmpty()) {
            report.appendLine(generateAsymmetricPointsReport(finalCandidateMatches))
        }
        
        return report.toString()
    }
    
    // Yardımcı fonksiyonlar
    private fun findDuplicateMatches(matches: List<Match>): Int {
        val pairSet = mutableSetOf<Pair<Long, Long>>()
        var duplicateCount = 0
        
        matches.forEach { match ->
            val pair1 = Pair(match.songId1, match.songId2)
            val pair2 = Pair(match.songId2, match.songId1)
            
            if (pairSet.contains(pair1) || pairSet.contains(pair2)) {
                duplicateCount++
            } else {
                pairSet.add(pair1)
                pairSet.add(pair2)
            }
        }
        
        return duplicateCount
    }
}