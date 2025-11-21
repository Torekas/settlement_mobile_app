package com.example.mojerozliczenia

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

// Importy modułów
import com.example.mojerozliczenia.flights.FlightSearchActivity
import com.example.mojerozliczenia.packing.PackingListScreen
import com.example.mojerozliczenia.packing.PackingViewModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getDatabase(applicationContext)
        val sessionManager = SessionManager(applicationContext)

        // Sprawdzamy sesję na starcie
        val savedUserId = sessionManager.fetchUserId()
        val startDestination = if (savedUserId != -1L) "trip_list/$savedUserId" else "auth"

        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                val context = LocalContext.current

                // Inicjalizacja ViewModeli
                val authViewModel = remember { AuthViewModel(db.appDao(), sessionManager) }
                val tripViewModel = remember { TripViewModel(db.appDao()) }

                val packingViewModel = remember { PackingViewModel(db.packingDao()) }

                // Tutaj tworzymy TripDetailsViewModel z dwoma DAO
                val tripDetailsViewModel = remember {
                    TripDetailsViewModel(db.appDao(), db.packingDao())
                }

                val addExpenseViewModel = remember { AddExpenseViewModel(db.appDao()) }

                NavHost(navController = navController, startDestination = startDestination) {

                    // --- EKRAN LOGOWANIA ---
                    composable("auth") {
                        AuthScreen(authViewModel) { userId ->
                            // Po zalogowaniu czyścimy stos, żeby cofnąć się nie dało do logowania
                            navController.navigate("trip_list/$userId") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }

                    // --- LISTA WYJAZDÓW ---
                    composable(
                        "trip_list/{userId}",
                        arguments = listOf(navArgument("userId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val userId = backStackEntry.arguments?.getLong("userId") ?: 0L

                        Box(modifier = Modifier.fillMaxSize()) {

                            TripListScreen(
                                viewModel = tripViewModel,
                                userId = userId,
                                onLogout = {
                                    // 1. Czyścimy sesję
                                    sessionManager.clearSession()

                                    // 2. Metoda "Atomowa": Restartujemy Activity.
                                    val intent = Intent(context, MainActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    context.startActivity(intent)
                                    finish()
                                },
                                onTripClick = { tripId ->
                                    navController.navigate("trip_details/$tripId")
                                }
                            )

                            // Przycisk "Szukaj Lotów"
                            ExtendedFloatingActionButton(
                                text = { Text("Szukaj Lotów") },
                                icon = { Icon(Icons.Default.Search, contentDescription = null) },
                                onClick = {
                                    val intent = Intent(context, FlightSearchActivity::class.java)
                                    context.startActivity(intent)
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .navigationBarsPadding()
                                    .padding(bottom = 8.dp, end = 16.dp)
                            )
                        }
                    }

                    // --- SZCZEGÓŁY WYJAZDU ---
                    composable(
                        "trip_details/{tripId}",
                        arguments = listOf(navArgument("tripId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val tripId = backStackEntry.arguments?.getLong("tripId") ?: 0L

                        LaunchedEffect(Unit) { tripDetailsViewModel.loadTripData(tripId) }

                        TripDetailsScreen(
                            tripId = tripId,
                            viewModel = tripDetailsViewModel,
                            onBack = { navController.popBackStack() },
                            onPackingClick = {
                                navController.navigate("packing_list/$tripId")
                            },
                            onAddExpenseClick = {
                                navController.navigate("add_expense/$tripId")
                            }
                        )
                    }

                    // --- LISTA PAKOWANIA ---
                    composable(
                        "packing_list/{tripId}",
                        arguments = listOf(navArgument("tripId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val tripId = backStackEntry.arguments?.getLong("tripId") ?: 0L

                        PackingListScreen(
                            tripId = tripId,
                            viewModel = packingViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // --- DODAWANIE WYDATKU ---
                    composable(
                        "add_expense/{tripId}",
                        arguments = listOf(navArgument("tripId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val tripId = backStackEntry.arguments?.getLong("tripId") ?: 0L
                        AddExpenseScreen(tripId, addExpenseViewModel) { navController.popBackStack() }
                    }
                }
            }
        }
    }
}