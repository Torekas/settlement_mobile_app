package com.example.mojerozliczenia.packing

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.DatePicker
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackingListScreen(
    tripId: Long,
    viewModel: PackingViewModel,
    onBack: () -> Unit
) {
    val items by viewModel.items.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Stan zwiniętych kategorii
    val expandedCategories = remember { mutableStateMapOf<String, Boolean>().withDefault { true } }

    // --- ZMIENNE DLA ALARMU ---
    var tempSelectedTime by remember { mutableLongStateOf(0L) }
    var showReminderTypeDialog by remember { mutableStateOf(false) }

    val calendar = Calendar.getInstance()

    // 2. Wybór Godziny -> Otwiera Dialog Typu
    val timePickerDialog = TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)

            tempSelectedTime = calendar.timeInMillis
            // Po wybraniu czasu pokazujemy pytanie: Budzik czy Powiadomienie?
            showReminderTypeDialog = true
        },
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
        true
    )

    // 1. Wybór Daty -> Otwiera Wybór Godziny
    val datePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
            calendar.set(year, month, dayOfMonth)
            timePickerDialog.show()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    LaunchedEffect(tripId) {
        viewModel.loadItems(tripId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lista Pakowania") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                    }
                },
                actions = {
                    // DZWONECZEK
                    IconButton(onClick = { datePickerDialog.show() }) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Ustaw przypomnienie",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Dodaj")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // 1. PASEK POSTĘPU
            val totalItems = items.size
            val packedItems = items.count { it.isPacked }
            val progress = if (totalItems > 0) packedItems.toFloat() / totalItems else 0f

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Postęp pakowania:", style = MaterialTheme.typography.titleMedium)
                        Text("$packedItems / $totalItems", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        trackColor = Color.White.copy(alpha = 0.3f),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 2. SZYBKIE DODAWANIE Z SZABLONÓW
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                items(PackingSuggestions.templates.keys.toList()) { templateName ->
                    SuggestionChip(
                        onClick = { viewModel.addFromTemplate(tripId, templateName) },
                        label = { Text(templateName) },
                        icon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }

            // 3. LISTA Z PODZIAŁEM NA KATEGORIE
            val groupedItems = items.groupBy { it.category }
            val sortedCategories = groupedItems.keys.sortedBy { if (it == "Inne") "ZZZZ" else it }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                sortedCategories.forEach { category ->
                    val categoryItems = groupedItems[category] ?: emptyList()
                    val isExpanded = expandedCategories.getOrPut(category) { true }

                    val catTotal = categoryItems.size
                    val catPacked = categoryItems.count { it.isPacked }

                    item {
                        CategoryHeader(
                            categoryName = category,
                            isExpanded = isExpanded,
                            total = catTotal,
                            packed = catPacked,
                            onToggle = { expandedCategories[category] = !isExpanded }
                        )
                    }

                    if (isExpanded) {
                        items(categoryItems, key = { it.id }) { item ->
                            PackingItemRow(
                                item = item,
                                onToggle = { viewModel.toggleItem(item) },
                                onDelete = { viewModel.deleteItem(item) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddPackingItemDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, category ->
                viewModel.addItem(tripId, name, category)
                showAddDialog = false
            }
        )
    }

    if (showReminderTypeDialog) {
        AlertDialog(
            onDismissRequest = { showReminderTypeDialog = false },
            title = { Text("Ustaw przypomnienie") },
            text = { Text("W jaki sposób chcesz otrzymać powiadomienie o pakowaniu?") },
            confirmButton = {
                // Opcja 1: Budzik
                Button(
                    onClick = {
                        viewModel.setSystemAlarm(context, tempSelectedTime, "Czas na pakowanie!")
                        showReminderTypeDialog = false
                    }
                ) {
                    Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Budzik")
                }
            },
            dismissButton = {
                // Opcja 2: Powiadomienie
                OutlinedButton(
                    onClick = {
                        viewModel.scheduleReminder(context, tempSelectedTime)
                        showReminderTypeDialog = false
                    }
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Powiadomienie")
                }
            }
        )
    }
}

@Composable
fun CategoryHeader(
    categoryName: String,
    isExpanded: Boolean,
    total: Int,
    packed: Int,
    onToggle: () -> Unit
) {
    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f)

    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Badge(containerColor = if (packed == total) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
                    Text("$packed/$total", modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
}

@Composable
fun PackingItemRow(item: PackingItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    val bgColor by animateColorAsState(
        if (item.isPacked) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface
    )
    val textColor = if (item.isPacked) Color.Gray else MaterialTheme.colorScheme.onSurface

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isPacked,
                onCheckedChange = { onToggle() }
            )
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (item.isPacked) TextDecoration.LineThrough else TextDecoration.None
                ),
                color = textColor,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Usuń", tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPackingItemDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Inne") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dodaj rzecz") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Co zabrać?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kategoria") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        PackingCategories.list.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    selectedCategory = category
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, selectedCategory) }) {
                Text("Dodaj")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}