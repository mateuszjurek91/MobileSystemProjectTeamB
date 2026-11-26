package com.example.focushydrate.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
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
    saving: Boolean, error: String?, pendingVotes: Set<Pair<String, String>>, onSave: (FeedbackStatus, String) -> Unit,
    onVote: (SpotFeedback, FeedbackVote) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val spotReports = remember(reports, spot.id) {
        reports.filter { it.spotId == spot.id }.sortedByDescending { it.createdAt }
    }
    val existing = spotReports.find { it.authorId == profile.id }
    val recentReports = spotReports.take(2)
    val dateFormat = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale) }
    var status by rememberSaveable(spot.id) {
        mutableStateOf(existing?.status?.takeUnless { it == FeedbackStatus.NO_WATER } ?: FeedbackStatus.NOT_WORKING)
    }
    var note by rememberSaveable(spot.id) { mutableStateOf(existing?.note ?: "") }
    var reading by rememberSaveable(spot.id) { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable(spot.id) { mutableStateOf(false) }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.feedback_delete)) },
            text = { Text(stringResource(R.string.feedback_delete_confirmation, spot.name)) },
            confirmButton = {
                TextButton(
                    enabled = existing != null && !saving,
                    onClick = { confirmingDelete = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
        return
    }

    if (reading) {
        AlertDialog(
            onDismissRequest = { reading = false },
            title = { Text(stringResource(R.string.feedback_read_title)) },
            text = {
                LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(spot.name, style = MaterialTheme.typography.titleMedium)
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    if (spotReports.isEmpty()) item { Text(stringResource(R.string.feedback_empty)) }
                    items(spotReports, key = { it.authorId }) { report ->
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FeedbackEntry(report, dateFormat, Modifier.weight(1f))
                            if (report.authorId != profile.id) {
                                FeedbackVoteButtons(report, profile.id,
                                    (report.spotId to report.authorId) !in pendingVotes,
                                    onVote)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { reading = false }) { Text(stringResource(R.string.feedback_back)) }
            }
        )
        return
    }

    AlertDialog(onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(spot.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.feedback_your), style = MaterialTheme.typography.titleSmall)
                Column {
                    FeedbackStatus.entries.filterNot { it == FeedbackStatus.NO_WATER }.forEach { item ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = item == status, enabled = !saving,
                            role = Role.RadioButton, onClick = { status = item }), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = item == status, onClick = null)
                            Text(stringResource(item.label()), Modifier.padding(start = 12.dp))
                        }
                    }
                }
                OutlinedTextField(note, { if (it.length <= 500) note = it }, Modifier.fillMaxWidth(), enabled = !saving,
                    colors = inputColors(),
                    label = { Text(stringResource(R.string.feedback_note)) }, minLines = 2, maxLines = 4,
                    supportingText = { Text("${note.length}/500") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (existing != null) {
                    TextButton(
                        enabled = !saving,
                        onClick = { confirmingDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.feedback_delete)) }
                }
                HorizontalDivider()
                Text(stringResource(R.string.recent_feedback), style = MaterialTheme.typography.titleSmall)
                if (recentReports.isEmpty()) Text(stringResource(R.string.feedback_empty))
                recentReports.forEach { report ->
                    FeedbackEntry(report, dateFormat, maxNoteLines = 2)
                }
                if (spotReports.isNotEmpty()) {
                    OutlinedButton(shape = MaterialTheme.shapes.small, enabled = !saving, onClick = { reading = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.feedback_read))
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !saving, onClick = { onSave(status, note.trim()) }) { Text(stringResource(R.string.feedback_submit)) } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}

@Composable
private fun FeedbackEntry(report: SpotFeedback, dateFormat: DateFormat,
    modifier: Modifier = Modifier, maxNoteLines: Int = Int.MAX_VALUE) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(report.authorName, style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(report.status.label()) + " · " + dateFormat.format(Date(report.createdAt)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (report.note.isNotBlank()) {
            Text(report.note, style = MaterialTheme.typography.bodyMedium,
                maxLines = maxNoteLines, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FeedbackVoteButtons(report: SpotFeedback, viewerId: String, enabled: Boolean,
    onVote: (SpotFeedback, FeedbackVote) -> Unit) {
    Row {
        FeedbackVote.entries.forEach { vote ->
            val checked = report.votes[viewerId] == vote
            val count = report.votes.values.count { it == vote }
            val label = when (vote) {
                FeedbackVote.LIKE -> R.string.feedback_like
                FeedbackVote.DISLIKE -> R.string.feedback_dislike
            }
            val icon = when (vote) {
                FeedbackVote.LIKE -> if (checked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder
                FeedbackVote.DISLIKE -> if (checked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown
            }
            IconToggleButton(checked = checked, enabled = enabled, onCheckedChange = { onVote(report, vote) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, contentDescription = stringResource(label, count), modifier = Modifier.size(20.dp))
                    Text(count.toString(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
