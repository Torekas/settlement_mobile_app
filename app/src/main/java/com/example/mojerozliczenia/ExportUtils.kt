package com.example.mojerozliczenia

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader

// --- KLASY DANYCH (Muszą pasować do tych w ViewModelu) ---
data class TripExportData(
    val name: String,
    val mainCurrency: String,
    val members: List<String>,
    val transactions: List<TransactionExportData>,
    val packingList: List<String>? = null
)

data class TransactionExportData(
    val payerName: String,
    val amount: Double,
    val currency: String,
    val description: String,
    val category: String,
    val exchangeRate: Double,
    val isRepayment: Boolean,
    val beneficiaryNames: List<String>
)
// ---------------------------------------------------------

object ExportUtils {

    // Funkcja zamienia obiekt TripExportData na String JSON
    fun tripToJson(data: TripExportData): String {
        return Gson().toJson(data)
    }

    // Funkcja zamienia String JSON na obiekt TripExportData
    fun jsonToTrip(json: String): TripExportData? {
        return try {
            val type = object : TypeToken<TripExportData>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveJsonToStorage(context: Context, json: String, fileName: String) {
        try {
            context.openFileOutput(fileName, Context.MODE_PRIVATE).use {
                it.write(json.toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Odczyt z URI (do importu)
    fun readJsonFromUri(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
            }
            inputStream?.close()
            sb.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}