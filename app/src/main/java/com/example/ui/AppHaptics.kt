package com.example.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Invisible tactile interactions: crisp, physical haptics for financial actions
 * (Save tap, swipe-to-settle completion, predictive chip selection, toggle switches, wheel ticks).
 */
object AppHaptics {

    /**
     * Trigger a subtle, crisp confirmation vibration when completing a high-value action
     * like tapping "Save", adding a customer, or completing a transaction.
     */
    fun performConfirm(view: View?) {
        if (view == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    /**
     * Trigger subtle feedback for button clicks, tabs, or chips.
     */
    fun performTap(view: View?) {
        if (view == null) return
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /**
     * Trigger subtle tick feedback for toggle switches (Dark Mode, Passcode Lock) or segmented controls.
     */
    fun performToggle(view: View?) {
        if (view == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    /**
     * Trigger feedback when crossing a swipe action threshold (Swipe to Delete / Edit).
     */
    fun performSwipeThreshold(view: View?) {
        if (view == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    /**
     * Trigger light tick for wheel picker or number increment.
     */
    fun performTick(view: View?) {
        if (view == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    /**
     * Trigger error feedback on validation failure.
     */
    fun performError(view: View?) {
        if (view == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
}
