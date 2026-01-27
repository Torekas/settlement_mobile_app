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
import kotlin.math.absoluteValue

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

            val expenses = transactions.filter { !it.isRepayment }

            // Calkowite wydatki w walucie bazowej (do ogolnego podsumowania)
            val totalSpent = expenses.sumOf { it.amount * it.exchangeRate }

            val categoryStats = expenses
                .groupBy { it.category }
                .mapValues { entry ->
                    entry.value.sumOf { it.amount * it.exchangeRate }
                }

            val currencyStats = expenses
                .groupBy { it.currency }
                .mapValues { entry ->
                    entry.value.sumOf { it.amount }
                }

            // NOWA LOGIKA: Obliczanie dlugow z podzialem na waluty
            val debts = calculateDebts(members, transactions, splits)

            _uiState.value = TripDetailsUiState(
                trip = tripData,
                members = members,
                transactions = transactions,
                totalSpent = totalSpent,
                debts = debts,
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

    private fun calculateDebts(
        members: List<User>,
        transactions: List<Transaction>,
        splits: List<TransactionSplit>
    ): List<Debt> {
        val allDebts = mutableListOf<Debt>()

        // 1. Grupowanie transakcji wedlug waluty
        val transactionsByCurrency = transactions.groupBy { it.currency }

        transactionsByCurrency.forEach { (currency, txsInCurrency) ->
            val balances = mutableMapOf<Long, Double>()
            members.forEach { balances[it.userId] = 0.0 }

            // 2. Obliczanie salda dla danej waluty (bez przeliczania kursow)
            for (tx in txsInCurrency) {
                val txSplits = splits.filter { it.transactionId == tx.transactionId }
                if (txSplits.isEmpty()) continue

                val amount = tx.amount
                balances[tx.payerId] = (balances[tx.payerId] ?: 0.0) + amount

                val totalWeight = txSplits.sumOf { it.weight }
                if (totalWeight == 0.0) continue

                for (split in txSplits) {
                    val share = amount * (split.weight / totalWeight)
                    balances[split.beneficiaryId] = (balances[split.beneficiaryId] ?: 0.0) - share
                }
            }

            // 3. Rozliczanie dlugow wewnatrz tej konkretnej waluty
            val debtors = balances.filter { it.value < -0.01 }.keys.toMutableList()
            val creditors = balances.filter { it.value > 0.01 }.keys.toMutableList()

            debtors.sortBy { balances[it] }
            creditors.sortByDescending { balances[it] }

            var i = 0
            var j = 0
            val currentBalances = balances.toMutableMap()

            while (i < debtors.size && j < creditors.size) {
                val debtorId = debtors[i]
                val creditorId = creditors[j]

                val debtAmount = (currentBalances[debtorId] ?: 0.0).absoluteValue
                val creditAmount = currentBalances[creditorId] ?: 0.0

                val settledAmount = minOf(debtAmount, creditAmount)

                if (debtorId != creditorId && settledAmount > 0.01) {
                    // Dodajemy dlug z informacja o walucie
                    allDebts.add(Debt(debtorId, creditorId, settledAmount, currency))
                }

                currentBalances[debtorId] = (currentBalances[debtorId] ?: 0.0) + settledAmount
                currentBalances[creditorId] = (currentBalances[creditorId] ?: 0.0) - settledAmount

                if ((currentBalances[debtorId] ?: 0.0).absoluteValue < 0.01) i++
                if ((currentBalances[creditorId] ?: 0.0) < 0.01) j++
            }
        }

        return allDebts
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
                val newTx = Transaction(
                    tripId = trip.tripId,
                    payerId = fromId,
                    amount = amount,
                    currency = currency,
                    description = "Splata dlugu ($currency)",
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

data class TripDetailsUiState(
    val trip: Trip? = null,
    val members: List<User> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val totalSpent: Double = 0.0,
    val debts: List<Debt> = emptyList(),
    val isLoading: Boolean = false,
    val showAddMemberDialog: Boolean = false,
    val addMemberError: String? = null,
    val addMemberLoading: Boolean = false,
    val categorySummaries: Map<String, Double> = emptyMap(),
    val currencySummaries: Map<String, Double> = emptyMap(),
    val fetchedSettlementRate: Double? = null
)
