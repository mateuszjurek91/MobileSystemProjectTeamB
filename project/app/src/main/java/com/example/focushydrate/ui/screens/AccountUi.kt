package com.example.focushydrate.ui.screens

import android.content.res.Resources
import androidx.compose.foundation.layout.*
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
