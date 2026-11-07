package com.example.focushydrate.ui.screens

import android.content.res.Resources
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.focushydrate.R
import com.example.focushydrate.data.AccountIssue

fun Resources.accountMessage(issue: AccountIssue): String = getString(when (issue) {
    AccountIssue.EMAIL -> R.string.error_email
    AccountIssue.USERNAME -> R.string.error_username
    AccountIssue.PASSWORD -> R.string.error_password
    AccountIssue.EXISTS -> R.string.error_exists
    AccountIssue.CREDENTIALS -> R.string.error_credentials
    AccountIssue.CURRENT_PASSWORD -> R.string.error_current_password
    AccountIssue.STORAGE -> R.string.error_storage
})

@Composable
fun PasswordField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean = true) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) },
        colors = inputColors(),
        enabled = enabled, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { IconButton(onClick = { visible = !visible }) {
            Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                stringResource(if (visible) R.string.hide_password else R.string.show_password))
        } })
}

@Composable
fun LanguagePicker(language: String, onLanguage: (String) -> Unit, enabled: Boolean = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(selected = language == "en", enabled = enabled, onClick = { onLanguage("en") }, label = { Text("English") })
        FilterChip(selected = language == "pl", enabled = enabled, onClick = { onLanguage("pl") }, label = { Text("Polski") })
    }
}

@Composable
internal fun EmailConsent(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .heightIn(min = 48.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(checked, onCheckedChange = null, enabled = enabled)
        Text(stringResource(R.string.email_consent))
    }
}

@Composable
internal fun inputColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    errorContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
)
