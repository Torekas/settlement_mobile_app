package com.example.mojerozliczenia.flights

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

const val API_KEY = "api_key"
const val API_SECRET = "api_secret"

class FlightViewModel : ViewModel() {

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

            } catch (e: Exception) {
                _flightState.value = FlightState.Error("Błąd: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}

sealed class FlightState {
    object Idle : FlightState()
    object Loading : FlightState()
    data class Success(val flights: List<FlightOffer>) : FlightState()
    data class Error(val message: String) : FlightState()
}