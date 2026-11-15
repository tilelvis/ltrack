package com.loantracker.app.services

import android.content.Context

/** The late-fee percentage used for the "if you miss the due date" estimate. */
object PenaltyPrefs {
    private const val FILE = "app_prefs"
    private const val KEY = "late_penalty_pct"
    const val DEFAULT = 5.0

    fun get(context: Context): Double =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getFloat(KEY, DEFAULT.toFloat()).toDouble()

    fun set(context: Context, percent: Double) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putFloat(KEY, percent.toFloat()).apply()
    }
}
