package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CustomerWithBalance
import com.example.data.NoteChecklistItem
import com.example.data.NoteEntity
import com.example.ui.AppHaptics
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.AppStrings
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.theme.LocalIsDarkMode
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

private const val FILTER_ALL = "ALL"
private const val FILTER_DONE = "DONE"

@Composable
fun NotesHeaderIconButton(
    hasDueReminder: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkMode.current
    val lang = LocalAppLanguage.current
    val view = LocalView.current
    val bgTint = if (isDark) Color(0xFF1F2937) else Color(0xFFECFDF3)
    val iconTint = if (isDark) Color(0xFF34D399) else Color(0xFF16A34A)
    val borderTint = if (isDark) Color(0xFF065F46) else Color(0xFFBBF7D0)

    Box(
        modifier = modifier.size(38.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = {
                AppHaptics.performTap(view)
                onClick()
            },
            shape = CircleShape,
            color = bgTint,
            border = BorderStroke(1.dp, borderTint),
            modifier = Modifier
                .size(36.dp)
                .testTag("open_notes_header_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.StickyNote2,
                    contentDescription = AppStrings.notes(lang),
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Small dot when a note has a reminder due today or overdue
        if (hasDueReminder) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-1).dp, y = 1.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF111827) else Color.White)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444))
                    .testTag("notes_due_reminder_dot")
            )
        }
    }
}

private data class NoteSectionGroup(
    val key: String,
    val title: String,
    val isPinnedSection: Boolean,
    val notes: List<NoteEntity>
)

private data class ActiveNoteSession(
    val note: NoteEntity,
    val isNew: Boolean
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotesSheetScreen(
    notes: List<NoteEntity>,
    customLabels: List<String>,
    customers: List<CustomerWithBalance>,
    initialNoteIdToOpen: String? = null,
    onConsumeInitialNoteId: () -> Unit = {},
    onSaveNote: (NoteEntity) -> Unit,
    onDeleteNotes: (List<NoteEntity>) -> Unit,
    onRestoreNotes: (List<NoteEntity>) -> Unit,
    onSetNotesPinned: (Collection<String>, Boolean) -> Unit,
    onSetNotesDone: (Collection<String>, Boolean) -> Unit,
    onAddCustomLabel: (String) -> String,
    onDeleteCustomLabel: (String) -> Unit,
    onClose: () -> Unit
) {
    val isDark = LocalIsDarkMode.current
    val lang = LocalAppLanguage.current
    val view = LocalView.current

    val sheetBg = if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7)
    val cardBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val pinnedCardBg = if (isDark) Color(0xFF112419) else Color(0xFFF0FDF4)
    val pinnedBorder = if (isDark) Color(0xFF1E462D) else Color(0xFFBBF7D0)
    val accentGreen = if (isDark) Color(0xFF34C759) else Color(0xFF16A34A)
    val softGreenChip = if (isDark) Color(0xFF142E1F) else Color(0xFFDCFCE7)
    val textPrimary = if (isDark) Color(0xFFF5F5F7) else Color(0xFF111827)
    val textSecondary = if (isDark) Color(0xFF8E8E93) else Color(0xFF6B7280)
    val dividerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E7EB)
    val dangerRed = if (isDark) Color(0xFFFF453A) else Color(0xFFDC2626)

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(FILTER_ALL) }
    var selectedNoteIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedNoteIds.isNotEmpty()

    var activeEditorSession by remember { mutableStateOf<ActiveNoteSession?>(null) }
    var undoDeletedNotes by remember { mutableStateOf<List<NoteEntity>>(emptyList()) }
    var undoBannerMessage by remember { mutableStateOf<String?>(null) }

    // Open note from notification tap if requested
    LaunchedEffect(initialNoteIdToOpen, notes) {
        if (!initialNoteIdToOpen.isNullOrBlank()) {
            val found = notes.firstOrNull { it.id == initialNoteIdToOpen }
            if (found != null) {
                activeEditorSession = ActiveNoteSession(note = found, isNew = false)
                onConsumeInitialNoteId()
            }
        }
    }

    // Auto-dismiss undo banner after 4.5 seconds
    LaunchedEffect(undoBannerMessage) {
        if (undoBannerMessage != null) {
            delay(4500)
            undoBannerMessage = null
            undoDeletedNotes = emptyList()
        }
    }

    BackHandler(enabled = activeEditorSession == null) {
        if (isSelectionMode) {
            selectedNoteIds = emptySet()
        } else {
            onClose()
        }
    }

    val createNewNote = {
        AppHaptics.performTap(view)
        val activeLabel = when (selectedFilter) {
            FILTER_ALL, FILTER_DONE -> NoteEntity.LABEL_GENERAL
            else -> selectedFilter
        }
        val startsAsChecklist = activeLabel == NoteEntity.LABEL_PURCHASE_LIST
        val initialChecklistJson = if (startsAsChecklist) {
            NoteEntity.encodeChecklist(listOf(NoteChecklistItem(text = "", isChecked = false)))
        } else {
            "[]"
        }
        val blankNote = NoteEntity(
            id = UUID.randomUUID().toString(),
            text = "",
            label = activeLabel,
            isChecklist = startsAsChecklist,
            checklistJson = initialChecklistJson,
            isPinned = false,
            isDone = false
        )
        activeEditorSession = ActiveNoteSession(note = blankNote, isNew = true)
    }

    // Filter & group notes
    val filteredNotes = remember(notes, searchQuery, selectedFilter) {
        val q = searchQuery.trim().lowercase()
        notes.filter { note ->
            if (note.isEmptyNote) return@filter false
            val matchesFilter = when (selectedFilter) {
                FILTER_DONE -> note.isDone
                FILTER_ALL -> !note.isDone
                else -> !note.isDone && note.label.equals(selectedFilter, ignoreCase = true)
            }
            if (!matchesFilter) return@filter false
            q.isEmpty() || note.searchTokens.contains(q)
        }.sortedByDescending { it.updatedAt }
    }

    val groupedSections = remember(filteredNotes, selectedFilter, lang) {
        buildNoteSections(filteredNotes, lang)
    }

    val listState = rememberLazyListState()
    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 44
        }
    }
    val collapsedHeaderAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "collapsedHeaderAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = sheetBg,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                // Frosted iOS top header bar
                Surface(
                    color = if (isScrolled || isSelectionMode) {
                        (if (isDark) Color(0xFF161618) else Color(0xFFF9FAFB)).copy(alpha = 0.94f)
                    } else {
                        sheetBg
                    },
                    tonalElevation = if (isScrolled) 2.dp else 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        if (isSelectionMode) {
                            val selectedNotes = remember(notes, selectedNoteIds) {
                                notes.filter { it.id in selectedNoteIds }
                            }
                            val anyUnpinned = selectedNotes.any { !it.isPinned }
                            val anyNotDone = selectedNotes.any { !it.isDone }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            AppHaptics.performTap(view)
                                            selectedNoteIds = emptySet()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = AppStrings.cancel(lang),
                                            tint = textPrimary
                                        )
                                    }
                                    Text(
                                        text = "${selectedNoteIds.size}",
                                        color = textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Pin / Unpin selected
                                    IconButton(
                                        onClick = {
                                            AppHaptics.performTap(view)
                                            onSetNotesPinned(selectedNoteIds, anyUnpinned)
                                            selectedNoteIds = emptySet()
                                        },
                                        modifier = Modifier.testTag("notes_bulk_pin_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PushPin,
                                            contentDescription = if (anyUnpinned) AppStrings.pin(lang) else AppStrings.unpin(lang),
                                            tint = accentGreen
                                        )
                                    }

                                    // Mark done / Restore selected
                                    IconButton(
                                        onClick = {
                                            AppHaptics.performConfirm(view)
                                            onSetNotesDone(selectedNoteIds, anyNotDone)
                                            selectedNoteIds = emptySet()
                                        },
                                        modifier = Modifier.testTag("notes_bulk_done_button")
                                    ) {
                                        Icon(
                                            imageVector = if (anyNotDone) Icons.Default.CheckCircleOutline else Icons.Default.Restore,
                                            contentDescription = if (anyNotDone) AppStrings.markAsDone(lang) else AppStrings.restore(lang),
                                            tint = accentGreen
                                        )
                                    }

                                    // Delete selected with Undo
                                    IconButton(
                                        onClick = {
                                            AppHaptics.performTap(view)
                                            val toDelete = notes.filter { it.id in selectedNoteIds }
                                            if (toDelete.isNotEmpty()) {
                                                undoDeletedNotes = toDelete
                                                undoBannerMessage = AppStrings.notesDeletedMessage(toDelete.size, lang)
                                                onDeleteNotes(toDelete)
                                            }
                                            selectedNoteIds = emptySet()
                                        },
                                        modifier = Modifier.testTag("notes_bulk_delete_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = AppStrings.delete(lang),
                                            tint = dangerRed
                                        )
                                    }
                                }
                            }
                        } else {
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
                                        onClose()
                                    },
                                    modifier = Modifier.testTag("notes_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = AppStrings.close(lang),
                                        tint = accentGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = AppStrings.close(lang),
                                        color = accentGreen,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp
                                    )
                                }

                                // Shrunk header title when scrolled
                                Text(
                                    text = AppStrings.notes(lang),
                                    color = textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    modifier = Modifier.alpha(collapsedHeaderAlpha)
                                )

                                // Count badge on right
                                Surface(
                                    color = softGreenChip,
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = filteredNotes.size.toString(),
                                        color = accentGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        if (isScrolled || isSelectionMode) {
                            HorizontalDivider(color = dividerColor.copy(alpha = 0.6f), thickness = 0.5.dp)
                        }
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = createNewNote,
                    containerColor = accentGreen,
                    contentColor = Color.White,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .navigationBarsPadding()
                        .size(58.dp)
                        .testTag("notes_fab_add")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = AppStrings.newNote(lang),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp)
                ) {
                    // 1. Large iOS "Notes" title that shrinks into the header on scroll
                    item(key = "large_title_and_search") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = AppStrings.notes(lang),
                                color = textPrimary,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp,
                                modifier = Modifier
                                    .alpha(1f - collapsedHeaderAlpha)
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Instant Search Bar
                            Surface(
                                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = AppStrings.searchNotes(lang),
                                                color = textSecondary,
                                                fontSize = 15.sp
                                            )
                                        }
                                        BasicTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                color = textPrimary,
                                                fontSize = 15.sp
                                            ),
                                            cursorBrush = SolidColor(accentGreen),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("notes_search_input")
                                        )
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        Icon(
                                            imageVector = Icons.Default.Cancel,
                                            contentDescription = AppStrings.cancel(lang),
                                            tint = textSecondary,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable { searchQuery = "" }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Filter Chips Row: All, Purchase List, Customers, Important Dates, To Remember, Done, + custom labels
                            val filterItems = remember(customLabels, lang) {
                                buildList {
                                    add(FILTER_ALL to AppStrings.all(lang))
                                    add(NoteEntity.LABEL_PURCHASE_LIST to AppStrings.labelPurchaseList(lang))
                                    add(NoteEntity.LABEL_CUSTOMERS to AppStrings.labelCustomers(lang))
                                    add(NoteEntity.LABEL_IMPORTANT_DATES to AppStrings.labelImportantDates(lang))
                                    add(NoteEntity.LABEL_TO_REMEMBER to AppStrings.labelToRemember(lang))
                                    add(FILTER_DONE to AppStrings.done(lang))
                                    customLabels.forEach { custom ->
                                        add(custom to custom)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                filterItems.forEach { (key, displayLabel) ->
                                    val isSelected = selectedFilter.equals(key, ignoreCase = true)
                                    Surface(
                                        onClick = {
                                            AppHaptics.performTap(view)
                                            selectedFilter = key
                                        },
                                        shape = RoundedCornerShape(50),
                                        color = if (isSelected) accentGreen else cardBg,
                                        border = if (isSelected) null else BorderStroke(1.dp, dividerColor),
                                        modifier = Modifier.testTag("notes_filter_chip_$key")
                                    ) {
                                        Text(
                                            text = displayLabel,
                                            color = if (isSelected) Color.White else textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    if (groupedSections.isEmpty()) {
                        // Friendly Empty State with "New note" button
                        item(key = "notes_empty_state") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp, vertical = 64.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    color = softGreenChip,
                                    shape = CircleShape,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.StickyNote2,
                                            contentDescription = null,
                                            tint = accentGreen,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = AppStrings.notesEmptyTitle(lang),
                                    color = textPrimary,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = AppStrings.notesEmptySubtitle(lang),
                                    color = textSecondary,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = createNewNote,
                                    colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                                    shape = RoundedCornerShape(50),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                                    modifier = Modifier.testTag("notes_empty_new_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = AppStrings.newNote(lang),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    } else {
                        // Grouped Note Sections in iOS-style rounded cards
                        items(
                            items = groupedSections,
                            key = { it.key }
                        ) { section ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(
                                        fadeInSpec = tween(220),
                                        fadeOutSpec = tween(180),
                                        placementSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                // Section Header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (section.isPinnedSection) {
                                        Icon(
                                            imageVector = Icons.Default.PushPin,
                                            contentDescription = null,
                                            tint = accentGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = section.title,
                                        color = if (section.isPinnedSection) accentGreen else textSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "(${section.notes.size})",
                                        color = textSecondary.copy(alpha = 0.75f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                // Rounded Grouped Card
                                Surface(
                                    color = if (section.isPinnedSection) pinnedCardBg else cardBg,
                                    shape = RoundedCornerShape(18.dp),
                                    border = if (section.isPinnedSection) {
                                        BorderStroke(1.dp, pinnedBorder)
                                    } else {
                                        BorderStroke(0.5.dp, dividerColor.copy(alpha = 0.7f))
                                    },
                                    shadowElevation = if (isDark) 0.dp else 1.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        section.notes.forEachIndexed { index, note ->
                                            key(note.id) {
                                                SwipeableNoteRow(
                                                    note = note,
                                                    isSelected = note.id in selectedNoteIds,
                                                    isSelectionMode = isSelectionMode,
                                                    accentGreen = accentGreen,
                                                    softGreenChip = softGreenChip,
                                                    textPrimary = textPrimary,
                                                    textSecondary = textSecondary,
                                                    dangerRed = dangerRed,
                                                    lang = lang,
                                                    onClick = {
                                                        AppHaptics.performTap(view)
                                                        if (isSelectionMode) {
                                                            selectedNoteIds = if (note.id in selectedNoteIds) {
                                                                selectedNoteIds - note.id
                                                            } else {
                                                                selectedNoteIds + note.id
                                                            }
                                                        } else {
                                                            activeEditorSession = ActiveNoteSession(note = note, isNew = false)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        AppHaptics.performConfirm(view)
                                                        selectedNoteIds = if (note.id in selectedNoteIds) {
                                                            selectedNoteIds - note.id
                                                        } else {
                                                            selectedNoteIds + note.id
                                                        }
                                                    },
                                                    onSwipePinToggle = {
                                                        AppHaptics.performTap(view)
                                                        onSetNotesPinned(listOf(note.id), !note.isPinned)
                                                    },
                                                    onSwipeDelete = {
                                                        AppHaptics.performTap(view)
                                                        undoDeletedNotes = listOf(note)
                                                        undoBannerMessage = AppStrings.notesDeletedMessage(1, lang)
                                                        onDeleteNotes(listOf(note))
                                                    }
                                                )
                                                if (index < section.notes.lastIndex) {
                                                    HorizontalDivider(
                                                        color = dividerColor.copy(alpha = 0.55f),
                                                        thickness = 0.5.dp,
                                                        modifier = Modifier.padding(start = 16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Undo Toast Bar when a note is deleted
                AnimatedVisibility(
                    visible = undoBannerMessage != null && undoDeletedNotes.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Surface(
                        color = if (isDark) Color(0xFF2C2C2E) else Color(0xFF1F2937),
                        shape = RoundedCornerShape(14.dp),
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = undoBannerMessage ?: "",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            TextButton(
                                onClick = {
                                    AppHaptics.performConfirm(view)
                                    val toRestore = undoDeletedNotes
                                    undoBannerMessage = null
                                    undoDeletedNotes = emptyList()
                                    onRestoreNotes(toRestore)
                                },
                                modifier = Modifier.testTag("notes_undo_delete_button")
                            ) {
                                Text(
                                    text = AppStrings.undo(lang),
                                    color = Color(0xFF4ADE80),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Smooth transition when opening a note editor from its row or "+" button
        AnimatedVisibility(
            visible = activeEditorSession != null,
            enter = fadeIn(animationSpec = tween(200)) +
                scaleIn(
                    initialScale = 0.94f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ),
            exit = fadeOut(animationSpec = tween(180)) +
                scaleOut(targetScale = 0.95f, animationSpec = tween(180))
        ) {
            val session = activeEditorSession
            if (session != null) {
                NoteEditorScreen(
                    initialNote = session.note,
                    isNewNote = session.isNew,
                    customers = customers,
                    customLabels = customLabels,
                    onAutoSave = { updated ->
                        onSaveNote(updated)
                    },
                    onAddCustomLabel = onAddCustomLabel,
                    onDeleteCustomLabel = onDeleteCustomLabel,
                    onClose = { finalNote ->
                        onSaveNote(finalNote)
                        activeEditorSession = null
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableNoteRow(
    note: NoteEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    accentGreen: Color,
    softGreenChip: Color,
    textPrimary: Color,
    textSecondary: Color,
    dangerRed: Color,
    lang: AppLanguage,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipePinToggle: () -> Unit,
    onSwipeDelete: () -> Unit
) {
    val isDark = LocalIsDarkMode.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            when (target) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onSwipePinToggle()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onSwipeDelete()
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !isSelectionMode,
        enableDismissFromEndToStart = !isSelectionMode,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val bgColor = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> accentGreen
                SwipeToDismissBoxValue.EndToStart -> dangerRed
                SwipeToDismissBoxValue.Settled -> Color.Transparent
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.CenterEnd
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Default.PushPin
                else -> Icons.Default.DeleteOutline
            }
            val scale by animateFloatAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) 0.85f else 1.1f,
                label = "swipeIconScale"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(22.dp)
                            .scale(scale)
                    )
                }
            }
        }
    ) {
        val rowBg = if (isSelected) {
            accentGreen.copy(alpha = 0.14f)
        } else {
            Color.Transparent
        }

        val dateStr = remember(note.updatedAt, lang) {
            formatNoteRowDate(note.updatedAt, lang)
        }
        val isOverdueReminder = !note.isDone && note.reminderTime in 1L until System.currentTimeMillis()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowBg)
                .alpha(if (note.isDone) 0.58f else 1f)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("note_row_${note.id}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) accentGreen else textSecondary,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                // First line: Title + tiny status icons on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = note.titleText.ifBlank { AppStrings.untitledNote(lang) },
                        color = textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (note.isDone) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.weight(1f)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        if (note.isPinned) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = AppStrings.pinned(lang),
                                tint = accentGreen,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        if (note.reminderTime > 0L) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = AppStrings.reminder(lang),
                                tint = if (isOverdueReminder) dangerRed else Color(0xFFF59E0B),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        if (note.linkedCustomerName.isNotBlank()) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = note.linkedCustomerName,
                                tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // Second line: Short preview
                if (note.previewText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = note.previewText,
                        color = textSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom metadata row: Last edited date, small label tag, checklist progress (3/8), linked customer name, overdue reminder time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = dateStr,
                        color = textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // Small label tag
                    Surface(
                        color = softGreenChip,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = AppStrings.translateNoteLabel(note.label, lang),
                            color = accentGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Checklist progress (e.g., 3/8)
                    if (note.isChecklist && note.totalChecklistCount > 0) {
                        Surface(
                            color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF3F4F6),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Checklist,
                                    contentDescription = null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${note.completedChecklistCount}/${note.totalChecklistCount}",
                                    color = textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Linked customer pill
                    if (note.linkedCustomerName.isNotBlank()) {
                        Text(
                            text = "• ${note.linkedCustomerName}",
                            color = if (isDark) Color(0xFF93C5FD) else Color(0xFF2563EB),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Overdue or upcoming reminder time
                    if (note.reminderTime > 0L) {
                        val remStr = remember(note.reminderTime) {
                            SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(note.reminderTime))
                        }
                        Text(
                            text = remStr,
                            color = if (isOverdueReminder) dangerRed else textSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isOverdueReminder) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

private fun buildNoteSections(notes: List<NoteEntity>, lang: AppLanguage): List<NoteSectionGroup> {
    if (notes.isEmpty()) return emptyList()

    val pinned = notes.filter { it.isPinned }.sortedByDescending { it.updatedAt }
    val unpinned = notes.filter { !it.isPinned }.sortedByDescending { it.updatedAt }

    val result = mutableListOf<NoteSectionGroup>()
    if (pinned.isNotEmpty()) {
        result.add(
            NoteSectionGroup(
                key = "section_pinned",
                title = AppStrings.pinned(lang),
                isPinnedSection = true,
                notes = pinned
            )
        )
    }

    val nowCal = Calendar.getInstance()
    val todayStart = (nowCal.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val yesterdayStart = todayStart - 24L * 3600L * 1000L
    val prev7DaysStart = todayStart - 7L * 24L * 3600L * 1000L

    val todayNotes = mutableListOf<NoteEntity>()
    val yesterdayNotes = mutableListOf<NoteEntity>()
    val prev7DaysNotes = mutableListOf<NoteEntity>()
    val monthBuckets = linkedMapOf<String, MutableList<NoteEntity>>()
    val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    for (note in unpinned) {
        val ts = note.updatedAt
        when {
            ts >= todayStart -> todayNotes.add(note)
            ts >= yesterdayStart -> yesterdayNotes.add(note)
            ts >= prev7DaysStart -> prev7DaysNotes.add(note)
            else -> {
                val monthTitle = monthFormat.format(Date(ts))
                monthBuckets.getOrPut(monthTitle) { mutableListOf() }.add(note)
            }
        }
    }

    if (todayNotes.isNotEmpty()) {
        result.add(
            NoteSectionGroup(
                key = "section_today",
                title = AppStrings.today(lang),
                isPinnedSection = false,
                notes = todayNotes
            )
        )
    }
    if (yesterdayNotes.isNotEmpty()) {
        result.add(
            NoteSectionGroup(
                key = "section_yesterday",
                title = AppStrings.yesterday(lang),
                isPinnedSection = false,
                notes = yesterdayNotes
            )
        )
    }
    if (prev7DaysNotes.isNotEmpty()) {
        result.add(
            NoteSectionGroup(
                key = "section_prev_7_days",
                title = AppStrings.previous7Days(lang),
                isPinnedSection = false,
                notes = prev7DaysNotes
            )
        )
    }
    for ((monthTitle, monthList) in monthBuckets) {
        result.add(
            NoteSectionGroup(
                key = "section_month_$monthTitle",
                title = monthTitle,
                isPinnedSection = false,
                notes = monthList
            )
        )
    }

    return result
}

private fun formatNoteRowDate(timestamp: Long, lang: AppLanguage): String {
    val nowCal = Calendar.getInstance()
    val todayStart = (nowCal.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val yesterdayStart = todayStart - 24L * 3600L * 1000L

    return when {
        timestamp >= todayStart -> SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))
        timestamp >= yesterdayStart -> AppStrings.yesterday(lang)
        else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(timestamp))
    }
}
