package com.example.ranking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ranking.navigation.RankingNavigation
import com.example.ranking.ui.theme.RankingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("MainActivity", "🚀 MAIN ACTIVITY STARTED! VERSION 2.0 - FULL MENU RESTORED")
        enableEdgeToEdge()
        setContent {
            android.util.Log.d("MainActivity", "📱 SET CONTENT CALLED - COMPOSE STARTING")
            RankingTheme {
                android.util.Log.d("MainActivity", "🎨 RANKING THEME OK - TRYING NAVIGATION")
                RankingNavigation()
                android.util.Log.d("MainActivity", "✅ RANKING NAVIGATION CALLED")
            }
        }
    }
}
//11