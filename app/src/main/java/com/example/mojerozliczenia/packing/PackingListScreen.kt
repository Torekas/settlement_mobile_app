package com.example.mojerozliczenia.packing

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackingListScreen(
    tripId: Long,
    viewModel: PackingViewModel,
    onBack: () -> Unit
) {
    val items by viewModel.items.collectAsState()
    var newItemName by remember { mutableStateOf("") }

    // Obliczanie postępu
    val totalItems = items.size
    val packedItems = items.count { it.isPacked }
    val progress = if (totalItems > 0) packedItems.toFloat() / totalItems else 0f

    LaunchedEffect(tripId) {
        viewModel.loadItems(tripId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lista Pakowania") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Wróć")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // 1. PASEK POSTĘPU
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Spakowano:", style = MaterialTheme.typography.titleMedium)
                        Text("$packedItems / $totalItems", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        trackColor = Color.White.copy(alpha = 0.3f),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. SUGESTIE (Szablony)
            Text("Szybkie dodawanie:", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
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

            // 3. DODAWANIE RĘCZNE
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newItemName,
                    onValueChange = { newItemName = it },
                    label = { Text("Co chcesz zabrać?") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        viewModel.addItem(tripId, newItemName)
                        newItemName = ""
                    },
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. LISTA RZECZY
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { item ->
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

@Composable
fun PackingItemRow(item: PackingItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    val bgColor by animateColorAsState(
        if (item.isPacked) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
    )
    val textColor = if (item.isPacked) Color.Gray else MaterialTheme.colorScheme.onSurface

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Usuń", tint = Color.Gray.copy(alpha = 0.6f))
            }
        }
    }
}