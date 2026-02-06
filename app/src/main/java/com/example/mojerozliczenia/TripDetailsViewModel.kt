package com.example.mojerozliczenia

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mojerozliczenia.packing.PackingDao
import com.example.mojerozliczenia.planner.PlannerDao
import com.example.mojerozliczenia.sync.SyncClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.round

class TripDetailsViewModel(
    private val dao: AppDao,
    private val packingDao: PackingDao,
    private val plannerDao: PlannerDao,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _trip = MutableStateFlow<Trip?>(null)
    val trip: StateFlow<Trip?> = _trip.asStateFlow()

    private val _uiState = MutableStateFlow(TripDetailsUiState())
    val uiState = _uiState.asStateFlow()

    fun loadTripData(tripId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val tripData = dao.getTripById(tripId)
            _trip.value = tripData

            val members = dao.getTripMembers(tripId)
            val transactions = dao.getTransactionsByTrip(tripId)
            val splits = dao.getSplitsForTrip(tripId)
            val membersById = members.associateBy { it.userId }
            val splitsByTransactionId = splits.groupBy { it.transactionId }

            val beneficiaryNamesByTransactionId = splitsByTransactionId.mapValues { (_, txSplits) ->
                txSplits.mapNotNull { split -> membersById[split.beneficiaryId]?.username }.distinct()
            }

            val beneficiaryIdsByTransactionId = splitsByTransactionId.mapValues { (_, txSplits) ->
                txSplits.map { split -> split.beneficiaryId }.distinct()
            }

            val expenses = transactions.filter { !it.isRepayment }

            // Calkowite wydatki w walucie bazowej (do ogolnego podsumowania)
            val totalSpent = expenses.sumOf { it.amount * it.exchangeRate }

            val categoryStats = expenses
                .groupBy { it.category }
                .mapValues { entry ->
                    entry.value.sumOf { it.amount * it.exchangeRate }
                }

            val currencyStats = expenses
                .groupBy { normalizeCurrencyCode(it.currency, tripData.mainCurrency) }
                .mapValues { entry ->
                    entry.value.sumOf { it.amount }
                }

            val settlementResult = calculateSettlement(
                members = members,
                transactions = transactions,
                splits = splits,
                defaultCurrency = tripData.mainCurrency
            )

            _uiState.value = TripDetailsUiState(
                trip = tripData,
                members = members,
                transactions = transactions,
                totalSpent = totalSpent,
                debts = settlementResult.debts,
                settlementSummaries = settlementResult.memberSummaries,
                transactionBeneficiaryNames = beneficiaryNamesByTransactionId,
                transactionBeneficiaryIds = beneficiaryIdsByTransactionId,
                isLoading = false,
                categorySummaries = categoryStats,
                currencySummaries = currencyStats
            )
        }
    }

    fun updateTripDetails(newName: String, newDate: Long) {
        viewModelScope.launch {
            _trip.value?.let { currentTrip ->
                val now = System.currentTimeMillis()
                val updatedTrip = currentTrip.copy(
                    name = newName,
                    startDate = newDate,
                    updatedAt = now,
                    syncState = SyncState.forUpdate(currentTrip.syncState)
                )
                dao.updateTrip(updatedTrip)
                loadTripData(currentTrip.tripId)
            }
        }
    }

    private fun calculateSettlement(
        members: List<User>,
        transactions: List<Transaction>,
        splits: List<TransactionSplit>,
        defaultCurrency: String
    ): SettlementResult {
        val splitsByTx = splits.groupBy { it.transactionId }
        val balancesByCurrency = mutableMapOf<String, MutableMap<Long, Double>>()
        val paidByCurrency = mutableMapOf<String, MutableMap<Long, Double>>()

        fun getOrCreateUserMap(
            container: MutableMap<String, MutableMap<Long, Double>>,
            currency: String
        ): MutableMap<Long, Double> {
            return container.getOrPut(currency) {
                members.associate { it.userId to 0.0 }.toMutableMap()
            }
        }

        transactions.forEach { tx ->
            val currency = normalizeCurrencyCode(tx.currency, defaultCurrency)
            val txSplits = splitsByTx[tx.transactionId].orEmpty()
            if (txSplits.isEmpty()) return@forEach

            val balances = getOrCreateUserMap(balancesByCurrency, currency)
            val paidMap = getOrCreateUserMap(paidByCurrency, currency)

            val amount = tx.amount
            balances[tx.payerId] = (balances[tx.payerId] ?: 0.0) + amount
            if (!tx.isRepayment) {
                paidMap[tx.payerId] = (paidMap[tx.payerId] ?: 0.0) + amount
            }

            val totalWeight = txSplits.sumOf { it.weight }
            if (totalWeight <= 0.0) return@forEach

            txSplits.forEach { split ->
                val share = amount * (split.weight / totalWeight)
                balances[split.beneficiaryId] = (balances[split.beneficiaryId] ?: 0.0) - share
            }
        }

        val allDebts = mutableListOf<Debt>()
        val allSummaries = mutableListOf<SettlementMemberSummary>()

        data class UserBalance(var userId: Long, var amount: Double)

        balancesByCurrency.toSortedMap().forEach { (currency, balancesMap) ->
            val paidMap = paidByCurrency[currency].orEmpty()

            members.forEach { member ->
                val paidAmount = roundCurrencyAmount(paidMap[member.userId] ?: 0.0)
                val netBalance = roundCurrencyAmount(balancesMap[member.userId] ?: 0.0)
                if (abs(paidAmount) > 0.004 || abs(netBalance) > 0.004) {
                    allSummaries.add(
                        SettlementMemberSummary(
                            userId = member.userId,
                            currency = currency,
                            paidAmount = paidAmount,
                            netBalance = netBalance
                        )
                    )
                }
            }

            val debtors = balancesMap
                .filter { it.value < -SETTLEMENT_EPSILON }
                .map { UserBalance(userId = it.key, amount = it.value) }
                .sortedBy { it.amount }
                .toMutableList()

            val creditors = balancesMap
                .filter { it.value > SETTLEMENT_EPSILON }
                .map { UserBalance(userId = it.key, amount = it.value) }
                .sortedByDescending { it.amount }
                .toMutableList()

            var i = 0
            var j = 0
            while (i < debtors.size && j < creditors.size) {
                val debtor = debtors[i]
                val creditor = creditors[j]

                val settleExact = minOf(debtor.amount.absoluteValue, creditor.amount)
                val settleRounded = roundCurrencyAmount(settleExact)

                if (debtor.userId != creditor.userId && settleRounded > SETTLEMENT_EPSILON) {
                    allDebts.add(
                        Debt(
                            fromUserId = debtor.userId,
                            toUserId = creditor.userId,
                            amount = settleRounded,
                            currency = currency
                        )
                    )
                }

                debtor.amount += settleExact
                creditor.amount -= settleExact

                if (debtor.amount.absoluteValue <= SETTLEMENT_EPSILON) i++
                if (creditor.amount <= SETTLEMENT_EPSILON) j++
            }
        }

        return SettlementResult(
            debts = allDebts,
            memberSummaries = allSummaries
        )
    }

    private fun normalizeCurrencyCode(currency: String?, defaultCurrency: String): String {
        val normalized = currency?.trim()?.uppercase().orEmpty()
        if (normalized.isNotBlank()) return normalized
        return defaultCurrency.trim().uppercase().ifBlank { "PLN" }
    }

    private fun roundCurrencyAmount(value: Double): Double {
        return round(value * 100.0) / 100.0
    }

    fun exportTripToJson(
        context: Context,
        trip: Trip,
        includePacking: Boolean,
        onJsonReady: (String) -> Unit
    ) {
        viewModelScope.launch {
            val members = dao.getTripMembers(trip.tripId)
            val transactions = dao.getTransactionsByTrip(trip.tripId)

            val transactionDataList = transactions.map { tx ->
                val splits = dao.getSplitsForTrip(trip.tripId).filter { it.transactionId == tx.transactionId }
                val payer = members.find { it.userId == tx.payerId }?.username ?: "Unknown"
                val beneficiaryNames = splits.mapNotNull { split ->
                    members.find { it.userId == split.beneficiaryId }?.username
                }

                TransactionExportData(
                    payerName = payer,
                    amount = tx.amount,
                    currency = tx.currency,
                    description = tx.description,
                    category = tx.category,
                    exchangeRate = tx.exchangeRate,
                    isRepayment = tx.isRepayment,
                    beneficiaryNames = beneficiaryNames
                )
            }

            var packingListNames: List<String>? = null
            if (includePacking) {
                val items = packingDao.getItemsForTripSync(trip.tripId)
                packingListNames = items.map { it.name }
            }

            val plannerEvents = plannerDao.getEventsForTripSync(trip.tripId).map { event ->
                PlannerEventExportData(
                    title = event.title,
                    description = event.description,
                    timeInMillis = event.timeInMillis,
                    locationName = event.locationName,
                    isDone = event.isDone
                )
            }

            val exportData = TripExportData(
                name = trip.name,
                mainCurrency = trip.mainCurrency,
                members = members.map { it.username },
                transactions = transactionDataList,
                packingList = packingListNames,
                plannerEvents = plannerEvents
            )

            val json = ExportUtils.tripToJson(exportData)
            onJsonReady(json)
        }
    }

    fun generateShareReport(): String {
        val tripName = _trip.value?.name ?: "Wyjazd"
        val total = String.format("%.2f", _uiState.value.totalSpent)
        return "Raport z wyjazdu '$tripName'.\nLacznie wydano (w walucie bazowej): $total.\n\nSprawdz szczegoly w aplikacji Moje Rozliczenia!"
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            dao.markTransactionDeleted(transaction.transactionId, now, SyncState.PENDING_DELETE)
            dao.deleteSplitsByTransactionId(transaction.transactionId)
            loadTripData(transaction.tripId)
        }
    }

    fun updateTransaction(
        transaction: Transaction,
        description: String,
        amount: Double,
        category: String,
        payerId: Long,
        beneficiaryIds: Set<Long>
    ) {
        if (description.isBlank() || amount <= 0.0 || beneficiaryIds.isEmpty()) return

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val updated = transaction.copy(
                payerId = payerId,
                amount = amount,
                description = description.trim(),
                category = if (transaction.isRepayment) transaction.category else category,
                updatedAt = now,
                syncState = SyncState.forUpdate(transaction.syncState)
            )
            dao.updateTransaction(updated)
            dao.deleteSplitsByTransactionId(transaction.transactionId)
            beneficiaryIds.forEach { beneficiaryId ->
                dao.insertTransactionSplit(
                    TransactionSplit(
                        transactionId = transaction.transactionId,
                        beneficiaryId = beneficiaryId,
                        weight = 1.0
                    )
                )
            }
            loadTripData(transaction.tripId)
        }
    }

    fun getMemberName(userId: Long): String {
        return _uiState.value.members.find { it.userId == userId }?.username ?: "???"
    }

    fun setAddMemberDialogVisibility(visible: Boolean) {
        _uiState.value = _uiState.value.copy(
            showAddMemberDialog = visible,
            addMemberError = null,
            addMemberLoading = false
        )
    }

    fun addMember(name: String) {
        viewModelScope.launch {
            _trip.value?.let { trip ->
                val now = System.currentTimeMillis()
                val normalized = name.trim()
                if (normalized.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        addMemberError = "Wpisz nazwe uzytkownika",
                        addMemberLoading = false
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(addMemberError = null, addMemberLoading = true)

                if (!SyncClient.isConfigured()) {
                    _uiState.value = _uiState.value.copy(
                        addMemberError = "Brak polaczenia z serwerem",
                        addMemberLoading = false
                    )
                    return@launch
                }

                val token = sessionManager.fetchAuthToken()
                val authHeader = if (token.isNullOrBlank()) null else "Bearer $token"

                val response = try {
                    SyncClient.createUsersApi().findUser(authHeader, normalized)
                } catch (exc: retrofit2.HttpException) {
                    val message = when (exc.code()) {
                        401, 403 -> "Zaloguj sie ponownie"
                        404 -> "Uzytkownik nie istnieje"
                        else -> "Blad serwera"
                    }
                    _uiState.value = _uiState.value.copy(addMemberError = message, addMemberLoading = false)
                    return@launch
                } catch (_: Exception) {
                    _uiState.value = _uiState.value.copy(
                        addMemberError = "Brak polaczenia z serwerem",
                        addMemberLoading = false
                    )
                    return@launch
                }

                val resolvedUser = dao.getUserBySyncId(response.userSyncId) ?: run {
                    val newId = dao.insertUser(
                        User(
                            syncId = response.userSyncId,
                            username = response.username,
                            passwordHash = "",
                            updatedAt = now,
                            syncState = SyncState.SYNCED
                        )
                    )
                    User(
                        userId = newId,
                        syncId = response.userSyncId,
                        username = response.username,
                        passwordHash = "",
                        updatedAt = now,
                        syncState = SyncState.SYNCED
                    )
                }

                dao.insertTripMember(
                    TripMember(
                        tripId = trip.tripId,
                        userId = resolvedUser.userId,
                        updatedAt = now,
                        syncState = SyncState.PENDING_CREATE
                    )
                )
                loadTripData(trip.tripId)
            }
            _uiState.value = _uiState.value.copy(addMemberLoading = false)
            setAddMemberDialogVisibility(false)
        }
    }

    fun settleDebt(fromId: Long, toId: Long, amount: Double, currency: String, rate: Double) {
        viewModelScope.launch {
            _trip.value?.let { trip ->
                val now = System.currentTimeMillis()
                val normalizedCurrency = normalizeCurrencyCode(currency, trip.mainCurrency)
                val newTx = Transaction(
                    tripId = trip.tripId,
                    payerId = fromId,
                    amount = amount,
                    currency = normalizedCurrency,
                    description = "Splata dlugu ($normalizedCurrency)",
                    category = "Inne",
                    exchangeRate = rate,
                    isRepayment = true,
                    updatedAt = now,
                    syncState = SyncState.PENDING_CREATE
                )
                val txId = dao.insertTransaction(newTx)
                dao.insertTransactionSplit(TransactionSplit(transactionId = txId, beneficiaryId = toId, weight = 1.0))
                loadTripData(trip.tripId)
            }
        }
    }

    fun fetchSettlementRateFromNbp(fromCurrency: String, toCurrency: String) {
        viewModelScope.launch {
            val normalizedFrom = fromCurrency.uppercase()
            val normalizedTo = toCurrency.uppercase()
            if (normalizedFrom == normalizedTo) {
                _uiState.value = _uiState.value.copy(fetchedSettlementRate = 1.0)
                return@launch
            }

            val fromRateToPln = fetchRateToPln(normalizedFrom)
            val toRateToPln = fetchRateToPln(normalizedTo)
            val rate = if (fromRateToPln != null && toRateToPln != null && toRateToPln != 0.0) {
                fromRateToPln / toRateToPln
            } else {
                null
            }
            _uiState.value = _uiState.value.copy(fetchedSettlementRate = rate)
        }
    }

    private suspend fun fetchRateToPln(currency: String): Double? {
        return if (currency.equals("PLN", ignoreCase = true)) {
            1.0
        } else {
            NetworkUtils.fetchNbpRate(currency)
        }
    }

    fun clearSettlementRate() {
        _uiState.value = _uiState.value.copy(fetchedSettlementRate = null)
    }

    fun removeMember(userId: Long) {
        viewModelScope.launch {
            _trip.value?.let { trip ->
                val now = System.currentTimeMillis()
                dao.markTripMemberDeleted(trip.tripId, userId, now, SyncState.PENDING_DELETE)
                loadTripData(trip.tripId)
            }
        }
    }
}

private data class SettlementResult(
    val debts: List<Debt>,
    val memberSummaries: List<SettlementMemberSummary>
)

private const val SETTLEMENT_EPSILON = 0.01

data class TripDetailsUiState(
    val trip: Trip? = null,
    val members: List<User> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val totalSpent: Double = 0.0,
    val debts: List<Debt> = emptyList(),
    val settlementSummaries: List<SettlementMemberSummary> = emptyList(),
    val transactionBeneficiaryNames: Map<Long, List<String>> = emptyMap(),
    val transactionBeneficiaryIds: Map<Long, List<Long>> = emptyMap(),
    val isLoading: Boolean = false,
    val showAddMemberDialog: Boolean = false,
    val addMemberError: String? = null,
    val addMemberLoading: Boolean = false,
    val categorySummaries: Map<String, Double> = emptyMap(),
    val currencySummaries: Map<String, Double> = emptyMap(),
    val fetchedSettlementRate: Double? = null
)

data class SettlementMemberSummary(
    val userId: Long,
    val currency: String,
    val paidAmount: Double,
    val netBalance: Double
) {
    val toPay: Double
        get() = if (netBalance < 0.0) -netBalance else 0.0

    val toRecover: Double
        get() = if (netBalance > 0.0) netBalance else 0.0
}
