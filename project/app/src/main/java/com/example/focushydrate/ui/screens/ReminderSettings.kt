package com.example.focushydrate.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.focushydrate.R
import com.example.focushydrate.reminders.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormatSymbols
import java.util.Locale

@Composable
fun ReminderSettings(accountId: String, enabled: Boolean = true) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val store = remember { ReminderStore(context.applicationContext) }
    var plan by remember(accountId) { mutableStateOf<ReminderPlan?>(null) }
    var start by rememberSaveable(accountId) { mutableStateOf("08:00") }
    var end by rememberSaveable(accountId) { mutableStateOf("22:00") }
    var wantsReminders by rememberSaveable(accountId) { mutableStateOf(false) }
    var interval by rememberSaveable(accountId) { mutableIntStateOf(120) }
    var days by rememberSaveable(accountId) { mutableStateOf((1..7).toList()) }
    var initialized by rememberSaveable(accountId) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var permitted by remember { mutableStateOf(DrinkReminders.allowed(context)) }
    fun reconcile() {
        scope.launch {
            withContext(Dispatchers.IO) { DrinkReminders.refresh(context.applicationContext) }
            permitted = DrinkReminders.allowed(context)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { reconcile() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) reconcile() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(accountId, attempt) {
        try {
            val loaded = withContext(Dispatchers.IO) { store.load(accountId) }
            plan = loaded
            if (!initialized) {
                wantsReminders = loaded.enabled
                interval = loaded.intervalMinutes
                days = loaded.weekdays.toList()
                start = String.format(Locale.ROOT, "%02d:%02d", loaded.startMinute / 60, loaded.startMinute % 60)
                end = String.format(Locale.ROOT, "%02d:%02d", loaded.endMinute / 60, loaded.endMinute % 60)
                initialized = true
            }
            error = null
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = resources.getString(R.string.error_load) }
    }
    Card {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.drink_reminders), style = MaterialTheme.typography.titleMedium)
            if (plan == null) {
                if (error == null) CircularProgressIndicator()
                else TextButton(onClick = { error = null; attempt++ }) { Text(stringResource(R.string.retry)) }
            } else {
                val editable = enabled && !busy
                Row(Modifier.fillMaxWidth().toggleable(wantsReminders, enabled = editable, role = Role.Switch,
                    onValueChange = { wantsReminders = it; saved = false }),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.reminders_enable), Modifier.weight(1f))
                    Switch(wantsReminders, onCheckedChange = null, enabled = editable)
                }
                if (wantsReminders) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(start, { start = it; saved = false }, Modifier.weight(1f),
                            label = { Text(stringResource(R.string.reminder_start)) }, singleLine = true,
                            enabled = editable, colors = inputColors())
                        OutlinedTextField(end, { end = it; saved = false }, Modifier.weight(1f),
                            label = { Text(stringResource(R.string.reminder_end)) }, singleLine = true,
                            enabled = editable, colors = inputColors())
                    }
                    Text(stringResource(R.string.reminder_time_hint), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.reminder_interval))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 60, 120, 240).forEach { minutes ->
                            FilterChip(interval == minutes, onClick = { interval = minutes; saved = false }, enabled = editable,
                                label = { Text(stringResource(R.string.reminder_minutes, minutes)) })
                        }
                    }
                    val locale = resources.configuration.locales[0]
                    val weekdays = DateFormatSymbols(locale).weekdays
                    Text(stringResource(R.string.reminder_days))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 3, 4, 5, 6, 7, 1).forEach { day ->
                            FilterChip(day in days, enabled = editable, onClick = {
                                days = if (day in days) days - day else days + day
                                saved = false
                            }, label = { Text(weekdays[day]) })
                        }
                    }
                    Text(stringResource(R.string.reminder_timing_note), style = MaterialTheme.typography.bodySmall)
                    if (!permitted) {
                        Text(stringResource(R.string.reminders_blocked))
                        TextButton(enabled = editable, onClick = {
                            val intent = if (Build.VERSION.SDK_INT >= 26) {
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:${context.packageName}"))
                            runCatching { context.startActivity(intent) }
                                .onFailure { error = resources.getString(R.string.error_storage) }
                        }) { Text(stringResource(R.string.reminder_system_settings)) }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (saved) Text(stringResource(R.string.reminders_saved))
                Button(enabled = editable, onClick = {
                    val previous = requireNotNull(plan)
                    val startMinute = parseReminderTime(start)
                    val endMinute = parseReminderTime(end)
                    if (wantsReminders && (startMinute == null || endMinute == null || startMinute >= endMinute || days.isEmpty())) {
                        error = resources.getString(R.string.reminder_invalid)
                    } else {
                        val next = if (wantsReminders) previous.copy(enabled = true, startMinute = startMinute!!,
                            endMinute = endMinute!!, weekdays = days.toSet(), intervalMinutes = interval,
                            enabledAt = if (previous.enabled) previous.enabledAt else System.currentTimeMillis())
                        else previous.copy(enabled = false)
                        busy = true
                        error = null
                        saved = false
                        scope.launch {
                            try {
                                if (withContext(Dispatchers.IO) { DrinkReminders.update(context.applicationContext, accountId, next) }) {
                                    plan = next
                                    saved = true
                                    permitted = DrinkReminders.allowed(context)
                                    if (wantsReminders && !previous.enabled && !permitted && Build.VERSION.SDK_INT >= 33) {
                                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else error = resources.getString(R.string.error_storage)
                            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (e: Exception) { error = resources.getString(R.string.error_storage) }
                            finally { busy = false }
                        }
                    }
                }) { Text(stringResource(R.string.reminders_save)) }
            }
        }
    }
}
