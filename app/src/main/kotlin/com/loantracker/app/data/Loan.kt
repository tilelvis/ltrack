package com.loantracker.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class PaymentInterval { WEEKLY, CUSTOM }

@Entity(tableName = "loans")
data class Loan(
    @PrimaryKey val id: String,
    val title: String,
    val principal: Double,
    val interestRate: Double = 0.0,
    val startDate: LocalDate,
    val dueDate: LocalDate,
    val expectedPerInterval: Double = 0.0,
    val interval: PaymentInterval = PaymentInterval.WEEKLY,
    val customIntervalDays: Int = 7,
    val notes: String? = null,
    val keyword: String? = null,
    val lenderName: String? = null,
    val lenderPhone: String? = null,
    val lenderEmail: String? = null,
    val isHidden: Boolean = false
) {
    val totalPayable: Double
        get() = if (interestRate <= 0.0) principal
                else principal + (principal * interestRate / 100.0)

    val daysRemaining: Long
        get() {
            val now = LocalDate.now()
            return if (now.isAfter(dueDate)) 0L
                   else ChronoUnit.DAYS.between(now, dueDate) + 1
        }
}
