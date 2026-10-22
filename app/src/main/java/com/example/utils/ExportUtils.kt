package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.*
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

object ExportUtils {

    fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }

    fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString())
                sb.setLength(0)
            } else {
                sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }

    fun generateCompleteDatabaseCsv(
        customers: List<CustomerEntity>,
        transactions: List<TransactionEntity>,
        invoices: List<InvoiceEntity>,
        rateItems: List<RateItemEntity>
    ): String {
        val sb = StringBuilder()

        // 1. CUSTOMERS
        sb.append("# SECTION: CUSTOMERS\n")
        sb.append("id,name,phone,businessName,partyType,notes,createdAt\n")
        customers.forEach { c ->
            sb.append(escapeCsv(c.id)).append(",")
            sb.append(escapeCsv(c.name)).append(",")
            sb.append(escapeCsv(c.phone)).append(",")
            sb.append(escapeCsv(c.businessName)).append(",")
            sb.append(escapeCsv(c.partyType)).append(",")
            sb.append(escapeCsv(c.notes)).append(",")
            sb.append(c.createdAt).append("\n")
        }

        // 2. TRANSACTIONS
        sb.append("\n# SECTION: TRANSACTIONS\n")
        sb.append("id,customerId,amount,type,description,date,invoiceRef,paymentMethod,status\n")
        transactions.forEach { t ->
            sb.append(escapeCsv(t.id)).append(",")
            sb.append(escapeCsv(t.customerId)).append(",")
            sb.append(String.format(Locale.US, "%.2f", t.amount)).append(",")
            sb.append(escapeCsv(t.type)).append(",")
            sb.append(escapeCsv(t.description)).append(",")
            sb.append(t.date).append(",")
            sb.append(escapeCsv(t.invoiceRef)).append(",")
            sb.append(escapeCsv(t.paymentMethod)).append(",")
            sb.append(escapeCsv(t.status)).append("\n")
        }

        // 3. INVOICES
        sb.append("\n# SECTION: INVOICES\n")
        sb.append("id,invoiceNumber,customerId,customerName,issueDate,dueDate,itemsJson,subtotal,taxAmount,discountAmount,totalAmount,paidAmount,status,notes\n")
        invoices.forEach { inv ->
            sb.append(escapeCsv(inv.id)).append(",")
            sb.append(escapeCsv(inv.invoiceNumber)).append(",")
            sb.append(escapeCsv(inv.customerId)).append(",")
            sb.append(escapeCsv(inv.customerName)).append(",")
            sb.append(inv.issueDate).append(",")
            sb.append(inv.dueDate).append(",")
            sb.append(escapeCsv(inv.itemsJson)).append(",")
            sb.append(String.format(Locale.US, "%.2f", inv.subtotal)).append(",")
            sb.append(String.format(Locale.US, "%.2f", inv.taxAmount)).append(",")
            sb.append(String.format(Locale.US, "%.2f", inv.discountAmount)).append(",")
            sb.append(String.format(Locale.US, "%.2f", inv.totalAmount)).append(",")
            sb.append(String.format(Locale.US, "%.2f", inv.paidAmount)).append(",")
            sb.append(escapeCsv(inv.status)).append(",")
            sb.append(escapeCsv(inv.notes)).append("\n")
        }

        // 4. RATE_ITEMS
        sb.append("\n# SECTION: RATE_ITEMS\n")
        sb.append("id,name,category,unit,qty,buyRate,sellPrice,sellPriceStr,minSellPrice,maxSellPrice,lastUpdated\n")
        rateItems.forEach { r ->
            sb.append(escapeCsv(r.id)).append(",")
            sb.append(escapeCsv(r.name)).append(",")
            sb.append(escapeCsv(r.category)).append(",")
            sb.append(escapeCsv(r.unit)).append(",")
            sb.append(String.format(Locale.US, "%.2f", r.qty)).append(",")
            sb.append(String.format(Locale.US, "%.2f", r.buyRate)).append(",")
            sb.append(String.format(Locale.US, "%.2f", r.sellPrice)).append(",")
            sb.append(escapeCsv(r.sellPriceStr)).append(",")
            sb.append(String.format(Locale.US, "%.2f", r.minSellPrice)).append(",")
            sb.append(String.format(Locale.US, "%.2f", r.maxSellPrice)).append(",")
            sb.append(r.lastUpdated).append("\n")
        }

        return sb.toString()
    }

    data class ParsedDatabaseBackup(
        val customers: List<CustomerEntity> = emptyList(),
        val transactions: List<TransactionEntity> = emptyList(),
        val invoices: List<InvoiceEntity> = emptyList(),
        val rateItems: List<RateItemEntity> = emptyList()
    )

    fun parseCompleteDatabaseCsv(csvContent: String): ParsedDatabaseBackup {
        val customers = mutableListOf<CustomerEntity>()
        val transactions = mutableListOf<TransactionEntity>()
        val invoices = mutableListOf<InvoiceEntity>()
        val rateItems = mutableListOf<RateItemEntity>()

        var currentSection = ""
        val lines = csvContent.lines()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue

            if (line.startsWith("# SECTION:")) {
                currentSection = line.removePrefix("# SECTION:").trim()
                continue
            }

            // Skip table column headers
            if (line.startsWith("id,") || line.startsWith("Date,") || line.startsWith("Item Name,")) {
                continue
            }

            val cols = parseCsvLine(rawLine)

            try {
                when (currentSection) {
                    "CUSTOMERS" -> {
                        if (cols.size >= 2) {
                            val id = cols.getOrNull(0)?.ifBlank { UUID.randomUUID().toString() } ?: UUID.randomUUID().toString()
                            val name = cols.getOrNull(1) ?: ""
                            if (name.isNotBlank()) {
                                customers.add(
                                    CustomerEntity(
                                        id = id,
                                        name = name,
                                        phone = cols.getOrNull(2) ?: "",
                                        businessName = cols.getOrNull(3) ?: "",
                                        partyType = cols.getOrNull(4) ?: CustomerEntity.PARTY_TYPE_CUSTOMER,
                                        notes = cols.getOrNull(5) ?: "",
                                        createdAt = cols.getOrNull(6)?.toLongOrNull() ?: System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    }
                    "TRANSACTIONS" -> {
                        if (cols.size >= 3) {
                            val id = cols.getOrNull(0)?.ifBlank { UUID.randomUUID().toString() } ?: UUID.randomUUID().toString()
                            val customerId = cols.getOrNull(1) ?: ""
                            val amount = cols.getOrNull(2)?.toDoubleOrNull() ?: 0.0
                            val type = cols.getOrNull(3) ?: "GAVE_CREDIT"
                            transactions.add(
                                TransactionEntity(
                                    id = id,
                                    customerId = customerId,
                                    amount = amount,
                                    type = type,
                                    description = cols.getOrNull(4) ?: "",
                                    date = cols.getOrNull(5)?.toLongOrNull() ?: System.currentTimeMillis(),
                                    invoiceRef = cols.getOrNull(6) ?: "",
                                    paymentMethod = cols.getOrNull(7) ?: "CASH",
                                    status = cols.getOrNull(8) ?: "COMPLETED"
                                )
                            )
                        }
                    }
                    "INVOICES" -> {
                        if (cols.size >= 4) {
                            val id = cols.getOrNull(0)?.ifBlank { UUID.randomUUID().toString() } ?: UUID.randomUUID().toString()
                            val invNum = cols.getOrNull(1) ?: ""
                            val custId = cols.getOrNull(2) ?: ""
                            val custName = cols.getOrNull(3) ?: ""
                            invoices.add(
                                InvoiceEntity(
                                    id = id,
                                    invoiceNumber = invNum,
                                    customerId = custId,
                                    customerName = custName,
                                    issueDate = cols.getOrNull(4)?.toLongOrNull() ?: System.currentTimeMillis(),
                                    dueDate = cols.getOrNull(5)?.toLongOrNull() ?: (System.currentTimeMillis() + 30L * 24 * 3600 * 1000),
                                    itemsJson = cols.getOrNull(6) ?: "[]",
                                    subtotal = cols.getOrNull(7)?.toDoubleOrNull() ?: 0.0,
                                    taxAmount = cols.getOrNull(8)?.toDoubleOrNull() ?: 0.0,
                                    discountAmount = cols.getOrNull(9)?.toDoubleOrNull() ?: 0.0,
                                    totalAmount = cols.getOrNull(10)?.toDoubleOrNull() ?: 0.0,
                                    paidAmount = cols.getOrNull(11)?.toDoubleOrNull() ?: 0.0,
                                    status = cols.getOrNull(12) ?: InvoiceEntity.STATUS_UNPAID,
                                    notes = cols.getOrNull(13) ?: ""
                                )
                            )
                        }
                    }
                    "RATE_ITEMS" -> {
                        if (cols.size >= 2) {
                            val id = cols.getOrNull(0)?.ifBlank { UUID.randomUUID().toString() } ?: UUID.randomUUID().toString()
                            val name = cols.getOrNull(1) ?: ""
                            if (name.isNotBlank()) {
                                val sellPrice = cols.getOrNull(6)?.toDoubleOrNull() ?: 0.0
                                val sellPriceStr = cols.getOrNull(7) ?: ""
                                val (minP, maxP) = RateItemEntity.computeMinMax(sellPrice, sellPriceStr)

                                rateItems.add(
                                    RateItemEntity(
                                        id = id,
                                        name = name,
                                        category = cols.getOrNull(2) ?: "General",
                                        unit = cols.getOrNull(3) ?: "Kg",
                                        qty = cols.getOrNull(4)?.toDoubleOrNull() ?: 1.0,
                                        buyRate = cols.getOrNull(5)?.toDoubleOrNull() ?: 0.0,
                                        sellPrice = sellPrice,
                                        sellPriceStr = sellPriceStr,
                                        minSellPrice = minP,
                                        maxSellPrice = maxP,
                                        lastUpdated = cols.getOrNull(10)?.toLongOrNull() ?: System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return ParsedDatabaseBackup(customers, transactions, invoices, rateItems)
    }

    fun generateLedgerCsv(
        customers: List<CustomerWithBalance>,
        transactions: List<TransactionEntity> = emptyList(),
        invoices: List<InvoiceEntity> = emptyList()
    ): String {
        val sb = StringBuilder()
        // Scope change: Only Customer Name, Phone, Total Pending Amount
        sb.append("Customer Name,Phone,Total Pending Amount\n")
        customers.sortedByDescending { it.netBalance }.forEach { item ->
            val cust = item.customer
            val phone = cust.phone.ifBlank { "N/A" }
            sb.append(escapeCsv(cust.name)).append(",")
            sb.append(escapeCsv(phone)).append(",")
            sb.append(String.format(Locale.US, "%.2f", item.netBalance)).append("\n")
        }
        return sb.toString()
    }

    fun generateRateListCsv(rateItems: List<RateItemEntity>): String {
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        // CSV Header
        sb.append("Item Name,Category,Unit,Default Quantity,Buying Rate,Selling Price,Margin (%),Last Updated\n")

        rateItems.sortedBy { it.name }.forEach { item ->
            val dateStr = dateFormat.format(Date(item.lastUpdated))
            val margin = if (item.buyRate > 0) ((item.sellPrice - item.buyRate) / item.buyRate) * 100.0 else 0.0

            sb.append(escapeCsv(item.name)).append(",")
            sb.append(escapeCsv(item.category)).append(",")
            sb.append(escapeCsv(item.unit)).append(",")
            sb.append(String.format(Locale.US, "%.2f", item.qty)).append(",")
            sb.append(String.format(Locale.US, "%.2f", item.buyRate)).append(",")
            sb.append(String.format(Locale.US, "%.2f", item.sellPrice)).append(",")
            sb.append(String.format(Locale.US, "%.1f%%", margin)).append(",")
            sb.append(escapeCsv(dateStr)).append("\n")
        }

        return sb.toString()
    }

    fun createTempFile(context: Context, fileName: String, content: String): File {
        val file = File(context.cacheDir, fileName)
        file.writeText(content)
        return file
    }

    fun shareFile(context: Context, file: File, mimeType: String, title: String = "Share File") {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, title))
    }

    fun writeContentToUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(content.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun readTextFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                InputStreamReader(inputStream, Charsets.UTF_8).readText()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    fun generateCustomerPendingBalancesCsv(customers: List<CustomerWithBalance>): String {
        val sb = StringBuilder()
        sb.append("Customer Name,Phone,Total Pending Amount\n")
        customers.forEach { cb ->
            sb.append(escapeCsv(cb.customer.name)).append(",")
            sb.append(escapeCsv(cb.customer.phone)).append(",")
            sb.append(String.format(Locale.US, "%.2f", cb.netBalance)).append("\n")
        }
        return sb.toString()
    }

    fun generateCustomerPendingBalancesHtml(
        businessName: String,
        currencySymbol: String,
        customers: List<CustomerWithBalance>
    ): String {
        val totalPending = customers.sumOf { it.netBalance }
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val dateStr = dateFormat.format(Date())

        val rows = customers.joinToString("") { cb ->
            """
            <tr>
                <td style="padding: 10px; border-bottom: 1px solid #E5E7EB; font-weight: 600;">${cb.customer.name}</td>
                <td style="padding: 10px; border-bottom: 1px solid #E5E7EB; color: #4B5563;">${cb.customer.phone.ifBlank { "-" }}</td>
                <td style="padding: 10px; border-bottom: 1px solid #E5E7EB; text-align: right; font-weight: 700; color: #379358;">$currencySymbol${String.format(Locale.US, "%.2f", cb.netBalance)}</td>
            </tr>
            """.trimIndent()
        }

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <title>Customer Pending Balances</title>
            <style>
                body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; margin: 20px; color: #1F2937; }
                h1 { color: #111827; font-size: 20px; margin-bottom: 4px; }
                p { color: #6B7280; font-size: 13px; margin-top: 0; }
                table { width: 100%; border-collapse: collapse; margin-top: 16px; font-size: 14px; }
                th { background-color: #F3F4F6; padding: 10px; text-align: left; font-weight: 600; border-bottom: 2px solid #E5E7EB; }
                .summary { margin-top: 20px; text-align: right; font-size: 16px; font-weight: 700; }
            </style>
        </head>
        <body>
            <h1>${businessName.ifBlank { "LedgerMate" }} - Customer Balances</h1>
            <p>Generated on $dateStr • Total Customers: ${customers.size}</p>
            <table>
                <thead>
                    <tr>
                        <th>Customer Name</th>
                        <th>Phone</th>
                        <th style="text-align: right;">Total Pending Amount</th>
                    </tr>
                </thead>
                <tbody>
                    $rows
                </tbody>
            </table>
            <div class="summary">
                Total Outstanding: $currencySymbol${String.format(Locale.US, "%.2f", totalPending)}
            </div>
        </body>
        </html>
        """.trimIndent()
    }
}

