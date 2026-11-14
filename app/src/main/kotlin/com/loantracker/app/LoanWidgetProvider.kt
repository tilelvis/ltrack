package com.loantracker.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.loantracker.app.data.AppDatabase
import com.loantracker.app.ui.isRepaid
import com.loantracker.app.ui.sumsByLoan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Home-screen widget: shows the loan that is at the front of the dashboard card stack
 * (balance, progress and days left). Tap it to open the app.
 */
class LoanWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        scope.launch {
            try {
                render(context.applicationContext, appWidgetManager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val PREFS = "widget_prefs"
        private const val KEY_FRONT = "front_loan_id"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Remember which loan is in front on the dashboard and refresh the widget. */
        fun setFrontLoan(context: Context, loanId: String) {
            val app = context.applicationContext
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_FRONT, loanId).apply()
            refresh(app)
        }

        /** Redraw every placed widget from the latest data. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, LoanWidgetProvider::class.java))
            if (ids.isEmpty()) return
            scope.launch { render(app, manager, ids) }
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val db = AppDatabase.get(context)
            val loans = db.loanDao().observeAll().first()
            val sums = db.paymentDao().observeAll().first().sumsByLoan()

            val active = loans.filter { !it.isRepaid(sums[it.id] ?: 0.0) }
            val completed = loans.size - active.size
            val frontId = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_FRONT, null)
            val front = active.firstOrNull { it.id == frontId } ?: active.firstOrNull()

            val views = RemoteViews(context.packageName, R.layout.widget_loan)

            if (front != null) {
                val paid = sums[front.id] ?: 0.0
                val total = front.totalPayable
                val balance = (total - paid).coerceAtLeast(0.0)
                val percent = if (total > 0) ((paid / total) * 100).toInt().coerceIn(0, 100) else 0
                val days = front.daysRemaining

                views.setTextViewText(R.id.widget_title, front.title)
                views.setTextViewText(R.id.widget_balance, money(balance))
                views.setTextViewText(R.id.widget_sub, "of ${money(total)}")
                views.setViewVisibility(R.id.widget_progress, View.VISIBLE)
                views.setProgressBar(R.id.widget_progress, 100, percent, false)
                views.setTextViewText(R.id.widget_percent, "$percent% repaid")
                views.setViewVisibility(R.id.widget_due, View.VISIBLE)
                val daysText = when {
                    days <= 0L -> "Overdue"
                    days == 1L -> "1 day left"
                    else -> "$days days left"
                }
                views.setTextViewText(R.id.widget_due, daysText)
                views.setTextColor(
                    R.id.widget_due,
                    if (days <= 0L) Color.parseColor("#FFC24B") else Color.WHITE
                )
                if (active.size > 1) {
                    val position = active.indexOfFirst { it.id == front.id } + 1
                    views.setViewVisibility(R.id.widget_count, View.VISIBLE)
                    views.setTextViewText(R.id.widget_count, "$position of ${active.size}")
                } else {
                    views.setViewVisibility(R.id.widget_count, View.GONE)
                }
                views.setContentDescription(
                    R.id.widget_root,
                    "${front.title}, ${money(balance)} outstanding, $daysText"
                )
            } else {
                views.setTextViewText(R.id.widget_title, "Loan Tracker")
                views.setViewVisibility(R.id.widget_count, View.GONE)
                views.setViewVisibility(R.id.widget_progress, View.GONE)
                views.setViewVisibility(R.id.widget_due, View.GONE)
                if (completed > 0) {
                    views.setTextViewText(R.id.widget_balance, "All clear")
                    views.setTextViewText(
                        R.id.widget_sub,
                        "$completed ${if (completed == 1) "loan" else "loans"} cleared"
                    )
                } else {
                    views.setTextViewText(R.id.widget_balance, "No loans yet")
                    views.setTextViewText(R.id.widget_sub, "Tap to add your first loan")
                }
                views.setTextViewText(R.id.widget_percent, "")
            }

            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pi = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)

            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun money(v: Double): String = String.format(Locale.US, "KSh %,.0f", v)
    }
}
