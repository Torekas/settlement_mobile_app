package com.example.mojerozliczenia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.mojerozliczenia.packing.PackingNotificationWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class TripUiState(
    val trips: List<Trip> = emptyList(),
    val isLoading: Boolean = false,
    val showAddDialog: Boolean = false
)

class TripViewModel(private val dao: AppDao) : ViewModel() {

    // To jest pole, którego szuka TripListScreen (Flow bezpośrednio z bazy)
    val allTrips: Flow<List<Trip>> = dao.getAllTrips()

    // Funkcja dodawania wyjazdu dopasowana do TripListScreen
    fun addTrip(context: Context, name: String, dateMillis: Long) {
        if (name.isBlank()) return

        viewModelScope.launch {
            val imageUrl = generateSmartImageUrl(name)

            // Zakładamy, że nazwa wyjazdu to też destination
            val newTrip = Trip(
                name = name,
                destination = name,
                mainCurrency = "PLN", // Domyślna waluta
                imageUrl = imageUrl,
                isImported = false,
                startDate = dateMillis
            )

            val tripId = dao.insertTrip(newTrip)

            // Tutaj używamy contextu przekazanego w parametrze
            scheduleNotification(context, name, dateMillis)

            // Opcjonalnie: dodaj twórcę jako członka, jeśli masz userId
            // dao.insertTripMember(TripMember(tripId = tripId, userId = userId))
        }
    }

    private fun scheduleNotification(context: Context, destination: String, dateMillis: Long) {
        val currentTime = System.currentTimeMillis()
        val notificationTime = dateMillis + 28800000 // 8:00 rano
        val delay = notificationTime - currentTime

        if (delay > 0) {
            val workRequest = OneTimeWorkRequestBuilder<PackingNotificationWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("trip_name" to destination))
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }

    fun deleteTrip(tripId: Long) {
        viewModelScope.launch {
            dao.deleteEntireTrip(tripId)
        }
    }

    // Funkcja importu
    fun importTrip(json: String) {
        viewModelScope.launch {
            val data = ExportUtils.jsonToTrip(json) ?: return@launch
            val imageUrl = generateSmartImageUrl(data.name)

            val newTrip = Trip(
                name = data.name,
                destination = data.name, // Używamy nazwy jako celu
                mainCurrency = data.mainCurrency,
                imageUrl = imageUrl,
                isImported = true,
                startDate = System.currentTimeMillis() // Domyślna data dla importu
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
        }
    }

    // --- GENERATOR AI ---
    private fun generateSmartImageUrl(originalName: String): String {
        val normalized = Normalizer.normalize(originalName, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        val contextKeywords = detectVibe(normalized)
        val prompt = "breathtaking travel photography of place named $normalized, $contextKeywords, iconic landmark, cinematic lighting, highly detailed, 8k, national geographic style, no text"
        val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
        val seed = originalName.hashCode()
        return "https://image.pollinations.ai/prompt/$encodedPrompt?width=800&height=500&nologo=true&seed=$seed&model=flux"
    }

    private fun detectVibe(name: String): String {
        val lower = name.lowercase(Locale.getDefault())
        return when {
            lower.contains("narty") || lower.contains("gory") || lower.contains("alpy") || lower.contains("zima") || lower.contains("zakopane") -> "snowy mountains, winter wonderland, ski resort"
            lower.contains("lato") || lower.contains("plaza") || lower.contains("morze") || lower.contains("wakacje") || lower.contains("wlochy") -> "sunny beach, turquoise ocean, summer vibes"
            lower.contains("londyn") || lower.contains("paryz") || lower.contains("nowy jork") || lower.contains("warszawa") -> "bustling city street, architecture, urban"
            lower.contains("mazury") || lower.contains("jezioro") || lower.contains("las") -> "lake view, forest, nature, calm water"
            else -> "scenic landscape, tourist attraction"
        }
    }
}