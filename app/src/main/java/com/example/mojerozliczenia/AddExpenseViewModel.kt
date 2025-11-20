package com.example.mojerozliczenia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class AddExpenseUiState(
    val members: List<User> = emptyList(),
    val payerId: Long = -1,
    val selectedMembers: Set<Long> = emptySet(),
    val isLoading: Boolean = false,
    val fetchedRate: Double? = null,
    val suggestedPayerId: Long = -1,
    val splitItems: List<SplitItemUi> = emptyList(),
    // NOWOŚĆ: Kategoria jest teraz w stanie ViewModelu (domyślnie FOOD)
    val selectedCategory: String = "FOOD"
)

data class SplitItemUi(
    val id: String,
    val name: String,
    val amount: Double,
    val assignedUserIds: Set<Long>
)

class AddExpenseViewModel(private val dao: AppDao) : ViewModel() {

    private val _uiState = MutableStateFlow(AddExpenseUiState())
    val uiState = _uiState.asStateFlow()

    private var tripId: Long = -1
    var mainCurrency = "PLN"

    // --- SŁOWNIK INTELIGENTNEJ KATEGORYZACJI ---
    private val categoryKeywords = mapOf(
        "FOOD" to listOf("PIZZA", "BURGER", "KEBAB", "OBIAD", "KOLACJA", "KAWA", "LUNCH", "RESTAURACJA", "BAR", "JEDZENIE", "MCDONALD", "KFC", "SUSHI", "LODY", "BIEDRONKA", "LIDL", "ZABKA", "ŻABKA", "CARREFOUR", "AUCHAN", "DINO"),
        "TRANSPORT" to listOf("UBER", "BOLT", "TAXI", "TAKSÓWKA", "PALIWO", "BENZYNA", "ORLEN", "BP", "SHELL", "CIRCLE", "BILET", "PKP", "POCIĄG", "AUTOBUS", "PARKING", "AUTOSTRADA"),
        "SHOPPING" to listOf("ZARA", "HM", "H&M", "CCC", "MEDIA", "RTV", "ROSSMANN", "HEBE", "APTEKA", "ZAKUPY", "PREZENT", "PEPCO", "ACTION"),
        "ENTERTAINMENT" to listOf("KINO", "CINEMA", "FILM", "NETFLIX", "TEATR", "MUZEUM", "BILET WSTĘPU", "AQUAPARK", "KRĘGLE", "BILARD", "IMPREZA", "ALKOHOL", "PIWO", "WÓDKA", "DRINK", "BAR"),
        "ACCOMMODATION" to listOf("HOTEL", "HOSTEL", "AIRBNB", "BOOKING", "NOCLEG", "POKÓJ", "APARTAMENT")
    )

    fun loadMembers(tripId: Long) {
        this.tripId = tripId
        viewModelScope.launch {
            val trip = dao.getTripById(tripId)
            mainCurrency = trip.mainCurrency
            val members = dao.getTripMembers(tripId)
            val transactions = dao.getTransactionsByTrip(tripId)
            val splits = dao.getSplitsForTrip(tripId)
            val balances = BalanceUtils.calculateBalances(transactions, splits)
            val suggestedId = balances.minByOrNull { it.value }?.key ?: members.firstOrNull()?.userId ?: -1

            _uiState.value = AddExpenseUiState(
                members = members,
                payerId = suggestedId,
                suggestedPayerId = suggestedId,
                selectedMembers = members.map { it.userId }.toSet()
            )
        }
    }

    // --- NOWA FUNKCJA: ANALIZA TEKSTU ---
    fun analyzeTitleAndCategorize(title: String) {
        val cleanTitle = title.uppercase(Locale.getDefault())

        // Sprawdzamy każdą kategorię
        for ((category, keywords) in categoryKeywords) {
            if (keywords.any { cleanTitle.contains(it) }) {
                // Znaleziono słowo kluczowe! Zmieniamy kategorię
                selectCategory(category)
                return // Kończymy po pierwszym trafieniu
            }
        }
    }

    fun selectCategory(category: String) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    // --- RESZTA FUNKCJI BEZ ZMIAN ---

    fun fetchRateFromNbp(currency: String) {
        if (currency.equals("PLN", ignoreCase = true)) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val rate = NetworkUtils.fetchNbpRate(currency)
            _uiState.value = _uiState.value.copy(isLoading = false, fetchedRate = rate)
        }
    }

    fun clearFetchedRate() {
        _uiState.value = _uiState.value.copy(fetchedRate = null)
    }

    fun selectPayer(userId: Long) {
        _uiState.value = _uiState.value.copy(payerId = userId)
    }

    fun toggleMemberSelection(userId: Long) {
        val current = _uiState.value.selectedMembers.toMutableSet()
        if (current.contains(userId)) current.remove(userId) else current.add(userId)
        _uiState.value = _uiState.value.copy(selectedMembers = current)
    }

    // Funkcje do OCR (jeśli używasz wersji detailed - zostaw, jeśli simple - usuń)
    // Tutaj zakładam wersję simple (skoro cofnęliśmy), ale zostawiam puste dla kompatybilności
    // Jeśli masz wersję detailed, wklej tu kod z poprzedniego kroku.

    fun saveExpense(title: String, amount: Double, currency: String, category: String, exchangeRate: Double, onFinished: () -> Unit) {
        val state = _uiState.value
        if (state.payerId == -1L || state.selectedMembers.isEmpty()) return

        viewModelScope.launch {
            val transaction = Transaction(
                tripId = tripId,
                payerId = state.payerId,
                amount = amount,
                currency = currency,
                description = title,
                category = category,
                exchangeRate = exchangeRate
            )
            val transactionId = dao.insertTransaction(transaction)

            state.selectedMembers.forEach { beneficiaryId ->
                dao.insertTransactionSplit(
                    TransactionSplit(transactionId = transactionId, beneficiaryId = beneficiaryId, weight = 1.0)
                )
            }
            onFinished()
        }
    }
}