package com.example.focushydrate.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.focushydrate.R

enum class Screen(val titleRes: Int, val icon: ImageVector) {
    Map(R.string.nav_map, Icons.Outlined.Map),
    Tracker(R.string.nav_tracker, Icons.Outlined.WaterDrop),
    Settings(R.string.nav_settings, Icons.Outlined.Settings)
}
