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

    var autoSyncFrequency: String
        get() = prefs.getString("auto_sync_frequency", "Instant") ?: "Instant"
        set(value) = prefs.edit().putString("auto_sync_frequency", value).apply()

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean("biometric_enabled", false)
        set(value) = prefs.edit().putBoolean("biometric_enabled", value).apply()

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
        private const val HASH_PREFIX = "sha256:"
    }

    // ============================================================
    // SALTED SHA-256 PASSCODE HASHING & VERIFICATION
    // ============================================================

    val hasPasscodeConfigured: Boolean
        get() = passcodePin.isNotBlank()

    fun setHashedPasscode(rawPin: String) {
        if (rawPin.isBlank()) {
            passcodePin = ""
            return
        }
        val saltBytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(saltBytes)
        val saltHex = saltBytes.joinToString("") { "%02x".format(it) }
        val digestHex = computeSha256(rawPin.trim(), saltHex)
        passcodePin = "$HASH_PREFIX$saltHex:$digestHex"
    }

    fun verifyPasscodePin(inputPin: String): Boolean {
        val stored = passcodePin
        if (stored.isBlank() || !stored.startsWith(HASH_PREFIX)) return false
        val cleanInput = inputPin.trim()
        val parts = stored.removePrefix(HASH_PREFIX).split(":")
        if (parts.size != 2) return false
        val saltHex = parts[0]
        val expectedHash = parts[1]
        val actualHash = computeSha256(cleanInput, saltHex)
        return java.security.MessageDigest.isEqual(
            expectedHash.toByteArray(Charsets.UTF_8),
            actualHash.toByteArray(Charsets.UTF_8)
        )
    }

    var failedPinAttempts: Int
        get() = prefs.getInt("failed_pin_attempts", 0)
        set(value) = prefs.edit().putInt("failed_pin_attempts", value).apply()

    var pinLockoutUntil: Long
        get() = prefs.getLong("pin_lockout_until", 0L)
        set(value) = prefs.edit().putLong("pin_lockout_until", value).apply()

    fun isPinLockedOut(nowMs: Long = System.currentTimeMillis()): Boolean {
        val until = pinLockoutUntil
        if (until <= 0L) return false
        if (nowMs >= until) {
            pinLockoutUntil = 0L
            failedPinAttempts = 0
            return false
        }
        return true
    }

    fun remainingPinLockoutSeconds(nowMs: Long = System.currentTimeMillis()): Long {
        val until = pinLockoutUntil
        if (until <= nowMs) return 0L
        return ((until - nowMs) + 999L) / 1000L
    }

    fun recordFailedPinAttempt(nowMs: Long = System.currentTimeMillis()): Pair<Int, Long> {
        val attempts = failedPinAttempts + 1
        failedPinAttempts = attempts
        if (attempts >= 5) {
            val lockoutMs = 30_000L
            pinLockoutUntil = nowMs + lockoutMs
            return attempts to 30L
        }
        return attempts to 0L
    }

    fun resetPinLockout() {
        prefs.edit()
            .putInt("failed_pin_attempts", 0)
            .putLong("pin_lockout_until", 0L)
            .apply()
    }

    private fun computeSha256(pin: String, saltHex: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md.update(saltHex.toByteArray(Charsets.UTF_8))
        val digest = md.digest(pin.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    // ============================================================
    // DELTA SYNC WATERMARKS & DIRTY RECORD QUEUE
    // ============================================================

    @Synchronized
    fun getCollectionWatermark(collection: String): Long {
        return prefs.getLong("delta_watermark_$collection", 0L)
    }

    @Synchronized
    fun setCollectionWatermark(collection: String, timestamp: Long) {
        val current = getCollectionWatermark(collection)
        if (timestamp > current) {
            prefs.edit().putLong("delta_watermark_$collection", timestamp).apply()
        }
    }

    @Synchronized
    fun resetAllSyncWatermarks() {
        prefs.edit()
            .remove("delta_watermark_customers")
            .remove("delta_watermark_transactions")
            .remove("delta_watermark_rate_items")
            .remove("delta_watermark_invoices")
            .remove("delta_watermark_notes")
            .remove("dirty_sync_records")
            .apply()
    }

    @Synchronized
    fun markRecordDirty(collection: String, id: String) {
        if (id.isBlank()) return
        val current = (prefs.getStringSet("dirty_sync_records", emptySet()) ?: emptySet()).toMutableSet()
        current.add("$collection:$id")
        prefs.edit().putStringSet("dirty_sync_records", current).apply()
    }

    @Synchronized
    fun clearRecordDirty(collection: String, id: String) {
        if (id.isBlank()) return
        val current = (prefs.getStringSet("dirty_sync_records", emptySet()) ?: emptySet()).toMutableSet()
        if (current.remove("$collection:$id")) {
            prefs.edit().putStringSet("dirty_sync_records", current).apply()
        }
    }

    @Synchronized
    fun clearCollectionDirty(collection: String, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val current = (prefs.getStringSet("dirty_sync_records", emptySet()) ?: emptySet()).toMutableSet()
        var modified = false
        for (id in ids) {
            if (current.remove("$collection:$id")) {
                modified = true
            }
        }
        if (modified) {
            prefs.edit().putStringSet("dirty_sync_records", current).apply()
        }
    }

    @Synchronized
    fun getDirtyRecordIds(collection: String): Set<String> {
        val prefix = "$collection:"
        val allDirty = prefs.getStringSet("dirty_sync_records", emptySet()) ?: emptySet()
        return allDirty.asSequence()
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    @Synchronized
    fun getTotalDirtyRecordCount(): Int {
        return (prefs.getStringSet("dirty_sync_records", emptySet()) ?: emptySet()).size
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
            com.example.crashlytics.AppCrashlytics.recordNonFatal(e, "setCategoryIconKey")
        }
    }

    fun clearAllUserAndLedgerPrefs() {
        isUnlocked = true
        prefs.edit().clear().commit()
    }
}

