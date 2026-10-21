package com.example.focushydrate.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(val title: String, val icon: ImageVector) {
    Tracker("Tracker", Icons.Default.CheckCircle),
    Checker("Water Checker", Icons.Default.Search),
    Minerals("Minerals", Icons.Default.List),
    Settings("Settings", Icons.Default.Settings)
}