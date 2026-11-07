package com.example.focushydrate

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.focushydrate.data.*
import com.example.focushydrate.ui.screens.AuthScreen
import com.example.focushydrate.ui.screens.StatisticsScreen
import com.example.focushydrate.ui.theme.FocusHydrateTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class PrototypeUiTest {
    @get:Rule val compose = createComposeRule()
    private class MemoryStorage : AccountStorage {
        var snapshot = AccountsSnapshot()
        override fun read() = snapshot
        override fun write(snapshot: AccountsSnapshot): Boolean { this.snapshot = snapshot; return true }
    }
    @Test fun registrationCollectsDetailsAndOptionalEmailConsent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val authenticated = AtomicReference<AccountProfile?>(null)
        val accounts = LocalAccounts(MemoryStorage())
        compose.setContent { FocusHydrateTheme {
            AuthScreen(accounts, "en", {}, { authenticated.set(it) })
        } }
        compose.onNodeWithText(context.getString(R.string.email)).performScrollTo().performTextInput("ui@example.invalid")
        compose.onNodeWithText(context.getString(R.string.username)).performScrollTo().performTextInput("UI Tester")
        compose.onNodeWithText(context.getString(R.string.password)).performScrollTo().performTextInput("TestPassword123")
        compose.onNodeWithText(context.getString(R.string.confirm_password)).performScrollTo().performTextInput("TestPassword123")
        compose.onNodeWithText(context.getString(R.string.email_consent)).performScrollTo().assertIsOff().performClick()
        compose.onNode(hasText(context.getString(R.string.create_account)) and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(30_000) { authenticated.get() != null }
        assertEquals("ui@example.invalid", authenticated.get()?.email)
        assertTrue(authenticated.get()?.emailNotifications == true)
    }
    @Test fun statisticsShowCalendarProgressAndSwitchChartRanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val history = listOf(DailyIntake("2026-11-05", 1000), DailyIntake("2026-11-06", 1500))
        compose.setContent { FocusHydrateTheme { StatisticsScreen(history, "2026-11-06", {}) } }
        compose.onNodeWithContentDescription(context.getString(R.string.day_progress, "2026-11-06", 1500, 2000, 75)).assertExists()
        saveScreenshot("calendar.png")
        compose.onNodeWithText(context.getString(R.string.year)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.chart_monthly)).assertExists()
        compose.onNodeWithText(context.getString(R.string.all_time)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.chart_monthly)).performScrollTo().assertIsDisplayed()
        saveScreenshot("chart.png")
    }
    private fun saveScreenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.externalCacheDir, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
