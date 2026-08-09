package com.example.ui.screens

import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale

import com.example.ui.components.formatAmount
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Message
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.shape.CircleShape
import com.example.ui.components.AppUpdateDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BusinessSettingsEntity
import com.example.data.model.IspPackageEntity
import com.example.ui.components.SectionHeader

@Composable
fun MoreScreen(

    settings: BusinessSettingsEntity,
    packages: List<IspPackageEntity>,
    onUpdateSettings: (BusinessSettingsEntity) -> Unit,
    onAddPackageClick: () -> Unit,
    onEditPackageClick: (IspPackageEntity) -> Unit,
    onExportBackup: ((String) -> Unit) -> Unit,
    onShowToast: (String) -> Unit,
    isGuestMode: Boolean = false,
    onOpenExpenseManagement: () -> Unit = {},
    onOpenBackupAndRestore: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenLogin: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current

    var ispName by remember(settings) { mutableStateOf(settings.ispName) }
    var hotline by remember(settings) { mutableStateOf(settings.hotline) }
    var address by remember(settings) { mutableStateOf(settings.address) }
    var currencySymbol by remember(settings) { mutableStateOf(settings.currencySymbol) }
    var networkStatus by remember(settings) { mutableStateOf(settings.networkStatus) }
    var themeMode by remember(settings) { mutableStateOf(settings.themeMode) }
    var logoUri by remember(settings) { mutableStateOf(settings.logoUri) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }
    var showAccountScreen by remember { mutableStateOf(false) }

    if (showAccountScreen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showAccountScreen = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            ProfileScreen(
                onBack = { showAccountScreen = false },
                onSignOut = {
                    showAccountScreen = false
                    onSignOut()
                },
                onShowToast = onShowToast
            )
        }
    }
    var isFingerprintLockEnabled by remember { mutableStateOf(false) }
    var isPinLockEnabled by remember { mutableStateOf(com.example.util.PinLockManager.isPinLockEnabled(context)) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showPinChangeDialog by remember { mutableStateOf(false) }
    var authUser by remember {
        mutableStateOf(
            try {
                com.example.IspApplication.ensureFirebaseInitialized(context)
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            } catch (e: Throwable) {
                null
            }
        )
    }

    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                logoUri = uri.toString()
            } catch (e: Exception) {
                // Ignore
                logoUri = uri.toString()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Profile Option
        if (!isGuestMode) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAccountScreen = true },
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 6.dp,
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                        Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        Spacer(modifier = Modifier.width(16.dp))
                        SectionHeader(
                            title = "Profile",
                            subtitle = "Account, Security & Backup"
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Tap to open",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    }
                }
            }
        }

        // ISP Business Settings Card
        item {
            var showBusinessInfoDialog by remember { mutableStateOf(false) }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showBusinessInfoDialog = true },
                shape = RoundedCornerShape(18.dp),
shadowElevation = 6.dp,
tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        SectionHeader(
                        title = androidx.compose.ui.res.stringResource(com.example.R.string.isp_business_info),
                        subtitle = androidx.compose.ui.res.stringResource(com.example.R.string.configure_noc_desc)
                    )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Tap to open",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showBusinessInfoDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showBusinessInfoDialog = false },
                    title = {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.isp_business_info),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Logo Management
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.company_logo),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (logoUri != null) {
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        coil.compose.AsyncImage(
                                            model = logoUri,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp).clip(androidx.compose.foundation.shape.CircleShape),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    }
                                } else {
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(imageVector = Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Column {
                                    Button(
                                        onClick = { imagePickerLauncher.launch(arrayOf("image/*")) },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(if (logoUri == null) androidx.compose.ui.res.stringResource(com.example.R.string.select_logo) else androidx.compose.ui.res.stringResource(com.example.R.string.change_logo))
                                    }
                                    if (logoUri != null) {
                                        androidx.compose.material3.TextButton(
                                            onClick = { logoUri = null }
                                        ) {
                                            Text(androidx.compose.ui.res.stringResource(com.example.R.string.remove_logo), color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            OutlinedTextField(
                                value = ispName,
                                onValueChange = { ispName = it },
                                label = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.isp_name_brand)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = hotline,
                                    onValueChange = { hotline = it },
                                    label = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.support_hotline)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = currencySymbol,
                                    onValueChange = { currencySymbol = it },
                                    label = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.currency_symbol)) },
                                    singleLine = true,
                                    modifier = Modifier.width(90.dp)
                                )
                            }

                            OutlinedTextField(
                                value = address,
                                onValueChange = { address = it },
                                label = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.office_address)) },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.network_status),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(androidx.compose.ui.res.stringResource(com.example.R.string.operational), androidx.compose.ui.res.stringResource(com.example.R.string.maintenance), androidx.compose.ui.res.stringResource(com.example.R.string.degraded)).forEach { status ->
                                    FilterChip(
                                        selected = (networkStatus == status),
                                        onClick = { networkStatus = status },
                                        label = { Text(status) }
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val updated = settings.copy(
                                    ispName = ispName.trim(),
                                    hotline = hotline.trim(),
                                    address = address.trim(),
                                    currencySymbol = currencySymbol.trim(),
                                    networkStatus = networkStatus,
                                    themeMode = themeMode,
                                    logoUri = logoUri
                                )
                                onUpdateSettings(updated)
                                showBusinessInfoDialog = false
                            }
                        ) {
                            Text(androidx.compose.ui.res.stringResource(com.example.R.string.save_business_info))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBusinessInfoDialog = false }) {
                            Text(androidx.compose.ui.res.stringResource(com.example.R.string.cancel))
                        }
                    }
                )
            }
        }

        // Bulk SMS Template Configuration Card
        item {
            var showSmsTemplateDialog by remember { mutableStateOf(false) }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSmsTemplateDialog = true },
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 6.dp,
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Message,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        SectionHeader(
                            title = "Bulk SMS Template",
                            subtitle = "বাল্ক এসএমএস মেসেজ টেমপ্লেট কাস্টমাইজ করুন"
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Tap to open",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showSmsTemplateDialog) {
                BulkSmsTemplateDialog(
                    context = context,
                    ispName = settings.ispName,
                    currencySymbol = settings.currencySymbol,
                    onDismiss = { showSmsTemplateDialog = false },
                    onShowToast = { onShowToast(it) }
                )
            }
        }

        // Speed Packages Management
        item {
            var showPackagesDialog by remember { mutableStateOf(false) }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPackagesDialog = true },
                shape = RoundedCornerShape(18.dp),
shadowElevation = 6.dp,
tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Router,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        SectionHeader(
                        title = androidx.compose.ui.res.stringResource(com.example.R.string.speed_packages),
                        
                    )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Tap to open",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showPackagesDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showPackagesDialog = false },
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.speed_packages),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Button(
                                onClick = {
                                    showPackagesDialog = false
                                    onAddPackageClick()
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(androidx.compose.ui.res.stringResource(com.example.R.string.add_package_btn), fontSize = 12.sp)
                            }
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (packages.isEmpty()) {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.example.R.string.no_packages_created),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                packages.forEach { pkg ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
shadowElevation = 3.dp,
tonalElevation = 2.dp,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(12.dp)
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = pkg.name,
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${pkg.speedMbps} Mbps • ${settings.currencySymbol}${pkg.monthlyPrice.formatAmount()}/mo",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            TextButton(onClick = {
                                                showPackagesDialog = false
                                                onEditPackageClick(pkg)
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.edit),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(androidx.compose.ui.res.stringResource(com.example.R.string.edit), fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showPackagesDialog = false }) {
                            Text(androidx.compose.ui.res.stringResource(com.example.R.string.cancel))
                        }
                    }
                )
            }
        }


        // Advanced Features Card
        item {
            var showAdvancedFeaturesScreen by remember { mutableStateOf(false) }

            if (showAdvancedFeaturesScreen) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showAdvancedFeaturesScreen = false },
                    properties = androidx.compose.ui.window.DialogProperties(
                        usePlatformDefaultWidth = false
                    )
                ) {
                    AdvancedFeaturesScreen(
                        onBackClick = { showAdvancedFeaturesScreen = false },
                        onOpenExpenseManagement = {
                            showAdvancedFeaturesScreen = false
                            onOpenExpenseManagement()
                        }
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvancedFeaturesScreen = true },
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 6.dp,
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        SectionHeader(
                        title = androidx.compose.ui.res.stringResource(com.example.R.string.advanced_features)
                    )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Tap to open",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Language Preference
        item {
            var showLanguageDialog by remember { mutableStateOf(false) }
            val sharedPrefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            var currentLang by remember { androidx.compose.runtime.mutableStateOf(sharedPrefs.getString("app_lang", "en") ?: "en") }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLanguageDialog = true },
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 6.dp,
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        SectionHeader(
                        title = androidx.compose.ui.res.stringResource(com.example.R.string.language),
                        subtitle = if (currentLang == "bn") "বাংলা" else "English"
                    )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Tap to open",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showLanguageDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showLanguageDialog = false },
                    title = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.language)) },
                    text = {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        sharedPrefs.edit().putString("app_lang", "en").apply()
                                        currentLang = "en"
                                        showLanguageDialog = false
                                        (context as? android.app.Activity)?.recreate()
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentLang == "en",
                                    onClick = {
                                        sharedPrefs.edit().putString("app_lang", "en").apply()
                                        currentLang = "en"
                                        showLanguageDialog = false
                                        (context as? android.app.Activity)?.recreate()
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("English")
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        sharedPrefs.edit().putString("app_lang", "bn").apply()
                                        currentLang = "bn"
                                        showLanguageDialog = false
                                        (context as? android.app.Activity)?.recreate()
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentLang == "bn",
                                    onClick = {
                                        sharedPrefs.edit().putString("app_lang", "bn").apply()
                                        currentLang = "bn"
                                        showLanguageDialog = false
                                        (context as? android.app.Activity)?.recreate()
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("বাংলা")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showLanguageDialog = false }) {
                            Text(androidx.compose.ui.res.stringResource(com.example.R.string.cancel))
                        }
                    }
                )
            }
        }
        
        // Collapsible Theme Preference Card
        item {
            var showThemeBottomSheet by remember { mutableStateOf(false) }
            val currentThemeItem = com.example.ui.theme.getThemeItem(themeMode)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showThemeBottomSheet = true },
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 6.dp,
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.theme_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentThemeItem.getLocalizedName(context),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open theme selector",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showThemeBottomSheet) {
                ThemeSelectionBottomSheet(
                    currentThemeKey = themeMode,
                    onThemeSelected = { newThemeKey ->
                        themeMode = newThemeKey
                        onUpdateSettings(settings.copy(themeMode = newThemeKey))
                        showThemeBottomSheet = false
                    },
                    onDismissRequest = { showThemeBottomSheet = false }
                )
            }
        }

        // Backup & Restore Option (Directly above App Update)
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenBackupAndRestore() },
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 6.dp,
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        SectionHeader(
                        title = androidx.compose.ui.res.stringResource(com.example.R.string.backup_and_restore),
                        subtitle = androidx.compose.ui.res.stringResource(com.example.R.string.backup_restore_subtitle)
                    )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Tap to open",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // App Update Option
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showUpdateDialog = true },
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 6.dp,
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        SectionHeader(
                        title = androidx.compose.ui.res.stringResource(com.example.R.string.app_update)
                    )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Tap to open",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // App Information (ONLY for authenticated users)
        if (!isGuestMode) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAbout() },
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 6.dp,
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                        Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        Spacer(modifier = Modifier.width(16.dp))
                        SectionHeader(
                            title = androidx.compose.ui.res.stringResource(com.example.R.string.about),
                            subtitle = androidx.compose.ui.res.stringResource(com.example.R.string.about_subtitle)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Tap to open",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    }
                }
            }
        }



        item { Spacer(modifier = Modifier.height(30.dp)) }
    }

    if (showUpdateDialog) {
        AppUpdateDialog(
            onDismissRequest = { showUpdateDialog = false }
        )
    }

    if (showSecurityDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSecurityDialog = false },
            title = {
                Text(
                    text = "Security",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // PIN Lock row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PIN App Lock",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isPinLockEnabled) "App lock active (4-6 digit PIN)" else "Secure app with a 4-6 digit PIN",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = isPinLockEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (com.example.util.PinLockManager.hasPinSet(context)) {
                                        com.example.util.PinLockManager.setPinLockEnabled(context, true)
                                        isPinLockEnabled = true
                                        onShowToast("PIN Lock enabled")
                                    } else {
                                        showPinSetupDialog = true
                                    }
                                } else {
                                    com.example.util.PinLockManager.setPinLockEnabled(context, false)
                                    isPinLockEnabled = false
                                    onShowToast("PIN Lock disabled")
                                }
                            },
                            modifier = Modifier.testTag("pin_lock_switch")
                        )
                    }

                    if (isPinLockEnabled || com.example.util.PinLockManager.hasPinSet(context)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { showPinChangeDialog = true },
                                modifier = Modifier.testTag("change_pin_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Change PIN")
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Fingerprint / Biometric Lock row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fingerprint / Biometric Lock",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Unlock app using fingerprint or face recognition",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = isFingerprintLockEnabled,
                            onCheckedChange = { checked ->
                                isFingerprintLockEnabled = checked
                                onShowToast(if (checked) "Biometric Lock enabled" else "Biometric Lock disabled")
                            },
                            modifier = Modifier.testTag("fingerprint_lock_switch")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSecurityDialog = false }
                ) {
                    Text("Close")
                }
            }
        )
    }



    if (showPinSetupDialog) {
        com.example.ui.components.PinSetupDialog(
            onDismiss = { showPinSetupDialog = false },
            onSuccess = {
                showPinSetupDialog = false
                isPinLockEnabled = true
            },
            onShowToast = onShowToast
        )
    }

    if (showPinChangeDialog) {
        com.example.ui.components.PinChangeDialog(
            onDismiss = { showPinChangeDialog = false },
            onSuccess = {
                showPinChangeDialog = false
            },
            onShowToast = onShowToast
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectionBottomSheet(
    currentThemeKey: String,
    onThemeSelected: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.select_theme),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.app_theme_appearance),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(com.example.ui.theme.ALL_THEME_ITEMS) { themeItem ->
                    val isSelected = currentThemeKey.equals(themeItem.key, ignoreCase = true) ||
                            (currentThemeKey.isBlank() && themeItem.key == "SYSTEM")

                    ThemeItemCard(
                        themeItem = themeItem,
                        isSelected = isSelected,
                        onClick = { onThemeSelected(themeItem.key) }
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeItemCard(
    themeItem: com.example.ui.theme.AppThemeItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Color Preview Swatch
                Surface(
                    modifier = Modifier.size(width = 48.dp, height = 34.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = themeItem.previewBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Surface(
                            modifier = Modifier
                                .padding(3.dp)
                                .size(width = 22.dp, height = 26.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = themeItem.previewSurface
                        ) {}
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(5.dp)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(themeItem.previewPrimary)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = themeItem.getLocalizedName(context),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                        ),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = themeItem.getDescription(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            } else {
                RadioButton(
                    selected = false,
                    onClick = onClick
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BulkSmsTemplateDialog(
    context: Context,
    ispName: String,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onShowToast: (String) -> Unit
) {
    var templateText by remember {
        mutableStateOf(com.example.util.SmsTemplateManager.getSmsTemplate(context))
    }

    var customTemplates by remember {
        mutableStateOf(com.example.util.SmsTemplateManager.getCustomTemplates(context))
    }

    var selectedTemplateId by remember {
        mutableStateOf<String?>(null)
    }

    var showSaveCustomDialog by remember { mutableStateOf(false) }
    var customTitleInput by remember { mutableStateOf("") }

    val allTemplates = remember(customTemplates) {
        com.example.util.SmsTemplateManager.BUILT_IN_TEMPLATES + customTemplates
    }

    val samplePreviewText = remember(templateText, ispName, currencySymbol) {
        com.example.util.SmsTemplateManager.replaceVariables(
            template = templateText,
            customerName = "রহিম আহমেদ",
            monthlyFee = "${currencySymbol}500.00",
            dueAmount = "${currencySymbol}500.00",
            packageName = "10 Mbps Basic",
            phone = "01700000000",
            ispName = ispName.ifBlank { "ISP Net" }
        )
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "💬 Bulk SMS Template",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "রেডি-মেড বাংলা টেমপ্লেট নির্বাচন ও কাস্টমাইজ করুন",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Ready-made Templates Selector
                Text(
                    text = "📋 প্রস্তুত বাংলা টেমপ্লেটসমূহ (Select Template):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allTemplates.forEach { item ->
                        val isSelected = selectedTemplateId == item.id || templateText == item.content
                        Surface(
                            onClick = {
                                selectedTemplateId = item.id
                                templateText = item.content
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (!item.isBuiltIn) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    text = "Custom",
                                                    fontSize = 9.sp,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.content.replace("\n", " "),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!item.isBuiltIn) {
                                        IconButton(
                                            onClick = {
                                                com.example.util.SmsTemplateManager.deleteCustomTemplate(context, item.id)
                                                customTemplates = com.example.util.SmsTemplateManager.getCustomTemplates(context)
                                                onShowToast("কাস্টম টেমপ্লেট মুছে ফেলা হয়েছে")
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Tags Insertion
                Text(
                    text = "🏷️ ট্যাগ নির্বাচন করুন (ক্লিক করলে টেক্সটে যুক্ত হবে):",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "[Customer Name]",
                        "[Monthly Fee]",
                        "[Due Amount]",
                        "[Package/Speed]",
                        "[Phone Number]",
                        "[ISP Name]"
                    ).forEach { tag ->
                        Surface(
                            onClick = { templateText += " $tag" },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Editable Box
                OutlinedTextField(
                    value = templateText,
                    onValueChange = { templateText = it },
                    label = { Text("Bangla SMS Template Editor") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showSaveCustomDialog = true }
                    ) {
                        Text("➕ কাস্টম হিসাবে সংরক্ষণ", fontSize = 11.sp)
                    }

                    TextButton(
                        onClick = {
                            templateText = com.example.util.SmsTemplateManager.DEFAULT_TEMPLATE
                            selectedTemplateId = null
                        }
                    ) {
                        Text("ডিফল্ট টেমপ্লেট রিসেট", fontSize = 11.sp)
                    }
                }

                // Sample Preview Card
                Text(
                    text = "👁️ স্যাম্পল মেসেজ প্রিভিউ (Live Preview):",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = samplePreviewText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("বাতিল")
                }

                Button(
                    onClick = {
                        com.example.util.SmsTemplateManager.saveSmsTemplate(context, templateText)
                        onShowToast("ডিফল্ট এসএমএস টেমপ্লেট সফলভাবে সংরক্ষিত হয়েছে")
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ডিফল্ট সেট করুন")
                }
            }
        },
        dismissButton = null
    )

    if (showSaveCustomDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSaveCustomDialog = false },
            title = { Text("কাস্টম টেমপ্লেট সংরক্ষণ করুন") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("টেমপ্লেটের জন্য একটি নাম দিন:")
                    OutlinedTextField(
                        value = customTitleInput,
                        onValueChange = { customTitleInput = it },
                        label = { Text("Template Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customTitleInput.isNotBlank()) {
                            val saved = com.example.util.SmsTemplateManager.saveCustomTemplate(
                                context,
                                customTitleInput.trim(),
                                templateText
                            )
                            customTemplates = com.example.util.SmsTemplateManager.getCustomTemplates(context)
                            selectedTemplateId = saved.id
                            showSaveCustomDialog = false
                            customTitleInput = ""
                            onShowToast("কাস্টম টেমপ্লেট সংরক্ষিত হয়েছে")
                        }
                    }
                ) {
                    Text("সংরক্ষণ")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSaveCustomDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }
}
