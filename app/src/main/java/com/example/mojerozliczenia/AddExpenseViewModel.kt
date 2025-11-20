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
        "FOOD" to listOf(
            "PIZZA", "BURGER", "KEBAB", "OBIAD", "KOLACJA", "KAWA", "LUNCH", "RESTAURACJA",
            "BAR", "JEDZENIE", "MCDONALD", "KFC", "SUSHI", "LODY", "BIEDRONKA", "LIDL",
            "ZABKA", "ŻABKA", "CARREFOUR", "AUCHAN", "DINO",
            "STOKROTKA", "KAUFLAND", "ALDI", "ŻABKA NANO", "DELIKATESY", "SPOŻYWCZY",
            "FOOD", "GASTRO", "PIEKARNIA", "PĄCZEK", "BISTRO", "LUNCHBAR", "ŻYWNOŚĆ"
        ),

        "TRANSPORT" to listOf(
            "UBER", "BOLT", "TAXI", "TAKSÓWKA", "PALIWO", "BENZYNA", "ORLEN", "BP",
            "SHELL", "CIRCLE", "BILET", "PKP", "POCIĄG", "AUTOBUS", "PARKING",
            "AUTOSTRADA",
            "LOT", "WIZZ", "RYANAIR", "METRO", "TRAMWAJ", "SKM", "MZK", "FLIXBUS",
            "KOLEJE", "MYJNIA", "PARKOMAT"
        ),

        "SHOPPING" to listOf(
            "ZARA", "HM", "H&M", "CCC", "MEDIA", "RTV", "ROSSMANN", "HEBE", "APTEKA",
            "ZAKUPY", "PREZENT", "PEPCO", "ACTION",
            "DOUGLAS", "SEPHORA", "EMPIK", "IKEA", "DECATHLON", "ALLEGRO", "AMAZON",
            "EOBUWIE", "MOHITO", "RESERVED", "SMYK", "KIK", "JYSK", "NEONET"
        ),

        "ENTERTAINMENT" to listOf(
            "KINO", "CINEMA", "FILM", "NETFLIX", "TEATR", "MUZEUM", "BILET WSTĘPU",
            "AQUAPARK", "KRĘGLE", "BILARD", "IMPREZA", "ALKOHOL", "PIWO", "WÓDKA",
            "DRINK", "BAR", "SPOTIFY", "YOUTUBE", "HBO", "VIAPLAY", "CANAL+", "KONCERT", "KLUB",
            "ESCAPE ROOM", "EVENT", "FESTIWAL", "PUB"
        ),

        "ACCOMMODATION" to listOf(
            "HOTEL", "HOSTEL", "AIRBNB", "BOOKING", "NOCLEG", "POKÓJ", "APARTAMENT",
            "MOTEL", "RECEPCJA", "ZAKWATEROWANIE", "RESORT", "CAMPING", "SPA"
        ),

        "OTHER" to listOf(
            "USŁUGA", "SERVICE", "OPŁATA", "PROWIZJA", "PRZELEW", "PRZEKAZ",
            "PŁATNOŚĆ", "SUBSKRYPCJA", "SKLEP", "MARKET", "UNKNOWN",
            "BANK", "MBANK", "ING", "PKO", "SANTANDER", "ALIOR",
            "SERWIS", "NAPRAWA", "ELEKTRONIKA", "KOMIS",
            "USŁUGI", "DOSTAWA", "KURIER", "INPOST", "DPD"
        )
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

    private fun detectCategory(title: String): String {
        val clean = title.uppercase(Locale.getDefault())

        // 1. Najpierw exact contains
        for ((category, keywords) in categoryKeywords) {
            if (keywords.any { clean.contains(it) }) return category
        }

        // 2. Potem tokenizacja
        val tokens = clean.split(" ", "-", "_", "/", ".")
        for ((category, keywords) in categoryKeywords) {
            if (keywords.any { tokens.contains(it) }) return category
        }

        // 3. Prefix matching (np. "MC" → MCDONALD, "CAR" → CARREFOUR)
        for ((category, keywords) in categoryKeywords) {
            if (keywords.any { kw -> clean.startsWith(kw.take(3)) }) return category
        }

        // 4. Fuzzy matching
        for ((category, keywords) in categoryKeywords) {
            if (keywords.any { kw -> kw.length > 4 && clean.contains(kw.take(4)) }) return category
        }

        return "OTHER"
    }

    fun analyzeTitleAndCategorize(title: String) {
        val cleanTitle = title.uppercase(Locale.getDefault())

        // Sprawdzamy każdą kategorię
        for ((category, keywords) in categoryKeywords) {
            if (keywords.any { cleanTitle.contains(it) }) {
                selectCategory(category)
                return
            }
        }
    }

    fun selectCategory(category: String) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

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