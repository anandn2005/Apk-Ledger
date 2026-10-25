package com.example.ui

import java.text.NumberFormat
import java.util.*

object Formatters {
    private val currencyFormatter = object : ThreadLocal<NumberFormat>() {
        override fun initialValue(): NumberFormat {
            return NumberFormat.getNumberInstance(Locale.US).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }
        }
    }

    private val monthNamesShort = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val dayNamesShort = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    fun formatCurrency(amount: Double, currencySymbol: String = "₹"): String {
        val safeAmount = if (amount.isNaN() || amount.isInfinite()) 0.0 else kotlin.math.abs(amount)
        val money = com.example.data.MoneyMath.fromRupeesDouble(safeAmount)
        return com.example.data.MoneyMath.formatMoney(money, currencySymbol)
    }

    fun formatSignedCurrency(amount: Double, currencySymbol: String = "₹"): String {
        val safeAmount = if (amount.isNaN() || amount.isInfinite()) 0.0 else amount
        val money = com.example.data.MoneyMath.fromRupeesDouble(kotlin.math.abs(safeAmount))
        val formatted = com.example.data.MoneyMath.formatMoney(money, currencySymbol)
        return when {
            safeAmount > 0.004 -> "+$formatted"
            safeAmount < -0.004 -> "-$formatted"
            else -> formatted
        }
    }

    fun daysSince(timestamp: Long, nowMs: Long = System.currentTimeMillis()): Int {
        if (timestamp <= 0L) return 0
        val diff = (nowMs - timestamp).coerceAtLeast(0L)
        return (diff / (24L * 60L * 60L * 1000L)).toInt()
    }

    fun formatRelativeDateOrAge(timestamp: Long, isOverdueCandidate: Boolean = false): String {
        if (timestamp <= 0L) return "No entries yet"
        val days = daysSince(timestamp)
        return when {
            isOverdueCandidate && days > 30 -> "Overdue ${days}d"
            days == 0 -> "Today"
            days == 1 -> "Yesterday"
            days in 2..30 -> "${days}d ago"
            else -> formatDateShort(timestamp)
        }
    }

    /**
     * Formats timestamp to DD/MM/YYYY using local device date parts.
     * Prevents UTC boundary off-by-one shifts.
     */
    fun formatDate(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return String.format(
            Locale.getDefault(),
            "%02d/%02d/%04d",
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.YEAR)
        )
    }

    /**
     * Formats timestamp to ISO date-only format (YYYY-MM-DD) from local date parts.
     * Never converts to UTC before taking the date parts.
     */
    fun toLocalDateString(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * Formats timestamp to ISO date-time string using local date & time parts.
     */
    fun toLocalDateTimeString(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return String.format(
            Locale.US,
            "%04d-%02d-%02dT%02d:%02d:%02d.000",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND)
        )
    }

    fun formatDateInput(input: String): String {
        val digits = input.filter { it.isDigit() }.take(8)
        val sb = StringBuilder()
        for (i in digits.indices) {
            sb.append(digits[i])
            if ((i == 1 || i == 3) && i != digits.lastIndex) {
                sb.append('/')
            }
        }
        return sb.toString()
    }

    /**
     * Parses a date string (DD/MM/YYYY, YYYY-MM-DD, DD-MM-YYYY) directly by splitting
     * local date components and anchoring the resulting timestamp to noon (12:00:00.000)
     * local time. This eliminates UTC-midnight off-by-one boundary bugs.
     */
    fun parseDate(dateStr: String): Long? {
        val clean = dateStr.trim()
        if (clean.isBlank()) return null

        val parts = when {
            clean.contains('/') -> clean.split('/')
            clean.contains('-') -> clean.split('-')
            clean.contains('.') -> clean.split('.')
            else -> return null
        }
        if (parts.size != 3) return null

        val (day, month, year) = if (parts[0].length == 4) {
            // yyyy-MM-dd
            val y = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            val d = parts[2].toIntOrNull() ?: return null
            Triple(d, m, y)
        } else {
            // dd/MM/yyyy or dd-MM-yyyy
            val d = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            val y = parts[2].toIntOrNull() ?: return null
            Triple(d, m, y)
        }

        if (day !in 1..31 || month !in 1..12 || year !in 1900..2100) return null

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 12) // Always anchor at local noon
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun formatTime(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        var hour = cal.get(Calendar.HOUR)
        if (hour == 0) hour = 12
        val minute = cal.get(Calendar.MINUTE)
        val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        return String.format(Locale.US, "%02d:%02d %s", hour, minute, amPm)
    }

    fun formatDateShort(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val m = cal.get(Calendar.MONTH).coerceIn(0, 11)
        return String.format(Locale.getDefault(), "%02d %s", cal.get(Calendar.DAY_OF_MONTH), monthNamesShort[m])
    }

    fun formatDateHeader(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val dow = (cal.get(Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6)
        val m = cal.get(Calendar.MONTH).coerceIn(0, 11)
        return "${dayNamesShort[dow]}, ${cal.get(Calendar.DAY_OF_MONTH)} ${monthNamesShort[m]} ${cal.get(Calendar.YEAR)}"
    }

    fun isSameCalendarDay(ts1: Long, ts2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = ts1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = ts2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    fun shiftCalendarDay(timestamp: Long, days: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, days)
        }
        return cal.timeInMillis
    }
}