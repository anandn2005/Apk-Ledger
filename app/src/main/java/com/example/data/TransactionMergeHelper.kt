package com.example.data

import com.example.ui.Formatters
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

object TransactionMergeHelper {

    data class MergePreview(
        val flattenedChildren: List<TransactionEntity>,
        val debitSum: Double,
        val creditSum: Double,
        val netCreditsMinusDebits: Double,
        val fromDate: Long,
        val toDate: Long,
        val defaultTitle: String
    ) {
        val isNetDebit: Boolean get() = netCreditsMinusDebits < -0.005
        val isNetCredit: Boolean get() = netCreditsMinusDebits > 0.005
        val isSettled: Boolean get() = kotlin.math.abs(netCreditsMinusDebits) <= 0.005
        val absNetAmount: Double
            get() = if (isSettled) 0.0 else kotlin.math.abs(netCreditsMinusDebits)
    }

    /**
     * Flattens selected transactions so already-merged parents expand into their
     * underlying children instead of nesting merged entries inside merged entries.
     * Returned children are guaranteed non-merged and sorted ascending by date.
     */
    fun flattenForMerge(selected: List<TransactionEntity>): List<TransactionEntity> {
        val seenIds = LinkedHashSet<String>()
        val result = ArrayList<TransactionEntity>(selected.size)
        for (txn in selected) {
            val candidates = if (txn.isMergedParent() && txn.mergedChildren.isNotEmpty()) {
                txn.mergedChildren
            } else {
                listOf(txn)
            }
            for (child in candidates) {
                if (seenIds.add(child.id)) {
                    result.add(
                        child.copy(
                            isMerged = false,
                            mergedChildrenJson = "[]"
                        )
                    )
                }
            }
        }
        return result.sortedBy { it.date }
    }

    fun previewMerge(selected: List<TransactionEntity>): MergePreview? {
        if (selected.size < 2) return null
        val children = flattenForMerge(selected)
        if (children.isEmpty()) return null

        var debitBd = BigDecimal.ZERO
        var creditBd = BigDecimal.ZERO
        for (child in children) {
            val amtBd = BigDecimal.valueOf(if (child.amount.isFinite()) child.amount else 0.0)
            if (child.isDebitEntry()) {
                debitBd = debitBd.add(amtBd)
            } else {
                creditBd = creditBd.add(amtBd)
            }
        }

        val netBd = creditBd.subtract(debitBd).setScale(2, RoundingMode.HALF_UP)
        val debitSum = debitBd.setScale(2, RoundingMode.HALF_UP).toDouble()
        val creditSum = creditBd.setScale(2, RoundingMode.HALF_UP).toDouble()
        val net = netBd.toDouble()

        val fromDate = children.first().date
        val toDate = children.last().date
        val defaultTitle = Formatters.formatMergedDateRange(fromDate, toDate)

        return MergePreview(
            flattenedChildren = children,
            debitSum = debitSum,
            creditSum = creditSum,
            netCreditsMinusDebits = net,
            fromDate = fromDate,
            toDate = toDate,
            defaultTitle = defaultTitle
        )
    }

    /**
     * Builds exactly ONE merged parent transaction from [selected] entries:
     * - net = sum(credits) - sum(debits)
     * - net < 0 -> GAVE_CREDIT (debit), amount = abs(net), status = "DUE"
     * - net > 0 -> GOT_PAYMENT (credit), amount = net, status = "COMPLETED"
     * - net == 0 -> GOT_PAYMENT, amount = 0.0, status = "PAID"
     * - parent.date = max(children.date)
     * - parent.mergedChildren = flat list sorted ascending by date
     */
    fun buildMergedParent(
        customerId: String,
        selected: List<TransactionEntity>,
        customDescription: String? = null,
        parentId: String = UUID.randomUUID().toString(),
        nowMs: Long = System.currentTimeMillis()
    ): TransactionEntity? {
        val preview = previewMerge(selected) ?: return null
        val type = if (preview.isNetDebit) {
            TransactionType.GAVE_CREDIT.name
        } else {
            TransactionType.GOT_PAYMENT.name
        }
        val status = when {
            preview.isSettled -> "PAID"
            preview.isNetDebit -> "DUE"
            else -> "COMPLETED"
        }
        val title = customDescription?.trim()?.takeIf { it.isNotBlank() } ?: preview.defaultTitle

        return TransactionEntity(
            id = parentId,
            customerId = customerId,
            amount = preview.absNetAmount,
            type = type,
            description = title,
            date = preview.toDate,
            invoiceRef = "",
            paymentMethod = "MERGED",
            status = status,
            lastUpdated = nowMs,
            quantity = 1.0,
            rate = preview.absNetAmount,
            subtotal = preview.absNetAmount,
            dueDate = 0L,
            isMerged = true,
            mergedChildrenJson = TransactionEntity.encodeMergedChildren(preview.flattenedChildren)
        )
    }

    /**
     * Computes customer net receivable balance (debits - credits), matching LedgerRepository.
     */
    fun computeReceivableBalance(transactions: List<TransactionEntity>): Double {
        var debits = BigDecimal.ZERO
        var credits = BigDecimal.ZERO
        for (txn in transactions) {
            val amt = BigDecimal.valueOf(if (txn.amount.isFinite()) txn.amount else 0.0)
            if (txn.isDebitEntry()) {
                debits = debits.add(amt)
            } else {
                credits = credits.add(amt)
            }
        }
        return debits.subtract(credits).setScale(2, RoundingMode.HALF_UP).toDouble()
    }
}
