package com.example.mojerozliczenia.planner

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class PlannerViewModel(private val dao: PlannerDao) : ViewModel() {

    private val _events = MutableStateFlow<List<PlannerEvent>>(emptyList())
    val events: StateFlow<List<PlannerEvent>> = _events

    fun loadEvents(tripId: Long) {
        viewModelScope.launch {
            dao.getEventsForTrip(tripId).collect { _events.value = it }
        }
    }

    // ZMIANA: Dodano parametr context, aby móc zaplanować powiadomienie
    fun addEvent(context: Context, tripId: Long, title: String, description: String, location: String, time: Long) {
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
            // Automatyczne planowanie powiadomienia
            scheduleEventNotification(context, title, location, time)
        }
    }

    // Logika planowania powiadomienia 1h przed
    private fun scheduleEventNotification(context: Context, title: String, location: String, eventTime: Long) {
        val currentTime = System.currentTimeMillis()
        // Czas powiadomienia = czas wydarzenia minus 1 godzina (3600000 ms)
        val triggerTime = eventTime - 3600000
        val delay = triggerTime - currentTime

        // Planujemy tylko jeśli czas powiadomienia jest w przyszłości
        if (delay > 0) {
            val workRequest = OneTimeWorkRequestBuilder<PlannerNotificationWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(
                    "event_title" to title,
                    "event_location" to location
                ))
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
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