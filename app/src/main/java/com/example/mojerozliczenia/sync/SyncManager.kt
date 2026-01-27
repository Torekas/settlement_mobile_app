package com.example.mojerozliczenia.sync

import com.example.mojerozliczenia.AppDao
import com.example.mojerozliczenia.SyncState
import com.example.mojerozliczenia.Transaction
import com.example.mojerozliczenia.TransactionSplit
import com.example.mojerozliczenia.Trip
import com.example.mojerozliczenia.TripMember
import com.example.mojerozliczenia.User

class SyncManager(
    private val dao: AppDao,
    private val api: SyncApi,
    private val prefs: SyncPrefs
) {
    suspend fun sync(userSyncId: String, authToken: String?, forceFullSync: Boolean = false): Boolean {
        if (!SyncClient.isConfigured()) return true

        val deviceId = prefs.getDeviceId()
        val lastSyncAt = prefs.getLastSyncAt()
        val pullSince = if (forceFullSync) 0L else lastSyncAt
        val authHeader = if (authToken.isNullOrBlank()) null else "Bearer $authToken"

        val dirtyUsers = dao.getDirtyUsers()
        val dirtyTrips = dao.getDirtyTrips()
        val dirtyMembers = dao.getDirtyTripMembers()
        val dirtyTransactions = dao.getDirtyTransactions()

        val splitsByTxId = if (dirtyTransactions.isEmpty()) {
            emptyMap()
        } else {
            dao.getSplitsForTransactions(dirtyTransactions.map { it.transactionId }).groupBy { it.transactionId }
        }
        val splitBeneficiaryIds = splitsByTxId.values.flatten().map { it.beneficiaryId }
        val userIds = (dirtyMembers.map { it.userId } +
            dirtyTransactions.map { it.payerId } +
            splitBeneficiaryIds).distinct()
        val tripIds = (dirtyMembers.map { it.tripId } + dirtyTransactions.map { it.tripId }).distinct()

        val usersById = if (userIds.isEmpty()) emptyMap() else dao.getUsersByIds(userIds).associateBy { it.userId }
        val tripsById = if (tripIds.isEmpty()) emptyMap() else dao.getTripsByIds(tripIds).associateBy { it.tripId }

        val userDtos = dirtyUsers.map { it.toSyncDto() }
        val tripDtos = dirtyTrips.map { it.toSyncDto() }

        val memberDtos = mutableListOf<SyncTripMemberDto>()
        val memberSyncIds = mutableListOf<String>()
        dirtyMembers.forEach { member ->
            val trip = tripsById[member.tripId] ?: return@forEach
            val user = usersById[member.userId] ?: return@forEach
            memberDtos.add(member.toSyncDto(trip.syncId, user.syncId))
            memberSyncIds.add(member.syncId)
        }

        val transactionDtos = mutableListOf<SyncTransactionDto>()
        val transactionIds = mutableListOf<Long>()
        dirtyTransactions.forEach { tx ->
            val trip = tripsById[tx.tripId] ?: return@forEach
            val payer = usersById[tx.payerId] ?: return@forEach
            val splitDtos = buildSplitDtos(splitsByTxId[tx.transactionId] ?: emptyList(), usersById)
            transactionDtos.add(tx.toSyncDto(trip.syncId, payer.syncId, splitDtos))
            transactionIds.add(tx.transactionId)
        }

        val hasChanges = userDtos.isNotEmpty() ||
            tripDtos.isNotEmpty() ||
            memberDtos.isNotEmpty() ||
            transactionDtos.isNotEmpty()

        if (hasChanges) {
            val pushResponse = api.pushChanges(
                authorization = authHeader,
                userSyncId = userSyncId,
                deviceId = deviceId,
                request = SyncPushRequest(
                    deviceId = deviceId,
                    lastSyncAt = lastSyncAt,
                    users = userDtos,
                    trips = tripDtos,
                    members = memberDtos,
                    transactions = transactionDtos
                )
            )

            if (dirtyUsers.isNotEmpty()) dao.markUsersSynced(dirtyUsers.map { it.userId })
            if (dirtyTrips.isNotEmpty()) dao.markTripsSynced(dirtyTrips.map { it.tripId })
            if (memberSyncIds.isNotEmpty()) dao.markTripMembersSynced(memberSyncIds)
            if (transactionIds.isNotEmpty()) dao.markTransactionsSynced(transactionIds)

            prefs.setLastSyncAt(pushResponse.serverTime)
        }

        val pullResponse = api.pullChanges(
            authorization = authHeader,
            userSyncId = userSyncId,
            deviceId = deviceId,
            since = pullSince
        )

        applyUsers(pullResponse.users)
        applyTrips(pullResponse.trips)
        applyMembers(pullResponse.members)
        applyTransactions(pullResponse.transactions)

        prefs.setLastSyncAt(pullResponse.serverTime)
        return true
    }

    private fun buildSplitDtos(
        splits: List<TransactionSplit>,
        usersById: Map<Long, User>
    ): List<SyncSplitDto> {
        return splits.mapNotNull { split ->
            val user = usersById[split.beneficiaryId] ?: return@mapNotNull null
            SyncSplitDto(beneficiarySyncId = user.syncId, weight = split.weight)
        }
    }

    private suspend fun applyUsers(users: List<SyncUserDto>) {
        users.forEach { dto ->
            val local = dao.getUserBySyncId(dto.syncId)
            if (local == null) {
                dao.insertUser(
                    User(
                        syncId = dto.syncId,
                        username = dto.username,
                        passwordHash = "",
                        updatedAt = dto.updatedAt,
                        syncState = SyncState.SYNCED,
                        isDeleted = dto.isDeleted
                    )
                )
            } else if (local.syncState == SyncState.SYNCED && dto.updatedAt > local.updatedAt) {
                val updated = local.copy(
                    username = dto.username,
                    updatedAt = dto.updatedAt,
                    syncState = SyncState.SYNCED,
                    isDeleted = dto.isDeleted
                )
                dao.updateUser(updated)
            }
        }
    }

    private suspend fun applyTrips(trips: List<SyncTripDto>) {
        trips.forEach { dto ->
            val local = dao.getTripBySyncId(dto.syncId)
            if (local == null) {
                dao.insertTrip(
                    Trip(
                        syncId = dto.syncId,
                        name = dto.name,
                        mainCurrency = dto.mainCurrency,
                        isArchived = dto.isArchived,
                        createdDate = dto.createdDate,
                        imageUrl = dto.imageUrl,
                        isImported = dto.isImported,
                        destination = dto.destination,
                        startDate = dto.startDate,
                        totalCost = dto.totalCost,
                        updatedAt = dto.updatedAt,
                        syncState = SyncState.SYNCED,
                        isDeleted = dto.isDeleted
                    )
                )
            } else if (local.syncState == SyncState.SYNCED && dto.updatedAt > local.updatedAt) {
                val updated = local.copy(
                    name = dto.name,
                    mainCurrency = dto.mainCurrency,
                    isArchived = dto.isArchived,
                    createdDate = dto.createdDate,
                    imageUrl = dto.imageUrl,
                    isImported = dto.isImported,
                    destination = dto.destination,
                    startDate = dto.startDate,
                    totalCost = dto.totalCost,
                    updatedAt = dto.updatedAt,
                    syncState = SyncState.SYNCED,
                    isDeleted = dto.isDeleted
                )
                dao.updateTrip(updated)
            }
        }
    }

    private suspend fun applyMembers(members: List<SyncTripMemberDto>) {
        members.forEach { dto ->
            val trip = dao.getTripBySyncId(dto.tripSyncId) ?: return@forEach
            val user = dao.getUserBySyncId(dto.userSyncId) ?: return@forEach
            val local = dao.getTripMember(trip.tripId, user.userId)
            if (local == null) {
                dao.insertTripMember(
                    TripMember(
                        tripId = trip.tripId,
                        userId = user.userId,
                        syncId = dto.syncId,
                        updatedAt = dto.updatedAt,
                        syncState = SyncState.SYNCED,
                        isDeleted = dto.isDeleted
                    )
                )
            } else if (local.syncState == SyncState.SYNCED && dto.updatedAt > local.updatedAt) {
                val updated = local.copy(
                    updatedAt = dto.updatedAt,
                    syncState = SyncState.SYNCED,
                    isDeleted = dto.isDeleted
                )
                dao.updateTripMember(updated)
            }
        }
    }

    private suspend fun applyTransactions(transactions: List<SyncTransactionDto>) {
        transactions.forEach { dto ->
            val trip = dao.getTripBySyncId(dto.tripSyncId) ?: return@forEach
            val payer = dao.getUserBySyncId(dto.payerSyncId) ?: return@forEach
            val local = dao.getTransactionBySyncId(dto.syncId)

            if (dto.isDeleted) {
                if (local != null && local.syncState == SyncState.SYNCED && dto.updatedAt > local.updatedAt) {
                    dao.markTransactionDeleted(local.transactionId, dto.updatedAt, SyncState.SYNCED)
                    dao.deleteSplitsByTransactionId(local.transactionId)
                }
                return@forEach
            }

            if (local == null) {
                val newId = dao.insertTransaction(
                    Transaction(
                        syncId = dto.syncId,
                        tripId = trip.tripId,
                        payerId = payer.userId,
                        amount = dto.amount,
                        currency = dto.currency,
                        description = dto.description,
                        category = dto.category,
                        exchangeRate = dto.exchangeRate,
                        date = dto.date,
                        isRepayment = dto.isRepayment,
                        updatedAt = dto.updatedAt,
                        syncState = SyncState.SYNCED,
                        isDeleted = false
                    )
                )
                insertSplits(newId, dto.splits)
            } else if (local.syncState == SyncState.SYNCED && dto.updatedAt > local.updatedAt) {
                val updated = local.copy(
                    tripId = trip.tripId,
                    payerId = payer.userId,
                    amount = dto.amount,
                    currency = dto.currency,
                    description = dto.description,
                    category = dto.category,
                    exchangeRate = dto.exchangeRate,
                    date = dto.date,
                    isRepayment = dto.isRepayment,
                    updatedAt = dto.updatedAt,
                    syncState = SyncState.SYNCED,
                    isDeleted = false
                )
                dao.updateTransaction(updated)
                dao.deleteSplitsByTransactionId(local.transactionId)
                insertSplits(local.transactionId, dto.splits)
            }
        }
    }

    private suspend fun insertSplits(transactionId: Long, splits: List<SyncSplitDto>) {
        if (splits.isEmpty()) return
        val userSyncIds = splits.map { it.beneficiarySyncId }.distinct()
        val users = userSyncIds.mapNotNull { dao.getUserBySyncId(it) }.associateBy { it.syncId }

        splits.forEach { split ->
            val user = users[split.beneficiarySyncId] ?: return@forEach
            dao.insertTransactionSplit(
                TransactionSplit(
                    transactionId = transactionId,
                    beneficiaryId = user.userId,
                    weight = split.weight
                )
            )
        }
    }

    private fun User.toSyncDto(): SyncUserDto {
        return SyncUserDto(
            syncId = syncId,
            username = username,
            updatedAt = updatedAt,
            isDeleted = isDeleted
        )
    }

    private fun Trip.toSyncDto(): SyncTripDto {
        return SyncTripDto(
            syncId = syncId,
            name = name,
            mainCurrency = mainCurrency,
            isArchived = isArchived,
            createdDate = createdDate,
            imageUrl = imageUrl,
            isImported = isImported,
            destination = destination,
            startDate = startDate,
            totalCost = totalCost,
            updatedAt = updatedAt,
            isDeleted = isDeleted
        )
    }

    private fun TripMember.toSyncDto(tripSyncId: String, userSyncId: String): SyncTripMemberDto {
        return SyncTripMemberDto(
            syncId = syncId,
            tripSyncId = tripSyncId,
            userSyncId = userSyncId,
            updatedAt = updatedAt,
            isDeleted = isDeleted
        )
    }

    private fun Transaction.toSyncDto(
        tripSyncId: String,
        payerSyncId: String,
        splits: List<SyncSplitDto>
    ): SyncTransactionDto {
        return SyncTransactionDto(
            syncId = syncId,
            tripSyncId = tripSyncId,
            payerSyncId = payerSyncId,
            amount = amount,
            currency = currency,
            description = description,
            category = category,
            exchangeRate = exchangeRate,
            date = date,
            isRepayment = isRepayment,
            updatedAt = updatedAt,
            isDeleted = isDeleted,
            splits = splits
        )
    }
}
