package com.example.focushydrate.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.focushydrate.R
import com.example.focushydrate.data.HydrationStore
import com.example.focushydrate.reminders.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettings(accountId: String, enabled: Boolean = true) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val store = remember { ReminderStore(context.applicationContext) }
    val hydrationStore = remember { HydrationStore(context.applicationContext) }

    var plan by remember(accountId) { mutableStateOf<ReminderPlan?>(null) }
    var lastDrinkAt by remember(accountId) { mutableLongStateOf(0L) }
    var busy by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var statusNotice by remember { mutableStateOf<String?>(null) }
    var permitted by remember { mutableStateOf(DrinkReminders.allowed(context)) }
    var pickingStart by remember { mutableStateOf<Boolean?>(null) }

    val locale = resources.configuration.locales[0]
    val isPolish = locale.language.equals("pl", ignoreCase = true)

    fun refreshState() {
        scope.launch {
            withContext(Dispatchers.IO) {
                DrinkReminders.refresh(context.applicationContext)
                lastDrinkAt = hydrationStore.lastDrink(accountId)
            }
            permitted = DrinkReminders.allowed(context)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshState() }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshState()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(accountId, attempt) {
        try {
            val loaded = withContext(Dispatchers.IO) { store.load(accountId) }
            lastDrinkAt = withContext(Dispatchers.IO) { hydrationStore.lastDrink(accountId) }
            plan = loaded.copy(weekdays = (1..7).toSet())
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = resources.getString(R.string.error_load)
        }
    }

    fun applyPlan(next: ReminderPlan, askPermissionIfNeeded: Boolean = false) {
        if (busy) return
        val normalized = next.copy(weekdays = (1..7).toSet())
        busy = true
        error = null
        statusNotice = null
        scope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    DrinkReminders.update(context.applicationContext, accountId, normalized)
                }
                if (ok) {
                    plan = normalized
                    permitted = DrinkReminders.allowed(context)
                    if (askPermissionIfNeeded && normalized.enabled && !permitted && Build.VERSION.SDK_INT >= 33) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    error = resources.getString(R.string.error_storage)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = resources.getString(R.string.error_storage)
            } finally {
                busy = false
            }
        }
    }

    Card(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val current = plan
            if (current == null) {
                Text(stringResource(R.string.drink_reminders), style = MaterialTheme.typography.titleMedium)
                if (error == null) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = { error = null; attempt++ }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            } else {
                val editable = enabled && !busy
                val nextTimestamp = remember(current, lastDrinkAt, permitted) {
                    if (!current.enabled || !permitted) null
                    else nextReminder(current, System.currentTimeMillis(), lastDrinkAt)
                }

                // Nagłówek + główny przełącznik
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .toggleable(
                            value = current.enabled,
                            enabled = editable,
                            role = Role.Switch,
                            onValueChange = { turnOn ->
                                val next = current.copy(
                                    enabled = turnOn,
                                    enabledAt = if (turnOn && !current.enabled) System.currentTimeMillis() else current.enabledAt
                                )
                                applyPlan(next, askPermissionIfNeeded = turnOn && !current.enabled)
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (current.enabled) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                        contentDescription = null,
                        tint = if (current.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.drink_reminders),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = formatNextStatus(nextTimestamp, current.enabled, permitted, isPolish, locale),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = current.enabled, onCheckedChange = null, enabled = editable)
                }

                AnimatedVisibility(visible = current.enabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (!permitted) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.reminders_blocked),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    TextButton(
                                        enabled = editable,
                                        contentPadding = PaddingValues(0.dp),
                                        onClick = {
                                            val intent = if (Build.VERSION.SDK_INT >= 26) {
                                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            } else {
                                                Intent(
                                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    Uri.parse("package:${context.packageName}")
                                                )
                                            }
                                            runCatching { context.startActivity(intent) }
                                                .onFailure { error = resources.getString(R.string.error_storage) }
                                        }
                                    ) {
                                        Text(stringResource(R.string.reminder_system_settings))
                                    }
                                }
                            }
                        }

                        // 1. Częstotliwość z wyraźnym wyróżnieniem aktywnego interwału
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.reminder_interval),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(30, 60, 120, 180).forEach { minutes ->
                                    val selected = current.intervalMinutes == minutes
                                    FilterChip(
                                        selected = selected,
                                        enabled = editable,
                                        onClick = {
                                            if (!selected) applyPlan(current.copy(intervalMinutes = minutes))
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            labelColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        label = {
                                            Text(
                                                text = formatIntervalLabel(minutes, isPolish),
                                                modifier = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Center,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        // 2. Zakres godzin
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TimeBox(
                                label = stringResource(R.string.reminder_start),
                                minutes = current.startMinute,
                                enabled = editable,
                                modifier = Modifier.weight(1f),
                                onClick = { pickingStart = true }
                            )
                            TimeBox(
                                label = stringResource(R.string.reminder_end),
                                minutes = current.endMinute,
                                enabled = editable,
                                modifier = Modifier.weight(1f),
                                onClick = { pickingStart = false }
                            )
                        }

                        // 3. Przycisk testu powiadomień
                        OutlinedButton(
                            enabled = editable && permitted,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val sent = DrinkReminders.sendTest(context.applicationContext)
                                statusNotice = if (sent) {
                                    if (isPolish) "Wysłano powiadomienie testowe." else "Test notification sent."
                                } else {
                                    resources.getString(R.string.error_storage)
                                }
                            }
                        ) {
                            Text(if (isPolish) "Wyślij testowe powiadomienie" else "Send test notification")
                        }
                    }
                }

                statusNotice?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    // Dialog zegara (TimePicker)
    val isStartDialog = pickingStart
    val currentPlan = plan
    if (isStartDialog != null && currentPlan != null) {
        val initialMinutes = if (isStartDialog) currentPlan.startMinute else (currentPlan.endMinute % 1440)
        val timeState = rememberTimePickerState(
            initialHour = initialMinutes / 60,
            initialMinute = initialMinutes % 60,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { pickingStart = null },
            title = {
                Text(
                    stringResource(
                        if (isStartDialog) R.string.reminder_start else R.string.reminder_end
                    )
                )
            },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val picked = timeState.hour * 60 + timeState.minute
                    val nextPlan = if (isStartDialog) {
                        val safeStart = picked.coerceIn(0, 1438)
                        val safeEnd = if (currentPlan.endMinute <= safeStart) {
                            (safeStart + 60).coerceAtMost(1440)
                        } else {
                            currentPlan.endMinute
                        }
                        currentPlan.copy(startMinute = safeStart, endMinute = safeEnd)
                    } else {
                        val normalizedEnd = if (picked == 0) 1440 else picked
                        val safeEnd = normalizedEnd.coerceIn(1, 1440)
                        val safeStart = if (currentPlan.startMinute >= safeEnd) {
                            (safeEnd - 60).coerceAtLeast(0)
                        } else {
                            currentPlan.startMinute
                        }
                        currentPlan.copy(startMinute = safeStart, endMinute = safeEnd)
                    }
                    pickingStart = null
                    applyPlan(nextPlan)
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { pickingStart = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun TimeBox(
    label: String,
    minutes: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatMinuteOfDay(minutes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(
                imageVector = Icons.Outlined.AccessTime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun formatMinuteOfDay(minutes: Int): String =
    if (minutes >= 1440) "24:00"
    else String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)

private fun formatIntervalLabel(minutes: Int, isPolish: Boolean): String = when (minutes) {
    30 -> "30 min"
    60 -> if (isPolish) "1 godz." else "1 hr"
    120 -> if (isPolish) "2 godz." else "2 hrs"
    180 -> if (isPolish) "3 godz." else "3 hrs"
    else -> "$minutes min"
}

private fun formatNextStatus(
    nextMillis: Long?,
    enabled: Boolean,
    permitted: Boolean,
    isPolish: Boolean,
    locale: Locale
): String {
    if (!enabled) return if (isPolish) "Wyłączone" else "Off"
    if (!permitted) return if (isPolish) "Wymaga zgody na powiadomienia" else "Notification permission required"
    if (nextMillis == null) return if (isPolish) "Wyłączone" else "Off"

    val timeText = DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(nextMillis))
    return if (DateUtils.isToday(nextMillis)) {
        if (isPolish) "Następne: dziś o $timeText" else "Next: today at $timeText"
    } else {
        if (isPolish) "Następne: jutro o $timeText" else "Next: tomorrow at $timeText"
    }
}