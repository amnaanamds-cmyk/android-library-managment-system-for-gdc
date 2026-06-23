package com.college.library.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.college.library.data.model.IssuedBook
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Generates and shares professional PDF receipts (Issue Slip / Return Slip).
 *
 * A4 = 595 x 842 pts (72 dpi)
 * We use a compact half-page slip (595 x 420) to save paper.
 */
object ReceiptGenerator {

    private const val PAGE_W = 595
    private const val PAGE_H = 420
    private const val MARGIN = 36f
    private const val COL2 = 300f   // x-start of right column values

    // ── Public API ────────────────────────────────────────────────────────────

    /** Generate an issue slip PDF and launch the share intent. */
    fun shareIssueSlip(
        context: Context,
        issuedBook: IssuedBook,
        strings: AppStrings
    ) {
        val file = buildIssueSlipPdf(context, issuedBook, strings)
        shareFile(context, file)
    }

    /** Generate a return slip PDF and launch the share intent. */
    fun shareReturnSlip(
        context: Context,
        issuedBook: IssuedBook,
        fine: Double,
        strings: AppStrings
    ) {
        val file = buildReturnSlipPdf(context, issuedBook, fine, strings)
        shareFile(context, file)
    }

    // ── Issue Slip ────────────────────────────────────────────────────────────

    private fun buildIssueSlipPdf(
        context: Context,
        book: IssuedBook,
        strings: AppStrings
    ): File {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
        val page = doc.startPage(pageInfo)
        drawIssueSlip(page.canvas, book, strings)
        doc.finishPage(page)

        val file = File(context.cacheDir, "issue_slip_${book.id}_${System.currentTimeMillis()}.pdf")
        doc.writeTo(FileOutputStream(file))
        doc.close()
        return file
    }

    private fun drawIssueSlip(canvas: Canvas, book: IssuedBook, strings: AppStrings) {
        val w = PAGE_W.toFloat()
        val h = PAGE_H.toFloat()

        // Background
        canvas.drawColor(Color.WHITE)

        // ── Header band ───────────────────────────────────────────────────────
        val headerPaint = Paint().apply { color = Color.parseColor("#1A237E"); style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, w, 80f, headerPaint)

        // Library name
        val titlePaint = Paint().apply {
            color = Color.WHITE; textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(strings.libraryName, MARGIN, 32f, titlePaint)

        // College header
        val subTitlePaint = Paint().apply { color = Color.parseColor("#B0BEC5"); textSize = 12f }
        canvas.drawText(strings.collegeHeader, MARGIN, 52f, subTitlePaint)

        // Slip type badge
        val badgePaint = Paint().apply { color = Color.parseColor("#43A047"); style = Paint.Style.FILL }
        val badgeRect = RectF(w - 150f, 18f, w - MARGIN, 62f)
        canvas.drawRoundRect(badgeRect, 8f, 8f, badgePaint)
        val badgeLabelPaint = Paint().apply {
            color = Color.WHITE; textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val badgeText = strings.issueSlip
        val textW = badgeLabelPaint.measureText(badgeText)
        canvas.drawText(badgeText, badgeRect.centerX() - textW / 2f, badgeRect.centerY() + 5f, badgeLabelPaint)

        // ── Divider ───────────────────────────────────────────────────────────
        val divPaint = Paint().apply { color = Color.parseColor("#E8EAF6"); strokeWidth = 1f }
        canvas.drawLine(MARGIN, 90f, w - MARGIN, 90f, divPaint)

        // ── Receipt info row ─────────────────────────────────────────────────
        val labelPaint = Paint().apply { color = Color.parseColor("#757575"); textSize = 11f }
        val valuePaint = Paint().apply {
            color = Color.BLACK; textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
        val receiptNo = "LIB-${book.id}-${System.currentTimeMillis() % 10000}"

        canvas.drawText("${strings.receiptNumber}:", MARGIN, 112f, labelPaint)
        canvas.drawText(receiptNo, COL2, 112f, valuePaint)

        canvas.drawText("${strings.issueDate}:", MARGIN, 132f, labelPaint)
        canvas.drawText(book.issueDate, COL2, 132f, valuePaint)

        // ── Details section ───────────────────────────────────────────────────
        canvas.drawLine(MARGIN, 145f, w - MARGIN, 145f, divPaint)

        drawLabelValue(canvas, labelPaint, valuePaint, strings.bookTitle, book.bookTitle, 165f, w)
        drawLabelValue(canvas, labelPaint, valuePaint, strings.isbn, book.bookIsbn.ifBlank { "—" }, 190f, w)
        drawLabelValue(canvas, labelPaint, valuePaint, strings.memberName, book.memberName, 215f, w)
        drawLabelValue(canvas, labelPaint, valuePaint, strings.memberId, book.memberMemberId, 240f, w)
        drawLabelValue(canvas, labelPaint, valuePaint, strings.dueDate, book.dueDate, 265f, w)

        // ── Due date highlight ────────────────────────────────────────────────
        val dueBandPaint = Paint().apply { color = Color.parseColor("#FFF3E0"); style = Paint.Style.FILL }
        canvas.drawRect(MARGIN, 278f, w - MARGIN, 310f, dueBandPaint)
        val dueTextPaint = Paint().apply {
            color = Color.parseColor("#E65100"); textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("⚠  ${strings.dueDate}: ${book.dueDate}", MARGIN + 12f, 299f, dueTextPaint)

        // ── Thank-you footer ──────────────────────────────────────────────────
        canvas.drawLine(MARGIN, 325f, w - MARGIN, 325f, divPaint)
        val footerPaint = Paint().apply { color = Color.parseColor("#90A4AE"); textSize = 11f }
        canvas.drawText(strings.thankYou, MARGIN, 348f, footerPaint)

        // Signature line
        canvas.drawLine(w - 200f, 390f, w - MARGIN, 390f, Paint().apply { color = Color.GRAY; strokeWidth = 1f })
        canvas.drawText("Librarian", w - 180f, 408f, Paint().apply { color = Color.GRAY; textSize = 10f })
    }

    // ── Return Slip ───────────────────────────────────────────────────────────

    private fun buildReturnSlipPdf(
        context: Context,
        book: IssuedBook,
        fine: Double,
        strings: AppStrings
    ): File {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
        val page = doc.startPage(pageInfo)
        drawReturnSlip(page.canvas, book, fine, strings)
        doc.finishPage(page)

        val file = File(context.cacheDir, "return_slip_${book.id}_${System.currentTimeMillis()}.pdf")
        doc.writeTo(FileOutputStream(file))
        doc.close()
        return file
    }

    private fun drawReturnSlip(canvas: Canvas, book: IssuedBook, fine: Double, strings: AppStrings) {
        val w = PAGE_W.toFloat()

        canvas.drawColor(Color.WHITE)

        // ── Header band ───────────────────────────────────────────────────────
        val headerPaint = Paint().apply { color = Color.parseColor("#1A237E"); style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, w, 80f, headerPaint)

        val titlePaint = Paint().apply {
            color = Color.WHITE; textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(strings.libraryName, MARGIN, 32f, titlePaint)
        val subTitlePaint = Paint().apply { color = Color.parseColor("#B0BEC5"); textSize = 12f }
        canvas.drawText(strings.collegeHeader, MARGIN, 52f, subTitlePaint)

        // Badge (orange for return)
        val badgePaint = Paint().apply { color = Color.parseColor("#F57C00"); style = Paint.Style.FILL }
        val badgeRect = RectF(w - 170f, 18f, w - MARGIN, 62f)
        canvas.drawRoundRect(badgeRect, 8f, 8f, badgePaint)
        val badgeLabelPaint = Paint().apply {
            color = Color.WHITE; textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val badgeText = strings.returnSlip
        val textW = badgeLabelPaint.measureText(badgeText)
        canvas.drawText(badgeText, badgeRect.centerX() - textW / 2f, badgeRect.centerY() + 5f, badgeLabelPaint)

        val divPaint = Paint().apply { color = Color.parseColor("#E8EAF6"); strokeWidth = 1f }
        canvas.drawLine(MARGIN, 90f, w - MARGIN, 90f, divPaint)

        val labelPaint = Paint().apply { color = Color.parseColor("#757575"); textSize = 11f }
        val valuePaint = Paint().apply {
            color = Color.BLACK; textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
        val receiptNo = "RET-${book.id}-${System.currentTimeMillis() % 10000}"

        canvas.drawText("${strings.receiptNumber}:", MARGIN, 112f, labelPaint)
        canvas.drawText(receiptNo, COL2, 112f, valuePaint)
        canvas.drawText("${strings.returnDate}:", MARGIN, 132f, labelPaint)
        canvas.drawText(today, COL2, 132f, valuePaint)

        canvas.drawLine(MARGIN, 145f, w - MARGIN, 145f, divPaint)

        drawLabelValue(canvas, labelPaint, valuePaint, strings.bookTitle, book.bookTitle, 165f, w)
        drawLabelValue(canvas, labelPaint, valuePaint, strings.isbn, book.bookIsbn.ifBlank { "—" }, 190f, w)
        drawLabelValue(canvas, labelPaint, valuePaint, strings.memberName, book.memberName, 215f, w)
        drawLabelValue(canvas, labelPaint, valuePaint, strings.memberId, book.memberMemberId, 240f, w)
        drawLabelValue(canvas, labelPaint, valuePaint, strings.issueDate, book.issueDate, 265f, w)

        // Fine highlight
        val fineColor = if (fine > 0.0) Color.parseColor("#FFEBEE") else Color.parseColor("#E8F5E9")
        val fineTextColor = if (fine > 0.0) Color.parseColor("#C62828") else Color.parseColor("#2E7D32")
        val fineBandPaint = Paint().apply { color = fineColor; style = Paint.Style.FILL }
        canvas.drawRect(MARGIN, 278f, w - MARGIN, 318f, fineBandPaint)
        val fineLabel = if (fine > 0.0)
            "${strings.fineAmount}: ${strings.rupees} ${"%.2f".format(fine)}"
        else
            strings.noFine
        val finePaint = Paint().apply {
            color = fineTextColor; textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(fineLabel, MARGIN + 12f, 303f, finePaint)

        canvas.drawLine(MARGIN, 330f, w - MARGIN, 330f, divPaint)
        val footerPaint = Paint().apply { color = Color.parseColor("#90A4AE"); textSize = 11f }
        canvas.drawText(strings.thankYou, MARGIN, 353f, footerPaint)

        canvas.drawLine(w - 200f, 390f, w - MARGIN, 390f, Paint().apply { color = Color.GRAY; strokeWidth = 1f })
        canvas.drawText("Librarian", w - 180f, 408f, Paint().apply { color = Color.GRAY; textSize = 10f })
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun drawLabelValue(
        canvas: Canvas,
        labelPaint: Paint,
        valuePaint: Paint,
        label: String,
        value: String,
        y: Float,
        pageW: Float
    ) {
        canvas.drawText("$label:", MARGIN, y, labelPaint)
        // Truncate long values gracefully
        val maxChars = ((pageW - COL2 - MARGIN) / (valuePaint.textSize * 0.55f)).toInt()
        val displayValue = if (value.length > maxChars) value.take(maxChars - 1) + "…" else value
        canvas.drawText(displayValue, COL2, y, valuePaint)
    }

    private fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Receipt"))
    }
}
