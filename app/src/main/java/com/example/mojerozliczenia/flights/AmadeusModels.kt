package com.example.mojerozliczenia.flights // <--- To musi tu być!

import com.google.gson.annotations.SerializedName

data class AuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("expires_in") val expiresIn: Int
)

data class FlightSearchResponse(
    val data: List<FlightOffer>
)

data class FlightOffer(
    val id: String,
    val price: Price,
    val itineraries: List<Itinerary>
)

data class Price(
    val currency: String,
    val total: String
)

data class Itinerary(
    val duration: String,
    val segments: List<Segment>
)

data class Segment(
    val departure: FlightEvent,
    val arrival: FlightEvent,
    val carrierCode: String
)

data class FlightEvent(
    val iataCode: String,
    val at: String
)