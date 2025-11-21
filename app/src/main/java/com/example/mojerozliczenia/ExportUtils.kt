package com.example.mojerozliczenia

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader

data class TripExportData(
    val name: String,
    val mainCurrency: String,
    val members: List<String>,
    val transactions: List<TransactionExportData>,
    val packingList: List<String>? = null,
    // NOWE POLE: Lista zdarzeń z planera
    val plannerEvents: List<PlannerEventExportData>? = null
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

data class PlannerEventExportData(
    val title: String,
    val description: String,
    val timeInMillis: Long,
    val locationName: String,
    val isDone: Boolean
)
// -------------------------------------

object ExportUtils {

    fun tripToJson(data: TripExportData): String {
        return Gson().toJson(data)
    }

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