package com.example.focushydrate.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focushydrate.R
import com.example.focushydrate.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(goal: Int, profile: AccountProfile, accounts: LocalAccounts, language: String,
    onLanguage: (String) -> Unit, onProfile: (AccountProfile?) -> Unit,
    onDelete: (String) -> AccountResult, onResetData: () -> Unit, dataBusy: Boolean = false) {
    var editing by remember { mutableStateOf<String?>(null) }
    var value by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(stringResource(R.string.nav_settings), fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
        LanguagePicker(language, onLanguage, !busy && !dataBusy)
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.daily_goal), fontWeight = FontWeight.SemiBold)
                Text("$goal ml", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
        ReminderSettings(profile.id, !busy && !dataBusy)
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.account), style = MaterialTheme.typography.titleMedium)
                Text(profile.username, fontWeight = FontWeight.Bold)
                Text(profile.email)
                TextButton(enabled = !busy && !dataBusy, onClick = { edit("username") }) { Text(stringResource(R.string.change_username)) }
                TextButton(enabled = !busy && !dataBusy, onClick = { edit("email") }) { Text(stringResource(R.string.change_email)) }
                TextButton(enabled = !busy && !dataBusy, onClick = { edit("password") }) { Text(stringResource(R.string.change_password)) }
                EmailConsent(profile.emailNotifications, !busy && !dataBusy) { checked ->
                    perform { accounts.update(profile.id, consent = checked) }
                }
                Text(stringResource(R.string.local_account_note), style = MaterialTheme.typography.bodySmall)
            }
        }
        notice?.let { Text(it) }
        if (editing == null) error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = { confirmReset = true }, enabled = !busy && !dataBusy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.reset_today))
        }
        OutlinedButton(onClick = { perform { accounts.logout() } }, enabled = !busy && !dataBusy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sign_out))
        }
        TextButton(onClick = { edit("delete") }, enabled = !busy && !dataBusy) {
            Text(stringResource(R.string.delete_account), color = MaterialTheme.colorScheme.error)
        }
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
