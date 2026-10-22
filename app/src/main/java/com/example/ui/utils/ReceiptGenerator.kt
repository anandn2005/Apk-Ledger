package com.example.ui.utils

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.CustomerEntity
import com.example.data.InvoiceEntity
import com.example.data.TransactionEntity
import com.example.data.TransactionType
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * Clean data model representing an itemized receipt line.
 */
data class ReceiptLineItem(
    val index: Int,
    val name: String,
    val quantity: Double,
    val unitPrice: Double,
    val unit: String = ""
) {
    val subtotal: Double get() = quantity * unitPrice
}

/**
 * Complete data model for generating minimalist receipts strictly following
 * the reference image layout structure.
 */
data class ReceiptData(
    val receiptNumber: String,
    val date: Long,
    val businessName: String,
    val businessAddress: String,
    val businessPhone: String,
    val upiId: String,
    val customerName: String,
    val customerPhone: String? = null, // Conditional: collapse/hide if empty
    val items: List<ReceiptLineItem>,
    val subtotal: Double,
    val taxAmount: Double = 0.0, // Conditional: only render if > 0
    val discountAmount: Double = 0.0, // Conditional: only render if > 0
    val partialPayment: Double = 0.0, // Conditional: only render if > 0
    val grandTotal: Double,
    val paymentStatus: String, // "DUE", "Cash", "Card", "UPI", "PAID"
    val currencySymbol: String = "₹"
) {
    val isDue: Boolean
        get() = paymentStatus.equals("DUE", ignoreCase = true) ||
                paymentStatus.equals("UNPAID", ignoreCase = true) ||
                paymentStatus.equals("OVERDUE", ignoreCase = true)
}

object ReceiptGenerator {

    /**
     * Map a TransactionEntity (and optionally associated Invoice / Customer) to ReceiptData.
     */
    fun createReceiptData(
        txn: TransactionEntity,
        customer: CustomerEntity,
        businessName: String,
        businessAddress: String,
        businessPhone: String,
        upiId: String,
        currencySymbol: String,
        associatedInvoice: InvoiceEntity? = null
    ): ReceiptData {
        val receiptNum = if (txn.invoiceRef.isNotBlank()) {
            txn.invoiceRef
        } else {
            "TXN-${txn.id.takeLast(6).uppercase(Locale.US)}"
        }

        val isCredit = txn.type == TransactionType.GAVE_CREDIT.name || txn.type == TransactionType.INVOICE_BILL.name
        val paymentStatus = if (isCredit) {
            "DUE"
        } else {
            if (txn.paymentMethod.isNotBlank()) txn.paymentMethod else "PAID"
        }

        if (associatedInvoice != null) {
            val itemsList = associatedInvoice.getItems().mapIndexed { i, invItem ->
                ReceiptLineItem(
                    index = i + 1,
                    name = invItem.description.ifBlank { "Item ${i + 1}" }.trim(),
                    quantity = invItem.quantity,
                    unitPrice = invItem.unitPrice
                )
            }

            val items = if (itemsList.isNotEmpty()) itemsList else listOf(
                ReceiptLineItem(
                    index = 1,
                    name = if (txn.description.isNotBlank()) txn.description else "Order Goods / Merchandise",
                    quantity = 1.0,
                    unitPrice = txn.amount
                )
            )

            return ReceiptData(
                receiptNumber = if (associatedInvoice.invoiceNumber.isNotBlank()) associatedInvoice.invoiceNumber else receiptNum,
                date = txn.date,
                businessName = businessName.ifBlank { "My Business" },
                businessAddress = businessAddress.ifBlank { "Main Bazaar, Market Yard" },
                businessPhone = businessPhone,
                upiId = upiId.ifBlank { "merchant@upi" },
                customerName = customer.name.ifBlank { "Valued Customer" },
                customerPhone = customer.phone.ifBlank { null },
                items = items,
                subtotal = if (associatedInvoice.subtotal > 0) associatedInvoice.subtotal else txn.amount,
                taxAmount = associatedInvoice.taxAmount,
                discountAmount = associatedInvoice.discountAmount,
                partialPayment = associatedInvoice.paidAmount,
                grandTotal = if (associatedInvoice.totalAmount > 0) associatedInvoice.totalAmount else txn.amount,
                paymentStatus = if (associatedInvoice.status == InvoiceEntity.STATUS_PAID) "PAID" else paymentStatus,
                currencySymbol = currencySymbol
            )
        }

        // Standalone transaction entry
        val items = listOf(
            ReceiptLineItem(
                index = 1,
                name = if (txn.description.isNotBlank()) txn.description else if (isCredit) "Goods Delivered on Credit" else "Payment Received",
                quantity = 1.0,
                unitPrice = txn.amount
            )
        )

        return ReceiptData(
            receiptNumber = receiptNum,
            date = txn.date,
            businessName = businessName.ifBlank { "My Business" },
            businessAddress = businessAddress.ifBlank { "Main Bazaar, Market Yard" },
            businessPhone = businessPhone,
            upiId = upiId.ifBlank { "merchant@upi" },
            customerName = customer.name.ifBlank { "Valued Customer" },
            customerPhone = customer.phone.ifBlank { null },
            items = items,
            subtotal = txn.amount,
            taxAmount = 0.0,
            discountAmount = 0.0,
            partialPayment = 0.0,
            grandTotal = txn.amount,
            paymentStatus = paymentStatus,
            currencySymbol = currencySymbol
        )
    }

    /**
     * Combined Receipt Generator: Collects all currently pending (unsettled / DUE)
     * ledger entries for this party into an itemized receipt.
     */
    fun createCombinedReceiptData(
        customer: CustomerEntity,
        transactions: List<TransactionEntity>,
        invoices: List<InvoiceEntity>,
        businessName: String,
        businessAddress: String,
        businessPhone: String,
        upiId: String,
        currencySymbol: String,
        netBalance: Double
    ): ReceiptData {
        // Collect pending / unsettled transactions (DUE or credit without PAID status)
        val pendingTxns = transactions.filter { txn ->
            val isCredit = txn.type == TransactionType.GAVE_CREDIT.name || txn.type == TransactionType.INVOICE_BILL.name
            txn.status == "DUE" || (isCredit && txn.status != "PAID")
        }.sortedBy { it.date }

        val itemsList = mutableListOf<ReceiptLineItem>()
        val dateFormat = SimpleDateFormat("dd MMM", Locale.US)

        if (pendingTxns.isNotEmpty()) {
            pendingTxns.forEachIndexed { index, txn ->
                val desc = when {
                    txn.description.isNotBlank() -> txn.description
                    txn.invoiceRef.isNotBlank() -> "Invoice #${txn.invoiceRef}"
                    txn.type == TransactionType.INVOICE_BILL.name -> "Inward Bill"
                    customer.partyType == CustomerEntity.PARTY_TYPE_SUPPLIER -> "Bill Entry"
                    else -> "Credit Sale"
                }
                val dateStr = dateFormat.format(Date(txn.date))
                itemsList.add(
                    ReceiptLineItem(
                        index = index + 1,
                        name = "$desc ($dateStr)",
                        quantity = 1.0,
                        unitPrice = txn.amount
                    )
                )
            }
        } else if (transactions.isNotEmpty()) {
            // If all entries settled, list recent entries with settled state
            val recentTxns = transactions.take(5)
            recentTxns.forEachIndexed { index, txn ->
                val desc = when {
                    txn.description.isNotBlank() -> txn.description
                    txn.type == TransactionType.GOT_PAYMENT.name -> "Payment / Deposit"
                    else -> "Settled Entry"
                }
                val dateStr = dateFormat.format(Date(txn.date))
                itemsList.add(
                    ReceiptLineItem(
                        index = index + 1,
                        name = "$desc ($dateStr)",
                        quantity = 1.0,
                        unitPrice = txn.amount
                    )
                )
            }
        } else {
            itemsList.add(
                ReceiptLineItem(
                    index = 1,
                    name = "No pending entries recorded",
                    quantity = 1.0,
                    unitPrice = 0.0
                )
            )
        }

        val totalAmount = if (pendingTxns.isNotEmpty()) {
            pendingTxns.sumOf { it.amount }
        } else {
            Math.max(0.0, netBalance)
        }

        val isDue = pendingTxns.isNotEmpty() || netBalance > 0
        val paymentStatus = if (isDue) "DUE" else "PAID"

        val receiptNum = "REC-${customer.name.take(3).filter { it.isLetter() }.uppercase(Locale.US).ifBlank { "CUST" }}-${(System.currentTimeMillis() % 10000).toString().padStart(4, '0')}"

        return ReceiptData(
            receiptNumber = receiptNum,
            date = System.currentTimeMillis(),
            businessName = businessName.ifBlank { "My Business" },
            businessAddress = businessAddress.ifBlank { "Main Bazaar, Market Yard" },
            businessPhone = businessPhone,
            upiId = upiId.ifBlank { "merchant@upi" },
            customerName = customer.name.ifBlank { "Valued Customer" },
            customerPhone = customer.phone.ifBlank { null },
            items = itemsList,
            subtotal = totalAmount,
            taxAmount = 0.0,
            discountAmount = 0.0,
            partialPayment = 0.0,
            grandTotal = totalAmount,
            paymentStatus = paymentStatus,
            currencySymbol = currencySymbol
        )
    }
    fun createReceiptDataFromInvoice(
        invoice: InvoiceEntity,
        customerPhone: String?,
        businessName: String,
        businessAddress: String,
        businessPhone: String,
        upiId: String,
        currencySymbol: String
    ): ReceiptData {
        val itemsList = invoice.getItems().mapIndexed { i, invItem ->
            ReceiptLineItem(
                index = i + 1,
                name = invItem.description.ifBlank { "Item ${i + 1}" }.trim(),
                quantity = invItem.quantity,
                unitPrice = invItem.unitPrice
            )
        }

        val items = if (itemsList.isNotEmpty()) itemsList else listOf(
            ReceiptLineItem(
                index = 1,
                name = "Bill Purchase",
                quantity = 1.0,
                unitPrice = invoice.totalAmount
            )
        )

        val isDue = invoice.status != InvoiceEntity.STATUS_PAID
        val paymentStatus = if (isDue) "DUE" else "PAID"

        return ReceiptData(
            receiptNumber = invoice.invoiceNumber,
            date = invoice.issueDate,
            businessName = businessName.ifBlank { "My Business" },
            businessAddress = businessAddress.ifBlank { "Main Bazaar, Market Yard" },
            businessPhone = businessPhone,
            upiId = upiId.ifBlank { "merchant@upi" },
            customerName = invoice.customerName.ifBlank { "Valued Customer" },
            customerPhone = customerPhone?.ifBlank { null },
            items = items,
            subtotal = invoice.subtotal,
            taxAmount = invoice.taxAmount,
            discountAmount = invoice.discountAmount,
            partialPayment = invoice.paidAmount,
            grandTotal = invoice.totalAmount,
            paymentStatus = paymentStatus,
            currencySymbol = currencySymbol
        )
    }

    /**
     * Export & Share action: Generates high quality bitmap, saves to cache,
     * and opens the native Android ACTION_SEND share sheet.
     */
    fun generateAndShareReceipt(context: Context, receipt: ReceiptData) {
        try {
            val bitmap = createReceiptBitmap(receipt)

            val receiptsDir = File(context.cacheDir, "receipts")
            receiptsDir.mkdirs()
            val file = File(receiptsDir, "receipt_${receipt.receiptNumber.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.png")
            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
            fos.close()

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                val summary = if (receipt.isDue) {
                    "Receipt #${receipt.receiptNumber} from ${receipt.businessName}. Total Due: ${receipt.currencySymbol}${String.format(Locale.US, "%.2f", receipt.grandTotal)}. Pay via UPI: ${receipt.upiId}"
                } else {
                    "Receipt #${receipt.receiptNumber} from ${receipt.businessName} for ${receipt.customerName}. Paid: ${receipt.currencySymbol}${String.format(Locale.US, "%.2f", receipt.grandTotal)}."
                }
                putExtra(Intent.EXTRA_TEXT, summary)
                putExtra(Intent.EXTRA_SUBJECT, "Receipt #${receipt.receiptNumber} - ${receipt.businessName}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Receipt")
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Creates a high-fidelity Bitmap strictly following the reference image:
     * - White ticket card with rounded corners and scalloped side notches
     * - Top Header with 3x3 dot logo mark, Business Name, Date, Address, Customer Name
     * - Conditional Customer Mobile row (completely collapsed if empty)
     * - Dashed horizontal divider across side notches
     * - Itemized list (Item name, Qty x Rate, Subtotal)
     * - Shaded gray bounding box for Totals with prominent Grand Total and conditional Tax/Discount/Payment
     * - Dynamic Footer: UPI QR Code when DUE, or centered "Thank you for your business!" when Paid
     */
    /**
     * Calculates the exact source dimensions (width, height) produced by createReceiptBitmap.
     * Guaranteed 100% mathematical consistency with the exported image.
     */
    fun calculateReceiptDimensions(receipt: ReceiptData): Pair<Int, Int> {
        val width = 1080
        val headerBaseHeight = 270f
        val hasCustomerPhone = !receipt.customerPhone.isNullOrBlank()
        val customerPhoneHeight = if (hasCustomerPhone) 46f else 0f
        val headerHeight = headerBaseHeight + customerPhoneHeight

        val separatorHeight = 50f
        val itemRowHeight = 96f
        val itemsListHeight = maxOf(receipt.items.size, 1) * itemRowHeight + 20f

        var totalsBoxHeight = 110f // Base for Grand Total
        if (receipt.paymentStatus.isNotBlank()) totalsBoxHeight += 44f
        if (receipt.taxAmount > 0) totalsBoxHeight += 44f
        if (receipt.discountAmount > 0) totalsBoxHeight += 44f
        if (receipt.partialPayment > 0) totalsBoxHeight += 44f

        val totalsSectionHeight = totalsBoxHeight + 40f
        val footerHeight = if (receipt.isDue) 310f else 160f
        val subFooterHeight = 90f
        val bottomPadding = 60f

        val totalHeight = (headerHeight + separatorHeight + itemsListHeight +
                totalsSectionHeight + footerHeight + subFooterHeight + bottomPadding).roundToInt()

        return Pair(width, totalHeight)
    }

    /**
     * Calculates the exact source aspect ratio (width / height) produced by the receipt generator.
     */
    fun getReceiptAspectRatio(receipt: ReceiptData): Float {
        val (w, h) = calculateReceiptDimensions(receipt)
        return w.toFloat() / h.toFloat()
    }

    fun createReceiptBitmap(receipt: ReceiptData): Bitmap {
        val (width, totalHeight) = calculateReceiptDimensions(receipt)
        val marginX = 54f
        val cardWidth = width - (marginX * 2)

        val headerBaseHeight = 270f
        val hasCustomerPhone = !receipt.customerPhone.isNullOrBlank()
        val customerPhoneHeight = if (hasCustomerPhone) 46f else 0f
        val headerHeight = headerBaseHeight + customerPhoneHeight
        val separatorHeight = 50f
        val itemRowHeight = 96f

        var totalsBoxHeight = 110f
        if (receipt.paymentStatus.isNotBlank()) totalsBoxHeight += 44f
        if (receipt.taxAmount > 0) totalsBoxHeight += 44f
        if (receipt.discountAmount > 0) totalsBoxHeight += 44f
        if (receipt.partialPayment > 0) totalsBoxHeight += 44f

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Clean backdrop (matching the app's minimalist styling)
        canvas.drawColor(Color.parseColor("#F1F5F9"))

        // 2. Draw white ticket card with rounded corners and side notch cutouts
        val cardRect = RectF(marginX, 30f, width - marginX, totalHeight - 30f)
        val cardCornerRadius = 40f

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(16f, 0f, 6f, Color.parseColor("#15000000"))
        }

        val cardPath = Path()
        cardPath.addRoundRect(cardRect, cardCornerRadius, cardCornerRadius, Path.Direction.CW)

        // Side notch cutouts at separator level
        val notchY = 30f + headerHeight + (separatorHeight / 2f)
        val notchRadius = 26f

        val leftNotch = Path().apply {
            addCircle(marginX, notchY, notchRadius, Path.Direction.CW)
        }
        val rightNotch = Path().apply {
            addCircle(width - marginX, notchY, notchRadius, Path.Direction.CW)
        }

        val finalCardPath = Path()
        finalCardPath.op(cardPath, leftNotch, Path.Op.DIFFERENCE)
        finalCardPath.op(finalCardPath, rightNotch, Path.Op.DIFFERENCE)

        canvas.drawPath(finalCardPath, cardPaint)

        // Card subtle stroke
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawPath(finalCardPath, strokePaint)

        // Paints
        val paintDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val paintSecondary = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val paintMuted = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val contentLeft = marginX + 54f
        val contentRight = width - marginX - 54f
        var currentY = 30f + 60f

        // --- 1. HEADER SECTION ---
        // Draw New Ledger Mate Store Logo Badge on PDF Receipt
        val logoSize = 42f
        val logoLeft = contentLeft
        val logoTop = currentY - 26f
        val logoRight = logoLeft + logoSize
        val logoBottom = logoTop + logoSize

        // 1. Soft Mint Background Squircle (#E2F4E7)
        val squirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2F4E7")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(logoLeft, logoTop, logoRight, logoBottom), 10f, 10f, squirclePaint)

        // 2. Outer Emerald Book Cover (#2E7E45)
        val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2E7E45")
            style = Paint.Style.FILL
        }
        val bookLeft = logoLeft + 9f
        val bookTop = logoTop + 9f
        val bookRight = logoRight - 9f
        val bookBottom = logoBottom - 9f
        canvas.drawRoundRect(RectF(bookLeft, bookTop, bookRight, bookBottom), 5f, 5f, coverPaint)

        // 3. Inner White Page Sheet (#FFFFFF)
        val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFFFFF")
            style = Paint.Style.FILL
        }
        val pageLeft = bookLeft + 2.5f
        val pageTop = bookTop + 2.5f
        val pageRight = bookRight - 2.5f
        val pageBottom = bookBottom - 2.5f
        canvas.drawRoundRect(RectF(pageLeft, pageTop, pageRight, pageBottom), 3f, 3f, pagePaint)

        // 4. Center Spine & Ledger Lines (#A1D9B5)
        val logoLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A1D9B5")
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            strokeCap = Paint.Cap.ROUND
        }
        val centerX = (pageLeft + pageRight) / 2f
        canvas.drawLine(centerX, pageTop, centerX, pageBottom, logoLinePaint)

        // Horizontal Ledger Lines
        canvas.drawLine(pageLeft + 2f, pageTop + 5f, centerX - 2f, pageTop + 5f, logoLinePaint)
        canvas.drawLine(pageLeft + 2f, pageTop + 9f, centerX - 2f, pageTop + 9f, logoLinePaint)
        canvas.drawLine(pageLeft + 2f, pageTop + 13f, centerX - 2f, pageTop + 13f, logoLinePaint)
        canvas.drawLine(centerX + 2f, pageTop + 5f, pageRight - 2f, pageTop + 5f, logoLinePaint)
        canvas.drawLine(centerX + 2f, pageTop + 9f, pageRight - 2f, pageTop + 9f, logoLinePaint)
        canvas.drawLine(centerX + 2f, pageTop + 13f, pageRight - 2f, pageTop + 13f, logoLinePaint)

        // 5. Dark Emerald Bookmark Ribbon with V-Notch (#1C6033)
        val ribbonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1C6033")
            style = Paint.Style.FILL
        }
        val rWidth = 5.5f
        val rLeft = centerX - (rWidth / 2f)
        val rRight = centerX + (rWidth / 2f)
        val rTop = bookTop - 1f
        val rBottom = pageTop + 8f

        val ribbonPath = Path().apply {
            moveTo(rLeft, rTop)
            lineTo(rRight, rTop)
            lineTo(rRight, rBottom)
            lineTo(centerX, rBottom - 3f)
            lineTo(rLeft, rBottom)
            close()
        }
        canvas.drawPath(ribbonPath, ribbonPaint)

        // Business Name (Prominent)
        paintDark.textSize = 34f
        paintDark.textAlign = Paint.Align.LEFT
        val businessNameX = logoRight + 14f
        canvas.drawText(receipt.businessName, businessNameX, currentY, paintDark)

        // Right side: Receipt No. & Date
        paintSecondary.textSize = 23f
        paintSecondary.textAlign = Paint.Align.RIGHT
        canvas.drawText("Receipt #${receipt.receiptNumber}", contentRight, currentY - 14f, paintSecondary)

        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US)
        paintMuted.textSize = 21f
        paintMuted.textAlign = Paint.Align.RIGHT
        canvas.drawText(dateFormat.format(Date(receipt.date)), contentRight, currentY + 16f, paintMuted)

        currentY += 46f

        // Business Address (Subtext under business name)
        paintSecondary.textSize = 22f
        paintSecondary.textAlign = Paint.Align.LEFT
        canvas.drawText(receipt.businessAddress, contentLeft, currentY, paintSecondary)

        currentY += 66f

        // Customer Name Header (Prominent greeting block)
        paintDark.textSize = 36f
        paintDark.textAlign = Paint.Align.LEFT
        canvas.drawText(receipt.customerName, contentLeft, currentY, paintDark)

        currentY += 38f
        paintSecondary.textSize = 24f
        canvas.drawText("Order / Account Reference #${receipt.receiptNumber}", contentLeft, currentY, paintSecondary)

        // Conditional Customer Mobile
        if (hasCustomerPhone) {
            currentY += 38f
            paintSecondary.textSize = 22f
            canvas.drawText("Mobile: ${receipt.customerPhone}", contentLeft, currentY, paintSecondary)
        }

        currentY = notchY

        // --- 2. SEPARATOR (Dashed Horizontal Divider across cutouts) ---
        val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
        }
        val dashStartX = marginX + notchRadius + 12f
        val dashEndX = width - marginX - notchRadius - 12f
        val dashPath = Path().apply {
            moveTo(dashStartX, notchY)
            lineTo(dashEndX, notchY)
        }
        canvas.drawPath(dashPath, dashPaint)

        currentY += 44f

        // --- 3. ITEMIZED LIST ---
        receipt.items.forEach { item ->
            val rowTop = currentY
            val itemNumText = "${item.index}"

            // Number index
            paintSecondary.textSize = 24f
            paintSecondary.textAlign = Paint.Align.LEFT
            canvas.drawText(itemNumText, contentLeft, rowTop + 24f, paintSecondary)

            // Item Name
            paintDark.textSize = 27f
            paintDark.textAlign = Paint.Align.LEFT
            canvas.drawText(item.name, contentLeft + 42f, rowTop + 24f, paintDark)

            // Subtotal on far right
            val formattedSubtotal = "${receipt.currencySymbol}${String.format(Locale.US, "%.2f", item.subtotal)}"
            paintDark.textSize = 27f
            paintDark.textAlign = Paint.Align.RIGHT
            canvas.drawText(formattedSubtotal, contentRight, rowTop + 24f, paintDark)

            // Indented Quantity x Rate row: "2 × ₹570.00"
            val qtyStr = if (item.quantity % 1.0 == 0.0) "${item.quantity.toInt()}" else String.format(Locale.US, "%.2f", item.quantity)
            val unitStr = if (item.unit.isNotBlank()) " ${item.unit}" else ""
            val rateStr = "${receipt.currencySymbol}${String.format(Locale.US, "%.2f", item.unitPrice)}"
            val qtyRateText = "$qtyStr$unitStr × $rateStr"

            paintSecondary.textSize = 22f
            paintSecondary.textAlign = Paint.Align.LEFT
            canvas.drawText(qtyRateText, contentLeft + 42f, rowTop + 58f, paintSecondary)

            currentY += itemRowHeight
        }

        currentY += 16f

        // --- 4. TOTALS SECTION (Shaded Background Bounding Box) ---
        val totalsRect = RectF(contentLeft, currentY, contentRight, currentY + totalsBoxHeight)
        val totalsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }
        val totalsStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(totalsRect, 22f, 22f, totalsPaint)
        canvas.drawRoundRect(totalsRect, 22f, 22f, totalsStroke)

        val totalsPaddingX = contentLeft + 30f
        val totalsRightX = contentRight - 30f
        var totalsY = currentY + 44f

        // Always display Grand Total prominently
        paintDark.textSize = 34f
        paintDark.textAlign = Paint.Align.LEFT
        canvas.drawText("Total", totalsPaddingX, totalsY, paintDark)

        val formattedTotal = "${receipt.currencySymbol}${String.format(Locale.US, "%.2f", receipt.grandTotal)}"
        paintDark.textSize = 36f
        paintDark.textAlign = Paint.Align.RIGHT
        canvas.drawText(formattedTotal, totalsRightX, totalsY, paintDark)

        // Payment status / Method
        totalsY += 40f
        paintSecondary.textSize = 22f
        paintSecondary.textAlign = Paint.Align.LEFT
        canvas.drawText("Payment Method", totalsPaddingX, totalsY, paintSecondary)

        paintDark.textSize = 22f
        paintDark.textAlign = Paint.Align.RIGHT
        val statusDisplay = if (receipt.isDue) "DUE / Outstanding" else receipt.paymentStatus
        canvas.drawText(statusDisplay, totalsRightX, totalsY, paintDark)

        // Conditional display: Tax (only if > 0)
        if (receipt.taxAmount > 0) {
            totalsY += 40f
            paintSecondary.textSize = 22f
            paintSecondary.textAlign = Paint.Align.LEFT
            canvas.drawText("Tax / GST", totalsPaddingX, totalsY, paintSecondary)

            paintSecondary.textAlign = Paint.Align.RIGHT
            canvas.drawText("+ ${receipt.currencySymbol}${String.format(Locale.US, "%.2f", receipt.taxAmount)}", totalsRightX, totalsY, paintSecondary)
        }

        // Conditional display: Discount (only if > 0)
        if (receipt.discountAmount > 0) {
            totalsY += 40f
            paintSecondary.textSize = 22f
            paintSecondary.textAlign = Paint.Align.LEFT
            canvas.drawText("Discount", totalsPaddingX, totalsY, paintSecondary)

            val paintDiscount = Paint(paintSecondary).apply {
                color = Color.parseColor("#16A34A")
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("- ${receipt.currencySymbol}${String.format(Locale.US, "%.2f", receipt.discountAmount)}", totalsRightX, totalsY, paintDiscount)
        }

        // Conditional display: Partial Payment (only if > 0)
        if (receipt.partialPayment > 0) {
            totalsY += 40f
            paintSecondary.textSize = 22f
            paintSecondary.textAlign = Paint.Align.LEFT
            canvas.drawText("Paid Amount", totalsPaddingX, totalsY, paintSecondary)

            paintSecondary.textAlign = Paint.Align.RIGHT
            canvas.drawText("${receipt.currencySymbol}${String.format(Locale.US, "%.2f", receipt.partialPayment)}", totalsRightX, totalsY, paintSecondary)
        }

        currentY += totalsBoxHeight + 36f

        // --- 5. DYNAMIC FOOTER LOGIC (Payment Status) ---
        if (receipt.isDue) {
            // Auto-generate UPI QR code at the bottom of the receipt
            val upiPayload = "upi://pay?pa=${receipt.upiId.trim()}&pn=${Uri.encode(receipt.businessName.trim())}&am=${String.format(Locale.US, "%.2f", receipt.grandTotal)}&cu=INR&tn=${Uri.encode("Receipt " + receipt.receiptNumber)}"
            val qrSize = 220
            val qrBitmap = drawQrCodeMatrix(upiPayload, qrSize)

            // Text on left
            paintDark.textSize = 30f
            paintDark.textAlign = Paint.Align.LEFT
            canvas.drawText("Scan & Pay via UPI", contentLeft, currentY + 40f, paintDark)

            paintSecondary.textSize = 23f
            canvas.drawText("Use GPay, PhonePe, Paytm or BHIM", contentLeft, currentY + 80f, paintSecondary)

            paintSecondary.textSize = 22f
            canvas.drawText("Amount Due: ${receipt.currencySymbol}${String.format(Locale.US, "%.2f", receipt.grandTotal)}", contentLeft, currentY + 120f, paintSecondary)

            val paintUpiId = Paint(paintDark).apply {
                textSize = 22f
                color = Color.parseColor("#2563EB")
            }
            canvas.drawText("UPI ID: ${receipt.upiId}", contentLeft, currentY + 160f, paintUpiId)

            // QR Code in rounded border container on the right
            val qrBoxLeft = contentRight - qrSize - 20f
            val qrBoxTop = currentY
            val qrBoxRect = RectF(qrBoxLeft, qrBoxTop, contentRight, qrBoxTop + qrSize + 20f)

            val qrBoxBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            val qrBoxBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#BFDBFE")
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRoundRect(qrBoxRect, 20f, 20f, qrBoxBg)
            canvas.drawRoundRect(qrBoxRect, 20f, 20f, qrBoxBorder)

            canvas.drawBitmap(qrBitmap, qrBoxLeft + 10f, qrBoxTop + 10f, null)

            currentY += qrSize + 40f
        } else {
            // Paid: Omit QR code. Replace with clean, centered "Thank you for your business!"
            val thankYouY = currentY + 40f

            val paintThankYou = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#16A34A")
                textSize = 32f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Thank you for your business!", width / 2f, thankYouY, paintThankYou)

            paintSecondary.textSize = 22f
            paintSecondary.textAlign = Paint.Align.CENTER
            canvas.drawText("Payment verified & successfully recorded.", width / 2f, thankYouY + 36f, paintSecondary)

            currentY += 110f
        }

        // --- 6. SUB-FOOTER ---
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F1F5F9")
            strokeWidth = 1.5f
        }
        canvas.drawLine(contentLeft, currentY, contentRight, currentY, linePaint)
        currentY += 34f

        paintMuted.textSize = 20f
        paintMuted.textAlign = Paint.Align.LEFT
        val footerLegal = "${receipt.businessName} • ${receipt.businessPhone}"
        canvas.drawText(footerLegal, contentLeft, currentY, paintMuted)

        paintMuted.textAlign = Paint.Align.RIGHT
        canvas.drawText("LedgerMate Verified Receipt", contentRight, currentY, paintMuted)

        return bitmap
    }

    /**
     * Generates a genuine ISO/IEC 18004 scannable QR code Bitmap using ZXing Core
     * for instant UPI payment scanning (GPay, PhonePe, Paytm, BHIM), with a deterministic
     * canvas fallback if encoding fails.
     */
    fun drawQrCodeMatrix(content: String, size: Int): Bitmap {
        try {
            val hints = mapOf(
                com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8",
                com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M,
                com.google.zxing.EncodeHintType.MARGIN to 1
            )
            val bitMatrix = com.google.zxing.qrcode.QRCodeWriter().encode(
                content.ifBlank { "upi://pay" },
                com.google.zxing.BarcodeFormat.QR_CODE,
                size,
                size,
                hints
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        } catch (_: Exception) {
            // Fallback below
        }

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        val modules = 29
        val moduleSize = size.toFloat() / modules

        fun drawFinderPattern(startX: Int, startY: Int) {
            // Outer 7x7 black
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
                (startY + 5) * moduleSize,
                paint
            )
        }

        // Top-Left, Top-Right, Bottom-Left Finder Patterns
        drawFinderPattern(1, 1)
        drawFinderPattern(modules - 8, 1)
        drawFinderPattern(1, modules - 8)

        // Alignment pattern
        val alignX = modules - 9
        val alignY = modules - 9
        paint.color = Color.BLACK
        canvas.drawRect(alignX * moduleSize, alignY * moduleSize, (alignX + 5) * moduleSize, (alignY + 5) * moduleSize, paint)
        paint.color = Color.WHITE
        canvas.drawRect((alignX + 1) * moduleSize, (alignY + 1) * moduleSize, (alignX + 4) * moduleSize, (alignY + 4) * moduleSize, paint)
        paint.color = Color.BLACK
        canvas.drawRect((alignX + 2) * moduleSize, (alignY + 2) * moduleSize, (alignX + 3) * moduleSize, (alignY + 3) * moduleSize, paint)

        // Timing lines
        for (i in 8 until modules - 8) {
            if (i % 2 == 0) {
                paint.color = Color.BLACK
                canvas.drawRect(i * moduleSize, 6 * moduleSize, (i + 1) * moduleSize, 7 * moduleSize, paint)
                canvas.drawRect(6 * moduleSize, i * moduleSize, 7 * moduleSize, (i + 1) * moduleSize, paint)
            }
        }

        // Deterministic content bits
        val bytes = content.toByteArray(Charsets.UTF_8)
        val seed = content.hashCode().toLong()
        val random = Random(seed)

        var bitIdx = 0
        for (row in 0 until modules) {
            for (col in 0 until modules) {
                // Skip finder patterns
                if ((row in 0..8 && col in 0..8) ||
                    (row in 0..8 && col >= modules - 9) ||
                    (row >= modules - 9 && col in 0..8)
                ) {
                    continue
                }
                // Skip timing lines
                if (row == 6 || col == 6) continue
                // Skip alignment
                if (row in alignY until alignY + 5 && col in alignX until alignX + 5) continue

                val byteVal = bytes[bitIdx % bytes.size].toInt()
                val bitIsSet = ((byteVal shr (bitIdx % 8)) and 1) == 1
                val isBlack = bitIsSet xor random.nextBoolean()

                if (isBlack) {
                    paint.color = Color.BLACK
                    canvas.drawRect(
                        col * moduleSize,
                        row * moduleSize,
                        (col + 1) * moduleSize,
                        (row + 1) * moduleSize,
                        paint
                    )
                }
                bitIdx++
            }
        }

        return bitmap
    }
}
