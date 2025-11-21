package com.example.mojerozliczenia

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload // NOWA IKONA
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListScreen(
    viewModel: TripViewModel,
    userId: Long,
    onLogout: () -> Unit,
    onTripClick: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var tripToDelete by remember { mutableStateOf<Trip?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val json = reader.readText()
                reader.close()
                inputStream?.close()

                if (json.isNotBlank()) {
                    viewModel.importTrip(json)
                    Toast.makeText(context, "Zaimportowano wyjazd!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Błąd odczytu: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Twoje Wyjazdy") },
                actions = {
                    IconButton(onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Importuj")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Wyloguj")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.setDialogVisibility(true) }) {
                Icon(Icons.Default.Add, contentDescription = "Dodaj wyjazd")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (state.trips.isEmpty()) {
                Text(
                    text = "Brak wyjazdów. Kliknij + aby dodać.",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.Gray
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.trips) { trip ->
                        TripItem(
                            trip = trip,
                            onClick = { onTripClick(trip.tripId) },
                            onDelete = { tripToDelete = trip }
                        )
                    }
                }
            }
        }

        if (state.showAddDialog) {
            AddTripDialog(
                onDismiss = { viewModel.setDialogVisibility(false) },
                onConfirm = { name, currency ->
                    viewModel.addNewTrip(name, currency, userId)
                }
            )
        }

        if (tripToDelete != null) {
            AlertDialog(
                onDismissRequest = { tripToDelete = null },
                title = { Text("Usuń wyjazd") },
                text = { Text("Czy na pewno chcesz usunąć wyjazd \"${tripToDelete?.name}\"?") },
                confirmButton = {
                    Button(
                        onClick = {
                            tripToDelete?.let { viewModel.deleteTrip(it.tripId) }
                            tripToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Usuń") }
                },
                dismissButton = { TextButton(onClick = { tripToDelete = null }) { Text("Anuluj") } }
            )
        }
    }
}

@Composable
fun TripItem(trip: Trip, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (trip.imageUrl != null) {
                AsyncImage(
                    model = trip.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer))
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    // TU ZMIANA: Nazwa + Ikonka importu
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = trip.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        if (trip.isImported) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Importowany",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Text(
                        text = "Waluta: ${trip.mainCurrency}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Usuń", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun AddTripDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("PLN") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nowy wyjazd") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nazwa wyjazdu") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = currency, onValueChange = { currency = it }, label = { Text("Waluta (np. PLN)") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(name, currency) }) { Text("Utwórz") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}