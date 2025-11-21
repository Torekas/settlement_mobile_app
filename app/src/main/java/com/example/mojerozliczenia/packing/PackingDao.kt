package com.example.mojerozliczenia.packing

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PackingDao {
    @Query("SELECT * FROM packing_items WHERE tripId = :tripId ORDER BY isPacked ASC, id DESC")
    fun getItemsForTrip(tripId: Long): Flow<List<PackingItem>>

    @Query("SELECT * FROM packing_items WHERE tripId = :tripId")
    suspend fun getItemsForTripSync(tripId: Long): List<PackingItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: PackingItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PackingItem>)

    @Update
    suspend fun updateItem(item: PackingItem)

    @Delete
    suspend fun deleteItem(item: PackingItem)

    @Query("DELETE FROM packing_items WHERE tripId = :tripId")
    suspend fun clearAllForTrip(tripId: Long)
}