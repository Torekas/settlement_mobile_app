package com.example.mojerozliczenia

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.regex.Pattern

data class ScanResult(
    val amount: Double?,
    val merchantName: String?
)

object OcrUtils {

    fun scanReceipt(bitmap: Bitmap, onSuccess: (ScanResult) -> Unit, onFailure: () -> Unit) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                // 1. Szukamy kwoty po słowach kluczowych (SUMA, RAZEM)
                var amount = findAmountByKeywords(visionText)

                // 2. Jeśli nie znaleziono, szukamy największej liczby (z filtrami)
                if (amount == null) {
                    amount = findMaxNumber(visionText.text)
                }

                val merchant = findMerchantName(visionText)

                if (amount != null || merchant.isNotEmpty()) {
                    onSuccess(ScanResult(amount, merchant))
                } else {
                    onFailure()
                }
            }
            .addOnFailureListener {
                onFailure()
            }
    }

    private fun findAmountByKeywords(visionText: Text): Double? {
        val keywords = listOf("SUMA", "RAZEM", "DO ZAPLATY", "DO ZAPŁATY", "PLN")
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val text = line.text.uppercase()
                if (keywords.any { text.contains(it) }) {
                    val number = extractNumber(line.text)
                    if (number != null) return number
                }
            }
        }
        return null
    }

    private fun findMaxNumber(fullText: String): Double? {
        val regex = Pattern.compile("\\d+[., ]\\d{2}")
        val matcher = regex.matcher(fullText)
        var maxAmount = 0.0
        var found = false

        while (matcher.find()) {
            try {
                var numStr = matcher.group().replace(",", ".").replace(" ", ".")
                val num = numStr.toDouble()
                // Filtry (lata, małe kwoty, duże liczby)
                if (num >= 2020 && num <= 2030 && (num % 1 == 0.0)) continue
                if (num < 2.0) continue
                if (num > 10000) continue

                if (num > maxAmount) {
                    maxAmount = num
                    found = true
                }
            } catch (e: Exception) { }
        }
        return if (found) maxAmount else null
    }

    private fun extractNumber(text: String): Double? {
        val regex = Pattern.compile("\\d+[.,]\\d{2}")
        val matcher = regex.matcher(text)
        if (matcher.find()) {
            return try {
                matcher.group().replace(",", ".").toDouble()
            } catch (e: Exception) { null }
        }
        return null
    }

    private fun findMerchantName(visionText: Text): String {
        val ignoreList = listOf("PARAGON", "FISKALNY", "NIP", "REGON", "SPRZEDAŻ", "DATA", "GODZ", "PLN", "SUMA", "WITAMY", "FAKTURA", "VAT")
        val blocks = visionText.textBlocks.sortedBy { it.boundingBox?.top ?: 0 }

        for (block in blocks) {
            for (line in block.lines) {
                val text = line.text.uppercase().trim()
                if (text.length < 3) continue
                if (text.any { it.isDigit() }) continue
                if (ignoreList.any { text.contains(it) }) continue
                return text
            }
        }
        return ""
    }
}