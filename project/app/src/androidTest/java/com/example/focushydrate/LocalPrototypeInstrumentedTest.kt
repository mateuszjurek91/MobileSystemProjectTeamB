package com.example.focushydrate

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.focushydrate.data.*
import com.example.focushydrate.localization.AppLanguage
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalPrototypeInstrumentedTest {
    private lateinit var base: Context
    private lateinit var isolated: Context
    private val names = mutableSetOf<String>()
    private val prefix = "test_${System.nanoTime()}_"
    @Before fun setUp() {
        base = InstrumentationRegistry.getInstrumentation().targetContext
        isolated = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                names.add(prefix + name)
                return base.getSharedPreferences(prefix + name, mode)
            }
        }
    }
    @After fun cleanUp() { names.forEach { base.deleteSharedPreferences(it) } }

    @Test fun accountAndConsentSurviveRepositoryRecreation() {
        val accounts = LocalAccounts(PreferencesAccountStorage(isolated))
        val registered = accounts.register("test@example.com", "Tester", "testpassword123", true) as AccountResult.Success
        val restored = LocalAccounts(PreferencesAccountStorage(isolated))
        assertEquals(registered.profile, restored.current())
        restored.logout()
        assertTrue(restored.login("test@example.com", "testpassword123") is AccountResult.Success)
    }
    @Test fun hydrationIsPersistentAndSeparatedBetweenAccounts() {
        val store = HydrationStore(isolated)
        assertTrue(store.save("first", listOf(DailyIntake("2026-11-05", 1500), DailyIntake("2026-11-06", 250))))
        assertEquals(250, HydrationStore(isolated).load("first").last().millilitres)
        assertTrue(store.load("second").isEmpty())
        assertTrue(store.delete("first")); assertTrue(store.load("first").isEmpty())
    }
    @Test fun deletingOwnerRemovesOwnedSpotsAndTheirReportsOnly() {
        val store = WaterSpotStore(isolated)
        store.save(listOf(WaterSpot("first", "First", 52.0, 19.0, "owner1"), WaterSpot("second", "Second", 51.0, 20.0, "owner2")))
        store.saveFeedback(listOf(
            SpotFeedback("first", "owner2", "Two", FeedbackStatus.NOT_WORKING, "Broken", 1),
            SpotFeedback("second", "owner1", "One", FeedbackStatus.NO_WATER, "Empty", 2),
            SpotFeedback("second", "owner2", "Two", FeedbackStatus.WORKING, "", 3)))
        assertTrue(store.deleteAccountData("owner1"))
        assertEquals(listOf("second"), WaterSpotStore(isolated).load().map { it.id })
        assertEquals(listOf("owner2"), WaterSpotStore(isolated).loadFeedback().map { it.authorId })
    }
    @Test fun switchingLanguageIsPersistedAndSelectsTranslatedResources() {
        assertTrue(AppLanguage.set(isolated, "pl"))
        assertEquals("pl", AppLanguage.current(isolated))
        assertEquals("Ustawienia", AppLanguage.wrap(isolated).getString(R.string.nav_settings))
        assertTrue(AppLanguage.set(isolated, "en"))
        assertEquals("Settings", AppLanguage.wrap(isolated).getString(R.string.nav_settings))
    }
}
