package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.ApiClient
import com.example.data.remote.ForgotPasswordRequest
import com.example.data.remote.ResetPasswordRequest
import com.example.data.remote.VerifyOtpRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ForgotPasswordStep {
    ENTER_EMAIL,
    ENTER_CODE,
    NEW_PASSWORD,
    SUCCESS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    initialEmail: String = "",
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var currentStep by remember { mutableStateOf(ForgotPasswordStep.ENTER_EMAIL) }

    var email by remember { mutableStateOf(initialEmail) }
    var verificationCode by remember { mutableStateOf("") }
    var resetToken by remember { mutableStateOf<String?>(null) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Resend countdown timer (starts at 60s once code is sent)
    var resendCooldown by remember { mutableIntStateOf(60) }

    LaunchedEffect(currentStep, resendCooldown) {
        if (currentStep == ForgotPasswordStep.ENTER_CODE && resendCooldown > 0) {
            delay(1000L)
            resendCooldown -= 1
        }
    }

    BackHandler {
        when (currentStep) {
            ForgotPasswordStep.ENTER_EMAIL -> onNavigateToLogin()
            ForgotPasswordStep.ENTER_CODE -> {
                errorMessage = null
                statusMessage = null
                currentStep = ForgotPasswordStep.ENTER_EMAIL
            }
            ForgotPasswordStep.NEW_PASSWORD -> {
                errorMessage = null
                statusMessage = null
                currentStep = ForgotPasswordStep.ENTER_CODE
            }
            ForgotPasswordStep.SUCCESS -> onNavigateToLogin()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentStep) {
                            ForgotPasswordStep.ENTER_EMAIL -> "Forgot Password"
                            ForgotPasswordStep.ENTER_CODE -> "Verify Email"
                            ForgotPasswordStep.NEW_PASSWORD -> "Set New Password"
                            ForgotPasswordStep.SUCCESS -> "Reset Complete"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when (currentStep) {
                                ForgotPasswordStep.ENTER_EMAIL -> onNavigateToLogin()
                                ForgotPasswordStep.ENTER_CODE -> {
                                    errorMessage = null
                                    statusMessage = null
                                    currentStep = ForgotPasswordStep.ENTER_EMAIL
                                }
                                ForgotPasswordStep.NEW_PASSWORD -> {
                                    errorMessage = null
                                    statusMessage = null
                                    currentStep = ForgotPasswordStep.ENTER_CODE
                                }
                                ForgotPasswordStep.SUCCESS -> onNavigateToLogin()
                            }
                        },
                        modifier = Modifier.testTag("forgot_password_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Progress Step Indicator
                StepProgressBar(currentStep = currentStep)

                Spacer(modifier = Modifier.height(24.dp))

                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "ForgotPasswordSteps"
                ) { step ->
                    when (step) {
                        ForgotPasswordStep.ENTER_EMAIL -> {
                            StepEnterEmail(
                                email = email,
                                onEmailChange = {
                                    email = it
                                    errorMessage = null
                                },
                                isLoading = isLoading,
                                errorMessage = errorMessage,
                                onSendCode = {
                                    val cleanEmail = email.trim()
                                    if (cleanEmail.isBlank()) {
                                        errorMessage = "Please enter your Gmail / Email address."
                                        return@StepEnterEmail
                                    }
                                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
                                        errorMessage = "Please enter a valid Gmail / Email address (e.g. user@example.com)."
                                        return@StepEnterEmail
                                    }

                                    errorMessage = null
                                    isLoading = true
                                    coroutineScope.launch {
                                        try {
                                            val response = ApiClient.apiService.sendPasswordResetCode(
                                                ForgotPasswordRequest(email = cleanEmail)
                                            )
                                            isLoading = false
                                            if (response.status) {
                                                resetToken = response.resetToken
                                                resendCooldown = 60
                                                statusMessage = response.message ?: "Verification code sent to your email."
                                                currentStep = ForgotPasswordStep.ENTER_CODE
                                            } else {
                                                errorMessage = response.message ?: "Could not send verification code. Please verify your email."
                                            }
                                        } catch (e: Throwable) {
                                            isLoading = false
                                            val raw = e.localizedMessage ?: e.message ?: ""
                                            errorMessage = if (raw.contains("network", ignoreCase = true) || raw.contains("UNAVAILABLE", ignoreCase = true)) {
                                                "Please check your internet connection and try again."
                                            } else {
                                                "Failed to connect to hosting server. Please try again."
                                            }
                                        }
                                    }
                                },
                                onCancel = onNavigateToLogin
                            )
                        }

                        ForgotPasswordStep.ENTER_CODE -> {
                            StepEnterCode(
                                email = email,
                                code = verificationCode,
                                onCodeChange = {
                                    if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                        verificationCode = it
                                        errorMessage = null
                                    }
                                },
                                resendCooldown = resendCooldown,
                                isLoading = isLoading,
                                errorMessage = errorMessage,
                                statusMessage = statusMessage,
                                onVerifyCode = {
                                    val cleanCode = verificationCode.trim()
                                    if (cleanCode.length < 6) {
                                        errorMessage = "Please enter the complete 6-digit verification code."
                                        return@StepEnterCode
                                    }

                                    errorMessage = null
                                    isLoading = true
                                    coroutineScope.launch {
                                        try {
                                            val response = ApiClient.apiService.verifyPasswordResetCode(
                                                VerifyOtpRequest(
                                                    email = email.trim(),
                                                    code = cleanCode
                                                )
                                            )
                                            isLoading = false
                                            if (response.status) {
                                                if (!response.resetToken.isNullOrBlank()) {
                                                    resetToken = response.resetToken
                                                }
                                                errorMessage = null
                                                statusMessage = response.message
                                                currentStep = ForgotPasswordStep.NEW_PASSWORD
                                            } else {
                                                errorMessage = response.message ?: "Invalid or expired verification code."
                                            }
                                        } catch (e: Throwable) {
                                            isLoading = false
                                            errorMessage = "Failed to verify code. Please check your connection."
                                        }
                                    }
                                },
                                onResendCode = {
                                    if (resendCooldown > 0) return@StepEnterCode
                                    errorMessage = null
                                    isLoading = true
                                    coroutineScope.launch {
                                        try {
                                            val response = ApiClient.apiService.sendPasswordResetCode(
                                                ForgotPasswordRequest(email = email.trim())
                                            )
                                            isLoading = false
                                            if (response.status) {
                                                resetToken = response.resetToken
                                                resendCooldown = 60
                                                statusMessage = "A new verification code has been sent to your email."
                                            } else {
                                                errorMessage = response.message ?: "Failed to resend code."
                                            }
                                        } catch (e: Throwable) {
                                            isLoading = false
                                            errorMessage = "Failed to resend code. Please check your connection."
                                        }
                                    }
                                },
                                onChangeEmail = {
                                    errorMessage = null
                                    statusMessage = null
                                    verificationCode = ""
                                    currentStep = ForgotPasswordStep.ENTER_EMAIL
                                }
                            )
                        }

                        ForgotPasswordStep.NEW_PASSWORD -> {
                            StepNewPassword(
                                newPassword = newPassword,
                                onNewPasswordChange = {
                                    newPassword = it
                                    errorMessage = null
                                },
                                confirmPassword = confirmPassword,
                                onConfirmPasswordChange = {
                                    confirmPassword = it
                                    errorMessage = null
                                },
                                newPasswordVisible = newPasswordVisible,
                                onToggleNewPasswordVisible = { newPasswordVisible = !newPasswordVisible },
                                confirmPasswordVisible = confirmPasswordVisible,
                                onToggleConfirmPasswordVisible = { confirmPasswordVisible = !confirmPasswordVisible },
                                isLoading = isLoading,
                                errorMessage = errorMessage,
                                onResetPassword = {
                                    if (newPassword.isBlank()) {
                                        errorMessage = "Please enter your new password."
                                        return@StepNewPassword
                                    }
                                    if (newPassword.length < 6) {
                                        errorMessage = "Password must be at least 6 characters long."
                                        return@StepNewPassword
                                    }
                                    if (newPassword != confirmPassword) {
                                        errorMessage = "Passwords do not match. Please re-check."
                                        return@StepNewPassword
                                    }

                                    errorMessage = null
                                    isLoading = true
                                    coroutineScope.launch {
                                        try {
                                            val response = ApiClient.apiService.resetPassword(
                                                ResetPasswordRequest(
                                                    email = email.trim(),
                                                    code = verificationCode.trim(),
                                                    resetToken = resetToken,
                                                    newPassword = newPassword
                                                )
                                            )
                                            isLoading = false
                                            if (response.status) {
                                                currentStep = ForgotPasswordStep.SUCCESS
                                            } else {
                                                errorMessage = response.message ?: "Password reset failed. Please try again."
                                            }
                                        } catch (e: Throwable) {
                                            isLoading = false
                                            errorMessage = "Failed to update password. Please check your connection."
                                        }
                                    }
                                }
                            )
                        }

                        ForgotPasswordStep.SUCCESS -> {
                            StepSuccess(
                                onNavigateToLogin = onNavigateToLogin
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun StepProgressBar(currentStep: ForgotPasswordStep) {
    val steps = listOf("Email", "Verify", "Password", "Done")
    val currentStepIndex = when (currentStep) {
        ForgotPasswordStep.ENTER_EMAIL -> 0
        ForgotPasswordStep.ENTER_CODE -> 1
        ForgotPasswordStep.NEW_PASSWORD -> 2
        ForgotPasswordStep.SUCCESS -> 3
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, title ->
            val isCompleted = index < currentStepIndex
            val isCurrent = index == currentStepIndex

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCompleted -> MaterialTheme.colorScheme.primary
                                isCurrent -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrent || isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                )
            }

            if (index < steps.size - 1) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .padding(horizontal = 4.dp)
                        .background(
                            if (index < currentStepIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
    }
}

@Composable
private fun StepEnterEmail(
    email: String,
    onEmailChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onSendCode: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MarkEmailRead,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Reset Your Password",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Enter your registered Gmail / Email address. We will send you a 6-digit verification code.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Gmail / Email Address") },
            placeholder = { Text("user@gmail.com") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Email, contentDescription = null)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onSendCode() }),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("forgot_password_email_input")
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSendCode,
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("send_verification_code_button")
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "Send Verification Code",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onCancel,
            modifier = Modifier.testTag("cancel_forgot_password_button")
        ) {
            Text(
                text = "Back to Login",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun StepEnterCode(
    email: String,
    code: String,
    onCodeChange: (String) -> Unit,
    resendCooldown: Int,
    isLoading: Boolean,
    errorMessage: String?,
    statusMessage: String?,
    onVerifyCode: () -> Unit,
    onResendCode: () -> Unit,
    onChangeEmail: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Enter Verification Code",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "We sent a 6-digit verification code to:\n$email",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            label = { Text("6-Digit Code") },
            placeholder = { Text("123456") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Key, contentDescription = null)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onVerifyCode() }),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("verification_code_input")
        )

        if (statusMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = statusMessage,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onVerifyCode,
            enabled = !isLoading && code.length == 6,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("verify_code_button")
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "Verify Code",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onChangeEmail,
                modifier = Modifier.testTag("change_email_button")
            ) {
                Text(text = "Change Email", style = MaterialTheme.typography.bodyMedium)
            }

            TextButton(
                onClick = onResendCode,
                enabled = resendCooldown == 0 && !isLoading,
                modifier = Modifier.testTag("resend_code_button")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (resendCooldown > 0) "Resend in ${resendCooldown}s" else "Resend Code",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun StepNewPassword(
    newPassword: String,
    onNewPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    newPasswordVisible: Boolean,
    onToggleNewPasswordVisible: () -> Unit,
    confirmPasswordVisible: Boolean,
    onToggleConfirmPasswordVisible: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onResetPassword: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Create New Password",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Your identity is verified. Enter and confirm your new secure password.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = newPassword,
            onValueChange = onNewPasswordChange,
            label = { Text("New Password (min 6 characters)") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Lock, contentDescription = null)
            },
            trailingIcon = {
                IconButton(onClick = onToggleNewPasswordVisible) {
                    Icon(
                        imageVector = if (newPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (newPasswordVisible) "Hide password" else "Show password"
                    )
                }
            },
            visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("new_password_input")
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = { Text("Confirm New Password") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Lock, contentDescription = null)
            },
            trailingIcon = {
                IconButton(onClick = onToggleConfirmPasswordVisible) {
                    Icon(
                        imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password"
                    )
                }
            },
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onResetPassword() }),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("confirm_new_password_input")
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onResetPassword,
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("update_password_button")
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "Update Password",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun StepSuccess(
    onNavigateToLogin: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Password Reset Successful!",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your password has been securely updated. You can now login to your ISP Billing account using your new password.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onNavigateToLogin,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("success_back_to_login_button")
            ) {
                Text(
                    text = "Go to Login",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
