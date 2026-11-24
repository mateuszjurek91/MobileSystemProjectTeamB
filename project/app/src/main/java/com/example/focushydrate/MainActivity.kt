package com.example.focushydrate

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
    val accounts = remember { LocalAccounts(PreferencesAccountStorage(context.applicationContext)) }
    var profile by remember { mutableStateOf<AccountProfile?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(attempt) {
        loading = true; loadError = false
        try { profile = withContext(Dispatchers.IO) { accounts.current() } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { loadError = true }
        finally { loading = false }
    }
    LaunchedEffect(loading, profile?.id) {
        if (!loading) withContext(Dispatchers.IO) { DrinkReminders.refresh(context.applicationContext) }
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
                Button(onClick = { attempt++ }) { Text(stringResource(R.string.retry)) }
            }
            profile == null -> AuthScreen(accounts, language, onLanguage) { profile = it }
            else -> profile?.let { active ->
                key(active.id) { AccountHome(active, accounts, language, onLanguage, trackerRequest) { profile = it } }
            }
        }
    }
}

@Composable
private fun AccountHome(profile: AccountProfile, accounts: LocalAccounts, language: String,
                        onLanguage: (String) -> Unit, trackerRequest: Int, onProfile: (AccountProfile?) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val store = remember { HydrationStore(context.applicationContext) }
    var history by remember { mutableStateOf<List<DailyIntake>>(emptyList()) }
    var goal by remember { mutableIntStateOf(2000) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var currentScreen by rememberSaveable { mutableStateOf(Screen.Tracker) }
    var statistics by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(trackerRequest) {
        if (trackerRequest > 0) {
            currentScreen = Screen.Tracker
            statistics = false
        }
    }
    var today by remember { mutableStateOf(Days.key()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(profile.id, retry) {
        loaded = false; error = null
        try {
            history = withContext(Dispatchers.IO) { store.load(profile.id) }
            goal = withContext(Dispatchers.IO) { store.loadGoal(profile.id) }
            loaded = true
        }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = resources.getString(R.string.error_load) }
    }
    LaunchedEffect(Unit) { while (isActive) { today = Days.key(); delay(15_000) } }
    fun changeWater(delta: Int?, reset: Boolean = false) {
        if (busy || !loaded) return
        busy = true
        scope.launch {
            try {
                val day = Days.key(); today = day
                val previous = history.find { it.day == day }?.millilitres ?: 0
                val next = logWater(history, day, if (reset) -previous else delta ?: 0, goal)
                val drinkAt = if (!reset && (delta ?: 0) > 0) System.currentTimeMillis() else null
                val saved = withContext(Dispatchers.IO) { store.save(profile.id, next, drinkAt) }
                if (saved) {
                    history = next
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
                else Button(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
            }
        } else Box(Modifier.weight(1f).fillMaxWidth()) {
            val intake = history.find { it.day == today }?.millilitres ?: 0
            when (currentScreen) {
                Screen.Map -> MapScreen(profile)
                Screen.Tracker -> if (statistics) StatisticsScreen(history, today) { statistics = false }
                else TrackerScreen(intake, goal, { changeWater(250) }, { changeWater(-250) },
                    { changeWater(null, reset = true) }, { statistics = true }, busy)
                Screen.Settings -> SettingsScreen(goal, profile, accounts, language, onLanguage, onProfile,
                    onGoalChange = { changeGoal(it) },
                    onDelete = { password ->
                        if (!accounts.verifyPassword(profile.id, password)) AccountResult.Failure(AccountIssue.CURRENT_PASSWORD)
                        else if (!store.delete(profile.id) || !WaterSpotStore(context.applicationContext).deleteAccountData(profile.id) ||
                            !ReminderStore(context.applicationContext).delete(profile.id))
                            AccountResult.Failure(AccountIssue.STORAGE)
                        else accounts.delete(profile.id, password)
                    }, onResetData = { changeWater(null, reset = true) }, dataBusy = busy)
            }
        }
        HydrationNavigation(currentScreen) {
            if (it == Screen.Tracker) statistics = false
            currentScreen = it
        }
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
                label = { Text(stringResource(screen.titleRes)) }
            )
        }
    }
}