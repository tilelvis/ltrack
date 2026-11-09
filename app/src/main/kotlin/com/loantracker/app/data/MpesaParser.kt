package com.loantracker.app.data

import java.time.LocalDateTime
import java.util.regex.Pattern

data class MpesaPayment(
    val code: String,
    val amount: Double,
    val sender: String,
    val date: LocalDateTime,
    val account: String? = null,
    val phone: String? = null
)

object MpesaParser {

    private val CODE_PATTERN = Pattern.compile(
        """\b([A-Z0-9]{8,12})\s+Confirmed""",
        Pattern.CASE_INSENSITIVE
    )

    private val AMOUNT_PATTERNS = listOf(
        Pattern.compile("""(?:Ksh|KES)\s?([\d,]+(?:\.\d{1,2})?)\s+(?:sent|has been sent)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:you\s+have\s+sent|sent)\s+(?:Ksh|KES)\s?([\d,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:Ksh|KES)\s?([\d,]+(?:\.\d{1,2})?)\s+(?:received|has\s+been\s+received)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""Confirmed\.?\s*(?:Ksh|KES)\s?([\d,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE)
    )

    private val PHONE_PATTERN = Pattern.compile("""\b(?:254|0)?7\d{8}\b""")
    private val ACCOUNT_PATTERN = Pattern.compile("""(?:for\s+account|Account)\s+([A-Z0-9]+)""", Pattern.CASE_INSENSITIVE)
    private val DATE_PATTERN = Pattern.compile("""(\d{1,2})/(\d{1,2})/(\d{2,4})\s+at\s+(\d{1,2}):(\d{2})\s*(AM|PM)?""", Pattern.CASE_INSENSITIVE)

    private val SENDER_PATTERNS = listOf(
        Pattern.compile("""sent to\s+(.+?)\s+for\s+account""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:sent to|to)\s+([A-Z][A-Z\s.']{2,50}?)\s+(?:on|at|\d)"""),
        Pattern.compile("""received from\s+([A-Z][A-Z\s.']{2,50}?)\s+(?:on|at|\d)""")
    )

    fun parse(message: String): MpesaPayment? {
        val code = CODE_PATTERN.matcher(message).let { m ->
            if (m.find()) m.group(1) else return null
        }

        val amount = AMOUNT_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.matcher(message).let { m ->
                if (m.find()) m.group(1)?.replace(",", "")?.toDoubleOrNull() else null
            }
        } ?: return null

        val sender = SENDER_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.matcher(message).let { m ->
                if (m.find()) {
                    m.group(1)?.trim()?.replace(Regex("\\s+\\d.*$"), "")?.trim()?.removeSuffix(".")?.trim()
                } else null
            }
        } ?: "Unknown"

        val account = ACCOUNT_PATTERN.matcher(message).let { m ->
            if (m.find()) m.group(1) else null
        }

        val phone = PHONE_PATTERN.matcher(message).let { m ->
            if (m.find()) m.group() else null
        }

        val date = DATE_PATTERN.matcher(message).let { m ->
            if (m.find()) {
                try {
                    val day = m.group(1).toInt()
                    val month = m.group(2).toInt()
                    var year = m.group(3).toInt()
                    if (year < 100) year += 2000
                    var hour = m.group(4).toInt()
                    val minute = m.group(5).toInt()
                    val ampm = m.group(6)?.uppercase()
                    if (ampm == "PM" && hour < 12) hour += 12
                    if (ampm == "AM" && hour == 12) hour = 0
                    LocalDateTime.of(year, month, day, hour, minute)
                } catch (e: Exception) {
                    LocalDateTime.now()
                }
            } else LocalDateTime.now()
        }

        return MpesaPayment(code, amount, sender, date, account, phone)
    }

    fun formatKes(amount: Double): String = "Ksh %,.2f".format(amount)
}
