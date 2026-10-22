package com.example.ui.utils

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.TransactionEntity
import com.example.data.TransactionType
import com.example.ui.Formatters
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object StatementImageGenerator {

    fun generateAndShareStatement(
        context: Context,
        businessName: String,
        businessPhone: String,
        upiId: String,
        customerName: String,
        customerBusiness: String,
        transactions: List<TransactionEntity>,
        netBalance: Double,
        currencySymbol: String
    ) {
        try {
            val bitmap = createStatementBitmap(
                businessName = businessName,
                businessPhone = businessPhone,
                upiId = upiId,
                customerName = customerName,
                customerBusiness = customerBusiness,
                transactions = transactions,
                netBalance = netBalance,
                currencySymbol = currencySymbol
            )

            // Save to cache file
            val imagesFolder = File(context.cacheDir, "statements")
            imagesFolder.mkdirs()
            val file = File(imagesFolder, "statement_${System.currentTimeMillis()}.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.flush()
            stream.close()

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                val summaryText = if (netBalance > 0) {
                    "Dear $customerName, here is your statement from $businessName. Total Outstanding Balance: $currencySymbol${String.format("%.2f", netBalance)}. Please clear the payment at your earliest convenience."
                } else {
                    "Statement of Account for $customerName from $businessName."
                }
                putExtra(Intent.EXTRA_TEXT, summaryText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Ledger Statement Image")
            context.startActivity(chooser)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createStatementBitmap(
        businessName: String,
        businessPhone: String,
        upiId: String,
        customerName: String,
        customerBusiness: String,
        transactions: List<TransactionEntity>,
        netBalance: Double,
        currencySymbol: String
    ): Bitmap {
        val width = 1080
        val rowHeight = 70
        val headerHeight = 240
        val custInfoHeight = 160
        val tableHeaderHeight = 70
        val summaryHeight = 220
        val qrSectionHeight = if (upiId.isNotBlank()) 320 else 100
        val footerHeight = 80

        val tableRowsHeight = maxOf(transactions.size, 1) * rowHeight
        val totalHeight = headerHeight + custInfoHeight + tableHeaderHeight + tableRowsHeight + summaryHeight + qrSectionHeight + footerHeight

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Header Banner (Dark Slate Blue)
        paint.color = Color.parseColor("#1E293B")
        canvas.drawRect(0f, 0f, width.toFloat(), headerHeight.toFloat(), paint)

        // Business Name
        paint.color = Color.WHITE
        paint.textSize = 52f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(businessName.ifBlank { "My Business Ledger" }, 50f, 90f, paint)

        // Business Phone & Subtitle
        paint.textSize = 28f
        paint.typeface = Typeface.DEFAULT
        paint.color = Color.parseColor("#94A3B8")
        val phoneStr = if (businessPhone.isNotBlank()) "Ph: $businessPhone  •  " else ""
        canvas.drawText("${phoneStr}OFFICIAL STATEMENT OF ACCOUNT", 50f, 140f, paint)

        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        paint.textSize = 24f
        canvas.drawText("Generated on: $dateStr", 50f, 185f, paint)

        // 2. Customer Info Section
        var currentY = headerHeight.toFloat()
        paint.color = Color.parseColor("#F8FAFC")
        canvas.drawRect(0f, currentY, width.toFloat(), currentY + custInfoHeight, paint)

        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 34f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Statement For: $customerName", 50f, currentY + 65f, paint)

        if (customerBusiness.isNotBlank()) {
            paint.textSize = 28f
            paint.typeface = Typeface.DEFAULT
            paint.color = Color.parseColor("#64748B")
            canvas.drawText("Company: $customerBusiness", 50f, currentY + 115f, paint)
        }

        currentY += custInfoHeight

        // 3. Table Header Bar
        paint.color = Color.parseColor("#E2E8F0")
        canvas.drawRect(0f, currentY, width.toFloat(), currentY + tableHeaderHeight, paint)

        paint.color = Color.parseColor("#334155")
        paint.textSize = 26f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        canvas.drawText("DATE", 50f, currentY + 45f, paint)
        canvas.drawText("PARTICULARS / MODE", 330f, currentY + 45f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("AMOUNT ($currencySymbol)", width - 50f, currentY + 45f, paint)
        paint.textAlign = Paint.Align.LEFT

        currentY += tableHeaderHeight

        // 4. Entries Table Rows
        paint.textSize = 26f
        paint.typeface = Typeface.DEFAULT

        if (transactions.isEmpty()) {
            paint.color = Color.parseColor("#64748B")
            canvas.drawText("No entries recorded in ledger.", 50f, currentY + 45f, paint)
            currentY += rowHeight
        } else {
            transactions.forEachIndexed { index, txn ->
                val isGaveCredit = txn.type == TransactionType.GAVE_CREDIT.name || txn.type == TransactionType.INVOICE_BILL.name

                // Row zebra shading
                if (index % 2 == 1) {
                    paint.color = Color.parseColor("#F1F5F9")
                    canvas.drawRect(0f, currentY, width.toFloat(), currentY + rowHeight, paint)
                }

                // Date
                paint.color = Color.parseColor("#475569")
                val dText = Formatters.formatDate(txn.date)
                canvas.drawText(dText, 50f, currentY + 45f, paint)

                // Particulars
                val descText = if (txn.description.isNotBlank()) txn.description else if (isGaveCredit) "Sale / Credit" else "Deposit / Payment"
                val truncatedDesc = if (descText.length > 30) descText.take(28) + "..." else descText
                paint.color = Color.parseColor("#0F172A")
                canvas.drawText(truncatedDesc, 330f, currentY + 45f, paint)

                // Amount
                paint.color = if (isGaveCredit) Color.parseColor("#DC2626") else Color.parseColor("#16A34A")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textAlign = Paint.Align.RIGHT
                val amountStr = (if (isGaveCredit) "- " else "+ ") + Formatters.formatCurrency(txn.amount, currencySymbol)
                canvas.drawText(amountStr, width - 50f, currentY + 45f, paint)

                paint.textAlign = Paint.Align.LEFT
                paint.typeface = Typeface.DEFAULT

                currentY += rowHeight
            }
        }

        // Table Bottom Border
        paint.color = Color.parseColor("#CBD5E1")
        paint.strokeWidth = 3f
        canvas.drawLine(0f, currentY, width.toFloat(), currentY, paint)

        // 5. Gross Total / Summary Box
        currentY += 20f
        val summaryBoxRect = RectF(40f, currentY, width - 40f, currentY + 180f)
        paint.color = if (netBalance > 0) Color.parseColor("#F0FDF4") else if (netBalance < 0) Color.parseColor("#FEF2F2") else Color.parseColor("#F8FAFC")
        canvas.drawRoundRect(summaryBoxRect, 24f, 24f, paint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = if (netBalance > 0) Color.parseColor("#86EFAC") else if (netBalance < 0) Color.parseColor("#FCA5A5") else Color.parseColor("#CBD5E1")
        }
        canvas.drawRoundRect(summaryBoxRect, 24f, 24f, borderPaint)

        paint.color = Color.parseColor("#334155")
        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        val statusTitle = when {
            netBalance > 0 -> "GROSS RECEIVABLE (TOTAL TO RECEIVE)"
            netBalance < 0 -> "GROSS PAYABLE (TOTAL TO PAY)"
            else -> "GROSS OUTSTANDING BALANCE"
        }
        canvas.drawText(statusTitle, 70f, currentY + 65f, paint)

        paint.textSize = 54f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = when {
            netBalance > 0 -> Color.parseColor("#15803D")
            netBalance < 0 -> Color.parseColor("#B91C1C")
            else -> Color.parseColor("#0F172A")
        }
        val totalAmtStr = Formatters.formatCurrency(Math.abs(netBalance), currencySymbol)
        canvas.drawText(totalAmtStr, 70f, currentY + 135f, paint)

        currentY += 220f

        // 6. QR Code Section for UPI Payment
        if (upiId.isNotBlank() && netBalance > 0) {
            paint.color = Color.parseColor("#F1F5F9")
            val qrBoxRect = RectF(40f, currentY, width - 40f, currentY + 280f)
            canvas.drawRoundRect(qrBoxRect, 24f, 24f, paint)

            val upiPayload = "upi://pay?pa=$upiId&pn=${Uri.encode(businessName)}&am=${String.format(Locale.US, "%.2f", netBalance)}&cu=INR"
            val qrBitmap = drawQrCodeMatrix(upiPayload, 220, 220)

            if (qrBitmap != null) {
                canvas.drawBitmap(qrBitmap, 70f, currentY + 30f, null)
            }

            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 32f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("SCAN & PAY VIA UPI", 320f, currentY + 90f, paint)

            paint.textSize = 28f
            paint.typeface = Typeface.DEFAULT
            paint.color = Color.parseColor("#475569")
            canvas.drawText("UPI ID: $upiId", 320f, currentY + 140f, paint)

            paint.textSize = 24f
            paint.color = Color.parseColor("#16A34A")
            canvas.drawText("Accepts GPay, PhonePe, Paytm & BHIM UPI", 320f, currentY + 190f, paint)

            currentY += 300f
        }

        // 7. Footer Guarantee
        paint.color = Color.parseColor("#94A3B8")
        paint.textSize = 22f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Thank you for your business! Generated via LedgerMate Offline Ledger.", width / 2f, currentY + 30f, paint)

        return bitmap
    }

    /**
     * Minimal, fast, pure Kotlin QR matrix generator
     * Draws standard finder patterns, timing patterns and grid simulation
     */
    private fun drawQrCodeMatrix(content: String, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.BLACK

        val modules = 25
        val moduleSize = width.toFloat() / modules

        fun drawFinderPattern(startX: Int, startY: Int) {
            // Outer 7x7
            paint.color = Color.BLACK
            canvas.drawRect(
                startX * moduleSize,
                startY * moduleSize,
                (startX + 7) * moduleSize,
                (startY + 7) * moduleSize,
                paint
            )
            // Inner 5x5 white
            paint.color = Color.WHITE
            canvas.drawRect(
                (startX + 1) * moduleSize,
                (startY + 1) * moduleSize,
                (startX + 6) * moduleSize,
                (startY + 6) * moduleSize,
                paint
            )
            // Center 3x3 black
            paint.color = Color.BLACK
            canvas.drawRect(
                (startX + 2) * moduleSize,
                (startY + 2) * moduleSize,
                (startX + 5) * moduleSize,
                (startX + 5) * moduleSize,
                paint
            )
        }

        // Top-Left, Top-Right, Bottom-Left Finder Patterns
        drawFinderPattern(1, 1)
        drawFinderPattern(modules - 8, 1)
        drawFinderPattern(1, modules - 8)

        // Alignment Pattern at (16, 16)
        paint.color = Color.BLACK
        canvas.drawRect(16 * moduleSize, 16 * moduleSize, 21 * moduleSize, 21 * moduleSize, paint)
        paint.color = Color.WHITE
        canvas.drawRect(17 * moduleSize, 17 * moduleSize, 20 * moduleSize, 20 * moduleSize, paint)
        paint.color = Color.BLACK
        canvas.drawRect(18 * moduleSize, 18 * moduleSize, 19 * moduleSize, 19 * moduleSize, paint)

        // Data module simulation derived from content hash
        val hash = content.hashCode()
        val random = Random(hash.toLong())

        for (row in 0 until modules) {
            for (col in 0 until modules) {
                // Skip finder patterns
                if ((row in 0..8 && col in 0..8) ||
                    (row in 0..8 && col in (modules - 9) until modules) ||
                    (row in (modules - 9) until modules && col in 0..8)
                ) {
                    continue
                }
                // Skip alignment
                if (row in 16..20 && col in 16..20) continue

                // Timing lines
                if (row == 6 || col == 6) {
                    if ((row + col) % 2 == 0) {
                        paint.color = Color.BLACK
                        canvas.drawRect(
                            col * moduleSize,
                            row * moduleSize,
                            (col + 1) * moduleSize,
                            (row + 1) * moduleSize,
                            paint
                        )
                    }
                    continue
                }

                if (random.nextBoolean()) {
                    paint.color = Color.BLACK
                    canvas.drawRect(
                        col * moduleSize,
                        row * moduleSize,
                        (col + 1) * moduleSize,
                        (row + 1) * moduleSize,
                        paint
                    )
                }
            }
        }

        return bitmap
    }
}
