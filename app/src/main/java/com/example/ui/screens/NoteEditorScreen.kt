package com.example.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CustomerWithBalance
import com.example.data.NoteChecklistItem
import com.example.data.NoteEntity
import com.example.notes.NoteReminderScheduler
import com.example.ui.AppHaptics
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.AppStrings
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.theme.LocalIsDarkMode
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NoteEditorScreen(
    initialNote: NoteEntity,
    isNewNote: Boolean,
    customers: List<CustomerWithBalance>,
    customLabels: List<String>,
    onAutoSave: (NoteEntity) -> Unit,
    onAddCustomLabel: (String) -> String,
    onDeleteCustomLabel: (String) -> Unit,
    onClose: (NoteEntity) -> Unit
) {
    val isDark = LocalIsDarkMode.current
    val lang = LocalAppLanguage.current
    val context = LocalContext.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val bgColor = if (isDark) Color(0xFF000000) else Color(0xFFFDFDFB)
    val cardColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val accentGreen = if (isDark) Color(0xFF34C759) else Color(0xFF16A34A)
    val softGreenBg = if (isDark) Color(0xFF112618) else Color(0xFFECFDF3)
    val textPrimary = if (isDark) Color(0xFFF5F5F7) else Color(0xFF111827)
    val textSecondary = if (isDark) Color(0xFF8E8E93) else Color(0xFF6B7280)
    val dividerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E7EB)
    val dangerRed = if (isDark) Color(0xFFFF453A) else Color(0xFFDC2626)

    var noteText by remember(initialNote.id) { mutableStateOf(initialNote.text) }
    var noteLabel by remember(initialNote.id) { mutableStateOf(initialNote.label.ifBlank { NoteEntity.LABEL_GENERAL }) }
    var isChecklist by remember(initialNote.id) { mutableStateOf(initialNote.isChecklist) }
    var checklistItems by remember(initialNote.id) {
        val decoded = NoteEntity.decodeChecklist(initialNote.checklistJson)
        mutableStateOf(
            if (initialNote.isChecklist && decoded.isEmpty()) {
                listOf(NoteChecklistItem(text = "", isChecked = false))
            } else {
                decoded
            }
        )
    }
    var isPinned by remember(initialNote.id) { mutableStateOf(initialNote.isPinned) }
    var isDone by remember(initialNote.id) { mutableStateOf(initialNote.isDone) }
    var linkedCustomerId by remember(initialNote.id) { mutableStateOf(initialNote.linkedCustomerId) }
    var linkedCustomerName by remember(initialNote.id) { mutableStateOf(initialNote.linkedCustomerName) }
    var reminderTime by remember(initialNote.id) { mutableStateOf(initialNote.reminderTime) }
    var editedAt by remember(initialNote.id) { mutableStateOf(initialNote.updatedAt) }

    var showLabelDialog by remember { mutableStateOf(false) }
    var showCustomerDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var focusedChecklistItemId by remember { mutableStateOf<String?>(null) }

    val mainTextFocusRequester = remember { FocusRequester() }

    val currentNoteSnapshot by remember {
        derivedStateOf {
            val cleanedChecklist = checklistItems.filter { it.text.isNotBlank() }
            NoteEntity(
                id = initialNote.id,
                text = noteText,
                label = noteLabel,
                isChecklist = isChecklist,
                checklistJson = NoteEntity.encodeChecklist(cleanedChecklist),
                isPinned = isPinned,
                isDone = isDone,
                linkedCustomerId = linkedCustomerId,
                linkedCustomerName = linkedCustomerName,
                reminderTime = reminderTime,
                createdAt = initialNote.createdAt,
                updatedAt = editedAt
            )
        }
    }

    // Track user edits to bump editedAt and auto-save in the background shortly after typing stops
    var hasUserEdited by remember(initialNote.id) { mutableStateOf(false) }

    LaunchedEffect(
        noteText,
        noteLabel,
        isChecklist,
        checklistItems,
        isPinned,
        isDone,
        linkedCustomerId,
        linkedCustomerName,
        reminderTime
    ) {
        if (!hasUserEdited) return@LaunchedEffect
        editedAt = System.currentTimeMillis()
        delay(350)
        onAutoSave(currentNoteSnapshot.copy(updatedAt = editedAt))
    }

    // Auto-open keyboard on blank new note
    LaunchedEffect(initialNote.id, isNewNote) {
        if (isNewNote) {
            delay(120)
            if (isChecklist && checklistItems.isNotEmpty()) {
                focusedChecklistItemId = checklistItems.first().id
            } else {
                try {
                    mainTextFocusRequester.requestFocus()
                    keyboardController?.show()
                } catch (_: Exception) {}
            }
        }
    }

    val handleExit = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onClose(currentNoteSnapshot)
    }

    BackHandler {
        handleExit()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        showReminderDialog = true
    }

    val editedDateFormatted = remember(editedAt) {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        sdf.format(Date(editedAt))
    }

    // First-line Title visual transformation for plain text mode
    val firstLineTitleTransformation = remember(textPrimary) {
        VisualTransformation { text ->
            val raw = text.text
            val newlineIdx = raw.indexOf('\n')
            val titleEnd = if (newlineIdx == -1) raw.length else newlineIdx
            val annotated = buildAnnotatedString {
                append(raw)
                if (titleEnd > 0) {
                    addStyle(
                        SpanStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        ),
                        start = 0,
                        end = titleEnd
                    )
                }
            }
            TransformedText(annotated, OffsetMapping.Identity)
        }
    }

    Scaffold(
        containerColor = bgColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color = bgColor.copy(alpha = 0.95f),
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                AppHaptics.performTap(view)
                                handleExit()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("note_editor_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = AppStrings.notes(lang),
                                tint = accentGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = AppStrings.notes(lang),
                                color = accentGreen,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Mark as Done / Restore button
                            IconButton(
                                onClick = {
                                    AppHaptics.performConfirm(view)
                                    hasUserEdited = true
                                    isDone = !isDone
                                },
                                modifier = Modifier.testTag("note_editor_done_status_button")
                            ) {
                                Icon(
                                    imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                                    contentDescription = if (isDone) AppStrings.restore(lang) else AppStrings.markAsDone(lang),
                                    tint = if (isDone) accentGreen else textSecondary
                                )
                            }

                            // Copy plain text button
                            IconButton(
                                onClick = {
                                    AppHaptics.performTap(view)
                                    val shareText = currentNoteSnapshot.toShareablePlainText()
                                    if (shareText.isNotBlank()) {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        clipboard?.setPrimaryClip(ClipData.newPlainText("Note", shareText))
                                        Toast.makeText(context, AppStrings.copiedToClipboard(lang), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.testTag("note_editor_copy_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = AppStrings.copyText(lang),
                                    tint = textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Done button to hide keyboard
                            TextButton(
                                onClick = {
                                    AppHaptics.performTap(view)
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                },
                                modifier = Modifier.testTag("note_editor_hide_keyboard_button")
                            ) {
                                Text(
                                    text = AppStrings.done(lang),
                                    color = accentGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }

                    // Small "Edited <date>" line at the top
                    Text(
                        text = AppStrings.editedPrefix(editedDateFormatted, lang),
                        color = textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 2.dp)
                    )

                    // Active metadata chips (Label, Linked Customer, Reminder, Done status)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Label pill
                        Surface(
                            color = softGreenBg,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.clickable {
                                AppHaptics.performTap(view)
                                showLabelDialog = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Label,
                                    contentDescription = null,
                                    tint = accentGreen,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = AppStrings.translateNoteLabel(noteLabel, lang),
                                    color = accentGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (linkedCustomerName.isNotBlank()) {
                            Surface(
                                color = if (isDark) Color(0xFF1E293B) else Color(0xFFEFF6FF),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.clickable {
                                    AppHaptics.performTap(view)
                                    showCustomerDialog = true
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = linkedCustomerName,
                                        color = if (isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        if (reminderTime > 0L) {
                            val isOverdue = !isDone && reminderTime < System.currentTimeMillis()
                            val reminderBg = if (isOverdue) {
                                if (isDark) Color(0xFF3A1215) else Color(0xFFFEF2F2)
                            } else {
                                if (isDark) Color(0xFF2C2512) else Color(0xFFFFFBEB)
                            }
                            val reminderFg = if (isOverdue) dangerRed else if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
                            val remFormat = remember(reminderTime) {
                                SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(reminderTime))
                            }
                            Surface(
                                color = reminderBg,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.clickable {
                                    AppHaptics.performTap(view)
                                    showReminderDialog = true
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Alarm,
                                        contentDescription = null,
                                        tint = reminderFg,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = remFormat,
                                        color = reminderFg,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        if (isDone) {
                            Surface(
                                color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF3F4F6),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = AppStrings.done(lang),
                                    color = textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = dividerColor.copy(alpha = 0.5f), thickness = 0.5.dp)
                }
            }
        },
        bottomBar = {
            // Toolbar above the keyboard with: checklist, label, customer, reminder, pin and share
            Surface(
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF8FAFC),
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Column {
                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Checklist toggle
                        NoteToolbarButton(
                            icon = Icons.Default.Checklist,
                            label = AppStrings.checklist(lang),
                            isActive = isChecklist,
                            accentColor = accentGreen,
                            inactiveColor = textSecondary,
                            testTag = "note_toolbar_checklist",
                            onClick = {
                                AppHaptics.performTap(view)
                                hasUserEdited = true
                                if (!isChecklist) {
                                    val lines = noteText.lines()
                                    if (checklistItems.none { it.text.isNotBlank() } && lines.size > 1) {
                                        val titleLine = lines.first()
                                        val bodyLines = lines.drop(1).map { it.trim() }.filter { it.isNotEmpty() }
                                        if (bodyLines.isNotEmpty()) {
                                            noteText = titleLine
                                            checklistItems = bodyLines.map { NoteChecklistItem(text = it, isChecked = false) }
                                        } else {
                                            checklistItems = listOf(NoteChecklistItem(text = "", isChecked = false))
                                        }
                                    } else if (checklistItems.isEmpty()) {
                                        checklistItems = listOf(NoteChecklistItem(text = "", isChecked = false))
                                    }
                                    isChecklist = true
                                } else {
                                    val nonBlankItems = checklistItems.filter { it.text.isNotBlank() }
                                    if (nonBlankItems.isNotEmpty()) {
                                        val appended = nonBlankItems.joinToString("\n") { it.text.trim() }
                                        noteText = if (noteText.isBlank()) appended else "${noteText.trimEnd()}\n$appended"
                                    }
                                    checklistItems = emptyList()
                                    isChecklist = false
                                }
                            }
                        )

                        // 2. Label picker
                        NoteToolbarButton(
                            icon = Icons.Default.Label,
                            label = AppStrings.label(lang),
                            isActive = noteLabel != NoteEntity.LABEL_GENERAL,
                            accentColor = accentGreen,
                            inactiveColor = textSecondary,
                            testTag = "note_toolbar_label",
                            onClick = {
                                AppHaptics.performTap(view)
                                showLabelDialog = true
                            }
                        )

                        // 3. Customer link
                        NoteToolbarButton(
                            icon = Icons.Default.PersonAddAlt1,
                            label = AppStrings.customer(lang),
                            isActive = linkedCustomerId.isNotBlank(),
                            accentColor = accentGreen,
                            inactiveColor = textSecondary,
                            testTag = "note_toolbar_customer",
                            onClick = {
                                AppHaptics.performTap(view)
                                showCustomerDialog = true
                            }
                        )

                        // 4. Reminder
                        NoteToolbarButton(
                            icon = Icons.Default.Notifications,
                            label = AppStrings.reminder(lang),
                            isActive = reminderTime > 0L,
                            accentColor = if (!isDone && reminderTime in 1L until System.currentTimeMillis()) dangerRed else accentGreen,
                            inactiveColor = textSecondary,
                            testTag = "note_toolbar_reminder",
                            onClick = {
                                AppHaptics.performTap(view)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !NoteReminderScheduler.hasNotificationPermission(context)
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    showReminderDialog = true
                                }
                            }
                        )

                        // 5. Pin
                        NoteToolbarButton(
                            icon = Icons.Default.PushPin,
                            label = if (isPinned) AppStrings.unpin(lang) else AppStrings.pin(lang),
                            isActive = isPinned,
                            accentColor = accentGreen,
                            inactiveColor = textSecondary,
                            testTag = "note_toolbar_pin",
                            onClick = {
                                AppHaptics.performTap(view)
                                hasUserEdited = true
                                isPinned = !isPinned
                            }
                        )

                        // 6. Share
                        NoteToolbarButton(
                            icon = Icons.Default.Share,
                            label = AppStrings.share(lang),
                            isActive = false,
                            accentColor = accentGreen,
                            inactiveColor = textSecondary,
                            testTag = "note_toolbar_share",
                            onClick = {
                                AppHaptics.performTap(view)
                                val plainText = currentNoteSnapshot.toShareablePlainText()
                                if (plainText.isNotBlank()) {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, plainText)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, AppStrings.share(lang)))
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .alpha(if (isDone) 0.65f else 1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            if (!isChecklist) {
                // Full screen plain text editor where the first line is styled as the Title
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 420.dp)
                ) {
                    if (noteText.isEmpty()) {
                        Column {
                            Text(
                                text = AppStrings.noteTitlePlaceholder(lang),
                                color = textSecondary.copy(alpha = 0.55f),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = AppStrings.noteBodyPlaceholder(lang),
                                color = textSecondary.copy(alpha = 0.45f),
                                fontSize = 16.sp
                            )
                        }
                    }
                    BasicTextField(
                        value = noteText,
                        onValueChange = {
                            hasUserEdited = true
                            noteText = it
                        },
                        textStyle = TextStyle(
                            color = textPrimary,
                            fontSize = 16.sp,
                            lineHeight = 24.sp
                        ),
                        cursorBrush = SolidColor(accentGreen),
                        visualTransformation = firstLineTitleTransformation,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 420.dp)
                            .focusRequester(mainTextFocusRequester)
                            .testTag("note_plain_text_input")
                    )
                }
            } else {
                // Checklist mode: Title line at top + interactive checklist with unchecked & Completed sections
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (noteText.isEmpty()) {
                        Text(
                            text = AppStrings.noteTitlePlaceholder(lang),
                            color = textSecondary.copy(alpha = 0.55f),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    BasicTextField(
                        value = noteText,
                        onValueChange = {
                            hasUserEdited = true
                            noteText = it
                        },
                        textStyle = TextStyle(
                            color = textPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 28.sp
                        ),
                        cursorBrush = SolidColor(accentGreen),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                val firstUnchecked = checklistItems.firstOrNull { !it.isChecked }
                                if (firstUnchecked != null) {
                                    focusedChecklistItemId = firstUnchecked.id
                                } else {
                                    val newItem = NoteChecklistItem(text = "", isChecked = false)
                                    hasUserEdited = true
                                    checklistItems = listOf(newItem) + checklistItems
                                    focusedChecklistItemId = newItem.id
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(mainTextFocusRequester)
                            .testTag("note_checklist_title_input")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val activeTotal = checklistItems.count { it.text.isNotBlank() }.coerceAtLeast(checklistItems.size)
                val completedCount = checklistItems.count { it.isChecked }
                // Progress badge ("3 of 8 done")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Surface(
                        color = softGreenBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = AppStrings.checklistProgressText(completedCount, activeTotal, lang),
                            color = accentGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                val uncheckedItems = checklistItems.filter { !it.isChecked }
                val checkedItems = checklistItems.filter { it.isChecked }

                // Unchecked items
                uncheckedItems.forEach { item ->
                    key(item.id) {
                        ChecklistRowEditor(
                            item = item,
                            isFocused = focusedChecklistItemId == item.id,
                            accentGreen = accentGreen,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            lang = lang,
                            onToggleCheck = {
                                AppHaptics.performTick(view)
                                hasUserEdited = true
                                checklistItems = checklistItems.map {
                                    if (it.id == item.id) it.copy(isChecked = true) else it
                                }
                            },
                            onTextChange = { newText ->
                                hasUserEdited = true
                                if (newText.contains('\n')) {
                                    val parts = newText.split('\n')
                                    val currentLineText = parts.first()
                                    val nextLineText = parts.drop(1).joinToString(" ")
                                    val newItem = NoteChecklistItem(text = nextLineText, isChecked = false)
                                    val idx = checklistItems.indexOfFirst { it.id == item.id }
                                    val mutable = checklistItems.toMutableList()
                                    if (idx != -1) {
                                        mutable[idx] = item.copy(text = currentLineText)
                                        mutable.add(idx + 1, newItem)
                                    } else {
                                        mutable.add(newItem)
                                    }
                                    checklistItems = mutable
                                    focusedChecklistItemId = newItem.id
                                } else {
                                    checklistItems = checklistItems.map {
                                        if (it.id == item.id) it.copy(text = newText) else it
                                    }
                                }
                            },
                            onNextPressed = {
                                hasUserEdited = true
                                val newItem = NoteChecklistItem(text = "", isChecked = false)
                                val idx = checklistItems.indexOfFirst { it.id == item.id }
                                val mutable = checklistItems.toMutableList()
                                if (idx != -1) {
                                    mutable.add(idx + 1, newItem)
                                } else {
                                    mutable.add(newItem)
                                }
                                checklistItems = mutable
                                focusedChecklistItemId = newItem.id
                            },
                            onDeleteRow = {
                                hasUserEdited = true
                                val remaining = checklistItems.filterNot { it.id == item.id }
                                checklistItems = if (remaining.isEmpty()) {
                                    listOf(NoteChecklistItem(text = "", isChecked = false))
                                } else {
                                    remaining
                                }
                            }
                        )
                    }
                }

                // "+ Add item" row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            AppHaptics.performTap(view)
                            hasUserEdited = true
                            val newItem = NoteChecklistItem(text = "", isChecked = false)
                            checklistItems = checklistItems + newItem
                            focusedChecklistItemId = newItem.id
                        }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = AppStrings.addChecklistItem(lang),
                        tint = accentGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = AppStrings.addChecklistItem(lang),
                        color = accentGreen,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Completed section at the bottom
                AnimatedVisibility(
                    visible = checkedItems.isNotEmpty(),
                    enter = fadeIn() + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        HorizontalDivider(color = dividerColor.copy(alpha = 0.6f), thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "${AppStrings.completedHeading(lang)} (${checkedItems.size})",
                            color = textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        checkedItems.forEach { item ->
                            key(item.id) {
                                ChecklistRowEditor(
                                    item = item,
                                    isFocused = false,
                                    accentGreen = accentGreen,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    lang = lang,
                                    onToggleCheck = {
                                        AppHaptics.performTick(view)
                                        hasUserEdited = true
                                        checklistItems = checklistItems.map {
                                            if (it.id == item.id) it.copy(isChecked = false) else it
                                        }
                                    },
                                    onTextChange = { newText ->
                                        hasUserEdited = true
                                        checklistItems = checklistItems.map {
                                            if (it.id == item.id) it.copy(text = newText.replace("\n", " ")) else it
                                        }
                                    },
                                    onNextPressed = {},
                                    onDeleteRow = {
                                        hasUserEdited = true
                                        checklistItems = checklistItems.filterNot { it.id == item.id }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(120.dp))
        }
    }

    // Label Picker & Creator Dialog
    if (showLabelDialog) {
        NoteLabelPickerDialog(
            selectedLabel = noteLabel,
            customLabels = customLabels,
            accentGreen = accentGreen,
            cardColor = cardColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            lang = lang,
            onSelectLabel = { chosen ->
                hasUserEdited = true
                noteLabel = chosen
                showLabelDialog = false
            },
            onCreateLabel = { newName ->
                val created = onAddCustomLabel(newName)
                hasUserEdited = true
                noteLabel = created
                showLabelDialog = false
            },
            onDeleteCustomLabel = { labelToDelete ->
                onDeleteCustomLabel(labelToDelete)
                if (noteLabel.equals(labelToDelete, ignoreCase = true)) {
                    hasUserEdited = true
                    noteLabel = NoteEntity.LABEL_GENERAL
                }
            },
            onDismiss = { showLabelDialog = false }
        )
    }

    // Customer Link Picker Dialog
    if (showCustomerDialog) {
        NoteCustomerPickerDialog(
            customers = customers,
            selectedCustomerId = linkedCustomerId,
            accentGreen = accentGreen,
            cardColor = cardColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            lang = lang,
            onSelectCustomer = { id, name ->
                hasUserEdited = true
                linkedCustomerId = id
                linkedCustomerName = name
                showCustomerDialog = false
            },
            onUnlink = {
                hasUserEdited = true
                linkedCustomerId = ""
                linkedCustomerName = ""
                showCustomerDialog = false
            },
            onDismiss = { showCustomerDialog = false }
        )
    }

    // Reminder Date (DD/MM/YYYY) & Time Picker Dialog
    if (showReminderDialog) {
        NoteReminderPickerDialog(
            initialReminderTime = reminderTime,
            accentGreen = accentGreen,
            cardColor = cardColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            lang = lang,
            onSaveReminder = { timestamp ->
                hasUserEdited = true
                reminderTime = timestamp
                showReminderDialog = false
                Toast.makeText(context, AppStrings.setReminder(lang), Toast.LENGTH_SHORT).show()
            },
            onClearReminder = {
                hasUserEdited = true
                reminderTime = 0L
                showReminderDialog = false
                Toast.makeText(context, AppStrings.clearReminder(lang), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showReminderDialog = false }
        )
    }
}

@Composable
private fun NoteToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    accentColor: Color,
    inactiveColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    val tint by animateColorAsState(
        targetValue = if (isActive) accentColor else inactiveColor,
        label = "toolbarTint"
    )
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(21.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun ChecklistRowEditor(
    item: NoteChecklistItem,
    isFocused: Boolean,
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    lang: AppLanguage,
    onToggleCheck: () -> Unit,
    onTextChange: (String) -> Unit,
    onNextPressed: () -> Unit,
    onDeleteRow: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isFocused) {
        if (isFocused) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onToggleCheck,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (item.isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = AppStrings.checklist(lang),
                tint = if (item.isChecked) accentGreen else textSecondary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (item.text.isEmpty() && !item.isChecked) {
                Text(
                    text = AppStrings.addChecklistItem(lang),
                    color = textSecondary.copy(alpha = 0.45f),
                    fontSize = 16.sp
                )
            }
            BasicTextField(
                value = item.text,
                onValueChange = onTextChange,
                textStyle = TextStyle(
                    color = if (item.isChecked) textSecondary else textPrimary,
                    fontSize = 16.sp,
                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
                ),
                cursorBrush = SolidColor(accentGreen),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { onNextPressed() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }

        IconButton(
            onClick = onDeleteRow,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = AppStrings.delete(lang),
                tint = textSecondary.copy(alpha = 0.55f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun NoteLabelPickerDialog(
    selectedLabel: String,
    customLabels: List<String>,
    accentGreen: Color,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    lang: AppLanguage,
    onSelectLabel: (String) -> Unit,
    onCreateLabel: (String) -> Unit,
    onDeleteCustomLabel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newLabelText by remember { mutableStateOf("") }
    val allLabels = remember(customLabels) {
        (NoteEntity.DEFAULT_LABELS + customLabels).distinct()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cardColor,
        title = {
            Text(
                text = AppStrings.label(lang),
                color = textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Create new label input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newLabelText,
                        onValueChange = { newLabelText = it },
                        placeholder = { Text(AppStrings.newLabel(lang), fontSize = 14.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    FilledIconButton(
                        onClick = {
                            if (newLabelText.isNotBlank()) {
                                onCreateLabel(newLabelText.trim())
                                newLabelText = ""
                            }
                        },
                        enabled = newLabelText.isNotBlank(),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = accentGreen)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = AppStrings.newLabel(lang),
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(allLabels) { label ->
                        val isSelected = label.equals(selectedLabel, ignoreCase = true)
                        val isCustom = NoteEntity.DEFAULT_LABELS.none { it.equals(label, ignoreCase = true) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) accentGreen.copy(alpha = 0.14f) else Color.Transparent)
                                .clickable { onSelectLabel(label) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Outlined.Label,
                                    contentDescription = null,
                                    tint = if (isSelected) accentGreen else textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = AppStrings.translateNoteLabel(label, lang),
                                    color = if (isSelected) accentGreen else textPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                            }
                            if (isCustom) {
                                IconButton(
                                    onClick = { onDeleteCustomLabel(label) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = AppStrings.delete(lang),
                                        tint = textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.cancel(lang), color = textSecondary)
            }
        }
    )
}

@Composable
private fun NoteCustomerPickerDialog(
    customers: List<CustomerWithBalance>,
    selectedCustomerId: String,
    accentGreen: Color,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    lang: AppLanguage,
    onSelectCustomer: (String, String) -> Unit,
    onUnlink: () -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(customers, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) customers
        else customers.filter {
            it.customer.name.lowercase().contains(q) ||
                it.customer.phone.lowercase().contains(q) ||
                it.customer.businessName.lowercase().contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cardColor,
        title = {
            Text(
                text = AppStrings.linkCustomer(lang),
                color = textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text(AppStrings.search(lang), fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedCustomerId.isNotBlank()) {
                    TextButton(
                        onClick = onUnlink,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AppStrings.unlinkCustomer(lang), color = Color(0xFFDC2626))
                    }
                }

                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered, key = { it.customer.id }) { item ->
                        val isSelected = item.customer.id == selectedCustomerId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) accentGreen.copy(alpha = 0.14f) else Color.Transparent)
                                .clickable { onSelectCustomer(item.customer.id, item.customer.name) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = accentGreen.copy(alpha = 0.15f),
                                shape = CircleShape,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = item.customer.name.take(1).uppercase(),
                                        color = accentGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.customer.name,
                                    color = textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                if (item.customer.phone.isNotBlank()) {
                                    Text(
                                        text = item.customer.phone,
                                        color = textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = accentGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.cancel(lang), color = textSecondary)
            }
        }
    )
}

@Composable
private fun NoteReminderPickerDialog(
    initialReminderTime: Long,
    accentGreen: Color,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    lang: AppLanguage,
    onSaveReminder: (Long) -> Unit,
    onClearReminder: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val initialCal = remember(initialReminderTime) {
        Calendar.getInstance().apply {
            if (initialReminderTime > 0L) {
                timeInMillis = initialReminderTime
            } else {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
        }
    }

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.US) }
    var dateInput by remember { mutableStateOf(dateFormatter.format(initialCal.time)) }
    var selectedHour by remember { mutableIntStateOf(initialCal.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableIntStateOf(initialCal.get(Calendar.MINUTE)) }

    val formattedTime = remember(selectedHour, selectedMinute) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
        }
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cardColor,
        title = {
            Text(
                text = AppStrings.setReminder(lang),
                color = textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Date input DD/MM/YYYY + DatePicker icon
                OutlinedTextField(
                    value = dateInput,
                    onValueChange = { dateInput = it },
                    label = { Text(AppStrings.dateLabel(lang)) },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val nowCal = Calendar.getInstance()
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        dateInput = String.format(Locale.US, "%02d/%02d/%04d", d, m + 1, y)
                                    },
                                    nowCal.get(Calendar.YEAR),
                                    nowCal.get(Calendar.MONTH),
                                    nowCal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = accentGreen)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Time picker row
                OutlinedCard(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, min ->
                                selectedHour = h
                                selectedMinute = min
                            },
                            selectedHour,
                            selectedMinute,
                            false
                        ).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, textSecondary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = accentGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = AppStrings.timeLabel(lang),
                                color = textSecondary,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = formattedTime,
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                if (initialReminderTime > 0L) {
                    TextButton(
                        onClick = onClearReminder,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.NotificationsOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AppStrings.clearReminder(lang), color = Color(0xFFDC2626))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedDate = try {
                        dateFormatter.isLenient = false
                        dateFormatter.parse(dateInput.trim())
                    } catch (_: Exception) {
                        null
                    }
                    val targetCal = Calendar.getInstance()
                    if (parsedDate != null) {
                        targetCal.time = parsedDate
                    }
                    targetCal.set(Calendar.HOUR_OF_DAY, selectedHour)
                    targetCal.set(Calendar.MINUTE, selectedMinute)
                    targetCal.set(Calendar.SECOND, 0)
                    targetCal.set(Calendar.MILLISECOND, 0)
                    onSaveReminder(targetCal.timeInMillis)
                },
                colors = ButtonDefaults.buttonColors(containerColor = accentGreen)
            ) {
                Text(AppStrings.save(lang), color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.cancel(lang), color = textSecondary)
            }
        }
    )
}
