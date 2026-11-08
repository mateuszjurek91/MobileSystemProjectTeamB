package com.example.focushydrate

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.focushydrate.data.*
import com.example.focushydrate.reminders.*
import com.example.focushydrate.ui.screens.ReminderSettings
import com.example.focushydrate.ui.theme.FocusHydrateTheme
import org.junit.*
import org.junit.Assert.*

class ReminderSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val base = instrumentation.targetContext
    private val names = mutableSetOf<String>()
    private val prefix = "reminder_ui_${System.nanoTime()}_"
    private val isolated = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            names.add(prefix + name)
            return base.getSharedPreferences(prefix + name, mode)
        }
    }
    private lateinit var profile: AccountProfile
    @Before fun register() {
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(base.packageName, Manifest.permission.POST_NOTIFICATIONS)
        profile = (LocalAccounts(PreferencesAccountStorage(isolated)).register("ui@example.invalid", "Tester", "TestPassword123", false) as AccountResult.Success).profile!!
    }
    @After fun cleanUp() {
        LocalAccounts(PreferencesAccountStorage(isolated)).logout()
        DrinkReminders.refresh(isolated)
        names.forEach { base.deleteSharedPreferences(it) }
    }
    @Test fun invalidTimesAreRejectedAndValidSettingsSurviveRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val registry = requireNotNull(androidx.activity.compose.LocalActivityResultRegistryOwner.current)
            CompositionLocalProvider(
                LocalContext provides isolated,
                androidx.activity.compose.LocalActivityResultRegistryOwner provides registry
            ) {
                FocusHydrateTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) { ReminderSettings(profile.id) }
                }
            }
        }
        compose.waitUntil(10000) { compose.onAllNodesWithText(base.getString(R.string.reminders_enable)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(base.getString(R.string.reminders_enable)).performScrollTo().performClick()
        compose.onNodeWithText(base.getString(R.string.drink_reminders)).performScrollTo()
        val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(base.externalCacheDir, "reminder-settings.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText(base.getString(R.string.reminder_start)).performScrollTo().performTextReplacement("25:00")
        compose.onNodeWithText(base.getString(R.string.reminders_save)).performScrollTo().performClick()
        compose.onNodeWithText(base.getString(R.string.reminder_invalid)).assertExists()
        assertFalse(ReminderStore(isolated).load(profile.id).enabled)
        compose.onNodeWithText(base.getString(R.string.reminder_start)).performScrollTo().performTextReplacement("09:15")
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(10000) { compose.onAllNodesWithText("09:15").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(base.getString(R.string.reminders_save)).performScrollTo().performClick()
        compose.waitUntil(10000) { ReminderStore(isolated).load(profile.id).enabled }
        val restored = ReminderStore(isolated).load(profile.id)
        assertEquals(555, restored.startMinute)
        assertEquals(1320, restored.endMinute)
        assertEquals(120, restored.intervalMinutes)
        compose.onNodeWithText(base.getString(R.string.reminders_enable)).performScrollTo().performClick()
        compose.onNodeWithText(base.getString(R.string.reminders_save)).performScrollTo().performClick()
        compose.waitUntil(10000) {
            !ReminderStore(isolated).load(profile.id).enabled && ReminderStore(isolated).token() == null
        }
        assertNull(ReminderStore(isolated).token())
    }
}
