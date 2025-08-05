package com.example.ranking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ranking.EmreUsuluTestRunner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestScreen() {
    var testResult by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Emre Usulü Test",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Button(
            onClick = {
                isLoading = true
                testResult = ""
                
                // Test çalıştır
                try {
                    val testRunner = EmreUsuluTestRunner()
                    val filePath = """C:\Users\ikizler1\OneDrive\Desktop\şebnem randomize 10000 bnlik.csv"""
                    testResult = testRunner.runTest(filePath)
                } catch (e: Exception) {
                    testResult = "HATA: ${e.message}\n${e.stackTrace.joinToString("\n")}"
                } finally {
                    isLoading = false
                }
            },
            enabled = !isLoading,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Test Başlat")
        }
        
        if (testResult.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = testResult,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }
    }
}