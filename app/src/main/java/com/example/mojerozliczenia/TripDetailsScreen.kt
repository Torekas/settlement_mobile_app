package com.example.mojerozliczenia

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// 1. Konfiguracja Kolorów i Ikon
fun getCategoryIcon(categoryName: String): ImageVector {
    return try { ExpenseCategory.valueOf(categoryName).icon } catch (e: Exception) { Icons.Default.MiscellaneousServices }
}

fun getCategoryColor(categoryName: String): Color {
    return when (categoryName) {
        "FOOD" -> Color(0xFFFFB74D)
        "SHOPPING" -> Color(0xFF64B5F6)
        "TRANSPORT" -> Color(0xFFE57373)
        "ACCOMMODATION" -> Color(0xFF9575CD)
        "ENTERTAINMENT" -> Color(0xFF4DB6AC)
        else -> Color(0xFF90A4AE)
    }
}

// Generowanie koloru avatara na podstawie imienia
fun getNameColor(name: String): Color {
    val hash = name.hashCode()
    val colors = listOf(
        Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8),
        Color(0xFF9575CD), Color(0xFF7986CB), Color(0xFF64B5F6),
        Color(0xFF4FC3F7), Color(0xFF4DD0E1), Color(0xFF4DB6AC),
        Color(0xFF81C784), Color(0xFFAED581), Color(0xFFFFD54F),
        Color(0xFFFFB74D), Color(0xFFFF8A65), Color(0xFFA1887F)
    )
    return colors[Math.abs(hash) % colors.size]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailsScreen(tripId: Long, viewModel: TripDetailsViewModel, onBack: () -> Unit, onAddExpenseClick: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(tripId) { viewModel.loadTripData(tripId) }

    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettleDialog by remember { mutableStateOf(false) }
    var selectedDebt by remember { mutableStateOf<Debt?>(null) }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    var jsonToSave by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && jsonToSave.isNotEmpty()) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonToSave.toByteArray())
                }
                Toast.makeText(context, "Zapisano plik pomyślnie!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Błąd zapisu: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val tabs = listOf("Pulpit", "Historia", "Rozliczenie")

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(state.trip?.name ?: "Ładowanie...") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć") } },
                    actions = {
                        IconButton(onClick = {
                            viewModel.prepareExport { json ->
                                jsonToSave = json
                                val filename = "${state.trip?.name?.replace(" ", "_") ?: "wyjazd"}.json"
                                exportLauncher.launch(filename)
                            }
                        }) { Icon(Icons.Default.Save, contentDescription = "Zapisz do pliku") }

                        IconButton(onClick = {
                            val report = viewModel.generateShareReport()
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, report)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Wyślij Raport"))
                        }) { Icon(Icons.Default.Share, contentDescription = "Udostępnij Raport") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                )
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedTab != 2) {
                ExtendedFloatingActionButton(
                    onClick = { onAddExpenseClick() },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Wydatek") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { 50 })
            ) {
                Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {

                    when (selectedTab) {
                        0 -> { // --- PULPIT ---
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                GradientCard(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                                    colors = listOf(Color(0xFF6200EA), Color(0xFFB388FF))
                                ) {
                                    Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Łączne wydatki", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        AnimatedAmountText(
                                            targetAmount = state.totalSpent,
                                            currency = state.trip?.mainCurrency ?: "PLN",
                                            style = MaterialTheme.typography.displayMedium,
                                            color = Color.White
                                        )
                                    }
                                }

                                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Uczestnicy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    TextButton(onClick = { viewModel.setAddMemberDialogVisibility(true) }) { Icon(Icons.Default.PersonAdd, null); Text("Dodaj") }
                                }
                                LazyRow(modifier = Modifier.padding(bottom = 24.dp)) {
                                    items(state.members) { user ->
                                        UserAvatar(name = user.username, modifier = Modifier.padding(end = 8.dp))
                                    }
                                }

                                if (state.categorySummaries.isNotEmpty()) {
                                    Text("Struktura wydatków", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth().height(180.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { PieChart(data = state.categorySummaries, modifier = Modifier.size(140.dp)) }
                                            Column(modifier = Modifier.weight(1f)) {
                                                state.categorySummaries.forEach { (cat, sum) ->
                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Brush.linearGradient(getCategoryGradient(cat))))
                                                        Spacer(Modifier.width(8.dp))
                                                        Column {
                                                            Text(cat, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                                            Text("${String.format(Locale.US, "%.0f", sum)} ${state.trip?.mainCurrency}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { Text("Dodaj wydatki, aby zobaczyć wykres 📊", color = Color.Gray) }
                                }
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                        }
                        1 -> { // --- HISTORIA ---
                            if (state.transactions.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Brak transakcji.", color = Color.Gray) }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                                    items(state.transactions) { transaction ->
                                        TransactionItem(transaction, viewModel.getMemberName(transaction.payerId), { viewModel.deleteTransaction(transaction) }, state.trip?.mainCurrency ?: "PLN")
                                    }
                                }
                            }
                        }
                        2 -> { // --- ROZLICZENIE (NOWY WYGLĄD) ---
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item {
                                    Text("Plan spłat", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    Text("Kto komu ile wisi", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                if (state.debts.isEmpty()) {
                                    item {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF81C784), // Ładny zielony
                                                modifier = Modifier.size(96.dp)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                "Wszystko rozliczone!",
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF388E3C)
                                            )
                                            Text("Nikt nikomu nic nie wisi 🎉", color = Color.Gray)
                                        }
                                    }
                                } else {
                                    items(state.debts) { debt ->
                                        ModernDebtItem(
                                            fromName = viewModel.getMemberName(debt.fromUserId),
                                            toName = viewModel.getMemberName(debt.toUserId),
                                            amount = debt.amount,
                                            currency = state.trip?.mainCurrency ?: "",
                                            onSettleClick = {
                                                selectedDebt = debt
                                                showSettleDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showSettleDialog && selectedDebt != null) {
            SettleDebtDialog(
                debt = selectedDebt!!,
                fromName = viewModel.getMemberName(selectedDebt!!.fromUserId),
                toName = viewModel.getMemberName(selectedDebt!!.toUserId),
                currency = state.trip?.mainCurrency ?: "PLN",
                onDismiss = { showSettleDialog = false },
                onConfirm = { amount -> viewModel.settleDebt(selectedDebt!!.fromUserId, selectedDebt!!.toUserId, amount, state.trip?.mainCurrency ?: "PLN"); showSettleDialog = false }
            )
        }

        if (state.showAddMemberDialog) {
            AddMemberDialog(onDismiss = { viewModel.setAddMemberDialogVisibility(false) }, onConfirm = { viewModel.addMember(it) })
        }
    }
}

// --- NOWY KOMPONENT: AVATAR UŻYTKOWNIKA ---
@Composable
fun UserAvatar(name: String, modifier: Modifier = Modifier) {
    val color = getNameColor(name)
    val initials = if (name.isNotEmpty()) name.take(1).uppercase() else "?"

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

// --- NOWY KOMPONENT: NOWOCZESNA KARTA DŁUGU ---
@Composable
fun ModernDebtItem(fromName: String, toName: String, amount: Double, currency: String, onSettleClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Wiersz przepływu: Avatar -> Strzałka -> Avatar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Dłużnik
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(name = fromName)
                }

                // Strzałka i akcja
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("oddaje", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Wierzyciel
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(name = toName)
                }
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            // Wiersz Kwoty i Przycisku
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${String.format(Locale.US, "%.2f", amount)} $currency",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error // Kolor długu
                )

                Button(
                    onClick = onSettleClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Spłać")
                }
            }
        }
    }
}

// --- POZOSTAŁE KOMPONENTY (Bez zmian) ---

@Composable
fun PieChart(data: Map<String, Double>, modifier: Modifier = Modifier) {
    val total = data.values.sum()
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(data) { animatedProgress.animateTo(1f, animationSpec = tween(durationMillis = 800)) }

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        val strokeWidth = 30.dp.toPx()
        var startAngle = -90f
        data.forEach { (category, amount) ->
            val sweepAngle = (amount.toFloat() / total.toFloat()) * 360f * animatedProgress.value
            drawArc(
                brush = Brush.linearGradient(getCategoryGradient(category)),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius + strokeWidth/2, center.y - radius + strokeWidth/2),
                size = Size((radius - strokeWidth/2) * 2, (radius - strokeWidth/2) * 2),
                style = Stroke(width = strokeWidth)
            )
            startAngle += sweepAngle
        }
    }
}

@Composable
fun SettleDebtDialog(debt: Debt, fromName: String, toName: String, currency: String, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var amountString by remember { mutableStateOf(debt.amount.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Spłata długu") },
        text = { Column { Text("$fromName oddaje pieniądze dla $toName"); Spacer(Modifier.height(8.dp)); OutlinedTextField(value = amountString, onValueChange = { amountString = it }, label = { Text("Kwota ($currency)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) } },
        confirmButton = { Button(onClick = { val amount = amountString.toDoubleOrNull(); if (amount != null && amount > 0) onConfirm(amount) }) { Text("Zatwierdź") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

@Composable
fun TransactionItem(transaction: Transaction, payerName: String, onDelete: () -> Unit, mainCurrency: String) {
    val isRepayment = transaction.isRepayment
    val icon = if (isRepayment) Icons.Default.CurrencyExchange else getCategoryIcon(transaction.category)
    val gradientColors = if (isRepayment) listOf(Color(0xFF66BB6A), Color(0xFF2E7D32)) else getCategoryGradient(transaction.category)

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(gradientColors)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(transaction.description, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(if(isRepayment) "$payerName spłaca" else "$payerName (${transaction.category})", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${String.format(Locale.US, "%.2f", transaction.amount)} ${transaction.currency}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                if (transaction.currency != mainCurrency) {
                    val converted = transaction.amount * transaction.exchangeRate
                    Text("≈ ${String.format(Locale.US, "%.2f", converted)} $mainCurrency", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Usuń", tint = Color.Gray.copy(alpha = 0.5f)) }
        }
    }
}

@Composable
fun AddMemberDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dodaj uczestnika") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Imię") }, singleLine = true) },
        confirmButton = { Button(onClick = { onConfirm(name) }) { Text("Dodaj") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}