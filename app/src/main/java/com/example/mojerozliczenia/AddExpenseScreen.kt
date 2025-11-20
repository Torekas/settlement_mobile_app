package com.example.mojerozliczenia

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale
import java.util.regex.Pattern

enum class ExpenseCategory(val label: String, val icon: ImageVector) {
    FOOD("Jedzenie", Icons.Default.Fastfood),
    SHOPPING("Zakupy", Icons.Default.LocalGroceryStore),
    TRANSPORT("Transport", Icons.Default.DirectionsBus),
    ACCOMMODATION("Nocleg", Icons.Default.Bed),
    ENTERTAINMENT("Rozrywka", Icons.Default.Movie),
    OTHER("Inne", Icons.Default.MiscellaneousServices)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(tripId: Long, viewModel: AddExpenseViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(tripId) { viewModel.loadMembers(tripId) }
    val state by viewModel.uiState.collectAsState()

    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("PLN") }

    // Kategoria teraz pochodzi z ViewModelu (dla automatyzacji), ale mapujemy ją na Enum
    val selectedCategory = try {
        ExpenseCategory.valueOf(state.selectedCategory)
    } catch (e: Exception) {
        ExpenseCategory.FOOD
    }

    val showExchangeRate = currency.uppercase() != viewModel.mainCurrency
    var exchangeRateInput by remember { mutableStateOf("1.0") }

    var isScanning by remember { mutableStateOf(false) }

    // Reakcja na pobranie kursu
    LaunchedEffect(state.fetchedRate) {
        if (state.fetchedRate != null) {
            exchangeRateInput = state.fetchedRate.toString()
            Toast.makeText(context, "Pobrano kurs: ${state.fetchedRate}", Toast.LENGTH_SHORT).show()
            viewModel.clearFetchedRate()
        }
    }

    // --- SKANER (TYLKO KWOTA) ---
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            isScanning = true
            Toast.makeText(context, "Szukam kwoty...", Toast.LENGTH_SHORT).show()
            OcrUtils.scanReceipt(
                bitmap = bitmap,
                onSuccess = { result ->
                    if (result.amount != null) {
                        amount = String.format(Locale.US, "%.2f", result.amount)
                        Toast.makeText(context, "Zczytano kwotę!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Nie znaleziono kwoty", Toast.LENGTH_SHORT).show()
                    }
                    isScanning = false
                },
                onFailure = {
                    isScanning = false
                    Toast.makeText(context, "Błąd odczytu", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    // --- GŁOS (TYTUŁ + KWOTA + AUTO KATEGORIA) ---
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (spokenText != null) {
                val matcher = Pattern.compile("(\\d+[.,]?\\d*)").matcher(spokenText)
                if (matcher.find()) {
                    amount = matcher.group(1)?.replace(",", ".") ?: ""
                    title = spokenText.replace(matcher.group(1) ?: "", "")
                        .replace("złotych", "", ignoreCase = true)
                        .replace("złoty", "", ignoreCase = true)
                        .replace("zł", "", ignoreCase = true)
                        .trim()
                } else title = spokenText

                // URUCHAMIAMY AUTO-KATEGORYZACJĘ PO GŁOSIE
                viewModel.analyzeTitleAndCategorize(title)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dodaj wydatek") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć") } },
                actions = {
                    IconButton(onClick = {
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            }
                            voiceLauncher.launch(intent)
                        } catch (e: Exception) { Toast.makeText(context, "Błąd mikrofonu", Toast.LENGTH_SHORT).show() }
                    }) { Icon(Icons.Default.Mic, contentDescription = "Mów") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
            // POLE TYTUŁU Z AUTO-ANALIZĄ
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    // Tutaj dzieje się magia: przy każdym wpisanym znaku sprawdzamy kategorię
                    viewModel.analyzeTitleAndCategorize(it)
                },
                label = { Text("Co kupiono?") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Kwota") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    trailingIcon = {
                        if (isScanning) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        } else {
                            IconButton(onClick = { cameraLauncher.launch() }) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = "Skanuj kwotę", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(value = currency, onValueChange = { currency = it }, label = { Text("Waluta") }, modifier = Modifier.width(100.dp))
            }

            if (showExchangeRate) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Przelicznik: 1 $currency = ? ${viewModel.mainCurrency}", style = MaterialTheme.typography.labelMedium)
                            OutlinedTextField(value = exchangeRateInput, onValueChange = { exchangeRateInput = it }, label = { Text("Kurs") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledIconToggleButton(
                            checked = state.isLoading,
                            onCheckedChange = { if (!state.isLoading) viewModel.fetchRateFromNbp(currency) },
                            modifier = Modifier.size(56.dp),
                            colors = IconButtonDefaults.filledIconToggleButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                        ) {
                            if (state.isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                            else Icon(Icons.Default.AutoMode, contentDescription = "Pobierz kurs", modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Kategoria (Auto)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ExpenseCategory.values()) { category ->
                    FilterChip(
                        selected = (category == selectedCategory),
                        // Kliknięcie ręczne nadal pozwala zmienić kategorię
                        onClick = { viewModel.selectCategory(category.name) },
                        label = { Text(category.label) },
                        leadingIcon = { Icon(category.icon, null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Kto zapłacił?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            state.members.forEach { member ->
                val isSuggested = (member.userId == state.suggestedPayerId)
                Row(Modifier.fillMaxWidth().height(48.dp).toggleable(value = (member.userId == state.payerId), onValueChange = { viewModel.selectPayer(member.userId) }, role = Role.RadioButton), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = (member.userId == state.payerId), onClick = null)
                    Text(member.username, modifier = Modifier.padding(start = 16.dp))
                    if (isSuggested) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.tertiaryContainer).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("Kolej na Ciebie 📉", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Dla kogo?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            state.members.forEach { member ->
                Row(Modifier.fillMaxWidth().height(48.dp).toggleable(value = state.selectedMembers.contains(member.userId), onValueChange = { viewModel.toggleMemberSelection(member.userId) }, role = Role.Checkbox), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = state.selectedMembers.contains(member.userId), onCheckedChange = null); Text(member.username, modifier = Modifier.padding(start = 16.dp))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    val amountValue = amount.toDoubleOrNull()
                    val rateValue = exchangeRateInput.toDoubleOrNull() ?: 1.0
                    if (title.isNotBlank() && amountValue != null) {
                        viewModel.saveExpense(title, amountValue, currency, selectedCategory.name, rateValue, onBack)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank() && amount.isNotBlank() && state.payerId != -1L && state.selectedMembers.isNotEmpty()
            ) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text("Zapisz wydatek") }
        }
    }
}