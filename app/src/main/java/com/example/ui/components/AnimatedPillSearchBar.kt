package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import com.example.ui.theme.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun AnimatedPillSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholderSequence: List<String> = listOf(
        "Search items...",
        "Search Pulses",
        "Search Rice & Grains",
        "Search Dry Fruit",
        "Search Oils & Ghee"
    ),
    testTag: String = "animated_search_bar",
    enableVoiceSearch: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val voiceLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                onQueryChange(spoken.trim())
            }
        }
    }

    // Synchronous local TextFieldValue to strictly preserve cursor and character position
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = query, selection = TextRange(query.length)))
    }

    // Keep internal TextFieldValue in sync if external query changes (e.g. cleared or reset)
    LaunchedEffect(query) {
        if (query != textFieldValue.text) {
            textFieldValue = TextFieldValue(
                text = query,
                selection = TextRange(query.length)
            )
        }
    }

    // Placeholder Cycling Animation (3.5s interval)
    LaunchedEffect(Unit) {
        while (true) {
            delay(3500L)
            currentIndex = (currentIndex + 1) % placeholderSequence.size
        }
    }

    val isDark = isAppInDarkTheme()

    val containerColor = if (isFocused) {
        if (isDark) DarkSurface else Color.White
    } else {
        if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9)
    }

    val borderColor = if (isFocused) {
        Color(0xFF379358)
    } else {
        if (isDark) DarkBorder else Color(0xFFE3EFE6)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Expanding Search Pill Surface with prominent pill container
        Surface(
            modifier = Modifier
                .weight(1f)
                .shadow(
                    elevation = if (isFocused) 3.dp else 0.5.dp,
                    shape = CircleShape,
                    spotColor = if (isFocused) Color(0xFF379358).copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.05f)
                )
                .border(
                    width = if (isFocused) 1.5.dp else 1.dp,
                    color = borderColor,
                    shape = CircleShape
                )
                .clip(CircleShape)
                .testTag(testTag),
            color = containerColor,
            shape = CircleShape
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (isFocused) Color(0xFF379358) else Color(0xFF8E8E93),
                    modifier = Modifier.size(19.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Animated Floating Placeholder
                    if (textFieldValue.text.isEmpty()) {
                        AnimatedContent(
                            targetState = placeholderSequence[currentIndex],
                            transitionSpec = {
                                (slideInVertically(animationSpec = tween(350)) { height -> height / 2 } +
                                        fadeIn(animationSpec = tween(350)))
                                    .togetherWith(
                                        slideOutVertically(animationSpec = tween(350)) { height -> -height / 2 } +
                                                fadeOut(animationSpec = tween(350))
                                    )
                            },
                            label = "search_placeholder_transition"
                        ) { animatedText ->
                            Text(
                                text = animatedText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF8E8E93),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Core Basic Text Field
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            textFieldValue = newValue
                            if (newValue.text != query) {
                                onQueryChange(newValue.text)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("${testTag}_input"),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        interactionSource = interactionSource,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                    )
                }

                if (textFieldValue.text.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            textFieldValue = TextFieldValue("")
                            onQueryChange("")
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else if (trailingIcon != null) {
                    trailingIcon()
                } else if (enableVoiceSearch) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            try {
                                val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak to search...")
                                }
                                voiceLauncher.launch(intent)
                            } catch (_: Exception) {
                                android.widget.Toast.makeText(context, "Voice search unavailable on this device", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("${testTag}_voice_btn")
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Mic,
                            contentDescription = "Voice search",
                            tint = if (isFocused) Color(0xFF379358) else Color(0xFF8E8E93),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }

        // iOS-Style Animated "Cancel" Button sliding in smoothly when focused
        AnimatedVisibility(
            visible = isFocused || textFieldValue.text.isNotEmpty(),
            enter = slideInHorizontally(
                initialOffsetX = { it / 2 },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(animationSpec = tween(180)) + expandHorizontally(),
            exit = slideOutHorizontally(
                targetOffsetX = { it / 2 },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeOut(animationSpec = tween(150)) + shrinkHorizontally()
        ) {
            Text(
                text = "Cancel",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        textFieldValue = TextFieldValue("")
                        onQueryChange("")
                        focusManager.clearFocus()
                    }
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                    .testTag("search_bar_cancel_btn")
            )
        }
    }
}
