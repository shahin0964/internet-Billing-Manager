package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.PinLockManager
import com.google.firebase.auth.FirebaseAuth

@Composable
fun PinUnlockOverlayScreen(
    onUnlocked: () -> Unit,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showRecoveryDialog by remember { mutableStateOf(false) }

    fun handleDigit(digit: String) {
        if (pinInput.length < 6) {
            val newPin = pinInput + digit
            pinInput = newPin
            errorMessage = null
            
            if (newPin.length >= 4) {
                if (PinLockManager.verifyPin(context, newPin)) {
                    errorMessage = null
                    onUnlocked()
                } else if (newPin.length == 6) {
                    errorMessage = "Incorrect PIN. Please try again."
                    pinInput = ""
                }
            }
        }
    }

    fun handleBackspace() {
        if (pinInput.isNotEmpty()) {
            pinInput = pinInput.dropLast(1)
            errorMessage = null
        }
    }

    fun handleCheckPin() {
        if (pinInput.length in 4..6) {
            if (PinLockManager.verifyPin(context, pinInput)) {
                errorMessage = null
                onUnlocked()
            } else {
                errorMessage = "Incorrect PIN. Please try again."
                pinInput = ""
            }
        } else {
            errorMessage = "PIN must be 4 to 6 digits"
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "App Lock",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "App Locked",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Enter 4-6 digit PIN to unlock",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(28.dp))

                // PIN indicator dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dotCount = if (pinInput.length > 4) pinInput.length else 4
                    for (i in 0 until dotCount) {
                        val isFilled = i < pinInput.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    1.dp,
                                    if (isFilled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    CircleShape
                                )
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Numeric Keypad
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("CLR", "0", "DEL")
                )

                for (row in keys) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (key in row) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(60.dp)
                            ) {
                                when (key) {
                                    "CLR" -> {
                                        TextButton(
                                            onClick = { pinInput = ""; errorMessage = null },
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Text(
                                                text = "Clear",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    "DEL" -> {
                                        IconButton(
                                            onClick = { handleBackspace() },
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    else -> {
                                        Surface(
                                            onClick = { handleDigit(key) },
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = key,
                                                    style = MaterialTheme.typography.titleLarge.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 24.sp
                                                    ),
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

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { showRecoveryDialog = true },
                    modifier = Modifier.testTag("forgot_pin_button")
                ) {
                    Text(
                        text = "Forgot PIN? Recover with Account",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showRecoveryDialog) {
        PinRecoveryDialog(
            onDismiss = { showRecoveryDialog = false },
            onSuccess = {
                PinLockManager.removePin(context)
                showRecoveryDialog = false
                onShowToast("PIN lock reset successfully via Account Verification")
                onUnlocked()
            }
        )
    }
}

@Composable
fun PinSetupDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(1) } // 1: enter pin, 2: confirm pin
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pinVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = if (step == 1) "Create PIN App Lock" else "Confirm Your PIN",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = if (step == 1)
                        "Enter a 4-6 digit numeric PIN to secure the app."
                    else
                        "Re-enter your PIN to confirm.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = if (step == 1) pin else confirmPin,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(6)
                        if (step == 1) pin = digits else confirmPin = digits
                        errorMessage = null
                    },
                    label = { Text(if (step == 1) "Enter 4-6 Digit PIN" else "Confirm PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { pinVisible = !pinVisible }) {
                            Icon(
                                imageVector = if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (pinVisible) "Hide PIN" else "Show PIN"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_input_field")
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (step == 1) {
                        if (!pin.matches(Regex("^[0-9]{4,6}$"))) {
                            errorMessage = "PIN must be between 4 and 6 digits"
                        } else {
                            step = 2
                            errorMessage = null
                        }
                    } else {
                        if (confirmPin != pin) {
                            errorMessage = "PINs do not match. Try again."
                            confirmPin = ""
                        } else {
                            val saved = PinLockManager.savePin(context, pin)
                            if (saved) {
                                onShowToast("PIN Lock enabled successfully")
                                onSuccess()
                            } else {
                                errorMessage = "Failed to save PIN securely"
                            }
                        }
                    }
                },
                modifier = Modifier.testTag("pin_setup_confirm_button")
            ) {
                Text(if (step == 1) "Next" else "Enable Lock")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PinChangeDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(1) } // 1: current pin, 2: new pin, 3: confirm new pin
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pinVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = when (step) {
                    1 -> "Enter Current PIN"
                    2 -> "Enter New PIN"
                    else -> "Confirm New PIN"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = when (step) {
                        1 -> "Enter your existing PIN to proceed."
                        2 -> "Enter a new 4-6 digit numeric PIN."
                        else -> "Re-enter your new PIN to confirm."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                val currentValue = when (step) {
                    1 -> currentPin
                    2 -> newPin
                    else -> confirmNewPin
                }

                OutlinedTextField(
                    value = currentValue,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(6)
                        when (step) {
                            1 -> currentPin = digits
                            2 -> newPin = digits
                            else -> confirmNewPin = digits
                        }
                        errorMessage = null
                    },
                    label = {
                        Text(
                            when (step) {
                                1 -> "Current PIN"
                                2 -> "New PIN (4-6 digits)"
                                else -> "Confirm New PIN"
                            }
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { pinVisible = !pinVisible }) {
                            Icon(
                                imageVector = if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (pinVisible) "Hide PIN" else "Show PIN"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (step) {
                        1 -> {
                            if (PinLockManager.verifyPin(context, currentPin)) {
                                step = 2
                                errorMessage = null
                            } else {
                                errorMessage = "Incorrect current PIN"
                            }
                        }
                        2 -> {
                            if (!newPin.matches(Regex("^[0-9]{4,6}$"))) {
                                errorMessage = "New PIN must be 4 to 6 digits"
                            } else {
                                step = 3
                                errorMessage = null
                            }
                        }
                        3 -> {
                            if (confirmNewPin != newPin) {
                                errorMessage = "PINs do not match"
                                confirmNewPin = ""
                            } else {
                                val saved = PinLockManager.savePin(context, newPin)
                                if (saved) {
                                    onShowToast("PIN changed successfully")
                                    onSuccess()
                                } else {
                                    errorMessage = "Failed to update PIN"
                                }
                            }
                        }
                    }
                }
            ) {
                Text(if (step < 3) "Next" else "Change PIN")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PinRecoveryDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var emailInput by remember {
        mutableStateOf(
            try {
                com.example.IspApplication.ensureFirebaseInitialized(context)
                FirebaseAuth.getInstance().currentUser?.email ?: ""
            } catch (e: Throwable) {
                ""
            }
        )
    }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val currentUser = remember {
        try {
            com.example.IspApplication.ensureFirebaseInitialized(context)
            FirebaseAuth.getInstance().currentUser
        } catch (e: Throwable) {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Account Verification",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = if (currentUser != null)
                        "Verify your account password (${currentUser.email}) to reset PIN lock."
                    else
                        "Sign in with your account credentials to verify ownership and reset PIN lock.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = {
                        if (currentUser == null) emailInput = it
                        errorMessage = null
                    },
                    enabled = currentUser == null,
                    label = { Text("Account Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = {
                        passwordInput = it
                        errorMessage = null
                    },
                    label = { Text("Account Password") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val email = emailInput.trim()
                    if (email.isBlank() || !email.contains("@")) {
                        errorMessage = "Please enter a valid account email"
                        return@Button
                    }
                    if (passwordInput.isBlank()) {
                        errorMessage = "Please enter account password"
                        return@Button
                    }

                    isLoading = true
                    errorMessage = null
                    try {
                        com.example.IspApplication.ensureFirebaseInitialized(context)
                        val auth = FirebaseAuth.getInstance()
                        auth.signInWithEmailAndPassword(email, passwordInput)
                            .addOnSuccessListener {
                                isLoading = false
                                onSuccess()
                            }
                            .addOnFailureListener { e ->
                                isLoading = false
                                errorMessage = e.localizedMessage ?: "Account verification failed. Incorrect password."
                            }
                    } catch (e: Throwable) {
                        isLoading = false
                        errorMessage = "Authentication service unavailable."
                    }
                },
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Verify & Reset PIN")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}
