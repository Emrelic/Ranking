package com.example.ranking.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ranking.data.CriterionList
import com.example.ranking.data.RankingDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class CreateCriteriaViewModel(application: Application) : AndroidViewModel(application) {
    private val database = RankingDatabase.getDatabase(application)
    private val gson = Gson()
    
    fun createCriterionList(
        name: String,
        criteria: List<String>,
        createdDate: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val criterionList = CriterionList(
                    name = name,
                    criteria = gson.toJson(criteria),
                    createdDate = createdDate,
                    isActive = true
                )
                
                database.criterionListDao().insertCriterionList(criterionList)
                onSuccess()
            } catch (e: Exception) {
                onError("Kriter listesi oluşturulamadı: ${e.message}")
            }
        }
    }
    
    fun loadCriterionList(
        id: Long,
        onSuccess: (CriterionList) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val criterionList = database.criterionListDao().getCriterionListById(id)
                if (criterionList != null) {
                    onSuccess(criterionList)
                } else {
                    onError("Kriter listesi bulunamadı")
                }
            } catch (e: Exception) {
                onError("Kriter listesi yüklenemedi: ${e.message}")
            }
        }
    }
    
    fun updateCriterionList(
        id: Long,
        name: String,
        criteria: List<String>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val existingList = database.criterionListDao().getCriterionListById(id)
                if (existingList != null) {
                    val updatedList = existingList.copy(
                        name = name,
                        criteria = gson.toJson(criteria)
                    )
                    database.criterionListDao().updateCriterionList(updatedList)
                    onSuccess()
                } else {
                    onError("Güncellenecek kriter listesi bulunamadı")
                }
            } catch (e: Exception) {
                onError("Kriter listesi güncellenemedi: ${e.message}")
            }
        }
    }
    
    fun importFromCSV(
        context: Context,
        uri: Uri,
        onSuccess: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // Dosya okuma ana thread'de yapılmaz (ANR riski)
                val criteria = withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val result = mutableListOf<String>()

                    reader.useLines { lines ->
                        lines.forEach { line ->
                            val trimmedLine = line.trim()
                            if (trimmedLine.isNotEmpty()) {
                                // Handle both single column and comma-separated values
                                if (trimmedLine.contains(",")) {
                                    // Multiple criteria per line
                                    trimmedLine.split(",").forEach { criterion ->
                                        val cleanCriterion = criterion.trim().replace("\"", "")
                                        if (cleanCriterion.isNotEmpty()) {
                                            result.add(cleanCriterion)
                                        }
                                    }
                                } else {
                                    // Single criterion per line
                                    result.add(trimmedLine)
                                }
                            }
                        }
                    }
                    result
                }

                if (criteria.isNotEmpty()) {
                    onSuccess(criteria.distinct()) // Remove duplicates
                } else {
                    onError("CSV dosyası boş veya uygun formatta değil")
                }
            } catch (e: Exception) {
                onError("CSV okuma hatası: ${e.message}")
            }
        }
    }
    
    fun parseCriteriaFromJson(json: String): List<String> {
        return try {
            gson.fromJson(json, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}