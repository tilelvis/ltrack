package com.loantracker.app.data

import java.time.LocalDate
import kotlin.math.ceil

enum class Severity { NORMAL, MODERATE, ELEVATED, CRITICAL, PAID }

data class DailyPaymentRecommendation(
    val dailyAmount: Double,
    val remainingBalance: Double,
    val daysRemaining: Long,
    val severity: Severity,
    val message: String,
    val isOnTrack: Boolean,
    val projectedFinalDay: Double
) {
    val needsActionToday: Boolean
        get() = severity == Severity.CRITICAL || severity == Severity.ELEVATED
}

object DailyPaymentCalculator {

    fun calculate(loan: Loan, totalPaid: Double): DailyPaymentRecommendation {
        val balance = (loan.totalPayable - totalPaid).coerceAtLeast(0.0)

        if (balance <= 0.01) {
            return DailyPaymentRecommendation(
                dailyAmount = 0.0,
                remainingBalance = 0.0,
                daysRemaining = loan.daysRemaining,
                severity = Severity.PAID,
                message = "Loan fully repaid!",
                isOnTrack = true,
                projectedFinalDay = 0.0
            )
        }

        val days = loan.daysRemaining
        val isOverdue = days <= 0L

        if (isOverdue) {
            return DailyPaymentRecommendation(
                dailyAmount = balance,
                remainingBalance = balance,
                daysRemaining = 0,
                severity = Severity.CRITICAL,
                message = "OVERDUE — pay the full balance today",
                isOnTrack = false,
                projectedFinalDay = balance
            )
        }

        val rawDaily = balance / days
        val daily = roundUp(rawDaily)

        val severity = when {
            days <= 3 -> Severity.CRITICAL
            days <= 7 -> Severity.ELEVATED
            days <= 14 -> Severity.MODERATE
            rawDaily > 500 -> Severity.ELEVATED
            else -> Severity.NORMAL
        }

        val message = when (severity) {
            Severity.CRITICAL -> "Critical — only $days days left. Pay today!"
            Severity.ELEVATED -> "Elevated — $days days left. Stay on track."
            Severity.MODERATE -> "On track — $days days remaining."
            else -> "Steady — $days days remaining."
        }

        return DailyPaymentRecommendation(
            dailyAmount = daily,
            remainingBalance = balance,
            daysRemaining = days,
            severity = severity,
            message = message,
            isOnTrack = severity != Severity.CRITICAL && severity != Severity.ELEVATED,
            projectedFinalDay = daily * days
        )
    }

    private fun roundUp(value: Double): Double {
        if (value <= 50) return ceil(value / 50.0) * 50
        if (value <= 200) return ceil(value / 25.0) * 25
        if (value <= 500) return ceil(value / 50.0) * 50
        return ceil(value / 100.0) * 100
    }
}
