from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


# Add account id and phone to derived report rows.
db_path = "app/src/main/java/com/dioonplus/app/data/LedgerDatabase.kt"
text = read(db_path)
text = replace_once(
    text,
    '''            SELECT e.id AS entry_id, p.name AS party_name, p.type AS party_type,
                   e.entry_type, e.amount_cents, e.note, e.created_at''',
    '''            SELECT e.id AS entry_id, p.id AS party_id, p.name AS party_name,
                   p.phone AS party_phone, p.type AS party_type,
                   e.entry_type, e.amount_cents, e.note, e.created_at''',
    "report query identity",
)
text = replace_once(
    text,
    '''                            entryId = cursor.getLong(cursor.getColumnIndexOrThrow("entry_id")),
                            partyName = cursor.getString(cursor.getColumnIndexOrThrow("party_name")),
                            partyType = PartyType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("party_type"))),''',
    '''                            entryId = cursor.getLong(cursor.getColumnIndexOrThrow("entry_id")),
                            partyId = cursor.getLong(cursor.getColumnIndexOrThrow("party_id")),
                            partyName = cursor.getString(cursor.getColumnIndexOrThrow("party_name")),
                            partyPhone = cursor.getString(cursor.getColumnIndexOrThrow("party_phone")),
                            partyType = PartyType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("party_type"))),''',
    "report row identity",
)
write(db_path, text)

write(
    "app/src/main/java/com/dioonplus/app/report/PdfReportExporter.kt",
    r'''package com.dioonplus.app.report

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
''',
)

write(
    "app/src/main/java/com/dioonplus/app/ui/screens/ReportsScreen.kt",
    r'''package com.dioonplus.app.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.dioonplus.app.DioonAppState
import com.dioonplus.app.data.DailyTotal
import com.dioonplus.app.data.EntryType
import com.dioonplus.app.data.PartyType
import com.dioonplus.app.data.ReportRow
import com.dioonplus.app.report.PdfReportExporter
import com.dioonplus.app.report.ReportDocumentMeta
import com.dioonplus.app.ui.theme.BorderColor
import com.dioonplus.app.ui.theme.DebtRed
import com.dioonplus.app.ui.theme.DebtRedSoft
import com.dioonplus.app.ui.theme.DioonBlue
import com.dioonplus.app.ui.theme.DioonBlueDark
import com.dioonplus.app.ui.theme.DioonBlueSoft
import com.dioonplus.app.ui.theme.SuccessGreen
import com.dioonplus.app.ui.theme.SuccessGreenSoft
import com.dioonplus.app.ui.theme.TextSecondary
import com.dioonplus.app.util.fileSafeDate
import com.dioonplus.app.util.formatDateTime
import com.dioonplus.app.util.formatDay
import com.dioonplus.app.util.formatMoney
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ReportPeriod(val label: String) {
    ALL("الكل"), TODAY("اليوم"), WEEK("7 أيام"), MONTH("هذا الشهر"), CUSTOM("مخصص")
}

enum class ReportPartyFilter(val label: String) {
    ALL("الكل"), CUSTOMERS("العملاء"), SUPPLIERS("الموردون")
}

enum class ReportEntryFilter(val label: String) {
    ALL("كل الحركات"), GAVE("أعطيت"), TOOK("أخذت")
}

data class ReportAccountOption(
    val id: Long,
    val name: String,
    val phone: String,
    val type: PartyType,
)

@Composable
fun ReportsScreen(contentPadding: PaddingValues, appState: DioonAppState) {
    val context = LocalContext.current
    val allRows = appState.reportRows.toList()
    var period by rememberSaveable { mutableStateOf(ReportPeriod.ALL) }
    var partyFilter by rememberSaveable { mutableStateOf(ReportPartyFilter.ALL) }
    var entryFilter by rememberSaveable { mutableStateOf(ReportEntryFilter.ALL) }
    var selectedPartyId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var customStart by rememberSaveable { mutableLongStateOf(startOfDay(System.currentTimeMillis())) }
    var customEnd by rememberSaveable { mutableLongStateOf(endOfDay(System.currentTimeMillis())) }
    var pendingPdfRows by remember { mutableStateOf<List<ReportRow>>(emptyList()) }
    var pendingPdfMeta by remember { mutableStateOf<ReportDocumentMeta?>(null) }

    val accounts = remember(allRows) {
        allRows
            .distinctBy { it.partyId }
            .map { ReportAccountOption(it.partyId, it.partyName, it.partyPhone, it.partyType) }
            .sortedBy { it.name.lowercase() }
    }
    val visibleAccounts = remember(accounts, partyFilter) {
        accounts.filter {
            when (partyFilter) {
                ReportPartyFilter.ALL -> true
                ReportPartyFilter.CUSTOMERS -> it.type == PartyType.CUSTOMER
                ReportPartyFilter.SUPPLIERS -> it.type == PartyType.SUPPLIER
            }
        }
    }
    val selectedAccount = accounts.firstOrNull { it.id == selectedPartyId }
    val bounds = remember(period, customStart, customEnd) { periodBounds(period, customStart, customEnd) }
    val filteredRows = remember(allRows, partyFilter, entryFilter, selectedPartyId, bounds) {
        allRows.filter { row ->
            val typeMatches = when (partyFilter) {
                ReportPartyFilter.ALL -> true
                ReportPartyFilter.CUSTOMERS -> row.partyType == PartyType.CUSTOMER
                ReportPartyFilter.SUPPLIERS -> row.partyType == PartyType.SUPPLIER
            }
            val entryMatches = when (entryFilter) {
                ReportEntryFilter.ALL -> true
                ReportEntryFilter.GAVE -> row.entryType == EntryType.GAVE
                ReportEntryFilter.TOOK -> row.entryType == EntryType.TOOK
            }
            val partyMatches = selectedPartyId == null || row.partyId == selectedPartyId
            val dateMatches = bounds.first?.let { row.createdAt >= it } != false &&
                bounds.second?.let { row.createdAt <= it } != false
            typeMatches && entryMatches && partyMatches && dateMatches
        }
    }
    val totalGave = remember(filteredRows) {
        filteredRows.filter { it.entryType == EntryType.GAVE }.sumOf { it.amountCents }
    }
    val totalTook = remember(filteredRows) {
        filteredRows.filter { it.entryType == EntryType.TOOK }.sumOf { it.amountCents }
    }
    val periodLabel = remember(period, customStart, customEnd) {
        buildPeriodLabel(period, customStart, customEnd)
    }
    val reportMeta = remember(selectedAccount, periodLabel, entryFilter, totalGave, totalTook) {
        ReportDocumentMeta(
            title = if (selectedAccount == null) "تقرير الحركة المالية" else "كشف حساب ${selectedAccount.name}",
            subtitle = buildString {
                append(if (selectedAccount == null) "تقرير تفصيلي حسب الفلاتر المختارة" else "كشف حساب تفصيلي لصاحب الحساب")
                if (entryFilter != ReportEntryFilter.ALL) append(" • ${entryFilter.label}")
            },
            periodLabel = periodLabel,
            gaveCents = totalGave,
            tookCents = totalTook,
            accountName = selectedAccount?.name,
            accountPhone = selectedAccount?.phone,
        )
    }
    val dailyTotals = remember(filteredRows) { buildDailyTotals(filteredRows) }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        val meta = pendingPdfMeta
        if (uri != null && meta != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    PdfReportExporter.write(stream, pendingPdfRows, meta)
                } ?: error("تعذر فتح الملف")
            }.onSuccess {
                Toast.makeText(context, "تم حفظ التقرير بنجاح", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "تعذر حفظ التقرير", Toast.LENGTH_LONG).show()
            }
        }
        pendingPdfMeta = null
        pendingPdfRows = emptyList()
    }

    fun savePdf() {
        pendingPdfRows = filteredRows
        pendingPdfMeta = reportMeta
        val suffix = selectedAccount?.name?.take(24) ?: "report"
        createPdfLauncher.launch("DioonPlus-$suffix-${fileSafeDate()}.pdf")
    }

    fun sharePdf(toOwner: Boolean) {
        runCatching {
            val file = PdfReportExporter.createShareFile(context, filteredRows, reportMeta)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val owner = selectedAccount
            val text = if (toOwner && owner != null) {
                buildString {
                    append("مرحباً ${owner.name}، مرفق كشف حسابك من تطبيق ديون بلس للفترة: $periodLabel.")
                    append("\nإجمالي أعطيت: ${formatMoney(totalGave)}")
                    append("\nإجمالي أخذت: ${formatMoney(totalTook)}")
                    append("\nصافي الفترة: ${formatMoney(totalGave - totalTook, includeSign = true)}")
                    if (owner.phone.isNotBlank()) append("\nرقم الحساب المسجل: ${owner.phone}")
                }
            } else {
                "مرفق تقرير ديون بلس للفترة: $periodLabel — ${filteredRows.size} حركة."
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val title = if (toOwner && owner != null) "إرسال كشف الحساب إلى ${owner.name}" else "مشاركة تقرير ديون بلس"
            context.startActivity(Intent.createChooser(intent, title))
        }.onFailure {
            Toast.makeText(context, "تعذر تجهيز التقرير للمشاركة", Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("التقارير", style = MaterialTheme.typography.headlineMedium, color = DioonBlueDark)
            Text("تقارير وكشوف حساب قابلة للتصفية والحفظ والإرسال", color = TextSecondary)
        }
        item {
            ReportFiltersCard(
                period = period,
                onPeriodChange = { period = it },
                partyFilter = partyFilter,
                onPartyFilterChange = {
                    partyFilter = it
                    if (selectedAccount != null && selectedAccount.type !in allowedTypes(it)) selectedPartyId = null
                },
                entryFilter = entryFilter,
                onEntryFilterChange = { entryFilter = it },
                selectedAccount = selectedAccount,
                customStart = customStart,
                customEnd = customEnd,
                onPickStart = {
                    showDatePicker(context, customStart) {
                        customStart = startOfDay(it)
                        if (customEnd < customStart) customEnd = endOfDay(it)
                    }
                },
                onPickEnd = {
                    showDatePicker(context, customEnd) {
                        customEnd = endOfDay(it)
                        if (customStart > customEnd) customStart = startOfDay(it)
                    }
                },
                onPickAccount = { showAccountDialog = true },
                onClearAccount = { selectedPartyId = null },
                onReset = {
                    period = ReportPeriod.ALL
                    partyFilter = ReportPartyFilter.ALL
                    entryFilter = ReportEntryFilter.ALL
                    selectedPartyId = null
                },
            )
        }
        item { PeriodCard(reportMeta.title, periodLabel, filteredRows.size) }
        item { ReportSummary(totalGave, totalTook, totalGave - totalTook) }
        item { ActivityChartCard(dailyTotals) }
        item {
            ExportCard(
                transactionCount = filteredRows.size,
                selectedAccount = selectedAccount,
                onExport = ::savePdf,
                onShare = { sharePdf(false) },
                onSendToOwner = { sharePdf(true) },
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("الحركات المطابقة", style = MaterialTheme.typography.titleLarge)
                Text("${filteredRows.size} حركة", color = TextSecondary)
            }
        }
        if (filteredRows.isEmpty()) {
            item { EmptyReportCard() }
        } else {
            items(filteredRows, key = { it.entryId }) { row -> ReportTransactionCard(row) }
        }
    }

    if (showAccountDialog) {
        AccountPickerDialog(
            accounts = visibleAccounts,
            selectedId = selectedPartyId,
            onDismiss = { showAccountDialog = false },
            onSelect = {
                selectedPartyId = it
                showAccountDialog = false
            },
        )
    }
}

@Composable
private fun ReportFiltersCard(
    period: ReportPeriod,
    onPeriodChange: (ReportPeriod) -> Unit,
    partyFilter: ReportPartyFilter,
    onPartyFilterChange: (ReportPartyFilter) -> Unit,
    entryFilter: ReportEntryFilter,
    onEntryFilterChange: (ReportEntryFilter) -> Unit,
    selectedAccount: ReportAccountOption?,
    customStart: Long,
    customEnd: Long,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onPickAccount: () -> Unit,
    onClearAccount: () -> Unit,
    onReset: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FilterAlt, contentDescription = null, tint = DioonBlue)
                Spacer(Modifier.size(8.dp))
                Text("تصفية التقرير", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onReset) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = null)
                    Text("إعادة ضبط")
                }
            }
            FilterLabel("الفترة")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ReportPeriod.entries) { option ->
                    FilterChip(selected = period == option, onClick = { onPeriodChange(option) }, label = { Text(option.label) })
                }
            }
            if (period == ReportPeriod.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPickStart, modifier = Modifier.weight(1f)) {
                        Text("من ${formatShortDate(customStart)}")
                    }
                    OutlinedButton(onClick = onPickEnd, modifier = Modifier.weight(1f)) {
                        Text("إلى ${formatShortDate(customEnd)}")
                    }
                }
            }
            FilterLabel("نوع الحساب")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ReportPartyFilter.entries) { option ->
                    FilterChip(selected = partyFilter == option, onClick = { onPartyFilterChange(option) }, label = { Text(option.label) })
                }
            }
            FilterLabel("صاحب الحساب")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickAccount, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.PersonSearch, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(selectedAccount?.name ?: "كل أصحاب الحسابات")
                }
                if (selectedAccount != null) TextButton(onClick = onClearAccount) { Text("إلغاء") }
            }
            FilterLabel("نوع الحركة")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ReportEntryFilter.entries) { option ->
                    FilterChip(selected = entryFilter == option, onClick = { onEntryFilterChange(option) }, label = { Text(option.label) })
                }
            }
        }
    }
}

@Composable
private fun FilterLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun AccountPickerDialog(
    accounts: List<ReportAccountOption>,
    selectedId: Long?,
    onDismiss: () -> Unit,
    onSelect: (Long?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختر صاحب الحساب") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 430.dp)) {
                item {
                    AccountOptionRow("كل أصحاب الحسابات", "بدون تحديد حساب", selectedId == null) { onSelect(null) }
                }
                items(accounts, key = { it.id }) { account ->
                    AccountOptionRow(
                        account.name,
                        buildString {
                            append(if (account.type == PartyType.CUSTOMER) "عميل" else "مورد")
                            if (account.phone.isNotBlank()) append(" • ${account.phone}")
                        },
                        selectedId == account.id,
                    ) { onSelect(account.id) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}

@Composable
private fun AccountOptionRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = if (selected) DioonBlueSoft else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = if (selected) DioonBlue else DioonBlueDark)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

@Composable
private fun PeriodCard(title: String, period: String, count: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(42.dp).background(DioonBlueSoft, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = DioonBlue) }
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(period, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Text("$count", color = DioonBlue, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReportSummary(gaveCents: Long, tookCents: Long, netCents: Long) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard(Modifier.weight(1f), "أعطيت", formatMoney(gaveCents), SuccessGreen, SuccessGreenSoft)
        MetricCard(Modifier.weight(1f), "أخذت", formatMoney(tookCents), DebtRed, DebtRedSoft)
        MetricCard(Modifier.weight(1f), "الصافي", formatMoney(netCents, includeSign = true), DioonBlue, DioonBlueSoft)
    }
}

@Composable
private fun MetricCard(modifier: Modifier, title: String, value: String, accent: Color, soft: Color) {
    Surface(modifier = modifier, color = soft, shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 13.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(Modifier.height(5.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActivityChartCard(totals: List<DailyTotal>) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("حركة آخر 7 أيام ضمن الفلاتر", style = MaterialTheme.typography.titleMedium)
                    Text("مقارنة أعطيت بأخذت", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LegendDot(SuccessGreen, "أعطيت")
                    LegendDot(DebtRed, "أخذت")
                }
            }
            Spacer(Modifier.height(18.dp))
            RealBarChart(totals)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                totals.forEach { Text(formatDay(it.dayStartMillis), style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
        Spacer(Modifier.size(5.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
private fun RealBarChart(totals: List<DailyTotal>) {
    val maxValue = totals.maxOfOrNull { maxOf(it.gaveCents, it.tookCents) }?.coerceAtLeast(1L) ?: 1L
    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
        if (totals.isEmpty()) return@Canvas
        val groupWidth = size.width / totals.size
        val baseline = size.height
        val barWidth = groupWidth * 0.18f
        totals.forEachIndexed { index, total ->
            val center = groupWidth * index + groupWidth / 2
            val gaveRatio = total.gaveCents.toFloat() / maxValue.toFloat()
            val tookRatio = total.tookCents.toFloat() / maxValue.toFloat()
            drawLine(SuccessGreen.copy(alpha = 0.88f), Offset(center - barWidth, baseline), Offset(center - barWidth, baseline - size.height * gaveRatio), barWidth, StrokeCap.Round)
            drawLine(DebtRed.copy(alpha = 0.88f), Offset(center + barWidth, baseline), Offset(center + barWidth, baseline - size.height * tookRatio), barWidth, StrokeCap.Round)
        }
    }
}

@Composable
private fun ExportCard(
    transactionCount: Int,
    selectedAccount: ReportAccountOption?,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onSendToOwner: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).background(DioonBlueSoft, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = DioonBlue) }
                Spacer(Modifier.size(10.dp))
                Column {
                    Text(if (selectedAccount == null) "تقرير PDF احترافي" else "كشف حساب ${selectedAccount.name}", style = MaterialTheme.typography.titleMedium)
                    Text("يشمل $transactionCount حركة مطابقة للفلاتر", color = TextSecondary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onExport, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = DioonBlue), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null)
                    Spacer(Modifier.size(7.dp))
                    Text("حفظ PDF")
                }
                OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.IosShare, contentDescription = null)
                    Spacer(Modifier.size(7.dp))
                    Text("مشاركة")
                }
            }
            if (selectedAccount != null) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onSendToOwner,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Outlined.Send, contentDescription = null)
                    Spacer(Modifier.size(7.dp))
                    Text("إرسال كشف الحساب لصاحب الحساب")
                }
                if (selectedAccount.phone.isBlank()) {
                    Text("لا يوجد رقم هاتف محفوظ؛ اختر المستلم يدوياً من تطبيق المشاركة.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun ReportTransactionCard(row: ReportRow) {
    val gave = row.entryType == EntryType.GAVE
    val accent = if (gave) SuccessGreen else DebtRed
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.partyName, fontWeight = FontWeight.SemiBold)
                Text(
                    "${if (gave) "أعطيت" else "أخذت"} • ${if (row.partyType == PartyType.CUSTOMER) "عميل" else "مورد"} • ${formatDateTime(row.createdAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                if (row.note.isNotBlank()) Text(row.note, style = MaterialTheme.typography.bodyMedium, color = DioonBlueDark)
            }
            Text(formatMoney(row.amountCents), color = accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyReportCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
    ) {
        Column(modifier = Modifier.padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(10.dp))
            Text("لا توجد حركات مطابقة", fontWeight = FontWeight.Bold)
            Text("غيّر الفترة أو صاحب الحساب أو نوع الحركة", color = TextSecondary)
        }
    }
}

private fun allowedTypes(filter: ReportPartyFilter): Set<PartyType> = when (filter) {
    ReportPartyFilter.ALL -> PartyType.entries.toSet()
    ReportPartyFilter.CUSTOMERS -> setOf(PartyType.CUSTOMER)
    ReportPartyFilter.SUPPLIERS -> setOf(PartyType.SUPPLIER)
}

private fun periodBounds(period: ReportPeriod, customStart: Long, customEnd: Long): Pair<Long?, Long?> {
    val now = System.currentTimeMillis()
    return when (period) {
        ReportPeriod.ALL -> null to null
        ReportPeriod.TODAY -> startOfDay(now) to endOfDay(now)
        ReportPeriod.WEEK -> {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = startOfDay(now)
                add(Calendar.DAY_OF_YEAR, -6)
            }
            calendar.timeInMillis to endOfDay(now)
        }
        ReportPeriod.MONTH -> {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            calendar.timeInMillis to endOfDay(now)
        }
        ReportPeriod.CUSTOM -> minOf(customStart, customEnd) to maxOf(customStart, customEnd)
    }
}

private fun buildPeriodLabel(period: ReportPeriod, customStart: Long, customEnd: Long): String = when (period) {
    ReportPeriod.ALL -> "كل الفترات"
    ReportPeriod.TODAY -> "اليوم: ${formatShortDate(System.currentTimeMillis())}"
    ReportPeriod.WEEK -> "آخر 7 أيام"
    ReportPeriod.MONTH -> "الشهر الحالي"
    ReportPeriod.CUSTOM -> "من ${formatShortDate(customStart)} إلى ${formatShortDate(customEnd)}"
}

private fun buildDailyTotals(rows: List<ReportRow>): List<DailyTotal> {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = startOfDay(System.currentTimeMillis())
        add(Calendar.DAY_OF_YEAR, -6)
    }
    val totals = LinkedHashMap<Long, LongArray>()
    repeat(7) {
        totals[calendar.timeInMillis] = longArrayOf(0L, 0L)
        calendar.add(Calendar.DAY_OF_YEAR, 1)
    }
    rows.forEach { row ->
        val day = startOfDay(row.createdAt)
        val bucket = totals[day] ?: return@forEach
        if (row.entryType == EntryType.GAVE) bucket[0] += row.amountCents else bucket[1] += row.amountCents
    }
    return totals.map { (day, values) -> DailyTotal(day, values[0], values[1]) }
}

private fun startOfDay(timestamp: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun endOfDay(timestamp: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 59)
    set(Calendar.MILLISECOND, 999)
}.timeInMillis

private fun formatShortDate(timestamp: Long): String =
    SimpleDateFormat("yyyy/MM/dd", Locale("ar")).format(Date(timestamp))

private fun showDatePicker(context: Context, initial: Long, onSelected: (Long) -> Unit) {
    val calendar = Calendar.getInstance().apply { timeInMillis = initial }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            val selected = Calendar.getInstance().apply {
                set(year, month, day, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            onSelected(selected.timeInMillis)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH),
    ).show()
}
''',
)

build_path = "app/build.gradle.kts"
text = read(build_path)
text = replace_once(
    text,
    '        versionCode = 6\n        versionName = "0.3.0"',
    '        versionCode = 7\n        versionName = "0.4.0"',
    "version bump",
)
write(build_path, text)

for relative in [
    "tools/apply_professional_reports.py",
    ".github/workflows/apply-professional-reports.yml",
]:
    target = ROOT / relative
    if target.exists():
        target.unlink()
