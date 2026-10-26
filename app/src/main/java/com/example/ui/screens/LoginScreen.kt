package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.ui.components.GoogleLogoIcon
import com.example.ui.components.pressScale
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.AppStrings
import com.example.ui.i18n.LocalAppLanguage
import kotlinx.coroutines.delay

private val AccentGreen = Color(0xFF379358)
private val AccentGreenLight = Color(0xFFEAF6ED)
private val PageBackground = Color(0xFFFAFCF9)
private val BorderGreenGray = Color(0xFFE3EFE6)
private val TextPrimary = Color(0xFF1B2E22)
private val TextSecondary = Color(0xFF64748B)
private val SystemRed = Color(0xFFFF3B30)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    isCheckingAuth: Boolean,
    isSigningIn: Boolean,
    onSignInWithGoogle: (android.content.Context) -> Unit,
    onSignInWithEmail: (email: String, pass: String) -> Unit,
    onSignUpWithEmail: (email: String, pass: String, name: String) -> Unit,
    onSendPasswordReset: (email: String) -> Unit = {},
    onContinueOffline: () -> Unit = {},
    userMessage: String? = null,
    currentLang: AppLanguage = LocalAppLanguage.current
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    // Auth Mode: "SIGN_IN" vs "SIGN_UP"
    var authMode by remember { mutableStateOf("SIGN_IN") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    // Staggered entrance choreography
    var showHero by remember { mutableStateOf(false) }
    var showCard by remember { mutableStateOf(false) }
    var showFooter by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showHero = true
        delay(60)
        showCard = true
        delay(70)
        showFooter = true
    }

    // Ambient radial glow behind hero
    val infiniteTransition = rememberInfiniteTransition(label = "heroAura")
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraScale"
    )
    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraAlpha"
    )

    val googleBtnInteraction = remember { MutableInteractionSource() }
    val isGooglePressed by googleBtnInteraction.collectIsPressedAsState()
    val googleBtnScale by animateFloatAsState(
        targetValue = if (isGooglePressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = 0.60f, stiffness = 500f),
        label = "googleBtnScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AccentGreen.copy(alpha = auraAlpha * 0.5f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.18f),
                        radius = size.minDimension * 0.65f * auraScale
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.18f),
                    radius = size.minDimension * 0.65f * auraScale
                )
            },
        color = PageBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ============================================================
            // 1. HERO SECTION: BRAND HEADER (NO UNNECESSARY FEATURE TAGS)
            // ============================================================
            AnimatedVisibility(
                visible = showHero,
                enter = fadeIn(tween(240)) + slideInVertically(
                    initialOffsetY = { -it / 4 },
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 360f)
                ) + scaleIn(
                    initialScale = 0.92f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 360f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(84.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(78.dp)
                                .graphicsLayer {
                                    scaleX = auraScale
                                    scaleY = auraScale
                                }
                                .clip(CircleShape)
                                .background(AccentGreen.copy(alpha = 0.10f))
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = "LedgerMate Logo",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(68.dp)
                        )
                    }

                    Text(
                        text = "LedgerMate",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp
                    )

                    Text(
                        text = AppStrings.signInToLedger(currentLang),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ============================================================
            // 2. AUTHENTICATION CARD: GOOGLE SIGN-IN + EMAIL & PASSWORD
            // ============================================================
            AnimatedVisibility(
                visible = showCard,
                enter = fadeIn(tween(260)) + slideInVertically(
                    initialOffsetY = { it / 4 },
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 360f)
                )
            ) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BorderGreenGray),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1-Tap Google Sign-In Button
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSignInWithGoogle(context)
                            },
                            enabled = !isSigningIn && !isCheckingAuth,
                            interactionSource = googleBtnInteraction,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGreen,
                                contentColor = Color.White,
                                disabledContainerColor = AccentGreen.copy(alpha = 0.55f),
                                disabledContentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 2.dp,
                                pressedElevation = 1.dp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .graphicsLayer {
                                    scaleX = googleBtnScale
                                    scaleY = googleBtnScale
                                }
                                .testTag("google_sign_in_button")
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Connecting...",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        GoogleLogoIcon(modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Continue with Google",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.2.sp
                                )
                            }
                        }

                        // Subtle Divider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = BorderGreenGray,
                                thickness = 1.dp
                            )
                            Text(
                                text = "or with email",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = BorderGreenGray,
                                thickness = 1.dp
                            )
                        }

                        // Mode Switcher: Sign In vs Create Account
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF1F5F2))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val isSignInMode = authMode == "SIGN_IN"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSignInMode) Color.White else Color.Transparent)
                                    .pressScale(targetScale = 0.96f)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        authMode = "SIGN_IN"
                                    }
                                    .testTag("tab_switch_sign_in"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Sign In",
                                    fontSize = 13.sp,
                                    fontWeight = if (isSignInMode) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSignInMode) AccentGreen else TextSecondary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (!isSignInMode) Color.White else Color.Transparent)
                                    .pressScale(targetScale = 0.96f)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        authMode = "SIGN_UP"
                                    }
                                    .testTag("tab_switch_sign_up"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Create Account",
                                    fontSize = 13.sp,
                                    fontWeight = if (!isSignInMode) FontWeight.Bold else FontWeight.Medium,
                                    color = if (!isSignInMode) AccentGreen else TextSecondary
                                )
                            }
                        }

                        // Optional Name field (Only shown for Sign Up)
                        AnimatedVisibility(
                            visible = authMode == "SIGN_UP",
                            enter = expandVertically(spring(dampingRatio = 0.8f)) + fadeIn(tween(180)),
                            exit = shrinkVertically(spring(dampingRatio = 0.8f)) + fadeOut(tween(140))
                        ) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Your Name (optional)") },
                                placeholder = { Text("e.g. John Doe") },
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_name_input")
                            )
                        }

                        // Email Field
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("Email Address") },
                            placeholder = { Text("name@example.com") },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null, tint = TextSecondary)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_email_input")
                        )

                        // Password Field
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Password") },
                            placeholder = { Text("Minimum 6 characters") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = TextSecondary)
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                        tint = TextSecondary
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    if (authMode == "SIGN_IN") {
                                        onSignInWithEmail(emailInput, passwordInput)
                                    } else {
                                        onSignUpWithEmail(emailInput, passwordInput, nameInput)
                                    }
                                }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_password_input")
                        )

                        // Forgot Password Link (Only in Sign In mode)
                        if (authMode == "SIGN_IN") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "Forgot Password?",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentGreen,
                                    modifier = Modifier
                                        .clickable { showForgotPasswordDialog = true }
                                        .padding(vertical = 2.dp)
                                        .testTag("login_forgot_password_link")
                                )
                            }
                        }

                        // Inline Error / Diagnostic Banner
                        val isTrueError = !isSigningIn &&
                            !userMessage.isNullOrBlank() &&
                            !userMessage.equals("Signed out", ignoreCase = true) &&
                            !userMessage.startsWith("Signed in", ignoreCase = true) &&
                            !userMessage.startsWith("Password reset link sent", ignoreCase = true)

                        if (isTrueError && userMessage != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFFF2F2),
                                border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = SystemRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = userMessage,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFC62828),
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        } else if (!userMessage.isNullOrBlank() && userMessage.startsWith("Password reset link sent", ignoreCase = true)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AccentGreenLight,
                                border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = userMessage,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = AccentGreen,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        // Submit Button for Email / Password
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (authMode == "SIGN_IN") {
                                    onSignInWithEmail(emailInput, passwordInput)
                                } else {
                                    onSignUpWithEmail(emailInput, passwordInput, nameInput)
                                }
                            },
                            enabled = !isSigningIn && !isCheckingAuth && emailInput.isNotBlank() && passwordInput.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TextPrimary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .pressScale(targetScale = 0.96f)
                                .testTag("login_submit_button")
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (authMode == "SIGN_IN") "Signing in..." else "Creating account...",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = if (authMode == "SIGN_IN") "Sign In with Email" else "Create Account",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ============================================================
            // 3. OFFLINE / GUEST CONTINUATION FLOW
            // ============================================================
            AnimatedVisibility(
                visible = showFooter,
                enter = fadeIn(tween(280)) + slideInVertically(
                    initialOffsetY = { it / 3 },
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 360f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onContinueOffline()
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, BorderGreenGray),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .pressScale(targetScale = 0.96f)
                            .testTag("login_continue_offline_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = AccentGreen
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = AppStrings.continueOffline(currentLang),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }

                    Text(
                        text = "Data stays on this device — you can sign in anytime in Settings",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Password Reset Dialog
    if (showForgotPasswordDialog) {
        var resetEmail by remember { mutableStateOf(emailInput) }
        Dialog(onDismissRequest = { showForgotPasswordDialog = false }) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Reset Password",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Enter your registered email address and we'll send you a password reset link.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showForgotPasswordDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (resetEmail.isNotBlank()) {
                                    onSendPasswordReset(resetEmail.trim())
                                    showForgotPasswordDialog = false
                                }
                            },
                            enabled = resetEmail.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Send Link")
                        }
                    }
                }
            }
        }
    }
}
