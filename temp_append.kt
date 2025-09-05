
    
    // Criteria Dialog Functions
    fun openCriteriaDialog() {
        _uiState.value = _uiState.value.copy(showCriteriaDialog = true)
    }
    
    fun closeCriteriaDialog() {
        _uiState.value = _uiState.value.copy(showCriteriaDialog = false)
    }
    
    fun saveCriteriaScores(matchId: Long, scores: Map<String, Pair<Double?, Double?>>) {
        viewModelScope.launch {
            try {
                val tournamentId = currentTournament?.id ?: return@launch
                
                scores.forEach { (criterionName, scorePair) ->
                    val criterionScore = CriterionScore(
                        matchId = matchId,
                        tournamentId = tournamentId,
                        criterionName = criterionName,
                        team1Score = scorePair.first,
                        team2Score = scorePair.second
                    )
                    
                    database.criterionScoreDao().insertCriterionScore(criterionScore)
                }
                
                // Update UI state with saved scores
                _uiState.value = _uiState.value.copy(
                    currentMatchCriteriaScores = scores
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Kriter skorları kaydedilemedi: ${e.message}"
                )
            }
        }
    }
    
    fun recordMatchResultFromCriteria(matchId: Long, result: Int) {
        viewModelScope.launch {
            val winnerId = when (result) {
                1 -> _uiState.value.currentMatch?.songId1
                2 -> _uiState.value.currentMatch?.songId2
                else -> null // draw
            }
            submitMatchResult(matchId, winnerId)
        }
    }
}
