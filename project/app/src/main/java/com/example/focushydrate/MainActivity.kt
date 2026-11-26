package com.example.focushydrate

import com.example.focushydrate.ui.theme.PrimaryButton
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.focushydrate.data.*
import com.example.focushydrate.localization.AppLanguage
import com.example.focushydrate.reminders.DrinkReminders
import com.example.focushydrate.reminders.ReminderStore
import com.example.focushydrate.ui.navigation.Screen
import com.example.focushydrate.ui.screens.*
import com.example.focushydrate.ui.theme.FocusHydrateTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var trackerRequest by mutableIntStateOf(0)
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("open_tracker", false)) {
            trackerRequest++
            intent.removeExtra("open_tracker")
        }
    }
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(AppLanguage.wrap(newBase)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent.getBooleanExtra("open_tracker", false)) {
            trackerRequest++
            intent.removeExtra("open_tracker")
        }
        setContent { FocusHydrateTheme { Surface { HydrationApp(trackerRequest) } } }
    }
}

@Composable
fun HydrationApp(trackerRequest: Int = 0) {
    val context = LocalContext.current
    val accounts = remember { LocalAccounts(FirestoreAccountStorage(context.applicationContext)) }
    val launchPrefs = remember {
        context.applicationContext.getSharedPreferences("app_launch_prefs", Context.MODE_PRIVATE)
    }
    var profile by remember { mutableStateOf<AccountProfile?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var notificationPromptFinished by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPromptFinished = true
        if (granted) {
            launchPrefs.edit().putBoolean("auto_enable_reminders", true).apply()
            scope.launch(Dispatchers.IO) {
                val activeId = profile?.id
                if (activeId != null) {
                    val reminderStore = ReminderStore(context.applicationContext)
                    val currentPlan = reminderStore.load(activeId)
                    if (!currentPlan.enabled && currentPlan.enabledAt == 0L) {
                        DrinkReminders.update(
                            context.applicationContext,
                            activeId,
                            currentPlan.copy(enabled = true, enabledAt = System.currentTimeMillis())
                        )
                    }
                    launchPrefs.edit().putBoolean("auto_enable_reminders", false).apply()
                } else {
                    DrinkReminders.refresh(context.applicationContext)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            DrinkReminders.createChannel(context.applicationContext)
        }
        val alreadyAsked = launchPrefs.getBoolean("asked_notification_permission", false)
        if (!alreadyAsked) {
            launchPrefs.edit().putBoolean("asked_notification_permission", true).apply()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                notificationPromptFinished = true
            }
        } else {
            notificationPromptFinished = true
        }
    }

    LaunchedEffect(attempt) {
        loading = true; loadError = false
        try { profile = withContext(Dispatchers.IO) { accounts.current() } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { loadError = true }
        finally { loading = false }
    }

    LaunchedEffect(loading, profile?.id) {
        if (!loading) {
            withContext(Dispatchers.IO) {
                val activeId = profile?.id
                if (activeId != null &&
                    launchPrefs.getBoolean("auto_enable_reminders", false) &&
                    DrinkReminders.allowed(context.applicationContext)
                ) {
                    val reminderStore = ReminderStore(context.applicationContext)
                    val currentPlan = reminderStore.load(activeId)
                    if (!currentPlan.enabled && currentPlan.enabledAt == 0L) {
                        DrinkReminders.update(
                            context.applicationContext,
                            activeId,
                            currentPlan.copy(enabled = true, enabledAt = System.currentTimeMillis())
                        )
                    }
                    launchPrefs.edit().putBoolean("auto_enable_reminders", false).apply()
                }
                DrinkReminders.refresh(context.applicationContext)
            }
        }
    }

    val language = AppLanguage.current(context)
    val onLanguage: (String) -> Unit = { next ->
        if (next != language) scope.launch {
            if (withContext(Dispatchers.IO) { AppLanguage.set(context, next) }) (context as? MainActivity)?.recreate()
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            loadError -> Column(Modifier.align(Alignment.Center).padding(24.dp)) {
                Text(stringResource(R.string.error_load))
                PrimaryButton(onClick = { attempt++ }) { Text(stringResource(R.string.retry)) }
            }
            profile == null -> AuthScreen(accounts, language, onLanguage) { profile = it }
            else -> profile?.let { active ->
                key(active.id) {
                    AccountHome(
                        profile = active,
                        accounts = accounts,
                        language = language,
                        onLanguage = onLanguage,
                        trackerRequest = trackerRequest,
                        canPromptMetrics = notificationPromptFinished,
                        onProfile = { profile = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountHome(
    profile: AccountProfile,
    accounts: LocalAccounts,
    language: String,
    onLanguage: (String) -> Unit,
    trackerRequest: Int,
    canPromptMetrics: Boolean,
    onProfile: (AccountProfile?) -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val store = remember { HydrationStore(context.applicationContext) }
    val prefs = remember { context.applicationContext.getSharedPreferences("focushydrate_ui_prefs", Context.MODE_PRIVATE) }
    var history by remember { mutableStateOf<List<DailyIntake>>(emptyList()) }
    var goal by remember { mutableIntStateOf(2000) }
    var defaultAmount by remember {
        mutableIntStateOf(prefs.getInt("default_amount_${profile.id}", 250).coerceIn(1, 5000))
    }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var currentScreen by rememberSaveable { mutableStateOf(Screen.Tracker) }
    var statistics by rememberSaveable { mutableStateOf(false) }
    var undoStack by remember { mutableStateOf<List<Int>>(emptyList()) }
    var showBodyMetricsDialog by rememberSaveable(profile.id) { mutableStateOf(false) }

    val isPolish = resources.configuration.locales[0].language.equals("pl", ignoreCase = true)

    LaunchedEffect(trackerRequest) {
        if (trackerRequest > 0) {
            currentScreen = Screen.Tracker
            statistics = false
        }
    }
    var today by remember { mutableStateOf(Days.key()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(profile.id, retry) {
        loaded = false; error = null; undoStack = emptyList()
        try {
            history = withContext(Dispatchers.IO) { store.load(profile.id) }
            goal = withContext(Dispatchers.IO) { store.loadGoal(profile.id) }
            loaded = true
        }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = resources.getString(R.string.error_load) }
    }

    // Oddzielne pytanie o wzrost i wagę po pierwszym zalogowaniu/uruchomieniu
    LaunchedEffect(loaded, canPromptMetrics, profile.id) {
        if (loaded && canPromptMetrics && !prefs.getBoolean("asked_body_metrics_${profile.id}", false)) {
            showBodyMetricsDialog = true
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            val currentDay = Days.key()
            if (currentDay != today) {
                today = currentDay
                undoStack = emptyList()
            }
            delay(15_000)
        }
    }
    fun updateDefaultAmount(newAmount: Int) {
        val valid = newAmount.coerceIn(1, 5000)
        defaultAmount = valid
        prefs.edit().putInt("default_amount_${profile.id}", valid).apply()
    }
    fun addWater(amount: Int) {
        if (busy || !loaded || amount <= 0) return
        busy = true
        scope.launch {
            try {
                val day = Days.key()
                if (day != today) {
                    today = day
                    undoStack = emptyList()
                }
                val next = logWater(history, day, amount, goal)
                val saved = withContext(Dispatchers.IO) { store.save(profile.id, next, System.currentTimeMillis()) }
                if (saved) {
                    history = next
                    undoStack = undoStack + amount
                    error = null
                    withContext(Dispatchers.IO) { DrinkReminders.refresh(context.applicationContext) }
                } else {
                    error = resources.getString(R.string.error_storage)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = resources.getString(R.string.error_storage) }
            finally { busy = false }
        }
    }
    fun undoLastWater() {
        val lastAmount = undoStack.lastOrNull() ?: return
        if (busy || !loaded) return
        busy = true
        scope.launch {
            try {
                val day = Days.key()
                if (day != today) {
                    today = day
                    undoStack = emptyList()
                    return@launch
                }
                val previous = history.find { it.day == day }?.millilitres ?: 0
                val toRemove = minOf(lastAmount, previous)
                val next = logWater(history, day, -toRemove, goal)
                val saved = withContext(Dispatchers.IO) { store.save(profile.id, next, null) }
                if (saved) {
                    history = next
                    undoStack = undoStack.dropLast(1)
                    error = null
                    withContext(Dispatchers.IO) { DrinkReminders.refresh(context.applicationContext) }
                } else {
                    error = resources.getString(R.string.error_storage)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = resources.getString(R.string.error_storage) }
            finally { busy = false }
        }
    }
    fun resetTodayWater() {
        if (busy || !loaded) return
        busy = true
        scope.launch {
            try {
                val day = Days.key(); today = day
                val previous = history.find { it.day == day }?.millilitres ?: 0
                val next = logWater(history, day, -previous, goal)
                val saved = withContext(Dispatchers.IO) { store.save(profile.id, next, null) }
                if (saved) {
                    history = next
                    undoStack = emptyList()
                    error = null
                    withContext(Dispatchers.IO) { DrinkReminders.refresh(context.applicationContext) }
                } else {
                    error = resources.getString(R.string.error_storage)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = resources.getString(R.string.error_storage) }
            finally { busy = false }
        }
    }
    fun changeGoal(newGoal: Int) {
        if (busy || !loaded || newGoal == goal) return
        busy = true
        scope.launch {
            try {
                val day = Days.key(); today = day
                val updatedHistory = updateGoalForDay(history, day, newGoal)
                val saved = withContext(Dispatchers.IO) {
                    val goalSaved = store.saveGoal(profile.id, newGoal)
                    val historySaved = if (updatedHistory != history) store.save(profile.id, updatedHistory, null) else true
                    goalSaved && historySaved
                }
                if (saved) {
                    goal = newGoal
                    history = updatedHistory
                    error = null
                } else {
                    error = resources.getString(R.string.error_storage)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = resources.getString(R.string.error_storage) }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize()) {
        error?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
        if (!loaded) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (error == null) CircularProgressIndicator()
                else PrimaryButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
            }
        } else Box(Modifier.weight(1f).fillMaxWidth()) {
            val intake = history.find { it.day == today }?.millilitres ?: 0
            when (currentScreen) {
                Screen.Map -> MapScreen(profile)
                Screen.Tracker -> if (statistics) StatisticsScreen(history, today) { statistics = false }
                else TrackerScreen(
                    intake = intake,
                    goal = goal,
                    defaultAmount = defaultAmount,
                    canUndo = undoStack.isNotEmpty() && intake > 0,
                    lastAddedAmount = undoStack.lastOrNull(),
                    onAddWater = { amount -> addWater(amount) },
                    onDefaultAmountChange = { updateDefaultAmount(it) },
                    onUndoWater = { undoLastWater() },
                    onResetData = { resetTodayWater() },
                    onStatistics = { statistics = true },
                    busy = busy
                )
                Screen.Settings -> SettingsScreen(goal, profile, accounts, language, onLanguage, onProfile,
                    onGoalChange = { changeGoal(it) },
                    onDelete = { password ->
                        if (!accounts.verifyPassword(profile.id, password)) AccountResult.Failure(AccountIssue.CURRENT_PASSWORD)
                        else if (!store.delete(profile.id) ||
                            !WaterSpotStore(context.applicationContext).deleteAccountData(profile.id) ||
                            !FirestoreWaterSpotStore().deleteAccountDataBlocking(profile.id) ||
                            !ReminderStore(context.applicationContext).delete(profile.id))
                            AccountResult.Failure(AccountIssue.STORAGE)
                        else {
                            prefs.edit()
                                .remove("default_amount_${profile.id}")
                                .remove("asked_body_metrics_${profile.id}")
                                .remove("weight_${profile.id}")
                                .remove("height_${profile.id}")
                                .apply()
                            accounts.delete(profile.id, password)
                        }
                    }, onResetData = { resetTodayWater() }, dataBusy = busy)
            }
        }
        HydrationNavigation(currentScreen) {
            if (it == Screen.Tracker) statistics = false
            currentScreen = it
        }
    }

    if (showBodyMetricsDialog) {
        var weightText by rememberSaveable { mutableStateOf("") }
        var heightText by rememberSaveable { mutableStateOf("") }
        var activity by rememberSaveable { mutableStateOf(ActivityLevel.MEDIUM) }

        val validWeight = weightText.toIntOrNull()?.takeIf { it in 35..200 }
        val validHeight = heightText.toIntOrNull()?.takeIf { it in 120..230 }
        val calculatedGoal = if (validWeight != null && validHeight != null) {
            calculateWaterGoal(validWeight, validHeight, activity)
        } else {
            null
        }

        fun dismissMetricsPrompt() {
            prefs.edit().putBoolean("asked_body_metrics_${profile.id}", true).apply()
            showBodyMetricsDialog = false
        }

        AlertDialog(
            onDismissRequest = { dismissMetricsPrompt() },
            title = {
                Text(
                    if (isPolish) "Dopasuj dzienny cel wody"
                    else "Personalize your daily water goal"
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isPolish) {
                            "Podaj swoją wagę i wzrost, aby obliczyć zalecaną ilość wody na dzień."
                        } else {
                            "Enter your weight and height to calculate your recommended daily water intake."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it.filter(Char::isDigit).take(3) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = inputColors(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.goal_weight_kg)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { heightText = it.filter(Char::isDigit).take(3) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = inputColors(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.goal_height_cm)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Text(
                        text = stringResource(R.string.goal_activity),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActivityLevel.entries.forEach { level ->
                            val labelRes = when (level) {
                                ActivityLevel.LOW -> R.string.activity_low
                                ActivityLevel.MEDIUM -> R.string.activity_medium
                                ActivityLevel.HIGH -> R.string.activity_high
                            }
                            FilterChip(
                                selected = activity == level,
                                onClick = { activity = level },
                                label = { Text(stringResource(labelRes)) }
                            )
                        }
                    }
                    if (calculatedGoal != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isPolish) {
                                    "Sugerowany cel dzienny: $calculatedGoal ml"
                                } else {
                                    "Suggested daily goal: $calculatedGoal ml"
                                },
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    } else if (weightText.isNotEmpty() || heightText.isNotEmpty()) {
                        Text(
                            text = if (isPolish) {
                                "Wpisz poprawną wagę (35–200 kg) i wzrost (120–230 cm)."
                            } else {
                                "Enter a valid weight (35–200 kg) and height (120–230 cm)."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = calculatedGoal != null && !busy,
                    onClick = {
                        val nextGoal = calculatedGoal ?: return@TextButton
                        prefs.edit()
                            .putBoolean("asked_body_metrics_${profile.id}", true)
                            .putInt("weight_${profile.id}", validWeight!!)
                            .putInt("height_${profile.id}", validHeight!!)
                            .apply()
                        showBodyMetricsDialog = false
                        changeGoal(nextGoal)
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissMetricsPrompt() }) {
                    Text(if (isPolish) "Pomiń" else "Skip")
                }
            }
        )
    }
}

@Composable
private fun HydrationNavigation(currentScreen: Screen, onSelect: (Screen) -> Unit) {
    androidx.compose.material3.NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Screen.entries.forEach { screen ->
            NavigationBarItem(
                selected = currentScreen == screen,
                onClick = { onSelect(screen) },
                icon = { Icon(screen.icon, contentDescription = null) },
                label = { Text(stringResource(screen.titleRes)) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}