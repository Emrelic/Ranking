package com.example.ranking.ui.screens.ranking

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.example.ranking.R

/**
 * Sıralama yöntemi kodunu kullanıcıya gösterilen başlığa çevirir.
 * Yeni Turnuva ekranındaki sistem adlarıyla (new_tournament_system_*_title)
 * tutarlı tek kaynak; RankingScreen ve ResultsScreen buradan beslenir.
 */
@Composable
@ReadOnlyComposable
internal fun methodTitle(method: String): String {
    return when (method) {
        "DIRECT_SCORING" -> stringResource(R.string.new_tournament_system_direct_scoring_title)
        "MERGE_SORT" -> stringResource(R.string.new_tournament_system_merge_sort_title)
        "LEAGUE" -> stringResource(R.string.new_tournament_system_league_title)
        "SWISS" -> stringResource(R.string.new_tournament_system_swiss_title)
        "EMRE_CORRECT" -> stringResource(R.string.new_tournament_system_emre_title)
        "ELIMINATION" -> stringResource(R.string.new_tournament_system_elimination_title)
        "FULL_ELIMINATION" -> stringResource(R.string.new_tournament_system_full_elimination_title)
        else -> stringResource(R.string.method_title_fallback)
    }
}
