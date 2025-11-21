package com.example.mojerozliczenia

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mojerozliczenia.packing.PackingDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.mojerozliczenia.ExportUtils

class TripDetailsViewModel(
    private val dao: AppDao,
    private val packingDao: PackingDao
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

            // Obliczenia finansowe
            val totalSpent = transactions.filter { !it.isRepayment }.sumOf { it.amount * it.exchangeRate }

            val debts =  BalanceUtils.calculateDebts(transactions, splits)

            _uiState.value = TripDetailsUiState(
                trip = tripData,
                members = members,
                transactions = transactions,
                totalSpent = totalSpent,
                debts = debts,
                isLoading = false
            )
        }
    }

    // --- FUNKCJA EKSPORTU ---
    fun exportTripToJson(
        context: Context,
        trip: Trip,
        includePacking: Boolean,
        onJsonReady: (String) -> Unit
    ) {
        viewModelScope.launch {
            val members = dao.getTripMembers(trip.tripId)
            val transactions = dao.getTransactionsByTrip(trip.tripId)

            // Przygotowanie transakcji
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

            // Przygotowanie listy pakowania
            var packingListNames: List<String>? = null
            if (includePacking) {
                // Używamy packingDao wstrzykniętego w konstruktorze
                val items = packingDao.getItemsForTripSync(trip.tripId)
                packingListNames = items.map { it.name }
            }

            val exportData = TripExportData(
                name = trip.name,
                mainCurrency = trip.mainCurrency,
                members = members.map { it.username },
                transactions = transactionDataList,
                packingList = packingListNames
            )

            // Generujemy JSON
            val json = ExportUtils.tripToJson(exportData)

            // Przekazujemy go z powrotem do widoku
            onJsonReady(json)
        }
    }

    fun removeMember(userId: Long) {
        viewModelScope.launch {
            _trip.value?.let { trip ->
                dao.removeMemberFromTrip(trip.tripId, userId)
                loadTripData(trip.tripId)
            }
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

    // Placeholdery dla NBP
    fun fetchSettlementRateFromNbp(currency: String) {
        if (currency.equals("PLN", ignoreCase = true) || currency == _uiState.value.trip?.mainCurrency) return

        // Ustawiamy loading, żeby kręciołek w dialogu działał
        val previousLoading = _uiState.value.isLoading
        _uiState.value = _uiState.value.copy(isLoading = true)

        viewModelScope.launch {
            val rate = NetworkUtils.fetchNbpRate(currency)
            _uiState.value = _uiState.value.copy(
                isLoading = previousLoading,
                fetchedSettlementRate = rate
            )
        }
    }

    fun clearSettlementRate() {
        _uiState.value = _uiState.value.copy(fetchedSettlementRate = null)
    }

}

// Struktury danych dla UI
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