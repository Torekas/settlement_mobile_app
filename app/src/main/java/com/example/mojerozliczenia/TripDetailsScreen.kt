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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.util.Locale
import kotlin.math.abs

// --- GŁÓWNY EKRAN ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailsScreen(
    tripId: Long,
    viewModel: TripDetailsViewModel,
    onBack: () -> Unit,
    onPackingClick: () -> Unit,
    onAddExpenseClick: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(tripId) { viewModel.loadTripData(tripId) }

    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettleDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var selectedDebt by remember { mutableStateOf<Debt?>(null) }

    // Stan do usuwania członka ekipy
    var memberToRemove by remember { mutableStateOf<User?>(null) }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val tabs = listOf("Pulpit", "Historia", "Rozliczenie")

    var jsonContentToSave by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(jsonContentToSave.toByteArray())
                }
                Toast.makeText(context, "Zapisano pomyślnie!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Błąd zapisu: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(state.trip?.name ?: "Ładowanie...") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showExportDialog = true }) {
                            Icon(Icons.Default.Save, contentDescription = "Zapisz do pliku")
                        }

                        IconButton(onClick = onPackingClick) {
                            Icon(
                                imageVector = Icons.Filled.Luggage,
                                contentDescription = "Lista Pakowania",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

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
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
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
        if (state.isLoading && !showSettleDialog) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { 50 })
            ) {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(16.dp)
                        .fillMaxSize()
                ) {
                    when (selectedTab) {
                        0 -> { // --- PULPIT ---
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                GradientCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    colors = listOf(Color(0xFF6200EA), Color(0xFFB388FF))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(24.dp)
                                            .fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "Łączne wydatki",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        AnimatedAmountText(
                                            targetAmount = state.totalSpent,
                                            currency = state.trip?.mainCurrency ?: "PLN",
                                            style = MaterialTheme.typography.displayMedium,
                                            color = Color.White
                                        )
                                    }
                                }

                                if (state.currencySummaries.isNotEmpty()) {
                                    LazyRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 24.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        items(state.currencySummaries.toList()) { (curr, amount) ->
                                            AssistChip(
                                                onClick = {},
                                                label = {
                                                    Text(
                                                        text = "${String.format(Locale.US, "%.2f", amount)} $curr",
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                },
                                                modifier = Modifier.padding(horizontal = 4.dp),
                                                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Ekipa",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    TextButton(onClick = { viewModel.setAddMemberDialogVisibility(true) }) {
                                        Icon(Icons.Default.PersonAdd, null); Text("Dodaj")
                                    }
                                }

                                // --- LISTA EKIPY Z USUWANIEM ---
                                LazyRow(modifier = Modifier.padding(bottom = 24.dp)) {
                                    items(state.members) { user ->
                                        Box(modifier = Modifier.padding(end = 12.dp)) {
                                            UserAvatar(
                                                name = user.username,
                                                modifier = Modifier.clickable {
                                                    // Kliknięcie w awatar ustawia osobę do usunięcia i otwiera dialog
                                                    memberToRemove = user
                                                }
                                            )

                                            // Mały minusik przy awatarze (opcjonalnie, dla jasności)
                                            Icon(
                                                imageVector = Icons.Default.RemoveCircle,
                                                contentDescription = "Usuń",
                                                tint = Color.Red.copy(alpha = 0.7f),
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .align(Alignment.TopEnd)
                                            )
                                        }
                                    }
                                }
                                // -------------------------------

                                if (state.categorySummaries.isNotEmpty()) {
                                    Text(
                                        "Struktura wydatków",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        elevation = CardDefaults.cardElevation(2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(180.dp)
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier.weight(1f),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                PieChart(
                                                    data = state.categorySummaries,
                                                    modifier = Modifier.size(140.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                state.categorySummaries.forEach { (cat, sum) ->
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(vertical = 4.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(12.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                    Brush.linearGradient(
                                                                        getCategoryGradient(cat)
                                                                    )
                                                                )
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                        Column {
                                                            val polishName = try {
                                                                ExpenseCategory.fromString(cat).label
                                                            } catch (e: Exception) {
                                                                cat
                                                            }
                                                            Text(
                                                                text = polishName,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Text(
                                                                "${
                                                                    String.format(
                                                                        Locale.US,
                                                                        "%.0f",
                                                                        sum
                                                                    )
                                                                } ${state.trip?.mainCurrency}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = Color.Gray
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Dodaj wydatki, aby zobaczyć wykres 📊",
                                            color = Color.Gray
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                        }
                        1 -> { // --- HISTORIA ---
                            if (state.transactions.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) { Text("Brak transakcji.", color = Color.Gray) }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 80.dp)
                                ) {
                                    items(state.transactions) { transaction ->
                                        TransactionItem(
                                            transaction,
                                            viewModel.getMemberName(transaction.payerId),
                                            { viewModel.deleteTransaction(transaction) },
                                            state.trip?.mainCurrency ?: "PLN"
                                        )
                                    }
                                }
                            }
                        }
                        2 -> { // --- ROZLICZENIE ---
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item {
                                    Text(
                                        "Plan spłat",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Kto komu ile wisi",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                if (state.debts.isEmpty()) {
                                    item {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 64.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF81C784),
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

        // --- DIALOG POTWIERDZENIA USUNIĘCIA CZŁONKA ---
        if (memberToRemove != null) {
            AlertDialog(
                onDismissRequest = { memberToRemove = null },
                title = { Text("Usunąć uczestnika?") },
                text = { Text("Czy na pewno chcesz usunąć ${memberToRemove?.username} z wyjazdu?") },
                confirmButton = {
                    Button(
                        onClick = {
                            memberToRemove?.let { user ->
                                viewModel.removeMember(user.userId)
                            }
                            memberToRemove = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Usuń")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { memberToRemove = null }) {
                        Text("Anuluj")
                    }
                }
            )
        }

        // --- POZOSTAŁE DIALOGI ---
        if (showExportDialog && state.trip != null) {
            val trip = state.trip!!
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = { Text("Eksport wyjazdu") },
                text = {
                    Text("Czy chcesz dołączyć do pliku listę rzeczy do spakowania?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.exportTripToJson(context, trip, includePacking = true) { json ->
                                jsonContentToSave = json
                                val filename = "trip_${trip.name.replace(" ", "_")}.json"
                                exportLauncher.launch(filename)
                            }
                            showExportDialog = false
                        }
                    ) {
                        Text("Tak, z listą")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { showExportDialog = false }) { Text("Anuluj") }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                viewModel.exportTripToJson(context, trip, includePacking = false) { json ->
                                    jsonContentToSave = json
                                    val filename = "trip_${trip.name.replace(" ", "_")}.json"
                                    exportLauncher.launch(filename)
                                }
                                showExportDialog = false
                            }
                        ) {
                            Text("Tylko finanse")
                        }
                    }
                }
            )
        }

        if (showSettleDialog && selectedDebt != null) {
            SettleDebtDialog(
                debt = selectedDebt!!,
                fromName = viewModel.getMemberName(selectedDebt!!.fromUserId),
                toName = viewModel.getMemberName(selectedDebt!!.toUserId),
                mainCurrency = state.trip?.mainCurrency ?: "PLN",
                onDismiss = { showSettleDialog = false; viewModel.clearSettlementRate() },
                onFetchRate = { curr -> viewModel.fetchSettlementRateFromNbp(curr) },
                fetchedRate = state.fetchedSettlementRate,
                onConfirm = { amount, currency, rate ->
                    viewModel.settleDebt(
                        selectedDebt!!.fromUserId,
                        selectedDebt!!.toUserId,
                        amount,
                        currency,
                        rate
                    )
                    showSettleDialog = false
                    viewModel.clearSettlementRate()
                }
            )
        }

        if (state.showAddMemberDialog) {
            AddMemberDialog(
                onDismiss = { viewModel.setAddMemberDialogVisibility(false) },
                onConfirm = { viewModel.addMember(it) })
        }
    }
}


@Composable
fun SettleDebtDialog(
    debt: Debt,
    fromName: String,
    toName: String,
    mainCurrency: String,
    onDismiss: () -> Unit,
    onFetchRate: (String) -> Unit,
    fetchedRate: Double?,
    onConfirm: (Double, String, Double) -> Unit
) {
    var amountString by remember { mutableStateOf(debt.amount.toString()) }
    var currency by remember { mutableStateOf(mainCurrency) }
    var rateString by remember { mutableStateOf("1.0") }

    LaunchedEffect(fetchedRate) {
        if (fetchedRate != null) rateString = fetchedRate.toString()
    }

    val inputAmount = amountString.toDoubleOrNull() ?: 0.0
    val inputRate = rateString.toDoubleOrNull() ?: 1.0
    val amountInMainCurrency = inputAmount * inputRate
    val difference = amountInMainCurrency - debt.amount
    val showRateField = currency.uppercase() != mainCurrency

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Spłata długu") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("$fromName oddaje pieniądze dla $toName", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = amountString,
                        onValueChange = { amountString = it },
                        label = { Text("Kwota") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it.uppercase() },
                        label = { Text("Waluta") },
                        modifier = Modifier.width(90.dp)
                    )
                }
                if (showRateField) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = rateString,
                            onValueChange = { rateString = it },
                            label = { Text("Kurs") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconToggleButton(
                            checked = false,
                            onCheckedChange = { onFetchRate(currency) },
                            modifier = Modifier.size(56.dp),
                            colors = IconButtonDefaults.filledIconToggleButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                        ) { Icon(Icons.Default.AutoMode, contentDescription = "Pobierz kurs", modifier = Modifier.size(24.dp)) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (inputAmount > 0) {
                    if (showRateField) {
                        Text("Wartość w $mainCurrency: ${String.format(Locale.US, "%.2f", amountInMainCurrency)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Spacer(Modifier.height(4.dp))
                    }
                    if (difference > 0.01) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("⚠️ Nadpłata!", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                Text("$toName będzie winien $fromName: ${String.format(Locale.US, "%.2f", difference)} $mainCurrency", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else if (difference < -0.01) {
                        Text("Pozostanie do spłaty: ${String.format(Locale.US, "%.2f", abs(difference))} $mainCurrency", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("Spłata całkowita ✅", color = Color(0xFF388E3C), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (inputAmount > 0) onConfirm(inputAmount, currency, inputRate) }) { Text("Zatwierdź") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

@Composable
fun UserAvatar(name: String, modifier: Modifier = Modifier) {
    val color = getNameColor(name)
    val initials = if (name.isNotEmpty()) name.take(1).uppercase() else "?"
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) { Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
        Spacer(Modifier.height(4.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
fun ModernDebtItem(fromName: String, toName: String, amount: Double, currency: String, onSettleClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) { UserAvatar(name = fromName) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("oddaje", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) }
                Row(verticalAlignment = Alignment.CenterVertically) { UserAvatar(name = toName) }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "${String.format(Locale.US, "%.2f", amount)} $currency", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Button(onClick = onSettleClick, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Spłać") }
            }
        }
    }
}

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
            val gradient = try { getCategoryGradient(category) } catch(e: Exception) { listOf(Color.Gray, Color.LightGray) }
            drawArc(brush = Brush.linearGradient(gradient), startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, topLeft = Offset(center.x - radius + strokeWidth/2, center.y - radius + strokeWidth/2), size = Size((radius - strokeWidth/2) * 2, (radius - strokeWidth/2) * 2), style = Stroke(width = strokeWidth))
            startAngle += sweepAngle
        }
    }
}

@Composable
fun TransactionItem(transaction: Transaction, payerName: String, onDelete: () -> Unit, mainCurrency: String) {
    val context = LocalContext.current
    val isRepayment = transaction.isRepayment
    val icon = if (isRepayment) Icons.Default.CurrencyExchange else try { getCategoryIcon(transaction.category) } catch (e: Exception) { Icons.Default.MiscellaneousServices }
    val gradientColors = if (isRepayment) listOf(Color(0xFF66BB6A), Color(0xFF2E7D32)) else try { getCategoryGradient(transaction.category) } catch (e: Exception) { listOf(Color.Gray, Color.LightGray) }
    val logoUrl = if (!isRepayment) try { LogoUtils.getLogoUrl(transaction.description) } catch(e: Exception) { null } else null

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
                if (logoUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(logoUrl).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        error = rememberVectorPainter(icon),
                        placeholder = rememberVectorPainter(icon)
                    )
                } else {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
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