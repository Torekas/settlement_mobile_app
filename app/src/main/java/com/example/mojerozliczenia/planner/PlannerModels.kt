package com.example.mojerozliczenia.planner

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "planner_events")
data class PlannerEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val title: String,
    val description: String,
    val timeInMillis: Long,
    val locationName: String,
    val isDone: Boolean = false
)