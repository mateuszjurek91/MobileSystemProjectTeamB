package com.example.focushydrate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.example.focushydrate.ui.navigation.Screen
import com.example.focushydrate.ui.screens.MineralWatersScreen
import com.example.focushydrate.ui.screens.SettingsScreen
import com.example.focushydrate.ui.screens.TrackerScreen
import com.example.focushydrate.ui.screens.WaterCheckerScreen
import com.example.focushydrate.ui.theme.FocusHydrateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FocusHydrateTheme {
                HydrationApp()
            }
        }
    }
}

@Composable
fun HydrationApp() {
    var currentScreen by rememberSaveable { mutableStateOf(Screen.Tracker) }
    var currentIntake by rememberSaveable { mutableIntStateOf(750) }
    var dailyGoal by rememberSaveable { mutableIntStateOf(2000) }
    var notificationsEnabled by rememberSaveable { mutableStateOf(true) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (currentScreen) {
                Screen.Tracker -> TrackerScreen(
                    intake = currentIntake,
                    goal = dailyGoal,
                    onAddWater = { currentIntake += 250 }
                )
                Screen.Checker -> WaterCheckerScreen()
                Screen.Minerals -> MineralWatersScreen()
                Screen.Settings -> SettingsScreen(
                    goal = dailyGoal,
                    notificationsEnabled = notificationsEnabled,
                    onToggleNotifications = { notificationsEnabled = it },
                    onResetData = { currentIntake = 0 }
                )
            }
        }
    }
}