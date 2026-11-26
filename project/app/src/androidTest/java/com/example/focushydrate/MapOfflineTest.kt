package com.example.focushydrate

import android.content.Context
import android.content.ContextWrapper
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.platform.app.InstrumentationRegistry
import com.example.focushydrate.data.AccountProfile
import com.example.focushydrate.ui.screens.MapScreen
import com.example.focushydrate.ui.theme.FocusHydrateTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MapOfflineTest {
    @get:Rule val compose = createComposeRule()

    @Test fun addPointWithoutInternetShowsMessageAndKeepsMapVisible() {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        compose.setContent {
            val context = LocalContext.current
            val offlineContext = object : ContextWrapper(context) {
                override fun getSystemService(name: String): Any? =
                    if (name == Context.CONNECTIVITY_SERVICE) null else super.getSystemService(name)
            }
            FocusHydrateTheme {
                CompositionLocalProvider(LocalContext provides offlineContext) {
                    MapScreen(AccountProfile("offline-test", "map@example.invalid", "Tester", false))
                }
            }
        }

        compose.onNode(
            hasClickAction() and hasAnyDescendant(hasText(resources.getString(R.string.add_point))),
            useUnmergedTree = true
        ).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText(resources.getString(R.string.error_offline)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.choose_point)).assertDoesNotExist()
        onView(isAssignableFrom(WebView::class.java))
            .check(matches(isDisplayed()))
            .check { view, error ->
                if (error != null) throw error
                assertEquals(WebSettings.LOAD_CACHE_ELSE_NETWORK, (view as WebView).settings.cacheMode)
            }
    }
}
