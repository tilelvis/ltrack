package com.loantracker.app.services

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.loantracker.app.MainActivity
import com.loantracker.app.R
import com.loantracker.app.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar
import kotlin.math.ceil

/**
 * Two reminders a day, 8:00 am and 5:00 pm, for as long as at least one loan is still active.
 *
 * Each alarm is a one-shot that re-arms the next day when it fires, which survives Doze better than
 * a repeating alarm. When no active loan is left the chain simply stops; opening the app after
 * adding a loan arms it again, and a reboot re-arms it too (see BootReceiver).
 */
object ReminderScheduler {
    private const val ACTION = "com.loantracker.app.REMINDER"
    private const val PREFS = "app_prefs"
    private const val KEY_ENABLED = "reminders_enabled"
    private const val REQ_BASE = 2001
    private const val CHANNEL_DAILY = "loan_tracker_daily"

    /** Slot 0 = morning, slot 1 = evening. */
    private val SLOTS = listOf(8 to 0, 17 to 0)

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
        if (on) ensureScheduled(context) else cancelAll(context)
    }

    /** Arms both daily alarms. Safe to call as often as you like: the same alarm is just replaced. */
    fun ensureScheduled(context: Context) {
        if (!enabled(context)) return
        SLOTS.indices.forEach { scheduleNext(context, it) }
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        SLOTS.indices.forEach { am.cancel(pending(context, it)) }
    }

    private fun pending(context: Context, slot: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION
            putExtra("slot", slot)
        }
        return PendingIntent.getBroadcast(
            context, REQ_BASE + slot, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleNext(context: Context, slot: Int) {
        val (hour, minute) = SLOTS[slot]
        val at = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 1_000) add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context, slot)
        val exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exactOk) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Called when an alarm fires (or from the Settings test button). Decides what, if anything, to say. */
    suspend fun fire(context: Context, slot: Int, test: Boolean = false) {
        if (!test && !enabled(context)) return
        val db = AppDatabase.get(context)
        val loans = db.loanDao().observeAll().first() // hidden loans are left out on purpose
        val payments = db.paymentDao().observeAll().first()
        val paidBy = payments.groupBy { it.loanId }.mapValues { (_, list) -> list.sumOf { it.amount } }
        val active = loans.filter { (paidBy[it.id] ?: 0.0) + 0.5 < it.totalPayable }.sortedBy { it.dueDate }

        if (active.isEmpty()) {
            // Nothing to remind about: don't re-arm. Adding a loan and opening the app starts it again.
            if (test) post(context, slot, "Reminders are ready", "They start as soon as you add a loan.")
            return
        }
        if (!test) scheduleNext(context, slot) // re-arm first so a hiccup below can't end the chain

        val first = active.first()
        val balance = (first.totalPayable - (paidBy[first.id] ?: 0.0)).coerceAtLeast(0.0)
        val daysLeft = first.daysRemaining
        val today = ceil(balance / daysLeft.coerceAtLeast(1L)).toLong()
        val outstanding = active.sumOf { (it.totalPayable - (paidBy[it.id] ?: 0.0)).coerceAtLeast(0.0) }
        val total = money(outstanding)
        val spread = if (active.size == 1) "outstanding" else "outstanding across ${active.size} loans"
        val ids = active.map { it.id }.toSet()
        val paidToday = payments.any { it.loanId in ids && it.paidAt.toLocalDate() == LocalDate.now() }
        val overdue = active.any { it.daysRemaining <= 0 }
        val day = if (daysLeft == 1L) "1 day" else "$daysLeft days"

        val (title, text) = when {
            slot == 1 && !test && paidToday ->
                "You’re on track today" to "Thanks for paying today. $total $spread."
            slot == 1 && !test ->
                "Still time to pay today" to
                    "${money(today.toDouble())} for ${first.title} keeps you on pace. Don’t let it roll into tomorrow."
            overdue ->
                "${first.title} is overdue" to "Clear ${money(balance)} to stop late fees building up. $total $spread."
            else ->
                "Pay ${money(today.toDouble())} today · ${first.title}" to "$day left. $total $spread."
        }
        post(context, slot, title, text)
    }

    private fun money(v: Double) = "KSh %,.0f".format(v)

    private fun post(context: Context, slot: Int, title: String, text: String) {
        NotificationService.createChannels(context) // the app may not have been opened since boot
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_DAILY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1001 + slot, notif)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slot = intent.getIntExtra("slot", 0)
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.fire(context.applicationContext, slot)
            } finally {
                result.finish()
            }
        }
    }
}

/** Re-arms the reminders after a reboot, an app update, or a clock or time-zone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.ensureScheduled(context.applicationContext)
    }
}
