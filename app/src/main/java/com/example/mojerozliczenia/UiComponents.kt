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