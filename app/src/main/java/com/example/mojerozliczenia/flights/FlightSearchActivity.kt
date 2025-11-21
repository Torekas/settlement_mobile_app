package com.example.mojerozliczenia.flights

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

class FlightSearchActivity : ComponentActivity() {
    private val viewModel by viewModels<FlightViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                FlightSearchScreen(viewModel, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightSearchScreen(viewModel: FlightViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var origin by remember { mutableStateOf("Warszawa") }
    var destination by remember { mutableStateOf("Paryż") }

    DisposableEffect(Unit) {
        val detector = ShakeDetector(context) {
            val randomDest = FlightUtils.getRandomDestination()
            destination = randomDest
            Toast.makeText(context, "🎲 Wylosowano: $randomDest!", Toast.LENGTH_SHORT).show()
        }
        detector.start()
        onDispose { detector.stop() }
    }

    val datePickerState = rememberDatePickerState()
    var showDatePicker by remember { mutableStateOf(false) }
    val selectedDate = remember(datePickerState.selectedDateMillis) {
        FlightUtils.formatDateForApi(datePickerState.selectedDateMillis)
    }
    val state = viewModel.flightState.value

    // UŻYWAMY SCAFFOLD - Standardowy układ aplikacji
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Szukaj Lotów") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->

        // Główna zawartość
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {

            // KARTA WYSZUKIWANIA - Spójna z resztą UI
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SearchInputRow(origin, { origin = it }, "Skąd", Icons.Default.LocationOn)
                    Spacer(modifier = Modifier.height(12.dp))
                    SearchInputRow(destination, { destination = it }, "Dokąd", Icons.Default.Flight)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = if(selectedDate.isNotEmpty()) selectedDate else "Wybierz datę",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Data wylotu") },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Kalendarz")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (selectedDate.isNotEmpty()) {
                                val originCode = FlightUtils.getCode(origin)
                                val destCode = FlightUtils.getCode(destination)
                                viewModel.searchForFlights(originCode, destCode, selectedDate)
                            } else {
                                Toast.makeText(context, "Wybierz datę!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Znajdź połączenia")
                    }
                }
            }

            // Shake info
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Potrząśnij telefonem, aby wylosować cel!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // WYNIKI
            Box(modifier = Modifier.fillMaxSize()) {
                when (state) {
                    is FlightState.Loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                    is FlightState.Error -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "⚠️ Błąd wyszukiwania",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    is FlightState.Success -> {
                        if (state.flights.isEmpty()) {
                            Text("Brak lotów dla wybranych kryteriów.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
                        } else {
                            FlightList(flights = state.flights)
                        }
                    }
                    is FlightState.Idle -> {}
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("OK") } }
        ) { DatePicker(state = datePickerState) }
    }
}

@Composable
fun SearchInputRow(value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun FlightList(flights: List<FlightOffer>) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(flights) { index, flight ->
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(
                    initialOffsetY = { 50 * (index + 1) },
                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                ) + fadeIn()
            ) {
                TicketCard(flight)
            }
        }
    }
}

@Composable
fun TicketCard(flight: FlightOffer) {
    val itinerary = flight.itineraries.firstOrNull()
    val firstSegment = itinerary?.segments?.firstOrNull()
    val lastSegment = itinerary?.segments?.lastOrNull()
    val carrierCode = firstSegment?.carrierCode ?: ""
    val priceValue = flight.price.total.toDoubleOrNull() ?: 0.0
    val isSuperDeal = priceValue < 100.0

    // Karta w stylu Material 3
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer // Standardowy kolor tła karty
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            // GÓRA KARTY (Logo + Cena)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer) // Kolor akcentu z motywu
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.White, // Logo zawsze na białym tle dla czytelności
                    modifier = Modifier.size(width = 60.dp, height = 30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AsyncImage(
                            model = FlightUtils.getAirlineLogoUrl(carrierCode),
                            contentDescription = "Airline Logo",
                            modifier = Modifier.padding(2.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                // Cena
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${flight.price.total} €",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (isSuperDeal) {
                        Text(
                            text = "SUPER CENA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary, // Kolor wyróżniający
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ŚRODEK KARTY (Trasa)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Wylot
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = firstSegment?.departure?.iataCode ?: "---",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = FlightUtils.extractTime(firstSegment?.departure?.at ?: ""),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Grafika środkowa
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Flight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.rotate(90f)
                    )
                    Text(
                        text = itinerary?.duration?.let { FlightUtils.formatDuration(it) } ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val stops = (itinerary?.segments?.size ?: 1) - 1
                    if (stops > 0) {
                        Text(
                            text = "$stops przesiadka",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = "Bezpośredni",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Przylot
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = lastSegment?.arrival?.iataCode ?: "---",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = FlightUtils.extractTime(lastSegment?.arrival?.at ?: ""),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Linia przerywana
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                FlightUtils.DashedDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // STOPKA
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = firstSegment?.departure?.at?.substringBefore("T") ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("Economy", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}