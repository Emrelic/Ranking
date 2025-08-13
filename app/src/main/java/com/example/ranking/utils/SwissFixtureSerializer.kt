package com.example.ranking.utils

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import org.json.JSONObject
import org.json.JSONArray

data class SwissFixtureData(
    val allMatches: List<Match>, // All matches across all rounds
    val currentRoundMatches: List<Match>, // Current round matches only
    val completedMatches: List<Match>, // Completed matches
    val upcomingMatches: List<Match>, // Not yet played
    val roundsData: Map<Int, RoundData> // Round-by-round breakdown
)

data class RoundData(
    val roundNumber: Int,
    val matches: List<Match>,
    val isComplete: Boolean,
    val standingsAfterRound: Map<Long, Double>
)

data class LiveStandings(
    val currentStandings: Map<Long, Double>, // songId -> points
    val rankings: List<RankingEntry>, // Sorted by points
    val roundByRoundProgress: Map<Int, Map<Long, Double>> // round -> (songId -> points that round)
)

data class RankingEntry(
    val songId: Long,
    val songName: String,
    val points: Double,
    val position: Int,
    val matchesPlayed: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int
)

object SwissFixtureSerializer {
    
    fun serializeFixtureData(fixtureData: SwissFixtureData): String {
        val json = JSONObject()
        
        // Serialize all matches
        val allMatchesArray = JSONArray()
        fixtureData.allMatches.forEach { match ->
            allMatchesArray.put(serializeMatch(match))
        }
        json.put("allMatches", allMatchesArray)
        
        // Serialize current round matches
        val currentRoundArray = JSONArray()
        fixtureData.currentRoundMatches.forEach { match ->
            currentRoundArray.put(serializeMatch(match))
        }
        json.put("currentRoundMatches", currentRoundArray)
        
        // Serialize rounds data
        val roundsDataJson = JSONObject()
        fixtureData.roundsData.forEach { (round, roundData) ->
            roundsDataJson.put(round.toString(), serializeRoundData(roundData))
        }
        json.put("roundsData", roundsDataJson)
        
        return json.toString()
    }
    
    private fun serializeMatch(match: Match): JSONObject {
        val matchJson = JSONObject()
        matchJson.put("id", match.id)
        matchJson.put("listId", match.listId)
        matchJson.put("rankingMethod", match.rankingMethod)
        matchJson.put("songId1", match.songId1)
        matchJson.put("songId2", match.songId2)
        matchJson.put("winnerId", match.winnerId ?: JSONObject.NULL)
        matchJson.put("score1", match.score1 ?: JSONObject.NULL)
        matchJson.put("score2", match.score2 ?: JSONObject.NULL)
        matchJson.put("round", match.round)
        matchJson.put("groupId", match.groupId ?: JSONObject.NULL)
        matchJson.put("isCompleted", match.isCompleted)
        matchJson.put("createdAt", match.createdAt)
        return matchJson
    }
    
    private fun serializeRoundData(roundData: RoundData): JSONObject {
        val roundJson = JSONObject()
        roundJson.put("roundNumber", roundData.roundNumber)
        roundJson.put("isComplete", roundData.isComplete)
        
        val matchesArray = JSONArray()
        roundData.matches.forEach { match ->
            matchesArray.put(serializeMatch(match))
        }
        roundJson.put("matches", matchesArray)
        
        val standingsJson = JSONObject()
        roundData.standingsAfterRound.forEach { (songId, points) ->
            standingsJson.put(songId.toString(), points)
        }
        roundJson.put("standingsAfterRound", standingsJson)
        
        return roundJson
    }
    
    fun serializeLiveStandings(standings: LiveStandings): String {
        val json = JSONObject()
        
        // Current standings
        val currentStandingsJson = JSONObject()
        standings.currentStandings.forEach { (songId, points) ->
            currentStandingsJson.put(songId.toString(), points)
        }
        json.put("currentStandings", currentStandingsJson)
        
        // Rankings
        val rankingsArray = JSONArray()
        standings.rankings.forEach { entry ->
            val entryJson = JSONObject()
            entryJson.put("songId", entry.songId)
            entryJson.put("songName", entry.songName)
            entryJson.put("points", entry.points)
            entryJson.put("position", entry.position)
            entryJson.put("matchesPlayed", entry.matchesPlayed)
            entryJson.put("wins", entry.wins)
            entryJson.put("draws", entry.draws)
            entryJson.put("losses", entry.losses)
            rankingsArray.put(entryJson)
        }
        json.put("rankings", rankingsArray)
        
        // Round by round progress
        val progressJson = JSONObject()
        standings.roundByRoundProgress.forEach { (round, roundPoints) ->
            val roundJson = JSONObject()
            roundPoints.forEach { (songId, points) ->
                roundJson.put(songId.toString(), points)
            }
            progressJson.put(round.toString(), roundJson)
        }
        json.put("roundByRoundProgress", progressJson)
        
        return json.toString()
    }
    
    fun deserializeFixtureData(fixtureJson: String): SwissFixtureData {
        val json = JSONObject(fixtureJson)
        
        // Deserialize all matches
        val allMatches = mutableListOf<Match>()
        val allMatchesArray = json.getJSONArray("allMatches")
        for (i in 0 until allMatchesArray.length()) {
            allMatches.add(deserializeMatch(allMatchesArray.getJSONObject(i)))
        }
        
        // Deserialize current round matches
        val currentRoundMatches = mutableListOf<Match>()
        val currentRoundArray = json.getJSONArray("currentRoundMatches")
        for (i in 0 until currentRoundArray.length()) {
            currentRoundMatches.add(deserializeMatch(currentRoundArray.getJSONObject(i)))
        }
        
        // Deserialize rounds data
        val roundsData = mutableMapOf<Int, RoundData>()
        val roundsDataJson = json.getJSONObject("roundsData")
        val keys = roundsDataJson.keys()
        while (keys.hasNext()) {
            val roundKey = keys.next()
            val round = roundKey.toInt()
            roundsData[round] = deserializeRoundData(roundsDataJson.getJSONObject(roundKey))
        }
        
        return SwissFixtureData(
            allMatches = allMatches,
            currentRoundMatches = currentRoundMatches,
            completedMatches = allMatches.filter { it.isCompleted },
            upcomingMatches = allMatches.filter { !it.isCompleted },
            roundsData = roundsData
        )
    }
    
    private fun deserializeMatch(matchJson: JSONObject): Match {
        return Match(
            id = matchJson.getLong("id"),
            listId = matchJson.getLong("listId"),
            rankingMethod = matchJson.getString("rankingMethod"),
            songId1 = matchJson.getLong("songId1"),
            songId2 = matchJson.getLong("songId2"),
            winnerId = if (matchJson.isNull("winnerId")) null else matchJson.getLong("winnerId"),
            score1 = if (matchJson.isNull("score1")) null else matchJson.getInt("score1"),
            score2 = if (matchJson.isNull("score2")) null else matchJson.getInt("score2"),
            round = matchJson.getInt("round"),
            groupId = if (matchJson.isNull("groupId")) null else matchJson.getInt("groupId"),
            isCompleted = matchJson.getBoolean("isCompleted"),
            createdAt = matchJson.getLong("createdAt")
        )
    }
    
    private fun deserializeRoundData(roundJson: JSONObject): RoundData {
        val matches = mutableListOf<Match>()
        val matchesArray = roundJson.getJSONArray("matches")
        for (i in 0 until matchesArray.length()) {
            matches.add(deserializeMatch(matchesArray.getJSONObject(i)))
        }
        
        val standingsAfterRound = mutableMapOf<Long, Double>()
        val standingsJson = roundJson.getJSONObject("standingsAfterRound")
        val keys = standingsJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            standingsAfterRound[key.toLong()] = standingsJson.getDouble(key)
        }
        
        return RoundData(
            roundNumber = roundJson.getInt("roundNumber"),
            matches = matches,
            isComplete = roundJson.getBoolean("isComplete"),
            standingsAfterRound = standingsAfterRound
        )
    }
    
    fun deserializeLiveStandings(standingsJson: String): LiveStandings {
        val json = JSONObject(standingsJson)
        
        // Current standings
        val currentStandings = mutableMapOf<Long, Double>()
        val currentStandingsJson = json.getJSONObject("currentStandings")
        val standingsKeys = currentStandingsJson.keys()
        while (standingsKeys.hasNext()) {
            val key = standingsKeys.next()
            currentStandings[key.toLong()] = currentStandingsJson.getDouble(key)
        }
        
        // Rankings
        val rankings = mutableListOf<RankingEntry>()
        val rankingsArray = json.getJSONArray("rankings")
        for (i in 0 until rankingsArray.length()) {
            val entryJson = rankingsArray.getJSONObject(i)
            rankings.add(
                RankingEntry(
                    songId = entryJson.getLong("songId"),
                    songName = entryJson.getString("songName"),
                    points = entryJson.getDouble("points"),
                    position = entryJson.getInt("position"),
                    matchesPlayed = entryJson.getInt("matchesPlayed"),
                    wins = entryJson.getInt("wins"),
                    draws = entryJson.getInt("draws"),
                    losses = entryJson.getInt("losses")
                )
            )
        }
        
        // Round by round progress
        val roundByRoundProgress = mutableMapOf<Int, Map<Long, Double>>()
        val progressJson = json.getJSONObject("roundByRoundProgress")
        val progressKeys = progressJson.keys()
        while (progressKeys.hasNext()) {
            val roundKey = progressKeys.next()
            val round = roundKey.toInt()
            val roundProgress = mutableMapOf<Long, Double>()
            val roundJson = progressJson.getJSONObject(roundKey)
            val roundKeys = roundJson.keys()
            while (roundKeys.hasNext()) {
                val songKey = roundKeys.next()
                roundProgress[songKey.toLong()] = roundJson.getDouble(songKey)
            }
            roundByRoundProgress[round] = roundProgress
        }
        
        return LiveStandings(
            currentStandings = currentStandings,
            rankings = rankings,
            roundByRoundProgress = roundByRoundProgress
        )
    }
}