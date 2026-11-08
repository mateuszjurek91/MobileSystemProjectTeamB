package com.example.focushydrate

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.focushydrate.data.*
import com.example.focushydrate.reminders.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val base = instrumentation.targetContext
    private val prefix = "reminder_test_${System.nanoTime()}_"
    private val names = mutableSetOf<String>()
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            names.add(prefix + name)
            return base.getSharedPreferences(prefix + name, mode)
        }
    }
    @Before fun allowNotifications() {
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(base.packageName, Manifest.permission.POST_NOTIFICATIONS)
        DrinkReminders.createChannel(context)
    }
    @After fun cleanUp() {
        LocalAccounts(PreferencesAccountStorage(context)).logout()
        DrinkReminders.refresh(context)
        names.forEach { base.deleteSharedPreferences(it) }
    }
    private fun register(email: String = "reminders@example.invalid"): AccountProfile =
        (LocalAccounts(PreferencesAccountStorage(context)).register(email, "Reminder User", "TestPassword123", false) as AccountResult.Success).profile!!
    private fun plan() = ReminderPlan(enabled = true, startMinute = 0, endMinute = 1440,
        intervalMinutes = 30, enabledAt = System.currentTimeMillis())
    private fun intent(accountId: String, token: String) = Intent()
        .setAction("com.example.focushydrate.DRINK_REMINDER").putExtra("account", accountId).putExtra("token", token)

    @Test fun settingsAndPositiveDrinkTimestampPersistSeparatelyForAccounts() {
        val profile = register()
        val p = plan().copy(weekdays = setOf(2, 4, 6))
        assertTrue(ReminderStore(context).save(profile.id, p))
        assertEquals(p, ReminderStore(context).load(profile.id))
        assertFalse(ReminderStore(context).load("other").enabled)
        val hydration = HydrationStore(context)
        assertTrue(hydration.save(profile.id, listOf(DailyIntake(Days.key(), 250)), 123456))
        assertEquals(123456L, HydrationStore(context).lastDrink(profile.id))
        assertTrue(hydration.save(profile.id, listOf(DailyIntake(Days.key(), 0))))
        assertEquals(123456L, hydration.lastDrink(profile.id))
        assertTrue(hydration.delete(profile.id))
        assertEquals(0L, hydration.lastDrink(profile.id))
        assertTrue(ReminderStore(context).delete(profile.id))
        assertFalse(ReminderStore(context).load(profile.id).enabled)
    }
    @Test fun deliveryPostsOnceAndRejectsOldAlarm() {
        val profile = register()
        com.example.focushydrate.localization.AppLanguage.set(context, "pl")
        val p = plan()
        assertTrue(DrinkReminders.update(context, profile.id, p))
        val token = requireNotNull(ReminderStore(context).token())
        assertTrue(ReminderStore(context).save(profile.id, p.copy(enabledAt = System.currentTimeMillis() - 3_600_000)))
        val alarm = intent(profile.id, token)
        assertTrue(DrinkReminders.deliver(context, alarm))
        val manager = base.getSystemService(NotificationManager::class.java)
        assertTrue(manager.activeNotifications.any { it.id == 2001 })
        val sent = ReminderStore(context).load(profile.id).lastSent
        assertTrue(sent > 0)
        assertFalse(DrinkReminders.deliver(context, alarm))
        assertEquals(sent, ReminderStore(context).load(profile.id).lastSent)
        val notification = manager.activeNotifications.single { it.id == 2001 }.notification
        assertEquals("Czas na wodę", notification.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertNotNull(notification.contentIntent)
        assertTrue(DrinkReminders.update(context, profile.id, p.copy(enabled = false)))
        assertNull(ReminderStore(context).token())
        assertFalse(manager.activeNotifications.any { it.id == 2001 })
    }
    @Test fun deniedPermissionAndLogoutCancelTheSchedule() {
        val profile = register()
        assertTrue(DrinkReminders.update(context, profile.id, plan()))
        val denied = object : ContextWrapper(context) {
            override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                if (permission == Manifest.permission.POST_NOTIFICATIONS) PackageManager.PERMISSION_DENIED
                else super.checkPermission(permission, pid, uid)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            assertFalse(DrinkReminders.allowed(denied))
            assertTrue(DrinkReminders.refresh(denied))
            assertNull(ReminderStore(context).token())
            assertTrue(ReminderStore(context).load(profile.id).enabled)
        }
        assertTrue(DrinkReminders.refresh(context))
        assertNotNull(ReminderStore(context).token())
        LocalAccounts(PreferencesAccountStorage(context)).logout()
        assertTrue(DrinkReminders.refresh(context))
        assertNull(ReminderStore(context).token())
    }
    @Test fun recentlyAddedWaterPostponesDeliveryAndAccountChangeInvalidatesAlarm() {
        val profile = register()
        val p = plan()
        assertTrue(DrinkReminders.update(context, profile.id, p))
        val token = requireNotNull(ReminderStore(context).token())
        ReminderStore(context).save(profile.id, p.copy(enabledAt = System.currentTimeMillis() - 3_600_000))
        HydrationStore(context).save(profile.id, listOf(DailyIntake(Days.key(), 250)), System.currentTimeMillis())
        assertTrue(DrinkReminders.deliver(context, intent(profile.id, token)))
        assertEquals(0L, ReminderStore(context).load(profile.id).lastSent)
        assertFalse(base.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == 2001 })
        val newToken = requireNotNull(ReminderStore(context).token())
        register("second@example.invalid")
        assertTrue(DrinkReminders.deliver(context, intent(profile.id, newToken)))
        assertNull(ReminderStore(context).token())
    }
}
