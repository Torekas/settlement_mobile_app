package com.example.mojerozliczenia

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale
import kotlin.math.abs

// --- 1. ENUM KATEGORII ---
enum class ExpenseCategory(val label: String, val icon: ImageVector) {
    FOOD("Jedzenie", Icons.Default.Restaurant),
    SHOPPING("Zakupy", Icons.Default.ShoppingCart),
    TRANSPORT("Transport", Icons.Default.DirectionsCar),
    ACCOMMODATION("Nocleg", Icons.Default.Hotel),
    ENTERTAINMENT("Rozrywka", Icons.Default.Movie),
    OTHER("Inne", Icons.Default.MiscellaneousServices);

    companion object {
        fun fromString(name: String): ExpenseCategory {
            return try {
                valueOf(name.uppercase())
            } catch (e: Exception) {
                OTHER
            }
        }
    }
}

// --- 2. KLASA DEBT ---
// --- 2. KLASA DEBT ---
data class Debt(
    val fromUserId: Long,
    val toUserId: Long,
    val amount: Double,
    val currency: String // <--- DODAJ TO POLE
)

// --- 3. LOGO UTILS (TERAZ Z LOGIKĄ POBIERANIA IKON) ---
object LogoUtils {
    fun getLogoUrl(description: String): String? {
        val lower = description.lowercase(Locale.getDefault())

        // Prosta heurystyka mapująca słowa kluczowe na domeny
        val domain = when {
            // Linie lotnicze
            lower.contains("lot") && !lower.contains("loteria") -> "lot.com"
            lower.contains("ryanair") -> "ryanair.com"
            lower.contains("wizz") -> "wizzair.com"
            lower.contains("lufthansa") -> "lufthansa.com"
            lower.contains("easyjet") -> "easyjet.com"
            lower.contains("klm") -> "klm.com"
            lower.contains("emirates") -> "emirates.com"

            // Transport miejski / Taxi
            lower.contains("uber") -> "uber.com"
            lower.contains("bolt") -> "bolt.eu"
            lower.contains("free now") || lower.contains("freenow") -> "free-now.com"
            lower.contains("jakdojade") -> "jakdojade.pl"

            // Kolej / Autobusy
            lower.contains("pkp") || lower.contains("intercity") -> "intercity.pl"
            lower.contains("flixbus") -> "flixbus.pl"

            // Noclegi
            lower.contains("booking") -> "booking.com"
            lower.contains("airbnb") -> "airbnb.com"
            lower.contains("agoda") -> "agoda.com"
            lower.contains("hotels.com") -> "hotels.com"

            // Sklepy spożywcze
            lower.contains("biedronka") -> "biedronka.pl"
            lower.contains("lidl") -> "lidl.pl"
            lower.contains("żabka") || lower.contains("zabka") -> "zabka.pl"
            lower.contains("auchan") -> "auchan.pl"
            lower.contains("carrefour") -> "carrefour.pl"
            lower.contains("kaufland") -> "kaufland.pl"
            lower.contains("dino") -> "grupadino.pl"

            // Stacje paliw
            lower.contains("orlen") -> "orlen.pl"
            lower.contains("bp") && !lower.contains("pkp") -> "bp.com"
            lower.contains("shell") -> "shell.com"
            lower.contains("circle k") || lower.contains("circlek") -> "circlek.pl"

            // Jedzenie / Kawa
            lower.contains("mcdonald") -> "mcdonalds.com"
            lower.contains("kfc") -> "kfc.pl"
            lower.contains("burger king") -> "burgerking.pl"
            lower.contains("subway") -> "subway.com"
            lower.contains("pizza hut") -> "pizzahut.pl"
            lower.contains("dominos") -> "dominos.pl"
            lower.contains("starbucks") -> "starbucks.com"
            lower.contains("costa") -> "costacoffee.pl"
            lower.contains("green caff") -> "greencaffenero.pl"

            // Inne popularne
            lower.contains("spotify") -> "spotify.com"
            lower.contains("netflix") -> "netflix.com"
            lower.contains("amazon") -> "amazon.com"
            lower.contains("allegro") -> "allegro.pl"
            lower.contains("pyszne") -> "pyszne.pl"
            lower.contains("glovo") -> "glovoapp.com"
            lower.contains("wolt") -> "wolt.com"

            else -> null
        }

        // Zwracamy URL do logo z serwisu Clearbit (darmowe API do logotypów)
        return domain?.let { "https://www.google.com/s2/favicons?sz=128&domain=$it" }
    }
}

// --- 4. FUNKCJE POMOCNICZE UI ---

fun getCategoryIcon(categoryName: String): ImageVector {
    return try {
        ExpenseCategory.valueOf(categoryName.uppercase()).icon
    } catch (e: Exception) {
        Icons.Default.MiscellaneousServices
    }
}

fun getCategoryGradient(categoryName: String): List<Color> {
    return when (ExpenseCategory.fromString(categoryName)) {
        ExpenseCategory.FOOD -> listOf(Color(0xFFFFB74D), Color(0xFFFF9800))
        ExpenseCategory.SHOPPING -> listOf(Color(0xFF64B5F6), Color(0xFF2196F3))
        ExpenseCategory.TRANSPORT -> listOf(Color(0xFFE57373), Color(0xFFF44336))
        ExpenseCategory.ACCOMMODATION -> listOf(Color(0xFF9575CD), Color(0xFF673AB7))
        ExpenseCategory.ENTERTAINMENT -> listOf(Color(0xFF4DB6AC), Color(0xFF009688))
        else -> listOf(Color(0xFF90A4AE), Color(0xFF607D8B))
    }
}

fun getNameColor(name: String): Color {
    if (name.isEmpty()) return Color.Gray
    val hash = name.hashCode()
    val colors = listOf(
        Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8),
        Color(0xFF9575CD), Color(0xFF7986CB), Color(0xFF64B5F6),
        Color(0xFF4FC3F7), Color(0xFF4DD0E1), Color(0xFF4DB6AC),
        Color(0xFF81C784), Color(0xFFAED581), Color(0xFFFFD54F),
        Color(0xFFFFB74D), Color(0xFFFF8A65), Color(0xFFA1887F)
    )
    return colors[abs(hash) % colors.size]
}