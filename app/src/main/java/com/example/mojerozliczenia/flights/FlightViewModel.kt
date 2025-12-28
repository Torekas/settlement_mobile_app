package com.example.mojerozliczenia.flights

import android.app.Application
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.util.Locale

const val API_KEY = "2TJ1vgu6VACjcpqHYEV9XczN0eJGw8sw"
const val API_SECRET = "IidjORTjqSme8n3Z"

private const val FLIGHT_CACHE_PREFS = "flight_cache"
private const val FLIGHT_CACHE_KEY = "recent_searches"
private const val FLIGHT_CACHE_LIMIT = 10

class FlightViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(FLIGHT_CACHE_PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _flightState = mutableStateOf<FlightState>(FlightState.Idle)
    val flightState: State<FlightState> = _flightState

    fun searchForFlights(origin: String, destination: String, date: String) {
        _flightState.value = FlightState.Loading

        viewModelScope.launch {
            try {
                // Krok 1: Pobranie tokena
                val authResponse = RetrofitClient.api.getAccessToken(
                    clientId = API_KEY,
                    clientSecret = API_SECRET
                )
                val bearerToken = "Bearer ${authResponse.accessToken}"

                // Krok 2: Wyszukiwanie
                val flightsResponse = RetrofitClient.api.searchFlights(
                    token = bearerToken,
                    origin = origin,
                    destination = destination,
                    date = date
                )

                _flightState.value = FlightState.Success(flightsResponse.data)
                cacheSearch(origin, destination, date, flightsResponse.data)
            } catch (e: Exception) {
                val cached = getCachedSearch(origin, destination, date)
                if (cached != null) {
                    _flightState.value = FlightState.Success(cached.flights, isCached = true)
                } else {
                    _flightState.value = FlightState.Error(
                        "Failed to fetch flights and no cached results available."
                    )
                }
                e.printStackTrace()
            }
        }
    }

    private fun cacheSearch(
        origin: String,
        destination: String,
        date: String,
        flights: List<FlightOffer>
    ) {
        val entries = loadCache().toMutableList()
        val key = cacheKey(origin, destination, date)
        entries.removeAll { cacheKey(it.origin, it.destination, it.date) == key }
        entries.add(0, CachedFlightSearch(origin, destination, date, System.currentTimeMillis(), flights))
        if (entries.size > FLIGHT_CACHE_LIMIT) {
            entries.subList(FLIGHT_CACHE_LIMIT, entries.size).clear()
        }
        prefs.edit().putString(FLIGHT_CACHE_KEY, gson.toJson(entries)).apply()
    }

    private fun getCachedSearch(origin: String, destination: String, date: String): CachedFlightSearch? {
        val key = cacheKey(origin, destination, date)
        return loadCache().firstOrNull { cacheKey(it.origin, it.destination, it.date) == key }
    }

    private fun loadCache(): List<CachedFlightSearch> {
        val json = prefs.getString(FLIGHT_CACHE_KEY, null).orEmpty()
        if (json.isBlank()) {
            return emptyList()
        }
        return try {
            val type = object : TypeToken<List<CachedFlightSearch>>() {}.type
            gson.fromJson<List<CachedFlightSearch>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun cacheKey(origin: String, destination: String, date: String): String {
        return "${origin.trim().uppercase(Locale.US)}|${destination.trim().uppercase(Locale.US)}|${date.trim()}"
    }
}

sealed class FlightState {
    object Idle : FlightState()
    object Loading : FlightState()
    data class Success(val flights: List<FlightOffer>, val isCached: Boolean = false) : FlightState()
    data class Error(val message: String) : FlightState()
}

data class CachedFlightSearch(
    val origin: String,
    val destination: String,
    val date: String,
    val timestamp: Long,
    val flights: List<FlightOffer>
)
