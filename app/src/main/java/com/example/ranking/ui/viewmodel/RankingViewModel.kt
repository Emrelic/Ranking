package com.example.ranking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.example.ranking.data.*
import com.example.ranking.data.RankingDatabase
import com.example.ranking.ranking.RankingEngine
import com.example.ranking.ranking.EmreSystemCorrect
import com.example.ranking.ranking.PairwiseComparisonSort
import com.example.ranking.ranking.TournamentTestProtocol
import com.example.ranking.repository.RankingRepository
import com.example.ranking.utils.CsvReader
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow

class RankingViewModel(application: Application) : AndroidViewModel(application) {
    
    data class StandingEntry(
        val position: Int,
        val song: Song,
        val points: Double,
        val played: Int,
        val won: Int,
        val drawn: Int,
        val lost: Int
    )
    
    private val database = RankingDatabase.getDatabase(application)
    private val repository = RankingRepository(database)
    
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
        val allSongs: List<Song> = emptyList(),
        val currentStandings: List<StandingEntry> = emptyList(),
        val emreState: EmreSystemCorrect.EmreState? = null,
        val showInitialRanking: Boolean = false, // İlk sıralama tablosunu göster
        val showMatchingsList: Boolean = false, // Eşleştirmeler listesini göster
        val matchingsList: List<Match> = emptyList(), // Oluşturulan eşleştirmeler
        val method: String = "", // Ranking metodu
        val showTestReport: Boolean = false, // Test raporu dialog'unu göster
        val testReportData: String = "", // Test raporu içeriği
        val testReportTitle: String = "", // Test raporu başlığı
        val canUndo: Boolean = false // Son maç sonucu geri alınabilir mi
    )
    
    private val _uiState = MutableStateFlow(RankingUiState())
    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()
    
    @Volatile
    private var songs: List<Song> = emptyList()
    private var currentMethod: String = ""
    private var currentListId: Long = 0L
    
    // Thread-safe song access
    private fun getSafeSongs(): List<Song> {
        return try {
            songs.takeIf { it.isNotEmpty() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    private var directScores: MutableMap<Long, Double> = mutableMapOf()
    private var currentSongIndex: Int = 0
    private var currentVotingSession: VotingSession? = null

    // Son tamamlanan maç - tur kapanana kadar geri alınabilir.
    // Tur kapanınca (Emre'de sonuçlar işlenip yeni tur üretilince) geri alma güvenli
    // olmaktan çıkar; bu yüzden tur kapanışında null'lanır.
    private var lastCompletedMatchId: Long? = null

    private fun undoSupported(): Boolean = currentMethod in setOf("LEAGUE", "EMRE_CORRECT", "MERGE_SORT")
    private var currentPairingMethod: com.example.ranking.data.EmrePairingMethod = com.example.ranking.data.EmrePairingMethod.SEQUENTIAL
    
    private fun resetState() {
        // Tüm state'i sıfırla
        directScores.clear()
        currentSongIndex = 0
        currentVotingSession = null
        emreState = null
        lastCompletedMatchId = null
        
        _uiState.value = RankingUiState(
            isLoading = false,
            method = currentMethod
        )
    }
    
    fun initializeRanking(listId: Long, method: String, pairingMethodName: String = "SEQUENTIAL", forceNew: Boolean = true) {
        currentListId = listId
        currentMethod = method
        currentPairingMethod = try {
            com.example.ranking.data.EmrePairingMethod.valueOf(pairingMethodName)
        } catch (e: Exception) {
            com.example.ranking.data.EmrePairingMethod.SEQUENTIAL
        }
        
        viewModelScope.launch {
            try {
                // YENİ TURNUVA: Eski session'ları temizle
                if (forceNew) {
                    // Eski aktif session'ları deaktive et
                    val existingSession = votingSessionDao.getActiveSession(listId, method)
                    if (existingSession != null) {
                        votingSessionDao.deactivateSession(existingSession.id)
                    }
                    
                    // Eski maçları temizle
                    repository.clearMatches(listId, method)
                    
                    // State'i sıfırla
                    resetState()
                }
                
                // Check for existing active session (sadece devam modunda)
                val activeSession = if (forceNew) null else votingSessionDao.getActiveSession(listId, method)
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
                            allSongs = songList,
                            method = method
                        )
                        
                        if (activeSession != null) {
                            // Resume existing session
                            resumeSession(activeSession)
                        } else {
                            // Start new session
                            when (method) {
                                "DIRECT_SCORING" -> {
                                    initializeDirectScoring()
                                }
                                "LEAGUE" -> {
                                    initializeLeague()
                                }
                                "ELIMINATION" -> {
                                    initializeElimination()
                                }
                                "FULL_ELIMINATION" -> {
                                    initializeFullElimination()
                                }
                                "SWISS" -> {
                                    initializeSwiss()
                                }
                                "EMRE_CORRECT" -> {
                                    initializeEmre()
                                }
                                "MERGE_SORT" -> {
                                    initializePairwiseSort()
                                }
                                "SINGLE_ELIMINATION" -> {
                                    initializeSingleElimination()
                                }
                                "DOUBLE_ELIMINATION" -> {
                                    initializeDoubleElimination()
                                }
                                else -> {
                                }
                            }
                        }
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Şarkı listesi boş!"
                        )
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
        
        val safeSongs = getSafeSongs()
        if (safeSongs.isEmpty() || currentSongIndex >= safeSongs.size) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Takım listesi erişilemiyor"
            )
            return
        }
        
        val currentSong = safeSongs[currentSongIndex]
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            currentSong = currentSong,
            currentIndex = currentSongIndex,
            totalCount = safeSongs.size,
            progress = currentSongIndex.toFloat() / safeSongs.size
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

    private fun initializePairwiseSort() {
        viewModelScope.launch {
            repository.clearMatches(currentListId, currentMethod)
            advancePairwiseSort()
        }
    }

    /**
     * İkili karşılaştırmalı sıralamada sıradaki soruyu üretir.
     * Karşılaştırmalar teker teker oluşturulur; sorulacak soru kalmadıysa sıralama biter.
     */
    private suspend fun advancePairwiseSort() {
        val allMatches = repository.getMatchesByListAndMethodSync(currentListId, currentMethod)
        val next = PairwiseComparisonSort.createNextComparisonMatch(songs, allMatches.filter { it.isCompleted })
        if (next != null) {
            repository.createMatches(listOf(next))
            loadNextMatch()
        } else {
            completeRanking()
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
        viewModelScope.launch {
            try {
                // Critical safety check first
                val safeSongs = getSafeSongs()
                if (safeSongs.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Takım listesi henüz yüklenmedi. Lütfen bekleyin."
                    )
                    return@launch
                }
                
                repository.clearMatches(currentListId, currentMethod)
                
                // Session oluştur
                createOrUpdateSession()
                
                // Doğru Emre usulü sistem başlatma
                emreState = EmreSystemCorrect.initializeEmreTournament(safeSongs)

                // Doğrudan turnuvayı başlat - ara ekran gösterme
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    showInitialRanking = false,
                    emreState = emreState,
                    allSongs = safeSongs,
                    currentMatch = null
                )

                // İlk turu otomatik oluştur
                createNextEmreRound(1)
                
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Başlatma hatası: ${e.message}"
                )
            }
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
    
    private fun initializeSingleElimination() {
        viewModelScope.launch {
            repository.clearMatches(currentListId, currentMethod)
            val matches = RankingEngine.createDirectEliminationMatches(songs, 1)
            repository.createMatches(matches)
            loadNextMatch()
        }
    }
    
    private fun initializeDoubleElimination() {
        viewModelScope.launch {
            repository.clearMatches(currentListId, currentMethod)
            val matches = RankingEngine.createDoubleEliminationMatches(songs)
            repository.createMatches(matches)
            loadNextMatch()
        }
    }
    
    fun startScoring() {
        viewModelScope.launch {
            // EMRE_CORRECT sistemi için logic

            if (currentMethod == "EMRE_CORRECT") {

                // EMRE_CORRECT için önce eşleştirmeler listesini oluştur ve göster
                createNextEmreRound(1)
            } else {
                // Diğer sistemler için eşleştirmeler listesini gizle
                _uiState.value = _uiState.value.copy(showMatchingsList = false)
                loadNextMatch()
            }

        }
    }
    
    fun selectMatch(match: Match) {
        android.util.Log.d("RANKING_DEBUG", "selectMatch() çağırıldı - Match ID: ${match.id}")
        
        viewModelScope.launch {
            try {
                // Takım bilgilerini yükle
                val song1 = songs.find { it.id == match.songId1 }
                val song2 = songs.find { it.id == match.songId2 }
                
                // Seçilen maçı current match olarak ayarla - showMatchingsList'i FALSE YAPMA!
                _uiState.value = _uiState.value.copy(
                    currentMatch = match,
                    song1 = song1,
                    song2 = song2,
                    showInitialRanking = false
                    // showMatchingsList = false, // BUNU KALDIRDIM! 
                )
                
                android.util.Log.d("RANKING_DEBUG", "selectMatch() tamamlandı - Takım 1: ${song1?.name}, Takım 2: ${song2?.name}")
                
            } catch (e: Exception) {
                android.util.Log.e("RANKING_DEBUG", "selectMatch() hatası: ${e.message}", e)
            }
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
                "EMRE_CORRECT" -> {
                    val currentRound = getCurrentEmreRound(completed)
                    // Emre usulünde sabit maksimum tur yok - sadece durma koşuluna bak
                    createNextEmreRound(currentRound)
                    return
                }
                "MERGE_SORT" -> {
                    // Sıradaki karşılaştırmayı üret (kalmadıysa advance içinde tamamlanır)
                    advancePairwiseSort()
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
        
        val safeSongs = getSafeSongs()
        val song1 = safeSongs.find { it.id == nextMatch.songId1 }
        val song2 = safeSongs.find { it.id == nextMatch.songId2 }
        
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
                val swissStandings = RankingEngine.createSwissStandingsFromMatches(safeSongs, allMatches.filter { it.isCompleted })
                val maxRounds = RankingEngine.getSwissRoundCount(safeSongs.size)
                
                repository.saveCompleteFixture(
                    sessionId = session.id,
                    currentRound = nextMatch.round,
                    totalRounds = maxRounds,
                    allMatches = allMatches,
                    currentStandings = swissStandings.standings
                )
            }
        }
        
        // İkili karşılaştırmada maçlar teker teker üretilir; DB'deki toplam yerine
        // tahmini toplam soru sayısı gösterilir (ilerleme çubuğu anlamlı olsun diye)
        val displayTotal = if (currentMethod == "MERGE_SORT") {
            maxOf(PairwiseComparisonSort.estimatedTotalComparisons(safeSongs.size), completed + 1)
        } else {
            total
        }

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            currentMatch = nextMatch,
            song1 = song1,
            song2 = song2,
            completedMatches = completed,
            totalMatches = displayTotal,
            progress = if (displayTotal > 0) completed.toFloat() / displayTotal else 0f,
            emreState = if (currentMethod == "EMRE_CORRECT") emreState else null,
            showMatchingsList = false,  // Maç yüklendiğinde eşleştirmeler listesini gizle
            showInitialRanking = false,  // İlk sıralama tablosunu da gizle
            canUndo = undoSupported() && lastCompletedMatchId != null
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

                loadNextMatch()
            } else {
                // Yeni eşleştirme üretilemedi: loadNextMatch aynı turu tekrar
                // istemesin diye turnuva burada bitirilir (sonsuz döngü koruması)
                completeRanking()
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = "Swiss round oluşturma hatası: ${e.message}"
            )
        }
    }
    
    // Doğru Emre usulü state
    private var emreState: EmreSystemCorrect.EmreState? = null
    
    /**
     * İlk eşleştirmeleri yap - Kullanıcı butona bastığında çağrılır
     */
    fun createFirstRoundMatches() {
        android.util.Log.d("CRASH_DEBUG", "createFirstRoundMatches() BAŞLADI")
        viewModelScope.launch {
            try {
                android.util.Log.d("CRASH_DEBUG", "createFirstRoundMatches() viewModelScope içinde")
                // Critical safety checks
                val safeSongs = getSafeSongs()
                if (safeSongs.isEmpty()) {
                    _uiState.value = _uiState.value.copy(error = "Takım listesi henüz yüklenmedi")
                    return@launch
                }
                
                val currentState = emreState
                if (currentState == null) {
                    _uiState.value = _uiState.value.copy(error = "EmreState bulunamadı")
                    return@launch
                }
                
                
                // İlk tur eşleştirmesini yap - YENİ HİBRİT SİSTEM kullan
                val pairingResult = EmreSystemCorrect.createHybridPairingSystem(currentState)
                
                
                
                if (pairingResult.matches.isNotEmpty()) {
                    repository.createMatches(pairingResult.matches)
                    
                    // Eşleştirmeler listesini göster
                    _uiState.value = _uiState.value.copy(
                        showInitialRanking = false,
                        showMatchingsList = true,
                        matchingsList = pairingResult.matches.sortedBy { it.matchNumber }
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Eşleştirme oluşturulamadı"
                    )
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Eşleştirme hatası: ${e.message}"
                )
            }
        }
    }
    
    private suspend fun createNextEmreRound(round: Int) {
        try {
            val currentState = emreState ?: run {
                return
            }
            val allMatches = repository.getMatchesByListAndMethodSync(currentListId, currentMethod)
            
            // Tamamlanmış maçları işle ve yeni state oluştur
            val completedMatches = allMatches.filter { it.isCompleted && it.round == round - 1 }
            
            if (completedMatches.isNotEmpty()) {
                // Bye geçen takımı bul (varsa)
                val byeTeam = findByeTeam(currentState, completedMatches)
                
                // State'i güncelle
                emreState = RankingEngine.processCorrectEmreResults(currentState, completedMatches, byeTeam)
            }
            
            // Sonraki tur için eşleştirme oluştur - YENİ HİBRİT SİSTEM
            val pairingResult = EmreSystemCorrect.createHybridPairingSystem(emreState!!)
            
            if (!pairingResult.canContinue) {
                // Turnuva tamamlandı
                completeRanking()
                return
            }
            
            if (pairingResult.matches.isNotEmpty()) {
                repository.createMatches(pairingResult.matches)
                
                // Her turda eşleştirmeler listesini göster
                
                _uiState.value = _uiState.value.copy(
                    showInitialRanking = false,
                    showMatchingsList = true,
                    currentMatch = null, // ⚠️ KRİTİK FİX: currentMatch'i null yap ki liste görülebilsin
                    matchingsList = pairingResult.matches.sortedBy { it.matchNumber },
                    emreState = emreState,
                    currentRound = round
                )
                
            } else {
                completeRanking()
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = "Emre round oluşturma hatası: ${e.message}"
            )
        }
    }
    
    private fun findByeTeam(state: EmreSystemCorrect.EmreState, matches: List<Match>): EmreSystemCorrect.EmreTeam? {
        val playedTeamIds = matches.flatMap { listOf(it.songId1, it.songId2) }.toSet()
        val byeTeam = state.teams.find { it.song.id !in playedTeamIds }
        return byeTeam
    }
    
    private fun findByeTeamFromMatches(state: EmreSystemCorrect.EmreState, matches: List<Match>, songs: List<Song>): EmreSystemCorrect.EmreTeam? {
        val playedTeamIds = matches.flatMap { listOf(it.songId1, it.songId2) }.toSet()
        val byeSong = songs.find { it.id !in playedTeamIds }
        return byeSong?.let { song ->
            state.teams.find { it.song.id == song.id }
        }
    }
    
    private suspend fun completeRanking() {
        try {
            val allMatches = repository.getMatchesByListAndMethodSync(currentListId, currentMethod)
            val results = when (currentMethod) {
                "LEAGUE" -> RankingEngine.calculateLeagueResults(songs, allMatches)
                "SWISS" -> RankingEngine.calculateSwissResults(songs, allMatches)
                "EMRE_CORRECT" -> {
                    if (emreState != null) {
                        RankingEngine.calculateCorrectEmreResults(emreState!!)
                    } else {
                        // Fallback: State yoksa tüm maçları yeniden işle
                        var state = EmreSystemCorrect.initializeEmreTournament(songs)
                        val matchesByRound = allMatches.filter { it.isCompleted }.groupBy { it.round }
                        
                        for ((round, roundMatches) in matchesByRound.toSortedMap()) {
                            val byeTeam = findByeTeamFromMatches(state, roundMatches, songs)
                            state = RankingEngine.processCorrectEmreResults(state, roundMatches, byeTeam)
                        }
                        
                        RankingEngine.calculateCorrectEmreResults(state)
                    }
                }
                "ELIMINATION" -> RankingEngine.calculateEliminationResults(songs, allMatches)
                "FULL_ELIMINATION" -> RankingEngine.calculateFullEliminationResults(songs, allMatches)
                "MERGE_SORT" -> PairwiseComparisonSort.calculateResults(songs, allMatches.filter { it.isCompleted })
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
                if (undoSupported()) lastCompletedMatchId = updatedMatch.id

                // Update Swiss state if this is a Swiss tournament
                if (currentMethod == "SWISS") {
                    updateSwissStateAfterMatch(updatedMatch)
                }

                // Update Emre state if this is an Emre tournament
                if (currentMethod == "EMRE_CORRECT") {
                    val roundClosed = updateEmreCorrectStateAfterMatch(updatedMatch)
                    // Tur kapandıysa loadNextMatch çağrılmaz: aynı tur ikinci kez
                    // işlenmesin (çift puanlama) ve yeni turun eşleştirme listesi
                    // ekranda kalsın
                    if (roundClosed) return@let
                }

                loadNextMatch()
            }
        }
    }

    /**
     * Son tamamlanan maç sonucunu geri alır (tur kapanmadıysa).
     * Maç tekrar "oynanmamış" duruma döner ve puanlama ekranına getirilir.
     */
    fun undoLastMatch() {
        viewModelScope.launch {
            try {
                val matchId = lastCompletedMatchId ?: return@launch
                val allMatches = repository.getMatchesByListAndMethodSync(currentListId, currentMethod)
                val match = allMatches.find { it.id == matchId } ?: run {
                    lastCompletedMatchId = null
                    _uiState.value = _uiState.value.copy(canUndo = false)
                    return@launch
                }

                // İkili karşılaştırmada bir sonraki soru önceden oluşturulmuş olabilir;
                // geri alınan cevaba bağlı olduğu için önce o silinir.
                if (currentMethod == "MERGE_SORT") {
                    repository.deleteUncompletedMatches(currentListId, currentMethod)
                }

                val restored = match.copy(winnerId = null, score1 = null, score2 = null, isCompleted = false)
                repository.updateMatch(restored)
                lastCompletedMatchId = null

                val safeSongs = getSafeSongs()
                val (completed, total) = repository.getMatchProgress(currentListId, currentMethod)
                _uiState.value = _uiState.value.copy(
                    currentMatch = restored,
                    song1 = safeSongs.find { it.id == restored.songId1 },
                    song2 = safeSongs.find { it.id == restored.songId2 },
                    completedMatches = completed,
                    totalMatches = total,
                    progress = if (total > 0) completed.toFloat() / total else 0f,
                    isComplete = false,
                    canUndo = false,
                    showMatchingsList = false,
                    showInitialRanking = false
                )

                if (currentMethod == "EMRE_CORRECT") {
                    calculateCurrentStandings()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Geri alma hatası: ${e.message}")
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
                if (undoSupported()) lastCompletedMatchId = updatedMatch.id

                // Update Swiss state if this is a Swiss tournament
                if (currentMethod == "SWISS") {
                    updateSwissStateAfterMatch(updatedMatch)
                }

                // Update Emre state if this is an Emre tournament
                if (currentMethod == "EMRE_CORRECT") {
                    val roundClosed = updateEmreCorrectStateAfterMatch(updatedMatch)
                    // Tur kapandıysa loadNextMatch çağrılmaz (çift puanlama koruması)
                    if (roundClosed) return@let
                }

                loadNextMatch()
            }
        }
    }
    
    fun submitDrawResult(matchId: Long, team1Score: Int, team2Score: Int) {
        viewModelScope.launch {
            val currentState = _uiState.value
            currentState.currentMatch?.let { match ->
                Log.d("RankingViewModel", "🎯 Skor girişi: Takım1=$team1Score, Takım2=$team2Score")
                
                val updatedMatch = match.copy(
                    winnerId = null, // Beraberlik için null
                    isCompleted = true,
                    score1 = team1Score,
                    score2 = team2Score
                )

                try {
                    repository.updateMatch(updatedMatch)
                    if (undoSupported()) lastCompletedMatchId = updatedMatch.id
                    Log.d("RankingViewModel", "✅ Skor güncellendi: ${match.songId1} vs ${match.songId2}")

                    // Beraberlik girişi de diğer sonuç yollarıyla aynı state
                    // güncellemesinden geçmeli (tur kapanışı tek koddan yürüsün)
                    if (currentMethod == "SWISS") {
                        updateSwissStateAfterMatch(updatedMatch)
                    }
                    if (currentMethod == "EMRE_CORRECT") {
                        val roundClosed = updateEmreCorrectStateAfterMatch(updatedMatch)
                        if (roundClosed) return@let
                    }

                    loadNextMatch()
                } catch (e: Exception) {
                    Log.e("RankingViewModel", "Error updating score result", e)
                }
            }
        }
    }
    
    private fun getCurrentSwissRound(completedMatches: Int): Int {
        val matchesPerRound = songs.size / 2
        return (completedMatches / matchesPerRound) + 1
    }
    
    private fun getCurrentEmreRound(completedMatches: Int): Int {
        if (completedMatches == 0) return 1
        
        // Emre usulünde her turda aynı sayıda maç oynanır (tüm takımlar katılır)
        val matchesPerRound = songs.size / 2
        if (matchesPerRound == 0) return 1
        
        return (completedMatches / matchesPerRound) + 1
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
            val targetSize = RankingEngine.getPreviousPowerOfTwo(songCount) // X'den küçük en büyük 2'nin üssü
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
            val remainingTeams = RankingEngine.getRemainingTeamsAfterRound(songs, allMatches, maxRound)
            val eliminatedSoFar = songCount - remainingTeams.size
            
            if (eliminatedSoFar >= teamsToEliminate) {
                // Yeterince takım elendi, final bracket başlat
                if (remainingTeams.size == targetSize) {
                    val finalMatches = RankingEngine.createDirectEliminationMatches(remainingTeams, 101, "FULL_ELIMINATION")
                    repository.createMatches(finalMatches)
                    return true
                } else {
                    // Hedef sayıya ulaştık
                    completeRanking()
                    return false
                }
            }
            
            // Bu turda kim kazandı kim kaybetti?
            val (winners, losers) = RankingEngine.getWinnersAndLosers(songs, currentRoundMatches)
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
                val nextRoundMatches = RankingEngine.createFullEliminationRoundMatches(losers, maxRound + 1)
                repository.createMatches(nextRoundMatches)
                return true

            } else if (losers.size == stillNeedToEliminate) {
                // Kaybeden sayısı Z'ye eşit - bu takımlar kesin elenir, final bracket başlar
                val finalMatches = RankingEngine.createDirectEliminationMatches(remainingTeams, 101, "FULL_ELIMINATION")
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
                    val nextRoundMatches = RankingEngine.createFullEliminationRoundMatches(candidates, maxRound + 1)
                    repository.createMatches(nextRoundMatches)
                    return true
                } else {
                    // Yeterli aday yoksa direkt finale geç
                    val finalMatches = RankingEngine.createDirectEliminationMatches(remainingTeams, 101, "FULL_ELIMINATION")
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
            "EMRE_CORRECT" -> {
                // Resume Emre system from existing matches
                val allMatches = repository.getMatchesByListAndMethodSync(currentListId, currentMethod)
                if (allMatches.isNotEmpty()) {
                    // Recreate Emre state from existing matches
                    var state = EmreSystemCorrect.initializeEmreTournament(songs)
                    val completedMatches = allMatches.filter { it.isCompleted }
                    val matchesByRound = completedMatches.groupBy { it.round }
                    
                    // Process each completed round to rebuild state
                    for ((round, roundMatches) in matchesByRound.toSortedMap()) {
                        val byeTeam = findByeTeamFromMatches(state, roundMatches, songs)
                        state = RankingEngine.processCorrectEmreResults(state, roundMatches, byeTeam)
                    }
                    
                    emreState = state
                    calculateCurrentStandings()
                    
                    // Tamamlanmamış maçlar varsa eşleştirmeler listesini göster
                    val incompleteMatches = allMatches.filter { !it.isCompleted }
                    if (incompleteMatches.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            showInitialRanking = false,
                            showMatchingsList = true,
                            matchingsList = incompleteMatches.sortedBy { it.matchNumber },
                            emreState = emreState
                        )
                    } else {
                        // Tüm maçlar tamamlanmış ama turnuva bitmemiş - sonraki turu oluştur
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            showInitialRanking = false,
                            showMatchingsList = false,
                            emreState = emreState,
                            allSongs = songs,
                            currentMatch = null
                        )
                        // Sonraki turu otomatik oluştur
                        val nextRound = (emreState?.currentRound ?: 0) + 1
                        createNextEmreRound(nextRound)
                    }
                } else {
                    // Hiç maç yok - yeni başlayan turnuva, doğrudan başlat
                    emreState = EmreSystemCorrect.initializeEmreTournament(songs)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        showInitialRanking = false,
                        emreState = emreState,
                        allSongs = songs,
                        currentMatch = null
                    )
                    // İlk turu otomatik oluştur
                    createNextEmreRound(1)
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
            val songList = repository.getSongListById(currentListId)
            val currentTime = System.currentTimeMillis()
            val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
            val formattedDate = dateFormat.format(java.util.Date(currentTime))
            val methodName = when (currentMethod) {
                "EMRE_CORRECT" -> "Geliştirilmiş İsviçre"
                "SWISS" -> "İsviçre"
                "LEAGUE" -> "Lig"
                "ELIMINATION" -> "Eleme"
                "FULL_ELIMINATION" -> "Tam Eleme"
                "DIRECT_SCORING" -> "Direkt Puanlama"
                else -> currentMethod
            }
            
            val newSession = VotingSession(
                listId = currentListId,
                rankingMethod = currentMethod,
                sessionName = "${songList?.name ?: "Liste"} - $methodName ($formattedDate)",
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
    
    private suspend fun calculateCurrentStandings() {
        try {
            val allMatches = repository.getMatchesByListAndMethodSync(currentListId, currentMethod)
            val completedMatches = allMatches.filter { it.isCompleted }
            
            if (currentMethod == "EMRE_CORRECT") {
                // Emre sistemi için puan hesaplama
                val standings = songs.map { song ->
                    var points = 0.0
                    var played = 0
                    var won = 0
                    var drawn = 0
                    var lost = 0
                    
                    completedMatches.forEach { match ->
                        if (match.songId1 == song.id || match.songId2 == song.id) {
                            played++
                            when (match.winnerId) {
                                song.id -> {
                                    won++
                                    points += 1.0
                                }
                                null -> {
                                    drawn++
                                    points += 0.5
                                }
                                else -> {
                                    lost++
                                }
                            }
                        }
                    }
                    
                    StandingEntry(
                        position = 0, // Will be set after sorting
                        song = song,
                        points = points,
                        played = played,
                        won = won,
                        drawn = drawn,
                        lost = lost
                    )
                }.sortedWith(
                    compareByDescending<StandingEntry> { it.points }
                        .thenBy { songs.indexOf(it.song) } // Original position as tiebreaker
                ).mapIndexed { index, entry ->
                    entry.copy(position = index + 1)
                }
                
                _uiState.value = _uiState.value.copy(currentStandings = standings)
            } else if (currentMethod == "LEAGUE") {
                // League sistemi için mevcut hesaplama (varsa)
                // Bu kısım zaten var olabilir
            }
        } catch (e: Exception) {
            // Hata durumunda boş liste
            _uiState.value = _uiState.value.copy(currentStandings = emptyList())
        }
    }
    
    /**
     * Emre state'ini tamamlanan maça göre günceller.
     *
     * @return true ise tur burada kapatıldı (puanlar işlendi, yeni tur üretildi
     * veya turnuva tamamlandı) — çağıran loadNextMatch() ÇAĞIRMAMALIDIR; aksi
     * halde aynı turun sonuçları createNextEmreRound üzerinden ikinci kez
     * işlenip puanlar bozulur.
     */
    private suspend fun updateEmreCorrectStateAfterMatch(completedMatch: Match): Boolean {
        try {
            val currentState = emreState ?: return false
            val allMatches = repository.getMatchesByListAndMethodSync(currentListId, currentMethod)

            // Bu turda tamamlanan tüm maçları al - matchNumber sıralaması ile
            val currentRoundMatches = allMatches.filter {
                it.isCompleted && it.round == completedMatch.round
            }.sortedBy { it.matchNumber }


            // Tur tamamlandı mı kontrol et
            // ⚠️ KRİTİK: Expected matches sayısı takım sayısına göre sabittir
            val expectedMatchesInRound = if (songs.size % 2 == 0) {
                songs.size / 2  // Çift takım = tam eşleştirme
            } else {
                (songs.size - 1) / 2  // Tek takım = 1 bye + eşleştirmeler
            }


            if (currentRoundMatches.size >= expectedMatchesInRound) {
                // Tur tamamlandı, sonuçları işle
                // Tur kapandıktan sonra maç geri alınamaz (yeni tur eşleştirmeleri bu sonuçlara dayanır)
                lastCompletedMatchId = null
                val byeTeam = findByeTeam(currentState, currentRoundMatches)
                emreState = RankingEngine.processCorrectEmreResults(currentState, currentRoundMatches, byeTeam)

                // Sonraki tur için eşleştirme oluştur - YENİ HİBRİT SİSTEM
                val pairingResult = EmreSystemCorrect.createHybridPairingSystem(emreState!!)

                if (!pairingResult.canContinue || pairingResult.matches.isEmpty()) {
                    // Turnuva tamamlandı
                    completeRanking()
                    calculateCurrentStandings()
                    return true
                }

                repository.createMatches(pairingResult.matches)

                // Yeni turun eşleştirmeler listesini göster
                // (currentMatch=null olmalı ki liste ekranı görünsün)
                _uiState.value = _uiState.value.copy(
                    showInitialRanking = false,
                    showMatchingsList = true,
                    currentMatch = null,
                    matchingsList = pairingResult.matches.sortedBy { it.matchNumber },
                    emreState = emreState
                )
                calculateCurrentStandings()
                return true
            }

            // Standings'i güncelle
            calculateCurrentStandings()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = "Emre durumu güncelleme hatası: ${e.message}"
            )
        }
        return false
    }
    
    // Test Protocol Functions
    fun showTestReport(reportType: String) {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                if (currentState.emreState == null) {
                    return@launch
                }
                
                // Completed matches'ları database'den al
                val completedMatches = repository.getCompletedMatchesForList(currentListId)
                
                val (reportData, reportTitle) = when (reportType) {
                    "match_history" -> {
                        val report = TournamentTestProtocol.generateMatchHistoryReport(currentState.emreState, completedMatches)
                        Pair(report, "Eşleştirme Geçmişi Raporu")
                    }
                    "round_by_round" -> {
                        val expectedMatchesPerRound = currentState.emreState.teams.size / 2
                        val report = TournamentTestProtocol.generateRoundByRoundReport(completedMatches, expectedMatchesPerRound)
                        Pair(report, "Tur Bazında Maç Raporu")
                    }
                    "asymmetric_points" -> {
                        // Son oluşturulan eşleştirmeler için CandidateMatch listesi oluştur
                        val candidateMatches = currentState.matchingsList.map { match ->
                            val team1 = currentState.emreState.teams.find { it.song.id == match.songId1 }
                            val team2 = currentState.emreState.teams.find { it.song.id == match.songId2 }
                            if (team1 != null && team2 != null) {
                                EmreSystemCorrect.CandidateMatch(team1, team2, (team1.points != team2.points))
                            } else {
                                null
                            }
                        }.filterNotNull()
                        
                        val report = TournamentTestProtocol.generateAsymmetricPointsReport(candidateMatches)
                        Pair(report, "Asimetrik Puan Analiz Raporu")
                    }
                    else -> {
                        Pair("Bilinmeyen rapor tipi: $reportType", "Hata")
                    }
                }
                
                _uiState.value = _uiState.value.copy(
                    showTestReport = true,
                    testReportData = reportData,
                    testReportTitle = reportTitle
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showTestReport = true,
                    testReportData = "Rapor oluşturulurken hata oluştu: ${e.message}",
                    testReportTitle = "Hata"
                )
            }
        }
    }
    
    fun hideTestReport() {
        _uiState.value = _uiState.value.copy(
            showTestReport = false,
            testReportData = "",
            testReportTitle = ""
        )
    }
    
    suspend fun getCriteriaForTournament(tournamentId: Long): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                // Tournament'tan criterionListId'yi al
                val tournament = database.tournamentDao().getTournamentById(tournamentId)

                if (tournament?.criterionListId != null) {
                    // CriterionList'ten criteria JSON'unu al
                    val criterionList = database.criterionListDao().getCriterionListById(tournament.criterionListId)

                    if (criterionList != null) {
                        // JSON'u parse et ve liste olarak döndür
                        val gson = Gson()
                        val criteriaArray = gson.fromJson(criterionList.criteria, Array<String>::class.java)
                        val result = criteriaArray.toList()
                        return@withContext result
                    }
                }
                emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    suspend fun getCriteriaSettingsForTournament(tournamentId: Long): Map<String, Any>? {
        return withContext(Dispatchers.IO) {
            try {
                // Tournament'tan criteriaSettings JSON'unu al
                val tournament = database.tournamentDao().getTournamentById(tournamentId)
                if (tournament?.criteriaSettings != null) {
                    // JSON'u parse et ve Map olarak döndür
                    val gson = Gson()
                    val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                    return@withContext gson.fromJson(tournament.criteriaSettings, type)
                }
                null
            } catch (e: Exception) {
                null
            }
        }
    }
}