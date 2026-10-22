package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppLanguage
import kotlinx.coroutines.delay

// Brand Color Palette derived from logo
private val AccentGreen = Color(0xFF379358)
private val AccentGreenDark = Color(0xFF2E7D4C)
private val AccentGreenLight = Color(0xFFEAF6ED)
private val PageBackground = Color(0xFFFAFCF9)
private val BorderGreenGray = Color(0xFFE3EFE6)
private val TextPrimary = Color(0xFF1B2E22)
private val TextSecondary = Color(0xFF8E8E93)
private val SystemRed = Color(0xFFFF3B30)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    isCheckingAuth: Boolean,
    isSigningIn: Boolean,
    onSignInWithEmail: (email: String, pass: String) -> Unit,
    onCreateAccountWithEmail: (email: String, pass: String) -> Unit,
    onContinueOffline: () -> Unit = {},
    userMessage: String? = null,
    currentLang: AppLanguage = LocalAppLanguage.current
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val emailFocusRequester = remember { FocusRequester() }

    val loginInteractionSource = remember { MutableInteractionSource() }
    val isLoginPressed by loginInteractionSource.collectIsPressedAsState()
    val loginScale by animateFloatAsState(
        targetValue = if (isLoginPressed) 0.98f else 1.0f,
        label = "loginScale"
    )

    // Auto-focus email field on load
    LaunchedEffect(Unit) {
        try {
            emailFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val isFormFilled = email.isNotBlank() && password.isNotBlank()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PageBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ============================================================
            // HEADER SECTION: LOGO & APP WORDMARK
            // ============================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // New App Logo Badge
                Icon(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "LedgerMate Logo",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(80.dp)
                )

                Text(
                    text = "LedgerMate",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = (-0.3).sp
                )

                val currentLang = com.example.ui.i18n.LocalAppLanguage.current
                Text(
                    text = com.example.ui.i18n.AppStrings.signInToLedger(currentLang),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )

                // Muted inline status indicator ("Not signed in") instead of bold red banner
                if (userMessage.isNullOrBlank() || userMessage.equals("Signed out", ignoreCase = true)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(TextSecondary.copy(alpha = 0.6f))
                        )
                        Text(
                            text = com.example.ui.i18n.AppStrings.notSignedIn(currentLang),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = TextSecondary
                        )
                    }
                }
            }

            // ============================================================
            // AUTHENTICATION FORM CARD
            // ============================================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderGreenGray),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isCheckingAuth) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.5.dp,
                                    color = AccentGreen
                                )
                                Text(
                                    text = com.example.ui.i18n.AppStrings.checkingSession(currentLang),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    } else {
                        // 1. Email Field
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = com.example.ui.i18n.AppStrings.email(currentLang),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                placeholder = {
                                    Text(
                                        text = "name@company.com",
                                        color = TextSecondary,
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp
                                    )
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = TextPrimary
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentGreen,
                                    unfocusedBorderColor = BorderGreenGray,
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 56.dp)
                                    .focusRequester(emailFocusRequester)
                                    .testTag("login_email_input")
                            )
                        }

                        // 2. Password Field
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = com.example.ui.i18n.AppStrings.password(currentLang),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                placeholder = {
                                    Text(
                                        text = "••••••••",
                                        color = TextSecondary,
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp
                                    )
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = TextPrimary
                                ),
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                            tint = if (passwordVisible) AccentGreen else TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        if (isFormFilled) {
                                            onSignInWithEmail(email.trim(), password)
                                        }
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentGreen,
                                    unfocusedBorderColor = BorderGreenGray,
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 56.dp)
                                    .testTag("login_password_input")
                            )
                        }

                        // Inline Error / Validation Message (if error occurred during submit)
                        val isTrueError = !isSigningIn &&
                            !userMessage.isNullOrBlank() &&
                            !userMessage.equals("Signed out", ignoreCase = true) &&
                            !userMessage.startsWith("Signed in", ignoreCase = true) &&
                            !userMessage.startsWith("Authenticat", ignoreCase = true) &&
                            !userMessage.startsWith("Creating account", ignoreCase = true) &&
                            !userMessage.startsWith("Account created", ignoreCase = true)

                        if (isTrueError && userMessage != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFFF2F2),
                                border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
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
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFFC62828),
                                            lineHeight = 18.sp
                                        )
                                    }

                                    // Quick one-tap switch if wrong action was chosen
                                    if (userMessage.contains("Create Account", ignoreCase = true) ||
                                        userMessage.contains("not registered", ignoreCase = true)) {
                                        Button(
                                            onClick = {
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                                onCreateAccountWithEmail(email.trim(), password)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = AccentGreen,
                                                contentColor = Color.White
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(38.dp)
                                        ) {
                                            Text(
                                                text = "Sign Up / Create Account Now",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else if (userMessage.contains("already exists", ignoreCase = true) ||
                                        userMessage.contains("Sign In", ignoreCase = true)) {
                                        Button(
                                            onClick = {
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                                onSignInWithEmail(email.trim(), password)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = AccentGreen,
                                                contentColor = Color.White
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(38.dp)
                                        ) {
                                            Text(
                                                text = "Sign In With This Email",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Action Buttons: Login and Create Account
                        if (isSigningIn) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.5.dp,
                                        color = AccentGreen
                                    )
                                    Text(
                                        text = "Authenticating...",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AccentGreen
                                    )
                                }
                            }
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                // Primary Action: Login Button
                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        onSignInWithEmail(email.trim(), password)
                                    },
                                    enabled = isFormFilled,
                                    interactionSource = loginInteractionSource,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentGreen,
                                        contentColor = Color.White,
                                        disabledContainerColor = AccentGreen.copy(alpha = 0.35f),
                                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .graphicsLayer {
                                            scaleX = loginScale
                                            scaleY = loginScale
                                        }
                                        .testTag("login_submit_button")
                                ) {
                                    Text(
                                        text = com.example.ui.i18n.AppStrings.signIn(currentLang),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Secondary Action: Create Account Button
                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        onCreateAccountWithEmail(email.trim(), password)
                                    },
                                    enabled = isFormFilled,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentGreenLight,
                                        contentColor = AccentGreen,
                                        disabledContainerColor = PageBackground,
                                        disabledContentColor = TextSecondary.copy(alpha = 0.6f)
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("login_create_account_button")
                                ) {
                                    Text(
                                        text = com.example.ui.i18n.AppStrings.createAccount(currentLang),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ============================================================
            // TERTIARY GUEST FLOW: CONTINUE WITH LOCAL STORAGE
            // ============================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Subtle horizontal divider with "or"
                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = BorderGreenGray,
                        thickness = 1.dp
                    )
                    Text(
                        text = "or",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextSecondary
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = BorderGreenGray,
                        thickness = 1.dp
                    )
                }

                // Continue with Local Storage Button
                OutlinedButton(
                    onClick = onContinueOffline,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderGreenGray),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
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
                        text = com.example.ui.i18n.AppStrings.continueOffline(currentLang),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }

                // Convenience Caption
                Text(
                    text = "No account needed — data stays on this device",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextSecondary
                )
            }
        }
    }
}
