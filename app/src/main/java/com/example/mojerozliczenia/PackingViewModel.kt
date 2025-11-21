package com.example.mojerozliczenia.packing

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class PackingViewModel(private val dao: PackingDao) : ViewModel() {

    private val _items = MutableStateFlow<List<PackingItem>>(emptyList())
    val items: StateFlow<List<PackingItem>> = _items

    fun loadItems(tripId: Long) {
        viewModelScope.launch {
            dao.getItemsForTrip(tripId).collect { _items.value = it }
        }
    }

    fun addItem(tripId: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            dao.insertItem(PackingItem(tripId = tripId, name = name))
        }
    }

    fun toggleItem(item: PackingItem) {
        viewModelScope.launch {
            dao.updateItem(item.copy(isPacked = !item.isPacked))
        }
    }

    fun deleteItem(item: PackingItem) {
        viewModelScope.launch {
            dao.deleteItem(item)
        }
    }

    fun addFromTemplate(tripId: Long, templateName: String) {
        val suggestions = PackingSuggestions.templates[templateName] ?: return
        viewModelScope.launch {
            val items = suggestions.map { PackingItem(tripId = tripId, name = it) }
            dao.insertAll(items)
        }
    }

    // Planowanie powiadomienia
    fun scheduleNotification(context: Context, tripName: String, tripDateMillis: Long) {
        val currentTime = System.currentTimeMillis()
        val delay = tripDateMillis - currentTime

        if (delay > 0) {
            val workRequest = OneTimeWorkRequestBuilder<PackingNotificationWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("trip_name" to tripName))
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}