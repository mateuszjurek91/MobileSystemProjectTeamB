package com.example.focushydrate.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    goal: Int,
    notificationsEnabled: Boolean,
    onToggleNotifications: (Boolean) -> Unit,
    onResetData: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings & Privacy", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Daily Goal: $goal ml", fontWeight = FontWeight.Bold)
                Text("Configured for standard study session baseline.")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("2h Inactivity Reminders", fontWeight = FontWeight.Bold)
                    Switch(checked = notificationsEnabled, onCheckedChange = onToggleNotifications)
                }
                if (!notificationsEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Permission denied / disabled: Local tracking still works normally. To enable background alerts, allow Notifications in Android System Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onResetData,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text("Reset Today's Local Data (0 ml)")
        }
    }
}