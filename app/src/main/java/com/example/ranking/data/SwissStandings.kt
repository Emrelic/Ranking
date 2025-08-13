package com.example.ranking.data

data class SwissStandings(
    val standings: Map<Long, Double>, // songId -> points
    val pairingHistory: Set<Pair<Long, Long>>, // all pairs who have played
    val roundHistory: List<RoundResult>
)

data class RoundResult(
    val roundNumber: Int,
    val matches: List<Match>,
    val pointsThisRound: Map<Long, Double>
)

data class SwissPairing(
    val song1: Song,
    val song2: Song,
    val canPair: Boolean = true,
    val priority: Int = 0 // for tiebreakers
)