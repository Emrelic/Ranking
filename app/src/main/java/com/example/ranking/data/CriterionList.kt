package com.example.ranking.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "criterion_lists")
data class CriterionList(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val criteria: String, // JSON array: ["Melodi","Ritim","Tempo","Vokal","Uyum"]
    val createdDate: String,
    val isActive: Boolean = true, // silme koruması için - kullanımdaki listeler silinemesin
    val createdAt: Long = System.currentTimeMillis()
)