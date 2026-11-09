package com.loantracker.app.services

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.loantracker.app.data.Loan
import com.loantracker.app.data.MpesaParser
import com.loantracker.app.data.MpesaPayment
import com.loantracker.app.data.Payment
import com.loantracker.app.data.PaymentSource
import java.time.LocalDate
import java.util.UUID

data class DetectedPayment(
    val mpesa: MpesaPayment,
    val rawMessage: String,
    val alreadyImported: Boolean,
    val withinDateRange: Boolean
)

object SmsService {

    fun hasPermission(context: android.content.Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Scan the SMS inbox for M-Pesa payments matching this loan.
     *
     * FILTERING (per user requirements):
     * 1. Body must contain "m-pesa" or "mpesa" (case-insensitive)
     * 2. Must parse as a valid M-Pesa confirmation (code + amount + date)
     * 3. Account number must match the loan's keyword (e.g. "001709")
     *    — this is the CONSTANT identifier that ties a payment to a specific loan
     * 4. Message date must fall within [loan.startDate, loan.dueDate]
     *    — messages outside this range belong to other/previous loans
     * 5. Dedup: skip if the M-Pesa code is already imported
     */
    fun scanForLoan(
        context: android.content.Context,
        loan: Loan,
        existingCodes: Set<String>,
        daysBack: Int = 90
    ): List<DetectedPayment> {
        if (!hasPermission(context)) return emptyList()

        val resolver = context.contentResolver
        val cutoff = System.currentTimeMillis() - daysBack * 24L * 60L * 60L * 1000L
        val results = mutableListOf<DetectedPayment>()

        val projection = arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE)
        val selection = "${Telephony.Sms.DATE} >= ?"
        val args = arrayOf(cutoff.toString())
        val sortOrder = "${Telephony.Sms.DATE} DESC"

        resolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection, selection, args, sortOrder
        )?.use { cursor ->
            val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
            while (cursor.moveToNext()) {
                val body = if (bodyIdx >= 0) cursor.getString(bodyIdx) ?: "" else ""

                // Filter 1: must be M-Pesa
                if (!body.contains("m-pesa", ignoreCase = true) &&
                    !body.contains("mpesa", ignoreCase = true)) continue

                // Filter 2: must parse successfully
                val parsed = MpesaParser.parse(body) ?: continue

                // Filter 3: account number must match the loan's keyword.
                // The keyword IS the account number (e.g. "001709") — it's constant
                // and uniquely identifies which loan this payment belongs to.
                val keyword = loan.keyword?.trim()
                if (!keyword.isNullOrBlank()) {
                    val accountMatches = parsed.account?.equals(keyword, ignoreCase = true) == true
                    val bodyContains = body.contains(keyword, ignoreCase = true)
                    if (!accountMatches && !bodyContains) continue
                }

                // Filter 4: message date must be within the loan's date range.
                // Messages outside [startDate, dueDate] belong to other/previous loans.
                val messageDate = parsed.date.toLocalDate()
                val withinRange = !messageDate.isBefore(loan.startDate) &&
                                  !messageDate.isAfter(loan.dueDate)
                if (!withinRange) continue

                // Filter 5: dedup
                val alreadyImported = existingCodes.contains(parsed.code.uppercase())

                results.add(DetectedPayment(parsed, body, alreadyImported, withinRange))
            }
        }

        return results
    }

    fun toPayment(detected: DetectedPayment, loanId: String): Payment = Payment(
        id = UUID.randomUUID().toString(),
        loanId = loanId,
        amount = detected.mpesa.amount,
        paidAt = detected.mpesa.date,
        mpesaCode = detected.mpesa.code,
        phone = detected.mpesa.phone,
        sender = detected.mpesa.sender,
        account = detected.mpesa.account,
        rawMessage = detected.rawMessage,
        source = PaymentSource.MPESA
    )
}
