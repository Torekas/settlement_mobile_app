package com.example.mojerozliczenia

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round

data class Debt(
    val fromUserId: Long,
    val toUserId: Long,
    val amount: Double
)

object BalanceUtils {

    // Saldo ujemne = dłużnik, Saldo dodatnie = nadpłacający
    fun calculateBalances(
        transactions: List<Transaction>,
        splits: List<TransactionSplit>
    ): Map<Long, Double> {
        val balances = mutableMapOf<Long, Double>()
        val splitsByTx = splits.groupBy { it.transactionId }

        transactions.forEach { tx ->
            // Przeliczamy na walutę główną
            val normalizedAmount = tx.amount * tx.exchangeRate

            // Płatnik zyskuje (zapłacił za grupę)
            balances[tx.payerId] = balances.getOrDefault(tx.payerId, 0.0) + normalizedAmount

            val txSplits = splitsByTx[tx.transactionId] ?: emptyList()
            if (txSplits.isNotEmpty()) {
                val splitAmount = normalizedAmount / txSplits.size
                txSplits.forEach { split ->
                    // Beneficjent traci (skonsumował)
                    balances[split.beneficiaryId] = balances.getOrDefault(split.beneficiaryId, 0.0) - splitAmount
                }
            }
        }
        return balances
    }

    // 2. Obliczanie długów (korzysta z powyższej funkcji)
    fun calculateDebts(
        transactions: List<Transaction>,
        splits: List<TransactionSplit>
    ): List<Debt> {
        val balances = calculateBalances(transactions, splits).toMutableMap()

        // Minimalizacja długów
        val debts = mutableListOf<Debt>()
        val debtors = balances.filter { it.value < -0.01 }.toMutableMap()
        val creditors = balances.filter { it.value > 0.01 }.toMutableMap()

        while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
            val debtor = debtors.entries.first()
            val creditor = creditors.entries.first()
            val amount = min(abs(debtor.value), creditor.value)

            debts.add(Debt(debtor.key, creditor.key, round(amount * 100) / 100.0))

            val newDebtorVal = debtor.value + amount
            val newCreditorVal = creditor.value - amount

            if (abs(newDebtorVal) < 0.01) debtors.remove(debtor.key) else debtors[debtor.key] = newDebtorVal
            if (abs(newCreditorVal) < 0.01) creditors.remove(creditor.key) else creditors[creditor.key] = newCreditorVal
        }
        return debts
    }
}