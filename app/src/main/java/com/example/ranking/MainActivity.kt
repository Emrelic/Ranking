package com.example.ranking

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.content.pm.ActivityInfo
import android.os.Build
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ranking.navigation.RankingNavigation
import com.example.ranking.ui.theme.RankingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ULTRA TAM EKRAN MODU: Navigation bar'ı tamamen gizle
        hideSystemUI()

        setContent {
            RankingTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    RankingNavigation()
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }
    
    override fun onPause() {
        super.onPause()
        hideSystemUI()
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        hideSystemUI()
    }
    
    private fun hideSystemUI() {
        // ULTRA AGRESIF TAM EKRAN MODU - Navigation bar (GERİ/HOME/RECENT) ve Status bar tamamen gizle
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ için modern approach - NAVİGASYON BUTONLARI TAMAMEN KALDIR
            window.setDecorFitsSystemWindows(false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            // SWIPE GESTÜRÜNÜ DE DEVRE DIŞI BIRAK - SADECE YAN KENARLARDAN ERİŞİM
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            
            // Android 11+ tam limitless mode
            window.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
        } else {
            // Android 10 ve öncesi için eski method - NAVİGASYON BUTONLARI GİZLE
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION  // 3 BUTON GİZLE: GERİ/HOME/RECENT
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY // KALICI GİZLEME
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LOW_PROFILE
            )
        }
        
        // SÜPER AGRESIF NAVİGASYON BAR GİZLEME - GERİ/HOME/RECENT BUTONLARI KALDIR
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or 
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or 
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // NAVİGASYON BUTONLARINI ŞEFFAF YAP (gizlenemiyorsa)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.navigationBarColor = Color.TRANSPARENT
            window.statusBarColor = Color.TRANSPARENT
        }
        
        // WindowCompat full immersive - NAVİGASYON BUTONLARI DEVRE DIŞI
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // SON ÇARE - NAVİGASYON BUTONLARINI ZORLA GİZLE (GERİ/HOME/RECENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or 
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or  // YAPIŞKAN İMMERSİVE MOD
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or   // NAVİGASYON BUTONLARI GİZLE
                View.SYSTEM_UI_FLAG_FULLSCREEN          // TAM EKRAN MOD
        }
        
        // EDGE-TO-EDGE DİSPLAY - TÜM EKRAN ALANINI KULLAN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}
//11