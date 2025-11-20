package com.example.mojerozliczenia

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getDatabase(applicationContext)

        // 1. Inicjalizacja SessionManager
        val sessionManager = SessionManager(applicationContext)

        // 2. Sprawdzenie, czy użytkownik jest zapamiętany
        val savedUserId = sessionManager.fetchUserId()
        val startDestination = if (savedUserId != -1L) "trip_list/$savedUserId" else "auth"

        setContent {
            MaterialTheme {
                val navController = rememberNavController()

                // Przekazujemy sessionManager do AuthViewModel
                val authViewModel = remember { AuthViewModel(db.appDao(), sessionManager) }

                val tripViewModel = remember { TripViewModel(db.appDao()) }
                val tripDetailsViewModel = remember { TripDetailsViewModel(db.appDao()) }
                val addExpenseViewModel = remember { AddExpenseViewModel(db.appDao()) }

                NavHost(navController = navController, startDestination = startDestination) {

                    composable("auth") {
                        AuthScreen(authViewModel) { userId ->
                            navController.navigate("trip_list/$userId") { popUpTo("auth") { inclusive = true } }
                        }
                    }

                    composable("trip_list/{userId}", arguments = listOf(navArgument("userId") { type = NavType.LongType })) { backStackEntry ->
                        val userId = backStackEntry.arguments?.getLong("userId") ?: 0L

                        TripListScreen(
                            viewModel = tripViewModel,
                            userId = userId,
                            onLogout = {
                                // 3. Wylogowanie: Czyścimy sesję i wracamy do logowania
                                sessionManager.clearSession()
                                navController.navigate("auth") { popUpTo("trip_list/$userId") { inclusive = true } }
                            }
                        ) { tripId ->
                            navController.navigate("trip_details/$tripId")
                        }
                    }

                    composable("trip_details/{tripId}", arguments = listOf(navArgument("tripId") { type = NavType.LongType })) { backStackEntry ->
                        val tripId = backStackEntry.arguments?.getLong("tripId") ?: 0L
                        LaunchedEffect(Unit) { tripDetailsViewModel.loadTripData(tripId) }
                        TripDetailsScreen(tripId, tripDetailsViewModel, { navController.popBackStack() }) {
                            navController.navigate("add_expense/$tripId")
                        }
                    }

                    composable("add_expense/{tripId}", arguments = listOf(navArgument("tripId") { type = NavType.LongType })) { backStackEntry ->
                        val tripId = backStackEntry.arguments?.getLong("tripId") ?: 0L
                        AddExpenseScreen(tripId, addExpenseViewModel) { navController.popBackStack() }
                    }
                }
            }
        }
    }
}