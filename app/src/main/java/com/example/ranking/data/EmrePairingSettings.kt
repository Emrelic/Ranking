package com.example.ranking.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class EmrePairingMethod {
    SEQUENTIAL,     // 1-2, 3-4, 5-6... (Sıra numarasına göre)
    RANDOM,         // Gelişigüzel
    ALPHABETICAL,   // Alfabetik sıraya göre
    SPLIT_HALF      // 1-19, 2-20, 3-21... (Yarı-yarıya)
}

@Entity(tableName = "emre_pairing_settings")
data class EmrePairingSettings(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val listId: Long,
    val pairingMethod: EmrePairingMethod = EmrePairingMethod.SEQUENTIAL,
    val description: String = ""
)