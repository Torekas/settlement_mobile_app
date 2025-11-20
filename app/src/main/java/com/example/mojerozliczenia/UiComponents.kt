package com.example.mojerozliczenia

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// 1. ANIMOWANY LICZNIK
@Composable
fun AnimatedAmountText(
    targetAmount: Double,
    currency: String,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val animatedValue = remember { Animatable(0f) }

    LaunchedEffect(targetAmount) {
        animatedValue.animateTo(
            targetValue = targetAmount.toFloat(),
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
        )
    }

    Text(
        text = "${String.format(Locale.US, "%.2f", animatedValue.value)} $currency",
        style = style,
        color = color,
        fontWeight = FontWeight.Bold
    )
}

// 2. GRADIENTOWA KARTA
@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(Color(0xFF42A5F5), Color(0xFF1976D2)),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(colors))
                .fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

// 3. POMOCNICZE KOLORY
fun getCategoryGradient(categoryName: String): List<Color> {
    return when (categoryName) {
        "FOOD" -> listOf(Color(0xFFFFCC80), Color(0xFFFB8C00))
        "SHOPPING" -> listOf(Color(0xFF81D4FA), Color(0xFF0288D1))
        "TRANSPORT" -> listOf(Color(0xFFEF9A9A), Color(0xFFD32F2F))
        "ACCOMMODATION" -> listOf(Color(0xFFB39DDB), Color(0xFF512DA8))
        "ENTERTAINMENT" -> listOf(Color(0xFF80CBC4), Color(0xFF00796B))
        else -> listOf(Color(0xFFB0BEC5), Color(0xFF455A64))
    }
}