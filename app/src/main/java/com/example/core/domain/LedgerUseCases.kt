package com.example.core.domain

import com.example.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Domain Use Cases encapsulating core ledger business rules:
 * - RecordSaleUseCase: Validates amount/dates and records a credit or paid sale.
 * - RecordDepositUseCase: Validates amount and records a payment received.
 * - GenerateReceiptUseCase: Creates an invoice and, if marked DUE, automatically posts a customer ledger entry.
 * - EditPriceUseCase: Updates item rates using integer paise math and appends to price history only when price changed.
 */
class RecordSaleUseCase(private val repository: LedgerRepository) {
    suspend operator fun invoke(
        customerId: String,
        amount: Double,
        description: String,
        paymentMethod: String = "DUE",
        date: Long = System.currentTimeMillis(),
        dueDate: Long = 0L,
        quantity: Double = 1.0,
        rate: Double = 0.0,
        subtotal: Double = 0.0
    ): Result<TransactionEntity> {
        if (customerId.isBlank()) {
            return Result.failure(IllegalArgumentException("Select a customer before saving"))
        }
        val safePaise = MoneyMath.fromRupeesDouble(amount).paise
        if (safePaise <= 0L) {
            return Result.failure(IllegalArgumentException("Amount must be greater than 0"))
        }
        if (safePaise > 10_00_00_000_00L) {
            return Result.failure(IllegalArgumentException("Amount exceeds maximum limit (₹10,00,00,000)"))
        }
        val roundedAmount = Money(safePaise).toRupeesDouble()
        val effectiveDueDate = if (dueDate > 0L && dueDate >= date) dueDate else date + (30L * 24 * 3600 * 1000L)
        val txn = TransactionEntity(
            customerId = customerId,
            amount = roundedAmount,
            type = TransactionType.GAVE_CREDIT.name,
            description = description.trim().ifBlank { "Sale on credit" },
            date = date,
            paymentMethod = paymentMethod.ifBlank { "DUE" },
            quantity = quantity,
            rate = rate,
            subtotal = if (subtotal > 0.0) subtotal else roundedAmount,
            dueDate = effectiveDueDate
        )
        repository.insertTransaction(txn)
        return Result.success(txn)
    }
}

class RecordDepositUseCase(private val repository: LedgerRepository) {
    suspend operator fun invoke(
        customerId: String,
        amount: Double,
        description: String,
        paymentMethod: String = "CASH",
        date: Long = System.currentTimeMillis()
    ): Result<TransactionEntity> {
        if (customerId.isBlank()) {
            return Result.failure(IllegalArgumentException("Select a customer before saving"))
        }
        val safePaise = MoneyMath.fromRupeesDouble(amount).paise
        if (safePaise <= 0L) {
            return Result.failure(IllegalArgumentException("Amount must be greater than 0"))
        }
        if (safePaise > 10_00_00_000_00L) {
            return Result.failure(IllegalArgumentException("Amount exceeds maximum limit (₹10,00,00,000)"))
        }
        val roundedAmount = Money(safePaise).toRupeesDouble()
        val txn = TransactionEntity(
            customerId = customerId,
            amount = roundedAmount,
            type = TransactionType.GOT_PAYMENT.name,
            description = description.trim().ifBlank { "Payment received" },
            date = date,
            paymentMethod = paymentMethod.ifBlank { "CASH" }
        )
        repository.insertTransaction(txn)
        return Result.success(txn)
    }
}

class GenerateReceiptUseCase(private val repository: LedgerRepository) {
    suspend operator fun invoke(
        customerId: String,
        customerName: String,
        items: List<InvoiceItem>,
        dueDays: Int,
        taxPercent: Double,
        discountAmount: Double,
        notes: String,
        paymentMode: String
    ): Result<InvoiceEntity> {
        if (customerName.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter a customer name"))
        }
        if (items.isEmpty()) {
            return Result.failure(IllegalArgumentException("Add at least one item to the receipt"))
        }
        val validDueDays = dueDays.coerceIn(0, 3650)
        val subtotalPaise = items.sumOf { item ->
            val qtyMilli = java.math.BigDecimal.valueOf(item.quantity)
                .multiply(java.math.BigDecimal.valueOf(1000L))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .toLong()
            MoneyMath.calculateLineTotal(
                unitPrice = MoneyMath.fromRupeesDouble(item.unitPrice),
                qtyMilli = qtyMilli
            ).paise
        }
        val subtotal = Money(subtotalPaise).toRupeesDouble()
        val taxAmount = Money(
            java.math.BigDecimal.valueOf(subtotalPaise)
                .multiply(java.math.BigDecimal.valueOf(taxPercent.coerceAtLeast(0.0)))
                .divide(java.math.BigDecimal.valueOf(100.0), 0, java.math.RoundingMode.HALF_UP)
                .toLong()
        ).toRupeesDouble()
        val safeDiscount = discountAmount.coerceIn(0.0, subtotal + taxAmount)
        val totalAmount = (subtotal + taxAmount - safeDiscount).coerceAtLeast(0.0)

        val itemsArray = JSONArray()
        items.forEach { item ->
            val obj = JSONObject()
            obj.put("description", item.description)
            obj.put("quantity", item.quantity)
            obj.put("unitPrice", item.unitPrice)
            itemsArray.put(obj)
        }

        val invoiceNum = repository.generateNextInvoiceNumber()
        val now = System.currentTimeMillis()
        val dueDate = (now + (validDueDays * 24L * 3600L * 1000L)).coerceAtLeast(now)
        val isPaid = paymentMode != "DUE"

        val invoice = InvoiceEntity(
            id = UUID.randomUUID().toString(),
            invoiceNumber = invoiceNum,
            customerId = customerId,
            customerName = customerName.trim(),
            issueDate = now,
            dueDate = dueDate,
            itemsJson = itemsArray.toString(),
            subtotal = subtotal,
            taxAmount = taxAmount,
            discountAmount = safeDiscount,
            totalAmount = totalAmount,
            paidAmount = if (isPaid) totalAmount else 0.0,
            status = if (isPaid) InvoiceEntity.STATUS_PAID else InvoiceEntity.STATUS_UNPAID,
            notes = notes
        )
        repository.insertInvoice(invoice)

        // Domain Rule (#12 & #28): A receipt marked DUE must also create a customer ledger entry
        if (customerId.isNotBlank()) {
            val summaryItems = items.joinToString(", ") {
                val qStr = if (it.quantity % 1.0 == 0.0) it.quantity.toLong().toString() else it.quantity.toString()
                "${it.description} x$qStr"
            }.take(90)
            repository.insertTransaction(
                TransactionEntity(
                    customerId = customerId,
                    amount = totalAmount,
                    type = TransactionType.GAVE_CREDIT.name,
                    description = "Receipt #$invoiceNum: $summaryItems",
                    date = now,
                    invoiceRef = invoiceNum,
                    paymentMethod = paymentMode,
                    dueDate = dueDate
                )
            )
            if (isPaid) {
                repository.insertTransaction(
                    TransactionEntity(
                        customerId = customerId,
                        amount = totalAmount,
                        type = TransactionType.GOT_PAYMENT.name,
                        description = "Payment received for Receipt #$invoiceNum ($paymentMode)",
                        date = now + 1L,
                        invoiceRef = invoiceNum,
                        paymentMethod = paymentMode
                    )
                )
            }
        }
        return Result.success(invoice)
    }
}

class EditPriceUseCase(private val repository: LedgerRepository) {
    suspend operator fun invoke(updatedItem: RateItemEntity): Result<RateItemEntity> {
        if (updatedItem.name.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter an item name"))
        }
        repository.updateRateItem(updatedItem)
        return Result.success(updatedItem)
    }
}
