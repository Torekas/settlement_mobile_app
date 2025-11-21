package com.example.mojerozliczenia.flights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FlightUtils {
    // Baza miast
    private val cities = mapOf(
        // Europa
        "warszawa" to "WAW",
        "kraków" to "KRK",
        "gdańsk" to "GDN",
        "wrocław" to "WRO",
        "poznań" to "POZ",
        "katowice" to "KTW",
        "berlin" to "BER",
        "monachium" to "MUC",
        "frankfurt" to "FRA",
        "hamburg" to "HAM",
        "düsseldorf" to "DUS",
        "amsterdam" to "AMS",
        "bruksela" to "BRU",
        "londyn" to "LHR",
        "londyn gatwick" to "LGW",
        "londyn stansted" to "STN",
        "paryż" to "CDG",
        "paryż orly" to "ORY",
        "rzym" to "FCO",
        "mediolan" to "MXP",
        "barcelona" to "BCN",
        "madryt" to "MAD",
        "lizbona" to "LIS",
        "wiedeń" to "VIE",
        "zurych" to "ZRH",
        "genewa" to "GVA",
        "oslo" to "OSL",
        "stockholm" to "ARN",
        "kopenhaga" to "CPH",
        "helsinki" to "HEL",
        "ateny" to "ATH",
        "dublin" to "DUB",
        "praga" to "PRG",
        "budapeszt" to "BUD",
        "zagrzeb" to "ZAG",
        "lublana" to "LJU",

        // Ameryka Północna
        "nowy jork" to "JFK",
        "nowy jork newark" to "EWR",
        "waszyngton" to "IAD",
        "chicago" to "ORD",
        "los angeles" to "LAX",
        "san francisco" to "SFO",
        "miami" to "MIA",
        "boston" to "BOS",
        "toronto" to "YYZ",
        "vancouver" to "YVR",
        "montreal" to "YUL",

        // Azja
        "tokio" to "HND",
        "tokio narita" to "NRT",
        "seul" to "ICN",
        "pekig" to "PEK",
        "szanghaj" to "PVG",
        "hongkong" to "HKG",
        "bangkok" to "BKK",
        "singapur" to "SIN",
        "kuala lumpur" to "KUL",
        "delhi" to "DEL",
        "mumbaj" to "BOM",
        "dubaj" to "DXB",
        "abu zabi" to "AUH",
        "doha" to "DOH",

        // Afryka
        "kair" to "CAI",
        "casablanca" to "CMN",
        "marakesz" to "RAK",
        "nairobi" to "NBO",
        "johannesburg" to "JNB",
        "kapsztad" to "CPT",

        // Ameryka Południowa
        "sao paulo" to "GRU",
        "rio de janeiro" to "GIG",
        "buenos aires" to "EZE",
        "lima" to "LIM",
        "bogota" to "BOG",
        "santiago" to "SCL",

        // Oceania
        "sydney" to "SYD",
        "melbourne" to "MEL",
        "auckland" to "AKL"
    )

    val randomDestinations = listOf("JFK", "HND", "DXB", "BCN", "FCO", "LHR", "CDG", "MIA", "LAX")

    fun getCode(input: String): String {
        val lowerInput = input.trim().lowercase()
        return cities[lowerInput] ?: input.uppercase().trim()
    }

    fun getRandomDestination(): String {
        return randomDestinations.random()
    }

    fun getAirlineLogoUrl(carrierCode: String): String {
        return "https://daisycon.io/images/airline/?width=100&height=50&color=ffffff&iata=$carrierCode"
    }

    fun formatDuration(isoDuration: String): String {
        val clean = isoDuration.removePrefix("PT")
        val hours = clean.substringBefore("H", "0").toIntOrNull() ?: 0
        val minutes = if (clean.contains("H")) {
            clean.substringAfter("H").substringBefore("M", "0").toIntOrNull() ?: 0
        } else {
            clean.substringBefore("M", "0").toIntOrNull() ?: 0
        }
        return "${hours}h ${minutes}m"
    }

    fun extractTime(dateTime: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val outputFormat = SimpleDateFormat("HH:mm", Locale.US)
            val date = inputFormat.parse(dateTime)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateTime
        }
    }

    fun formatDateForApi(millis: Long?): String {
        millis ?: return ""
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return formatter.format(Date(millis))
    }

    @Composable
    fun DashedDivider(
        color: Color = Color.Gray,
        thickness: Dp = 1.dp,
        dashWidth: Dp = 10.dp,
        gapWidth: Dp = 10.dp,
        modifier: Modifier = Modifier
    ) {
        Canvas(modifier = modifier.fillMaxWidth().height(thickness)) {
            drawLine(
                color = color,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = thickness.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashWidth.toPx(), gapWidth.toPx()), 0f)
            )
        }
    }
}