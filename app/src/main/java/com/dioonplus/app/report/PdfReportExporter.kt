package com.dioonplus.app.report

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.dioonplus.app.data.DashboardSummary
import com.dioonplus.app.data.EntryType
import com.dioonplus.app.data.ReportRow
import com.dioonplus.app.util.fileSafeDate
import com.dioonplus.app.util.formatDateTime
import com.dioonplus.app.util.formatMoney
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object PdfReportExporter {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val ROWS_PER_PAGE = 15

    fun write(
        output: OutputStream,
        rows: List<ReportRow>,
        summary: DashboardSummary,
    ) {
        val document = PdfDocument()
        try {
            val pages = rows.chunked(ROWS_PER_PAGE).ifEmpty { listOf(emptyList()) }
            pages.forEachIndexed { pageIndex, pageRows ->
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas
                drawHeader(canvas, pageIndex + 1, pages.size)
                drawSummary(canvas, summary)
                drawTable(canvas, pageRows)
                document.finishPage(page)
            }
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    fun createShareFile(
        context: Context,
        rows: List<ReportRow>,
        summary: DashboardSummary,
    ): File {
        val directory = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(directory, "DioonPlus-report-${fileSafeDate()}.pdf")
        FileOutputStream(file).use { write(it, rows, summary) }
        return file
    }

    private fun drawHeader(canvas: android.graphics.Canvas, page: Int, pageCount: Int) {
        val darkBlue = Color.rgb(14, 49, 91)
        val blue = Color.rgb(23, 105, 224)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkBlue
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 11f
            textAlign = Paint.Align.RIGHT
        }
        val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blue }
        canvas.drawRoundRect(505f, 35f, 555f, 85f, 10f, 10f, logoPaint)
        val logoText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("د+", 530f, 67f, logoText)
        canvas.drawText("ديون بلس", 492f, 55f, titlePaint)
        canvas.drawText("تقرير الحركة المالية الشامل", 492f, 76f, subtitlePaint)
        canvas.drawText("صفحة $page من $pageCount", 555f, 815f, subtitlePaint)
        canvas.drawLine(40f, 100f, 555f, 100f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blue; strokeWidth = 2f })
    }

    private fun drawSummary(canvas: android.graphics.Canvas, summary: DashboardSummary) {
        val labels = listOf(
            Triple("لك عند الآخرين", formatMoney(summary.receivableCents), Color.rgb(21, 152, 108)),
            Triple("عليك للآخرين", formatMoney(summary.payableCents), Color.rgb(226, 85, 85)),
            Triple("صافي الرصيد", formatMoney(summary.netCents, includeSign = true), Color.rgb(23, 105, 224)),
        )
        val boxWidth = 160f
        val gap = 15f
        labels.forEachIndexed { index, item ->
            val right = 555f - index * (boxWidth + gap)
            val left = right - boxWidth
            val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(247, 249, 252) }
            canvas.drawRoundRect(left, 118f, right, 178f, 10f, 10f, background)
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = 10f
                textAlign = Paint.Align.RIGHT
            }
            val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = item.third
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(item.first, right - 10f, 140f, labelPaint)
            canvas.drawText(item.second, right - 10f, 164f, valuePaint)
        }
    }

    private fun drawTable(canvas: android.graphics.Canvas, rows: List<ReportRow>) {
        val startY = 205f
        val rowHeight = 36f
        val blue = Color.rgb(23, 105, 224)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blue }
        canvas.drawRoundRect(40f, startY, 555f, startY + 32f, 7f, 7f, headerPaint)
        val headerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("التاريخ", 545f, startY + 21f, headerText)
        canvas.drawText("الحساب", 410f, startY + 21f, headerText)
        canvas.drawText("الحركة", 260f, startY + 21f, headerText)
        canvas.drawText("المبلغ", 145f, startY + 21f, headerText)

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(21, 36, 55)
            textSize = 9.5f
            textAlign = Paint.Align.RIGHT
        }
        val secondaryPaint = Paint(bodyPaint).apply { color = Color.GRAY; textSize = 8.5f }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(230, 236, 243); strokeWidth = 1f }

        if (rows.isEmpty()) {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                textSize = 13f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("لا توجد حركات في التقرير", PAGE_WIDTH / 2f, startY + 90f, emptyPaint)
            return
        }

        rows.forEachIndexed { index, row ->
            val top = startY + 32f + index * rowHeight
            if (index % 2 == 1) {
                canvas.drawRect(40f, top, 555f, top + rowHeight, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(250, 251, 253) })
            }
            canvas.drawText(formatDateTime(row.createdAt), 545f, top + 16f, secondaryPaint)
            canvas.drawText(row.partyName.take(24), 410f, top + 16f, bodyPaint)
            val typeLabel = if (row.entryType == EntryType.GAVE) "أعطيت" else "أخذت"
            val typePaint = Paint(bodyPaint).apply {
                color = if (row.entryType == EntryType.GAVE) Color.rgb(21, 152, 108) else Color.rgb(226, 85, 85)
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText(typeLabel, 260f, top + 16f, typePaint)
            canvas.drawText(formatMoney(row.amountCents), 145f, top + 16f, typePaint)
            if (row.note.isNotBlank()) {
                canvas.drawText(row.note.take(48), 410f, top + 30f, secondaryPaint)
            }
            canvas.drawLine(40f, top + rowHeight, 555f, top + rowHeight, linePaint)
        }
    }
}
