package com.example.mojerozliczenia

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.example.mojerozliczenia.packing.PackingItem
import java.util.UUID

@Entity(
    tableName = "users",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class User(
    @PrimaryKey(autoGenerate = true) val userId: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val username: String,
    val passwordHash: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val syncState: Int = SyncState.SYNCED,
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "trips",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class Trip(
    @PrimaryKey(autoGenerate = true) val tripId: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val name: String,
    val mainCurrency: String = "PLN",
    val isArchived: Boolean = false,
    val createdDate: Long = System.currentTimeMillis(),
    val imageUrl: String? = null,
    val isImported: Boolean = false,
    val destination: String,
    val startDate: Long = System.currentTimeMillis(),
    val totalCost: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis(),
    val syncState: Int = SyncState.SYNCED,
    val isDeleted: Boolean = false
)

@Entity(primaryKeys = ["tripId", "userId"])
data class TripMember(
    val tripId: Long,
    val userId: Long,
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncState: Int = SyncState.SYNCED,
    val isDeleted: Boolean = false
)

@Entity(tableName = "exchange_rates")
data class ExchangeRate(
    @PrimaryKey(autoGenerate = true) val rateId: Long = 0,
    val tripId: Long,
    val currencyCode: String,
    val rateToMain: Double
)

@Entity(
    tableName = "transactions_v2",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val transactionId: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val tripId: Long,
    val payerId: Long,
    val amount: Double,
    val currency: String,
    val description: String,
    val category: String = "Inne",
    val exchangeRate: Double = 1.0,
    val date: Long = System.currentTimeMillis(),
    val isRepayment: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val syncState: Int = SyncState.SYNCED,
    val isDeleted: Boolean = false
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
    @Query("SELECT * FROM users WHERE username = :name AND isDeleted = 0 LIMIT 1")
    suspend fun getUserByName(name: String): User?

    @Query("SELECT * FROM users WHERE userId = :id LIMIT 1")
    suspend fun getUserById(id: Long): User?

    @Query("SELECT * FROM users WHERE syncId = :syncId LIMIT 1")
    suspend fun getUserBySyncId(syncId: String): User?

    @Query("SELECT * FROM users WHERE userId IN (:ids)")
    suspend fun getUsersByIds(ids: List<Long>): List<User>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Update
    suspend fun updateUser(user: User)

    @Query("SELECT * FROM trips WHERE isDeleted = 0 ORDER BY createdDate DESC")
    fun getAllTrips(): Flow<List<Trip>>

    @Query("""
        SELECT t.* FROM trips t
        INNER JOIN TripMember tm ON t.tripId = tm.tripId
        WHERE tm.userId = :userId AND tm.isDeleted = 0 AND t.isDeleted = 0
        ORDER BY t.createdDate DESC
    """)
    fun getTripsForUser(userId: Long): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE tripId = :id AND isDeleted = 0")
    suspend fun getTripById(id: Long): Trip

    @Query("SELECT * FROM trips WHERE syncId = :syncId LIMIT 1")
    suspend fun getTripBySyncId(syncId: String): Trip?

    @Query("SELECT * FROM trips WHERE tripId IN (:ids)")
    suspend fun getTripsByIds(ids: List<Long>): List<Trip>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: Trip): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTripMember(member: TripMember)

    @Update
    suspend fun updateTrip(trip: Trip)

    @Update
    suspend fun updateTripMember(member: TripMember)

    @Query("SELECT * FROM TripMember WHERE tripId = :tripId AND userId = :userId LIMIT 1")
    suspend fun getTripMember(tripId: Long, userId: Long): TripMember?

    @Query("""
        SELECT u.* FROM users u
        INNER JOIN TripMember tm ON u.userId = tm.userId
        WHERE tm.tripId = :tripId AND tm.isDeleted = 0 AND u.isDeleted = 0
    """)
    suspend fun getTripMembers(tripId: Long): List<User>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Query("SELECT * FROM transactions_v2 WHERE syncId = :syncId LIMIT 1")
    suspend fun getTransactionBySyncId(syncId: String): Transaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionSplit(split: TransactionSplit)

    @Query("SELECT * FROM transactions_v2 WHERE tripId = :tripId AND isDeleted = 0 ORDER BY date DESC")
    suspend fun getTransactionsByTrip(tripId: Long): List<Transaction>

    @Query("""
        SELECT * FROM transaction_splits
        WHERE transactionId IN (
            SELECT transactionId FROM transactions_v2 WHERE tripId = :tripId AND isDeleted = 0
        )
    """)
    suspend fun getSplitsForTrip(tripId: Long): List<TransactionSplit>

    @Query("SELECT * FROM transaction_splits WHERE transactionId IN (:transactionIds)")
    suspend fun getSplitsForTransactions(transactionIds: List<Long>): List<TransactionSplit>

    @Query("UPDATE transactions_v2 SET isDeleted = 1, updatedAt = :updatedAt, syncState = :syncState WHERE transactionId = :txId")
    suspend fun markTransactionDeleted(txId: Long, updatedAt: Long, syncState: Int)

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

    @Query("UPDATE trips SET isDeleted = 1, updatedAt = :updatedAt, syncState = :syncState WHERE tripId = :tripId")
    suspend fun markTripDeleted(tripId: Long, updatedAt: Long, syncState: Int)

    @Query("SELECT * FROM packing_items WHERE tripId = :tripId")
    suspend fun getPackingItemsSync(tripId: Long): List<PackingItem>

    @Query("UPDATE TripMember SET isDeleted = 1, updatedAt = :updatedAt, syncState = :syncState WHERE tripId = :tripId AND userId = :userId")
    suspend fun markTripMemberDeleted(tripId: Long, userId: Long, updatedAt: Long, syncState: Int)

    @androidx.room.Transaction
    suspend fun deleteEntireTrip(tripId: Long) {
        deleteSplitsByTripId(tripId)
        deleteTransactionsByTripId(tripId)
        deleteMembersByTripId(tripId)
        deleteRatesByTripId(tripId)
        markTripDeleted(tripId, System.currentTimeMillis(), SyncState.PENDING_DELETE)
    }

    // Sync helpers
    @Query("SELECT * FROM users WHERE syncState != :synced")
    suspend fun getDirtyUsers(synced: Int = SyncState.SYNCED): List<User>

    @Query("SELECT * FROM trips WHERE syncState != :synced")
    suspend fun getDirtyTrips(synced: Int = SyncState.SYNCED): List<Trip>

    @Query("SELECT * FROM TripMember WHERE syncState != :synced")
    suspend fun getDirtyTripMembers(synced: Int = SyncState.SYNCED): List<TripMember>

    @Query("SELECT * FROM transactions_v2 WHERE syncState != :synced")
    suspend fun getDirtyTransactions(synced: Int = SyncState.SYNCED): List<Transaction>

    @Query("UPDATE users SET syncState = :synced WHERE userId IN (:ids)")
    suspend fun markUsersSynced(ids: List<Long>, synced: Int = SyncState.SYNCED)

    @Query("UPDATE trips SET syncState = :synced WHERE tripId IN (:ids)")
    suspend fun markTripsSynced(ids: List<Long>, synced: Int = SyncState.SYNCED)

    @Query("UPDATE TripMember SET syncState = :synced WHERE syncId IN (:syncIds)")
    suspend fun markTripMembersSynced(syncIds: List<String>, synced: Int = SyncState.SYNCED)

    @Query("UPDATE transactions_v2 SET syncState = :synced WHERE transactionId IN (:ids)")
    suspend fun markTransactionsSynced(ids: List<Long>, synced: Int = SyncState.SYNCED)
}
