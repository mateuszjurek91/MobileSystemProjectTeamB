package com.example.focushydrate.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
fun AuthScreen(accounts: LocalAccounts, language: String, onLanguage: (String) -> Unit, onAuthenticated: (AccountProfile) -> Unit) {
    var registration by rememberSaveable { mutableStateOf(true) }
    var email by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LanguagePicker(language, onLanguage, !busy)
        Icon(Icons.Outlined.WaterDrop, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(if (registration) R.string.create_account else R.string.sign_in), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), enabled = !busy,
            shape = MaterialTheme.shapes.medium,
            colors = inputColors(),
            label = { Text(stringResource(R.string.email)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        if (registration) OutlinedTextField(username, { if (it.length <= 30) username = it }, Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = inputColors(),
            enabled = !busy, label = { Text(stringResource(R.string.username)) }, singleLine = true)
        PasswordField(password, { password = it }, stringResource(R.string.password), !busy)
        if (registration) {
            Text(stringResource(R.string.error_password), style = MaterialTheme.typography.bodySmall)
            PasswordField(confirm, { confirm = it }, stringResource(R.string.confirm_password), !busy)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(stringResource(R.string.local_account_note), style = MaterialTheme.typography.bodySmall)
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = {
            if (registration && password != confirm) {
                error = resources.getString(R.string.error_confirm)
            } else {
                busy = true; error = null
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            if (registration) accounts.register(email, username, password, false) else accounts.login(email, password)
                        }
                        when (result) {
                            is AccountResult.Success -> result.profile?.let { password = ""; confirm = ""; onAuthenticated(it) }
                            is AccountResult.Failure -> error = resources.accountMessage(result.issue)
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = resources.getString(R.string.error_storage) }
                    finally { busy = false }
                }
            }
        }) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Text(stringResource(if (registration) R.string.create_account else R.string.sign_in))
        }
        TextButton(enabled = !busy, onClick = { registration = !registration; error = null; password = ""; confirm = "" }) {
            Text(stringResource(if (registration) R.string.have_account else R.string.need_account))
        }
    }
}
