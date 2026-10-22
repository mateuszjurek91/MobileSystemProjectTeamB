package com.example.focushydrate.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TrackerScreen(intake: Int, goal: Int, onAddWater: () -> Unit) {
    val percentage = ((intake.toFloat() / goal.toFloat()) * 100).toInt().coerceAtMost(100)

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "FocusHydrate Tracker",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Progress Card (Accessibility Req 2: Non-color text indicator + high contrast)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$intake / $goal ml — $percentage%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { (intake.toFloat() / goal.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(12.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Next inactivity reminder in: 2h 00m (reset on log)",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Quick-Log Button (Accessibility Req 1: >= 48x48dp target + TalkBack semantics)
        Button(
            onClick = onAddWater,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .semantics {
                    contentDescription = "Log 250 milliliters of water. Current total is $intake milliliters out of $goal."
                }
        ) {
            Text(
                text = "+250 ml (One-Tap Log)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}