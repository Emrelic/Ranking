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
    private val csvReader: CsvReader
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
}