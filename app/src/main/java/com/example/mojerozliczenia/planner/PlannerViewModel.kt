package com.example.mojerozliczenia.planner

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlannerViewModel(private val dao: PlannerDao) : ViewModel() {

    private val _events = MutableStateFlow<List<PlannerEvent>>(emptyList())
    val events: StateFlow<List<PlannerEvent>> = _events

    fun loadEvents(tripId: Long) {
        viewModelScope.launch {
            dao.getEventsForTrip(tripId).collect { _events.value = it }
        }
    }

    fun addEvent(tripId: Long, title: String, description: String, location: String, time: Long) {
        if (title.isBlank()) return
        viewModelScope.launch {
            dao.insertEvent(
                PlannerEvent(
                    tripId = tripId,
                    title = title,
                    description = description,
                    locationName = location,
                    timeInMillis = time
                )
            )
        }
    }

    fun deleteEvent(event: PlannerEvent) {
        viewModelScope.launch {
            dao.deleteEvent(event)
        }
    }

    fun toggleDone(event: PlannerEvent) {
        viewModelScope.launch {
            dao.updateEvent(event.copy(isDone = !event.isDone))
        }
    }

    // Funkcja otwierająca Mapy Google z nawigacją
    fun openMapNavigation(context: Context, locationName: String) {
        val gmmIntentUri = Uri.parse("google.navigation:q=${Uri.encode(locationName)}")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")

        try {
            context.startActivity(mapIntent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(locationName)}"))
            context.startActivity(webIntent)
        }
    }
}