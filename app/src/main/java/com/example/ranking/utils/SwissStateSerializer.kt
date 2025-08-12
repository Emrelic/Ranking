package com.example.ranking.utils

import com.example.ranking.data.SwissStandings
import com.example.ranking.data.RoundResult
import com.example.ranking.data.Match
import org.json.JSONObject
import org.json.JSONArray

object SwissStateSerializer {
    
    fun serializeStandings(standings: Map<Long, Double>): String {
        val json = JSONObject()
        standings.forEach { (songId, points) ->
            json.put(songId.toString(), points)
        }
        return json.toString()
    }
    
    fun deserializeStandings(standingsJson: String): Map<Long, Double> {
        val json = JSONObject(standingsJson)
        val standings = mutableMapOf<Long, Double>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            standings[key.toLong()] = json.getDouble(key)
        }
        return standings
    }
    
    fun serializePairingHistory(pairingHistory: Set<Pair<Long, Long>>): String {
        val json = JSONArray()
        pairingHistory.forEach { (song1, song2) ->
            val pairJson = JSONArray()
            pairJson.put(song1)
            pairJson.put(song2)
            json.put(pairJson)
        }
        return json.toString()
    }
    
    fun deserializePairingHistory(pairingHistoryJson: String): Set<Pair<Long, Long>> {
        val json = JSONArray(pairingHistoryJson)
        val pairingHistory = mutableSetOf<Pair<Long, Long>>()
        for (i in 0 until json.length()) {
            val pairJson = json.getJSONArray(i)
            val song1 = pairJson.getLong(0)
            val song2 = pairJson.getLong(1)
            pairingHistory.add(Pair(song1, song2))
        }
        return pairingHistory
    }
    
    fun serializeRoundHistory(roundHistory: List<RoundResult>): String {
        val json = JSONArray()
        roundHistory.forEach { round ->
            val roundJson = JSONObject()
            roundJson.put("roundNumber", round.roundNumber)
            
            val pointsJson = JSONObject()
            round.pointsThisRound.forEach { (songId, points) ->
                pointsJson.put(songId.toString(), points)
            }
            roundJson.put("pointsThisRound", pointsJson)
            
            val matchesJson = JSONArray()
            round.matches.forEach { match ->
                val matchJson = JSONObject()
                matchJson.put("id", match.id)
                matchJson.put("songId1", match.songId1)
                matchJson.put("songId2", match.songId2)
                matchJson.put("winnerId", match.winnerId ?: JSONObject.NULL)
                matchJson.put("round", match.round)
                matchesJson.put(matchJson)
            }
            roundJson.put("matches", matchesJson)
            
            json.put(roundJson)
        }
        return json.toString()
    }
    
    fun deserializeRoundHistory(roundHistoryJson: String): List<RoundResult> {
        val json = JSONArray(roundHistoryJson)
        val roundHistory = mutableListOf<RoundResult>()
        
        for (i in 0 until json.length()) {
            val roundJson = json.getJSONObject(i)
            val roundNumber = roundJson.getInt("roundNumber")
            
            val pointsJson = roundJson.getJSONObject("pointsThisRound")
            val pointsThisRound = mutableMapOf<Long, Double>()
            val pointsKeys = pointsJson.keys()
            while (pointsKeys.hasNext()) {
                val key = pointsKeys.next()
                pointsThisRound[key.toLong()] = pointsJson.getDouble(key)
            }
            
            val matchesJson = roundJson.getJSONArray("matches")
            val matches = mutableListOf<Match>()
            for (j in 0 until matchesJson.length()) {
                val matchJson = matchesJson.getJSONObject(j)
                val match = Match(
                    id = matchJson.getLong("id"),
                    listId = 0, // Will be set by the caller
                    rankingMethod = "SWISS",
                    songId1 = matchJson.getLong("songId1"),
                    songId2 = matchJson.getLong("songId2"),
                    winnerId = if (matchJson.isNull("winnerId")) null else matchJson.getLong("winnerId"),
                    round = matchJson.getInt("round"),
                    isCompleted = true
                )
                matches.add(match)
            }
            
            roundHistory.add(RoundResult(roundNumber, matches, pointsThisRound))
        }
        
        return roundHistory
    }
}