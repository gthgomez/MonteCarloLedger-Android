package com.montecarlo.ledger.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import com.montecarlo.ledger.GlassTokens
import com.montecarlo.ledger.MainViewModel
import com.montecarlo.ledger.ui.GlassTint
import com.montecarlo.ledger.data.LedgerBackupSnapshot
import com.montecarlo.ledger.data.ReminderPreferences
import com.montecarlo.ledger.data.SettingsEntity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.OnboardingMilestone
import com.montecarlo.ledger.data.OnboardingProgress
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.data.RecurringCandidate
import com.montecarlo.ledger.util.centsToDisplay
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppView(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val incomes by viewModel.allIncome.collectAsStateWithLifecycle()
    val payments by viewModel.allPayments.collectAsStateWithLifecycle()
    val debts by viewModel.allDebts.collectAsStateWithLifecycle()
    val billOccurrences by viewModel.allBillOccurrences.collectAsStateWithLifecycle()
    val transactionRules by viewModel.allTransactionRules.collectAsStateWithLifecycle()
    val accounts by viewModel.allAccounts.collectAsStateWithLifecycle()
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val onboardingProgress by viewModel.onboardingProgress.collectAsStateWithLifecycle()
    val settings by viewModel.allSettings.collectAsStateWithLifecycle()
    val reminderPreferences by viewModel.reminderPreferences.collectAsStateWithLifecycle()
    val appLockPreferences by viewModel.appLockPreferences.collectAsStateWithLifecycle()
    val appLockUnlocked by viewModel.appLockUnlocked.collectAsStateWithLifecycle()
    AppLockSessionEffects(viewModel = viewModel, lockEnabled = appLockPreferences.enabled)
    var section by rememberSaveable { mutableStateOf(AppSection.Dashboard) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showRulesScreen by rememberSaveable { mutableStateOf(false) }
    var addKind by rememberSaveable { mutableStateOf<AddKind?>(null) }
    var pendingBillPrefill by remember { mutableStateOf<BillPrefill?>(null) }
    var selectedIncome by remember { mutableStateOf<IncomeEntity?>(null) }
    var selectedPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var selectedTransaction by remember { mutableStateOf<com.montecarlo.ledger.data.TransactionEntity?>(null) }
    val hazeState = remember { HazeState() }
    val colorScheme = MaterialTheme.colorScheme
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthClass = windowWidthClass(maxWidth)
        val isCompact = widthClass == WindowWidthClass.Compact
        val isExpanded = widthClass == WindowWidthClass.Expanded

        val railWidth = if (isExpanded) 88.dp else 72.dp

        var lockError by rememberSaveable { mutableStateOf<String?>(null) }
        val locked = appLockPreferences.enabled && !appLockUnlocked
        val throttleState by viewModel.appLockThrottleState.collectAsStateWithLifecycle()
        val lockoutRemainingSeconds = if (throttleState.lockoutUntilEpochMs > 0L) {
            ((throttleState.lockoutUntilEpochMs - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        } else 0L

        if (locked) {
            AppBrandBackdrop(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.background)
            )
            AppLockScreen(
                errorMessage = lockError,
                lockoutRemainingSeconds = lockoutRemainingSeconds,
                onUnlock = { pin ->
                    viewModel.unlockApp(pin) { unlocked, errorMsg ->
                        lockError = if (unlocked) null else (errorMsg ?: "Incorrect PIN.")
                    }
                },
            )
            return@BoxWithConstraints
        }

        Row(modifier = Modifier.fillMaxSize()) {
            if (!isCompact) {
                AdaptiveNavigationRail(
                    section = section,
                    onSelectSection = { section = it },
                    railWidth = railWidth,
                    colorScheme = colorScheme,
                )
            }

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                AppChrome(
                    viewModel = viewModel,
                    uiState = uiState,
                    incomes = incomes,
                    payments = payments,
                    debts = debts,
                    pendingBillPrefill = pendingBillPrefill,
                    onClearBillPrefill = { pendingBillPrefill = null },
                    billOccurrences = billOccurrences,
                    transactionRules = transactionRules,
                    transactions = transactions,
                    accounts = accounts,
                    onboardingProgress = onboardingProgress,
                    settings = settings,
                    reminderPreferences = reminderPreferences,
                    appLockPreferences = appLockPreferences,
                    dashboardErrorMessage = uiState.error,
                    section = section,
                    addKind = addKind,
                    showSettings = showSettings,
                    showRulesScreen = showRulesScreen,
                    onSetSection = { section = it },
                    onSetAddKind = { addKind = it },
                    onTrackAsBill = { candidate ->
                        pendingBillPrefill = candidate.toBillPrefill()
                        addKind = AddKind.Bill
                    },
                    onSetShowSettings = { showSettings = it },
                    onSetShowRulesScreen = { showRulesScreen = it },
                    selectedIncome = selectedIncome,
                    selectedPayment = selectedPayment,
                    selectedTransaction = selectedTransaction,
                    onSelectIncome = { selectedIncome = it },
                    onSelectPayment = { selectedPayment = it },
                    onSelectTransaction = { selectedTransaction = it },
                    onClearSelection = {
                        selectedIncome = null
                        selectedPayment = null
                        selectedTransaction = null
                    },
                    hazeState = hazeState,
                    colorScheme = colorScheme,
                    isCompact = isCompact,
                )

                if (addKind == null && selectedIncome == null && selectedPayment == null && selectedTransaction == null && !showSettings && !showRulesScreen) {
                    QuickAddFab(
                        onAddExpense = { addKind = AddKind.Transaction },
                        onAddBill = { addKind = AddKind.Bill },
                        onAddIncome = { addKind = AddKind.Income },
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppChrome(
    viewModel: MainViewModel,
    uiState: com.montecarlo.ledger.AppUiState,
    incomes: List<IncomeEntity>,
    payments: List<PaymentEntity>,
    debts: List<com.montecarlo.ledger.data.DebtEntity>,
    pendingBillPrefill: BillPrefill?,
    onClearBillPrefill: () -> Unit,
    billOccurrences: List<com.montecarlo.ledger.data.BillOccurrenceEntity>,
    transactionRules: List<com.montecarlo.ledger.data.TransactionRuleEntity>,
    transactions: List<com.montecarlo.ledger.data.TransactionEntity>,
    accounts: List<com.montecarlo.ledger.data.AccountEntity>,
    onboardingProgress: OnboardingProgress,
    settings: List<SettingsEntity>,
    reminderPreferences: ReminderPreferences,
    appLockPreferences: com.montecarlo.ledger.data.AppLockPreferences,
    dashboardErrorMessage: String?,
    section: AppSection,
    addKind: AddKind?,
    showSettings: Boolean,
    showRulesScreen: Boolean,
    onSetSection: (AppSection) -> Unit,
    onSetAddKind: (AddKind?) -> Unit,
    onTrackAsBill: (RecurringCandidate) -> Unit,
    onSetShowSettings: (Boolean) -> Unit,
    onSetShowRulesScreen: (Boolean) -> Unit,
    selectedIncome: IncomeEntity?,
    selectedPayment: PaymentEntity?,
    selectedTransaction: com.montecarlo.ledger.data.TransactionEntity?,
    onSelectIncome: (IncomeEntity?) -> Unit,
    onSelectPayment: (PaymentEntity?) -> Unit,
    onSelectTransaction: (com.montecarlo.ledger.data.TransactionEntity?) -> Unit,
    onClearSelection: () -> Unit,
    hazeState: HazeState,
    colorScheme: androidx.compose.material3.ColorScheme,
    isCompact: Boolean,
) {
    val title = when {
        selectedIncome != null -> "Edit Income"
        selectedPayment != null -> "Edit Payment"
        selectedTransaction != null -> "Edit Transaction"
        section == AppSection.Dashboard && onboardingProgress.isComplete -> "Dashboard"
        section == AppSection.Dashboard && !onboardingProgress.isComplete -> "Start here"
        addKind == AddKind.Income -> "Log paycheck"
        addKind == AddKind.Bill -> "Add bill"
        addKind == AddKind.Transaction -> "Record spending"
        addKind == AddKind.Goal -> "Set a savings goal"
        addKind == null && section == AppSection.Dashboard && !onboardingProgress.isComplete -> "Choose what to add"
        else -> section.title
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var transactionCsvImportPreview by remember { mutableStateOf<CsvImportPreview?>(null) }
    var billCsvImportPreview by remember { mutableStateOf<BillCsvImportPreview?>(null) }
    var restoreBackupPreview by remember { mutableStateOf<com.montecarlo.ledger.data.LedgerBackupSnapshot?>(null) }
    var showSuccessToast by remember { mutableStateOf(false) }
    var showReminderSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showAppLockSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showBankBalanceDialog by rememberSaveable { mutableStateOf(false) }
    var showAddAnotherBillDialog by rememberSaveable { mutableStateOf(false) }
    var showEncryptDialog by rememberSaveable { mutableStateOf(false) }
    var pendingBackupUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showDecryptDialog by remember { mutableStateOf<android.net.Uri?>(null) }
    var showPrivacyDialog by rememberSaveable { mutableStateOf(false) }
    val csvMimeTypes = arrayOf(
        "text/csv",
        "text/plain",
        "application/csv",
        "application/vnd.ms-excel"
    )
    val jsonMimeTypes = arrayOf(
        "application/json",
        "text/json",
        "text/plain"
    )
    val encryptedBackupMimeTypes = arrayOf(
        "application/octet-stream",
        "text/plain",
        "*/*",
    )
    fun handlePersistenceResult(
        result: Result<Unit>,
        showSuccess: Boolean = true,
        onSuccessAction: () -> Unit,
    ) {
        result.fold(
            onSuccess = {
                onSuccessAction()
                if (showSuccess) showSuccessToast = true
            },
            onFailure = { throwable ->
                Toast.makeText(
                    context,
                    throwable.message ?: "Unable to save changes.",
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
    }
    val backupPayload = remember(
        uiState,
        incomes,
        payments,
        billOccurrences,
        transactionRules,
        transactions,
        onboardingProgress,
        settings,
        reminderPreferences,
        dashboardErrorMessage,
    ) {
        buildLedgerBackupJson(
            exportedAtIso = LocalDateTime.now().toString(),
            uiState = uiState,
            incomes = incomes,
            payments = payments,
            transactions = transactions,
            billOccurrences = billOccurrences,
            onboardingProgress = onboardingProgress,
            settings = settings,
            rules = transactionRules,
            assets = uiState.assets,
            goals = uiState.goals,
            categoryBudgets = uiState.categoryBudgets,
        )
    }
    val currentBackupPayload by rememberUpdatedState(backupPayload)
    val transactionImportLauncher = rememberLauncherForActivityResult(
        contract = OpenDocument(),
    ) { uri ->
        viewModel.endExternalActivity()
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        parseTransactionCsv(
                            csvText = readTextCapped(context, uri, input),
                            sourceName = "CSV file"
                        )
                    } ?: error("Unable to open CSV file.")
                }
            }
            result.onSuccess { preview ->
                transactionCsvImportPreview = preview
            }.onFailure { throwable ->
                Toast.makeText(
                    context,
                    throwable.message ?: "Unable to import CSV.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    val billImportLauncher = rememberLauncherForActivityResult(
        contract = OpenDocument(),
    ) { uri ->
        viewModel.endExternalActivity()
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        parseBillCsv(
                            csvText = readTextCapped(context, uri, input),
                            sourceName = "CSV file"
                        )
                    } ?: error("Unable to open CSV file.")
                }
            }
            result.onSuccess { preview ->
                billCsvImportPreview = preview
            }.onFailure { throwable ->
                Toast.makeText(
                    context,
                    throwable.message ?: "Unable to import bills CSV.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    val backupLauncher = rememberLauncherForActivityResult(
        contract = CreateDocument("application/json"),
    ) { uri ->
        viewModel.endExternalActivity()
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(currentBackupPayload.toByteArray())
                    } ?: error("Unable to open backup file.")
                }
            }
            Toast.makeText(
                context,
                if (result.isSuccess) "Backup saved" else "Backup failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val encryptedBackupLauncher = rememberLauncherForActivityResult(
        contract = CreateDocument("application/octet-stream"),
    ) { uri ->
        viewModel.endExternalActivity()
        if (uri == null) return@rememberLauncherForActivityResult
        pendingBackupUri = uri
        showEncryptDialog = true
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = OpenDocument(),
    ) { uri ->
        viewModel.endExternalActivity()
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        parseLedgerBackupJson(input.bufferedReader().readText())
                    } ?: error("Unable to open backup file.")
                }
            }
            result.onSuccess { snapshot ->
                restoreBackupPreview = snapshot
            }.onFailure { throwable ->
                Toast.makeText(
                    context,
                    throwable.message ?: "Unable to restore backup.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    val encryptedRestoreLauncher = rememberLauncherForActivityResult(
        contract = OpenDocument(),
    ) { uri ->
        viewModel.endExternalActivity()
        if (uri != null) {
            showDecryptDialog = uri
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                context,
                "Notifications are off. You can enable them later in system settings.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    AppBrandBackdrop(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            // TopAppBar: Color.Transparent lets the hazeEffect own the surface entirely.
            // containerColor override at 0.92 cancels the blur — must be Transparent here.
                TopAppBar(
                    title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            AppBrandMark(
                                modifier = Modifier.size(28.dp),
                                contentDescription = null,
                            )
                            Text(
                                title,
                                style = MaterialTheme.typography.titleLarge,
                                color = GlassTokens.TextPrimary,
                                modifier = Modifier.semantics { heading() },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            OnboardingProgressWidget(
                                progress = onboardingProgress,
                                onClick = {
                                    onClearSelection()
                                    when (onboardingProgress.nextActionMilestone()) {
                                        OnboardingMilestone.RECONCILIATION -> {
                                            showBankBalanceDialog = true
                                        }
                                        OnboardingMilestone.FIRST_INCOME -> {
                                            onSetAddKind(AddKind.Income)
                                        }
                                        OnboardingMilestone.FIRST_BILL -> {
                                            onSetAddKind(AddKind.Bill)
                                        }
                                        OnboardingMilestone.FIRST_GOAL -> {
                                            onSetAddKind(AddKind.Goal)
                                        }
                                        OnboardingMilestone.FIRST_EXPENSE -> {
                                            onSetAddKind(AddKind.Transaction)
                                        }
                                        null -> {
                                            onSetAddKind(null)
                                            onSetSection(AppSection.Dashboard)
                                        }
                                    }
                                }
                            )
                            IconButton(
                                onClick = { onSetShowSettings(true) }
                            ) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = "Settings",
                                    tint = GlassTokens.TextPrimary
                                )
                            }
                        }
                    }
                    },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier
                    .hazeEffect(
                        state = hazeState,
                        style = dev.chrisbanes.haze.HazeStyle(
                            backgroundColor = colorScheme.surfaceContainerLow.copy(alpha = 0.62f),
                            tint = dev.chrisbanes.haze.HazeTint(Color.Transparent),
                            blurRadius = 14.dp,
                        )
                    )
            )
        },
        bottomBar = {
            if (isCompact) {
                // NavBar: solid surface preserves glass budget for content heroes (TopAppBar keeps subtle haze).
                NavigationBar(
                    containerColor = colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .drawWithContent {
                            drawContent()
                            // 1dp top edge border separates bar from content below
                            drawRect(
                                color = GlassTokens.NavBorderTop,
                                topLeft = Offset.Zero,
                                size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()),
                            )
                        }
                ) {
                    AppSection.primaryNav.forEach { item ->
                        NavigationBarItem(
                            selected = section == item && selectedIncome == null && selectedPayment == null && selectedTransaction == null && addKind == null,
                            onClick = {
                                onClearSelection()
                                onSetAddKind(null)
                                onSetSection(item)
                            },
                            icon = { androidx.compose.material3.Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            modifier = Modifier,
                            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                selectedIconColor = GlassTokens.CyanBright,
                                selectedTextColor = GlassTokens.CyanBright,
                                indicatorColor = GlassTokens.NavIndicator,
                                unselectedIconColor = GlassTokens.TextSecondary,
                                unselectedTextColor = GlassTokens.TextSecondary
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .padding(padding)
        ) {
            when {
                showRulesScreen -> {
                    TransactionRulesScreen(
                        onDismiss = { onSetShowRulesScreen(false) },
                        viewModel = viewModel,
                        hazeState = hazeState,
                    )
                }

                showSettings -> {
                    SettingsScreen(
                        onDismiss = { onSetShowSettings(false) },
                        onImportCsv = {
                            viewModel.beginExternalActivity()
                            transactionImportLauncher.launch(csvMimeTypes)
                        },
                        onImportBills = {
                            viewModel.beginExternalActivity()
                            billImportLauncher.launch(csvMimeTypes)
                        },
                        onBackup = {
                            viewModel.beginExternalActivity()
                            backupLauncher.launch("montecarlo-ledger-backup-${LocalDateTime.now().toLocalDate()}.json")
                        },
                        onRestore = {
                            viewModel.beginExternalActivity()
                            restoreLauncher.launch(jsonMimeTypes)
                        },
                        onBackupEncrypted = {
                            viewModel.beginExternalActivity()
                            encryptedBackupLauncher.launch("montecarlo-ledger-backup-${LocalDateTime.now().toLocalDate()}.mcl")
                        },
                        onRestoreEncrypted = {
                            viewModel.beginExternalActivity()
                            encryptedRestoreLauncher.launch(encryptedBackupMimeTypes)
                        },
                        onShowReminders = { showReminderSettingsDialog = true },
                        onShowAppLock = { showAppLockSettingsDialog = true },
                        onShowPrivacy = { showPrivacyDialog = true },
                        onEraseAllData = {
                            viewModel.eraseAllData { result ->
                                val message = result.fold(
                                    onSuccess = { "All local data erased" },
                                    onFailure = { it.message ?: "Erase failed" },
                                )
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                if (result.isSuccess) {
                                    onSetShowSettings(false)
                                }
                            }
                        },
                        onShowTransactionRules = { onSetShowRulesScreen(true) },
                        appLockPreferences = appLockPreferences,
                        viewModel = viewModel,
                        hazeState = hazeState
                    )
                }

                section == AppSection.Dashboard -> DashboardScreen(
                    viewModel = viewModel,
                    onAddIncome = {
                        onSetAddKind(AddKind.Income)
                    },
                    onAddPayment = {
                        onSetAddKind(AddKind.Bill)
                    },
                    onAddTransaction = {
                        onSetAddKind(AddKind.Transaction)
                    },
                    onAddGoal = {
                        onSetAddKind(AddKind.Goal)
                    },
                    onCheckBalance = {
                        showBankBalanceDialog = true
                    },
                    onOpenAnalysis = {
                        onSetSection(AppSection.Analysis)
                    },
                    onOpenReview = {
                        onSetSection(AppSection.Review)
                    },
                    onOpenDebtPayoff = {
                        onSetSection(AppSection.DebtPayoff)
                    },
                    onEditTransaction = {
                        onSelectTransaction(it)
                    },
                    hazeState = hazeState
                )

                section == AppSection.Ledger -> LedgerScreen(
                    viewModel = viewModel,
                    onEditTransaction = { onSelectTransaction(it) },
                    onEditIncome = { onSelectIncome(it) },
                    onEditPayment = { onSelectPayment(it) },
                    onAddIncome = {
                        onSetAddKind(AddKind.Income)
                    },
                    onAddPayment = {
                        onSetAddKind(AddKind.Bill)
                    }
                )

                section == AppSection.Planning -> PlanningScreen(viewModel)

                section == AppSection.Review -> ReviewCommandCenterScreen(
                    viewModel = viewModel,
                    onEditTransaction = { onSelectTransaction(it) },
                    onTrackAsBill = onTrackAsBill,
                )

                section == AppSection.Analysis -> AnalysisScreen(
                    viewModel,
                    hazeState = hazeState,
                    onTrackAsBill = onTrackAsBill,
                )

                section == AppSection.DebtPayoff -> {
                    val debtItems = debts.filter { it.isActive }.map {
                        com.montecarlo.ledger.processing.DebtItem(
                            id = it.id,
                            name = it.name,
                            balanceCents = it.balanceCents,
                            aprBasisPoints = it.aprBasisPoints,
                            minPaymentCents = it.minimumPaymentCents,
                            dueDayOfMonth = it.dueDayOfMonth,
                            linkedPaymentId = it.linkedPaymentId,
                            kind = it.kind,
                            minPaymentPercentBps = it.minPaymentPercentBps,
                            minPaymentFloorCents = it.minPaymentFloorCents,
                        )
                    }
                    val events = com.montecarlo.ledger.processing.TimelineService.generateTimeline(incomes, payments, java.time.LocalDate.now(), 90, billOccurrences)
                    Column(modifier = Modifier.fillMaxSize()) {
                        DebtManagementScreen(
                            debts = debts,
                            payments = payments,
                            onAdd = viewModel::addDebt,
                            onUpdate = viewModel::updateDebt,
                            onDelete = viewModel::deleteDebt,
                            modifier = Modifier.weight(1f),
                            accounts = accounts,
                        )
                        if (debtItems.isNotEmpty()) {
                            DebtPayoffScreen(
                                debts = debtItems,
                                currentBalanceCents = uiState.ledgerBalanceCents,
                                forecastEvents = events,
                                modifier = Modifier.weight(2f),
                            )
                        }
                    }
                }
            }
        }
    }

    EditSheets(
        selectedIncome = selectedIncome,
        selectedPayment = selectedPayment,
        selectedTransaction = selectedTransaction,
        onSelectIncome = onSelectIncome,
        onSelectPayment = onSelectPayment,
        onSelectTransaction = onSelectTransaction,
        viewModel = viewModel,
        handleResult = { result, onSuccessAction, showSuccess ->
            handlePersistenceResult(result, onSuccessAction, showSuccess)
        },
    )

    AddKindSheet(
        addKind = addKind,
        pendingBillPrefill = pendingBillPrefill,
        payments = payments,
        billOccurrences = billOccurrences,
        dashboardErrorMessage = dashboardErrorMessage,
        onSetAddKind = onSetAddKind,
        onClearBillPrefill = onClearBillPrefill,
        onShowAddAnotherPrompt = { showAddAnotherBillDialog = true },
        onTransactionSaved = { showSuccessToast = true },
        viewModel = viewModel,
        handleResult = { result, onSuccessAction, showSuccess ->
            handlePersistenceResult(result, onSuccessAction, showSuccess)
        },
    )

    ImportCsvSheets(
        transactionPreview = transactionCsvImportPreview,
        billPreview = billCsvImportPreview,
        onDismissTransaction = { transactionCsvImportPreview = null },
        onDismissBill = { billCsvImportPreview = null },
        viewModel = viewModel,
    )
    restoreBackupPreview?.let { preview ->
        RestoreBackupDialog(
            snapshot = preview,
            onDismiss = { restoreBackupPreview = null },
            onRestore = {
                viewModel.restoreBackup(preview) { result ->
                    val message = result.fold(
                        onSuccess = { "Backup restored" },
                        onFailure = { it.message ?: "Restore failed" },
                    )
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    restoreBackupPreview = null
                }
            }
        )
    }

    if (showReminderSettingsDialog) {
        ReminderSettingsDialog(
            preferences = reminderPreferences,
            onDismiss = { showReminderSettingsDialog = false },
            onSave = { updated ->
                viewModel.updateReminderPreferences(updated)
                showReminderSettingsDialog = false
                if (
                    updated.enabled &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )
    }

    if (showPrivacyDialog) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyDialog = false })
    }

    if (showAppLockSettingsDialog) {
        AppLockSettingsDialog(
            preferences = appLockPreferences,
            onDismiss = { showAppLockSettingsDialog = false },
            onEnable = { pin ->
                viewModel.enableAppLock(pin) { ok, message ->
                    if (ok) {
                        showAppLockSettingsDialog = false
                        showSuccessToast = true
                    } else {
                        Toast.makeText(context, message ?: "Unable to enable app lock.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDisable = {
                viewModel.disableAppLock { ok, message ->
                    if (ok) {
                        showAppLockSettingsDialog = false
                        showSuccessToast = true
                    } else {
                        Toast.makeText(context, message ?: "Unable to disable app lock.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onLockNow = {
                showAppLockSettingsDialog = false
                viewModel.lockApp()
            },
        )
    }

    BankBalanceSheet(
        bankBalanceCents = uiState.bankBalanceCents,
        onDismiss = { showBankBalanceDialog = false },
        viewModel = viewModel,
        handleResult = { result, onSuccessAction, showSuccess ->
            handlePersistenceResult(result, onSuccessAction, showSuccess)
        },
    )
    if (showAddAnotherBillDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddAnotherBillDialog = false
                showSuccessToast = true
            },
            title = { Text("Bill saved") },
            text = { Text("Do you want to add another bill? Most people have several — rent, utilities, subscriptions.") },
            confirmButton = {
                Button(onClick = {
                    showAddAnotherBillDialog = false
                    onSetAddKind(AddKind.Bill)
                }) { Text("Add another") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddAnotherBillDialog = false
                    showSuccessToast = true
                }) { Text("Done") }
            }
        )
    }

    LaunchedEffect(showSuccessToast) {
        if (showSuccessToast) {
            kotlinx.coroutines.delay(2000)
            showSuccessToast = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedVisibility(
            visible = showSuccessToast,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp)
        ) {
            GlassCard(
                tint = GlassTint.Teal,
                surfaceStyle = GlassSurfaceStyle.Standard,
                cornerRadius = 24.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = GlassTokens.PositiveGreen
                    )
                    Text("Success", color = GlassTokens.TextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showEncryptDialog && pendingBackupUri != null) {
        PasswordDialog(
            title = "Encrypted Backup Password",
            description = "Set a password to encrypt your ledger backup. You will need this to restore the file.",
            onDismiss = { 
                showEncryptDialog = false
                pendingBackupUri = null
            },
            onConfirm = { password ->
                scope.launch {
                    val encrypted = withContext(Dispatchers.IO) {
                        runCatching {
                            com.montecarlo.ledger.security.SecurityUtils.encryptWithHmac(currentBackupPayload, password.toCharArray())
                        }.getOrNull()
                    }
                    if (encrypted != null) {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(pendingBackupUri!!)?.use { output ->
                                output.write(encrypted.toByteArray())
                            }
                        }
                        showSuccessToast = true
                    }
                    showEncryptDialog = false
                    pendingBackupUri = null
                }
            }
        )
    }

    if (showDecryptDialog != null) {
        PasswordDialog(
            title = "Enter Backup Password",
            description = "This file is encrypted. Enter the password used during export.",
            onDismiss = { showDecryptDialog = null },
            onConfirm = { password ->
                scope.launch {
                    val decryptResult = withContext(Dispatchers.IO) {
                        runCatching {
                            val encryptedText = context.contentResolver.openInputStream(showDecryptDialog!!)
                                ?.use { it.bufferedReader().readText() }
                                ?: error("Unable to read backup file.")
                            val decrypted = com.montecarlo.ledger.security.SecurityUtils.decrypt(encryptedText, password.toCharArray())
                            val integrityResult = com.montecarlo.ledger.security.SecurityUtils.verifyIntegrity(
                                encryptedText, decrypted, password.toCharArray()
                            )
                            Pair(decrypted, integrityResult)
                        }
                    }
                    decryptResult.fold(
                        onSuccess = { (_, integrityResult) ->
                            when (integrityResult) {
                                is com.montecarlo.ledger.security.BackupIntegrityResult.Valid -> {
                                    val snapshot = runCatching { parseLedgerBackupJson(integrityResult.plaintext) }.getOrNull()
                                    if (snapshot != null) {
                                        restoreBackupPreview = snapshot
                                    } else {
                                        Toast.makeText(context, "Backup file is corrupted or unsupported.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                is com.montecarlo.ledger.security.BackupIntegrityResult.LegacyNoIntegrity -> {
                                    val snapshot = runCatching { parseLedgerBackupJson(integrityResult.plaintext) }.getOrNull()
                                    if (snapshot != null) {
                                        restoreBackupPreview = snapshot
                                        Toast.makeText(
                                            context,
                                            "Backup loaded. No integrity signature found — this backup was created by an older version of the app.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        Toast.makeText(context, "Backup file is corrupted or unsupported.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                is com.montecarlo.ledger.security.BackupIntegrityResult.IntegrityFailure -> {
                                    Toast.makeText(
                                        context,
                                        integrityResult.message,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onFailure = { error ->
                            val message = when (error) {
                                is javax.crypto.AEADBadTagException,
                                is javax.crypto.BadPaddingException -> "Incorrect password."
                                is IllegalArgumentException -> error.message ?: "Invalid encrypted backup."
                                else -> "Incorrect password or corrupted file."
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        },
                    )
                    showDecryptDialog = null
                }
            }
        )
    }
}

/** Cap for CSV reads so a hostile or mis-exported file cannot OOM the parser. */
private const val MAX_CSV_BYTES = 20 * 1024 * 1024

/**
 * Reads an imported document up to [MAX_CSV_BYTES]. Uses the declared document
 * size as a cheap pre-check and then a byte cap on the stream so a lying size
 * cannot slip a giant payload through.
 */
private fun readTextCapped(context: android.content.Context, uri: Uri, input: java.io.InputStream): String {
    val declaredSize = context.contentResolver.query(
        uri, arrayOf(OpenableColumns.SIZE), null, null, null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else -1L
        } else {
            -1L
        }
    } ?: -1L
    if (declaredSize > MAX_CSV_BYTES) {
        throw IllegalArgumentException(
            "CSV file is too large (over ${MAX_CSV_BYTES / (1024 * 1024)} MB). Split it into smaller files and try again."
        )
    }
    val reader = input.bufferedReader()
    val output = StringBuilder()
    val buffer = CharArray(16 * 1024)
    var totalChars = 0
    while (true) {
        val read = reader.read(buffer)
        if (read < 0) break
        totalChars += read
        if (totalChars > MAX_CSV_BYTES) {
            throw IllegalArgumentException(
                "CSV file is too large (over ${MAX_CSV_BYTES / (1024 * 1024)} MB). Split it into smaller files and try again."
            )
        }
        output.append(buffer, 0, read)
    }
    return output.toString()
}

