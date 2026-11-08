package com.example.focushydrate.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.focushydrate.MainActivity
import com.example.focushydrate.R
import com.example.focushydrate.data.*
import com.example.focushydrate.localization.AppLanguage
import java.util.UUID

object DrinkReminders {
    private const val CHANNEL = "drink_water"
    private const val NOTIFICATION = 2001
    private const val ACTION = "com.example.focushydrate.DRINK_REMINDER"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val localized = AppLanguage.wrap(context)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, localized.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 26 ||
            context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    private fun pending(context: Context, token: String = "", account: String = ""): PendingIntent =
        PendingIntent.getBroadcast(context, NOTIFICATION,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION).putExtra("token", token).putExtra("account", account),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    @Synchronized
    fun update(context: Context, account: String, plan: ReminderPlan): Boolean {
        if (!ReminderStore(context).save(account, plan)) return false
        return refresh(context)
    }

    @Synchronized
    fun refresh(context: Context, dismissNotification: Boolean = true): Boolean = runCatching {
        createChannel(context)
        val store = ReminderStore(context)
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(pending(context))
        if (dismissNotification) NotificationManagerCompat.from(context).cancel(NOTIFICATION)
        check(store.setToken(null))
        val profile = LocalAccounts(PreferencesAccountStorage(context)).current() ?: return true
        val plan = store.load(profile.id)
        if (!allowed(context)) return true
        val next = nextReminder(plan, System.currentTimeMillis(), HydrationStore(context).lastDrink(profile.id)) ?: return true
        val token = UUID.randomUUID().toString()
        check(store.setToken(token))
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending(context, token, profile.id))
        true
    }.onFailure { android.util.Log.w("DrinkReminders", "Could not schedule reminder", it) }.getOrDefault(false)

    @Synchronized
    fun deliver(context: Context, intent: Intent): Boolean = runCatching {
        val store = ReminderStore(context)
        if (intent.action != ACTION || intent.getStringExtra("token") != store.token() || store.token() == null) return false
        val profile = LocalAccounts(PreferencesAccountStorage(context)).current() ?: return refresh(context)
        if (intent.getStringExtra("account") != profile.id) return refresh(context)
        val plan = store.load(profile.id)
        val now = System.currentTimeMillis()
        val due = nextReminder(plan, now, HydrationStore(context).lastDrink(profile.id))
        if (due == now && allowed(context)) {
            check(store.save(profile.id, plan.copy(lastSent = now)))
            val localized = AppLanguage.wrap(context)
            val open = PendingIntent.getActivity(context, NOTIFICATION,
                Intent(context, MainActivity::class.java).putExtra("open_tracker", true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(localized, CHANNEL)
                .setSmallIcon(R.drawable.ic_water_notification)
                .setContentTitle(localized.getString(R.string.reminder_title))
                .setContentText(localized.getString(R.string.reminder_body))
                .setContentIntent(open).setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setCategory(NotificationCompat.CATEGORY_REMINDER).build()
            // Recheck immediately before posting in case permission was revoked.
            try {
                if (allowed(context)) NotificationManagerCompat.from(context).notify(NOTIFICATION, notification)
            } catch (e: SecurityException) {
                return refresh(context)
            }
            refresh(context, dismissNotification = false)
        } else refresh(context)
    }.onFailure { android.util.Log.w("DrinkReminders", "Could not deliver reminder", it) }.getOrDefault(false)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread({
            try {
                when (intent.action) {
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                    Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                    "android.app.action.APP_BLOCK_STATE_CHANGED",
                    "android.app.action.NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED" -> DrinkReminders.refresh(context)
                    else -> DrinkReminders.deliver(context, intent)
                }
            } finally { pending.finish() }
        }, "drink-reminder").start()
    }
}
