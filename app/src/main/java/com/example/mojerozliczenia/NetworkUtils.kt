package com.example.mojerozliczenia

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object NetworkUtils {

    suspend fun fetchNbpRate(currencyCode: String): Double? {
        return withContext(Dispatchers.IO) {
            try {
                // ZMIANA: Używamy HTTPS zamiast HTTP
                val url = "https://api.nbp.pl/api/exchangerates/rates/a/${currencyCode.lowercase()}/?format=json"

                // Dodajemy logowanie, żebyś widział w Logcat co się dzieje
                Log.d("NBP_API", "Pobieram z: $url")

                val jsonString = URL(url).readText()
                Log.d("NBP_API", "Odpowiedź: $jsonString")

                val jsonObject = JSONObject(jsonString)
                val ratesArray = jsonObject.getJSONArray("rates")
                val rateObject = ratesArray.getJSONObject(0)

                rateObject.getDouble("mid")
            } catch (e: Exception) {
                // Wypisz błąd w konsoli (Logcat), żeby wiedzieć co poszło nie tak
                Log.e("NBP_API", "Błąd pobierania: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }
}