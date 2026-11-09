package com.loantracker.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

enum class PaymentSource { MPESA, MANUAL }

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = Loan::class,
            parentColumns = ["id"],
            childColumns = ["loanId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("loanId"), Index("mpesaCode")]
)
data class Payment(
    @PrimaryKey val id: String,
    val loanId: String,
    val amount: Double,
    val paidAt: LocalDateTime,
    val mpesaCode: String? = null,
    val phone: String? = null,
    val sender: String? = null,
    val account: String? = null,
    val rawMessage: String? = null,
    val source: PaymentSource = PaymentSource.MANUAL,
    val notes: String? = null
)
