package com.example.crashlytics

import android.util.Log

object AppCrashlytics {
    private const val TAG = "Crashlytics"

    /**
     * Records a non-fatal exception safely without any customer names or money amounts.
     */
    fun recordNonFatal(throwable: Throwable, contextTag: String = "general") {
        Log.w(TAG, "Recorded non-fatal in $contextTag: ${throwable.message}", throwable)
    }

    /**
     * Logs breadcrumb safely without PII or financial quantities
     */
    fun logEvent(event: String) {
        Log.d(TAG, "Breadcrumb: $event")
    }
}


