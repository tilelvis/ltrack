package com.loantracker.app.services

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.loantracker.app.MainActivity
import com.loantracker.app.R
import java.util.Calendar

object NotificationService {

    private const val CHANNEL_DAILY = "loan_tracker_daily"
    private const val CHANNEL_CRITICAL = "loan_tracker_critical"
    private const val BASE_REMINDER_ID = 1001
    private const val MAX_REMINDERS = 5

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val daily = NotificationChannel(
            CHANNEL_DAILY, "Daily Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Daily payment reminder notifications"
            enableVibration(true)
        }

        val critical = NotificationChannel(
            CHANNEL_CRITICAL, "Critical Alerts",
            NotificationManager.IMPORTANCE_MAX
        ).apply {
            description = "Critical payment alerts"
        }

        nm.createNotificationChannel(daily)
        nm.createNotificationChannel(critical)
    }

    fun scheduleDailyReminder(
        context: Context, hour: Int, minute: Int, slotId: Int,
        loanTitle: String, amount: String
    ) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.loantracker.app.REMINDER"
            putExtra("loan_title", loanTitle)
            putExtra("amount", amount)
            putExtra("slot_id", slotId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, BASE_REMINDER_ID + slotId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    fun cancelAllReminders(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (slotId in 0 until MAX_REMINDERS) {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = "com.loantracker.app.REMINDER"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, BASE_REMINDER_ID + slotId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pendingIntent)
        }
    }

    fun showPaymentImported(context: Context, amount: String, code: String, loanTitle: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(context, CHANNEL_CRITICAL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Payment auto-imported")
            .setContentText("Ksh $amount from M-Pesa ($code) — $loanTitle")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .build()
        nm.notify(998, notif)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val loanTitle = intent.getStringExtra("loan_title") ?: "your loan"
        val amount = intent.getStringExtra("amount") ?: ""

        val notif = NotificationCompat.Builder(context, "loan_tracker_daily")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Loan Tracker reminder")
            .setContentText(if (amount.isNotEmpty()) "Pay Ksh $amount for \"$loanTitle\" today." else "Don't forget to pay \"$loanTitle\" today.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val slotId = intent.getIntExtra("slot_id", 0)
        nm.notify(1001 + slotId, notif)
    }
}
