package com.example.mojerozliczenia

import com.google.gson.Gson

// Struktura danych do eksportu (DTO - Data Transfer Object)
// Nie używamy ID, tylko nazw (String), bo na innym telefonie ID będą inne.

data class ExportedTrip(
    val name: String,
    val mainCurrency: String,
    val members: List<String>, // Lista imion
    val transactions: List<ExportedTransaction>
)

data class ExportedTransaction(
    val description: String,
    val amount: Double,
    val currency: String,
    val category: String,
    val exchangeRate: Double,
    val isRepayment: Boolean,
    val payerName: String, // Kto płacił (imię)
    val beneficiaryNames: List<String> // Kto korzystał (imiona)
)

object ExportUtils {
    private val gson = Gson()

    fun tripToJson(trip: ExportedTrip): String {
        return gson.toJson(trip)
    }

    fun jsonToTrip(json: String): ExportedTrip? {
        return try {
            gson.fromJson(json, ExportedTrip::class.java)
        } catch (e: Exception) {
            null
        }
    }
}