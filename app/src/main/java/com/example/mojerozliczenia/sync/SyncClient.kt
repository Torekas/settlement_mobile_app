package com.example.mojerozliczenia.sync

import com.example.mojerozliczenia.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object SyncClient {
    fun isConfigured(): Boolean {
        return BuildConfig.SYNC_BASE_URL.isNotBlank() &&
            !BuildConfig.SYNC_BASE_URL.contains("example.invalid")
    }

    fun createSyncApi(): SyncApi {
        return createRetrofit().create(SyncApi::class.java)
    }

    fun createAuthApi(): AuthApi {
        return createRetrofit().create(AuthApi::class.java)
    }

    fun createUsersApi(): UsersApi {
        return createRetrofit().create(UsersApi::class.java)
    }

    private fun createRetrofit(): Retrofit {
        val logging = HttpLoggingInterceptor()
        logging.level = HttpLoggingInterceptor.Level.BASIC

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        val baseUrl = ensureTrailingSlash(BuildConfig.SYNC_BASE_URL)

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun ensureTrailingSlash(url: String): String {
        return if (url.endsWith("/")) url else "$url/"
    }
}
