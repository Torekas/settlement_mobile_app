package com.example.mojerozliczenia

import java.text.Normalizer

object LogoUtils {
    fun getLogoUrl(merchantName: String): String {
        // 1. Usuwamy polskie znaki (np. Żabka -> Zabka)
        val normalized = Normalizer.normalize(merchantName, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

        // 2. Usuwamy znaki specjalne i spacje
        val cleanName = normalized.lowercase()
            .replace(" ", "")
            .replace(Regex("[^a-z0-9]"), "")

        // 3. Tworzymy URL do Clearbit
        // np. https://logo.clearbit.com/netflix.com
        return "https://logo.clearbit.com/$cleanName.com"
    }
}