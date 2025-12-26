package com.example.mojerozliczenia

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round

object BalanceUtils {

    /**
     * Oblicza salda użytkowników dla listy transakcji w jednej, konkretnej walucie.
     * Nie używamy tutaj exchangeRate, ponieważ liczymy dług w walucie oryginalnej.
     */
    fun calculateBalances(
        transactions: List<Transaction>,
        splits: List<TransactionSplit>
    ): Map<Long, Double> {
        val balances = mutableMapOf<Long, Double>()
        val splitsByTx = splits.groupBy { it.transactionId }

        transactions.forEach { tx ->
            // Pobieramy kwotę w oryginalnej walucie (np. 10.00 EUR)
            val amount = tx.amount

            // Płatnik (payer) dostaje "plus" - tyle założył za innych
            balances[tx.payerId] = balances.getOrDefault(tx.payerId, 0.0) + amount

            val txSplits = splitsByTx[tx.transactionId] ?: emptyList()
            if (txSplits.isNotEmpty()) {
                val totalWeight = txSplits.sumOf { it.weight }

                if (totalWeight > 0) {
                    txSplits.forEach { split ->
                        // Beneficjent dostaje "minus" - tyle skonsumował i jest winien
                        val share = amount * (split.weight / totalWeight)
                        balances[split.beneficiaryId] =
                            balances.getOrDefault(split.beneficiaryId, 0.0) - share
                    }
                }
            }
        }
        return balances
    }

    /**
     * Oblicza plan spłat (kto komu ile) dla konkretnej waluty.
     */
    fun calculateDebts(
        transactions: List<Transaction>,
        splits: List<TransactionSplit>,
        currency: String // Parametr wymagany przez nową klasę Debt
    ): List<Debt> {
        // Obliczamy salda netto (kto jest na plusie, kto na minusie)
        val balances = calculateBalances(transactions, splits).toMutableMap()

        val debts = mutableListOf<Debt>()

        // Filtrujemy dłużników (minus) i wierzycieli (plus)
        val debtors = balances.filter { it.value < -0.01 }.toMutableMap()
        val creditors = balances.filter { it.value > 0.01 }.toMutableMap()

        // Algorytm minimalizacji liczby transakcji
        while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
            val debtor = debtors.entries.first()
            val creditor = creditors.entries.first()

            // Kwota spłaty to minimum z długu dłużnika i nadpłaty wierzyciela
            val amountToSettle = min(abs(debtor.value), creditor.value)

            if (amountToSettle > 0.01) {
                debts.add(
                    Debt(
                        fromUserId = debtor.key,
                        toUserId = creditor.key,
                        amount = round(amountToSettle * 100) / 100.0,
                        currency = currency // Przekazujemy walutę do obiektu długu
                    )
                )
            }

            // Aktualizujemy salda w mapach tymczasowych
            val newDebtorVal = debtor.value + amountToSettle
            val newCreditorVal = creditor.value - amountToSettle

            if (abs(newDebtorVal) < 0.01) debtors.remove(debtor.key) else debtors[debtor.key] = newDebtorVal
            if (abs(newCreditorVal) < 0.01) creditors.remove(creditor.key) else creditors[creditor.key] = newCreditorVal
        }
        return debts
    }
}