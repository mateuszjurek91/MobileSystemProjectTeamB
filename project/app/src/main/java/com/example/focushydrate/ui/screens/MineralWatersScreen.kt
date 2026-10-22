package com.example.focushydrate.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MineralWatersScreen() {
    val waters = listOf(
        "Muszynianka" to "High mineralization (1516 mg/L) • High Mg & Ca",
        "Piwniczanka" to "High mineralization (1750 mg/L) • Good for focus",
        "Cisowianka" to "Medium mineralization (742 mg/L) • Low sodium",
        "Żywiec Zdrój" to "Spring water (230 mg/L) • Comparable to filtered tap water"
    )

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Mineral Waters Reference", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(waters) { (name, desc) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(desc, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}