package com.loantracker.app.services

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.loantracker.app.data.Loan
import com.loantracker.app.data.Payment
import com.loantracker.app.data.PaymentSource
import com.loantracker.app.ui.repaymentNote
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Premium PDF output: branded header band, summary card, running-balance statement
 * table with pagination, "PAID IN FULL" stamp, and a receipt-style payment slip.
 */
object PdfService {

    private const val W = 595
    private const val H = 842
    private const val M = 40f
    private const val BAND_H = 132f
    private const val ROW_H = 22f
    private const val CONTENT_BOTTOM = 788f

    private val BRAND = Color.parseColor("#0F3D2E")
    private val BRAND_SOFT = Color.parseColor("#1B5A43")
    private val PRIMARY = Color.parseColor("#2E7D5B")
    private val TINT = Color.parseColor("#EEF5F0")
    private val TINT_2 = Color.parseColor("#F7FAF8")
    private val INK = Color.parseColor("#1C2420")
    private val MUTED = Color.parseColor("#6B7770")
    private val LINE = Color.parseColor("#DDE5DF")
    private val SUCCESS = Color.parseColor("#1F6B4A")
    private val DANGER = Color.parseColor("#A8322A")
    private val GOLD = Color.parseColor("#C9A24B")

    private val DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val DATE_TIME_FMT = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)
    private val LONG_FMT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy 'at' h:mm a", Locale.ENGLISH)

    // Bundled typeface (Plus Jakarta Sans) so PDFs look the same on every phone.
    private var tfRegular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private var tfMedium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private var tfBold: Typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)

    private fun loadFonts(context: Context) {
        try {
            fun weight(w: Int): Typeface? =
                Typeface.Builder(context.assets, "fonts/plus_jakarta_sans.ttf")
                    .setFontVariationSettings("'wght' $w")
                    .build()
            tfRegular = weight(400) ?: tfRegular
            tfMedium = weight(500) ?: tfMedium
            tfBold = weight(700) ?: tfBold
        } catch (e: Exception) {
            // Keep the system fonts if the bundled one can't be loaded.
        }
    }

    // ---------------------------------------------------------------- public API

    fun generateLoanReport(
        context: Context, loan: Loan, payments: List<Payment>, totalPaid: Double
    ): File? {
        loadFonts(context)
        val sorted = payments.sortedBy { it.sortKey() }
        val repaid = isRepaid(loan, totalPaid)
        val balance = (loan.totalPayable - totalPaid).coerceAtLeast(0.0)
        val progress = if (loan.totalPayable > 0) (totalPaid / loan.totalPayable).coerceIn(0.0, 1.0) else 0.0
        val ref = "LT-" + loan.id.take(6).uppercase(Locale.ENGLISH)
        val today = LocalDate.now()
        val lastPaid: LocalDate? = sorted.lastOrNull()?.paidAt?.toLocalDate()

        // Core facts sit on ONE row; contact info and notes (if any) get a second, wider row.
        val core = buildList<Pair<String, String>> {
            loan.lenderName?.takeIf { it.isNotBlank() }?.let { add("LENDER" to it) }
            loan.keyword?.takeIf { it.isNotBlank() }?.let { add("M-PESA ACCOUNT" to it) }
            add("START DATE" to loan.startDate.format(DATE_FMT))
            add("DUE DATE" to loan.dueDate.format(DATE_FMT))
            add("LOAN PERIOD" to "${ChronoUnit.DAYS.between(loan.startDate, loan.dueDate) + 1} days")
        }
        val extras = buildList<Pair<String, String>> {
            loan.lenderPhone?.takeIf { it.isNotBlank() }?.let { add("LENDER PHONE" to it) }
            loan.lenderEmail?.takeIf { it.isNotBlank() }?.let { add("LENDER EMAIL" to it) }
            loan.notes?.takeIf { it.isNotBlank() }?.let { add("NOTES" to it) }
        }

        // ---- fixed first-page layout (also used to count pages up front)
        val heroTop = BAND_H + 26f
        val tilesTop = heroTop + 112f + 14f
        val detailsTop = tilesTop + 58f + 26f
        val detailRows = 1 + (if (extras.isEmpty()) 0 else (extras.size + 2) / 3)
        val tableTitleY = detailsTop + 22f + detailRows * 36f + 14f
        val firstRowsY = tableTitleY + 12f + 28f
        val contRowsY = 78f + 12f + 28f

        fun countPages(n: Int): Int {
            var pages = 1
            var y = firstRowsY
            repeat(n) {
                if (y + ROW_H > CONTENT_BOTTOM) { pages++; y = contRowsY }
                y += ROW_H
            }
            if (y + ROW_H * 2 > CONTENT_BOTTOM) pages++
            return pages
        }
        val totalPages = countPages(sorted.size)

        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var canvas = page.canvas

        // ---- header + summary
        val pill = when {
            repaid -> "PAID IN FULL" to SUCCESS
            today.isAfter(loan.dueDate) -> "OVERDUE" to DANGER
            else -> "ACTIVE" to PRIMARY
        }
        drawHeader(canvas, "LOAN STATEMENT", loan.title, "Statement of account", ref, pill)

        // hero card
        val hero = RectF(M, heroTop, W - M, heroTop + 112f)
        canvas.drawRoundRect(hero, 16f, 16f, fill(TINT))
        canvas.drawText("OUTSTANDING BALANCE", M + 20f, heroTop + 28f, label(9f, MUTED))
        canvas.drawText(money(balance), M + 20f, heroTop + 62f, text(32f, BRAND, bold = true))
        if (repaid) {
            drawStamp(canvas, W - M - 100f, heroTop + 46f, "PAID IN FULL", SUCCESS)
        } else {
            canvas.drawText(
                "${(progress * 100).toInt()}%",
                W - M - 20f, heroTop + 54f, text(28f, PRIMARY, bold = true, align = Paint.Align.RIGHT)
            )
            canvas.drawText(
                "repaid", W - M - 20f, heroTop + 70f, text(9.5f, MUTED, align = Paint.Align.RIGHT)
            )
        }
        val barL = M + 20f
        val barR = W - M - 20f
        val barY = heroTop + 80f
        canvas.drawRoundRect(RectF(barL, barY, barR, barY + 8f), 4f, 4f, fill(Color.parseColor("#D3E1D8")))
        if (progress > 0) {
            canvas.drawRoundRect(
                RectF(barL, barY, barL + (barR - barL) * progress.toFloat().coerceAtLeast(0.02f), barY + 8f),
                4f, 4f, fill(if (repaid) SUCCESS else PRIMARY)
            )
        }
        canvas.drawText(
            "Paid ${money(totalPaid)} of ${money(loan.totalPayable)}",
            barL, heroTop + 104f, text(9.5f, MUTED)
        )
        val dueNote = when {
            repaid -> repaymentNote(loan.dueDate, lastPaid)
            today.isAfter(loan.dueDate) -> "Overdue since ${loan.dueDate.format(DATE_FMT)}"
            else -> "Due ${loan.dueDate.format(DATE_FMT)} · ${loan.daysRemaining} ${if (loan.daysRemaining == 1L) "day" else "days"} left"
        }
        canvas.drawText(dueNote, barR, heroTop + 104f, text(9.5f, if (repaid) SUCCESS else MUTED, bold = repaid, align = Paint.Align.RIGHT))

        // stat tiles
        val interest = loan.totalPayable - loan.principal
        val tiles = listOf(
            "PRINCIPAL" to money(loan.principal),
            "INTEREST · ${trimRate(loan.interestRate)}%" to money(interest),
            "TOTAL PAYABLE" to money(loan.totalPayable),
            "TOTAL PAID" to money(totalPaid)
        )
        val tileW = (W - 2 * M - 30f) / 4f
        tiles.forEachIndexed { i, (l, v) ->
            val x = M + i * (tileW + 10f)
            val r = RectF(x, tilesTop, x + tileW, tilesTop + 58f)
            canvas.drawRoundRect(r, 10f, 10f, fill(Color.WHITE))
            canvas.drawRoundRect(r, 10f, 10f, stroke(LINE, 1f))
            canvas.drawText(l, x + 12f, tilesTop + 22f, label(7.5f, MUTED))
            canvas.drawText(fit(v, text(13f, INK, bold = true), tileW - 20f), x + 12f, tilesTop + 42f, text(13f, INK, bold = true))
        }

        // details
        sectionTitle(canvas, "LOAN DETAILS", detailsTop)
        fun drawGrid(items: List<Pair<String, String>>, perRow: Int, startY: Float) {
            val cellW = (W - 2 * M) / perRow
            items.forEachIndexed { i, (l, v) ->
                val cx = M + (i % perRow) * cellW
                val cy = startY + (i / perRow) * 36f
                canvas.drawText(l, cx, cy + 12f, label(7.5f, MUTED))
                canvas.drawText(fit(v, text(11f, INK, bold = true), cellW - 14f), cx, cy + 28f, text(11f, INK, bold = true))
            }
        }
        drawGrid(core, core.size, detailsTop + 22f)
        if (extras.isNotEmpty()) drawGrid(extras, minOf(extras.size, 3), detailsTop + 22f + 36f)

        // payment history
        sectionTitle(canvas, "PAYMENT HISTORY (${sorted.size})", tableTitleY)
        drawTableHeader(canvas, tableTitleY + 12f)
        var y = firstRowsY

        if (sorted.isEmpty()) {
            canvas.drawText("No payments recorded yet", W / 2f, y + 16f, text(11f, MUTED, align = Paint.Align.CENTER))
            y += ROW_H
        }

        var running = loan.totalPayable
        val dateP = text(9.5f, INK)
        val mutedP = text(9.5f, MUTED)
        val amtP = text(9.5f, INK, bold = true, align = Paint.Align.RIGHT)
        val balP = text(9.5f, MUTED, align = Paint.Align.RIGHT)
        sorted.forEachIndexed { i, p ->
            if (y + ROW_H > CONTENT_BOTTOM) {
                drawFooter(canvas, pageNo, totalPages, ref)
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
                canvas = page.canvas
                drawRunningHeader(canvas, loan.title, ref)
                drawTableHeader(canvas, 78f + 12f)
                y = contRowsY
            }
            if (i % 2 == 0) canvas.drawRect(M, y, W - M, y + ROW_H, fill(TINT_2))
            running = (running - p.amount).coerceAtLeast(0.0)
            val base = y + 15f
            canvas.drawText(p.paidAt.format(DATE_TIME_FMT), M + 12f, base, dateP)
            canvas.drawText(if (p.source == PaymentSource.MPESA) "M-Pesa" else "Manual", 172f, base, mutedP)
            canvas.drawText(fit(p.mpesaCode ?: "—", mutedP, 100f), 238f, base, mutedP)
            canvas.drawText(num(p.amount), 452f, base, amtP)
            canvas.drawText(num(running), W - M - 12f, base, balP)
            y += ROW_H
        }

        // totals
        if (y + ROW_H * 2 > CONTENT_BOTTOM) {
            drawFooter(canvas, pageNo, totalPages, ref)
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            canvas = page.canvas
            drawRunningHeader(canvas, loan.title, ref)
            y = 78f + 12f
        }
        canvas.drawLine(M, y + 4f, W - M, y + 4f, stroke(BRAND, 1.4f))
        canvas.drawText("TOTAL PAID", M + 12f, y + 24f, label(8.5f, BRAND))
        canvas.drawText(
            money(totalPaid), W - M - 12f, y + 24f,
            text(12f, BRAND, bold = true, align = Paint.Align.RIGHT)
        )
        if (!repaid) {
            canvas.drawText(
                "Balance ${money(balance)}", W - M - 12f, y + 40f,
                text(9.5f, MUTED, align = Paint.Align.RIGHT)
            )
        }

        drawFooter(canvas, pageNo, totalPages, ref)
        doc.finishPage(page)

        val file = File(context.cacheDir, "loan_report_${sanitize(loan.title)}_${timestamp()}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        shareFile(context, file, "Loan statement for ${loan.title}")
        return file
    }

    fun generatePaymentReceipt(
        context: Context, loan: Loan, payment: Payment, totalPaid: Double
    ): File? {
        loadFonts(context)
        val repaid = isRepaid(loan, totalPaid)
        val balance = (loan.totalPayable - totalPaid).coerceAtLeast(0.0)
        val ref = payment.mpesaCode ?: payment.id.take(8).uppercase(Locale.ENGLISH)

        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create())
        val canvas = page.canvas

        drawHeader(canvas, "PAYMENT RECEIPT", "Receipt", loan.title, "#$ref", "RECEIVED" to PRIMARY)

        // success badge
        val cx = W / 2f
        var y = BAND_H + 70f
        canvas.drawCircle(cx, y, 30f, fill(TINT))
        canvas.drawCircle(cx, y, 22f, fill(PRIMARY))
        val check = Path().apply {
            moveTo(cx - 9f, y + 1f); lineTo(cx - 3f, y + 8f); lineTo(cx + 10f, y - 7f)
        }
        canvas.drawPath(
            check,
            stroke(Color.WHITE, 3.4f).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        )

        // amount
        y += 74f
        canvas.drawText("AMOUNT PAID", cx, y - 28f, label(9f, MUTED).apply { textAlign = Paint.Align.CENTER })
        canvas.drawText(
            String.format(Locale.US, "KSh %,.2f", payment.amount), cx, y + 8f,
            text(40f, BRAND, bold = true, align = Paint.Align.CENTER)
        )
        canvas.drawText(
            payment.paidAt.format(LONG_FMT), cx, y + 32f,
            text(10.5f, MUTED, align = Paint.Align.CENTER)
        )

        // M-Pesa code chip
        y += 52f
        payment.mpesaCode?.let { code ->
            val chip = RectF(cx - 130f, y, cx + 130f, y + 46f)
            canvas.drawRoundRect(chip, 12f, 12f, fill(TINT))
            canvas.drawRoundRect(chip, 12f, 12f, stroke(PRIMARY, 1f))
            canvas.drawText("M-PESA TRANSACTION CODE", cx, y + 18f, label(7.5f, MUTED).apply { textAlign = Paint.Align.CENTER })
            canvas.drawText(code, cx, y + 37f, text(15f, BRAND, bold = true, align = Paint.Align.CENTER).apply { letterSpacing = 0.12f })
            y += 46f
        }
        y += 34f

        // detail card
        val rows = buildList<Pair<String, String>> {
            add("Loan" to loan.title)
            loan.lenderName?.takeIf { it.isNotBlank() }?.let { add("Lender" to it) }
            add("Method" to if (payment.source == PaymentSource.MPESA) "M-Pesa" else "Manual entry")
            payment.sender?.takeIf { it.isNotBlank() }?.let { add("Received from" to it) }
            add("Loan principal" to money(loan.principal))
            add("Total payable" to money(loan.totalPayable))
            add("Total paid to date" to money(totalPaid))
            add("Due date" to loan.dueDate.format(DATE_FMT))
        }
        val cardTop = y
        val cardH = 108f + rows.size * 30f
        val card = RectF(M, cardTop, W - M, cardTop + cardH)
        canvas.drawRoundRect(card, 16f, 16f, fill(Color.WHITE))
        canvas.drawRoundRect(card, 16f, 16f, stroke(LINE, 1f))
        sectionTitle(canvas, "PAYMENT DETAILS", cardTop + 28f, M + 20f, W - M - 20f)
        val lp = text(10.5f, MUTED)
        val vp = text(10.5f, INK, bold = true, align = Paint.Align.RIGHT)
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 0.8f; color = LINE
            pathEffect = DashPathEffect(floatArrayOf(2f, 3f), 0f)
        }
        var ry = cardTop + 60f
        rows.forEach { (l, v) ->
            canvas.drawText(l, M + 20f, ry, lp)
            canvas.drawText(fit(v, vp, 300f), W - M - 20f, ry, vp)
            canvas.drawLine(M + 20f, ry + 10f, W - M - 20f, ry + 10f, dash)
            ry += 30f
        }
        // balance highlight
        val bal = RectF(M + 14f, ry - 4f, W - M - 14f, ry + 34f)
        canvas.drawRoundRect(bal, 10f, 10f, fill(if (repaid) Color.parseColor("#DDEEE3") else TINT))
        canvas.drawText(
            if (repaid) "Loan status" else "Remaining balance", M + 28f, ry + 20f,
            text(11f, BRAND, bold = true)
        )
        canvas.drawText(
            if (repaid) "Paid in full" else money(balance), W - M - 28f, ry + 20f,
            text(13f, if (repaid) SUCCESS else BRAND, bold = true, align = Paint.Align.RIGHT)
        )

        if (repaid) drawStamp(canvas, W - M - 110f, cardTop + cardH + 46f, "PAID IN FULL", SUCCESS)

        canvas.drawText(
            "Thank you. Keep this receipt for your records.", M, cardTop + cardH + 40f,
            text(10f, MUTED)
        )

        drawFooter(canvas, 1, 1, "#$ref")
        doc.finishPage(page)

        val file = File(context.cacheDir, "receipt_${sanitize(ref)}_${timestamp()}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        shareFile(context, file, "Payment receipt for ${loan.title}")
        return file
    }

    // ---------------------------------------------------------------- drawing blocks

    private fun drawHeader(
        canvas: Canvas, kicker: String, title: String, subtitle: String,
        ref: String, pill: Pair<String, Int>?
    ) {
        canvas.drawRect(0f, 0f, W.toFloat(), BAND_H, fill(BRAND))
        canvas.save()
        canvas.clipRect(0f, 0f, W.toFloat(), BAND_H)
        canvas.drawCircle(W - 30f, -10f, 120f, fill(BRAND_SOFT).apply { alpha = 150 })
        canvas.drawCircle(W - 120f, BAND_H + 30f, 70f, fill(BRAND_SOFT).apply { alpha = 90 })
        canvas.restore()
        canvas.drawRect(0f, BAND_H, W.toFloat(), BAND_H + 3f, fill(GOLD))

        canvas.drawText("LOAN TRACKER", M, 40f, label(9.5f, Color.WHITE).apply { alpha = 200; letterSpacing = 0.3f })
        canvas.drawText(kicker, M, 62f, label(8.5f, GOLD))
        canvas.drawText(fit(title, text(26f, Color.WHITE, bold = true), 330f), M, 92f, text(26f, Color.WHITE, bold = true))
        canvas.drawText(
            fit(subtitle, text(12f, Color.WHITE), 330f), M, 112f,
            text(12f, Color.WHITE).apply { alpha = 210 }
        )

        canvas.drawText(
            ref, W - M, 40f,
            text(9f, Color.WHITE, align = Paint.Align.RIGHT).apply { alpha = 190 }
        )
        pill?.let { (txt, color) ->
            val tp = text(9f, color, bold = true, align = Paint.Align.CENTER).apply { letterSpacing = 0.12f }
            val pw = tp.measureText(txt) + 26f
            val r = RectF(W - M - pw, 56f, W - M, 80f)
            canvas.drawRoundRect(r, 12f, 12f, fill(Color.WHITE))
            canvas.drawText(txt, r.centerX(), r.centerY() + 3.2f, tp)
        }
        canvas.drawText(
            "Generated ${LocalDateTime.now().format(DATE_TIME_FMT)}", W - M, 112f,
            text(8.5f, Color.WHITE, align = Paint.Align.RIGHT).apply { alpha = 170 }
        )
    }

    private fun drawRunningHeader(canvas: Canvas, title: String, ref: String) {
        canvas.drawRect(0f, 0f, W.toFloat(), 46f, fill(BRAND))
        canvas.drawRect(0f, 46f, W.toFloat(), 48f, fill(GOLD))
        canvas.drawText("LOAN TRACKER", M, 29f, label(9f, Color.WHITE).apply { alpha = 200; letterSpacing = 0.3f })
        canvas.drawText(
            fit(title, text(11f, Color.WHITE, bold = true, align = Paint.Align.RIGHT), 300f),
            W - M, 29f, text(11f, Color.WHITE, bold = true, align = Paint.Align.RIGHT)
        )
        canvas.drawText("PAYMENT HISTORY (continued)", M, 70f, label(8.5f, BRAND))
        canvas.drawText(ref, W - M, 70f, text(8.5f, MUTED, align = Paint.Align.RIGHT))
    }

    private fun drawTableHeader(canvas: Canvas, top: Float) {
        canvas.drawRoundRect(RectF(M, top, W - M, top + 26f), 8f, 8f, fill(BRAND))
        val h = label(7.5f, Color.WHITE)
        val hr = label(7.5f, Color.WHITE).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("DATE", M + 12f, top + 17f, h)
        canvas.drawText("METHOD", 172f, top + 17f, h)
        canvas.drawText("REFERENCE", 238f, top + 17f, h)
        canvas.drawText("AMOUNT (KSh)", 452f, top + 17f, hr)
        canvas.drawText("BALANCE (KSh)", W - M - 12f, top + 17f, hr)
    }

    private fun sectionTitle(canvas: Canvas, title: String, y: Float, left: Float = M, right: Float = W - M) {
        canvas.drawText(title, left, y, label(8.5f, BRAND).apply { isFakeBoldText = true })
        canvas.drawLine(left, y + 6f, right, y + 6f, stroke(LINE, 1f))
    }

    private fun drawFooter(canvas: Canvas, page: Int, total: Int, ref: String) {
        canvas.drawLine(M, 806f, W - M, 806f, stroke(LINE, 1f))
        canvas.drawText("Loan Tracker  ·  $ref", M, 822f, text(8f, MUTED))
        canvas.drawText("Page $page of $total", W - M, 822f, text(8f, MUTED, align = Paint.Align.RIGHT))
        canvas.drawText(
            "Generated from records kept in the Loan Tracker app", W / 2f, 822f,
            text(8f, MUTED, align = Paint.Align.CENTER)
        )
    }

    private fun drawStamp(canvas: Canvas, cx: Float, cy: Float, textStr: String, color: Int) {
        val tp = text(15f, color, bold = true, align = Paint.Align.CENTER).apply {
            letterSpacing = 0.14f; alpha = 215
        }
        val w = tp.measureText(textStr) + 34f
        val h = 36f
        canvas.save()
        canvas.rotate(-9f, cx, cy)
        val r = RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        canvas.drawRoundRect(r, 7f, 7f, stroke(color, 2.4f).apply { alpha = 215 })
        canvas.drawRoundRect(
            RectF(r.left + 3.5f, r.top + 3.5f, r.right - 3.5f, r.bottom - 3.5f),
            5f, 5f, stroke(color, 0.8f).apply { alpha = 215 }
        )
        canvas.drawText(textStr, cx, cy + 5.4f, tp)
        canvas.restore()
    }

    // ---------------------------------------------------------------- paints & helpers

    private fun text(
        size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        textAlign = align
        typeface = if (bold) tfBold else tfRegular
    }

    /** Small-caps style label with letter spacing. */
    private fun label(size: Float, color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        letterSpacing = 0.14f
        typeface = tfMedium
    }

    private fun fill(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }

    private fun stroke(color: Int, width: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        this.color = color
    }

    private fun fit(textStr: String, p: Paint, maxW: Float): String {
        if (p.measureText(textStr) <= maxW) return textStr
        var t = textStr
        while (t.isNotEmpty() && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    private fun money(v: Double): String = String.format(Locale.US, "KSh %,.0f", v)
    private fun num(v: Double): String =
        if (v % 1.0 == 0.0) String.format(Locale.US, "%,.0f", v) else String.format(Locale.US, "%,.2f", v)
    private fun trimRate(r: Double): String = if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()

    private fun isRepaid(loan: Loan, paid: Double): Boolean =
        loan.totalPayable > 0.0 && paid + 0.5 >= loan.totalPayable

    private fun Payment.sortKey(): Long =
        paidAt.toLocalDate().toEpochDay() * 86_400L + paidAt.toLocalTime().toSecondOfDay()

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
    private fun timestamp(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ENGLISH))
}
