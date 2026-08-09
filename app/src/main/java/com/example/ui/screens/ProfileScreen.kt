package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.PinSetupDialog
import com.example.util.PinLockManager
import com.example.IspApplication
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isPinLockEnabled by remember { mutableStateOf(PinLockManager.isPinLockEnabled(context)) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showDisablePinDialog by remember { mutableStateOf(false) }
    
    val prefs = context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
    var privacyModeEnabled by remember { mutableStateOf(prefs.getBoolean("privacy_mode", false)) }
    
    val authUser = try {
        IspApplication.ensureFirebaseInitialized(context)
        FirebaseAuth.getInstance().currentUser
    } catch (e: Throwable) { null }

    val userEmail = authUser?.email ?: "Unknown"

    var showPasswordChangeDialog by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    var syncTimeState by remember { mutableStateOf(prefs.getLong("last_cloud_sync_time", 0L)) }
    var pendingBackups by remember { mutableStateOf(prefs.getInt("pending_sync_count", 0)) }
    var isSyncing by remember { mutableStateOf(prefs.getBoolean("is_syncing", false)) }
    
    androidx.compose.runtime.DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            when (key) {
                "privacy_mode" -> privacyModeEnabled = sharedPreferences.getBoolean("privacy_mode", false)
                "last_cloud_sync_time" -> syncTimeState = sharedPreferences.getLong("last_cloud_sync_time", 0L)
                "pending_sync_count" -> pendingBackups = sharedPreferences.getInt("pending_sync_count", 0)
                "is_syncing" -> isSyncing = sharedPreferences.getBoolean("is_syncing", false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    
    val syncStatusText = when {
        isSyncing -> stringResource(R.string.sync_in_progress)
        pendingBackups > 0 -> stringResource(R.string.sync_pending)
        syncTimeState > 0 -> stringResource(R.string.synced)
        else -> stringResource(R.string.not_synced)
    }
    
    val syncColor = when {
        isSyncing -> Color(0xFF2196F3)
        pendingBackups > 0 -> Color(0xFFFFA000)
        syncTimeState > 0 -> Color(0xFF00C853)
        else -> Color.Gray
    }

    val lastBackupTimeText = remember(syncTimeState) {
        if (syncTimeState > 0) {
            val format = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
            format.format(Date(syncTimeState))
        } else {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Account Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = userEmail,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    TextButton(
                        onClick = { showPasswordChangeDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                    ) {
                        Text(stringResource(R.string.change_password), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                }
            }

            // Sync Status
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = syncColor, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.sync_status), fontSize = 14.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = syncColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(syncStatusText, color = syncColor, fontSize = 13.sp)
                    }
                }
            }

            // Pending Backups
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFFFBC02D), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.pending_backups_count), fontSize = 14.sp)
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = pendingBackups.toString(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Last Backup Time
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.last_backup_time), fontSize = 14.sp)
                    }
                    Text(lastBackupTimeText ?: stringResource(R.string.no_backup), fontSize = 13.sp)
                }
            }

            // App Security
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Text(stringResource(R.string.app_security), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.pin_protection), fontSize = 14.sp)
                        }
                        Switch(
                            checked = isPinLockEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (PinLockManager.hasPinSet(context)) {
                                        PinLockManager.setPinLockEnabled(context, true)
                                        isPinLockEnabled = true
                                        onShowToast(context.getString(R.string.pin_protection_enabled_toast))
                                    } else {
                                        showPinSetupDialog = true
                                    }
                                } else {
                                    showDisablePinDialog = true
                                }
                            }
                        )
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.privacy_mode), fontSize = 14.sp)
                        }
                        Switch(
                            checked = privacyModeEnabled,
                            onCheckedChange = { checked ->
                                prefs.edit().putBoolean("privacy_mode", checked).apply()
                                privacyModeEnabled = checked
                                onShowToast(if (checked) context.getString(R.string.privacy_mode_enabled_toast) else context.getString(R.string.privacy_mode_disabled_toast))
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Logout Button
            Button(
                onClick = {
                    try {
                        FirebaseAuth.getInstance().signOut()
                        onSignOut()
                    } catch (e: Exception) {
                        onShowToast(context.getString(R.string.logout_failed, e.message))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(stringResource(R.string.logout), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (showPasswordChangeDialog) {
            AlertDialog(
                onDismissRequest = { showPasswordChangeDialog = false },
                title = { Text(stringResource(R.string.change_password)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it },
                            label = { Text(stringResource(R.string.current_password)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text(stringResource(R.string.new_password)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (currentPassword.isNotEmpty() && newPassword.isNotEmpty() && authUser != null) {
                                isLoading = true
                                val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(userEmail, currentPassword)
                                authUser.reauthenticate(credential).addOnCompleteListener { reauthTask ->
                                    if (reauthTask.isSuccessful) {
                                        authUser.updatePassword(newPassword).addOnCompleteListener { updateTask ->
                                            isLoading = false
                                            if (updateTask.isSuccessful) {
                                                onShowToast(context.getString(R.string.password_changed_success))
                                                showPasswordChangeDialog = false
                                            } else {
                                                onShowToast(context.getString(R.string.password_change_failed, updateTask.exception?.message))
                                            }
                                        }
                                    } else {
                                        isLoading = false
                                        onShowToast(context.getString(R.string.incorrect_current_password, reauthTask.exception?.message))
                                    }
                                }
                            } else {
                                onShowToast(context.getString(R.string.fill_all_fields))
                            }
                        },
                        enabled = !isLoading
                    ) {
                        Text(if (isLoading) stringResource(R.string.please_wait) else stringResource(R.string.change))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPasswordChangeDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showPinSetupDialog) {
            PinSetupDialog(
                onDismiss = { showPinSetupDialog = false },
                onSuccess = {
                    PinLockManager.setPinLockEnabled(context, true)
                    isPinLockEnabled = true
                    showPinSetupDialog = false
                    onShowToast(context.getString(R.string.pin_setup_success))
                },
                onShowToast = onShowToast
            )
        }

        if (showDisablePinDialog) {
            AlertDialog(
                onDismissRequest = { showDisablePinDialog = false },
                title = { Text(stringResource(R.string.disable_pin_lock)) },
                text = { Text(stringResource(R.string.disable_pin_lock_confirm)) },
                confirmButton = {
                    Button(
                        onClick = {
                            PinLockManager.setPinLockEnabled(context, false)
                            isPinLockEnabled = false
                            showDisablePinDialog = false
                            onShowToast(context.getString(R.string.pin_lock_disabled))
                        }
                    ) {
                        Text(stringResource(R.string.yes))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDisablePinDialog = false }) {
                        Text(stringResource(R.string.no))
                    }
                }
            )
        }
    }
}
