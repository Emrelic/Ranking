package com.example.ranking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ranking.data.*
import com.example.ranking.repository.RankingRepository
import com.example.ranking.utils.CsvReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.gson.Gson

class ResultsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = RankingDatabase.getDatabase(application)
    private val repository = RankingRepository(
        songDao = database.songDao(),
        songListDao = database.songListDao(),
        rankingResultDao = database.rankingResultDao(),
        matchDao = database.matchDao(),
        leagueSettingsDao = database.leagueSettingsDao(),
        archiveDao = database.archiveDao(),
        csvReader = CsvReader()
    )
    
    private val _results = MutableStateFlow<List<Pair<RankingResult, Song>>>(emptyList())
    val results: StateFlow<List<Pair<RankingResult, Song>>> = _results.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _leagueTable = MutableStateFlow<List<LeagueTableEntry>>(emptyList())
    val leagueTable: StateFlow<List<LeagueTableEntry>> = _leagueTable.asStateFlow()
    
    private val _matchSummary = MutableStateFlow<List<MatchSummaryItem>>(emptyList())
    val matchSummary: StateFlow<List<MatchSummaryItem>> = _matchSummary.asStateFlow()
    
    data class LeagueTableEntry(
        val teamName: String,
        val played: Int,
        val won: Int,
        val drawn: Int,
        val lost: Int,
        val goalsFor: Int,
        val goalsAgainst: Int,
        val goalDifference: Int,
        val points: Int
    )
    
    data class MatchSummaryItem(
        val team1Id: Long,
        val team1Name: String,
        val team2Id: Long,
        val team2Name: String,
        val score1: Int?,
        val score2: Int?,
        val winnerId: Long?
    )
    
    data class ArchivableResult(
        val songId: Long,
        val songName: String,
        val artist: String,
        val album: String,
        val score: Double,
        val position: Int
    )
    
    data class ArchivableMatch(
        val team1Id: Long,
        val team1Name: String,
        val team2Id: Long,
        val team2Name: String,
        val score1: Int?,
        val score2: Int?,
        val winnerId: Long?,
        val isCompleted: Boolean,
        val round: Int
    )
    
    data class ArchivableLeagueSettings(
        val useScores: Boolean,
        val winPoints: Int,
        val drawPoints: Int,
        val allowDraws: Boolean,
        val doubleRoundRobin: Boolean
    )
    
    fun loadResults(listId: Long, method: String) {
        viewModelScope.launch {
            _isLoading.value = true
            
            repository.getRankingResults(listId, method).collect { rankingResults ->
                val resultsWithSongs = mutableListOf<Pair<RankingResult, Song>>()
                
                for (result in rankingResults) {
                    val song = database.songDao().getSongById(result.songId)
                    if (song != null) {
                        resultsWithSongs.add(Pair(result, song))
                    }
                }
                
                _results.value = resultsWithSongs
                _isLoading.value = false
            }
        }
    }
    
    fun loadLeagueTable(listId: Long) {
        viewModelScope.launch {
            try {
                val matches = repository.getMatchesByListAndMethodSync(listId, "LEAGUE")
                val songs = repository.getSongsByListId(listId)
                val settings = repository.getLeagueSettings(listId, "LEAGUE")
                
                // Calculate table entries
                val tableEntries = mutableMapOf<Long, LeagueTableEntry>()
                
                songs.collect { songList ->
                    // Initialize entries for all teams
                    songList.forEach { song ->
                        tableEntries[song.id] = LeagueTableEntry(
                            teamName = song.name,
                            played = 0,
                            won = 0,
                            drawn = 0,
                            lost = 0,
                            goalsFor = 0,
                            goalsAgainst = 0,
                            goalDifference = 0,
                            points = 0
                        )
                    }
                    
                    // Process completed matches
                    matches.filter { it.isCompleted }.forEach { match ->
                        val team1Entry = tableEntries[match.songId1]!!
                        val team2Entry = tableEntries[match.songId2]!!
                        
                        val score1 = match.score1 ?: 0
                        val score2 = match.score2 ?: 0
                        
                        // Update team 1
                        tableEntries[match.songId1] = team1Entry.copy(
                            played = team1Entry.played + 1,
                            won = team1Entry.won + if (match.winnerId == match.songId1) 1 else 0,
                            drawn = team1Entry.drawn + if (match.winnerId == null) 1 else 0,
                            lost = team1Entry.lost + if (match.winnerId == match.songId2) 1 else 0,
                            goalsFor = team1Entry.goalsFor + score1,
                            goalsAgainst = team1Entry.goalsAgainst + score2,
                            points = team1Entry.points + when (match.winnerId) {
                                match.songId1 -> settings?.winPoints ?: 3
                                null -> settings?.drawPoints ?: 1
                                else -> 0
                            }
                        )
                        
                        // Update team 2
                        tableEntries[match.songId2] = team2Entry.copy(
                            played = team2Entry.played + 1,
                            won = team2Entry.won + if (match.winnerId == match.songId2) 1 else 0,
                            drawn = team2Entry.drawn + if (match.winnerId == null) 1 else 0,
                            lost = team2Entry.lost + if (match.winnerId == match.songId1) 1 else 0,
                            goalsFor = team2Entry.goalsFor + score2,
                            goalsAgainst = team2Entry.goalsAgainst + score1,
                            points = team2Entry.points + when (match.winnerId) {
                                match.songId2 -> settings?.winPoints ?: 3
                                null -> settings?.drawPoints ?: 1
                                else -> 0
                            }
                        )
                    }
                    
                    // Sort by points, then goal difference, then goals for
                    val sortedTable = tableEntries.values
                        .map { entry -> entry.copy(goalDifference = entry.goalsFor - entry.goalsAgainst) }
                        .sortedWith(
                            compareByDescending<LeagueTableEntry> { it.points }
                                .thenByDescending { it.goalDifference }
                                .thenByDescending { it.goalsFor }
                        )
                    
                    _leagueTable.value = sortedTable
                }
            } catch (e: Exception) {
                // Handle error
                _leagueTable.value = emptyList()
            }
        }
    }
    
    fun loadMatchSummary(listId: Long) {
        viewModelScope.launch {
            try {
                val matches = repository.getMatchesByListAndMethodSync(listId, "LEAGUE")
                val songs = repository.getSongsByListId(listId)
                
                songs.collect { songList ->
                    val songsMap = songList.associateBy { it.id }
                    
                    val summaryItems = matches
                        .filter { it.isCompleted }
                        .map { match ->
                            MatchSummaryItem(
                                team1Id = match.songId1,
                                team1Name = songsMap[match.songId1]?.name ?: "Bilinmeyen",
                                team2Id = match.songId2,
                                team2Name = songsMap[match.songId2]?.name ?: "Bilinmeyen",
                                score1 = match.score1,
                                score2 = match.score2,
                                winnerId = match.winnerId
                            )
                        }
                    
                    _matchSummary.value = summaryItems
                }
            } catch (e: Exception) {
                // Handle error
                _matchSummary.value = emptyList()
            }
        }
    }
    
    fun archiveResults(listId: Long, method: String, archiveName: String) {
        viewModelScope.launch {
            try {
                // Get all data needed for archiving
                val songList = repository.getSongListById(listId)
                val matches = repository.getMatchesByListAndMethodSync(listId, method)
                val settings = if (method == "LEAGUE") {
                    repository.getLeagueSettings(listId, method)
                } else null
                
                val songs = mutableListOf<Song>()
                repository.getSongsByListId(listId).collect { songsList ->
                    songs.clear()
                    songs.addAll(songsList)
                }
                
                val songsMap = songs.associateBy { it.id }
                
                // Create archivable results from current results
                val archivableResults = _results.value.mapIndexed { index, (result, song) ->
                    ArchivableResult(
                        songId = song.id,
                        songName = song.name,
                        artist = song.artist,
                        album = song.album,
                        score = result.score,
                        position = index + 1
                    )
                }
                
                // Create archivable matches
                val archivableMatches = matches.map { match ->
                    ArchivableMatch(
                        team1Id = match.songId1,
                        team1Name = songsMap[match.songId1]?.name ?: "Unknown",
                        team2Id = match.songId2,
                        team2Name = songsMap[match.songId2]?.name ?: "Unknown",
                        score1 = match.score1,
                        score2 = match.score2,
                        winnerId = match.winnerId,
                        isCompleted = match.isCompleted,
                        round = match.round
                    )
                }
                
                // Create archivable league settings
                val archivableSettings = settings?.let { 
                    ArchivableLeagueSettings(
                        useScores = it.useScores,
                        winPoints = it.winPoints,
                        drawPoints = it.drawPoints,
                        allowDraws = it.allowDraws,
                        doubleRoundRobin = it.doubleRoundRobin
                    )
                }
                
                // Create archivable league table for league method
                val archivableLeagueTable = if (method == "LEAGUE") {
                    _leagueTable.value.ifEmpty { 
                        // If not loaded yet, load it now
                        loadLeagueTable(listId)
                        _leagueTable.value 
                    }
                } else null
                
                // Create archive entry
                val archive = Archive(
                    name = archiveName,
                    listId = listId,
                    listName = songList?.name ?: "Unknown List",
                    method = method,
                    totalSongs = songs.size,
                    totalMatches = matches.size,
                    completedMatches = matches.count { it.isCompleted },
                    finalResults = Gson().toJson(archivableResults),
                    leagueTable = archivableLeagueTable?.let { Gson().toJson(it) },
                    matchResults = Gson().toJson(archivableMatches),
                    leagueSettings = archivableSettings?.let { Gson().toJson(it) },
                    isCompleted = matches.all { it.isCompleted }
                )
                
                repository.saveArchive(archive)
                
            } catch (e: Exception) {
                // Handle archiving error - could emit error state
            }
        }
    }
}