package com.example.mojerozliczenia.packing

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

class PackingViewModel(private val dao: PackingDao) : ViewModel() {

    private val _items = MutableStateFlow<List<PackingItem>>(emptyList())
    val items: StateFlow<List<PackingItem>> = _items

    fun loadItems(tripId: Long) {
        viewModelScope.launch {
            dao.getItemsForTrip(tripId).collect { _items.value = it }
        }
    }

    fun addItem(tripId: Long, name: String, category: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            dao.insertItem(PackingItem(tripId = tripId, name = name, category = category))
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
            val items = suggestions.map { (name, cat) ->
                PackingItem(tripId = tripId, name = name, category = cat)
            }
            dao.insertAll(items)
        }
    }

    // 1. Powiadomienie
    fun scheduleReminder(context: Context, timestamp: Long) {
        val currentTime = System.currentTimeMillis()
        val delay = timestamp - currentTime

        if (delay > 0) {
            val workRequest = OneTimeWorkRequestBuilder<PackingNotificationWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("trip_name" to "Przypomnienie o pakowaniu!"))
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            Toast.makeText(context, "Ustawiono powiadomienie w aplikacji 🔔", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Wybrano czas w przeszłości", Toast.LENGTH_SHORT).show()
        }
    }

    // 2. NOWA FUNKCJA: Budzik systemowy
    fun setSystemAlarm(context: Context, timestamp: Long, message: String) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp

        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }

        try {
            context.startActivity(intent)
            Toast.makeText(context, "Ustawiono budzik na $hour:$minute ⏰", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Nie znaleziono aplikacji zegara", Toast.LENGTH_SHORT).show()
        }
    }
}