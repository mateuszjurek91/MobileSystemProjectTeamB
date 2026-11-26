package com.example.focushydrate.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.focushydrate.R
import com.example.focushydrate.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(goal: Int, profile: AccountProfile, accounts: LocalAccounts, language: String,
                   onLanguage: (String) -> Unit, onProfile: (AccountProfile?) -> Unit,
                   onDelete: (String) -> AccountResult, onResetData: () -> Unit, dataBusy: Boolean = false,
                   onGoalChange: (Int) -> Unit = {}) {
    var editing by remember { mutableStateOf<String?>(null) }
    var value by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    fun edit(field: String) {
        editing = field; value = when(field) { "username" -> profile.username; "email" -> profile.email; else -> "" }
        currentPassword = ""; confirm = ""; error = null; notice = null
    }
    fun perform(action: () -> AccountResult) {
        if (dataBusy) return
        busy = true; error = null; notice = null
        scope.launch {
            try {
                when (val result = withContext(Dispatchers.IO) { action() }) {
                    is AccountResult.Success -> {
                        editing = null; value = ""; currentPassword = ""; confirm = ""
                        notice = resources.getString(R.string.profile_saved); onProfile(result.profile)
                    }
                    is AccountResult.Failure -> error = resources.accountMessage(result.issue)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = resources.getString(R.string.error_storage) }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
        LanguagePicker(language, onLanguage, !busy && !dataBusy)
        ListItem(
            modifier = Modifier.clip(MaterialTheme.shapes.large).clickable(enabled = !busy && !dataBusy) { showGoalDialog = true },
            headlineContent = { Text(stringResource(R.string.daily_goal)) },
            supportingContent = { Text(stringResource(R.string.goal_tap_to_edit)) },
            trailingContent = { Text("$goal ml", fontWeight = FontWeight.SemiBold) }
        )
        ReminderSettings(profile.id, !busy && !dataBusy)
        Text(stringResource(R.string.account), style = MaterialTheme.typography.titleMedium)
        ListItem(
            modifier = Modifier.clip(MaterialTheme.shapes.large),
            headlineContent = { Text(stringResource(R.string.username)) },
            supportingContent = {
                Column {
                    Text(profile.username)
                    TextButton(enabled = !busy && !dataBusy, onClick = { edit("username") }) {
                        Text(stringResource(R.string.change_username))
                    }
                }
            }
        )
        ListItem(
            modifier = Modifier.clip(MaterialTheme.shapes.large),
            headlineContent = { Text(stringResource(R.string.email)) },
            supportingContent = {
                Column {
                    Text(profile.email)
                    TextButton(enabled = !busy && !dataBusy, onClick = { edit("email") }) {
                        Text(stringResource(R.string.change_email))
                    }
                }
            }
        )
        TextButton(enabled = !busy && !dataBusy, onClick = { edit("password") }) {
            Text(stringResource(R.string.change_password))
        }
        Text(stringResource(R.string.local_account_note), style = MaterialTheme.typography.bodySmall)
        notice?.let { Text(it) }
        if (editing == null) error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { confirmReset = true }, enabled = !busy && !dataBusy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.reset_today))
        }
        OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { perform { accounts.logout() } }, enabled = !busy && !dataBusy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sign_out))
        }
        TextButton(onClick = { edit("delete") }, enabled = !busy && !dataBusy) {
            Text(stringResource(R.string.delete_account), color = MaterialTheme.colorScheme.error)
        }
    }
    if (showGoalDialog) {
        var selectedGoal by remember { mutableIntStateOf(goal) }
        var showCalculator by remember { mutableStateOf(false) }
        var weight by remember { mutableStateOf("") }
        var height by remember { mutableStateOf("") }
        var activity by remember { mutableStateOf(ActivityLevel.MEDIUM) }

        fun recalculateIfValid(wText: String, hText: String, act: ActivityLevel) {
            val w = wText.toIntOrNull()
            val h = hText.toIntOrNull()
            if (w != null && h != null && w in 35..200 && h in 120..230) {
                selectedGoal = calculateWaterGoal(w, h, act)
            }
        }

        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text(stringResource(R.string.daily_goal)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            shape = MaterialTheme.shapes.small,
                            onClick = { selectedGoal = (selectedGoal - 250).coerceAtLeast(1000) }
                        ) { Text("-250") }
                        Text(
                            text = "$selectedGoal ml",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        FilledTonalButton(
                            shape = MaterialTheme.shapes.small,
                            onClick = { selectedGoal = (selectedGoal + 250).coerceAtMost(5000) }
                        ) { Text("+250") }
                    }
                    TextButton(onClick = { showCalculator = !showCalculator }) {
                        Text(
                            stringResource(
                                if (showCalculator) R.string.goal_hide_calculator
                                else R.string.goal_show_calculator
                            )
                        )
                    }
                    if (showCalculator) {
                        OutlinedTextField(
                            value = weight,
                            onValueChange = {
                                weight = it.filter(Char::isDigit).take(3)
                                recalculateIfValid(weight, height, activity)
                            },
                            colors = inputColors(),
                            singleLine = true,
                            label = { Text(stringResource(R.string.goal_weight_kg)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = height,
                            onValueChange = {
                                height = it.filter(Char::isDigit).take(3)
                                recalculateIfValid(weight, height, activity)
                            },
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
                                    onClick = {
                                        activity = level
                                        recalculateIfValid(weight, height, activity)
                                    },
                                    label = { Text(stringResource(labelRes)) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onGoalChange(selectedGoal)
                    showGoalDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false },
        title = { Text(stringResource(R.string.reset_title)) }, text = { Text(stringResource(R.string.reset_body)) },
        confirmButton = { TextButton(onClick = { onResetData(); confirmReset = false }) { Text(stringResource(R.string.reset)) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } })
    editing?.let { field ->
        AlertDialog(onDismissRequest = { if (!busy) { editing = null; currentPassword = ""; value = ""; confirm = "" } },
            title = { Text(stringResource(when(field) {
                "username" -> R.string.change_username; "email" -> R.string.change_email
                "password" -> R.string.change_password; else -> R.string.delete_account
            })) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (field == "delete") Text(stringResource(R.string.delete_account_body))
                    if (field == "username" || field == "email") OutlinedTextField(value, { value = it },
                        colors = inputColors(),
                        enabled = !busy && !dataBusy, singleLine = true, label = { Text(stringResource(if (field == "email") R.string.email else R.string.username)) },
                        keyboardOptions = KeyboardOptions(keyboardType = if (field == "email") KeyboardType.Email else KeyboardType.Text))
                    if (field != "username") PasswordField(currentPassword, { currentPassword = it }, stringResource(R.string.current_password), !busy)
                    if (field == "password") {
                        PasswordField(value, { value = it }, stringResource(R.string.new_password), !busy)
                        PasswordField(confirm, { confirm = it }, stringResource(R.string.confirm_password), !busy)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(enabled = !busy && !dataBusy, onClick = {
                if (field == "password" && value != confirm) error = resources.getString(R.string.error_confirm)
                else perform {
                    when(field) {
                        "username" -> accounts.update(profile.id, username = value)
                        "email" -> accounts.update(profile.id, email = value, currentPassword = currentPassword)
                        "password" -> accounts.update(profile.id, newPassword = value, currentPassword = currentPassword)
                        else -> onDelete(currentPassword)
                    }
                }
            }) { Text(stringResource(if (field == "delete") R.string.delete else R.string.save)) } },
            dismissButton = { TextButton(enabled = !busy && !dataBusy, onClick = { editing = null; currentPassword = ""; value = ""; confirm = "" }) {
                Text(stringResource(R.string.cancel))
            } })
    }
}