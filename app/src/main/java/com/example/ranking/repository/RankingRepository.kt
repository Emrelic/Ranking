package com.example.ranking.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.ranking.data.*
import com.example.ranking.data.dao.*
import com.example.ranking.utils.CsvReader
import kotlinx.coroutines.flow.Flow

class RankingRepository(
    private val songDao: SongDao,
    private val songListDao: SongListDao,
    private val rankingResultDao: RankingResultDao,
    private val matchDao: MatchDao,
    private val leagueSettingsDao: LeagueSettingsDao,
    private val archiveDao: ArchiveDao,
    private val csvReader: CsvReader,
    private val swissStateDao: SwissStateDao? = null,
    private val swissMatchStateDao: SwissMatchStateDao? = null
) {
    
    // Song List operations
    fun getAllSongLists(): Flow<List<SongList>> = songListDao.getAllSongLists()
    
    suspend fun createSongList(name: String): Long {
        val songList = SongList(name = name)
        return songListDao.insertSongList(songList)
    }
    
    suspend fun getSongListById(id: Long): SongList? = songListDao.getSongListById(id)
    
    suspend fun deleteSongList(songList: SongList) {
        songDao.deleteSongsByListId(songList.id)
        rankingResultDao.deleteAllRankingResults(songList.id)
        songListDao.deleteSongList(songList)
    }
    
    // Song operations
    fun getSongsByListId(listId: Long): Flow<List<Song>> = songDao.getSongsByListId(listId)
    
    suspend fun getSongsByListIdSync(listId: Long): List<Song> = songDao.getSongsByListIdSync(listId)
    
    suspend fun addSong(listId: Long, name: String, artist: String = "", album: String = "", trackNumber: Int = 0): Long {
        val song = Song(name = name, artist = artist, album = album, trackNumber = trackNumber, listId = listId)
        val songId = songDao.insertSong(song)
        updateSongCount(listId)
        return songId
    }
    
    suspend fun importSongsFromCsv(context: Context, listId: Long, uri: Uri) {
        try {
            Log.d("RankingRepository", "CSV okuma başlıyor: $uri")
            val csvSongs = csvReader.readCsvFromUri(context, uri)
            Log.d("RankingRepository", "CSV'den ${csvSongs.size} öğe okundu")
            
            if (csvSongs.isEmpty()) {
                throw Exception("CSV dosyasında öğe bulunamadı")
            }
            
            val songs = csvSongs.map { csvSong ->
                Song(
                    name = csvSong.name, 
                    artist = csvSong.artist, 
                    album = csvSong.album,
                    trackNumber = csvSong.trackNumber,
                    listId = listId
                )
            }
            
            Log.d("RankingRepository", "Öğeler veritabanına kaydediliyor...")
            songDao.insertSongs(songs)
            updateSongCount(listId)
            Log.d("RankingRepository", "CSV import işlemi tamamlandı")
            
        } catch (e: Exception) {
            Log.e("RankingRepository", "CSV import hatası: ${e.message}", e)
            throw e
        }
    }
    
    private suspend fun updateSongCount(listId: Long) {
        val songList = songListDao.getSongListById(listId)
        val count = songDao.getSongCountByListId(listId)
        songList?.let {
            songListDao.updateSongList(it.copy(songCount = count))
        }
    }
    
    // Ranking operations
    fun getRankingResults(listId: Long, method: String): Flow<List<RankingResult>> =
        rankingResultDao.getRankingResults(listId, method)
    
    suspend fun getRankingResultsSync(listId: Long, method: String): List<RankingResult> =
        rankingResultDao.getRankingResultsSync(listId, method)
    
    suspend fun saveRankingResults(results: List<RankingResult>) {
        rankingResultDao.insertRankingResults(results)
    }
    
    suspend fun clearRankingResults(listId: Long, method: String) {
        rankingResultDao.deleteRankingResults(listId, method)
    }
    
    // Match operations
    fun getMatchesByListAndMethod(listId: Long, method: String): Flow<List<Match>> =
        matchDao.getMatchesByListAndMethod(listId, method)
    
    suspend fun getMatchesByListAndMethodSync(listId: Long, method: String): List<Match> =
        matchDao.getMatchesByListAndMethodSync(listId, method)
    
    suspend fun getNextUncompletedMatch(listId: Long, method: String): Match? =
        matchDao.getNextUncompletedMatch(listId, method)
    
    suspend fun updateMatch(match: Match) {
        matchDao.updateMatch(match)
    }
    
    suspend fun createMatches(matches: List<Match>) {
        matchDao.insertMatches(matches)
    }
    
    suspend fun clearMatches(listId: Long, method: String) {
        matchDao.deleteMatches(listId, method)
    }
    
    suspend fun getMatchProgress(listId: Long, method: String): Pair<Int, Int> {
        val completed = matchDao.getCompletedMatchCount(listId, method)
        val total = matchDao.getTotalMatchCount(listId, method)
        return Pair(completed, total)
    }
    
    // League Settings operations
    suspend fun getLeagueSettings(listId: Long, method: String): LeagueSettings? =
        leagueSettingsDao.getByListAndMethod(listId, method)
    
    suspend fun saveLeagueSettings(settings: LeagueSettings) {
        val existing = leagueSettingsDao.getByListAndMethod(settings.listId, settings.rankingMethod)
        if (existing != null) {
            leagueSettingsDao.update(settings.copy(id = existing.id))
        } else {
            leagueSettingsDao.insert(settings)
        }
    }
    
    // Archive operations
    fun getAllArchives(): Flow<List<Archive>> = archiveDao.getAllArchives()
    
    suspend fun saveArchive(archive: Archive): Long = archiveDao.insert(archive)
    
    suspend fun getArchiveById(id: Long): Archive? = archiveDao.getArchiveById(id)
    
    suspend fun deleteArchive(archive: Archive) = archiveDao.delete(archive)
    
    fun getArchivesByMethod(method: String): Flow<List<Archive>> = 
        archiveDao.getArchivesByMethod(method)
    
    // Swiss State operations
    suspend fun saveSwissState(
        sessionId: Long,
        currentRound: Int,
        maxRounds: Int,
        standings: Map<Long, Double>,
        pairingHistory: Set<Pair<Long, Long>>,
        roundHistory: List<com.example.ranking.data.RoundResult>
    ): Long? {
        return swissStateDao?.let { dao ->
            val standingsJson = com.example.ranking.utils.SwissStateSerializer.serializeStandings(standings)
            val pairingHistoryJson = com.example.ranking.utils.SwissStateSerializer.serializePairingHistory(pairingHistory)
            val roundHistoryJson = com.example.ranking.utils.SwissStateSerializer.serializeRoundHistory(roundHistory)
            
            val swissState = SwissState(
                sessionId = sessionId,
                currentRound = currentRound,
                maxRounds = maxRounds,
                standings = standingsJson,
                pairingHistory = pairingHistoryJson,
                roundHistory = roundHistoryJson
            )
            dao.insertOrUpdateSwissState(swissState)
        }
    }
    
    suspend fun loadSwissState(sessionId: Long): com.example.ranking.data.SwissStandings? {
        return swissStateDao?.getSwissStateBySession(sessionId)?.let { swissState ->
            val standings = com.example.ranking.utils.SwissStateSerializer.deserializeStandings(swissState.standings)
            val pairingHistory = com.example.ranking.utils.SwissStateSerializer.deserializePairingHistory(swissState.pairingHistory)
            val roundHistory = com.example.ranking.utils.SwissStateSerializer.deserializeRoundHistory(swissState.roundHistory)
            
            com.example.ranking.data.SwissStandings(
                standings = standings,
                pairingHistory = pairingHistory,
                roundHistory = roundHistory
            )
        }
    }
    
    suspend fun deleteSwissState(sessionId: Long) {
        swissStateDao?.deleteSwissStateBySession(sessionId)
    }
    
    // Advanced Swiss Match State operations - Real-time persistence
    suspend fun saveCurrentMatchState(
        sessionId: Long,
        match: Match,
        song1Name: String,
        song2Name: String,
        preliminaryWinnerId: Long? = null,
        preliminaryScore1: Int? = null,
        preliminaryScore2: Int? = null
    ) {
        swissMatchStateDao?.let { dao ->
            val matchState = SwissMatchState(
                sessionId = sessionId,
                matchId = match.id,
                currentRound = match.round,
                song1Id = match.songId1,
                song2Id = match.songId2,
                song1Name = song1Name,
                song2Name = song2Name,
                isMatchInProgress = true,
                preliminaryWinnerId = preliminaryWinnerId,
                preliminaryScore1 = preliminaryScore1,
                preliminaryScore2 = preliminaryScore2,
                lastUpdateTime = System.currentTimeMillis()
            )
            dao.insertOrUpdateMatchState(matchState)
        }
    }
    
    suspend fun updateMatchProgress(
        sessionId: Long,
        preliminaryWinnerId: Long?,
        preliminaryScore1: Int? = null,
        preliminaryScore2: Int? = null
    ) {
        swissMatchStateDao?.let { dao ->
            val currentState = dao.getCurrentMatchState(sessionId)
            currentState?.let { state ->
                val updatedState = state.copy(
                    preliminaryWinnerId = preliminaryWinnerId,
                    preliminaryScore1 = preliminaryScore1,
                    preliminaryScore2 = preliminaryScore2,
                    lastUpdateTime = System.currentTimeMillis()
                )
                dao.updateMatchState(updatedState)
            }
        }
    }
    
    suspend fun getCurrentMatchState(sessionId: Long): SwissMatchState? {
        return swissMatchStateDao?.getCurrentMatchState(sessionId)
    }
    
    suspend fun completeCurrentMatch(sessionId: Long) {
        swissMatchStateDao?.markAllMatchesComplete(sessionId)
    }
    
    suspend fun saveCompleteFixture(
        sessionId: Long,
        currentRound: Int,
        totalRounds: Int,
        allMatches: List<Match>,
        currentStandings: Map<Long, Double>
    ) {
        swissMatchStateDao?.let { dao ->
            // Create fixture data
            val fixtureData = com.example.ranking.utils.SwissFixtureData(
                allMatches = allMatches,
                currentRoundMatches = allMatches.filter { it.round == currentRound },
                completedMatches = allMatches.filter { it.isCompleted },
                upcomingMatches = allMatches.filter { !it.isCompleted },
                roundsData = createRoundsData(allMatches, currentStandings)
            )
            
            // Create live standings
            val liveStandings = createLiveStandings(allMatches, currentStandings)
            
            val fixture = SwissFixture(
                sessionId = sessionId,
                currentRound = currentRound,
                totalRounds = totalRounds,
                fixtureData = com.example.ranking.utils.SwissFixtureSerializer.serializeFixtureData(fixtureData),
                currentStandings = com.example.ranking.utils.SwissFixtureSerializer.serializeLiveStandings(liveStandings),
                nextMatchIndex = allMatches.indexOfFirst { !it.isCompleted },
                isRoundComplete = allMatches.filter { it.round == currentRound }.all { it.isCompleted },
                lastUpdated = System.currentTimeMillis()
            )
            
            dao.insertOrUpdateFixture(fixture)
        }
    }
    
    suspend fun loadCompleteFixture(sessionId: Long): SwissFixture? {
        return swissMatchStateDao?.getFixture(sessionId)
    }
    
    private fun createRoundsData(allMatches: List<Match>, currentStandings: Map<Long, Double>): Map<Int, com.example.ranking.utils.RoundData> {
        val roundsData = mutableMapOf<Int, com.example.ranking.utils.RoundData>()
        
        val matchesByRound = allMatches.groupBy { it.round }
        matchesByRound.forEach { (round, matches) ->
            val isComplete = matches.all { it.isCompleted }
            roundsData[round] = com.example.ranking.utils.RoundData(
                roundNumber = round,
                matches = matches,
                isComplete = isComplete,
                standingsAfterRound = if (isComplete) currentStandings else emptyMap()
            )
        }
        
        return roundsData
    }
    
    private fun createLiveStandings(allMatches: List<Match>, currentStandings: Map<Long, Double>): com.example.ranking.utils.LiveStandings {
        // Get all unique song IDs
        val allSongIds = mutableSetOf<Long>()
        allMatches.forEach { match ->
            allSongIds.add(match.songId1)
            allSongIds.add(match.songId2)
        }
        
        // Create ranking entries
        val rankings = allSongIds.mapIndexed { index, songId ->
            val points = currentStandings[songId] ?: 0.0
            val matchesPlayed = allMatches.count { (it.songId1 == songId || it.songId2 == songId) && it.isCompleted }
            
            // Calculate win/draw/loss stats
            var wins = 0
            var draws = 0
            var losses = 0
            
            allMatches.filter { it.isCompleted && (it.songId1 == songId || it.songId2 == songId) }.forEach { match ->
                when (match.winnerId) {
                    songId -> wins++
                    null -> draws++
                    else -> losses++
                }
            }
            
            com.example.ranking.utils.RankingEntry(
                songId = songId,
                songName = "Song $songId", // Will be populated by caller
                points = points,
                position = index + 1, // Will be recalculated
                matchesPlayed = matchesPlayed,
                wins = wins,
                draws = draws,
                losses = losses
            )
        }.sortedByDescending { it.points }.mapIndexed { index, entry ->
            entry.copy(position = index + 1)
        }
        
        // Round by round progress
        val roundByRoundProgress = mutableMapOf<Int, Map<Long, Double>>()
        val matchesByRound = allMatches.groupBy { it.round }
        matchesByRound.forEach { (round, matches) ->
            val roundPoints = mutableMapOf<Long, Double>()
            allSongIds.forEach { songId -> roundPoints[songId] = 0.0 }
            
            matches.filter { it.isCompleted }.forEach { match ->
                when (match.winnerId) {
                    match.songId1 -> roundPoints[match.songId1] = roundPoints[match.songId1]!! + 1.0
                    match.songId2 -> roundPoints[match.songId2] = roundPoints[match.songId2]!! + 1.0
                    null -> {
                        roundPoints[match.songId1] = roundPoints[match.songId1]!! + 0.5
                        roundPoints[match.songId2] = roundPoints[match.songId2]!! + 0.5
                    }
                }
            }
            roundByRoundProgress[round] = roundPoints
        }
        
        return com.example.ranking.utils.LiveStandings(
            currentStandings = currentStandings,
            rankings = rankings,
            roundByRoundProgress = roundByRoundProgress
        )
    }
    
    suspend fun deleteAllSwissMatchStates(sessionId: Long) {
        swissMatchStateDao?.deleteAllMatchStates(sessionId)
        swissMatchStateDao?.deleteFixture(sessionId)
    }
}