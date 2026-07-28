package com.dioonplus.app.report

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.dioonplus.app.data.EntryType
import com.dioonplus.app.data.ReportRow
import com.dioonplus.app.util.fileSafeDate
import com.dioonplus.app.util.formatDateTime
import com.dioonplus.app.util.formatMoney
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

data class ReportDocumentMeta(
    val title: String,
    val subtitle: String,
    val periodLabel: String,
    val gaveCents: Long,
    val tookCents: Long,
    val accountName: String? = null,
    val accountPhone: String? = null,
) {
    val netCents: Long get() = gaveCents - tookCents
}

object PdfReportExporter {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val ROWS_PER_PAGE = 15

    fun write(
        output: OutputStream,
        rows: List<ReportRow>,
        meta: ReportDocumentMeta,
    ) {
        val document = PdfDocument()
        try {
            val pages = rows.chunked(ROWS_PER_PAGE).ifEmpty { listOf(emptyList()) }
            pages.forEachIndexed { pageIndex, pageRows ->
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas
                drawHeader(canvas, meta, pageIndex + 1, pages.size)
                drawSummary(canvas, meta)
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
        meta: ReportDocumentMeta,
    ): File {
        val directory = File(context.cacheDir, "reports").apply { mkdirs() }
        val suffix = meta.accountName
            ?.replace(Regex("[^\\p{L}\\p{N}]+"), "-")
            ?.trim('-')
            ?.take(32)
            ?.takeIf { it.isNotBlank() }
            ?: "report"
        val file = File(directory, "DioonPlus-$suffix-${fileSafeDate()}.pdf")
        FileOutputStream(file).use { write(it, rows, meta) }
        return file
    }

    private fun drawHeader(
        canvas: android.graphics.Canvas,
        meta: ReportDocumentMeta,
        page: Int,
        pageCount: Int,
    ) {
        val darkBlue = Color.rgb(14, 49, 91)
        val blue = Color.rgb(23, 105, 224)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkBlue
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 10.5f
            textAlign = Paint.Align.RIGHT
        }
        val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blue }
        canvas.drawRoundRect(505f, 34f, 555f, 84f, 10f, 10f, logoPaint)
        val logoText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("د+", 530f, 66f, logoText)
        canvas.drawText(meta.title.take(38), 492f, 52f, titlePaint)
        canvas.drawText(meta.subtitle.take(70), 492f, 72f, subtitlePaint)
        canvas.drawText(meta.periodLabel.take(80), 555f, 95f, subtitlePaint)
        meta.accountName?.let { name ->
            val detail = buildString {
                append("صاحب الحساب: ").append(name)
                if (!meta.accountPhone.isNullOrBlank()) append("  •  ").append(meta.accountPhone)
            }
            canvas.drawText(detail.take(80), 555f, 112f, subtitlePaint)
        }
        canvas.drawText("صفحة $page من $pageCount", 555f, 815f, subtitlePaint)
        canvas.drawLine(40f, 122f, 555f, 122f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = blue
            strokeWidth = 2f
        })
    }

    private fun drawSummary(canvas: android.graphics.Canvas, meta: ReportDocumentMeta) {
        val labels = listOf(
            Triple("إجمالي أعطيت", formatMoney(meta.gaveCents), Color.rgb(21, 152, 108)),
            Triple("إجمالي أخذت", formatMoney(meta.tookCents), Color.rgb(226, 85, 85)),
            Triple("صافي الفترة", formatMoney(meta.netCents, includeSign = true), Color.rgb(23, 105, 224)),
        )
        val top = 138f
        val boxWidth = 160f
        val gap = 15f
        labels.forEachIndexed { index, item ->
            val right = 555f - index * (boxWidth + gap)
            val left = right - boxWidth
            val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(247, 249, 252) }
            canvas.drawRoundRect(left, top, right, top + 60f, 10f, 10f, background)
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = 10f
                textAlign = Paint.Align.RIGHT
            }
            val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = item.third
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(item.first, right - 10f, top + 22f, labelPaint)
            canvas.drawText(item.second, right - 10f, top + 47f, valuePaint)
        }
    }

    private fun drawTable(canvas: android.graphics.Canvas, rows: List<ReportRow>) {
        val startY = 215f
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
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(230, 236, 243)
            strokeWidth = 1f
        }

        if (rows.isEmpty()) {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                textSize = 13f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("لا توجد حركات مطابقة للفلاتر", PAGE_WIDTH / 2f, startY + 90f, emptyPaint)
            return
        }

        rows.forEachIndexed { index, row ->
            val top = startY + 32f + index * rowHeight
            if (index % 2 == 1) {
                canvas.drawRect(40f, top, 555f, top + rowHeight, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(250, 251, 253)
                })
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
