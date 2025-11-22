package com.example.mojerozliczenia.packing

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "packing_items")
data class PackingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val name: String,
    val category: String = "Inne",
    val isPacked: Boolean = false
)

object PackingCategories {
    val list = listOf(
        "Ubrania",
        "Dokumenty",
        "Kosmetyki",
        "Elektronika",
        "Apteczka",
        "Jedzenie i picie",
        "Akcesoria",
        "Rozrywka",
        "Inne"
    )
}


// Inteligentne sugestie - gotowe listy
object PackingSuggestions {
    val templates = mapOf(
        "🏖️ Plaża" to listOf(
            "Krem z filtrem" to "Kosmetyki",
            "Ręcznik plażowy" to "Inne",
            "Okulary przeciwsłoneczne" to "Akcesoria",
            "Strój kąpielowy" to "Ubrania",
            "Klapki" to "Ubrania",
            "Kapelusz" to "Akcesoria",
            "Woda do picia" to "Jedzenie i picie",
            "Przekąski" to "Jedzenie i picie",
            "Torba plażowa" to "Akcesoria",
            "Parasolka plażowa" to "Inne",
            "Chusteczki nawilżane" to "Kosmetyki",
            "Książka lub czytnik" to "Rozrywka",
            "Pokrowiec na telefon" to "Akcesoria",
            "Mata plażowa" to "Inne"
        ),

        "🏔️ Góry" to listOf(
            "Buty trekkingowe" to "Ubrania",
            "Kurtka przeciwdeszczowa" to "Ubrania",
            "Latarka czołowa" to "Elektronika",
            "Apteczka" to "Apteczka",
            "Mapa" to "Dokumenty",
            "Powerbank" to "Elektronika",
            "Plecak trekkingowy" to "Akcesoria",
            "Batoniki energetyczne" to "Jedzenie i picie",
            "Czapka i rękawiczki" to "Ubrania",
            "Buff lub chusta" to "Ubrania",
            "Termos z herbatą" to "Jedzenie i picie",
            "Okrycie przeciw wiatrowe" to "Ubrania",
            "Kijki trekkingowe" to "Akcesoria",
            "Folia NRC" to "Apteczka"
        ),

        "🏙️ Miasto" to listOf(
            "Wygodne buty" to "Ubrania",
            "Powerbank" to "Elektronika",
            "Portfel" to "Dokumenty",
            "Dokumenty" to "Dokumenty",
            "Ładowarka" to "Elektronika",
            "Parasol" to "Akcesoria",
            "Butelka wody" to "Jedzenie i picie",
            "Słuchawki" to "Elektronika",
            "Karta miejska / bilety" to "Dokumenty",
            "Kosmetyczka mini" to "Kosmetyki",
            "Mapa offline / aplikacja" to "Akcesoria",
            "Kurtka lekka" to "Ubrania",
            "Okulary przeciwsłoneczne" to "Akcesoria",
            "Środek do dezynfekcji rąk" to "Kosmetyki"
        ),

        "🥶 Zima" to listOf(
            "Czapka i rękawiczki" to "Ubrania",
            "Termos" to "Jedzenie i picie",
            "Ciepłe skarpety" to "Ubrania",
            "Krem ochronny" to "Kosmetyki",
            "Szalik" to "Ubrania",
            "Bielizna termiczna" to "Ubrania",
            "Ocieplane buty" to "Ubrania",
            "Kurtka zimowa" to "Ubrania",
            "Kieszonkowe ogrzewacze" to "Akcesoria",
            "Balsam do ust" to "Kosmetyki",
            "Latarka" to "Elektronika",
            "Termoaktywna bluza" to "Ubrania",
            "Okulary chroniące od śniegu" to "Akcesoria",
            "Powerbank (baterie szybciej padają na mrozie)" to "Elektronika"
        )
    )
}

