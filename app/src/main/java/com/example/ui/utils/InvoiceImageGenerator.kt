package com.example.ui.utils

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.InvoiceEntity
import com.example.data.InvoiceItem
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.*

object InvoiceImageGenerator {

    fun saveInvoiceImageFile(
        context: Context,
        invoice: InvoiceEntity,
        businessName: String,
        businessPhone: String,
        businessAddress: String,
        currencySymbol: String
    ): File {
        val bitmap = createInvoiceBitmap(
            invoice = invoice,
            businessName = businessName,
            businessPhone = businessPhone,
            businessAddress = businessAddress,
            currencySymbol = currencySymbol
        )

        val folder = File(context.filesDir, "stored_invoices")
        folder.mkdirs()
        val file = File(folder, "invoice_${invoice.id}.png")
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.flush()
        stream.close()
        return file
    }

    fun getOrGenerateInvoiceImage(
        context: Context,
        invoice: InvoiceEntity,
        businessName: String,
        businessPhone: String,
        businessAddress: String,
        currencySymbol: String
    ): File {
        if (invoice.imagePath.isNotBlank()) {
            val f = File(invoice.imagePath)
            if (f.exists()) return f
        }
        val folder = File(context.filesDir, "stored_invoices")
        val fallbackFile = File(folder, "invoice_${invoice.id}.png")
        if (fallbackFile.exists()) return fallbackFile

        return saveInvoiceImageFile(
            context = context,
            invoice = invoice,
            businessName = businessName,
            businessPhone = businessPhone,
            businessAddress = businessAddress,
            currencySymbol = currencySymbol
        )
    }

    fun loadInvoiceBitmap(
        context: Context,
        invoice: InvoiceEntity,
        businessName: String,
        businessPhone: String,
        businessAddress: String,
        currencySymbol: String
    ): Bitmap {
        return try {
            val file = getOrGenerateInvoiceImage(
                context = context,
                invoice = invoice,
                businessName = businessName,
                businessPhone = businessPhone,
                businessAddress = businessAddress,
                currencySymbol = currencySymbol
            )
            BitmapFactory.decodeFile(file.absolutePath) ?: createInvoiceBitmap(
                invoice = invoice,
                businessName = businessName,
                businessPhone = businessPhone,
                businessAddress = businessAddress,
                currencySymbol = currencySymbol
            )
        } catch (e: Exception) {
            e.printStackTrace()
            createInvoiceBitmap(
                invoice = invoice,
                businessName = businessName,
                businessPhone = businessPhone,
                businessAddress = businessAddress,
                currencySymbol = currencySymbol
            )
        }
    }

    fun generateAndShareInvoiceImage(
        context: Context,
        invoice: InvoiceEntity,
        businessName: String,
        businessPhone: String,
        businessAddress: String,
        currencySymbol: String
    ) {
        try {
            val file = getOrGenerateInvoiceImage(
                context = context,
                invoice = invoice,
                businessName = businessName,
                businessPhone = businessPhone,
                businessAddress = businessAddress,
                currencySymbol = currencySymbol
            )

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                val summaryText = "Receipt for ${invoice.customerName} from $businessName. Total: $currencySymbol${String.format(Locale.US, "%.2f", invoice.totalAmount)}."
                putExtra(Intent.EXTRA_TEXT, summaryText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Minimalist Receipt")
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Ultra-minimalist receipt generator.
     * Strips away borders, tables, and extra data fields.
     * Contains ONLY:
     * 1. Store Name (at top)
     * 2. Customer Name
     * 3. Item Name(s)
     * 4. Total Amount
     * 5. Footer: "Thank you for business"
     */
    private fun createInvoiceBitmap(
        invoice: InvoiceEntity,
        businessName: String,
        businessPhone: String,
        businessAddress: String,
        currencySymbol: String
    ): Bitmap {
        val items = mutableListOf<String>()
        try {
            val jsonArr = JSONArray(invoice.itemsJson)
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val desc = obj.optString("description", "").trim()
                if (desc.isNotBlank()) {
                    items.add(desc)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (items.isEmpty()) {
            items.add("General Purchase")
        }

        val width = 720
        val topPadding = 60f
        val storeHeaderHeight = 60f
        val customerSectionHeight = 50f
        val itemLineHeight = 40f
        val itemsTotalHeight = items.size * itemLineHeight
        val totalSectionHeight = 70f
        val footerHeight = 60f
        val bottomPadding = 60f

        val totalHeight = (topPadding + storeHeaderHeight + customerSectionHeight +
                itemsTotalHeight + totalSectionHeight + footerHeight + bottomPadding).toInt()

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Pure white background
        canvas.drawColor(Color.WHITE)

        // Text Paints
        val paintStoreName = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 36f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val paintCustomerName = Paint().apply {
            color = Color.parseColor("#334155")
            textSize = 26f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }

        val paintItemName = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 24f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }

        val paintTotalAmount = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 34f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        val paintFooter = Paint().apply {
            color = Color.parseColor("#64748B")
            textSize = 22f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }

        val leftMargin = 60f
        var currentY = topPadding + 36f

        // 1. Store Name (at the top, centered)
        val displayStoreName = if (businessName.isNotBlank()) businessName.trim() else "My Store"
        canvas.drawText(displayStoreName, width / 2f, currentY, paintStoreName)
        currentY += storeHeaderHeight

        // 2. Customer Name
        val custName = if (invoice.customerName.isNotBlank()) invoice.customerName.trim() else "Customer"
        canvas.drawText("Customer: $custName", leftMargin, currentY, paintCustomerName)
        currentY += customerSectionHeight

        // 3. Item Name(s)
        items.forEach { itemName ->
            canvas.drawText(itemName, leftMargin, currentY, paintItemName)
            currentY += itemLineHeight
        }
        currentY += 24f

        // 4. Total Amount
        val formattedTotal = String.format(Locale.US, "%.2f", invoice.totalAmount)
        canvas.drawText("Total: $currencySymbol$formattedTotal", leftMargin, currentY, paintTotalAmount)
        currentY += totalSectionHeight

        // 5. Footer: "Thank you for business"
        canvas.drawText("Thank you for business", width / 2f, currentY, paintFooter)

        return bitmap
    }
}
