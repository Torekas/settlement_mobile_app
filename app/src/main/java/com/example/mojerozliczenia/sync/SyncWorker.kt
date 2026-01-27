package com.example.mojerozliczenia.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mojerozliczenia.AppDatabase
import com.example.mojerozliczenia.SessionManager

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val sessionManager = SessionManager(applicationContext)
        val userSyncId = sessionManager.fetchUserSyncId()
        if (userSyncId.isNullOrBlank()) return Result.success()
        if (!SyncClient.isConfigured()) return Result.success()

        val authToken = sessionManager.fetchAuthToken()
        val db = AppDatabase.getDatabase(applicationContext)
        val manager = SyncManager(db.appDao(), SyncClient.createSyncApi(), SyncPrefs(applicationContext))

        return try {
            manager.sync(userSyncId, authToken)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
