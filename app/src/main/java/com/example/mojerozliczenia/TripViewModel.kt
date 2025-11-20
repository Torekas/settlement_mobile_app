package com.example.mojerozliczenia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TripUiState(
    val trips: List<Trip> = emptyList(),
    val isLoading: Boolean = false,
    val showAddDialog: Boolean = false
)

class TripViewModel(private val dao: AppDao) : ViewModel() {

    private val _uiState = MutableStateFlow(TripUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadTrips()
    }

    fun loadTrips() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val tripsList = dao.getAllTrips()
            _uiState.value = _uiState.value.copy(trips = tripsList, isLoading = false)
        }
    }

    fun setDialogVisibility(isVisible: Boolean) {
        _uiState.value = _uiState.value.copy(showAddDialog = isVisible)
    }

    fun addNewTrip(name: String, currency: String, creatorId: Long) {
        if (name.isBlank()) return

        viewModelScope.launch {
            val newTrip = Trip(name = name, mainCurrency = currency)
            val tripId = dao.insertTrip(newTrip)
            dao.insertTripMember(TripMember(tripId = tripId, userId = creatorId))
            loadTrips()
            setDialogVisibility(false)
        }
    }

    // --- NOWA FUNKCJA ---
    fun deleteTrip(tripId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            dao.deleteEntireTrip(tripId)
            loadTrips() // Odśwież listę po usunięciu
        }
    }

    // Funkcja importu (zachowujemy ją)
    fun importTrip(json: String) {
        viewModelScope.launch {
            val data = ExportUtils.jsonToTrip(json) ?: return@launch
            val newTrip = Trip(name = "${data.name} (Import)", mainCurrency = data.mainCurrency)
            val newTripId = dao.insertTrip(newTrip)
            val userMap = mutableMapOf<String, Long>()

            data.members.forEach { name ->
                val existingUser = dao.getUserByName(name)
                val userId = existingUser?.userId ?: dao.insertUser(User(username = name, passwordHash = ""))
                userMap[name] = userId
                dao.insertTripMember(TripMember(tripId = newTripId, userId = userId))
            }

            data.transactions.forEach { txData ->
                val payerId = userMap[txData.payerName] ?: return@forEach
                val newTx = Transaction(
                    tripId = newTripId,
                    payerId = payerId,
                    amount = txData.amount,
                    currency = txData.currency,
                    description = txData.description,
                    category = txData.category,
                    exchangeRate = txData.exchangeRate,
                    isRepayment = txData.isRepayment
                )
                val newTxId = dao.insertTransaction(newTx)

                txData.beneficiaryNames.forEach { benName ->
                    val benId = userMap[benName]
                    if (benId != null) {
                        dao.insertTransactionSplit(TransactionSplit(transactionId = newTxId, beneficiaryId = benId, weight = 1.0))
                    }
                }
            }
            loadTrips()
        }
    }
}