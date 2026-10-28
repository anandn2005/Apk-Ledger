package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.compose.runtime.Stable
import androidx.compose.runtime.Immutable
import java.util.UUID

// Sealed Domain Result Wrapper for safe error handling & offline resiliency
sealed class Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Resource<Nothing>()
    object Loading : Resource<Nothing>()
}

@Immutable
@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["name"]),
        Index(value = ["phone"])
    ]
)
data class CustomerEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String = "",
    val businessName: String = "",
    val partyType: String = PARTY_TYPE_CUSTOMER, // CUSTOMER or SUPPLIER
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = createdAt,
    val area: String = ""
) {
    @delegate:androidx.room.Ignore
    val searchTokens: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        "${name.lowercase()} ${businessName.lowercase()} ${phone.lowercase()} ${area.lowercase()}"
    }

    companion object {
        const val PARTY_TYPE_CUSTOMER = "CUSTOMER"
        const val PARTY_TYPE_SUPPLIER = "SUPPLIER"
    }
}

enum class TransactionType {
    GAVE_CREDIT, // Money given / Outflow / Receivable increased
    GOT_PAYMENT, // Money received / Inflow / Receivable decreased
    INVOICE_BILL // Invoice issued
}

@Immutable
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["customerId"]),
        Index(value = ["date"]),
        Index(value = ["type"])
    ]
)
data class TransactionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val customerId: String,
    val amount: Double,
    val type: String, // GAVE_CREDIT or GOT_PAYMENT
    val description: String = "",
    val date: Long = System.currentTimeMillis(),
    val invoiceRef: String = "",
    val paymentMethod: String = "CASH", // CASH, UPI, BANK_TRANSFER, CHEQUE
    val status: String = "COMPLETED",
    val lastUpdated: Long = date,
    val quantity: Double = 1.0,
    val rate: Double = 0.0,
    val subtotal: Double = 0.0,
    val dueDate: Long = 0L
)

@Immutable
data class InvoiceItem(
    val description: String,
    val quantity: Double,
    val unitPrice: Double
) {
    val total: Double get() = quantity * unitPrice
}

@Immutable
@Entity(
    tableName = "invoices",
    indices = [
        Index(value = ["customerId"]),
        Index(value = ["issueDate"]),
        Index(value = ["invoiceNumber"], unique = true)
    ]
)
data class InvoiceEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val invoiceNumber: String,
    val customerId: String,
    val customerName: String,
    val issueDate: Long = System.currentTimeMillis(),
    val dueDate: Long,
    val itemsJson: String, // Serialized JSON array of InvoiceItem
    val subtotal: Double,
    val taxAmount: Double = 0.0,
    val discountAmount: Double = 0.0,
    val totalAmount: Double,
    val paidAmount: Double = 0.0,
    val status: String = STATUS_UNPAID, // UNPAID, PARTIAL, PAID, OVERDUE
    val notes: String = "",
    val imagePath: String = "", // Stored visual receipt image file path
    val lastUpdated: Long = issueDate
) {
    @delegate:androidx.room.Ignore
    val parsedItems: List<InvoiceItem> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (itemsJson.isBlank() || itemsJson == "[]") return@lazy emptyList()
        try {
            val array = org.json.JSONArray(itemsJson)
            val list = ArrayList<InvoiceItem>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    InvoiceItem(
                        description = obj.optString("description", ""),
                        quantity = obj.optDouble("quantity", 1.0),
                        unitPrice = obj.optDouble("unitPrice", 0.0)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getItems(): List<InvoiceItem> = parsedItems

    companion object {
        const val STATUS_UNPAID = "UNPAID"
        const val STATUS_PARTIAL = "PARTIAL"
        const val STATUS_PAID = "PAID"
        const val STATUS_OVERDUE = "OVERDUE"
    }
}

@Immutable
@Entity(
    tableName = "audit_logs",
    indices = [
        Index(value = ["timestamp"])
    ]
)
data class AuditLogEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String, // DELETE_TRANSACTION, DELETE_INVOICE, DELETE_CUSTOMER, CREATE_TRANSACTION, CREATE_INVOICE
    val title: String,
    val description: String = "",
    val detailsJson: String, // JSON payload to restore
    val isRestored: Boolean = false
)

@Immutable
data class RateVariant(
    val id: String = UUID.randomUUID().toString(),
    val label: String, // e.g. "1ltr", "500mL", "S", "M", "L"
    val unit: String = "Kg",
    val qty: Double = 1.0,
    val buyRate: Double = 0.0,
    val sellPrice: Double = 0.0,
    val sellPriceStr: String = "",
    val priceHistoryJson: String = "[]"
) {
    private val cachedDisplaySellPrice: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (sellPriceStr.isNotBlank()) {
            val cleanStr = sellPriceStr.substringBefore(" (").trim()
            if (cleanStr != "0" && cleanStr != "0.0" && cleanStr != "0.00") {
                return@lazy cleanStr
            }
        }
        if (sellPrice > 0) {
            return@lazy if (sellPrice % 1.0 == 0.0) sellPrice.toLong().toString() else String.format(java.util.Locale.US, "%.2f", sellPrice)
        }
        ""
    }

    val numericSellPrice: Double by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (sellPrice > 0.0) sellPrice
        else MoneyMath.parseMoneyFromUserInput(sellPriceStr).toRupeesDouble()
    }

    private val cachedBuyNumberStr: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (buyRate <= 0.0) return@lazy ""
        val effective = if (qty > 0.0) buyRate / qty else buyRate
        if (effective % 1.0 == 0.0) effective.toLong().toString()
        else String.format(java.util.Locale.US, "%.2f", effective)
    }

    private val cachedUnitSuffix: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (unit.isNotBlank()) "/${unit.lowercase()}" else ""
    }

    fun getFormattedBuyRate(currencySymbol: String): String {
        if (cachedBuyNumberStr.isEmpty()) return "-"
        return "$currencySymbol$cachedBuyNumberStr$cachedUnitSuffix"
    }

    fun getFormattedSellPrice(currencySymbol: String): String {
        if (sellPriceStr.isNotBlank() && sellPriceStr != "0.00" && sellPriceStr != "0") {
            return sellPriceStr
        }
        if (sellPrice > 0) {
            return "$currencySymbol$cachedDisplaySellPrice"
        }
        return "-"
    }

    fun getDisplaySellPrice(): String = cachedDisplaySellPrice
}

@Immutable
@Entity(
    tableName = "rate_items",
    indices = [
        Index(value = ["category"]),
        Index(value = ["name"]),
        Index(value = ["minSellPrice"]),
        Index(value = ["maxSellPrice"])
    ]
)
data class RateItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String = "General",
    val unit: String = "Kg", // "Kg", "gm", "Ltr", "mL", "Pkt", "Box", "Pcs", "Carton", "Tin"
    val qty: Double = 1.0,
    val buyRate: Double = 0.0,
    val sellPrice: Double = 0.0,
    val sellPriceStr: String = "", // String/Range value like "130-140" or "150"
    val minSellPrice: Double = 0.0,
    val maxSellPrice: Double = 0.0,
    val priceHistoryJson: String = "[]",
    val hasMultipleVariants: Boolean = false,
    val variantsJson: String = "[]",
    val lastUpdated: Long = System.currentTimeMillis()
) {
    @delegate:androidx.room.Ignore
    private val cachedVariants: List<RateVariant> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (variantsJson.isBlank() || variantsJson == "[]") return@lazy emptyList()
        try {
            val jsonArray = org.json.JSONArray(variantsJson)
            val list = ArrayList<RateVariant>(jsonArray.length())
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    RateVariant(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        label = obj.optString("label", ""),
                        unit = obj.optString("unit", unit),
                        qty = obj.optDouble("qty", 1.0),
                        buyRate = obj.optDouble("buyRate", 0.0),
                        sellPrice = obj.optDouble("sellPrice", 0.0),
                        sellPriceStr = obj.optString("sellPriceStr", ""),
                        priceHistoryJson = obj.optString("priceHistoryJson", "[]")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    @delegate:androidx.room.Ignore
    private val cachedHasVariants: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) {
        (hasMultipleVariants || (variantsJson.isNotBlank() && variantsJson != "[]")) && cachedVariants.isNotEmpty()
    }

    @get:androidx.room.Ignore
    @get:JvmName("variantsList")
    val variants: List<RateVariant>
        get() = if (cachedHasVariants) cachedVariants else emptyList()

    @delegate:androidx.room.Ignore
    private val cachedDisplaySellPrice: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (cachedHasVariants) {
            val vList = cachedVariants
            if (vList.isNotEmpty()) {
                val prices = vList.map { it.getDisplaySellPrice() }
                return@lazy prices.joinToString(" / ")
            }
        }
        if (sellPriceStr.isNotBlank()) {
            val cleanStr = sellPriceStr.substringBefore(" (").trim()
            if (cleanStr.isNotBlank()) return@lazy cleanStr
        }
        if (sellPrice > 0) {
            return@lazy if (sellPrice % 1.0 == 0.0) sellPrice.toLong().toString() else String.format(java.util.Locale.US, "%.2f", sellPrice)
        }
        "0"
    }

    @delegate:androidx.room.Ignore
    val numericSellPrice: Double by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (sellPrice > 0.0) sellPrice
        else MoneyMath.parseMoneyFromUserInput(sellPriceStr).toRupeesDouble()
    }

    @delegate:androidx.room.Ignore
    val hasValidSellPrice: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) {
        (sellPriceStr.isNotBlank() && sellPriceStr != "0.00" && sellPriceStr != "0") || sellPrice > 0.0
    }

    @delegate:androidx.room.Ignore
    private val cachedBuyNumberStr: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val effectiveUnitRate = if (qty > 0.0) buyRate / qty else buyRate
        if (effectiveUnitRate % 1.0 == 0.0) {
            effectiveUnitRate.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", effectiveUnitRate)
        }
    }

    @delegate:androidx.room.Ignore
    private val cachedUnitSuffix: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (unit.isNotBlank()) "/${unit.lowercase()}" else "/unit"
    }

    @delegate:androidx.room.Ignore
    val searchTokens: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val variantTokens = if (cachedHasVariants) {
            cachedVariants.joinToString(" ") { it.label.lowercase() }
        } else ""
        "${name.lowercase()} ${category.lowercase()} $variantTokens"
    }

    fun getFormattedBuyRate(currencySymbol: String): String =
        "$currencySymbol$cachedBuyNumberStr$cachedUnitSuffix"

    fun getFormattedSellPrice(currencySymbol: String): String {
        if (sellPriceStr.isNotBlank()) return sellPriceStr
        if (sellPrice > 0.0) return "$currencySymbol$cachedDisplaySellPrice"
        return "${currencySymbol}0.00"
    }

    fun getDisplaySellPrice(): String = cachedDisplaySellPrice

    fun getVariants(): List<RateVariant> = cachedVariants

    fun hasVariants(): Boolean = cachedHasVariants

    companion object {
        fun encodeVariants(variants: List<RateVariant>): String {
            if (variants.isEmpty()) return "[]"
            val array = org.json.JSONArray()
            variants.forEach { v ->
                val obj = org.json.JSONObject()
                obj.put("id", v.id)
                obj.put("label", v.label.trim())
                obj.put("unit", v.unit)
                obj.put("qty", v.qty)
                obj.put("buyRate", v.buyRate)
                obj.put("sellPrice", v.sellPrice)
                obj.put("sellPriceStr", v.sellPriceStr)
                obj.put("priceHistoryJson", v.priceHistoryJson)
                array.put(obj)
            }
            return array.toString()
        }

        fun computeMinMax(sellPrice: Double, sellPriceStr: String): Pair<Double, Double> {
            if (sellPriceStr.isNotBlank()) {
                val cleaned = sellPriceStr.replace("\"", "").trim()
                if (cleaned.contains("-")) {
                    val parts = cleaned.split("-")
                    val min = parts.getOrNull(0)?.trim()?.toDoubleOrNull()
                    val max = parts.getOrNull(1)?.trim()?.toDoubleOrNull()
                    if (min != null && max != null) {
                        return Pair(Math.min(min, max), Math.max(min, max))
                    }
                }
                val direct = cleaned.toDoubleOrNull()
                if (direct != null && direct > 0) {
                    return Pair(direct, direct)
                }
            }
            val base = if (sellPrice > 0) sellPrice else 0.0
            return Pair(base, base)
        }
    }
}

@Immutable
@Entity(
    tableName = "price_history",
    indices = [
        Index(value = ["itemId"]),
        Index(value = ["timestamp"])
    ]
)
data class PriceHistoryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    val buyRate: Double = 0.0,
    val sellPrice: Double = 0.0,
    val sellPriceStr: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Immutable
data class CustomerWithBalance(
    val customer: CustomerEntity,
    val totalGave: Double = 0.0,
    val totalGot: Double = 0.0,
    val netBalance: Double = 0.0, // Positive = Customer owes us (Receivable), Negative = We owe customer (Payable)
    val lastTransactionDate: Long? = null,
    val oldestDueDate: Long? = null
)

@Immutable
data class DailyTrendPoint(
    val dayLabel: String,
    val netVolume: Double,
    val timestamp: Long
)

@Immutable
data class BusinessSummary(
    val totalReceivable: Double = 0.0,
    val totalPayable: Double = 0.0,
    val netCashflow: Double = 0.0,
    val unpaidInvoiceCount: Int = 0,
    val overdueInvoiceCount: Int = 0,
    val customerCount: Int = 0,
    val totalInvoicesAmount: Double = 0.0,
    val sevenDayTrend: List<DailyTrendPoint> = emptyList()
)

@Immutable
data class NoteChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val isChecked: Boolean = false
)

@Immutable
@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["updatedAt"]),
        Index(value = ["label"]),
        Index(value = ["isPinned"]),
        Index(value = ["isDone"]),
        Index(value = ["linkedCustomerId"])
    ]
)
data class NoteEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val label: String = LABEL_GENERAL,
    val isChecklist: Boolean = false,
    val checklistJson: String = "[]",
    val isPinned: Boolean = false,
    val isDone: Boolean = false,
    val linkedCustomerId: String = "",
    val linkedCustomerName: String = "",
    val reminderTime: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
) {
    @delegate:androidx.room.Ignore
    val checklistItems: List<NoteChecklistItem> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        decodeChecklist(checklistJson)
    }

    @delegate:androidx.room.Ignore
    val completedChecklistCount: Int by lazy(LazyThreadSafetyMode.PUBLICATION) {
        checklistItems.count { it.isChecked && it.text.isNotBlank() }
    }

    @delegate:androidx.room.Ignore
    val totalChecklistCount: Int by lazy(LazyThreadSafetyMode.PUBLICATION) {
        checklistItems.count { it.text.isNotBlank() }
    }

    @delegate:androidx.room.Ignore
    val titleText: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val firstTextLine = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        if (!firstTextLine.isNullOrEmpty()) {
            firstTextLine
        } else {
            val firstChecklist = checklistItems.firstOrNull { it.text.isNotBlank() }?.text?.trim()
            firstChecklist ?: ""
        }
    }

    @delegate:androidx.room.Ignore
    val previewText: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val nonEmptyLines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (nonEmptyLines.size > 1) {
            nonEmptyLines.drop(1).joinToString(" • ").take(140)
        } else if (isChecklist && checklistItems.isNotEmpty()) {
            val itemsToJoin = if (nonEmptyLines.isEmpty()) {
                checklistItems.filter { it.text.isNotBlank() }.drop(1)
            } else {
                checklistItems.filter { it.text.isNotBlank() }
            }
            itemsToJoin.joinToString(", ") { (if (it.isChecked) "✓ " else "○ ") + it.text.trim() }.take(140)
        } else {
            ""
        }
    }

    @delegate:androidx.room.Ignore
    val isEmptyNote: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) {
        text.isBlank() && checklistItems.none { it.text.isNotBlank() }
    }

    @delegate:androidx.room.Ignore
    val searchTokens: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val checklistStr = checklistItems.joinToString(" ") { it.text.lowercase() }
        "${text.lowercase()} $checklistStr ${label.lowercase()} ${linkedCustomerName.lowercase()}"
    }

    fun toShareablePlainText(): String {
        val sb = StringBuilder()
        val trimmedText = text.trim()
        if (trimmedText.isNotEmpty()) {
            sb.append(trimmedText)
        }
        val activeItems = checklistItems.filter { it.text.isNotBlank() }
        if (activeItems.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            val unchecked = activeItems.filter { !it.isChecked }
            val checked = activeItems.filter { it.isChecked }
            unchecked.forEach { item ->
                sb.append("☐ ").append(item.text.trim()).append("\n")
            }
            if (checked.isNotEmpty()) {
                if (unchecked.isNotEmpty()) sb.append("\n")
                checked.forEach { item ->
                    sb.append("☑ ").append(item.text.trim()).append("\n")
                }
            }
        }
        if (linkedCustomerName.isNotBlank()) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("Customer: ").append(linkedCustomerName)
        }
        return sb.toString().trim()
    }

    companion object {
        const val LABEL_PURCHASE_LIST = "Purchase List"
        const val LABEL_CUSTOMERS = "Customers"
        const val LABEL_IMPORTANT_DATES = "Important Dates"
        const val LABEL_TO_REMEMBER = "To Remember"
        const val LABEL_GENERAL = "General"

        val DEFAULT_LABELS = listOf(
            LABEL_PURCHASE_LIST,
            LABEL_CUSTOMERS,
            LABEL_IMPORTANT_DATES,
            LABEL_TO_REMEMBER,
            LABEL_GENERAL
        )

        fun encodeChecklist(items: List<NoteChecklistItem>): String {
            if (items.isEmpty()) return "[]"
            val arr = org.json.JSONArray()
            items.forEach { item ->
                val obj = org.json.JSONObject()
                obj.put("id", item.id)
                obj.put("text", item.text)
                obj.put("isChecked", item.isChecked)
                arr.put(obj)
            }
            return arr.toString()
        }

        fun decodeChecklist(json: String): List<NoteChecklistItem> {
            if (json.isBlank() || json == "[]") return emptyList()
            return try {
                val arr = org.json.JSONArray(json)
                val list = ArrayList<NoteChecklistItem>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        NoteChecklistItem(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            text = obj.optString("text", ""),
                            isChecked = obj.optBoolean("isChecked", false)
                        )
                    )
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}


