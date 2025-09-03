package com.example.ranking.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "tournaments",
    foreignKeys = [
        ForeignKey(
            entity = SongList::class,
            parentColumns = ["id"],
            childColumns = ["songListId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CriterionList::class,
            parentColumns = ["id"],
            childColumns = ["criterionListId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class Tournament(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val startDate: String,
    val songListId: Long,
    val systemType: String, // "SWISS", "EMRE_CORRECT"
    val criterionListId: Long? = null,
    val criteriaSettings: String? = null, // JSON: {"scoringType":"comparative","scoreScale":10,"drawThresholdPercent":[40,60],"autoWinnerFromCriteria":false,"autoOpenCriteriaPanel":false,"mandatoryCriteria":false}
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)