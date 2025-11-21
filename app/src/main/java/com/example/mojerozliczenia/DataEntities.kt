package com.example.mojerozliczenia

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.mojerozliczenia.packing.PackingItem

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val userId: Long = 0,
    val username: String,
    val passwordHash: String
)

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val tripId: Long = 0, // To jest klucz główyny
    val name: String,
    val mainCurrency: String = "PLN",
    val isArchived: Boolean = false,
    val createdDate: Long = System.currentTimeMillis(),
    val imageUrl: String? = null,
    val isImported: Boolean = false,
    val destination: String,
    val startDate: Long = System.currentTimeMillis(),
    val totalCost: Double = 0.0
)

@Entity(primaryKeys = ["tripId", "userId"])
data class TripMember(val tripId: Long, val userId: Long)

@Entity(tableName = "exchange_rates")
data class ExchangeRate(
    @PrimaryKey(autoGenerate = true) val rateId: Long = 0,
    val tripId: Long,
    val currencyCode: String,
    val rateToMain: Double
)

@Entity(tableName = "transactions_v2")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val transactionId: Long = 0,
    val tripId: Long,
    val payerId: Long,
    val amount: Double,
    val currency: String,
    val description: String,
    val category: String = "Inne",
    val exchangeRate: Double = 1.0,
    val date: Long = System.currentTimeMillis(),
    val isRepayment: Boolean = false
)

@Entity(tableName = "transaction_splits")
data class TransactionSplit(
    @PrimaryKey(autoGenerate = true) val splitId: Long = 0,
    val transactionId: Long,
    val beneficiaryId: Long,
    val weight: Double = 1.0
)

@Dao
interface AppDao {
    @Query("SELECT * FROM users WHERE username = :name LIMIT 1")
    suspend fun getUserByName(name: String): User?

    @Insert
    suspend fun insertUser(user: User): Long

    @Query("SELECT * FROM trips ORDER BY createdDate DESC")
    fun getAllTrips(): Flow<List<Trip>>

    @Insert
    suspend fun insertTrip(trip: Trip): Long
    @Insert
    suspend fun insertTripMember(member: TripMember)
    @Query("SELECT * FROM trips WHERE tripId = :id")
    suspend fun getTripById(id: Long): Trip
    @Query("SELECT u.* FROM users u INNER JOIN TripMember tm ON u.userId = tm.userId WHERE tm.tripId = :tripId")
    suspend fun getTripMembers(tripId: Long): List<User>
    @Insert
    suspend fun insertTransaction(transaction: Transaction): Long
    @Insert
    suspend fun insertTransactionSplit(split: TransactionSplit)
    @Query("SELECT * FROM transactions_v2 WHERE tripId = :tripId ORDER BY date DESC")
    suspend fun getTransactionsByTrip(tripId: Long): List<Transaction>
    @Query("SELECT * FROM transaction_splits WHERE transactionId IN (SELECT transactionId FROM transactions_v2 WHERE tripId = :tripId)")
    suspend fun getSplitsForTrip(tripId: Long): List<TransactionSplit>
    @Query("DELETE FROM transactions_v2 WHERE transactionId = :txId")
    suspend fun deleteTransactionById(txId: Long)
    @Query("DELETE FROM transaction_splits WHERE transactionId = :txId")
    suspend fun deleteSplitsByTransactionId(txId: Long)
    @Query("DELETE FROM transaction_splits WHERE transactionId IN (SELECT transactionId FROM transactions_v2 WHERE tripId = :tripId)")
    suspend fun deleteSplitsByTripId(tripId: Long)
    @Query("DELETE FROM transactions_v2 WHERE tripId = :tripId")
    suspend fun deleteTransactionsByTripId(tripId: Long)
    @Query("DELETE FROM TripMember WHERE tripId = :tripId")
    suspend fun deleteMembersByTripId(tripId: Long)
    @Query("DELETE FROM exchange_rates WHERE tripId = :tripId")
    suspend fun deleteRatesByTripId(tripId: Long)
    @Query("DELETE FROM trips WHERE tripId = :tripId")
    suspend fun deleteTripById(tripId: Long)

    @Query("SELECT * FROM packing_items WHERE tripId = :tripId")
    suspend fun getPackingItemsSync(tripId: Long): List<PackingItem>

    @Query("DELETE FROM TripMember WHERE tripId = :tripId AND userId = :userId")
    suspend fun removeMemberFromTrip(tripId: Long, userId: Long)

    @androidx.room.Transaction
    suspend fun deleteEntireTrip(tripId: Long) {
        deleteSplitsByTripId(tripId)
        deleteTransactionsByTripId(tripId)
        deleteMembersByTripId(tripId)
        deleteRatesByTripId(tripId)
        deleteTripById(tripId)
    }

}