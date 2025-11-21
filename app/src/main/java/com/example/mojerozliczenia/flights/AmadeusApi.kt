package com.example.mojerozliczenia.flights

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

interface AmadeusService {
    @FormUrlEncoded
    @POST("v1/security/oauth2/token")
    suspend fun getAccessToken(
        @Field("grant_type") grantType: String = "client_credentials",
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String
    ): AuthResponse

    @GET("v2/shopping/flight-offers")
    suspend fun searchFlights(
        @Header("Authorization") token: String,
        @Query("originLocationCode") origin: String,
        @Query("destinationLocationCode") destination: String,
        @Query("departureDate") date: String,
        @Query("adults") adults: Int = 1,
        @Query("max") maxResults: Int = 10
    ): FlightSearchResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://test.api.amadeus.com/"

    // Tutaj był błąd. Poprawione odwołanie do Level.BODY
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    val api: AmadeusService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AmadeusService::class.java)
    }
}