package com.example.mojerozliczenia.packing

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mojerozliczenia.R

class PackingNotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val tripName = inputData.getString("trip_name") ?: "Twój wyjazd"
        showNotification(tripName)
        return Result.success()
    }

    private fun showNotification(tripName: String) {
        val channelId = "packing_channel"
        val notificationId = 1001

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Przypomnienia o pakowaniu", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Powiadomienia o liście rzeczy do spakowania"
            }
            val notificationManager: NotificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar) // Zmień na swoją ikonę
            .setContentTitle("To już dziś! 🎒")
            .setContentText("Wyjazd: $tripName. Sprawdź, czy wszystko spakowane!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, builder.build())
        }
    }
}