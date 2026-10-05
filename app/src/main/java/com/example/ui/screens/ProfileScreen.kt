package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.IspApplication
import com.example.R
import com.example.ui.components.PinChangeDialog
import com.example.ui.components.PinSetupDialog
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.EmeraldSuccess
import com.example.util.HostingSyncManager
import com.example.util.PinLockManager
import com.example.util.PrivacyModeManager
import kotlinx.coroutines.flow.flowOf
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
    
    // Persistent reactive states
    val isPrivacyModeActive by PrivacyModeManager.privacyModeFlow.collectAsState()
    val isPinLockEnabled by PinLockManager.pinLockEnabledFlow.collectAsState()
    val hasPinSet by PinLockManager.hasPinSetFlow.collectAsState()

    // Dialog states
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showPinChangeDialog by remember { mutableStateOf(false) }
    var showDisablePinDialog by remember { mutableStateOf(false) }
    
    val prefs = remember { context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE) }
    
    val userEmail = IspApplication.getUserEmail(context) ?: "Unknown"
    val currentUid = IspApplication.getUserId(context)
    val syncTimeKey = currentUid?.let { "last_cloud_sync_time_$it" }

    fun readLatestSyncTime(): Long {
        if (currentUid.isNullOrBlank()) return 0L
        val userSyncTime = prefs.getLong("last_cloud_sync_time_$currentUid", 0L)
        if (userSyncTime > 0L) return userSyncTime
        return prefs.getLong("last_cloud_sync_time", 0L)
    }

    var syncTimeState by remember(currentUid) { 
        mutableStateOf(readLatestSyncTime()) 
    }

    val isSyncing by HostingSyncManager.isSyncingFlow.collectAsState()

    val pendingBackups by remember(currentUid) {
        if (currentUid != null && IspApplication.isLoggedIn(context)) {
            HostingSyncManager.observePendingDirtyCount(context, currentUid)
        } else {
            flowOf(0)
        }
    }.collectAsState(initial = 0)

    LaunchedEffect(currentUid, isSyncing, pendingBackups) {
        syncTimeState = readLatestSyncTime()
    }

    DisposableEffect(prefs, currentUid) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                syncTimeKey, "last_cloud_sync_time_$currentUid", "last_cloud_sync_time" -> {
                    syncTimeState = readLatestSyncTime()
                }
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
        syncTimeState > 0 -> EmeraldSuccess
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
                title = { Text(stringResource(R.string.profile), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Account Profile Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                tonalElevation = 1.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(10.dp)
                                .fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Admin Account",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = userEmail,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!currentUid.isNullOrBlank()) {
                            Text(
                                text = "UID: ${currentUid.take(8)}...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // 2. App Security Section (PIN App Lock & Privacy Mode)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                tonalElevation = 1.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.app_security),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isPinLockEnabled) EmeraldSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = if (isPinLockEnabled) "PROTECTED" else "UNLOCKED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isPinLockEnabled) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                    // Row A: PIN Protection Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.pin_protection),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isPinLockEnabled) "App lock active with secure 4-6 digit PIN" else "Lock app on background or restart",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = isPinLockEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (PinLockManager.hasPinSet(context)) {
                                        PinLockManager.setPinLockEnabled(context, true)
                                        onShowToast("PIN Protection enabled")
                                    } else {
                                        showPinSetupDialog = true
                                    }
                                } else {
                                    showDisablePinDialog = true
                                }
                            },
                            modifier = Modifier.testTag("pin_protection_switch")
                        )
                    }

                    // Change PIN action if enabled
                    if (isPinLockEnabled || hasPinSet) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { showPinChangeDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Change Security PIN", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                    // Row B: Privacy Mode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.privacy_mode),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isPrivacyModeActive) "Masking financial amounts & phone numbers" else "Hide sensitive balances from onlookers",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = isPrivacyModeActive,
                            onCheckedChange = { checked ->
                                PrivacyModeManager.setPrivacyModeEnabled(context, checked)
                                onShowToast(if (checked) "Privacy Mode Enabled (Amounts Masked)" else "Privacy Mode Disabled")
                            },
                            modifier = Modifier.testTag("privacy_mode_switch")
                        )
                    }
                }
            }

            // 3. Cloud Sync & Backup Status Section
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                tonalElevation = 1.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Cloud Sync & Backup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                    // Sync Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = syncColor, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.sync_status), style = MaterialTheme.typography.bodyMedium)
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = syncColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = syncStatusText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = syncColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Pending Backups
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFFFBC02D), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.pending_backups_count), style = MaterialTheme.typography.bodyMedium)
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = pendingBackups.toString(),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    // Last Backup Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.last_backup_time), style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            text = lastBackupTimeText ?: stringResource(R.string.no_backup),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Logout Action
            Button(
                onClick = {
                    try {
                        onSignOut()
                    } catch (e: Exception) {
                        onShowToast(context.getString(R.string.logout_failed, e.message))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.logout), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }

        // Dialogs
        if (showPinSetupDialog) {
            PinSetupDialog(
                onDismiss = { showPinSetupDialog = false },
                onSuccess = {
                    PinLockManager.setPinLockEnabled(context, true)
                    showPinSetupDialog = false
                    onShowToast("PIN Setup Successful. App Lock is now active.")
                },
                onShowToast = onShowToast
            )
        }

        if (showPinChangeDialog) {
            PinChangeDialog(
                onDismiss = { showPinChangeDialog = false },
                onSuccess = {
                    showPinChangeDialog = false
                    onShowToast("PIN changed successfully")
                },
                onShowToast = onShowToast
            )
        }

        if (showDisablePinDialog) {
            AlertDialog(
                onDismissRequest = { showDisablePinDialog = false },
                shape = RoundedCornerShape(20.dp),
                title = { Text(stringResource(R.string.disable_pin_lock), fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to disable PIN protection? Your app will no longer require a PIN to open.") },
                confirmButton = {
                    Button(
                        onClick = {
                            PinLockManager.setPinLockEnabled(context, false)
                            showDisablePinDialog = false
                            onShowToast("PIN Lock disabled")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.yes), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showDisablePinDialog = false },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.no))
                    }
                }
            )
        }
    }
}
