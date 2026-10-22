package com.example.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ReceiptDraft(
    val customerId: String? = null,
    val customerName: String = "",
    val customerMobile: String = "",
    val items: List<InvoiceItem> = emptyList(),
    val dueDays: String = "30",
    val tax: String = "0",
    val discount: String = "0",
    val notes: String = "",
    val paymentMode: String = "UPI"
)

object ReceiptDraftManager {
    private const val PREFS_NAME = "receipt_draft_prefs"
    private const val KEY_HAS_DRAFT = "has_draft"
    private const val KEY_CUSTOMER_ID = "customer_id"
    private const val KEY_CUSTOMER_NAME = "customer_name"
    private const val KEY_CUSTOMER_MOBILE = "customer_mobile"
    private const val KEY_ITEMS_JSON = "items_json"
    private const val KEY_DUE_DAYS = "due_days"
    private const val KEY_TAX = "tax"
    private const val KEY_DISCOUNT = "discount"
    private const val KEY_NOTES = "notes"
    private const val KEY_PAYMENT_MODE = "payment_mode"

    fun hasDraft(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HAS_DRAFT, false)
    }

    fun loadDraft(context: Context): ReceiptDraft? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_HAS_DRAFT, false)) return null

        val customerId = prefs.getString(KEY_CUSTOMER_ID, null)
        val customerName = prefs.getString(KEY_CUSTOMER_NAME, "") ?: ""
        val customerMobile = prefs.getString(KEY_CUSTOMER_MOBILE, "") ?: ""
        val dueDays = prefs.getString(KEY_DUE_DAYS, "30") ?: "30"
        val tax = prefs.getString(KEY_TAX, "0") ?: "0"
        val discount = prefs.getString(KEY_DISCOUNT, "0") ?: "0"
        val notes = prefs.getString(KEY_NOTES, "") ?: ""
        val paymentMode = prefs.getString(KEY_PAYMENT_MODE, "UPI") ?: "UPI"

        val items = mutableListOf<InvoiceItem>()
        val itemsJson = prefs.getString(KEY_ITEMS_JSON, "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(itemsJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                items.add(
                    InvoiceItem(
                        description = obj.optString("description", ""),
                        quantity = obj.optDouble("quantity", 1.0),
                        unitPrice = obj.optDouble("unitPrice", 0.0)
                    )
                )
            }
        } catch (_: Exception) {}

        return ReceiptDraft(
            customerId = customerId,
            customerName = customerName,
            customerMobile = customerMobile,
            items = items,
            dueDays = dueDays,
            tax = tax,
            discount = discount,
            notes = notes,
            paymentMode = paymentMode
        )
    }

    fun saveDraft(
        context: Context,
        customerId: String?,
        customerName: String,
        customerMobile: String,
        items: List<InvoiceItem>,
        dueDays: String,
        tax: String,
        discount: String,
        notes: String,
        paymentMode: String
    ) {
        val isEmpty = customerName.isBlank() && customerMobile.isBlank() && items.isEmpty() && notes.isBlank()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (isEmpty) {
            prefs.edit().putBoolean(KEY_HAS_DRAFT, false).apply()
            return
        }

        val jsonArray = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("description", item.description)
            obj.put("quantity", item.quantity)
            obj.put("unitPrice", item.unitPrice)
            jsonArray.put(obj)
        }

        prefs.edit()
            .putBoolean(KEY_HAS_DRAFT, true)
            .putString(KEY_CUSTOMER_ID, customerId)
            .putString(KEY_CUSTOMER_NAME, customerName)
            .putString(KEY_CUSTOMER_MOBILE, customerMobile)
            .putString(KEY_ITEMS_JSON, jsonArray.toString())
            .putString(KEY_DUE_DAYS, dueDays)
            .putString(KEY_TAX, tax)
            .putString(KEY_DISCOUNT, discount)
            .putString(KEY_NOTES, notes)
            .putString(KEY_PAYMENT_MODE, paymentMode)
            .apply()
    }

    fun clearDraft(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
