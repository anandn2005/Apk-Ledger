package com.example.ui.screens

import android.content.Context
import android.content.DialogInterface
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PrimaryContainerLight
import com.example.ui.theme.PrimaryPurpleDark

@Composable
fun PasscodeLockScreen(
    onVerifyPin: (String) -> Boolean,
    isBiometricEnabled: Boolean = false,
    onBiometricSuccess: () -> Unit = {},
    title: String = "Enter PIN",
    subtitle: String = ""
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val triggerBiometricUnlock = remember(context, isBiometricEnabled, onBiometricSuccess) {
        {
            launchHardwareBiometricPrompt(
                context = context,
                onSuccess = {
                    errorMessage = null
                    onBiometricSuccess()
                },
                onError = { msg ->
                    if (msg.isNotBlank()) {
                        errorMessage = msg
                    }
                }
            )
        }
    }

    LaunchedEffect(isBiometricEnabled) {
        if (isBiometricEnabled) {
            triggerBiometricUnlock()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                shape = CircleShape,
                color = PrimaryContainerLight,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Security Lock",
                        tint = PrimaryPurpleDark,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // PIN Dots Indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 28.dp)
            ) {
                repeat(4) { index ->
                    val isFilled = index < pin.length
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFilled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Keypad Grid
            val bottomLeftKey = if (isBiometricEnabled) "BIO" else ""
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf(bottomLeftKey, "0", "DEL")
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (row in keys) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        for (key in row) {
                            if (key.isEmpty()) {
                                Spacer(modifier = Modifier.size(72.dp))
                            } else {
                                Surface(
                                    onClick = {
                                        when (key) {
                                            "BIO" -> {
                                                errorMessage = null
                                                triggerBiometricUnlock()
                                            }
                                            "DEL" -> {
                                                if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                                errorMessage = null
                                            }
                                            else -> {
                                                if (pin.length < 4) {
                                                    pin += key
                                                    errorMessage = null
                                                    if (pin.length == 4) {
                                                        val success = onVerifyPin(pin)
                                                        if (!success) {
                                                            errorMessage = "Incorrect PIN."
                                                            pin = ""
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (key == "BIO") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        when (key) {
                                            "DEL" -> {
                                                Icon(
                                                    imageVector = Icons.Default.Backspace,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            "BIO" -> {
                                                Icon(
                                                    imageVector = Icons.Default.Fingerprint,
                                                    contentDescription = "Unlock with Biometrics",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(30.dp)
                                                )
                                            }
                                            else -> {
                                                Text(
                                                    text = key,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 24.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
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

            if (isBiometricEnabled) {
                Spacer(modifier = Modifier.height(18.dp))
                OutlinedButton(
                    onClick = { triggerBiometricUnlock() },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Unlock with Fingerprint / Face ID",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                }
            }
        }
    }
}

private fun launchHardwareBiometricPrompt(
    context: Context,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        onError("Biometric unlock requires Android 9.0 or higher.")
        return
    }
    try {
        val builder = BiometricPrompt.Builder(context)
            .setTitle("Unlock LedgerMate")
            .setSubtitle("Verify your fingerprint or face to access your ledger")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
        }

        builder.setNegativeButton(
            "Use PIN",
            context.mainExecutor,
            DialogInterface.OnClickListener { _, _ -> }
        )

        val prompt = builder.build()
        val cancellationSignal = CancellationSignal()

        prompt.authenticate(
            cancellationSignal,
            context.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Biometric not recognized. Try again or enter PIN.")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    super.onAuthenticationError(errorCode, errString)
                    // Ignore user-initiated cancellation / negative button
                    if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED &&
                        errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED
                    ) {
                        onError(errString?.toString() ?: "Biometric authentication unavailable.")
                    }
                }
            }
        )
    } catch (e: Exception) {
        onError("Biometric sensor unavailable on this device. Please use your PIN.")
    }
}
