package com.college.library.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.college.library.data.model.Book
import com.college.library.data.model.IssuedBook
import com.college.library.data.model.Member
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Generates PDF reports using Android built-in [PdfDocument] API.
 * A4 = 595 x 842 pts at 72 dpi.
 */
class PdfExportManager(private val context: Context) {

    companion object {
        private const val PAGE_W = 595
        private const val PAGE_H = 842
        private const val MARGIN = 36f
        private const val HEADER_HEIGHT = 70f
        private const val ROW_HEIGHT = 22f

        private const val NAVY = "#1A237E"
        private const val GOLD = "#FFD700"
        private const val GREY = "#757575"
        private const val LIGHT_GREY = "#F5F5F5"
        private const val DARK_TEXT = "#212121"
        private const val DANGER_RED = "#F44336"
    }

    // ── Public API ───────────────────────────────────────────────────────────

    fun exportBooksCatalog(books: List<Book>): File {
        val columns = listOf(
            ColDef("Title", 160f),
            ColDef("Author", 120f),
            ColDef("ISBN", 90f),
            ColDef("Status", 70f),
            ColDef("Category", 83f)
        )
        val rows = books.map { listOf(it.title, it.author, it.isbn, it.status, it.category) }
        return buildTablePdf(
            title = "Books Catalog",
            subtitle = "${books.size} books",
            columns = columns,
            rows = rows,
            fileName = "books_catalog"
        )
    }

    fun exportMembersDirectory(members: List<Member>): File {
        val columns = listOf(
            ColDef("Name", 120f),
            ColDef("Member ID", 80f),
            ColDef("Department", 90f),
            ColDef("Type", 65f),
            ColDef("Phone", 80f),
            ColDef("Books", 50f)
        )
        val rows = members.map {
            listOf(it.name, it.memberId, it.department, it.memberType, it.phone, it.booksIssued.toString())
        }
        return buildTablePdf(
            title = "Members Directory",
            subtitle = "${members.size} members",
            columns = columns,
            rows = rows,
            fileName = "members_directory"
        )
    }

    fun exportTransactionHistory(transactions: List<IssuedBook>): File {
        val columns = listOf(
            ColDef("Book", 130f),
            ColDef("Member", 100f),
            ColDef("Issue Date", 75f),
            ColDef("Due Date", 75f),
            ColDef("Return", 70f),
            ColDef("Fine", 45f)
        )
        val rows = transactions.map {
            listOf(
                it.bookTitle,
                it.memberName,
                it.issueDate,
                it.dueDate,
                it.returnDate ?: "—",
                if (it.fine > 0.0) "%.0f".format(it.fine) else "—"
            )
        }
        return buildTablePdf(
            title = "Transaction History",
            subtitle = "${transactions.size} transactions",
            columns = columns,
            rows = rows,
            fileName = "transactions"
        )
    }

    fun exportOverdueReport(overdueBooks: List<IssuedBook>): File {
        val columns = listOf(
            ColDef("Book", 140f),
            ColDef("Member", 110f),
            ColDef("Member ID", 80f),
            ColDef("Due Date", 80f),
            ColDef("Days Late", 60f)
        )
        val today = LocalDate.now()
        val rows = overdueBooks.map {
            val dueDate = try {
                LocalDate.parse(it.dueDate, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: Exception) {
                today
            }
            val daysLate = java.time.temporal.ChronoUnit.DAYS.between(dueDate, today)
            listOf(it.bookTitle, it.memberName, it.memberMemberId, it.dueDate, daysLate.toString())
        }
        return buildTablePdf(
            title = "Overdue Books Report",
            subtitle = "${overdueBooks.size} overdue as of ${today.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}",
            columns = columns,
            rows = rows,
            fileName = "overdue_report",
            highlightColor = DANGER_RED
        )
    }

    fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share PDF Report"))
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private data class ColDef(val header: String, val width: Float)

    private fun buildTablePdf(
        title: String,
        subtitle: String,
        columns: List<ColDef>,
        rows: List<List<String>>,
        fileName: String,
        highlightColor: String = NAVY
    ): File {
        val doc = PdfDocument()
        val maxRowsFirstPage = ((PAGE_H - HEADER_HEIGHT - MARGIN * 2 - 40f) / ROW_HEIGHT).toInt() - 1
        val maxRowsOtherPages = ((PAGE_H - MARGIN * 2 - 20f) / ROW_HEIGHT).toInt() - 1

        var pageNum = 1
        var rowIndex = 0
        val totalRows = rows.size

        while (rowIndex < totalRows || pageNum == 1) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawColor(Color.WHITE)

            var y: Float

            if (pageNum == 1) {
                y = drawHeader(canvas, title, subtitle, highlightColor)
            } else {
                y = MARGIN + 10f
                // Small continuation header
                val contPaint = Paint().apply {
                    color = Color.parseColor(GREY); textSize = 10f
                }
                canvas.drawText("$title (continued - page $pageNum)", MARGIN, y, contPaint)
                y += 16f
            }

            // Draw table header row
            y = drawTableHeader(canvas, columns, y)

            // Draw data rows
            val maxRows = if (pageNum == 1) maxRowsFirstPage else maxRowsOtherPages
            var rowsOnPage = 0

            while (rowIndex < totalRows && rowsOnPage < maxRows) {
                val row = rows[rowIndex]
                y = drawTableRow(canvas, columns, row, y, rowIndex % 2 == 1)
                rowIndex++
                rowsOnPage++
            }

            // Footer
            drawFooter(canvas, pageNum)

            doc.finishPage(page)
            pageNum++

            // If no rows at all, still produce one page
            if (totalRows == 0) break
        }

        val file = File(context.cacheDir, "${fileName}_${System.currentTimeMillis()}.pdf")
        doc.writeTo(FileOutputStream(file))
        doc.close()
        return file
    }

    private fun drawHeader(canvas: Canvas, title: String, subtitle: String, accentColor: String): Float {
        val w = PAGE_W.toFloat()

        // Header band
        val headerPaint = Paint().apply {
            color = Color.parseColor(NAVY); style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, w, HEADER_HEIGHT, headerPaint)

        // Gold accent line
        val goldPaint = Paint().apply {
            color = Color.parseColor(GOLD); style = Paint.Style.FILL
        }
        canvas.drawRect(0f, HEADER_HEIGHT, w, HEADER_HEIGHT + 3f, goldPaint)

        // Library name
        val titlePaint = Paint().apply {
            color = Color.WHITE; textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("GDC Library", MARGIN, 30f, titlePaint)

        // Report title
        val reportPaint = Paint().apply {
            color = Color.parseColor("#B0BEC5"); textSize = 13f
        }
        canvas.drawText(title, MARGIN, 50f, reportPaint)

        // Date on right
        val datePaint = Paint().apply {
            color = Color.WHITE; textSize = 11f
        }
        val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
        val dateWidth = datePaint.measureText(dateStr)
        canvas.drawText(dateStr, w - MARGIN - dateWidth, 30f, datePaint)

        // Subtitle on right
        val subPaint = Paint().apply {
            color = Color.parseColor("#B0BEC5"); textSize = 11f
        }
        val subWidth = subPaint.measureText(subtitle)
        canvas.drawText(subtitle, w - MARGIN - subWidth, 50f, subPaint)

        return HEADER_HEIGHT + 3f + 20f
    }

    private fun drawTableHeader(canvas: Canvas, columns: List<ColDef>, startY: Float): Float {
        val headerBg = Paint().apply {
            color = Color.parseColor(NAVY); style = Paint.Style.FILL
        }
        val headerText = Paint().apply {
            color = Color.WHITE; textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        canvas.drawRect(MARGIN, startY, PAGE_W - MARGIN, startY + ROW_HEIGHT, headerBg)

        var x = MARGIN + 4f
        for (col in columns) {
            canvas.drawText(col.header, x, startY + 15f, headerText)
            x += col.width
        }

        return startY + ROW_HEIGHT
    }

    private fun drawTableRow(
        canvas: Canvas,
        columns: List<ColDef>,
        values: List<String>,
        startY: Float,
        alternate: Boolean
    ): Float {
        if (alternate) {
            val bgPaint = Paint().apply {
                color = Color.parseColor(LIGHT_GREY); style = Paint.Style.FILL
            }
            canvas.drawRect(MARGIN, startY, PAGE_W - MARGIN, startY + ROW_HEIGHT, bgPaint)
        }

        val textPaint = Paint().apply {
            color = Color.parseColor(DARK_TEXT); textSize = 9f
        }

        var x = MARGIN + 4f
        for (i in columns.indices) {
            val value = values.getOrElse(i) { "" }
            val maxChars = (columns[i].width / (textPaint.textSize * 0.52f)).toInt()
            val display = if (value.length > maxChars) value.take(maxChars - 1) + "..." else value
            canvas.drawText(display, x, startY + 15f, textPaint)
            x += columns[i].width
        }

        // Row separator
        val linePaint = Paint().apply {
            color = Color.parseColor("#E0E0E0"); strokeWidth = 0.5f
        }
        canvas.drawLine(MARGIN, startY + ROW_HEIGHT, PAGE_W - MARGIN, startY + ROW_HEIGHT, linePaint)

        return startY + ROW_HEIGHT
    }

    private fun drawFooter(canvas: Canvas, pageNum: Int) {
        val w = PAGE_W.toFloat()
        val y = PAGE_H - MARGIN

        val linePaint = Paint().apply {
            color = Color.parseColor("#E0E0E0"); strokeWidth = 0.5f
        }
        canvas.drawLine(MARGIN, y - 12f, w - MARGIN, y - 12f, linePaint)

        val footerPaint = Paint().apply {
            color = Color.parseColor(GREY); textSize = 9f
        }
        canvas.drawText("GDC Library Management System", MARGIN, y, footerPaint)

        val pageText = "Page $pageNum"
        val pageWidth = footerPaint.measureText(pageText)
        canvas.drawText(pageText, w - MARGIN - pageWidth, y, footerPaint)
    }
}
