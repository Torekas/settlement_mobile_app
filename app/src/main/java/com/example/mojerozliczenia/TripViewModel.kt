package com.example.mojerozliczenia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

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
            // Generujemy ulepszony URL do obrazka
            val imageUrl = generateSmartImageUrl(name)

            val newTrip = Trip(
                name = name,
                mainCurrency = currency,
                imageUrl = imageUrl,
                isImported = false
            )

            val tripId = dao.insertTrip(newTrip)
            dao.insertTripMember(TripMember(tripId = tripId, userId = creatorId))
            loadTrips()
            setDialogVisibility(false)
        }
    }

    // --- NOWA, INTELIGENTNA LOGIKA GENEROWANIA OBRAZKÓW ---
    private fun generateSmartImageUrl(originalName: String): String {
        // 1. Normalizacja (usuwanie polskich znaków)
        val normalized = Normalizer.normalize(originalName, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

        // 2. Wykrywanie "klimatu" (Vibe Check)
        val contextKeywords = detectVibe(normalized)

        // 3. Budujemy potężny prompt
        // Konstrukcja: "Travel photography of [NAZWA], [SŁOWA KLUCZOWE], [STYL]"
        val prompt = "breathtaking travel photography of place named $normalized, $contextKeywords, iconic landmark, cinematic lighting, highly detailed, 8k, national geographic style, no text"

        // 4. Kodujemy URL
        val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")

        // 5. Seed oparty na nazwie (żeby "Laponia" zawsze wyglądała tak samo dla tej samej nazwy)
        // Dzięki temu unikamy losowania dziwnych rzeczy przy każdym przeładowaniu
        val seed = originalName.hashCode()

        // Używamy modelu z parametrami
        return "https://image.pollinations.ai/prompt/$encodedPrompt?width=800&height=500&nologo=true&seed=$seed&model=flux"
    }

    // Prosta heurystyka do dodawania słów kluczowych
    private fun detectVibe(name: String): String {
        val lower = name.lowercase(Locale.getDefault())
        return when {
            // Zima / Góry
            lower.contains("narty") || lower.contains("gory") || lower.contains("alpy") ||
                    lower.contains("zima") || lower.contains("ferie") || lower.contains("laponia") ||
                    lower.contains("finlandia") || lower.contains("zakopane") || lower.contains("tatry") ->
                "snowy mountains, winter wonderland, cold, ski resort"

            // Lato / Plaża / Ciepłe kraje
            lower.contains("lato") || lower.contains("plaza") || lower.contains("morze") ||
                    lower.contains("wakacje") || lower.contains("grecja") || lower.contains("hiszpania") ||
                    lower.contains("wlochy") || lower.contains("chorwacja") || lower.contains("baltyk") ->
                "sunny beach, turquoise ocean, summer vibes, warm"

            // Miasto / City break
            lower.contains("londyn") || lower.contains("paryz") || lower.contains("nowy jork") ||
                    lower.contains("warszawa") || lower.contains("krakow") || lower.contains("lodz") ||
                    lower.contains("berlin") || lower.contains("praga") ->
                "bustling city street, architecture, urban, downtown"

            // Natura / Mazury
            lower.contains("mazury") || lower.contains("jezioro") || lower.contains("las") ||
                    lower.contains("kajaki") || lower.contains("biwak") ->
                "lake view, forest, nature, calm water, camping"

            else -> "scenic landscape, tourist attraction" // Domyślnie
        }
    }

    // Funkcja importu (też używa nowego generatora)
    fun importTrip(json: String) {
        viewModelScope.launch {
            val data = ExportUtils.jsonToTrip(json) ?: return@launch

            // Jeśli importowany wyjazd nie ma zdjęcia w JSON (lub chcemy je odświeżyć), generujemy nowe
            val imageUrl = generateSmartImageUrl(data.name)

            val newTrip = Trip(
                name = data.name,
                mainCurrency = data.mainCurrency,
                imageUrl = imageUrl,
                isImported = true
            )
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

    fun deleteTrip(tripId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            dao.deleteEntireTrip(tripId)
            loadTrips()
        }
    }
}