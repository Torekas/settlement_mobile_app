package com.example.mojerozliczenia.sync

data class SyncPushRequest(
    val deviceId: String,
    val lastSyncAt: Long,
    val users: List<SyncUserDto>,
    val trips: List<SyncTripDto>,
    val members: List<SyncTripMemberDto>,
    val transactions: List<SyncTransactionDto>
)

data class SyncPushResponse(
    val serverTime: Long = System.currentTimeMillis()
)

data class SyncPullResponse(
    val serverTime: Long,
    val users: List<SyncUserDto> = emptyList(),
    val trips: List<SyncTripDto> = emptyList(),
    val members: List<SyncTripMemberDto> = emptyList(),
    val transactions: List<SyncTransactionDto> = emptyList()
)

data class SyncUserDto(
    val syncId: String,
    val username: String,
    val updatedAt: Long,
    val isDeleted: Boolean
)

data class SyncTripDto(
    val syncId: String,
    val name: String,
    val mainCurrency: String,
    val isArchived: Boolean,
    val createdDate: Long,
    val imageUrl: String?,
    val isImported: Boolean,
    val destination: String,
    val startDate: Long,
    val totalCost: Double,
    val updatedAt: Long,
    val isDeleted: Boolean
)

data class SyncTripMemberDto(
    val syncId: String,
    val tripSyncId: String,
    val userSyncId: String,
    val updatedAt: Long,
    val isDeleted: Boolean
)

data class SyncSplitDto(
    val beneficiarySyncId: String,
    val weight: Double
)

data class SyncTransactionDto(
    val syncId: String,
    val tripSyncId: String,
    val payerSyncId: String,
    val amount: Double,
    val currency: String,
    val description: String,
    val category: String,
    val exchangeRate: Double,
    val date: Long,
    val isRepayment: Boolean,
    val updatedAt: Long,
    val isDeleted: Boolean,
    val splits: List<SyncSplitDto>
)
