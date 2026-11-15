package com.loantracker.app.services

import android.content.Context
import android.net.Uri
import com.loantracker.app.data.AppDatabase
import com.loantracker.app.data.Loan
import com.loantracker.app.data.Payment
import com.loantracker.app.data.PaymentInterval
import com.loantracker.app.data.PaymentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Plain-JSON backup of every loan and payment, so data survives a reinstall or a database reset.
 * Restore merges: loans and payments in the file are added or updated, nothing else is removed.
 */
object BackupService {
    private const val FORMAT = 1

    data class Counts(val loans: Int, val payments: Int)

    suspend fun backup(context: Context, uri: Uri): Result<Counts> = withContext(Dispatchers.IO) {
        runCatching {
            val db = AppDatabase.get(context)
            val loans = db.loanDao().observeAllIncludingHidden().first()
            val payments = db.paymentDao().observeAll().first()
            val root = JSONObject()
                .put("app", "LoanTracker")
                .put("format", FORMAT)
                .put("exportedAt", LocalDateTime.now().toString())
                .put("loans", JSONArray().also { a -> loans.forEach { a.put(loanJson(it)) } })
                .put("payments", JSONArray().also { a -> payments.forEach { a.put(paymentJson(it)) } })
            val out = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Couldn’t open the file for writing")
            out.bufferedWriter().use { it.write(root.toString(2)) }
            Counts(loans.size, payments.size)
        }
    }

    suspend fun restore(context: Context, uri: Uri): Result<Counts> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IOException("Couldn’t open the file")
            val root = JSONObject(text)
            if (root.optString("app") != "LoanTracker") throw IOException("This isn’t a Loan Tracker backup")
            val loanArr = root.optJSONArray("loans") ?: JSONArray()
            val payArr = root.optJSONArray("payments") ?: JSONArray()
            val loans = (0 until loanArr.length()).map { loanFrom(loanArr.getJSONObject(it)) }
            val payments = (0 until payArr.length()).map { paymentFrom(payArr.getJSONObject(it)) }

            val db = AppDatabase.get(context)
            val loanDao = db.loanDao()
            val ids = loans.map { it.id }.toSet()
            loans.forEach { l ->
                // Update in place: replacing a loan row would cascade-delete its payments.
                if (loanDao.getById(l.id) != null) loanDao.update(l) else loanDao.upsert(l)
            }
            val known = ids + loanDao.observeAllIncludingHidden().first().map { it.id }
            val usable = payments.filter { it.loanId in known }
            usable.forEach { db.paymentDao().insert(it) }
            Counts(loans.size, usable.size)
        }
    }

    // ---------------------------------------------------------------- JSON mapping

    private fun JSONObject.str(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)

    private fun loanJson(l: Loan) = JSONObject()
        .put("id", l.id).put("title", l.title).put("principal", l.principal)
        .put("interestRate", l.interestRate).put("startDate", l.startDate.toString())
        .put("dueDate", l.dueDate.toString()).put("expectedPerInterval", l.expectedPerInterval)
        .put("interval", l.interval.name).put("customIntervalDays", l.customIntervalDays)
        .put("notes", l.notes ?: JSONObject.NULL).put("keyword", l.keyword ?: JSONObject.NULL)
        .put("lenderName", l.lenderName ?: JSONObject.NULL).put("lenderPhone", l.lenderPhone ?: JSONObject.NULL)
        .put("lenderEmail", l.lenderEmail ?: JSONObject.NULL).put("isHidden", l.isHidden)

    private fun loanFrom(o: JSONObject) = Loan(
        id = o.getString("id"),
        title = o.getString("title"),
        principal = o.getDouble("principal"),
        interestRate = o.optDouble("interestRate", 0.0),
        startDate = LocalDate.parse(o.getString("startDate")),
        dueDate = LocalDate.parse(o.getString("dueDate")),
        expectedPerInterval = o.optDouble("expectedPerInterval", 0.0),
        interval = o.str("interval")?.let { runCatching { PaymentInterval.valueOf(it) }.getOrNull() }
            ?: PaymentInterval.WEEKLY,
        customIntervalDays = o.optInt("customIntervalDays", 7),
        notes = o.str("notes"),
        keyword = o.str("keyword"),
        lenderName = o.str("lenderName"),
        lenderPhone = o.str("lenderPhone"),
        lenderEmail = o.str("lenderEmail"),
        isHidden = o.optBoolean("isHidden", false)
    )

    private fun paymentJson(p: Payment) = JSONObject()
        .put("id", p.id).put("loanId", p.loanId).put("amount", p.amount)
        .put("paidAt", p.paidAt.toString()).put("mpesaCode", p.mpesaCode ?: JSONObject.NULL)
        .put("phone", p.phone ?: JSONObject.NULL).put("sender", p.sender ?: JSONObject.NULL)
        .put("account", p.account ?: JSONObject.NULL).put("rawMessage", p.rawMessage ?: JSONObject.NULL)
        .put("source", p.source.name).put("notes", p.notes ?: JSONObject.NULL)

    private fun paymentFrom(o: JSONObject) = Payment(
        id = o.getString("id"),
        loanId = o.getString("loanId"),
        amount = o.getDouble("amount"),
        paidAt = LocalDateTime.parse(o.getString("paidAt")),
        mpesaCode = o.str("mpesaCode"),
        phone = o.str("phone"),
        sender = o.str("sender"),
        account = o.str("account"),
        rawMessage = o.str("rawMessage"),
        source = o.str("source")?.let { runCatching { PaymentSource.valueOf(it) }.getOrNull() } ?: PaymentSource.MANUAL,
        notes = o.str("notes")
    )
}
