package com.loantracker.app.services

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.loantracker.app.data.Loan
import com.loantracker.app.data.Payment
import com.loantracker.app.data.PaymentSource
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfService {

    private val PAGE_WIDTH = 595
    private val PAGE_HEIGHT = 842

    fun generateLoanReport(
        context: Context, loan: Loan, payments: List<Payment>, totalPaid: Double
    ): File? {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        val y = drawHeader(canvas, "LOAN REPORT", loan.title)
        drawLoanSummary(canvas, y, loan, totalPaid)
        drawPaymentsTable(canvas, 340f, payments)
        doc.finishPage(page)

        val file = File(context.cacheDir, "loan_report_${sanitize(loan.title)}_${timestamp()}.pdf")
        doc.writeTo(FileOutputStream(file))
        doc.close()
        shareFile(context, file, "Loan report for ${loan.title}")
        return file
    }

    fun generatePaymentReceipt(
        context: Context, loan: Loan, payment: Payment, totalPaid: Double
    ): File? {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply { textSize = 20f; isFakeBoldText = true; isAntiAlias = true }
        val labelPaint = Paint().apply { textSize = 11f; isAntiAlias = true; setARGB(255, 107, 112, 128) }
        val valuePaint = Paint().apply { textSize = 12f; isAntiAlias = true; isFakeBoldText = true }
        val amountPaint = Paint().apply { textSize = 44f; isAntiAlias = true; isFakeBoldText = true; setARGB(255, 31, 174, 13) }
        val boxPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2f; setARGB(255, 31, 174, 13) }
        val linePaint = Paint().apply { strokeWidth = 1f; setARGB(255, 229, 231, 235) }

        canvas.drawText("PAYMENT RECEIPT", 40f, 50f, titlePaint)
        canvas.drawText("Loan Tracker", 40f, 70f, labelPaint)
        canvas.drawText(SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date()), 40f, 85f, labelPaint)
        canvas.drawLine(40f, 95f, (PAGE_WIDTH - 40).toFloat(), 95f, linePaint)

        canvas.drawText("Loan: ${loan.title}", 40f, 120f, valuePaint)
        loan.lenderName?.let { canvas.drawText("Lender: $it", 40f, 140f, valuePaint) }

        val amtText = "Ksh %,.2f".format(payment.amount)
        val amtWidth = amountPaint.measureText(amtText)
        canvas.drawText(amtText, (PAGE_WIDTH - amtWidth) / 2, 220f, amountPaint)

        canvas.drawText("Paid on ${SimpleDateFormat("EEEE, d MMMM y • h:mm a", Locale.getDefault()).format(Date())}",
            (PAGE_WIDTH - 300) / 2f, 250f, labelPaint)

        payment.mpesaCode?.let {
            val boxLeft = (PAGE_WIDTH - 300) / 2f
            canvas.drawRect(boxLeft, 270f, boxLeft + 300, 310f, boxPaint)
            canvas.drawText("M-PESA TRANSACTION CODE", boxLeft + 10, 287f, labelPaint)
            canvas.drawText(it, boxLeft + 10, 302f, valuePaint)
        }

        var dy = 340f
        val balance = (loan.totalPayable - totalPaid).coerceAtLeast(0.0)
        val rows = listOf(
            "Source" to (if (payment.source == PaymentSource.MPESA) "M-Pesa" else "Manual"),
            "Sender" to (payment.sender ?: "—"),
            "Loan principal" to "Ksh %,.0f".format(loan.principal),
            "Total payable" to "Ksh %,.0f".format(loan.totalPayable),
            "Total paid so far" to "Ksh %,.0f".format(totalPaid),
            "Remaining balance" to "Ksh %,.0f".format(balance),
            "Due date" to SimpleDateFormat("d MMM y", Locale.getDefault()).format(java.sql.Date.valueOf(loan.dueDate.toString()))
        )
        for ((label, value) in rows) {
            canvas.drawText(label, 40f, dy, labelPaint)
            canvas.drawText(value, 300f, dy, valuePaint)
            dy += 22f
        }

        canvas.drawLine(40f, dy + 10, (PAGE_WIDTH - 40).toFloat(), dy + 10, linePaint)
        canvas.drawText("Receipt #${payment.mpesaCode ?: payment.id.take(8)}", 40f, dy + 30f, labelPaint)

        doc.finishPage(page)

        val file = File(context.cacheDir, "receipt_${payment.mpesaCode ?: payment.id.take(8)}_${timestamp()}.pdf")
        doc.writeTo(FileOutputStream(file))
        doc.close()
        shareFile(context, file, "Payment receipt for ${loan.title}")
        return file
    }

    private fun drawHeader(canvas: Canvas, label: String, loanTitle: String): Float {
        val titlePaint = Paint().apply { textSize = 22f; isFakeBoldText = true; isAntiAlias = true }
        val subPaint = Paint().apply { textSize = 12f; isAntiAlias = true; setARGB(255, 107, 112, 128) }
        val linePaint = Paint().apply { strokeWidth = 1f; setARGB(255, 229, 231, 235) }

        canvas.drawText(label, 40f, 50f, titlePaint)
        canvas.drawText("Loan Tracker • ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())}", 40f, 70f, subPaint)
        canvas.drawText(loanTitle, 40f, 90f, subPaint)
        canvas.drawLine(40f, 100f, (PAGE_WIDTH - 40).toFloat(), 100f, linePaint)
        return 120f
    }

    private fun drawLoanSummary(canvas: Canvas, startY: Float, loan: Loan, totalPaid: Double) {
        val labelPaint = Paint().apply { textSize = 11f; isAntiAlias = true; setARGB(255, 107, 112, 128) }
        val valuePaint = Paint().apply { textSize = 14f; isFakeBoldText = true; isAntiAlias = true }
        val balance = (loan.totalPayable - totalPaid).coerceAtLeast(0.0)
        val progress = if (loan.totalPayable > 0) (totalPaid / loan.totalPayable * 100).toInt() else 0

        val cards = listOf(
            "Payable" to "Ksh %,.0f".format(loan.totalPayable),
            "Paid" to "Ksh %,.0f".format(totalPaid),
            "Balance" to "Ksh %,.0f".format(balance),
            "Progress" to "$progress%"
        )
        val cardWidth = (PAGE_WIDTH - 80 - 30) / 4
        cards.forEachIndexed { i, (label, value) ->
            val x = 40f + i * (cardWidth + 10)
            canvas.drawText(label, x, startY, labelPaint)
            canvas.drawText(value, x, startY + 20, valuePaint)
        }
    }

    private fun drawPaymentsTable(canvas: Canvas, startY: Float, payments: List<Payment>) {
        val headerPaint = Paint().apply { textSize = 10f; isFakeBoldText = true; isAntiAlias = true; setARGB(255, 107, 112, 128) }
        val rowPaint = Paint().apply { textSize = 10f; isAntiAlias = true }
        val linePaint = Paint().apply { strokeWidth = 1f; setARGB(255, 229, 231, 235) }

        canvas.drawLine(40f, startY, (PAGE_WIDTH - 40).toFloat(), startY, linePaint)
        canvas.drawText("Date", 40f, startY + 15f, headerPaint)
        canvas.drawText("Source", 200f, startY + 15f, headerPaint)
        canvas.drawText("Code", 300f, startY + 15f, headerPaint)
        canvas.drawText("Amount", 450f, startY + 15f, headerPaint)
        canvas.drawLine(40f, startY + 20f, (PAGE_WIDTH - 40).toFloat(), startY + 20f, linePaint)

        var y = startY + 35f
        for (p in payments) {
            canvas.drawText(SimpleDateFormat("d MMM y", Locale.getDefault()).format(java.sql.Date.valueOf(p.paidAt.toLocalDate().toString())), 40f, y, rowPaint)
            canvas.drawText(if (p.source.name == "MPESA") "M-Pesa" else "Manual", 200f, y, rowPaint)
            canvas.drawText(p.mpesaCode ?: "—", 300f, y, rowPaint)
            canvas.drawText("Ksh %,.0f".format(p.amount), 450f, y, rowPaint)
            y += 18f
        }
    }

    private fun shareFile(context: Context, file: File, subject: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share PDF"))
    }

    private fun sanitize(s: String): String = s.replace(Regex("[^a-zA-Z0-9]"), "_")
    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}
