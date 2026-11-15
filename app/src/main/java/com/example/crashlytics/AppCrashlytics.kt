package com.example.crashlytics

import android.util.Log
import com.example.BuildConfig

object AppCrashlytics {
    private const val TAG = "Crashlytics"

    /**
     * Records a non-fatal exception safely in debug builds without leaking stack traces in release logcat.
     */
    fun recordNonFatal(throwable: Throwable, contextTag: String = "general") {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "Recorded non-fatal in $contextTag: ${throwable.message}", throwable)
        }
    }

    /**
     * Logs breadcrumb safely without PII or financial quantities (debug builds only).
     */
    fun logEvent(event: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Breadcrumb: $event")
        }
    }
}


