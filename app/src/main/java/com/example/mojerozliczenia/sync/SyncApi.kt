package com.example.mojerozliczenia.sync

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface SyncApi {
    @POST("sync/push")
    suspend fun pushChanges(
        @Header("Authorization") authorization: String?,
        @Header("X-User-Sync-Id") userSyncId: String,
        @Header("X-Device-Id") deviceId: String,
        @Body request: SyncPushRequest
    ): SyncPushResponse

    @GET("sync/pull")
    suspend fun pullChanges(
        @Header("Authorization") authorization: String?,
        @Header("X-User-Sync-Id") userSyncId: String,
        @Header("X-Device-Id") deviceId: String,
        @Query("since") since: Long
    ): SyncPullResponse
}
