package com.example.mojerozliczenia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TripDetailsUiState(
    val trip: Trip? = null,
    val members: List<User> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val totalSpent: Double = 0.0,
    val categorySummaries: Map<String, Double> = emptyMap(),
    val isLoading: Boolean = true,
    val showAddMemberDialog: Boolean = false
)

class TripDetailsViewModel(private val dao: AppDao) : ViewModel() {

    private val _uiState = MutableStateFlow(TripDetailsUiState())
    val uiState = _uiState.asStateFlow()

    private var currentTripId: Long = -1

    fun loadTripData(tripId: Long) {
        currentTripId = tripId
        viewModelScope.launch {
            val trip = dao.getTripById(tripId)
            val members = dao.getTripMembers(tripId)
            val transactions = dao.getTransactionsByTrip(tripId)
            val splits = dao.getSplitsForTrip(tripId)

            val debts = BalanceUtils.calculateDebts(transactions, splits)
            val total = transactions.filter { !it.isRepayment }.sumOf { it.amount * it.exchangeRate }
            val catSum = transactions.filter { !it.isRepayment }
                .groupBy { it.category }
                .mapValues { entry -> entry.value.sumOf { it.amount * it.exchangeRate } }

            _uiState.value = _uiState.value.copy(
                trip = trip,
                members = members,
                transactions = transactions,
                debts = debts,
                totalSpent = total,
                categorySummaries = catSum,
                isLoading = false
            )
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            dao.deleteSplitsByTransactionId(transaction.transactionId)
            dao.deleteTransactionById(transaction.transactionId)
            loadTripData(currentTripId)
        }
    }

    fun setAddMemberDialogVisibility(isVisible: Boolean) {
        _uiState.value = _uiState.value.copy(showAddMemberDialog = isVisible)
    }

    fun addMember(name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return

        viewModelScope.launch {
            var existingUser = dao.getUserByName(cleanName)
            val userIdToAdd: Long = existingUser?.userId ?: dao.insertUser(User(username = cleanName, passwordHash = ""))

            val isAlreadyMember = _uiState.value.members.any { it.userId == userIdToAdd }
            if (!isAlreadyMember) {
                dao.insertTripMember(TripMember(tripId = currentTripId, userId = userIdToAdd))
                loadTripData(currentTripId)
            }
            setAddMemberDialogVisibility(false)
        }
    }

    fun getMemberName(userId: Long): String {
        return _uiState.value.members.find { it.userId == userId }?.username ?: "Nieznany"
    }

    fun settleDebt(fromUserId: Long, toUserId: Long, amount: Double, currency: String) {
        viewModelScope.launch {
            val transactionId = dao.insertTransaction(
                Transaction(
                    tripId = currentTripId,
                    payerId = fromUserId,
                    amount = amount,
                    currency = currency,
                    description = "Spłata długu",
                    isRepayment = true,
                    exchangeRate = 1.0
                )
            )
            dao.insertTransactionSplit(TransactionSplit(transactionId = transactionId, beneficiaryId = toUserId, weight = 1.0))
            loadTripData(currentTripId)
        }
    }

    fun prepareExport(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            val trip = state.trip ?: return@launch
            val splits = dao.getSplitsForTrip(trip.tripId)

            val exportedTransactions = state.transactions.map { tx ->
                val txSplits = splits.filter { it.transactionId == tx.transactionId }
                val beneficiaryNames = txSplits.map { split -> getMemberName(split.beneficiaryId) }

                ExportedTransaction(
                    description = tx.description,
                    amount = tx.amount,
                    currency = tx.currency,
                    category = tx.category,
                    exchangeRate = tx.exchangeRate,
                    isRepayment = tx.isRepayment,
                    payerName = getMemberName(tx.payerId),
                    beneficiaryNames = beneficiaryNames
                )
            }

            val exportData = ExportedTrip(
                name = trip.name,
                mainCurrency = trip.mainCurrency,
                members = state.members.map { it.username },
                transactions = exportedTransactions
            )

            val json = ExportUtils.tripToJson(exportData)
            onReady(json)
        }
    }

    fun generateShareReport(): String {
        val state = _uiState.value
        val tripName = state.trip?.name ?: "Wyjazd"
        val debts = state.debts
        val currency = state.trip?.mainCurrency ?: "PLN"

        val sb = StringBuilder()
        sb.append("📊 Rozliczenie wyjazdu: $tripName\n")
        sb.append("-----------------------------\n")

        if (debts.isEmpty()) {
            sb.append("✅ Wszystko rozliczone! Nikt nikomu nic nie wisi.\n")
        } else {
            sb.append("Do oddania:\n")
            debts.forEach { debt ->
                val from = getMemberName(debt.fromUserId)
                val to = getMemberName(debt.toUserId)
                sb.append("🔴 $from ➡️ $to: ${debt.amount} $currency\n")
            }
        }

        sb.append("-----------------------------\n")
        sb.append("Wygenerowano w aplikacji MojeRozliczenia 📱")

        return sb.toString()
    }
}