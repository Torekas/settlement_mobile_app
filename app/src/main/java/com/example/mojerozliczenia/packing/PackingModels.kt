package com.example.mojerozliczenia.packing

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "packing_items")
data class PackingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val name: String,
    val isPacked: Boolean = false
)

// Inteligentne sugestie - gotowe listy
object PackingSuggestions {
    val templates = mapOf(
        "🏖️ Plaża" to listOf(
            "Krem z filtrem",
            "Ręcznik plażowy",
            "Okulary przeciwsłoneczne",
            "Strój kąpielowy",
            "Klapki",
            "Kapelusz",
            "Woda do picia",
            "Przekąski",
            "Torba plażowa",
            "Parasolka plażowa",
            "Chusteczki nawilżane",
            "Książka lub czytnik",
            "Pokrowiec na telefon",
            "Mata plażowa"
        ),

        "🏔️ Góry" to listOf(
            "Buty trekkingowe",
            "Kurtka przeciwdeszczowa",
            "Latarka czołowa",
            "Apteczka",
            "Mapa",
            "Powerbank",
            "Plecak trekkingowy",
            "Batoniki energetyczne",
            "Czapka i rękawiczki",
            "Buff lub chusta",
            "Termos z herbatą",
            "Okrycie przeciw wiatrowe",
            "Kijki trekkingowe",
            "Folia NRC"
        ),

        "🏙️ Miasto" to listOf(
            "Wygodne buty",
            "Powerbank",
            "Portfel",
            "Dokumenty",
            "Ładowarka",
            "Parasol",
            "Butelka wody",
            "Słuchawki",
            "Karta miejska / bilety",
            "Kosmetyczka mini",
            "Mapa offline / aplikacja",
            "Kurtka lekka",
            "Okulary przeciwsłoneczne",
            "Środek do dezynfekcji rąk"
        ),

        "🥶 Zima" to listOf(
            "Czapka i rękawiczki",
            "Termos",
            "Ciepłe skarpety",
            "Krem ochronny",
            "Szalik",
            "Bielizna termiczna",
            "Ocieplane buty",
            "Kurtka zimowa",
            "Kieszonkowe ogrzewacze",
            "Balsam do ust",
            "Latarka",
            "Termoaktywna bluza",
            "Okulary chroniące od śniegu",
            "Powerbank (baterie szybciej padają na mrozie)"
        )
    )
}
