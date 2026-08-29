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
import com.example.ranking.ranking.SwissSystem
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
        val showMatchingsList: Boolean = false, // Eşleştirmeler listesini göster
        val matchingsList: List<Match> = emptyList(), // Oluşturulan eşleştirmeler
        val method: String = "", // Ranking metodu
        val canUndo: Boolean = false, // Son maç sonucu geri alınabilir mi
        // Bu liste+yöntem için aktif turnuva kaydı (kriter dialogu bununla
        // kriterleri bulur; maçlarda tournamentId tutulmadığı için gerekli)
        val activeTournamentId: Long? = null
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

    /**
     * Son sonucun geri alınabildiği yöntemler.
     *
     * Maç tabanlı ve sonucu tersine çevrilebilir olanlar. Direkt Puanlama
     * hariç: orada "son maç" kavramı yok, puanlar tek tek kaydediliyor.
     */
    private fun undoSupported(): Boolean =
        currentMethod in setOf("LEAGUE", "SWISS", "EMRE_CORRECT", "MERGE_SORT")
    
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
    
    fun initializeRanking(listId: Long, method: String, @Suppress("UNUSED_PARAMETER") pairingMethodName: String = "SEQUENTIAL", forceNew: Boolean = true) {
        currentListId = listId
        currentMethod = method

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

                // Sihirbazdan oluşturulan turnuva kaydı (kriter listesi bilgisi
                // burada; maçlar tournamentId taşımadığından ayrıca çözülür)
                val activeTournament = database.tournamentDao()
                    .getActiveTournamentForList(listId, method)

                // 🔴 TEK SEFERLİK okuma. Eskiden sonsuz bir collect'ti ve
                // gövdesinde initializeEmre/initializeLeague/... çağrılıyordu —
                // bu fonksiyonlar clearMatches yapar. songs tablosuna her yazma
                // yeni bir emission üretip init'i baştan koşturuyordu: turnuva
                // ekranı açıkken bir öğe adı düzenlenirse TURNUVA SIFIRLANIYORDU.
                // Ayrıca collect hiç tamamlanmadığı için her çağrı kalıcı bir
                // collector bırakıyordu.
                val songList = repository.getSongsByListIdSync(listId)
                run {
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
                            method = method,
                            activeTournamentId = activeTournament?.id
                        )
                        
                        // 🔴 KURTARMA: oturum kaydı yok AMA maçlar duruyorsa
                        // turnuva sıfırlanmaz — maçlardan devam edilir.
                        //
                        // Oturum kaydı birkaç yoldan kaybolabiliyor: yöntem
                        // eskiden hiç oturum kurmuyordu (Lig ve İkili
                        // Karşılaştırma), önceki bir "yeni turnuva" girişi
                        // oturumu kapatmış olabiliyor, ya da oturum yazılmadan
                        // uygulama kapanabiliyor. Bu durumda eskiden
                        // initializeX() çağrılıyor ve oynanmış maçların hepsi
                        // siliniyordu. Maç kayıtları asıl gerçektir; oturum
                        // yalnız bir imleç.
                        val mevcutMaclar = if (!forceNew && activeSession == null) {
                            repository.getMatchesByListAndMethodSync(listId, method)
                        } else {
                            emptyList()
                        }

                        if (activeSession != null) {
                            // Resume existing session
                            resumeSession(activeSession)
                        } else if (mevcutMaclar.isNotEmpty()) {
                            // Eksik oturumu kur, sonra maçlardan devam et
                            createOrUpdateSession()
                            currentVotingSession?.let { yeniOturum ->
                                _uiState.value = _uiState.value.copy(
                                    currentSession = yeniOturum,
                                    hasActiveSession = true
                                )
                                resumeSession(yeniOturum)
                            } ?: loadNextMatch()
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
                                // SINGLE/DOUBLE_ELIMINATION kaldırıldı: algoritmaları
                                // tamamlanmadı ve UI'dan seçilemiyorlar
                                else -> {
                                    _uiState.value = _uiState.value.copy(
                                        isLoading = false,
                                        error = "Bilinmeyen sıralama yöntemi: $method"
                                    )
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
            // Oturum kaydi olmadan getActiveSession daima null doner; bu yuzden
            // ekrana her giris "yeni turnuva" sayilip clearMatches ile oynanmis
            // maclari siliyor, Duraklat/Sifirla butonlari da hic gorunmuyordu.
            createOrUpdateSession()
            val settings = _uiState.value.leagueSettings
            val doubleRoundRobin = settings?.doubleRoundRobin ?: false
            val matches = RankingEngine.createLeagueMatches(songs, doubleRoundRobin)
            repository.createMatches(matches)
            loadNextMatch()
        }
    }

    private fun initializePairwiseSort() {
        viewModelScope.launch {
            // Bkz. initializeLeague: oturum kaydi olmayan yontemde her giris
            // cevaplanmis tum karsilastirmalari siliyordu
            createOrUpdateSession()
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
                
                    
                // Session oluştur
                createOrUpdateSession()
                
                // Doğru Emre usulü sistem başlatma
                emreState = EmreSystemCorrect.initializeEmreTournament(safeSongs)

                // Doğrudan turnuvayı başlat - ara ekran gösterme
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
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
            val matches = RankingEngine.createEliminationMatches(songs)
            repository.createMatches(matches)
            loadNextMatch()
        }
    }
    
    private fun initializeFullElimination() {
        viewModelScope.launch {
            val matches = RankingEngine.createFullEliminationMatches(songs)
            repository.createMatches(matches)
            loadNextMatch()
        }
    }
    
    fun selectMatch(match: Match) {
        viewModelScope.launch {
            try {
                // Takım bilgilerini yükle
                val song1 = songs.find { it.id == match.songId1 }
                val song2 = songs.find { it.id == match.songId2 }

                // Seçilen maçı current match olarak ayarla
                _uiState.value = _uiState.value.copy(
                    currentMatch = match,
                    song1 = song1,
                    song2 = song2
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Maç seçme hatası: ${e.message}")
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
                    // Tur numarası maçlardan türetilir: bye varken tur başına
                    // (n-1)/2 maç olduğu için "tamamlanan / (n/2)" hesabı tek
                    // takım sayısında yanlış tur veriyordu.
                    val sonTur = repository.getMatchesByListAndMethodSync(currentListId, currentMethod)
                        .maxOfOrNull { it.round } ?: 0
                    val siradakiTur = sonTur + 1
                    if (siradakiTur <= SwissSystem.recommendedRoundCount(songs.size)) {
                        createNextSwissRound(siradakiTur)
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
        val allMatchesForProgress = if (currentMethod == "EMRE_CORRECT") {
            repository.getMatchesByListAndMethodSync(currentListId, currentMethod)
        } else {
            emptyList()
        }

        // İlerleme paydası.
        //
        // EMRE_CORRECT'te turnuvanın toplam maç sayısı ÖNCEDEN BİLİNMEZ (turlar
        // eşleşme kurulabildiği sürece üretilir). Payda "o ana kadar yaratılmış
        // maçlar" olduğu için her tur sonunda 4/4 → çubuk doluyor, kullanıcı
        // turnuvanın bittiğini sanıyor; yeni tur üretilince 4/8'e düşüyordu.
        // Çözüm: Emre'de TUR İÇİ ilerleme gösterilir — "bu turun 2/4 maçı".
        val displayTotal = when (currentMethod) {
            "MERGE_SORT" ->
                maxOf(PairwiseComparisonSort.estimatedTotalComparisons(safeSongs.size), completed + 1)
            "EMRE_CORRECT" -> {
                val buTur = nextMatch?.round ?: _uiState.value.currentRound
                allMatchesForProgress.count { it.round == buTur }.coerceAtLeast(1)
            }
            else -> total
        }
        val displayCompleted = if (currentMethod == "EMRE_CORRECT") {
            val buTur = nextMatch?.round ?: _uiState.value.currentRound
            allMatchesForProgress.count { it.round == buTur && it.isCompleted }
        } else {
            completed
        }

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            currentMatch = nextMatch,
            song1 = song1,
            song2 = song2,
            completedMatches = displayCompleted,
            totalMatches = displayTotal,
            progress = if (displayTotal > 0) displayCompleted.toFloat() / displayTotal else 0f,
            emreState = if (currentMethod == "EMRE_CORRECT") emreState else null,
            showMatchingsList = false,  // Maç yüklendiğinde eşleştirmeler listesini gizle
            canUndo = undoSupported() && lastCompletedMatchId != null
        )
    }
    
    /**
     * İsviçre sisteminde sıradaki turu üretir — YENİ MOTOR (`SwissSystem`).
     *
     * Eski yol `RankingEngine.createSwissMatchesWithState` idi ve dört kuralı
     * birden çiğniyordu: bye yoktu (7 takımda 6 numaralı takım hiç eşleşmiyor,
     * hiç puan almıyor, sessizce turdan düşüyordu), puan grubunda tek kalan
     * takım da düşüyordu, tekrar eşleşme serbest bırakılmıştı ("if no fresh
     * pairing found, pair the first two available") ve matchNumber hiç
     * atanmıyordu. Bu yüzden sistem menüden gizlenmişti.
     */
    private suspend fun createNextSwissRound(round: Int) {
        try {
            val allMatches = repository.getMatchesByListAndMethodSync(currentListId, currentMethod)
            val completedMatches = allMatches.filter { it.isCompleted }

            val state = SwissSystem.computeState(songs, completedMatches)
            val pairing = SwissSystem.createNextRound(state, completedMatches)

            if (!pairing.canContinue || pairing.matches.isEmpty()) {
                // Turnuva dürüstçe biter (tekrarsız tam eşleştirme kurulamıyor
                // ya da tur bütçesi doldu). loadNextMatch aynı turu tekrar
                // istemesin diye burada kapatılır.
                completeRanking()
                return
            }

            // id'leri yazılmış kopyalar (bkz. createMatches) — id=0 kalırsa
            // listeden seçilen maçın sonucu sessizce kaybolur
            val kayitliMaclar = repository.createMatches(pairing.matches)

            // BYE geçen takımın puanı maç kaydı üretmez; state'ten okunur.
            // Kullanıcıya da bildirilir, yoksa "benim takımım niye oynamadı"
            // sorusu cevapsız kalır.
            byeBilgisi = pairing.byeTeam?.song?.name

            currentVotingSession?.let { session ->
                repository.saveSwissState(
                    sessionId = session.id,
                    currentRound = round,
                    maxRounds = state.maxRounds,
                    standings = state.teams.associate { it.id to it.points },
                    pairingHistory = emptySet(),
                    roundHistory = emptyList()
                )
            }

            _uiState.value = _uiState.value.copy(
                currentRound = round,
                matchingsList = kayitliMaclar.sortedBy { it.matchNumber }
            )

            loadNextMatch()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = "İsviçre turu oluşturulamadı: ${e.message}"
            )
        }
    }

    /** Bu turda bye geçen takımın adı (İsviçre/Emre) — UI bilgilendirmesi için. */
    private var byeBilgisi: String? = null
    
    // Doğru Emre usulü state
    private var emreState: EmreSystemCorrect.EmreState? = null

    private suspend fun createNextEmreRound(round: Int) {
        try {
            val currentState = emreState ?: run {
                return
            }
            val allMatches = repository.getMatchesByListAndMethodSync(currentListId, currentMethod)
            
            // Tamamlanmış maçları işle ve yeni state oluştur.
            //
            // 🔴 ÇİFT İŞLEME KORUMASI: tur kapanışı normalde
            // `updateEmreCorrectStateAfterMatch` içinde yapılır ve state oradaki
            // `processCorrectEmreResults` ile bir sonraki tura geçer
            // (currentRound = R+1). Burada aynı turu bir kez daha işlemek
            // puanları ikiye katlar. State zaten ilerlemişse geçmiş tur
            // yeniden işlenmez.
            val oncekiTurZatenIslendi = currentState.currentRound >= round
            val completedMatches = if (oncekiTurZatenIslendi) {
                emptyList()
            } else {
                allMatches.filter { it.isCompleted && it.round == round - 1 }
            }

            if (completedMatches.isNotEmpty()) {
                // Bye geçen takımı bul (varsa)
                val byeTeam = findByeTeam(currentState, completedMatches)

                // State'i güncelle (tiebreaker tüm maç geçmişini görmeli)
                emreState = RankingEngine.processCorrectEmreResults(
                    currentState,
                    completedMatches,
                    byeTeam,
                    allCompletedMatches = allMatches.filter { it.isCompleted }
                )
            }
            
            // Sonraki tur için eşleştirme oluştur - YENİ HİBRİT SİSTEM
            val pairingResult = EmreSystemCorrect.createHybridPairingSystem(emreState!!)
            
            if (!pairingResult.canContinue) {
                // Turnuva tamamlandı
                completeRanking()
                return
            }
            
            if (pairingResult.matches.isNotEmpty()) {
                // id'si yazılmış kopyalar alınır: listeden seçilen maç @Update ile
                // güncelleneceği için id=0 kalırsa o turun ilk sonucu kaybolur
                val kayitliMaclar = repository.createMatches(pairingResult.matches)

                // Her turda eşleştirmeler listesini göster

                _uiState.value = _uiState.value.copy(
                    showMatchingsList = true,
                    currentMatch = null, // currentMatch null olmalı ki liste görülebilsin
                    matchingsList = kayitliMaclar.sortedBy { it.matchNumber },
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
    
    /**
     * Bir turun bye geçen takımını maç kayıtlarından bulur.
     *
     * Bye YALNIZCA tek sayıda takım varken oluşur. Çift takımda "maç
     * oynamamış takım" aramak, yarım kalmış bir turda oynamamış takımı
     * bye sanıp ona hayalet puan verir.
     */
    private fun findByeTeamFromMatches(state: EmreSystemCorrect.EmreState, matches: List<Match>, songs: List<Song>): EmreSystemCorrect.EmreTeam? {
        if (songs.size % 2 == 0) return null
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
                "SWISS" -> SwissSystem.calculateResults(songs, allMatches.filter { it.isCompleted })
                "EMRE_CORRECT" -> {
                    if (emreState != null) {
                        RankingEngine.calculateCorrectEmreResults(emreState!!)
                    } else {
                        // Fallback: State yoksa tüm maçları yeniden işle
                        var state = EmreSystemCorrect.initializeEmreTournament(songs)
                        val matchesByRound = allMatches.filter { it.isCompleted }.groupBy { it.round }
                        
                        val tumTamamlanan = allMatches.filter { it.isCompleted }
                        for ((round, roundMatches) in matchesByRound.toSortedMap()) {
                            val byeTeam = findByeTeamFromMatches(state, roundMatches, songs)
                            // Tiebreaker zinciri TÜM geçmişi görmeli (bkz. resumeSession)
                            state = RankingEngine.processCorrectEmreResults(
                                state,
                                roundMatches,
                                byeTeam,
                                allCompletedMatches = tumTamamlanan
                            )
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

            // Oturumu ve turnuva kaydini KAPAT.
            // Kapanmadiginda bitmis turnuva "Aktif Turnuvalar"da kalir ve
            // "Devam Et" bitmis oturumu yeniden acar.
            currentVotingSession?.let { oturum ->
                votingSessionDao.completeTournament(oturum.id, System.currentTimeMillis())
            }
            _uiState.value.activeTournamentId?.let { turnuvaId ->
                database.tournamentDao().completeTournament(turnuvaId)
            }

            _uiState.value = _uiState.value.copy(
                isComplete = true,
                progress = 1f,
                hasActiveSession = false
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
            // ⚠️ Oturum yoksa puan HİÇ YAZILMIYORDU: yalnız bellekte kalıp
            // uygulama kapanınca sessizce kayboluyordu. Oturum burada
            // garanti altına alınır — puan diske yazılmadan devam edilmez.
            if (currentVotingSession == null) {
                createOrUpdateSession()
            }
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
                    showMatchingsList = false
                )

                // Puan tablosu geri alınan sonucu yansıtmalı — yoksa silinen
                // puan tabloda durmaya devam eder
                if (currentMethod in setOf("EMRE_CORRECT", "LEAGUE", "SWISS")) {
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
                    
                    // Turdaki toplam maç sayısı: yarım kalmış bir tur
                    // BİTMİŞ gibi işlenirse, tur gerçekten kapanınca aynı
                    // maçlar ikinci kez sayılır ve puanlar çift olur.
                    val macSayisiByRound = allMatches.groupBy { it.round }
                        .mapValues { (_, macs) -> macs.size }

                    // Process each completed round to rebuild state
                    for ((round, roundMatches) in matchesByRound.toSortedMap()) {
                        if (roundMatches.size < (macSayisiByRound[round] ?: 0)) continue
                        val byeTeam = findByeTeamFromMatches(state, roundMatches, songs)
                        // Tiebreaker zinciri TÜM geçmişi görmeli; yalnız bu turun
                        // maçlarıyla kırılan eşitlik, canlı akıştan farklı bir
                        // sıra (ve farklı bir sonraki fikstür) üretir.
                        state = RankingEngine.processCorrectEmreResults(
                            state,
                            roundMatches,
                            byeTeam,
                            allCompletedMatches = completedMatches
                        )
                    }

                    emreState = state
                    calculateCurrentStandings()
                    
                    // Tamamlanmamış maçlar varsa eşleştirmeler listesini göster
                    val incompleteMatches = allMatches.filter { !it.isCompleted }
                    if (incompleteMatches.isNotEmpty()) {
                        // KALDIĞI NOKTAYA DÖN — eşleştirme listesine değil, oy
                        // verilecek MAÇA. Kullanıcı turnuvayı bir maçın başında
                        // bırakıyor; dönüşte liste ekranı gelince "kaçıncı
                        // maçtaydım" sorusunu kendisi çözmek zorunda kalıyordu.
                        //
                        // Maçı loadNextMatch() seçiyor: sorgusu round ASC +
                        // matchNumber ASC, yani Emre usulünün oylama sırası.
                        // İlerleme sayaçlarını ve geri alma durumunu da o kurar.
                        _uiState.value = _uiState.value.copy(
                            matchingsList = incompleteMatches.sortedBy { it.matchNumber },
                            emreState = emreState,
                            allSongs = songs,
                            currentRound = incompleteMatches.minOf { it.round }
                        )
                        loadNextMatch()
                    } else {
                        // Tüm maçlar tamamlanmış ama turnuva bitmemiş - sonraki turu oluştur
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
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
                "MERGE_SORT" -> "İkili Karşılaştırma"
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
                // Emre sistemi için puan hesaplama.
                // Maçlardan canlı hesaplanır (tur ortasında da güncel);
                // bye puanı maç kaydı üretmediği için emreState'ten eklenir.
                val stateTeams = emreState?.teams?.associateBy { it.song.id }
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

                    // Bye geçen turların +1 puanı (kapanmış turlar için state'te tutulur)
                    points += (stateTeams?.get(song.id)?.byeCount ?: 0) * 1.0

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
                        // Algoritmanın kendi sıralaması (tiebreaker zinciri işlenmiş) esas alınır
                        .thenBy { stateTeams?.get(it.song.id)?.currentPosition ?: songs.indexOf(it.song) }
                ).mapIndexed { index, entry ->
                    entry.copy(position = index + 1)
                }
                
                _uiState.value = _uiState.value.copy(currentStandings = standings)
            } else if (currentMethod == "LEAGUE" || currentMethod == "SWISS") {
                // Lig ve İsviçre: canlı puan durumu maçlardan hesaplanır.
                // Bu dal eskiden BOŞ bir yorum bloğuydu ("Bu kısım zaten var
                // olabilir") — ligde "Puan Durumu" ekranı hep boş çıkıyordu.
                val ligPuani = if (currentMethod == "LEAGUE") 3.0 else 1.0
                val beraberlikPuani = if (currentMethod == "LEAGUE") 1.0 else 0.5

                val standings = songs.map { song ->
                    var points = 0.0
                    var played = 0
                    var won = 0
                    var drawn = 0
                    var lost = 0
                    var attilan = 0
                    var yenilen = 0

                    completedMatches.forEach { match ->
                        val birinci = match.songId1 == song.id
                        val ikinci = match.songId2 == song.id
                        if (!birinci && !ikinci) return@forEach

                        played++
                        // Skor girilmişse averaj için topla
                        val kendiSkor = if (birinci) match.score1 else match.score2
                        val rakipSkor = if (birinci) match.score2 else match.score1
                        attilan += kendiSkor ?: 0
                        yenilen += rakipSkor ?: 0

                        when (match.winnerId) {
                            song.id -> { won++; points += ligPuani }
                            null -> { drawn++; points += beraberlikPuani }
                            else -> lost++
                        }
                    }

                    Triple(
                        StandingEntry(
                            position = 0,
                            song = song,
                            points = points,
                            played = played,
                            won = won,
                            drawn = drawn,
                            lost = lost
                        ),
                        attilan - yenilen,  // averaj
                        attilan             // atılan
                    )
                }.sortedWith(
                    // Toplam sıralı zincir: puan → averaj → atılan → galibiyet → id.
                    // "Aralarındaki maç" kriteri BİLEREK yok: karşılaştırıcıyı
                    // geçişsiz yapıp TimSort'u çökertiyor (bkz. EmreSystemCorrect).
                    compareByDescending<Triple<StandingEntry, Int, Int>> { it.first.points }
                        .thenByDescending { it.second }
                        .thenByDescending { it.third }
                        .thenByDescending { it.first.won }
                        .thenBy { it.first.song.id }
                ).mapIndexed { index, (entry, _, _) ->
                    entry.copy(position = index + 1)
                }

                _uiState.value = _uiState.value.copy(currentStandings = standings)
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


            // Tur tamamlandı mı? — O TURUN GERÇEK maç sayısına bakılır.
            //
            // Eskiden `songs.size / 2` sabiti kullanılıyordu. O sabit, motorun
            // her turda tam eşleştirme ürettiği varsayımına dayanıyor; bir tur
            // herhangi bir sebeple daha az maç içerirse (ya da veritabanında
            // tur numaraları çakışırsa) sayı hiç tutmuyor, tur KAPANMIYOR ve
            // loadNextMatch boş maç bulup createNextEmreRound'u çağırıyor —
            // eşleştirme penceresi tur ortasında yeniden açılıyor.
            val turdakiTumMaclar = allMatches.count { it.round == completedMatch.round }
            val expectedMatchesInRound = if (turdakiTumMaclar > 0) {
                turdakiTumMaclar
            } else if (songs.size % 2 == 0) {
                songs.size / 2
            } else {
                (songs.size - 1) / 2
            }


            if (currentRoundMatches.size >= expectedMatchesInRound) {
                // Tur tamamlandı, sonuçları işle
                // Tur kapandıktan sonra maç geri alınamaz (yeni tur eşleştirmeleri bu sonuçlara dayanır)
                lastCompletedMatchId = null
                // Buton da kaybolmalı: canUndo true kalırsa kullanıcı basıyor
                // ve hiçbir şey olmuyordu (imleç boş, işlem sessizce dönüyor)
                _uiState.value = _uiState.value.copy(canUndo = false)
                val byeTeam = findByeTeam(currentState, currentRoundMatches)
                emreState = RankingEngine.processCorrectEmreResults(
                    currentState,
                    currentRoundMatches,
                    byeTeam,
                    allCompletedMatches = allMatches.filter { it.isCompleted }
                )

                // Sonraki tur için eşleştirme oluştur - YENİ HİBRİT SİSTEM
                val pairingResult = EmreSystemCorrect.createHybridPairingSystem(emreState!!)

                if (!pairingResult.canContinue || pairingResult.matches.isEmpty()) {
                    // Turnuva tamamlandı
                    completeRanking()
                    calculateCurrentStandings()
                    return true
                }

                // id'si yazılmış kopyalar alınır (bkz. createMatches)
                val kayitliMaclar = repository.createMatches(pairingResult.matches)

                // Yeni turun eşleştirmeler listesini göster
                // (currentMatch=null olmalı ki liste ekranı görünsün)
                _uiState.value = _uiState.value.copy(
                    showMatchingsList = true,
                    currentMatch = null,
                    matchingsList = kayitliMaclar.sortedBy { it.matchNumber },
                    emreState = emreState,
                    currentRound = emreState!!.currentRound
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
    
    /**
     * Kriter dialogundan gelen puanları criterion_scores tablosuna yazar.
     * Klasik ranking akışında maçlarda tournamentId olmayabilir; o durumda
     * liste+yöntem için aktif turnuva kaydı bulunur. Turnuva kaydı yoksa
     * FK ihlali yaşamamak için skorlar kaydedilmez.
     */
    fun saveCriteriaScores(match: Match, scores: Map<String, Pair<Double?, Double?>>) {
        viewModelScope.launch {
            try {
                val tournamentId = match.tournamentId
                    ?: database.tournamentDao()
                        .getActiveTournamentForList(currentListId, currentMethod)?.id
                    ?: return@launch

                val entries = scores.mapNotNull { (criterionName, pair) ->
                    if (pair.first == null && pair.second == null) null
                    else com.example.ranking.data.CriterionScore(
                        matchId = match.id,
                        tournamentId = tournamentId,
                        criterionName = criterionName,
                        team1Score = pair.first,
                        team2Score = pair.second
                    )
                }
                if (entries.isNotEmpty()) {
                    database.criterionScoreDao().insertCriterionScores(entries)
                }
            } catch (e: Exception) {
                Log.e("RankingViewModel", "Kriter skorları kaydedilemedi: ${e.message}", e)
            }
        }
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