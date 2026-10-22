package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import com.example.ui.theme.isAppInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.Formatters
import android.app.DatePickerDialog
import java.util.Calendar
import com.example.ui.screens.PricePoint
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import java.text.SimpleDateFormat
import java.util.*

/**
 * Soft Floating Card adhering to modern, soft design principles:
 * - Pristine white surface
 * - 24dp rounded corners
 * - Broad, diffused low-opacity drop shadow
 * - Zero hard borders
 * - Generous padding
 */
@Composable
fun FluentCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 10.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    border: BorderStroke? = null,
    backgroundColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val defaultBg = backgroundColor ?: Color.White

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color(0x08000000),
                spotColor = Color(0x10000000)
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = onClick
                    )
                } else Modifier
            ),
        shape = shape,
        color = defaultBg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = border
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

/**
 * Segmented 3-Box DD/MM/YYYY Date Input.
 *
 * Replaces buggy single-string auto-masking text fields with 3 independent,
 * un-reformatted numeric segment inputs: [DD] / [MM] / [YYYY].
 * - Auto-advances focus on complete segment (DD 2 digits -> MM; MM 2 digits -> YYYY)
 * - Backspace moves focus to previous segment and deletes its last digit
 * - Unified rounded-rect container (12dp radius, 1dp border) with whole-container accent-green focus ring
 * - Soft-validates on blur (DD: 01-31, MM: 01-12, YYYY: 1900-2100) without blocking typing mid-entry
 * - Native Android DatePickerDialog integration via calendar icon populates all 3 segments cleanly
 * - Formats and parses using local date components, eliminating UTC off-by-one boundary bugs.
 */
@Composable
fun SegmentedDateInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    onDateChosen: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val dayFocusRequester = remember { FocusRequester() }
    val monthFocusRequester = remember { FocusRequester() }
    val yearFocusRequester = remember { FocusRequester() }

    var isDayFocused by remember { mutableStateOf(false) }
    var isMonthFocused by remember { mutableStateOf(false) }
    var isYearFocused by remember { mutableStateOf(false) }
    val isAnyFocused = isDayFocused || isMonthFocused || isYearFocused

    var day by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }

    // Sync from external value (e.g. "DD/MM/YYYY", "YYYY-MM-DD", etc.)
    LaunchedEffect(value) {
        val clean = value.trim()
        val parts = when {
            clean.contains('/') -> clean.split('/')
            clean.contains('-') -> clean.split('-')
            clean.contains('.') -> clean.split('.')
            else -> emptyList()
        }
        if (parts.size == 3) {
            val (d, m, y) = if (parts[0].length == 4) {
                // yyyy-MM-dd
                Triple(parts[2], parts[1], parts[0])
            } else {
                // dd/MM/yyyy
                Triple(parts[0], parts[1], parts[2])
            }
            if (d != day || m != month || y != year) {
                day = d
                month = m
                year = y
            }
        } else if (clean.isBlank()) {
            if (day.isNotEmpty() || month.isNotEmpty() || year.isNotEmpty()) {
                day = ""
                month = ""
                year = ""
            }
        }
    }

    var softValidationFailed by remember { mutableStateOf(false) }

    // Soft-validate on blur
    var wasAnyFocused by remember { mutableStateOf(false) }
    LaunchedEffect(isAnyFocused) {
        if (wasAnyFocused && !isAnyFocused) {
            val d = day.toIntOrNull()
            val m = month.toIntOrNull()
            val y = year.toIntOrNull()
            val hasInvalidDay = day.isNotEmpty() && (d == null || d !in 1..31)
            val hasInvalidMonth = month.isNotEmpty() && (m == null || m !in 1..12)
            val hasInvalidYear = year.isNotEmpty() && year.length == 4 && (y == null || y !in 1900..2100)
            softValidationFailed = hasInvalidDay || hasInvalidMonth || hasInvalidYear
        }
        wasAnyFocused = isAnyFocused
    }

    fun notifyChange(newDay: String, newMonth: String, newYear: String) {
        softValidationFailed = false
        val composed = if (newDay.isNotEmpty() || newMonth.isNotEmpty() || newYear.isNotEmpty()) {
            "$newDay/$newMonth/$newYear"
        } else ""
        onValueChange(composed)

        if (newDay.length == 2 && newMonth.length == 2 && newYear.length == 4) {
            val d = newDay.toIntOrNull() ?: 0
            val m = newMonth.toIntOrNull() ?: 0
            val y = newYear.toIntOrNull() ?: 0
            if (d in 1..31 && m in 1..12 && y in 1900..2100) {
                val parsedTs = Formatters.parseDate("$newDay/$newMonth/$newYear")
                if (parsedTs != null) {
                    onDateChosen?.invoke(parsedTs)
                }
            }
        }
    }

    val launchPicker = {
        val currentTs = Formatters.parseDate("$day/$month/$year") ?: System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = currentTs }
        DatePickerDialog(
            context,
            { _, pickedYear, pickedMonth, pickedDay ->
                val dStr = String.format(Locale.US, "%02d", pickedDay)
                val mStr = String.format(Locale.US, "%02d", pickedMonth + 1)
                val yStr = String.format(Locale.US, "%04d", pickedYear)
                day = dStr
                month = mStr
                year = yStr
                notifyChange(dStr, mStr, yStr)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val isDark = isAppInDarkTheme()
    val effectiveError = isError || softValidationFailed
    val AccentGreen = Color(0xFF379358)
    val BorderGreenGray = Color(0xFFE3EFE6)
    val DarkBorder = Color(0xFF3A3A3C)
    val textPrimary = if (isDark) Color.White else Color(0xFF1B2E22)
    val textSecondary = Color(0xFF8E8E93)
    val containerBg = if (isDark) Color(0xFF1C1C1E) else Color.White

    val borderColor = when {
        effectiveError -> MaterialTheme.colorScheme.error
        isAnyFocused -> AccentGreen
        else -> if (isDark) DarkBorder else BorderGreenGray
    }
    val borderWidth = if (isAnyFocused || effectiveError) 1.5.dp else 1.dp

    Column(modifier = modifier) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textSecondary,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = containerBg,
            border = BorderStroke(borderWidth, borderColor),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(
                    elevation = if (isAnyFocused) 2.dp else 0.5.dp,
                    shape = RoundedCornerShape(12.dp),
                    spotColor = if (isAnyFocused) AccentGreen.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.05f)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Calendar Icon Button (picks date via native Android DatePickerDialog)
                IconButton(
                    onClick = launchPicker,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "Pick Date from Calendar",
                        tint = if (isAnyFocused) AccentGreen else textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Segments Row: [ DD ] / [ MM ] / [ YYYY ]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    // Segment 1: DD
                    BasicTextField(
                        value = day,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(2)
                            day = digits
                            notifyChange(digits, month, year)
                            if (digits.length == 2) {
                                monthFocusRequester.requestFocus()
                            }
                        },
                        modifier = Modifier
                            .width(28.dp)
                            .focusRequester(dayFocusRequester)
                            .onFocusChanged { isDayFocused = it.isFocused }
                            .testTag("date_input_day"),
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            fontFeatureSettings = "tnum",
                            color = textPrimary
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { monthFocusRequester.requestFocus() }
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(AccentGreen),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.Center) {
                                if (day.isEmpty()) {
                                    Text(
                                        text = "DD",
                                        style = TextStyle(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            fontFeatureSettings = "tnum",
                                            color = textSecondary.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    Text(
                        text = "/",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    // Segment 2: MM
                    BasicTextField(
                        value = month,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(2)
                            month = digits
                            notifyChange(day, digits, year)
                            if (digits.length == 2) {
                                yearFocusRequester.requestFocus()
                            }
                        },
                        modifier = Modifier
                            .width(28.dp)
                            .focusRequester(monthFocusRequester)
                            .onFocusChanged { isMonthFocused = it.isFocused }
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Backspace) {
                                    if (month.isEmpty()) {
                                        dayFocusRequester.requestFocus()
                                        if (day.isNotEmpty()) {
                                            val newD = day.dropLast(1)
                                            day = newD
                                            notifyChange(newD, month, year)
                                        }
                                        true
                                    } else false
                                } else false
                            }
                            .testTag("date_input_month"),
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            fontFeatureSettings = "tnum",
                            color = textPrimary
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { yearFocusRequester.requestFocus() }
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(AccentGreen),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.Center) {
                                if (month.isEmpty()) {
                                    Text(
                                        text = "MM",
                                        style = TextStyle(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            fontFeatureSettings = "tnum",
                                            color = textSecondary.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    Text(
                        text = "/",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    // Segment 3: YYYY
                    BasicTextField(
                        value = year,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(4)
                            year = digits
                            notifyChange(day, month, digits)
                            if (digits.length == 4) {
                                focusManager.clearFocus()
                                onImeAction()
                            }
                        },
                        modifier = Modifier
                            .width(52.dp)
                            .focusRequester(yearFocusRequester)
                            .onFocusChanged { isYearFocused = it.isFocused }
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Backspace) {
                                    if (year.isEmpty()) {
                                        monthFocusRequester.requestFocus()
                                        if (month.isNotEmpty()) {
                                            val newM = month.dropLast(1)
                                            month = newM
                                            notifyChange(day, newM, year)
                                        }
                                        true
                                    } else false
                                } else false
                            }
                            .testTag("date_input_year"),
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            fontFeatureSettings = "tnum",
                            color = textPrimary
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = imeAction
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                onImeAction()
                            },
                            onNext = {
                                focusManager.clearFocus()
                                onImeAction()
                            }
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(AccentGreen),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.Center) {
                                if (year.isEmpty()) {
                                    Text(
                                        text = "YYYY",
                                        style = TextStyle(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            fontFeatureSettings = "tnum",
                                            color = textSecondary.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                // Quick clear button if anything is typed
                if (day.isNotEmpty() || month.isNotEmpty() || year.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            day = ""
                            month = ""
                            year = ""
                            notifyChange("", "", "")
                            dayFocusRequester.requestFocus()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear Date",
                            tint = textSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        if (effectiveError) {
            val msg = errorMessage ?: "Enter a valid date (DD: 01-31, MM: 01-12, YYYY: 1900-2100)"
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }
    }
}

/**
 * Fluent Date Input Field using Android's native default DatePickerDialog.
 * Tapping this field launches Android's default date selector, completely
 * eliminating typing glitches, caret jumps, and mobile keyboard issues.
 */
@Composable
fun FluentDateInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Date (DD/MM/YYYY)",
    isError: Boolean = false,
    errorMessage: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    onDateChosen: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val isDark = isAppInDarkTheme()
    val AccentGreen = Color(0xFF379358)
    val AccentGreenLight = Color(0xFFEAF6ED)
    val BorderGreenGray = Color(0xFFE3EFE6)
    val DarkBorder = Color(0xFF3A3A3C)
    val textPrimary = if (isDark) Color.White else Color(0xFF1C1C1E)
    val textSecondary = Color(0xFF8E8E93)
    val containerBg = if (isDark) Color(0xFF1C1C1E) else Color.White

    val launchPicker = {
        val currentTs = Formatters.parseDate(value) ?: System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = currentTs }
        DatePickerDialog(
            context,
            { _, pickedYear, pickedMonth, pickedDay ->
                val selectedCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, pickedYear)
                    set(Calendar.MONTH, pickedMonth)
                    set(Calendar.DAY_OF_MONTH, pickedDay)
                    set(Calendar.HOUR_OF_DAY, 12)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val formatted = String.format(java.util.Locale.US, "%02d/%02d/%04d", pickedDay, pickedMonth + 1, pickedYear)
                onValueChange(formatted)
                onDateChosen?.invoke(selectedCal.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textSecondary,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    BorderStroke(
                        if (isError) 1.5.dp else 1.dp,
                        if (isError) MaterialTheme.colorScheme.error else (if (isDark) DarkBorder else BorderGreenGray)
                    ),
                    RoundedCornerShape(12.dp)
                )
                .clickable { launchPicker() }
                .testTag("fluent_date_selector_field"),
            color = containerBg,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(if (isDark) AccentGreen.copy(alpha = 0.2f) else AccentGreenLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Select Date",
                            tint = AccentGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = if (value.isNotBlank()) value else "Select Date",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (value.isNotBlank()) textPrimary else textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Open Date Selector",
                    tint = textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (isError && !errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }
    }
}

/**
 * Minimalist Fluent Graphical Price History Chart.
 * Plots Buy Price in Red and Sell Price in Green on a sleek canvas.
 */
@Composable
fun FluentPriceHistoryChart(
    history: List<PricePoint>,
    currencySymbol: String = "$",
    modifier: Modifier = Modifier
) {
    if (history.isEmpty()) return

    // Order points chronologically from oldest to newest for plotting
    val sortedPoints = remember(history) {
        history.sortedBy { it.date }
    }

    val isDark = isAppInDarkTheme()
    val gridLineColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
    val textLabelColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
            .border(
                BorderStroke(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        // Chart Header & Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Price History",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Legend: Red = Buy Rate, Green = Sell Price
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Buy Legend
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(DebitRed)
                    )
                    Text(
                        text = "Buy Price",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = DebitRed
                    )
                }

                // Sell Legend
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(CreditGreen)
                    )
                    Text(
                        text = "Sell Price",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = CreditGreen
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        var selectedIndex by remember(sortedPoints) { mutableStateOf<Int?>(null) }

        // Graphical Canvas with Interactive Touch/Drag
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(145.dp)
                .pointerInput(sortedPoints) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (sortedPoints.isNotEmpty()) {
                                val canvasWidth = size.width
                                val paddingLeft = 10f
                                val paddingRight = 10f
                                val plotWidth = (canvasWidth - paddingLeft - paddingRight).coerceAtLeast(1f)
                                val total = sortedPoints.size
                                var closestIdx = 0
                                var minDistance = Float.MAX_VALUE
                                for (i in 0 until total) {
                                    val ptX = if (total <= 1) paddingLeft + (plotWidth / 2f)
                                              else paddingLeft + (i.toFloat() / (total - 1)) * plotWidth
                                    val dist = kotlin.math.abs(ptX - offset.x)
                                    if (dist < minDistance) {
                                        minDistance = dist
                                        closestIdx = i
                                    }
                                }
                                selectedIndex = if (selectedIndex == closestIdx) null else closestIdx
                            }
                        }
                    )
                }
                .pointerInput(sortedPoints) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (sortedPoints.isNotEmpty()) {
                                val canvasWidth = size.width
                                val paddingLeft = 10f
                                val paddingRight = 10f
                                val plotWidth = (canvasWidth - paddingLeft - paddingRight).coerceAtLeast(1f)
                                val total = sortedPoints.size
                                var closestIdx = 0
                                var minDistance = Float.MAX_VALUE
                                for (i in 0 until total) {
                                    val ptX = if (total <= 1) paddingLeft + (plotWidth / 2f)
                                              else paddingLeft + (i.toFloat() / (total - 1)) * plotWidth
                                    val dist = kotlin.math.abs(ptX - offset.x)
                                    if (dist < minDistance) {
                                        minDistance = dist
                                        closestIdx = i
                                    }
                                }
                                selectedIndex = closestIdx
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (sortedPoints.isNotEmpty()) {
                                val canvasWidth = size.width
                                val paddingLeft = 10f
                                val paddingRight = 10f
                                val plotWidth = (canvasWidth - paddingLeft - paddingRight).coerceAtLeast(1f)
                                val total = sortedPoints.size
                                var closestIdx = 0
                                var minDistance = Float.MAX_VALUE
                                for (i in 0 until total) {
                                    val ptX = if (total <= 1) paddingLeft + (plotWidth / 2f)
                                              else paddingLeft + (i.toFloat() / (total - 1)) * plotWidth
                                    val dist = kotlin.math.abs(ptX - change.position.x)
                                    if (dist < minDistance) {
                                        minDistance = dist
                                        closestIdx = i
                                    }
                                }
                                selectedIndex = closestIdx
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                val paddingLeft = 10f
                val paddingRight = 10f
                val paddingTop = 16f
                val paddingBottom = 16f

                val plotWidth = canvasWidth - paddingLeft - paddingRight
                val plotHeight = canvasHeight - paddingTop - paddingBottom

                val allBuy = sortedPoints.map { it.buyRate }
                val allSell = sortedPoints.map { it.sellPrice }
                val allValues = allBuy + allSell

                val minVal = (allValues.minOrNull() ?: 0.0).coerceAtLeast(0.0)
                val rawMax = allValues.maxOrNull() ?: 1.0
                val maxVal = if (rawMax == minVal) minVal + 10.0 else rawMax
                val valRange = (maxVal - minVal).coerceAtLeast(1.0)

                fun normalizeY(value: Double): Float {
                    val ratio = ((value - minVal) / valRange).toFloat()
                    return paddingTop + (plotHeight * (1.0f - ratio))
                }

                fun getX(index: Int, total: Int): Float {
                    if (total <= 1) return paddingLeft + (plotWidth / 2f)
                    return paddingLeft + (index.toFloat() / (total - 1)) * plotWidth
                }

                // Draw Horizontal Grid Guide Lines (3 levels)
                for (i in 0..2) {
                    val y = paddingTop + (plotHeight * (i / 2f))
                    drawLine(
                        color = gridLineColor,
                        start = Offset(paddingLeft, y),
                        end = Offset(canvasWidth - paddingRight, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // If only 1 data point, draw dots
                if (sortedPoints.size == 1) {
                    val pt = sortedPoints[0]
                    val x = getX(0, 1)
                    val yBuy = normalizeY(pt.buyRate)
                    val ySell = normalizeY(pt.sellPrice)

                    // Sell dot (Green)
                    drawCircle(color = CreditGreen, radius = 6.dp.toPx(), center = Offset(x, ySell))
                    // Buy dot (Red)
                    drawCircle(color = DebitRed, radius = 6.dp.toPx(), center = Offset(x, yBuy))
                    return@Canvas
                }

                // Draw Sell Path (Green)
                val sellPath = Path()
                val buyPath = Path()

                sortedPoints.forEachIndexed { i, pt ->
                    val x = getX(i, sortedPoints.size)
                    val ySell = normalizeY(pt.sellPrice)
                    val yBuy = normalizeY(pt.buyRate)

                    if (i == 0) {
                        sellPath.moveTo(x, ySell)
                        buyPath.moveTo(x, yBuy)
                    } else {
                        sellPath.lineTo(x, ySell)
                        buyPath.lineTo(x, yBuy)
                    }
                }

                // Stroke Sell Line (Green)
                drawPath(
                    path = sellPath,
                    color = CreditGreen,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Stroke Buy Line (Red)
                drawPath(
                    path = buyPath,
                    color = DebitRed,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Draw vertical indicator line if a point is selected
                if (selectedIndex != null && selectedIndex!! in sortedPoints.indices) {
                    val selX = getX(selectedIndex!!, sortedPoints.size)
                    drawLine(
                        color = if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.25f),
                        start = Offset(selX, paddingTop),
                        end = Offset(selX, canvasHeight - paddingBottom),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                }

                // Draw Data Points on top
                sortedPoints.forEachIndexed { i, pt ->
                    val x = getX(i, sortedPoints.size)
                    val ySell = normalizeY(pt.sellPrice)
                    val yBuy = normalizeY(pt.buyRate)
                    val isSel = selectedIndex == i

                    if (isSel) {
                        // Highlight aura around selected point
                        drawCircle(color = CreditGreen.copy(alpha = 0.25f), radius = 10.dp.toPx(), center = Offset(x, ySell))
                        drawCircle(color = DebitRed.copy(alpha = 0.25f), radius = 10.dp.toPx(), center = Offset(x, yBuy))
                    }

                    // Outer white ring + inner colored dot
                    drawCircle(color = Color.White, radius = if (isSel) 6.dp.toPx() else 5.dp.toPx(), center = Offset(x, ySell))
                    drawCircle(color = CreditGreen, radius = if (isSel) 4.5.dp.toPx() else 3.5.dp.toPx(), center = Offset(x, ySell))

                    drawCircle(color = Color.White, radius = if (isSel) 6.dp.toPx() else 5.dp.toPx(), center = Offset(x, yBuy))
                    drawCircle(color = DebitRed, radius = if (isSel) 4.5.dp.toPx() else 3.5.dp.toPx(), center = Offset(x, yBuy))
                }
            }

            // Minimalist Floating Tooltip displaying Buy Price & Sell Price
            if (selectedIndex != null && selectedIndex!! in sortedPoints.indices) {
                val selPt = sortedPoints[selectedIndex!!]
                val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp)
                        .clickable { selectedIndex = null }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = sdf.format(Date(selPt.date)),
                            style = MaterialTheme.typography.labelSmall,
                            color = textLabelColor,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(DebitRed))
                            Text(
                                text = "Buy: $currencySymbol${String.format(java.util.Locale.US, "%.2f", selPt.buyRate)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = DebitRed
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(CreditGreen))
                            Text(
                                text = "Sell: $currencySymbol${String.format(java.util.Locale.US, "%.2f", selPt.sellPrice)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = CreditGreen
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Date Axis (Oldest to Latest)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
            val firstDate = sortedPoints.firstOrNull()?.date ?: System.currentTimeMillis()
            val lastDate = sortedPoints.lastOrNull()?.date ?: System.currentTimeMillis()

            Text(
                text = sdf.format(Date(firstDate)),
                style = MaterialTheme.typography.labelSmall,
                color = textLabelColor
            )
            if (sortedPoints.size > 1) {
                Text(
                    text = sdf.format(Date(lastDate)),
                    style = MaterialTheme.typography.labelSmall,
                    color = textLabelColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Reusable Glassmorphic styling utilities for modal sheets, dialog cards, and bottom containers
 */
fun getGlassSpecularBorderBrush(isDarkMode: Boolean): Brush {
    return Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (isDarkMode) 0.35f else 0.85f),
            Color.White.copy(alpha = if (isDarkMode) 0.12f else 0.35f),
            Color.White.copy(alpha = if (isDarkMode) 0.04f else 0.10f),
            Color.Transparent
        )
    )
}

fun getGlassSurfaceColor(isDarkMode: Boolean): Color {
    return if (isDarkMode) Color(0xCC1A1A1A) else Color(0xCCFFFFFF)
}

