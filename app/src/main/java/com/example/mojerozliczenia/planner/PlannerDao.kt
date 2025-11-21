package com.example.mojerozliczenia.planner

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannerDao {
    @Query("SELECT * FROM planner_events WHERE tripId = :tripId ORDER BY timeInMillis ASC")
    fun getEventsForTrip(tripId: Long): Flow<List<PlannerEvent>>

    // --- DO EKSPORTU ---
    @Query("SELECT * FROM planner_events WHERE tripId = :tripId")
    suspend fun getEventsForTripSync(tripId: Long): List<PlannerEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: PlannerEvent)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<PlannerEvent>)

    @Update
    suspend fun updateEvent(event: PlannerEvent)

    @Delete
    suspend fun deleteEvent(event: PlannerEvent)
}