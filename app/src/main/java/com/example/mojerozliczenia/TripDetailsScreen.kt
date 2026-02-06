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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.Date
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
    onAddExpenseClick: () -> Unit,
    onPlannerClick: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(tripId) { viewModel.loadTripData(tripId) }

    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettleDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    // Stan widoczności menu opcji (trzy kropki)
    var menuExpanded by remember { mutableStateOf(false) }

    var selectedDebt by remember { mutableStateOf<Debt?>(null) }
    var selectedTransactionForEdit by remember { mutableStateOf<Transaction?>(null) }
    var showEditTransactionDialog by remember { mutableStateOf(false) }
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
                    title = {
                        Text(
                            text = state.trip?.name ?: "Ładowanie...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis // Obcinanie nazwy z "..." jeśli nadal za długa
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                        }
                    },
                    actions = {
                        // --- MENU OPCJI (TRZY KROPKI) ---
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Opcje wyjazdu")
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            // 1. Edycja
                            DropdownMenuItem(
                                text = { Text("Edytuj wyjazd") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = {
                                    showEditDialog = true
                                    menuExpanded = false
                                }
                            )

                            // 2. Planer
                            DropdownMenuItem(
                                text = { Text("Planer podróży") },
                                leadingIcon = { Icon(Icons.Default.Event, null) },
                                onClick = {
                                    onPlannerClick()
                                    menuExpanded = false
                                }
                            )

                            // 3. Lista pakowania
                            DropdownMenuItem(
                                text = { Text("Lista pakowania") },
                                leadingIcon = { Icon(Icons.Default.Luggage, null) }, // Wymaga icons-extended lub użyj np. Checklist
                                onClick = {
                                    onPackingClick()
                                    menuExpanded = false
                                }
                            )

                            HorizontalDivider()

                            // 4. Zapisz do pliku
                            DropdownMenuItem(
                                text = { Text("Zapisz do pliku") },
                                leadingIcon = { Icon(Icons.Default.Save, null) },
                                onClick = {
                                    showExportDialog = true
                                    menuExpanded = false
                                }
                            )

                            // 5. Udostępnij raport
                            DropdownMenuItem(
                                text = { Text("Udostępnij raport") },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                onClick = {
                                    menuExpanded = false
                                    val report = viewModel.generateShareReport()
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, report)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Wyślij Raport"))
                                }
                            )
                        }
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
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {

                                // 1. DATA WYJAZDU
                                if (state.trip != null) {
                                    val dateStr = SimpleDateFormat("dd MMMM yyyy", Locale("pl", "PL")).format(Date(state.trip!!.startDate))

                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.DateRange,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = dateStr,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }

                                // 2. KAFELEK WYDATKÓW
                                GradientCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    colors = listOf(Color(0xFF6200EA), Color(0xFFB388FF))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(vertical = 32.dp, horizontal = 16.dp)
                                            .fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "Łączne wydatki",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White.copy(alpha = 0.9f)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        AnimatedAmountText(
                                            targetAmount = state.totalSpent,
                                            currency = state.trip?.mainCurrency ?: "PLN",
                                            style = MaterialTheme.typography.headlineLarge,
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
                                        "Uczestnicy",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    TextButton(onClick = { viewModel.setAddMemberDialogVisibility(true) }) {
                                        Icon(Icons.Default.PersonAdd, null); Text("Dodaj")
                                    }
                                }

                                LazyRow(modifier = Modifier.padding(bottom = 24.dp).fillMaxWidth()) {
                                    items(state.members) { user ->
                                        Box(modifier = Modifier.padding(end = 12.dp)) {
                                            UserAvatar(
                                                name = user.username,
                                                modifier = Modifier.clickable { memberToRemove = user }
                                            )
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

                                // --- STRUKTURA WYDATKÓW ---
                                if (state.categorySummaries.isNotEmpty()) {
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            "Struktura wydatków",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        elevation = CardDefaults.cardElevation(2.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(20.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Box(
                                                modifier = Modifier.weight(0.8f),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                DonutPieChart(
                                                    data = state.categorySummaries,
                                                    modifier = Modifier.size(130.dp)
                                                )
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text("Suma", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                                    Text("100%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(20.dp))

                                            Column(modifier = Modifier.weight(1.2f)) {
                                                val total = state.categorySummaries.values.sum()
                                                state.categorySummaries.toList().sortedByDescending { it.second }.forEach { (cat, sum) ->
                                                    val percentage = if (total > 0) (sum / total).toFloat() else 0f
                                                    val polishName = try {
                                                        ExpenseCategory.fromString(cat).label
                                                    } catch (e: Exception) { cat }
                                                    val colorList = try { getCategoryGradient(cat) } catch(e: Exception) { listOf(Color.Gray, Color.LightGray) }
                                                    val mainColor = colorList.firstOrNull() ?: Color.Gray

                                                    Column(modifier = Modifier.padding(bottom = 12.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                text = polishName,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Text(
                                                                text = "${(percentage * 100).toInt()}%",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = mainColor
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        LinearProgressIndicator(
                                                            progress = { percentage },
                                                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                                                            color = mainColor,
                                                            trackColor = mainColor.copy(alpha = 0.2f),
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = "${String.format(Locale.US, "%.2f", sum)} ${state.trip?.mainCurrency}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color.Gray
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp)
                                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.PieChart, null, tint = Color.Gray)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                "Dodaj wydatki, aby zobaczyć wykres 📊",
                                                color = Color.Gray,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
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
                                        val beneficiaryNames = state.transactionBeneficiaryNames[transaction.transactionId].orEmpty()
                                        TransactionItem(
                                            transaction = transaction,
                                            payerName = viewModel.getMemberName(transaction.payerId),
                                            beneficiaryNames = beneficiaryNames,
                                            onEdit = {
                                                selectedTransactionForEdit = transaction
                                                showEditTransactionDialog = true
                                            },
                                            onDelete = { viewModel.deleteTransaction(transaction) },
                                            mainCurrency = state.trip?.mainCurrency ?: "PLN"
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

                                val summariesByCurrency = state.settlementSummaries
                                    .groupBy { it.currency.uppercase() }
                                    .toSortedMap()

                                summariesByCurrency.forEach { (currency, summaries) ->
                                    val orderedSummaries = summaries.sortedByDescending { abs(it.netBalance) }
                                    item {
                                        Text(
                                            "Podsumowanie uczestnikow ($currency)",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            elevation = CardDefaults.cardElevation(1.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                                orderedSummaries.forEachIndexed { index, summary ->
                                                    SettlementSummaryRow(
                                                        name = viewModel.getMemberName(summary.userId),
                                                        paidAmount = summary.paidAmount,
                                                        toPay = summary.toPay,
                                                        toRecover = summary.toRecover,
                                                        currency = currency
                                                    )
                                                    if (index != orderedSummaries.lastIndex) {
                                                        HorizontalDivider()
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
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
                                    val debtsByCurrency = state.debts.groupBy { it.currency.uppercase() }.toSortedMap()

                                    debtsByCurrency.forEach { (currency, debts) ->
                                        item {
                                            Text(
                                                "Waluta: $currency",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                        items(debts) { debt ->
                                            ModernDebtItem(
                                                fromName = viewModel.getMemberName(debt.fromUserId),
                                                toName = viewModel.getMemberName(debt.toUserId),
                                                amount = debt.amount,
                                                currency = currency,
                                                onSettleClick = {
                                                    selectedDebt = debt
                                                    showSettleDialog = true
                                                }
                                            )
                                        }
                                        item { Spacer(modifier = Modifier.height(8.dp)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- DIALOGI ---

        if (showEditDialog && state.trip != null) {
            val trip = state.trip!!
            EditTripDialog(
                currentName = trip.name,
                currentDate = trip.startDate,
                onDismiss = { showEditDialog = false },
                onConfirm = { newName, newDate ->
                    viewModel.updateTripDetails(newName, newDate)
                    showEditDialog = false
                }
            )
        }

        if (memberToRemove != null) {
            AlertDialog(
                onDismissRequest = { memberToRemove = null },
                title = { Text("Usunąć uczestnika?") },
                text = { Text("Czy na pewno chcesz usunąć ${memberToRemove?.username} z wyjazdu?") },
                confirmButton = {
                    Button(
                        onClick = {
                            memberToRemove?.let { user -> viewModel.removeMember(user.userId) }
                            memberToRemove = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Usuń") }
                },
                dismissButton = { TextButton(onClick = { memberToRemove = null }) { Text("Anuluj") } }
            )
        }

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
                onDismiss = { showSettleDialog = false; viewModel.clearSettlementRate() },
                onFetchRate = { fromCurrency, toCurrency -> viewModel.fetchSettlementRateFromNbp(fromCurrency, toCurrency) },
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

        if (showEditTransactionDialog && selectedTransactionForEdit != null) {
            val transaction = selectedTransactionForEdit!!
            EditTransactionDialog(
                transaction = transaction,
                members = state.members,
                initialBeneficiaryIds = state.transactionBeneficiaryIds[transaction.transactionId].orEmpty().toSet(),
                onDismiss = {
                    showEditTransactionDialog = false
                    selectedTransactionForEdit = null
                },
                onConfirm = { description, amount, category, payerId, beneficiaryIds ->
                    viewModel.updateTransaction(
                        transaction = transaction,
                        description = description,
                        amount = amount,
                        category = category,
                        payerId = payerId,
                        beneficiaryIds = beneficiaryIds
                    )
                    showEditTransactionDialog = false
                    selectedTransactionForEdit = null
                }
            )
        }

        if (state.showAddMemberDialog) {
            AddMemberDialog(
                onDismiss = { viewModel.setAddMemberDialogVisibility(false) },
                onConfirm = { viewModel.addMember(it) },
                error = state.addMemberError,
                isLoading = state.addMemberLoading
            )
        }
    }
}

// --- KOMPONENTY LOKALNE ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTripDialog(
    currentName: String,
    currentDate: Long,
    onDismiss: () -> Unit,
    onConfirm: (String, Long) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = currentDate)
    var showDatePicker by remember { mutableStateOf(false) }

    val selectedDateMillis = datePickerState.selectedDateMillis ?: currentDate
    val dateString = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(selectedDateMillis))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edytuj wyjazd") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nazwa wyjazdu") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = dateString,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Data rozpoczęcia") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Wybierz datę")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    onConfirm(name, selectedDateMillis)
                }
            }) {
                Text("Zapisz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Anuluj") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun EditTransactionDialog(
    transaction: Transaction,
    members: List<User>,
    initialBeneficiaryIds: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String, Long, Set<Long>) -> Unit
) {
    var description by remember(transaction) { mutableStateOf(transaction.description) }
    var amountInput by remember(transaction) {
        mutableStateOf(String.format(Locale.US, "%.2f", transaction.amount))
    }
    var selectedCategory by remember(transaction) { mutableStateOf(transaction.category) }
    var selectedPayerId by remember(transaction) { mutableLongStateOf(transaction.payerId) }
    var selectedBeneficiaryIds by remember(transaction, initialBeneficiaryIds) {
        mutableStateOf(initialBeneficiaryIds.toSet())
    }

    val amountValue = parseFlexibleDecimal(amountInput)
    val isAmountValid = amountValue != null && amountValue > 0.0
    val canSave = description.isNotBlank() &&
        isAmountValid &&
        selectedPayerId != -1L &&
        selectedBeneficiaryIds.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edytuj zdarzenie") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Opis") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("Kwota") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = !isAmountValid && amountInput.isNotBlank(),
                    supportingText = {
                        if (!isAmountValid && amountInput.isNotBlank()) {
                            Text("Podaj poprawna kwote (moze byc 12,50 albo 12.50)")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = transaction.currency,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Waluta") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (!transaction.isRepayment) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Kategoria", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ExpenseCategory.values().toList()) { category ->
                            FilterChip(
                                selected = selectedCategory == category.name,
                                onClick = { selectedCategory = category.name },
                                label = { Text(category.label) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Kto zaplacil?", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                members.forEach { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPayerId = member.userId }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedPayerId == member.userId,
                            onClick = { selectedPayerId = member.userId }
                        )
                        Text(member.username)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Dla kogo?", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                members.forEach { member ->
                    val checked = selectedBeneficiaryIds.contains(member.userId)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val updated = selectedBeneficiaryIds.toMutableSet()
                                if (checked) updated.remove(member.userId) else updated.add(member.userId)
                                selectedBeneficiaryIds = updated
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                val updated = selectedBeneficiaryIds.toMutableSet()
                                if (it) updated.add(member.userId) else updated.remove(member.userId)
                                selectedBeneficiaryIds = updated
                            }
                        )
                        Text(member.username)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    val amount = amountValue ?: return@Button
                    onConfirm(description.trim(), amount, selectedCategory, selectedPayerId, selectedBeneficiaryIds)
                }
            ) {
                Text("Zapisz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}

// FUNKCJA DONUT PIE CHART
@Composable
fun DonutPieChart(data: Map<String, Double>, modifier: Modifier = Modifier) {
    val total = data.values.sum()
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(data) {
        animatedProgress.animateTo(1f, animationSpec = tween(durationMillis = 1000))
    }

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - 20f
        val strokeWidth = 40f

        var startAngle = -90f

        data.forEach { (category, amount) ->
            val sweepAngle = (amount.toFloat() / total.toFloat()) * 360f * animatedProgress.value
            val colorList = try { getCategoryGradient(category) } catch(e: Exception) { listOf(Color.Gray, Color.LightGray) }

            if (sweepAngle > 0) {
                drawArc(
                    brush = Brush.linearGradient(colorList),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle - 2f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            startAngle += sweepAngle
        }
    }
}

// --- POZOSTAŁE KOMPONENTY (jeśli nie są w UiComponents) ---

@Composable
fun SettleDebtDialog(
    debt: Debt,
    fromName: String,
    toName: String,
    onDismiss: () -> Unit,
    onFetchRate: (String, String) -> Unit,
    fetchedRate: Double?,
    onConfirm: (Double, String, Double) -> Unit
) {
    var amountString by remember(debt) { mutableStateOf(debt.amount.toString()) }
    var currency by remember(debt) { mutableStateOf(debt.currency.uppercase()) }
    var rateString by remember(debt) { mutableStateOf("1.0") }

    LaunchedEffect(fetchedRate) {
        if (fetchedRate != null) rateString = fetchedRate.toString()
    }

    val inputAmount = parseFlexibleDecimal(amountString) ?: 0.0
    val inputRate = parseFlexibleDecimal(rateString) ?: 1.0
    val debtCurrency = debt.currency.uppercase()
    val selectedCurrency = currency.uppercase()
    val showRateField = selectedCurrency != debtCurrency
    val amountInDebtCurrency = if (showRateField) inputAmount * inputRate else inputAmount
    val difference = amountInDebtCurrency - debt.amount
    val isOverpayment = difference > 0.01

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
                            label = { Text("Kurs do $debtCurrency") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconToggleButton(
                            checked = false,
                            onCheckedChange = { onFetchRate(selectedCurrency, debtCurrency) },
                            modifier = Modifier.size(56.dp),
                            colors = IconButtonDefaults.filledIconToggleButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                        ) { Icon(Icons.Default.AutoMode, contentDescription = "Pobierz kurs", modifier = Modifier.size(24.dp)) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (inputAmount > 0) {
                    if (showRateField) {
                        Text("Wartość w $debtCurrency: ${String.format(Locale.US, "%.2f", amountInDebtCurrency)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Spacer(Modifier.height(4.dp))
                    }
                    if (isOverpayment) {
                        Text(
                            "Kwota jest za duza. Maksymalnie: ${String.format(Locale.US, "%.2f", debt.amount)} $debtCurrency",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (difference < -0.01) {
                        Text("Pozostanie do spłaty: ${String.format(Locale.US, "%.2f", abs(difference))} $debtCurrency", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("Spłata całkowita ✅", color = Color(0xFF388E3C), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (inputAmount > 0 && !isOverpayment) onConfirm(inputAmount, selectedCurrency, inputRate) },
                enabled = inputAmount > 0 && !isOverpayment
            ) { Text("Zatwierdź") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

private fun parseFlexibleDecimal(input: String): Double? {
    return input.trim().replace(',', '.').toDoubleOrNull()
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
fun SettlementSummaryRow(name: String, paidAmount: Double, toPay: Double, toRecover: Double, currency: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Zaplacil: ${String.format(Locale.US, "%.2f", paidAmount)} $currency",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Text(
                text = "Oddaje: ${String.format(Locale.US, "%.2f", toPay)} $currency",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Odzyska: ${String.format(Locale.US, "%.2f", toRecover)} $currency",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF2E7D32),
            fontWeight = FontWeight.Medium
        )
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
fun TransactionItem(
    transaction: Transaction,
    payerName: String,
    beneficiaryNames: List<String>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    mainCurrency: String
) {
    val context = LocalContext.current
    val isRepayment = transaction.isRepayment
    val icon = if (isRepayment) Icons.Default.CurrencyExchange else try { getCategoryIcon(transaction.category) } catch (e: Exception) { Icons.Default.MiscellaneousServices }
    val gradientColors = if (isRepayment) listOf(Color(0xFF66BB6A), Color(0xFF2E7D32)) else try { getCategoryGradient(transaction.category) } catch (e: Exception) { listOf(Color.Gray, Color.LightGray) }
    val logoUrl = if (!isRepayment) try { LogoUtils.getLogoUrl(transaction.description) } catch(e: Exception) { null } else null
    val beneficiariesText = beneficiaryNames.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "brak danych"
    val dateText = remember(transaction.date) {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(transaction.date))
    }

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
                Text(
                    text = if (isRepayment) "Do: $beneficiariesText" else "Dla: $beneficiariesText",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${String.format(Locale.US, "%.2f", transaction.amount)} ${transaction.currency}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                if (!isRepayment && transaction.currency != mainCurrency) {
                    val converted = transaction.amount * transaction.exchangeRate
                    Text("≈ ${String.format(Locale.US, "%.2f", converted)} $mainCurrency", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
            Column {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Edytuj", tint = Color.Gray.copy(alpha = 0.7f))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Usuń", tint = Color.Gray.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
fun AddMemberDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    error: String?,
    isLoading: Boolean
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dodaj uczestnika") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Imię") },
                    singleLine = true,
                    isError = error != null
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }, enabled = !isLoading) {
                Text(if (isLoading) "Dodawanie..." else "Dodaj")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

