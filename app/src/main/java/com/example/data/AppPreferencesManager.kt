package com.example.data

import android.content.Context
import android.content.SharedPreferences

class AppPreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ledgermate_prefs", Context.MODE_PRIVATE)

    var passcodePin: String
        get() = prefs.getString("passcode_pin", "") ?: ""
        set(value) = prefs.edit().putString("passcode_pin", value).apply()

    var isPasscodeEnabled: Boolean
        get() = prefs.getBoolean("passcode_enabled", false)
        set(value) = prefs.edit().putBoolean("passcode_enabled", value).apply()

    var currencySymbol: String
        get() = prefs.getString("currency_symbol", "₹") ?: "₹"
        set(value) = prefs.edit().putString("currency_symbol", value).apply()

    var businessName: String
        get() = prefs.getString("business_name", "My Business Ledger") ?: "My Business Ledger"
        set(value) = prefs.edit().putString("business_name", value).apply()

    var businessPhone: String
        get() = prefs.getString("business_phone", "+91 98765 43210") ?: "+91 98765 43210"
        set(value) = prefs.edit().putString("business_phone", value).apply()

    var businessAddress: String
        get() = prefs.getString("business_address", "APMC Market Yard, Main Bazaar") ?: "APMC Market Yard, Main Bazaar"
        set(value) = prefs.edit().putString("business_address", value).apply()

    var upiId: String
        get() = prefs.getString("upi_id", "merchant@upi") ?: "merchant@upi"
        set(value) = prefs.edit().putString("upi_id", value).apply()

    var businessGstin: String
        get() = prefs.getString("business_gstin", "") ?: ""
        set(value) = prefs.edit().putString("business_gstin", value).apply()

    var pinnedCustomerIds: Set<String>
        get() {
            val str = prefs.getString("pinned_customer_ids", "") ?: ""
            if (str.isBlank()) return emptySet()
            return str.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        }
        set(value) {
            prefs.edit().putString("pinned_customer_ids", value.joinToString(",")).apply()
        }

    var pinnedRateItemIds: List<String>
        get() {
            val str = prefs.getString("pinned_rate_item_ids", "") ?: ""
            if (str.isBlank()) return emptyList()
            return str.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
        }
        set(value) {
            prefs.edit().putString("pinned_rate_item_ids", value.map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString(",")).apply()
        }

    var isPinnedItemsCollapsed: Boolean
        get() = prefs.getBoolean("pinned_rate_items_collapsed", false)
        set(value) = prefs.edit().putBoolean("pinned_rate_items_collapsed", value).apply()

    var pinnedRateItemsLastUpdated: Long
        get() = prefs.getLong("pinned_rate_items_last_updated", 0L)
        set(value) = prefs.edit().putLong("pinned_rate_items_last_updated", value).apply()

    var customCategories: List<String>
        get() {
            val str = prefs.getString("custom_categories", "") ?: ""
            if (str.isBlank()) return listOf("ALL", "Pulses & Kirana", "Dry Fruit", "Rice", "Surf & Soap", "Tobacco", "Tea", "Namkeen", "Oil & Ghee", "Biscuit", "Others")
            return str.split("|||").filter { it.isNotBlank() }
        }
        set(value) {
            prefs.edit().putString("custom_categories", value.joinToString("|||")).apply()
        }

    var isUnlocked: Boolean = false

    var lastSyncTimestamp: Long
        get() = prefs.getLong("last_sync_timestamp", 0L)
        set(value) = prefs.edit().putLong("last_sync_timestamp", value).apply()

    var webClientId: String
        get() = prefs.getString("web_client_id", "") ?: ""
        set(value) = prefs.edit().putString("web_client_id", value).apply()

    var cachedUserEmail: String
        get() = prefs.getString("cached_user_email", "") ?: ""
        set(value) = prefs.edit().putString("cached_user_email", value).apply()

    var cachedUserName: String
        get() = prefs.getString("cached_user_name", "") ?: ""
        set(value) = prefs.edit().putString("cached_user_name", value).apply()

    var cachedUserPhotoUrl: String
        get() = prefs.getString("cached_user_photo_url", "") ?: ""
        set(value) = prefs.edit().putString("cached_user_photo_url", value).apply()

    var isAutoSyncEnabled: Boolean
        get() = prefs.getBoolean("auto_sync_enabled", true)
        set(value) = prefs.edit().putBoolean("auto_sync_enabled", value).apply()

    var isDarkMode: Boolean
        get() = prefs.getBoolean("is_dark_mode", false)
        set(value) = prefs.edit().putBoolean("is_dark_mode", value).apply()

    var categoryIconMapJson: String
        get() = prefs.getString("category_icon_map_json", "{}") ?: "{}"
        set(value) = prefs.edit().putString("category_icon_map_json", value).apply()

    var selectedLanguageCode: String
        get() = prefs.getString("selected_language_code", "en") ?: "en"
        set(value) = prefs.edit().putString("selected_language_code", value).apply()

    var customUnits: List<String>
        get() {
            val jsonStr = prefs.getString("custom_units_json", null)
            return if (jsonStr != null) {
                try {
                    val arr = org.json.JSONArray(jsonStr)
                    List(arr.length()) { arr.getString(it) }
                } catch (e: Exception) {
                    defaultSystemUnits
                }
            } else {
                defaultSystemUnits
            }
        }
        set(value) {
            val arr = org.json.JSONArray(value)
            prefs.edit().putString("custom_units_json", arr.toString()).apply()
        }

    var hiddenCategories: Set<String>
        get() = prefs.getStringSet("hidden_categories_set", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("hidden_categories_set", value).apply()

    var reminderTemplate: String
        get() = prefs.getString("whatsapp_reminder_template", "") ?: ""
        set(value) = prefs.edit().putString("whatsapp_reminder_template", value).apply()

    var customNoteLabels: List<String>
        get() {
            val str = prefs.getString("custom_note_labels", "") ?: ""
            if (str.isBlank()) return emptyList()
            return str.split("|||").map { it.trim() }.filter { it.isNotBlank() && it !in NoteEntity.DEFAULT_LABELS }.distinct()
        }
        set(value) {
            val cleaned = value.map { it.trim() }.filter { it.isNotBlank() && it !in NoteEntity.DEFAULT_LABELS }.distinct()
            prefs.edit().putString("custom_note_labels", cleaned.joinToString("|||")).apply()
        }

    companion object {
        val defaultSystemUnits = listOf("Kg", "gm", "Ltr", "mL", "Pcs", "Pkt", "Box", "Carton", "Dozen", "Bora", "Tin", "Meter")
    }

    fun getCategoryIconKey(category: String): String {
        return try {
            val json = org.json.JSONObject(categoryIconMapJson)
            json.optString(category, "")
        } catch (e: Exception) {
            ""
        }
    }

    fun setCategoryIconKey(category: String, iconKey: String) {
        try {
            val json = org.json.JSONObject(categoryIconMapJson)
            json.put(category, iconKey)
            categoryIconMapJson = json.toString()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

