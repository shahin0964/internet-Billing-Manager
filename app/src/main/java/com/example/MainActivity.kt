package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.os.Build
import com.example.data.model.BillEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.IspPackageEntity
import com.example.ui.components.AppUpdateDialog
import com.example.util.AppUpdateManager
import com.example.ui.components.BillGenerateDialog
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.example.ui.components.CustomerDialog
import com.example.ui.components.PackageDialog
import com.example.ui.components.PaymentDialog
import com.example.ui.components.EditBillDialog
import com.example.ui.screens.BillingScreen
import com.example.ui.screens.CollectionScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DueManagementScreen
import com.example.ui.screens.ExpenseManagementScreen
import com.example.ui.screens.MoreScreen
import com.example.ui.theme.IspControlTheme
import com.example.ui.viewmodel.IspViewModel

enum class NavTab(
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD(com.example.R.string.dashboard, Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    CUSTOMERS(com.example.R.string.customers, Icons.Filled.People, Icons.Outlined.People),
    BILLING(com.example.R.string.billing, Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
    COLLECTION(com.example.R.string.collection, Icons.Filled.CreditCard, Icons.Outlined.CreditCard),
    MORE(com.example.R.string.more, Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz)
}

class MainActivity : ComponentActivity() {

    private val viewModel: IspViewModel by viewModels()
    private val openUpdateDialogFlow = kotlinx.coroutines.flow.MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_update_dialog", false)) {
            openUpdateDialogFlow.tryEmit(true)
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        val prefs = newBase.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val lang = prefs.getString("app_lang", "en") ?: "en"
        val locale = java.util.Locale(lang)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (intent?.getBooleanExtra("open_update_dialog", false) == true) {
            openUpdateDialogFlow.tryEmit(true)
        }

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            IspControlTheme(themeMode = settings.themeMode) {
                MainAppContent(viewModel = viewModel, openUpdateDialogFlow = openUpdateDialogFlow)
            }
        }
    }
}

@Composable
fun MainAppContent(
    viewModel: IspViewModel,
    openUpdateDialogFlow: kotlinx.coroutines.flow.SharedFlow<Boolean> = remember { kotlinx.coroutines.flow.MutableSharedFlow() }
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(NavTab.DASHBOARD) }
    val pagerState = rememberPagerState(initialPage = currentTab.ordinal) { NavTab.entries.size }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentTab) {
        if (pagerState.currentPage != currentTab.ordinal) {
            pagerState.animateScrollToPage(currentTab.ordinal)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (currentTab.ordinal != pagerState.currentPage) {
            currentTab = NavTab.entries[pagerState.currentPage]
        }
    }

    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val packages by viewModel.packages.collectAsStateWithLifecycle()
    val bills by viewModel.bills.collectAsStateWithLifecycle()
    val payments by viewModel.payments.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val expenseCategories by viewModel.expenseCategories.collectAsStateWithLifecycle()

    val customerQuery by viewModel.customerSearchQuery.collectAsStateWithLifecycle()
    val customerStatusFilter by viewModel.customerStatusFilter.collectAsStateWithLifecycle()

    val billQuery by viewModel.billSearchQuery.collectAsStateWithLifecycle()

    val collectionQuery by viewModel.collectionSearchQuery.collectAsStateWithLifecycle()

    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Navigation & Overlay state
    var showExpenseManagementScreen by remember { mutableStateOf(false) }

    // Dialog state
    var showCustomerDialog by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<CustomerEntity?>(null) }

    var showPaymentDialog by remember { mutableStateOf(false) }
    var preSelectedPaymentBill by remember { mutableStateOf<BillEntity?>(null) }
    var showEditBillDialog by remember { mutableStateOf(false) }
    var billToEdit by remember { mutableStateOf<BillEntity?>(null) }

    var showPackageDialog by remember { mutableStateOf(false) }
    var packageToEdit by remember { mutableStateOf<IspPackageEntity?>(null) }

    var showGenerateBillsDialog by remember { mutableStateOf(false) }
    var showAutoUpdatePrompt by remember { mutableStateOf(false) }
    var autoUpdateReleaseInfo by remember { mutableStateOf<com.example.util.GitHubReleaseInfo?>(null) }
    var showBackupAndRestoreScreen by remember { mutableStateOf(false) }
    var showAboutScreen by remember { mutableStateOf(false) }
    val initialAuthUser = remember {
        try {
            if (com.example.IspApplication.isLoggedIn(context)) {
                com.example.IspApplication.ensureFirebaseInitialized(context)
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            } else {
                null
            }
        } catch (e: Throwable) {
            null
        }
    }
    var isAuthChosen by remember { mutableStateOf(initialAuthUser != null) }
    var isGuestMode by remember { mutableStateOf(false) }
    var authModeSignUp by remember { mutableStateOf(false) }
    var showLoginRequiredDialog by remember { mutableStateOf(false) }
    var isAppLocked by remember {
        mutableStateOf(
            try {
                com.example.util.PinLockManager.isPinLockEnabled(context)
            } catch (e: Throwable) {
                false
            }
        )
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                if (com.example.util.PinLockManager.isPinLockEnabled(context)) {
                    isAppLocked = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun runAction(action: () -> Unit) {
        if (isGuestMode) {
            showLoginRequiredDialog = true
        } else {
            action()
        }
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            android.util.Log.d("MainActivity", "Notification permission granted")
        }
    }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                "android.permission.POST_NOTIFICATIONS"
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                permissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
            }
        }
    }

    LaunchedEffect(openUpdateDialogFlow) {
        openUpdateDialogFlow.collect { trigger ->
            if (trigger) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val result = AppUpdateManager.checkForUpdates(context, force = false)
                    val info = result.getOrNull()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        autoUpdateReleaseInfo = info
                        showAutoUpdatePrompt = true
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val result = AppUpdateManager.checkForUpdates(context, force = false)
                result.onSuccess { info ->
                    if (info.isNewer) {
                        val alreadyNotified = AppUpdateManager.isVersionAlreadyNotified(context, info.version)
                        if (!alreadyNotified) {
                            AppUpdateManager.saveVersionNotified(context, info.version)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                autoUpdateReleaseInfo = info
                                showAutoUpdatePrompt = true
                            }
                            com.example.util.AppNotificationHelper.showUpdateNotification(context, info.version)
                        }
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.w("MainActivity", "Auto update check failed safely on startup: ${e.message}")
            }
        }
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    if (!isAuthChosen) {
        if (authModeSignUp) {
            com.example.ui.screens.SignUpScreen(
                onSignUpClick = { name, email, phone, pass, onError, onSuccess ->
                    try {
                        com.example.IspApplication.ensureFirebaseInitialized(context)
                        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                        auth.createUserWithEmailAndPassword(email.trim(), pass)
                            .addOnSuccessListener { result ->
                                val user = result.user
                                if (user != null && name.isNotBlank()) {
                                    val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                        .setDisplayName(name.trim())
                                        .build()
                                    user.updateProfile(profileUpdates)
                                }
                                com.example.IspApplication.setLoggedIn(context, true)
                                isGuestMode = false
                                isAuthChosen = true
                                viewModel.showToast("Account created for ${name.ifBlank { email }}")
                                onSuccess()
                            }
                            .addOnFailureListener { e ->
                                val rawMsg = e.localizedMessage ?: e.message ?: ""
                                val friendlyMsg = when {
                                    rawMsg.contains("EMAIL_EXISTS", ignoreCase = true) || rawMsg.contains("ALREADY_IN_USE", ignoreCase = true) ->
                                        "An account with this email already exists. Please log in."
                                    rawMsg.contains("WEAK_PASSWORD", ignoreCase = true) ->
                                        "Password should be at least 6 characters."
                                    rawMsg.contains("INVALID_EMAIL", ignoreCase = true) ->
                                        "Please enter a valid email address."
                                    rawMsg.contains("API keys are not supported", ignoreCase = true) ||
                                    rawMsg.contains("internal error", ignoreCase = true) ->
                                        "Unable to connect to authentication server. Please try again or continue as guest."
                                    else -> rawMsg.ifBlank { "Sign up failed. Please try again." }
                                }
                                onError(friendlyMsg)
                            }
                    } catch (e: Throwable) {
                        onError(e.localizedMessage ?: e.message ?: "Sign up error occurred.")
                    }
                },
                onNavigateToLogin = {
                    authModeSignUp = false
                }
            )
        } else {
            com.example.ui.screens.LoginScreen(
                onLoginClick = { identifier, pass, rememberMe, onError, onSuccess ->
                    try {
                        com.example.IspApplication.ensureFirebaseInitialized(context)
                        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                        val trimmedId = identifier.trim()
                        if (trimmedId.contains("@")) {
                            auth.signInWithEmailAndPassword(trimmedId, pass)
                                .addOnSuccessListener { result ->
                                    com.example.IspApplication.setLoggedIn(context, true)
                                    isGuestMode = false
                                    isAuthChosen = true
                                    viewModel.showToast("Logged in as ${result.user?.email ?: trimmedId}")
                                    onSuccess()
                                }
                                .addOnFailureListener { e ->
                                    val rawMsg = e.localizedMessage ?: e.message ?: ""
                                    val friendlyMsg = when {
                                        rawMsg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
                                        rawMsg.contains("INVALID_CREDENTIALS", ignoreCase = true) ||
                                        rawMsg.contains("WRONG_PASSWORD", ignoreCase = true) ||
                                        rawMsg.contains("INVALID_PASSWORD", ignoreCase = true) ->
                                            "Incorrect email or password. Please check your credentials."
                                        rawMsg.contains("USER_NOT_FOUND", ignoreCase = true) ||
                                        rawMsg.contains("no user record", ignoreCase = true) ->
                                            "No account found with this email. Please sign up first."
                                        rawMsg.contains("TOO_MANY_ATTEMPTS", ignoreCase = true) ||
                                        rawMsg.contains("TOO_MANY_REQUESTS", ignoreCase = true) ->
                                            "Too many failed login attempts. Please try again later."
                                        rawMsg.contains("USER_DISABLED", ignoreCase = true) ->
                                            "This user account has been disabled."
                                        rawMsg.contains("API keys are not supported", ignoreCase = true) ||
                                        rawMsg.contains("internal error", ignoreCase = true) ->
                                            "Unable to connect to authentication server. Please try again or continue as guest."
                                        else -> rawMsg.ifBlank { "Login failed. Please check your credentials." }
                                    }
                                    onError(friendlyMsg)
                                }
                        } else {
                            onError("Please enter a valid Gmail / Email address (e.g. user@example.com).")
                        }
                    } catch (e: Throwable) {
                        onError(e.localizedMessage ?: e.message ?: "Login error occurred.")
                    }
                },
                onNavigateToSignUp = {
                    authModeSignUp = true
                },
                onContinueAsGuest = {
                    isGuestMode = true
                    isAuthChosen = true
                },
                onForgotPasswordClick = { identifier, onError, onSuccess ->
                    try {
                        com.example.IspApplication.ensureFirebaseInitialized(context)
                        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                        val email = identifier.trim()
                        if (!email.contains("@")) {
                            onError("Please enter a valid Gmail / Email address to receive password reset link.")
                        } else {
                            auth.sendPasswordResetEmail(email)
                                .addOnSuccessListener {
                                    onSuccess("Password reset email sent to $email")
                                }
                                .addOnFailureListener { e ->
                                    val rawMsg = e.localizedMessage ?: e.message ?: ""
                                    val friendlyMsg = when {
                                        rawMsg.contains("USER_NOT_FOUND", ignoreCase = true) ||
                                        rawMsg.contains("no user record", ignoreCase = true) ->
                                            "No account found with this email address."
                                        else -> rawMsg.ifBlank { "Failed to send password reset email." }
                                    }
                                    onError(friendlyMsg)
                                }
                        }
                    } catch (e: Throwable) {
                        onError(e.localizedMessage ?: e.message ?: "Password reset error occurred.")
                    }
                }
            )
        }
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                val isLiquidGlassTheme = settings.themeMode.uppercase() == "LIQUID_GLASS" && com.example.ui.theme.isLiquidGlassSupported()
                if (isLiquidGlassTheme) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp)
                    ) {
                        // Blurred background glass layer (bright white frosted glass look)
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .shadow(6.dp, shape = RoundedCornerShape(32.dp))
                                .clip(RoundedCornerShape(32.dp))
                                .background(Color.White.copy(alpha = 0.65f))
                                .blur(12.dp)
                        )
                        
                        // Glass border & content layer (Unblurred)
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Transparent)
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(32.dp)
                                )
                        ) {
                            val containerWidth = maxWidth
                            val totalTabs = NavTab.entries.size
                            val tabWidth = containerWidth / totalTabs
                            
                            val currentFloatPage = pagerState.currentPage + pagerState.currentPageOffsetFraction
                            val offsetAbs = kotlin.math.abs(pagerState.currentPageOffsetFraction)
                            val stretchFactor = kotlin.math.sin(offsetAbs * kotlin.math.PI).toFloat()
                            
                            val basePillWidth = tabWidth - 8.dp
                            val pillWidth = basePillWidth * (1f + stretchFactor * 0.22f)
                            
                            val pillCenter = tabWidth * (currentFloatPage + 0.5f)
                            val pillLeft = (pillCenter - (pillWidth / 2f)).coerceIn(4.dp, containerWidth - pillWidth - 4.dp)
                            
                            // 1. Sliding glass highlight indicator (extremely premium 3D white glass pill)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .offset(x = pillLeft)
                                    .width(pillWidth)
                                    .height(46.dp)
                                    .shadow(
                                        elevation = 8.dp,
                                        shape = RoundedCornerShape(24.dp),
                                        ambientColor = Color.Black.copy(alpha = 0.12f),
                                        spotColor = Color.Black.copy(alpha = 0.2f)
                                    )
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color.White.copy(alpha = 0.92f))
                                    .border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = 0.95f),
                                        shape = RoundedCornerShape(24.dp)
                                    )
                            )
                            
                            // 2. Interactive Menu Icons & Text
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NavTab.entries.forEachIndexed { index, tab ->
                                    val distance = kotlin.math.abs(currentFloatPage - index)
                                    val selectionFraction = (1f - distance).coerceIn(0f, 1f)
                                    
                                    val activeColor = Color(0xFF0F172A) // Rich slate black
                                    val inactiveColor = Color(0x730F172A) // Semi-transparent slate black
                                    val tabColor = lerp(inactiveColor, activeColor, selectionFraction)
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(24.dp))
                                            .clickable {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(tab.ordinal)
                                                }
                                            }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            val scale = 1f + (selectionFraction * 0.10f)
                                            Icon(
                                                imageVector = if (selectionFraction > 0.5f) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = androidx.compose.ui.res.stringResource(tab.titleRes),
                                                tint = tabColor,
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .graphicsLayer {
                                                        scaleX = scale
                                                        scaleY = scale
                                                    }
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = androidx.compose.ui.res.stringResource(tab.titleRes),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = if (selectionFraction > 0.5f) FontWeight.Bold else FontWeight.Normal
                                                ),
                                                color = tabColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    NavigationBar(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        NavTab.entries.forEach { tab ->
                            val isSelected = currentTab == tab
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = androidx.compose.ui.res.stringResource(tab.titleRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(tab.titleRes),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isLiquidGlassTheme = settings.themeMode.uppercase() == "LIQUID_GLASS" && com.example.ui.theme.isLiquidGlassSupported()
            if (isLiquidGlassTheme) {
                com.example.ui.theme.GlassmorphismBackground()
            }
            val RenderScreen = @Composable { tab: NavTab ->
                when (tab) {
                NavTab.DASHBOARD -> {
                    DashboardScreen(
                        customers = customers,
                        bills = bills,
                        payments = payments,
                        expenses = expenses,
                        settings = settings,
                        onNavigateToCustomers = { currentTab = NavTab.CUSTOMERS },
                        onNavigateToBilling = { currentTab = NavTab.BILLING },
                        onNavigateToCollection = { currentTab = NavTab.COLLECTION },
                        onNavigateToDue = {
                            currentTab = NavTab.BILLING
                        },
                        onAddCustomerClick = {
                            runAction {
                                customerToEdit = null
                                showCustomerDialog = true
                            }
                        },
                        onCollectPaymentClick = {
                            runAction {
                                preSelectedPaymentBill = null
                                showPaymentDialog = true
                            }
                        },
                        onGenerateBillsClick = {
                            runAction {
                                showGenerateBillsDialog = true
                            }
                        }
                    )
                }

                NavTab.CUSTOMERS -> {
                    CustomersScreen(
                        customers = customers,
                        bills = bills,
                        payments = payments,
                        packages = packages,
                        currencySymbol = settings.currencySymbol,
                        searchQuery = customerQuery,
                        onSearchQueryChange = { viewModel.customerSearchQuery.value = it },
                        selectedStatusFilter = customerStatusFilter,
                        onStatusFilterChange = { viewModel.customerStatusFilter.value = it },
                        onAddCustomerClick = {
                            runAction {
                                customerToEdit = null
                                showCustomerDialog = true
                            }
                        },
                        onEditCustomerClick = { cust ->
                            runAction {
                                customerToEdit = cust
                                showCustomerDialog = true
                            }
                        },
                        onDeleteCustomerClick = { cust ->
                            runAction {
                                viewModel.deleteCustomer(cust)
                            }
                        },
                        onToggleStatusClick = { cust ->
                            runAction {
                                viewModel.toggleCustomerStatus(cust)
                            }
                        },
                        onCollectPaymentForCustomer = { cust ->
                            runAction {
                                val unpaidForCust = bills.find { it.customerId == cust.id && it.dueAmount > 0 }
                                preSelectedPaymentBill = unpaidForCust
                                showPaymentDialog = true
                            }
                        },
                        ispName = settings.ispName
                    )
                }

                NavTab.BILLING -> {
                    val billingScreenBills by viewModel.billingScreenBills.collectAsStateWithLifecycle()
                    BillingScreen(
                        bills = billingScreenBills,
                        currencySymbol = settings.currencySymbol,
                        searchQuery = billQuery,
                        onSearchQueryChange = { viewModel.billSearchQuery.value = it },
                        onGenerateBillsClick = {
                            runAction {
                                showGenerateBillsDialog = true
                            }
                        },
                        onRecordPaymentForBill = { bill ->
                            runAction {
                                preSelectedPaymentBill = bill
                                showPaymentDialog = true
                            }
                        },
                        onEditBill = { bill ->
                            runAction {
                                billToEdit = bill
                                showEditBillDialog = true
                            }
                        }
                    )
                }

                NavTab.COLLECTION -> {
                    CollectionScreen(
                        payments = payments,
                        bills = bills,
                        currencySymbol = settings.currencySymbol,
                        searchQuery = collectionQuery,
                        onSearchQueryChange = { viewModel.collectionSearchQuery.value = it },
                        onCollectPaymentClick = {
                            runAction {
                                preSelectedPaymentBill = null
                                showPaymentDialog = true
                            }
                        },
                        onDeletePaymentClick = { payment ->
                            runAction {
                                viewModel.deletePayment(payment)
                            }
                        }
                    )
                }

                NavTab.MORE -> {
                    MoreScreen(
                        settings = settings,
                        packages = packages,
                        onUpdateSettings = { newSettings ->
                            runAction {
                                viewModel.updateSettings(newSettings)
                            }
                        },
                        onAddPackageClick = {
                            runAction {
                                packageToEdit = null
                                showPackageDialog = true
                            }
                        },
                        onEditPackageClick = { pkg ->
                            runAction {
                                packageToEdit = pkg
                                showPackageDialog = true
                            }
                        },
                        onExportBackup = { callback ->
                            runAction {
                                viewModel.exportBackup(callback)
                            }
                        },
                        onShowToast = { msg ->
                            viewModel.showToast(msg)
                        },
                        isGuestMode = isGuestMode,
                        onOpenExpenseManagement = {
                            runAction {
                                showExpenseManagementScreen = true
                            }
                        },
                        onOpenBackupAndRestore = {
                            runAction {
                                showBackupAndRestoreScreen = true
                            }
                        },
                        onOpenAbout = {
                            showAboutScreen = true
                        },
                        onOpenLogin = {
                            authModeSignUp = false
                            isAuthChosen = false
                        },
                        onSignOut = {
                            isGuestMode = false
                            isAuthChosen = false
                        }
                    )
                }
            }
            }

            if (isLiquidGlassTheme) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = true
                ) { page ->
                    RenderScreen(NavTab.entries[page])
                }
            } else {
                RenderScreen(currentTab)
            }
        }
    }

    if (showExpenseManagementScreen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showExpenseManagementScreen = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            ExpenseManagementScreen(
                expenses = expenses,
                customCategories = expenseCategories,
                currencySymbol = settings.currencySymbol,
                onBackClick = { showExpenseManagementScreen = false },
                onSaveExpense = { viewModel.saveExpense(it) },
                onUpdateExpense = { viewModel.updateExpense(it) },
                onDeleteExpense = { viewModel.deleteExpense(it) },
                onAddCustomCategory = { viewModel.addCustomCategory(it) }
            )
        }
    }

    if (showBackupAndRestoreScreen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showBackupAndRestoreScreen = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            com.example.ui.screens.BackupAndRestoreScreen(
                viewModel = viewModel,
                onBackClick = { showBackupAndRestoreScreen = false }
            )
        }
    }

    if (showAboutScreen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showAboutScreen = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            com.example.ui.screens.AboutScreen(
                onBackClick = { showAboutScreen = false },
                isGuestMode = isGuestMode
            )
        }
    }

    if (showLoginRequiredDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLoginRequiredDialog = false },
            title = {
                Text(
                    text = "Login Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "You are currently in Guest Mode. Please log in or sign up to perform this action.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLoginRequiredDialog = false
                        authModeSignUp = false
                        isAuthChosen = false
                    }
                ) {
                    Text("Login")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showLoginRequiredDialog = false
                        authModeSignUp = true
                        isAuthChosen = false
                    }
                ) {
                    Text("Sign Up")
                }
            }
        )
    }

    // Dialogs
    if (showCustomerDialog) {
        CustomerDialog(
            initialCustomer = customerToEdit,
            availablePackages = packages,
            currencySymbol = settings.currencySymbol,
            onDismiss = { showCustomerDialog = false },
            onSave = { customer ->
                if (customer.id == 0L) {
                    viewModel.saveCustomer(customer)
                } else {
                    viewModel.updateCustomer(customer)
                }
                showCustomerDialog = false
            }
        )
    }

    if (showPaymentDialog) {
        val unpaidBills = bills.filter { it.dueAmount > 0 }
        PaymentDialog(
            unpaidBills = unpaidBills,
            preSelectedBill = preSelectedPaymentBill,
            currencySymbol = settings.currencySymbol,
            onDismiss = { showPaymentDialog = false },
            onRecordPayment = { billId, customerId, amount, method, notes ->
                viewModel.recordPayment(billId, customerId, amount, method, notes)
                showPaymentDialog = false
            }
        )
    }

    if (showEditBillDialog && billToEdit != null) {
        val selectedCustomerForEdit = customers.find { it.id == billToEdit?.customerId }
        EditBillDialog(
            bill = billToEdit!!,
            customer = selectedCustomerForEdit,
            availablePackages = packages,
            currencySymbol = settings.currencySymbol,
            onDismiss = { showEditBillDialog = false; billToEdit = null },
            onSave = { updatedCustomer, updatedBill ->
                viewModel.updateCustomer(updatedCustomer)
                viewModel.updateBill(updatedBill)
                showEditBillDialog = false
                billToEdit = null
            },
            onDeleteBill = { billToDelete ->
                viewModel.deleteBill(billToDelete)
                showEditBillDialog = false
                billToEdit = null
            }
        )
    }

    if (showPackageDialog) {
        PackageDialog(
            initialPackage = packageToEdit,
            currencySymbol = settings.currencySymbol,
            onDismiss = { showPackageDialog = false },
            onSave = { pkg ->
                if (pkg.id == 0L) {
                    viewModel.savePackage(pkg)
                } else {
                    viewModel.updatePackage(pkg)
                }
                showPackageDialog = false
            }
        )
    }

    if (showGenerateBillsDialog) {
        val activeCustomers = remember(customers) { customers.filter { it.status == "ACTIVE" } }
        BillGenerateDialog(
            activeCustomers = activeCustomers,
            onDismiss = { showGenerateBillsDialog = false },
            onGenerate = { month, dueDate, selectedCustomerIds ->
                viewModel.generateMonthlyBills(month, dueDate, selectedCustomerIds)
                showGenerateBillsDialog = false
            }
        )
    }

    if (showAutoUpdatePrompt) {
        AppUpdateDialog(
            initialReleaseInfo = autoUpdateReleaseInfo,
            onDismissRequest = { showAutoUpdatePrompt = false }
        )
    }

    if (isAppLocked) {
        com.example.ui.components.PinUnlockOverlayScreen(
            onUnlocked = {
                isAppLocked = false
            },
            onShowToast = { msg ->
                viewModel.showToast(msg)
            }
        )
    }
}
}
