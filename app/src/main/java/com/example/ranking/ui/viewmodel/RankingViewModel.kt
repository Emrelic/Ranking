package com.example.ranking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ranking.data.*
import com.example.ranking.data.RankingDatabase
import com.example.ranking.ranking.RankingEngine
import com.example.ranking.repository.RankingRepository
import com.example.ranking.utils.CsvReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow

class RankingViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = RankingDatabase.getDatabase(application)
    private val repository = RankingRepository(
        songDao = database.songDao(),
        songListDao = database.songListDao(),
        rankingResultDao = database.rankingResultDao(),
        matchDao = database.matchDao(),
        leagueSettingsDao = database.leagueSettingsDao(),
        archiveDao = database.archiveDao(),
        csvReader = CsvReader(),
        swissStateDao = database.swissStateDao(),
        swissMatchStateDao = database.swissMatchStateDao()
    )
    
    private val votingSessionDao = database.votingSessionDao()
    private val votingScoreDao = database.votingScoreDao()
    
    data class RankingUiState(
        val isLoading: Boolean = true,
        val isComplete: Boolean = false,
        val progress: Float = 0f,
        val currentIndex: Int = 0,
        val totalCount: Int = 0,
        val currentSong: Song? = null,
        val currentMatch: Match? = null,
        val song1: Song? = null,
        val song2: Song? = null,
        val completedMatches: Int = 0,
        val totalMatches: Int = 0,
        val currentRound: Int = 1,
        val leagueSettings: LeagueSettings? = null,
        val error: String? = null,
        val currentSession: VotingSession? = null,
        val hasActiveSession: Boolean = false,
        val completedScores: Map<Long, Double> = emptyMap(),
        val allSongs: List<Song> = emptyList()
    )
    
    private val _uiState = MutableStateFlow(RankingUiState())
    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()
    
    private var songs: List<Song> = emptyList()
    private var currentMethod: String = ""
    private var currentListId: Long = 0L
    private var directScores: MutableMap<Long, Double> = mutableMapOf()
    private var currentSongIndex: Int = 0
    private var currentVotingSession: VotingSession? = null
    
    fun initializeRanking(listId: Long, method: String) {
        currentListId = listId
        currentMethod = method
        
        viewModelScope.launch {
            try {
                // Check for existing active session
                val activeSession = votingSessionDao.getActiveSession(listId, method)
                currentVotingSession = activeSession
                
                // Load league settings if applicable
                val settings = if (method == "LEAGUE") {
                    repository.getLeagueSettings(listId, method)
                } else null
                
                repository.getSongsByListId(listId).collect { songList ->
                    songs = songList
                    if (songs.isNotEmpty()) {
                        // Load completed scores if resuming a session
                        val completedScores = if (activeSession != null) {
                            val scores = votingScoreDao.getScoresForSessionSync(activeSession.id)
                            scores.associate { it.songId to it.score }
                        } else {
                            emptyMap()
                        }
                        
                        _uiState.value = _uiState.value.copy(
                            leagueSettings = settings,
                            currentSession = activeSession,
                            hasActiveSession = activeSession != null,
                            completedScores = completedScores,
                            allSongs = songList
                        )
                        
                        if (activeSession != null) {
                            // Resume existing session
                            resumeSession(activeSession)
                        } else {
                            // Start new session
                            when (method) {
                                "DIRECT_SCORING" -> initializeDirectScoring()
                                "LEAGUE" -> initializeLeague()
                                "ELIMINATION" -> initializeElimination()
                                "FULL_ELIMINATION" -> initializeFullElimination()
                                "SWISS" -> initializeSwiss()
                                "EMRE" -> initializeEmre()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Hata: ${e.message}"
                )
            }
        }
    }
    
    private fun initializeDirectScoring() {
        viewModelScope.launch {
            directScores.clear()
            currentSongIndex = 0
            createOrUpdateSession()
            updateDirectScoringUI()
        }
    }
    
    private fun updateDirectScoringUI() {
        if (currentSongIndex >= songs.size) {
            // Complete - calculate results
            completeDirectScoring()
            return
        }
        
        val currentSong = songs[currentSongIndex]
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            currentSong = currentSong,
            currentIndex = currentSongIndex,
            totalCount = songs.size,
            progress = currentSongIndex.toFloat() / songs.size
        )
    }
    
    private fun completeDirectScoring() {
        viewModelScope.launch {
            val results = RankingEngine.createDirectScoringResults(songs, directScores)
            repository.clearRankingResults(currentListId, currentMethod)
            repository.saveRankingResults(results)
            
            // Complete the session
            currentVotingSession?.let { session ->
                val completedSession = session.copy(
                    isCompleted = true,
                    progress = 1f,
                    completedAt = System.currentTimeMillis(),
                    lastModified = System.currentTimeMillis()
                )
                votingSessionDao.updateSession(completedSession)
                currentVotingSession = completedSession
            }
            
            _uiState.value = _uiState.value.copy(
                isComplete = true,
                progress = 1f
            )
        }
    }
    
    private fun initializeLeague() {
        viewModelScope.launch {
            repository.clearMatches(currentListId, currentMethod)
            val settings = _uiState.value.leagueSettings
            val doubleRoundRobin = settings?.doubleRoundRobin ?: false
            val matches = RankingEngine.createLeagueMatches(songs, doubleRoundRobin)
            repository.createMatches(matches)
            loadNextMatch()
        }
    }
    
    private fun initializeSwiss() {
        viewModelScope.launch {
            repository.clearMatches(currentListId, currentMethod)
            
            // Initialize Swiss state for first round
            currentVotingSession?.let { session ->
                val maxRounds = RankingEngine.getSwissRoundCount(songs.size)
                val initialStandings = songs.associate { it.id to 0.0 }
                repository.saveSwissState(
                    sessionId = session.id,
                    currentRound = 1,
                    maxRounds = maxRounds,
                    standings = initialStandings,
                    pairingHistory = emptySet(),
                    roundHistory = emptyList()
                )
            }
            
            val matches = RankingEngine.createSwissMatches(songs, 1, emptyList())
            repository.createMatches(matches)
            loadNextMatch()
        }
    }
    
    private fun initializeEmre() {
        // Reset Emre state variables
        emreFirstConsecutiveWin = false
        emreSecondConsecutiveWin = false
        emreConsecutiveWinCount = 0
        
        viewModelScope.launch {
            repository.clearMatches(currentListId, currentMethod)
            val matches = RankingEngine.createEmreMatches(songs, 1)
            repository.createMatches(matches)
            loadNextMatch()
        }
    }
    
    private fun initializeElimination() {
        viewModelScope.launch {
            repository.clearMatches(currentListId, currentMethod)
            val matches = RankingEngine.createEliminationMatches(songs)
            repository.createMatches(matches)
            loadNextMatch()
        }
    }
    
    private fun initializeFullElimination() {
        viewModelScope.launch {
            repository.clearMatches(currentListId, currentMethod)
            val matches = RankingEngine.createFullEliminationMatches(songs)
            repository.createMatches(matches)
            loadNextMatch()
        }
    }
    
    private suspend fun loadNextMatch() {
        val nextMatch = repository.getNextUncompletedMatch(currentListId, currentMethod)
        val (completed, total) = repository.getMatchProgress(currentListId, currentMethod)
        
        if (nextMatch == null) {
            // Check if we need more rounds (for Swiss, Emre, or Elimination)
            when (currentMethod) {
                "SWISS" -> {
                    val currentRound = getCurrentSwissRound(completed)
                    val maxRounds = RankingEngine.getSwissRoundCount(songs.size)
                    if (currentRound <= maxRounds) {
                        createNextSwissRound(currentRound)
                        return
                    }
                }
                "EMRE" -> {
                    val currentRound = getCurrentEmreRound(completed)
                    // Emre usulünde sabit maksimum tur yok - sadece durma koşuluna bak
                    createNextEmreRound(currentRound)
                    return
                }
                "ELIMINATION" -> {
                    // Check if we need to start knockout rounds after group stage
                    val allMatches = repository.getMatchesByListAndMethodSync(currentListId, currentMethod)
                    createNextEliminationRound(allMatches)
                    return
                }
                "FULL_ELIMINATION" -> {
                    // Check if we need to create final bracket
                    val allMatches = repository.getMatchesByListAndMethodSync(currentListId, currentMethod)
                    val created = createNextFullEliminationRound(allMatches)
                    if (created) {
                        // Yeni maçlar yaratıldı, tekrar kontrol et
                        loadNextMatch()
                    }
                    return
                }
            }
            
            // Complete ranking
            completeRanking()
            return
        }
        
        val song1 = songs.find { it.id == nextMatch.songId1 }
        val song2 = songs.find { it.id == nextMatch.songId2 }
        
        // Save current match state for Swiss system (real-time persistence)
        if (currentMethod == "SWISS") {
            currentVotingSession?.let { session ->
                repository.saveCurrentMatchState(
                    sessionId = session.id,
                    match = nextMatch,
                    song1Name = song1?.name ?: "Unknown",
                    song2Name = song2?.name ?: "Unknown"
                )
                
                // Save complete fixture state
                val allMatches = repository.getMatchesByListAndMethodSync(currentListId, currentMethod)
                val swissStandings = RankingEngine.createSwissStandingsFromMatches(songs, allMatches.filter { it.isCompleted })
                val maxRounds = RankingEngine.getSwissRoundCount(songs.size)
                
                repository.saveCompleteFixture(
                    sessionId = session.id,
                    currentRound = nextMatch.round,
                    totalRounds = maxRounds,
                    allMatches = allMatches,
                    currentStandings = swissStandings.standings
                )
            }
        }
        
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            currentMatch = nextMatch,
            song1 = song1,
            song2 = song2,
            completedMatches = completed,
            totalMatches = total,
            progress = if (total > 0) completed.toFloat() / total else 0f
        )
    }
    
    private suspend fun createNextSwissRound(round: Int) {
        try {
            val allMatches = repository.getMatchesByListAndMethodSync(currentListId, currentMethod)
            val completedMatches = allMatches.filter { it.isCompleted }
            
            // Create Swiss standings from completed matches
            val swissStandings = RankingEngine.createSwissStandingsFromMatches(songs, completedMatches)
            
            // Create new matches using advanced pairing
            val newMatches = RankingEngine.createSwissMatchesWithState(songs, swissStandings)
            
            if (newMatches.isNotEmpty()) {
                repository.createMatches(newMatches)
                
                // Save updated Swiss state
                currentVotingSession?.let { session ->
                    val maxRounds = RankingEngine.getSwissRoundCount(songs.size)
                    repository.saveSwissState(
                        sessionId = session.id,
                        currentRound = round,
                        maxRounds = maxRounds,
                        standings = swissStandings.standings,
                        pairingHistory = swissStandings.pairingHistory,
                        roundHistory = swissStandings.roundHistory
                    )
                }
            }
            
            loadNextMatch()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = "Swiss round oluşturma hatası: ${e.message}"
            )
        }
    }
    
    private var emreFirstConsecutiveWin = false
    private var emreSecondConsecutiveWin = false
    private var emreConsecutiveWinCount = 0
    
    private suspend fun createNextEmreRound(round: Int) {
        try {
            val allMatches = repository.getMatchesByListAndMethodSync(currentListId, currentMethod)
            
            // Check if current round is complete and satisfies stopping condition
            if (RankingEngine.checkEmreCompletion(songs, allMatches, round - 1)) {
                // Bir tur daha tüm birinciler kazandı
                emreConsecutiveWinCount++
                
                if (emreConsecutiveWinCount == 1) {
                    // İlk kez üstüste kazanma - devam et
                    emreFirstConsecutiveWin = true
                    val newMatches = RankingEngine.createEmreMatchesWithOrdering(songs, round, allMatches)
                    if (newMatches.isNotEmpty()) {
                        repository.createMatches(newMatches)
                    }
                    loadNextMatch()
                } else if (emreConsecutiveWinCount >= 2) {
                    // İki kez üstüste kazanma - sıralamayı tamamla
                    emreSecondConsecutiveWin = true
                    completeRanking()
                    return
                }
            } else {
                // Bu turda tüm birinciler kazanmadı - sayacı sıfırla
                emreConsecutiveWinCount = 0
                emreFirstConsecutiveWin = false
                emreSecondConsecutiveWin = false
                
                // Sonraki tur için eşleştirme oluştur
                val newMatches = RankingEngine.createEmreMatchesWithOrdering(songs, round, allMatches)
                if (newMatches.isNotEmpty()) {
                    repository.createMatches(newMatches)
                }
                loadNextMatch()
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = "Emre round oluşturma hatası: ${e.message}"
            )
        }
    }
    
    private suspend fun completeRanking() {
        try {
            val allMatches = repository.getMatchesByListAndMethodSync(currentListId, currentMethod)
            val results = when (currentMethod) {
                "LEAGUE" -> RankingEngine.calculateLeagueResults(songs, allMatches)
                "SWISS" -> RankingEngine.calculateSwissResults(songs, allMatches)
                "EMRE" -> RankingEngine.calculateEmreResults(songs, allMatches)
                "ELIMINATION" -> RankingEngine.calculateEliminationResults(songs, allMatches)
                "FULL_ELIMINATION" -> RankingEngine.calculateFullEliminationResults(songs, allMatches)
                else -> emptyList()
            }
            
            repository.clearRankingResults(currentListId, currentMethod)
            repository.saveRankingResults(results)
            
            _uiState.value = _uiState.value.copy(
                isComplete = true,
                progress = 1f
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = "Sıralama tamamlama hatası: ${e.message}"
            )
        }
    }
    
    fun submitDirectScore(songId: Long, score: Double) {
        viewModelScope.launch {
            directScores[songId] = score
            
            // Save score to session
            currentVotingSession?.let { session ->
                val votingScore = VotingScore(
                    sessionId = session.id,
                    songId = songId,
                    score = score
                )
                votingScoreDao.insertOrUpdateScore(votingScore)
            }
            
            // Update UI state with new completed scores
            val updatedScores = _uiState.value.completedScores.toMutableMap()
            updatedScores[songId] = score
            _uiState.value = _uiState.value.copy(completedScores = updatedScores)
            
            currentSongIndex++
            createOrUpdateSession()
            updateDirectScoringUI()
        }
    }
    
    fun submitMatchResult(@Suppress("UNUSED_PARAMETER") matchId: Long, winnerId: Long?) {
        viewModelScope.launch {
            val currentState = _uiState.value
            currentState.currentMatch?.let { match ->
                val updatedMatch = match.copy(
                    winnerId = winnerId,
                    isCompleted = true
                )
                repository.updateMatch(updatedMatch)
                
                // Update Swiss state if this is a Swiss tournament
                if (currentMethod == "SWISS") {
                    updateSwissStateAfterMatch(updatedMatch)
                }
                
                loadNextMatch()
            }
        }
    }
    
    fun submitMatchResultWithScore(@Suppress("UNUSED_PARAMETER") matchId: Long, winnerId: Long?, score1: Int?, score2: Int?) {
        viewModelScope.launch {
            val currentState = _uiState.value
            currentState.currentMatch?.let { match ->
                val updatedMatch = match.copy(
                    winnerId = winnerId,
                    score1 = score1,
                    score2 = score2,
                    isCompleted = true
                )
                repository.updateMatch(updatedMatch)
                
                // Update Swiss state if this is a Swiss tournament
                if (currentMethod == "SWISS") {
                    updateSwissStateAfterMatch(updatedMatch)
                }
                
                loadNextMatch()
            }
        }
    }
    
    private fun getCurrentSwissRound(completedMatches: Int): Int {
        val matchesPerRound = songs.size / 2
        return (completedMatches / matchesPerRound) + 1
    }
    
    private fun getCurrentEmreRound(completedMatches: Int): Int {
        if (completedMatches == 0) return 1
        
        var currentSongCount = songs.size
        var totalMatchesSoFar = 0
        var round = 1
        
        while (totalMatchesSoFar < completedMatches) {
            val matchesInThisRound = currentSongCount / 2
            totalMatchesSoFar += matchesInThisRound
            if (totalMatchesSoFar <= completedMatches) {
                round++
                currentSongCount = matchesInThisRound * 2 // Only winners go to next round
            }
        }
        
        return round
    }
    
    private suspend fun createNextEliminationRound(allMatches: List<Match>) {
        try {
            val songCount = songs.size
            val targetSize = 2.0.pow(kotlin.math.floor(log2(songCount.toDouble()))).toInt()
            
            // If already a power of 2, all matches are already created in direct elimination
            if (songCount == targetSize) {
                // All matches should be created, just complete ranking
                completeRanking()
                return
            }
            
            // Check if group stage is complete
            val groupMatches = allMatches.filter { it.round == 0 }
            val groupsComplete = groupMatches.all { it.isCompleted }
            
            if (groupsComplete && allMatches.none { it.round > 0 }) {
                // Group stage done, need to create knockout rounds
                val teamsToEliminate = songCount - targetSize
                val groupConfig = RankingEngine.calculateOptimalGroupConfig(songCount, teamsToEliminate)
                val qualifiers = RankingEngine.getGroupQualifiers(songs, groupMatches, groupConfig)
                
                // Create knockout matches
                val knockoutMatches = RankingEngine.createEliminationKnockoutMatches(qualifiers, 1)
                repository.createMatches(knockoutMatches)
                loadNextMatch()
            } else {
                // All rounds complete
                completeRanking()
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = "Eleme turu oluşturma hatası: ${e.message}"
            )
        }
    }
    
    private suspend fun createNextFullEliminationRound(allMatches: List<Match>): Boolean {
        try {
            val songCount = songs.size
            val targetSize = getPreviousPowerOfTwo(songCount) // X'den küçük en büyük 2'nin üssü
            val teamsToEliminate = songCount - targetSize
            
            // İlk kontrol: Direkt eleme başlatılmalı mı?
            if (teamsToEliminate == 0) {
                // Zaten 2'nin kuvveti, tüm maçlar yaratılmış olmalı
                completeRanking()
                return false
            }
            
            // Ön eleme aşamasındayız
            val maxRound = allMatches.maxOfOrNull { it.round } ?: 0
            val currentRoundMatches = allMatches.filter { it.round == maxRound }
            val currentRoundComplete = currentRoundMatches.isNotEmpty() && currentRoundMatches.all { it.isCompleted }
            
            if (!currentRoundComplete) {
                // Mevcut tur henüz tamamlanmamış
                return false
            }
            
            // Bu turdan sonra kalan takımları hesapla
            val remainingTeams = getRemainingTeamsAfterRound(allMatches, maxRound)
            val eliminatedSoFar = songCount - remainingTeams.size
            
            // Debug için log ekle
            _uiState.value = _uiState.value.copy(
                error = "Debug: Tur $maxRound, Kalan: ${remainingTeams.size}, Elenen: $eliminatedSoFar, Hedef Z: $teamsToEliminate"
            )
            
            if (eliminatedSoFar >= teamsToEliminate) {
                // Yeterince takım elendi, final bracket başlat
                if (remainingTeams.size == targetSize) {
                    val finalMatches = RankingEngine.createDirectEliminationMatches(remainingTeams, 101)
                    repository.createMatches(finalMatches)
                    return true
                } else {
                    // Hedef sayıya ulaştık
                    completeRanking()
                    return false
                }
            }
            
            // Bu turda kim kazandı kim kaybetti?
            val (winners, losers) = getWinnersAndLosers(currentRoundMatches)
            val stillNeedToEliminate = teamsToEliminate - eliminatedSoFar
            
            // Güvenlik kontrolü
            if (losers.isEmpty() && winners.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    error = "Hata: Hiç kazanan/kaybeden bulunamadı"
                )
                return false
            }
            
            // Prompt kurallarına göre karar ver
            if (losers.size > stillNeedToEliminate) {
                // Kaybeden sayısı Z'den büyük ise, eleme devam eder
                val nextRoundMatches = createEliminationMatches(losers, maxRound + 1, stillNeedToEliminate)
                repository.createMatches(nextRoundMatches)
                return true
                
            } else if (losers.size == stillNeedToEliminate) {
                // Kaybeden sayısı Z'ye eşit - bu takımlar kesin elenir, final bracket başlar
                val finalMatches = RankingEngine.createDirectEliminationMatches(remainingTeams, 101)
                repository.createMatches(finalMatches)
                return true
                
            } else {
                // Kaybeden sayısı Z'den küçük - eksik kalan takımlar kazananlardan belirlenir
                val need = stillNeedToEliminate - losers.size
                val take = need * 2 // Python kodundaki mantık: 2 katı aday çek
                
                // Son tur kazananlarından 2*need kişi al
                val candidates = if (winners.size >= take) {
                    winners.take(take)
                } else {
                    // Yetmezse tüm kazananları al
                    winners
                }
                
                if (candidates.size >= 2) {
                    // Bu adayları eşleştir ve need kadar kaybeden üret
                    val nextRoundMatches = createEliminationMatches(candidates, maxRound + 1, need)
                    repository.createMatches(nextRoundMatches)
                    return true
                } else {
                    // Yeterli aday yoksa direkt finale geç
                    val finalMatches = RankingEngine.createDirectEliminationMatches(remainingTeams, 101)
                    repository.createMatches(finalMatches)
                    return true
                }
            }
            
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = "Tam eleme turu oluşturma hatası: ${e.message}"
            )
            return false
        }
    }
    
    private fun getFullEliminationQualifiers(allMatches: List<Match>): List<Song> {
        // Bu fonksiyon ön elemeden kalan takımları bulur
        // Gerçek implementasyonda maç sonuçlarına göre kazananlar belirlenir
        val eliminatedSongs = mutableSetOf<Long>()
        
        // Ön eleme maçlarında kaybedenleri bul
        allMatches.filter { it.round <= 100 && it.isCompleted }.forEach { match ->
            when (match.winnerId) {
                match.songId1 -> eliminatedSongs.add(match.songId2)
                match.songId2 -> eliminatedSongs.add(match.songId1)
            }
        }
        
        // Kalan takımları döndür
        return songs.filter { it.id !in eliminatedSongs }
    }
    
    private fun getRemainingTeamsAfterRound(allMatches: List<Match>, round: Int): List<Song> {
        // Bu turda ve önceki turlarda elenen takımları bul
        val eliminatedSongIds = mutableSetOf<Long>()
        
        allMatches.filter { it.round <= round && it.isCompleted }.forEach { match ->
            when (match.winnerId) {
                match.songId1 -> eliminatedSongIds.add(match.songId2)
                match.songId2 -> eliminatedSongIds.add(match.songId1)
                // null (berabere) durumunda kimse elenmiyor
            }
        }
        
        // Kalan takımları döndür
        return songs.filter { it.id !in eliminatedSongIds }
    }
    
    private fun createNextEliminationRoundMatches(teams: List<Song>, round: Int): List<Match> {
        val matches = mutableListOf<Match>()
        val teamList = teams.toMutableList()
        
        while (teamList.size >= 2) {
            if (teamList.size == 3) {
                // Son üç takım - üçlü grup maçı
                val team1 = teamList[0]
                val team2 = teamList[1] 
                val team3 = teamList[2]
                
                matches.add(Match(
                    listId = songs[0].listId,
                    rankingMethod = "FULL_ELIMINATION",
                    songId1 = team1.id,
                    songId2 = team2.id,
                    winnerId = null,
                    round = round
                ))
                matches.add(Match(
                    listId = songs[0].listId,
                    rankingMethod = "FULL_ELIMINATION", 
                    songId1 = team1.id,
                    songId2 = team3.id,
                    winnerId = null,
                    round = round
                ))
                matches.add(Match(
                    listId = songs[0].listId,
                    rankingMethod = "FULL_ELIMINATION",
                    songId1 = team2.id,
                    songId2 = team3.id,
                    winnerId = null,
                    round = round
                ))
                
                teamList.clear()
            } else {
                val team1 = teamList.removeAt(0)
                val team2 = teamList.removeAt(0)
                matches.add(Match(
                    listId = songs[0].listId,
                    rankingMethod = "FULL_ELIMINATION",
                    songId1 = team1.id,
                    songId2 = team2.id,
                    winnerId = null,
                    round = round
                ))
            }
        }
        
        return matches
    }
    
    private fun getWinnersAndLosers(matches: List<Match>): Pair<List<Song>, List<Song>> {
        val winners = mutableListOf<Song>()
        val losers = mutableListOf<Song>()
        
        // Üçlü grup tespiti: Aynı 3 takım birbiriyle 3 maç yapıyorsa
        val allSongIds = mutableSetOf<Long>()
        matches.forEach { match ->
            allSongIds.add(match.songId1)
            allSongIds.add(match.songId2)
        }
        
        if (allSongIds.size == 3 && matches.size == 3) {
            // Üçlü grup - lig usulü puan hesapla
            val points = mutableMapOf<Long, Int>()
            allSongIds.forEach { points[it] = 0 }
            
            matches.forEach { match ->
                when (match.winnerId) {
                    match.songId1 -> points[match.songId1] = points[match.songId1]!! + 3
                    match.songId2 -> points[match.songId2] = points[match.songId2]!! + 3
                    null -> {
                        points[match.songId1] = points[match.songId1]!! + 1
                        points[match.songId2] = points[match.songId2]!! + 1
                    }
                }
            }
            
            val sortedByPoints = allSongIds.sortedByDescending { points[it] ?: 0 }
            val winner = songs.find { it.id == sortedByPoints[0] }
            val groupLosers = sortedByPoints.drop(1).mapNotNull { id -> songs.find { it.id == id } }
            
            winner?.let { winners.add(it) }
            losers.addAll(groupLosers)
            
        } else {
            // Normal ikili eşleşmeler veya karışık durumlar
            // Her ikili eşleşmeyi ayrı ayrı işle
            matches.forEach { match ->
                val song1 = songs.find { it.id == match.songId1 }
                val song2 = songs.find { it.id == match.songId2 }
                
                when (match.winnerId) {
                    match.songId1 -> {
                        song1?.let { winners.add(it) }
                        song2?.let { losers.add(it) }
                    }
                    match.songId2 -> {
                        song2?.let { winners.add(it) }
                        song1?.let { losers.add(it) }
                    }
                    // null (berabere) durumunda kimse kazanmıyor/kaybetmiyor - ancak tam elemede berabere olmaz
                }
            }
        }
        
        return Pair(winners.distinct(), losers.distinct())
    }
    
    private fun createEliminationMatches(teams: List<Song>, round: Int, targetEliminations: Int): List<Match> {
        val matches = mutableListOf<Match>()
        val teamList = teams.toMutableList()
        
        // Doğru eşleştirme mantığı: Çift sayıda ise hepsi ikili, tek sayıda ise son 3 üçlü
        if (teamList.size % 2 == 0) {
            // Çift sayı - hepsi ikili eşleşme
            while (teamList.size >= 2) {
                val team1 = teamList.removeAt(0)
                val team2 = teamList.removeAt(0)
                matches.add(Match(
                    listId = songs[0].listId,
                    rankingMethod = "FULL_ELIMINATION",
                    songId1 = team1.id,
                    songId2 = team2.id,
                    winnerId = null,
                    round = round
                ))
            }
        } else {
            // Tek sayı - son 3 üçlü grup
            while (teamList.size > 3) {
                val team1 = teamList.removeAt(0)
                val team2 = teamList.removeAt(0)
                matches.add(Match(
                    listId = songs[0].listId,
                    rankingMethod = "FULL_ELIMINATION",
                    songId1 = team1.id,
                    songId2 = team2.id,
                    winnerId = null,
                    round = round
                ))
            }
            
            // Son 3 takım üçlü grup
            if (teamList.size == 3) {
                val team1 = teamList[0]
                val team2 = teamList[1] 
                val team3 = teamList[2]
                
                matches.add(Match(
                    listId = songs[0].listId,
                    rankingMethod = "FULL_ELIMINATION",
                    songId1 = team1.id,
                    songId2 = team2.id,
                    winnerId = null,
                    round = round
                ))
                matches.add(Match(
                    listId = songs[0].listId,
                    rankingMethod = "FULL_ELIMINATION", 
                    songId1 = team1.id,
                    songId2 = team3.id,
                    winnerId = null,
                    round = round
                ))
                matches.add(Match(
                    listId = songs[0].listId,
                    rankingMethod = "FULL_ELIMINATION",
                    songId1 = team2.id,
                    songId2 = team3.id,
                    winnerId = null,
                    round = round
                ))
            }
        }
        
        return matches
    }
    
    private fun getNextPowerOfTwo(n: Int): Int {
        // X'den küçük veya eşit en büyük 2'nin üssü
        return when {
            n <= 1 -> 1
            n <= 2 -> 2
            n <= 4 -> 4  
            n <= 8 -> 8
            n <= 16 -> 16
            n <= 32 -> 32
            n <= 64 -> 64
            n <= 128 -> 128
            n <= 256 -> 256
            n <= 512 -> 512
            n <= 1024 -> 1024
            else -> 1024
        }
    }
    
    private fun getPreviousPowerOfTwo(n: Int): Int {
        if (n <= 1) return 1
        var result = 1
        while (result * 2 <= n) {
            result *= 2
        }
        return result
    }
    
    private suspend fun resumeSession(session: VotingSession) {
        when (session.rankingMethod) {
            "DIRECT_SCORING" -> {
                // Load existing scores
                val existingScores = votingScoreDao.getScoresForSessionSync(session.id)
                directScores.clear()
                existingScores.forEach { score ->
                    directScores[score.songId] = score.score
                }
                currentSongIndex = session.currentIndex
                updateDirectScoringUI()
            }
            "SWISS" -> {
                // Load comprehensive Swiss state and resume from exact position
                val savedMatchState = repository.getCurrentMatchState(session.id)
                val savedFixture = repository.loadCompleteFixture(session.id)
                
                if (savedMatchState != null && savedMatchState.isMatchInProgress) {
                    // Resume from middle of a match
                    val match = repository.getMatchesByListAndMethodSync(currentListId, currentMethod)
                        .find { it.id == savedMatchState.matchId }
                    
                    if (match != null) {
                        val song1 = songs.find { it.id == match.songId1 }
                        val song2 = songs.find { it.id == match.songId2 }
                        
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            currentMatch = match,
                            song1 = song1,
                            song2 = song2,
                            progress = savedFixture?.let { fixture ->
                                val completed = repository.getMatchProgress(currentListId, currentMethod).first
                                val total = repository.getMatchProgress(currentListId, currentMethod).second
                                if (total > 0) completed.toFloat() / total else 0f
                            } ?: 0f
                        )
                        
                        // Restore preliminary selections if any
                        savedMatchState.preliminaryWinnerId?.let { winnerId ->
                            // UI should show the preliminary selection
                            // This can be handled by the UI layer observing the match state
                        }
                        return
                    }
                }
                
                if (savedFixture != null) {
                    // Resume from saved fixture state
                    loadNextMatch()
                } else {
                    // Fallback: recreate from matches
                    val allMatches = repository.getMatchesByListAndMethodSync(currentListId, currentMethod)
                    if (allMatches.isNotEmpty()) {
                        val completedMatches = allMatches.filter { it.isCompleted }
                        val swissStandings = RankingEngine.createSwissStandingsFromMatches(songs, completedMatches)
                        val maxRounds = RankingEngine.getSwissRoundCount(songs.size)
                        
                        // Save recreated state
                        repository.saveSwissState(
                            sessionId = session.id,
                            currentRound = (completedMatches.maxOfOrNull { it.round } ?: 0) + 1,
                            maxRounds = maxRounds,
                            standings = swissStandings.standings,
                            pairingHistory = swissStandings.pairingHistory,
                            roundHistory = swissStandings.roundHistory
                        )
                        
                        // Save complete fixture for future resumes
                        repository.saveCompleteFixture(
                            sessionId = session.id,
                            currentRound = (completedMatches.maxOfOrNull { it.round } ?: 0) + 1,
                            totalRounds = maxRounds,
                            allMatches = allMatches,
                            currentStandings = swissStandings.standings
                        )
                    }
                    loadNextMatch()
                }
            }
            else -> {
                // For other match-based methods, resume from current match
                loadNextMatch()
            }
        }
    }
    
    private suspend fun createOrUpdateSession() {
        val session = currentVotingSession
        if (session == null) {
            // Create new session
            val newSession = VotingSession(
                listId = currentListId,
                rankingMethod = currentMethod,
                sessionName = "Oturum ${System.currentTimeMillis()}",
                currentIndex = currentSongIndex,
                totalItems = songs.size,
                progress = if (songs.isNotEmpty()) currentSongIndex.toFloat() / songs.size else 0f,
                currentSongId = songs.getOrNull(currentSongIndex)?.id,
                currentRound = 1,
                completedMatches = 0,
                totalMatches = 0
            )
            val sessionId = votingSessionDao.createSession(newSession)
            currentVotingSession = newSession.copy(id = sessionId)
        } else {
            // Update existing session
            val updatedSession = session.copy(
                currentIndex = currentSongIndex,
                progress = if (songs.isNotEmpty()) currentSongIndex.toFloat() / songs.size else 0f,
                currentSongId = songs.getOrNull(currentSongIndex)?.id,
                lastModified = System.currentTimeMillis()
            )
            votingSessionDao.updateSession(updatedSession)
            currentVotingSession = updatedSession
        }
    }
    
    fun pauseSession() {
        viewModelScope.launch {
            currentVotingSession?.let { session ->
                val pausedSession = session.copy(
                    isPaused = true,
                    lastModified = System.currentTimeMillis()
                )
                votingSessionDao.updateSession(pausedSession)
                currentVotingSession = pausedSession
            }
        }
    }
    
    fun resumeSession() {
        viewModelScope.launch {
            currentVotingSession?.let { session ->
                val resumedSession = session.copy(
                    isPaused = false,
                    lastModified = System.currentTimeMillis()
                )
                votingSessionDao.updateSession(resumedSession)
                currentVotingSession = resumedSession
            }
        }
    }
    
    fun deleteCurrentSession() {
        viewModelScope.launch {
            currentVotingSession?.let { session ->
                // Delete all Swiss-related state if exists
                if (currentMethod == "SWISS") {
                    repository.deleteSwissState(session.id)
                    repository.deleteAllSwissMatchStates(session.id)
                }
                
                votingSessionDao.deleteSession(session)
                currentVotingSession = null
                _uiState.value = _uiState.value.copy(
                    currentSession = null,
                    hasActiveSession = false
                )
            }
        }
    }
    
    fun updateScoreInSession(songId: Long, newScore: Double) {
        viewModelScope.launch {
            currentVotingSession?.let { session ->
                val votingScore = VotingScore(
                    sessionId = session.id,
                    songId = songId,
                    score = newScore
                )
                votingScoreDao.insertOrUpdateScore(votingScore)
                
                // Update local scores map
                directScores[songId] = newScore
                
                // Update UI state with new completed scores
                val updatedScores = _uiState.value.completedScores.toMutableMap()
                updatedScores[songId] = newScore
                _uiState.value = _uiState.value.copy(completedScores = updatedScores)
                
                // Recalculate results if needed
                if (currentMethod == "DIRECT_SCORING") {
                    val results = RankingEngine.createDirectScoringResults(songs, directScores)
                    repository.clearRankingResults(currentListId, currentMethod)
                    repository.saveRankingResults(results)
                }
            }
        }
    }
    
    // Real-time match state updates (called while user is selecting winner/scores)
    fun updateMatchSelection(songId: Long) {
        if (currentMethod == "SWISS") {
            viewModelScope.launch {
                currentVotingSession?.let { session ->
                    repository.updateMatchProgress(
                        sessionId = session.id,
                        preliminaryWinnerId = songId
                    )
                }
            }
        }
    }
    
    fun updateMatchScores(score1: Int?, score2: Int?) {
        if (currentMethod == "SWISS") {
            viewModelScope.launch {
                currentVotingSession?.let { session ->
                    val currentState = repository.getCurrentMatchState(session.id)
                    repository.updateMatchProgress(
                        sessionId = session.id,
                        preliminaryWinnerId = currentState?.preliminaryWinnerId,
                        preliminaryScore1 = score1,
                        preliminaryScore2 = score2
                    )
                }
            }
        }
    }
    
    private suspend fun updateSwissStateAfterMatch(completedMatch: Match) {
        currentVotingSession?.let { session ->
            try {
                val allMatches = repository.getMatchesByListAndMethodSync(currentListId, currentMethod)
                val completedMatches = allMatches.filter { it.isCompleted }
                
                // Recreate Swiss standings with the new completed match
                val swissStandings = RankingEngine.createSwissStandingsFromMatches(songs, completedMatches)
                val maxRounds = RankingEngine.getSwissRoundCount(songs.size)
                
                // Update Swiss state in database
                repository.saveSwissState(
                    sessionId = session.id,
                    currentRound = completedMatch.round,
                    maxRounds = maxRounds,
                    standings = swissStandings.standings,
                    pairingHistory = swissStandings.pairingHistory,
                    roundHistory = swissStandings.roundHistory
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Swiss durumu güncelleme hatası: ${e.message}"
                )
            }
        }
    }
}