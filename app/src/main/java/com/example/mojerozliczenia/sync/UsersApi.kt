package com.example.mojerozliczenia.sync

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface UsersApi {
    @GET("users/find")
    suspend fun findUser(
        @Header("Authorization") authorization: String?,
        @Query("username") username: String
    ): UserLookupResponse
}
