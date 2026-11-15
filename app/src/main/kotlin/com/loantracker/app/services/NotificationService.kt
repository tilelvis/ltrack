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
