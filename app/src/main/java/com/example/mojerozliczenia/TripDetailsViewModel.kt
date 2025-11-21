package com.example.mojerozliczenia

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mojerozliczenia.packing.PackingDao
import com.example.mojerozliczenia.planner.PlannerDao // Import
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

class TripDetailsViewModel(
    private val dao: AppDao,
    private val packingDao: PackingDao,
    private val plannerDao: PlannerDao // Nowy parametr w konstruktorze
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
                val updatedTrip = currentTrip.copy(
                    name = newName,
                    startDate = newDate
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
        val balances = mutableMapOf<Long, Double>()
        members.forEach { balances[it.userId] = 0.0 }

        for (tx in transactions) {
            val txSplits = splits.filter { it.transactionId == tx.transactionId }
            if (txSplits.isEmpty()) continue

            val amountInBase = tx.amount * tx.exchangeRate
            balances[tx.payerId] = (balances[tx.payerId] ?: 0.0) + amountInBase

            val totalWeight = txSplits.sumOf { it.weight }
            if (totalWeight == 0.0) continue

            for (split in txSplits) {
                val share = amountInBase * (split.weight / totalWeight)
                balances[split.beneficiaryId] = (balances[split.beneficiaryId] ?: 0.0) - share
            }
        }

        val debtors = balances.filter { it.value < -0.01 }.keys.toMutableList()
        val creditors = balances.filter { it.value > 0.01 }.keys.toMutableList()
        val debtsList = mutableListOf<Debt>()

        debtors.sortBy { balances[it] }
        creditors.sortByDescending { balances[it] }

        var i = 0
        var j = 0

        while (i < debtors.size && j < creditors.size) {
            val debtorId = debtors[i]
            val creditorId = creditors[j]

            val debtAmount = (balances[debtorId] ?: 0.0).absoluteValue
            val creditAmount = balances[creditorId] ?: 0.0

            val settledAmount = minOf(debtAmount, creditAmount)

            if (debtorId != creditorId && settledAmount > 0.01) {
                debtsList.add(Debt(debtorId, creditorId, settledAmount))
            }

            balances[debtorId] = (balances[debtorId] ?: 0.0) + settledAmount
            balances[creditorId] = (balances[creditorId] ?: 0.0) - settledAmount

            if ((balances[debtorId] ?: 0.0).absoluteValue < 0.01) i++
            if ((balances[creditorId] ?: 0.0) < 0.01) j++
        }

        return debtsList
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
                plannerEvents = plannerEvents // Dodajemy do JSON
            )

            val json = ExportUtils.tripToJson(exportData)
            onJsonReady(json)
        }
    }

    fun generateShareReport(): String {
        val tripName = _trip.value?.name ?: "Wyjazd"
        val total = String.format("%.2f", _uiState.value.totalSpent)
        return "Raport z wyjazdu '$tripName'.\nŁącznie wydano: $total PLN.\n\nSprawdź szczegóły w aplikacji Moje Rozliczenia!"
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            dao.deleteTransactionById(transaction.transactionId)
            dao.deleteSplitsByTransactionId(transaction.transactionId)
            loadTripData(transaction.tripId)
        }
    }

    fun getMemberName(userId: Long): String {
        return _uiState.value.members.find { it.userId == userId }?.username ?: "???"
    }

    fun setAddMemberDialogVisibility(visible: Boolean) {
        _uiState.value = _uiState.value.copy(showAddMemberDialog = visible)
    }

    fun addMember(name: String) {
        viewModelScope.launch {
            _trip.value?.let { trip ->
                val user = dao.getUserByName(name) ?: run {
                    val newId = dao.insertUser(User(username = name, passwordHash = ""))
                    User(userId = newId, username = name, passwordHash = "")
                }
                dao.insertTripMember(TripMember(tripId = trip.tripId, userId = user.userId))
                loadTripData(trip.tripId)
            }
            setAddMemberDialogVisibility(false)
        }
    }

    fun settleDebt(fromId: Long, toId: Long, amount: Double, currency: String, rate: Double) {
        viewModelScope.launch {
            _trip.value?.let { trip ->
                val newTx = Transaction(
                    tripId = trip.tripId,
                    payerId = fromId,
                    amount = amount,
                    currency = currency,
                    description = "Spłata długu",
                    category = "Inne",
                    exchangeRate = rate,
                    isRepayment = true
                )
                val txId = dao.insertTransaction(newTx)
                dao.insertTransactionSplit(TransactionSplit(transactionId = txId, beneficiaryId = toId, weight = 1.0))
                loadTripData(trip.tripId)
            }
        }
    }

    fun fetchSettlementRateFromNbp(currency: String) {
        val rate = if (currency == "EUR") 4.3 else 1.0
        _uiState.value = _uiState.value.copy(fetchedSettlementRate = rate)
    }

    fun clearSettlementRate() {
        _uiState.value = _uiState.value.copy(fetchedSettlementRate = null)
    }

    fun removeMember(userId: Long) {
        viewModelScope.launch {
            _trip.value?.let { trip ->
                dao.removeMemberFromTrip(trip.tripId, userId)
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
    val categorySummaries: Map<String, Double> = emptyMap(),
    val currencySummaries: Map<String, Double> = emptyMap(),
    val fetchedSettlementRate: Double? = null
)