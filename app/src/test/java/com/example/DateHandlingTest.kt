package com.example

import com.example.ui.Formatters
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class DateHandlingTest {

    @Test
    fun testDateRoundTripInIST() {
        val originalTz = TimeZone.getDefault()
        try {
            // Set timezone to IST (UTC+5:30)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))

            val inputStr = "30/09/2026"
            val timestamp = Formatters.parseDate(inputStr)
            assertNotNull(timestamp)

            // Format back to date string
            val formatted = Formatters.formatDate(timestamp!!)
            assertEquals("30/09/2026", formatted)

            // Local ISO string should match 2026-09-30 without reverting to 2026-09-29
            val isoDate = Formatters.toLocalDateString(timestamp)
            assertEquals("2026-09-30", isoDate)
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun testMonthBoundaryTransition() {
        val originalTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))

            val sep30 = Formatters.parseDate("30/09/2026")!!
            assertEquals("30/09/2026", Formatters.formatDate(sep30))

            // Shift forward 1 day -> Oct 1
            val oct1 = Formatters.shiftCalendarDay(sep30, 1)
            assertEquals("01/10/2026", Formatters.formatDate(oct1))

            // Shift backward 1 day -> Sep 30
            val backToSep30 = Formatters.shiftCalendarDay(oct1, -1)
            assertEquals("30/09/2026", Formatters.formatDate(backToSep30))
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun testYearBoundaryTransition() {
        val originalTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))

            val dec31 = Formatters.parseDate("31/12/2026")!!
            assertEquals("31/12/2026", Formatters.formatDate(dec31))

            // Shift forward 1 day -> Jan 1, 2027
            val jan1 = Formatters.shiftCalendarDay(dec31, 1)
            assertEquals("01/01/2027", Formatters.formatDate(jan1))

            // Shift backward 1 day -> Dec 31, 2026
            val backToDec31 = Formatters.shiftCalendarDay(jan1, -1)
            assertEquals("31/12/2026", Formatters.formatDate(backToDec31))
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun testDateHeaderFormatting() {
        val originalTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))

            val dateTs = Formatters.parseDate("30/09/2026")!!
            val header = Formatters.formatDateHeader(dateTs)
            assertEquals("Wed, 30 Sep 2026", header)
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun testSegmentedDateComposition() {
        // Simulating the 3 segments: DD = "15", MM = "08", YYYY = "2026"
        val day = "15"
        val month = "08"
        val year = "2026"
        val composed = "$day/$month/$year"

        val parsedTs = Formatters.parseDate(composed)
        assertNotNull(parsedTs)
        val validTs = parsedTs!!

        val cal = Calendar.getInstance().apply { timeInMillis = validTs }
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(7, cal.get(Calendar.MONTH)) // 0-indexed month: 7 = August
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(12, cal.get(Calendar.HOUR_OF_DAY)) // Noon anchor verified

        assertEquals("15/08/2026", Formatters.formatDate(validTs))
        assertEquals("2026-08-15", Formatters.toLocalDateString(validTs))
    }

    @Test
    fun testInvalidDatesReturnNull() {
        assertNull(Formatters.parseDate("32/01/2026"))
        assertNull(Formatters.parseDate("00/01/2026"))
        assertNull(Formatters.parseDate("15/13/2026"))
        assertNull(Formatters.parseDate("15/00/2026"))
        assertNull(Formatters.parseDate("15/05/1850"))
        assertNull(Formatters.parseDate("15/05/2150"))
    }
}
