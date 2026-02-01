package com.example.ranking.youtube

import android.content.Context
import android.util.Log
import com.example.ranking.data.YouTubeTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class YouTubeService(private val context: Context) {
    
    companion object {
        private const val TAG = "YouTubeService"
        private const val API_KEY = "YOUR_YOUTUBE_API_KEY_HERE" // TODO: Move to BuildConfig
        private const val BASE_URL = "https://www.googleapis.com/youtube/v3"
        private const val MAX_RESULTS = 50
    }
    
    private val client = OkHttpClient()
    
    /**
     * Search for an artist's top tracks by view count
     */
    suspend fun searchArtistTopTracks(
        artistName: String, 
        maxResults: Int = 25
    ): Result<List<YouTubeTrack>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching tracks for artist: $artistName")
            
            // Build search query
            val query = buildSearchUrl(artistName, maxResults)
            val request = Request.Builder()
                .url(query)
                .build()
            
            // Execute request
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "API request failed: ${response.code}")
                return@withContext Result.failure(Exception("API request failed: ${response.code}"))
            }
            
            // Parse response
            val responseBody = response.body?.string()
            if (responseBody == null) {
                Log.e(TAG, "Empty response body")
                return@withContext Result.failure(Exception("Empty response"))
            }
            
            val tracks = parseSearchResponse(responseBody, artistName)
            Log.d(TAG, "Found ${tracks.size} tracks for $artistName")
            
            Result.success(tracks)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching artist tracks", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get detailed video statistics for a list of video IDs
     */
    suspend fun getVideoStatistics(videoIds: List<String>): Result<Map<String, VideoStats>> = withContext(Dispatchers.IO) {
        try {
            if (videoIds.isEmpty()) {
                return@withContext Result.success(emptyMap())
            }
            
            val videoIdsParam = videoIds.joinToString(",")
            val url = "$BASE_URL/videos" +
                    "?part=statistics,snippet,contentDetails" +
                    "&id=$videoIdsParam" +
                    "&key=$API_KEY"
            
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Video stats request failed: ${response.code}"))
            }
            
            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty video stats response"))
            
            val stats = parseVideoStatsResponse(responseBody)
            Result.success(stats)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting video statistics", e)
            Result.failure(e)
        }
    }
    
    /**
     * Build search URL for artist videos
     */
    private fun buildSearchUrl(artistName: String, maxResults: Int): String {
        return "$BASE_URL/search" +
                "?part=snippet" +
                "&type=video" +
                "&q=${artistName.replace(" ", "%20")}%20official%20music%20video" +
                "&order=viewCount" +
                "&maxResults=$maxResults" +
                "&key=$API_KEY"
    }
    
    /**
     * Parse search response JSON
     */
    private fun parseSearchResponse(responseBody: String, artistName: String): List<YouTubeTrack> {
        try {
            val json = JSONObject(responseBody)
            val items = json.getJSONArray("items")
            val tracks = mutableListOf<YouTubeTrack>()
            
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val snippet = item.getJSONObject("snippet")
                val videoId = item.getJSONObject("id").getString("videoId")
                
                // Filter for music videos and artist relevance
                val title = snippet.getString("title")
                val channelTitle = snippet.getString("channelTitle")
                
                // Basic filtering - could be improved
                if (isRelevantMusicVideo(title, channelTitle, artistName)) {
                    val track = YouTubeTrack(
                        videoId = videoId,
                        title = title,
                        artist = artistName, // Use searched artist name
                        viewCount = 0, // Will be updated with statistics call
                        thumbnailUrl = snippet.getJSONObject("thumbnails")
                            .getJSONObject("medium").getString("url"),
                        publishedAt = snippet.getString("publishedAt")
                    )
                    tracks.add(track)
                }
            }
            
            return tracks
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing search response", e)
            return emptyList()
        }
    }
    
    /**
     * Parse video statistics response
     */
    private fun parseVideoStatsResponse(responseBody: String): Map<String, VideoStats> {
        try {
            val json = JSONObject(responseBody)
            val items = json.getJSONArray("items")
            val statsMap = mutableMapOf<String, VideoStats>()
            
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val videoId = item.getString("id")
                val statistics = item.getJSONObject("statistics")
                val snippet = item.getJSONObject("snippet")
                val contentDetails = item.getJSONObject("contentDetails")
                
                val viewCount = statistics.optString("viewCount", "0").toLongOrNull() ?: 0L
                val duration = parseDuration(contentDetails.optString("duration", ""))
                
                statsMap[videoId] = VideoStats(
                    viewCount = viewCount,
                    durationSeconds = duration,
                    title = snippet.optString("title", ""),
                    channelTitle = snippet.optString("channelTitle", "")
                )
            }
            
            return statsMap
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing video stats response", e)
            return emptyMap()
        }
    }
    
    /**
     * Check if video is relevant music content
     */
    private fun isRelevantMusicVideo(title: String, channelTitle: String, artistName: String): Boolean {
        val titleLower = title.lowercase()
        val channelLower = channelTitle.lowercase()
        val artistLower = artistName.lowercase()
        
        // Basic relevance check
        return titleLower.contains(artistLower) || 
               channelLower.contains(artistLower) ||
               titleLower.contains("music") ||
               titleLower.contains("official")
    }
    
    /**
     * Parse YouTube duration format (PT4M13S -> 253 seconds)
     */
    private fun parseDuration(duration: String): Int? {
        try {
            if (duration.isEmpty() || !duration.startsWith("PT")) {
                return null
            }
            
            var totalSeconds = 0
            var current = ""
            
            for (char in duration.substring(2)) {
                if (char.isDigit()) {
                    current += char
                } else {
                    val value = current.toIntOrNull() ?: 0
                    when (char) {
                        'H' -> totalSeconds += value * 3600
                        'M' -> totalSeconds += value * 60
                        'S' -> totalSeconds += value
                    }
                    current = ""
                }
            }
            
            return totalSeconds
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing duration: $duration", e)
            return null
        }
    }
    
    data class VideoStats(
        val viewCount: Long,
        val durationSeconds: Int?,
        val title: String,
        val channelTitle: String
    )
}