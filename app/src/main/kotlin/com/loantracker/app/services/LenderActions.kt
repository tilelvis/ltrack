package com.loantracker.app.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.loantracker.app.data.Loan

object LenderActions {

    fun call(context: Context, phone: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            toast(context, "Could not open dialer")
        }
    }

    fun whatsapp(context: Context, loan: Loan, totalPaid: Double) {
        val phone = normalizePhone(loan.lenderPhone) ?: run {
            toast(context, "No lender phone set")
            return
        }
        val balance = (loan.totalPayable - totalPaid).coerceAtLeast(0.0)
        val msg = "Hello ${loan.lenderName ?: ""}, regarding my loan \"${loan.title}\". Remaining balance: Ksh %,.0f. Due: ${loan.dueDate}.".format(balance)
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone?text=${Uri.encode(msg)}"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            toast(context, "WhatsApp not available")
        }
    }

    fun sms(context: Context, phone: String, loan: Loan, totalPaid: Double) {
        val balance = (loan.totalPayable - totalPaid).coerceAtLeast(0.0)
        val msg = "Hello, regarding my loan \"${loan.title}\". Remaining balance: Ksh %,.0f.".format(balance)
        try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone"))
            intent.putExtra("sms_body", msg)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            toast(context, "Could not open SMS app")
        }
    }

    fun email(context: Context, loan: Loan, totalPaid: Double, email: String) {
        val balance = (loan.totalPayable - totalPaid).coerceAtLeast(0.0)
        val subject = "About loan: ${loan.title}"
        val body = "Hello ${loan.lenderName ?: ""},\n\nRegarding my loan \"${loan.title}\". Remaining balance: Ksh %,.0f. Due: ${loan.dueDate}.".format(balance)
        try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
            intent.putExtra(Intent.EXTRA_SUBJECT, subject)
            intent.putExtra(Intent.EXTRA_TEXT, body)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(Intent.createChooser(intent, "Send email"))
        } catch (e: Exception) {
            toast(context, "No email app available")
        }
    }

    private fun normalizePhone(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var s = raw.replace(Regex("[\\s\\-()]"), "")
        if (s.startsWith("+")) s = s.substring(1)
        if (s.startsWith("00")) s = s.substring(2)
        if (s.startsWith("07") || s.startsWith("01")) s = "254${s.substring(1)}"
        return s
    }

    private fun toast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}
