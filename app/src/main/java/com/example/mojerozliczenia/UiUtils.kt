package com.example.mojerozliczenia

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.abs

// --- 1. ENUM KATEGORII (Poprawiony: dodano pole label) ---
enum class ExpenseCategory(val label: String, val icon: ImageVector) {
    FOOD("Jedzenie", Icons.Default.Restaurant),
    SHOPPING("Zakupy", Icons.Default.ShoppingCart),
    TRANSPORT("Transport", Icons.Default.DirectionsCar),
    ACCOMMODATION("Nocleg", Icons.Default.Hotel),
    ENTERTAINMENT("Rozrywka", Icons.Default.Movie),
    OTHER("Inne", Icons.Default.MiscellaneousServices);

    // Pomocnicza metoda do szukania po nazwie (ignoruje wielkość liter)
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
data class Debt(
    val fromUserId: Long,
    val toUserId: Long,
    val amount: Double
)

// --- 3. LOGO UTILS ---
object LogoUtils {
    fun getLogoUrl(description: String): String? {
        return null
    }
}

// --- 4. FUNKCJE POMOCNICZE UI ---

fun getCategoryIcon(categoryName: String): ImageVector {
    return ExpenseCategory.fromString(categoryName).icon
}

fun getCategoryGradient(categoryName: String): List<Color> {
    // Używamy bezpiecznego mapowania z Enuma
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