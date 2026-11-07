package com.example.focushydrate.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.focushydrate.R
import com.example.focushydrate.data.*
import java.text.DateFormat
import java.util.Date

fun FeedbackStatus.label(): Int = when(this) {
    FeedbackStatus.WORKING -> R.string.feedback_working
    FeedbackStatus.NOT_WORKING -> R.string.feedback_broken
    FeedbackStatus.NO_WATER -> R.string.feedback_no_water
    FeedbackStatus.WRONG_LOCATION -> R.string.feedback_wrong_location
}

@Composable
fun FeedbackDialog(spot: WaterSpot, reports: List<SpotFeedback>, profile: AccountProfile,
    saving: Boolean, error: String?, onSave: (FeedbackStatus, String) -> Unit, onDismiss: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val existing = reports.find { it.spotId == spot.id && it.authorId == profile.id }
    var status by remember(spot.id) { mutableStateOf(existing?.status ?: FeedbackStatus.NOT_WORKING) }
    var note by remember(spot.id) { mutableStateOf(existing?.note ?: "") }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.feedback_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(spot.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.feedback_local), style = MaterialTheme.typography.bodySmall)
                FeedbackStatus.entries.forEach { item ->
                    Row(Modifier.fillMaxWidth().selectable(selected = item == status, enabled = !saving,
                        role = Role.RadioButton, onClick = { status = item }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = item == status, onClick = null)
                        Text(stringResource(item.label()))
                    }
                }
                OutlinedTextField(note, { if (it.length <= 500) note = it }, enabled = !saving,
                    colors = inputColors(),
                    label = { Text(stringResource(R.string.feedback_note)) }, minLines = 2,
                    supportingText = { Text("${note.length}/500") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(stringResource(R.string.recent_feedback), style = MaterialTheme.typography.titleSmall)
                val recent = reports.filter { it.spotId == spot.id }.sortedByDescending { it.createdAt }
                if (recent.isEmpty()) Text(stringResource(R.string.feedback_empty))
                recent.take(10).forEach { report ->
                    Text("${report.authorName} · ${stringResource(report.status.label())}", style = MaterialTheme.typography.bodyMedium)
                    if (report.note.isNotBlank()) Text(report.note, style = MaterialTheme.typography.bodySmall)
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(report.createdAt)),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = { TextButton(enabled = !saving, onClick = { onSave(status, note.trim()) }) { Text(stringResource(R.string.feedback_submit)) } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}
