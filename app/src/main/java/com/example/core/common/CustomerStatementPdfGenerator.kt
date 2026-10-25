package com.example.core.common

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.CustomerEntity
import com.example.data.TransactionEntity
import com.example.data.TransactionType
import com.example.ui.Formatters
import java.io.File
import java.io.FileOutputStream

/**
 * Generates a dated, running-balance Customer Statement PDF and opens the share sheet (e.g. WhatsApp)
 * so shopkeepers can settle disputes quickly and share proof of dues (#27).
 */
object CustomerStatementPdfGenerator {

    fun generateAndShareStatement(
        context: Context,
        customer: CustomerEntity,
        transactions: List<TransactionEntity>,
        businessName: String,
        businessPhone: String,
        businessAddress: String = "",
        upiId: String,
        currencySymbol: String
    ) {
        try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595 // A4 width in points
            val pageHeight = 842 // A4 height in points

            val sortedAsc = transactions.sortedBy { it.date }
            var runningBalance = 0.0
            val rows = sortedAsc.map { txn ->
                val isSale = txn.type == TransactionType.GAVE_CREDIT.name || txn.type == TransactionType.INVOICE_BILL.name
                if (isSale) {
                    runningBalance += txn.amount
                } else {
                    runningBalance -= txn.amount
                }
                Triple(txn, isSale, runningBalance)
            }

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(16, 185, 129)
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(55, 65, 81)
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(17, 24, 39)
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(31, 41, 55)
                textSize = 10f
            }
            val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(5, 150, 105)
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(220, 38, 38)
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(229, 231, 235)
                strokeWidth = 1f
            }
            val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(243, 244, 246)
                style = Paint.Style.FILL
            }

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas: Canvas = page.canvas
            var y = 48f

            // Store Header
            val storeTitle = businessName.ifBlank { "LedgerMate Store" }
            canvas.drawText(storeTitle, 40f, y, titlePaint)
            y += 18f
            val storeMeta = buildList {
                if (businessAddress.isNotBlank()) add(businessAddress)
                if (businessPhone.isNotBlank()) add("Phone: $businessPhone")
                if (upiId.isNotBlank()) add("UPI: $upiId")
            }.joinToString("  •  ")
            if (storeMeta.isNotBlank()) {
                canvas.drawText(storeMeta, 40f, y, subHeaderPaint)
                y += 16f
            }
            canvas.drawText("Statement Date: ${Formatters.formatDate(System.currentTimeMillis())}", 40f, y, subHeaderPaint)
            y += 20f
            canvas.drawLine(40f, y, (pageWidth - 40).toFloat(), y, linePaint)
            y += 22f

            // Customer Info
            canvas.drawText("Customer Account Statement", 40f, y, boldPaint)
            y += 16f
            val custLine = buildList {
                add(customer.name)
                if (customer.area.isNotBlank()) add("Area: ${customer.area}")
                if (customer.phone.isNotBlank()) add("Mob: ${customer.phone}")
            }.joinToString("  •  ")
            canvas.drawText(custLine, 40f, y, subHeaderPaint)
            y += 18f

            val totalSale = rows.filter { it.second }.sumOf { it.first.amount }
            val totalReceived = rows.filter { !it.second }.sumOf { it.first.amount }
            val netDue = totalSale - totalReceived
            val netLabel = when {
                netDue > 0.005 -> "Net Receivable: ${Formatters.formatCurrency(netDue, currencySymbol)}"
                netDue < -0.005 -> "Net Payable: ${Formatters.formatCurrency(kotlin.math.abs(netDue), currencySymbol)}"
                else -> "Net Balance: Settled (${currencySymbol}0)"
            }
            canvas.drawText(
                "Total Sale on credit: ${Formatters.formatCurrency(totalSale, currencySymbol)}   |   Payment received: ${Formatters.formatCurrency(totalReceived, currencySymbol)}   |   $netLabel",
                40f,
                y,
                boldPaint
            )
            y += 22f

            fun drawTableHeader(c: Canvas, currentY: Float): Float {
                c.drawRect(40f, currentY - 14f, (pageWidth - 40).toFloat(), currentY + 8f, headerBgPaint)
                c.drawText("Date", 46f, currentY, boldPaint)
                c.drawText("Description / Type", 120f, currentY, boldPaint)
                c.drawText("Sale (+)", 330f, currentY, boldPaint)
                c.drawText("Received (-)", 415f, currentY, boldPaint)
                c.drawText("Balance", 495f, currentY, boldPaint)
                return currentY + 22f
            }

            y = drawTableHeader(canvas, y)

            for ((txn, isSale, bal) in rows) {
                if (y > pageHeight - 60f) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    y = 48f
                    y = drawTableHeader(canvas, y)
                }

                val dateStr = Formatters.formatDate(txn.date)
                val typeLabel = if (isSale) "Sale on credit" else "Payment received"
                val desc = txn.description.ifBlank { typeLabel }.take(32)
                canvas.drawText(dateStr, 46f, y, cellPaint)
                canvas.drawText(desc, 120f, y, cellPaint)

                if (isSale) {
                    canvas.drawText(Formatters.formatCurrency(txn.amount, currencySymbol), 330f, y, redPaint)
                    canvas.drawText("-", 415f, y, cellPaint)
                } else {
                    canvas.drawText("-", 330f, y, cellPaint)
                    canvas.drawText(Formatters.formatCurrency(txn.amount, currencySymbol), 415f, y, greenPaint)
                }

                val balText = Formatters.formatCurrency(kotlin.math.abs(bal), currencySymbol) +
                    if (bal > 0.005) " Dr" else if (bal < -0.005) " Cr" else ""
                canvas.drawText(balText, 495f, y, if (bal > 0.005) greenPaint else cellPaint)

                y += 8f
                canvas.drawLine(40f, y, (pageWidth - 40).toFloat(), y, linePaint)
                y += 16f
            }

            if (upiId.isNotBlank() && netDue > 0.005) {
                y += 10f
                val amountParam = String.format(java.util.Locale.US, "%.2f", netDue)
                canvas.drawText(
                    "Pay via UPI: $upiId (upi://pay?pa=$upiId&am=$amountParam&cu=INR)",
                    40f,
                    y.coerceAtMost(pageHeight - 30f),
                    greenPaint
                )
            }

            pdfDocument.finishPage(page)

            val exportDir = File(context.cacheDir, "receipts").apply { mkdirs() }
            val safeName = customer.name.replace("[^a-zA-Z0-9_-]".toRegex(), "_").ifBlank { "Customer" }
            val pdfFile = File(exportDir, "Statement_${safeName}_${System.currentTimeMillis() % 100000}.pdf")
            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Account Statement for ${customer.name} from $storeTitle.\n$netLabel" +
                        if (upiId.isNotBlank() && netDue > 0.005) "\nPay via UPI: $upiId" else ""
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Customer Statement PDF"))
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to generate PDF statement: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
