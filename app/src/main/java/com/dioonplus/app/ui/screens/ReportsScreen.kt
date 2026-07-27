package com.dioonplus.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.dioonplus.app.data.ReportRow
import com.dioonplus.app.report.PdfReportExporter
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

@Composable
fun ReportsScreen(contentPadding: PaddingValues, appState: DioonAppState) {
    val context = LocalContext.current
    val rows = appState.reportRows
    val totalGave = remember(rows.toList()) { rows.filter { it.entryType == EntryType.GAVE }.sumOf { it.amountCents } }
    val totalTook = remember(rows.toList()) { rows.filter { it.entryType == EntryType.TOOK }.sumOf { it.amountCents } }
    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    PdfReportExporter.write(stream, rows.toList(), appState.summary)
                } ?: error("تعذر فتح الملف")
            }.onSuccess {
                Toast.makeText(context, "تم حفظ التقرير بنجاح", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "تعذر حفظ التقرير", Toast.LENGTH_LONG).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("التقارير", style = MaterialTheme.typography.headlineMedium, color = DioonBlueDark)
            Text("تقارير فعلية مبنية على الحركات المحفوظة", color = TextSecondary)
        }
        item { PeriodCard(rows.toList()) }
        item {
            ReportSummary(
                gaveCents = totalGave,
                tookCents = totalTook,
                netCents = appState.summary.netCents,
            )
        }
        item { ActivityChartCard(appState.dailyTotals.toList()) }
        item {
            ExportCard(
                transactionCount = rows.size,
                onExport = {
                    createPdfLauncher.launch("DioonPlus-report-${fileSafeDate()}.pdf")
                },
                onShare = {
                    runCatching {
                        val file = PdfReportExporter.createShareFile(context, rows.toList(), appState.summary)
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.files",
                            file,
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "مشاركة تقرير ديون بلس"))
                    }.onFailure {
                        Toast.makeText(context, "تعذر تجهيز التقرير للمشاركة", Toast.LENGTH_LONG).show()
                    }
                },
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("أحدث الحركات", style = MaterialTheme.typography.titleLarge)
                Text("${rows.size} حركة", color = TextSecondary)
            }
        }
        if (rows.isEmpty()) {
            item { EmptyReportCard() }
        } else {
            items(rows.take(12), key = { it.entryId }) { row ->
                ReportTransactionCard(row)
            }
        }
    }
}

@Composable
private fun PeriodCard(rows: List<ReportRow>) {
    val period = when {
        rows.isEmpty() -> "لا توجد بيانات حتى الآن"
        rows.size == 1 -> formatDateTime(rows.first().createdAt)
        else -> "من ${formatDateTime(rows.last().createdAt)} إلى ${formatDateTime(rows.first().createdAt)}"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(DioonBlueSoft, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = DioonBlue)
            }
            Spacer(Modifier.size(10.dp))
            Column {
                Text("كل الحركات", fontWeight = FontWeight.SemiBold)
                Text(period, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
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
private fun MetricCard(
    modifier: Modifier,
    title: String,
    value: String,
    accent: Color,
    soft: Color,
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("حركة آخر 7 أيام", style = MaterialTheme.typography.titleMedium)
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
                totals.forEach { total ->
                    Text(formatDay(total.dayStartMillis), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
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
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
    ) {
        if (totals.isEmpty()) return@Canvas
        val groupWidth = size.width / totals.size
        val baseline = size.height
        val barWidth = groupWidth * 0.18f
        totals.forEachIndexed { index, total ->
            val center = groupWidth * index + groupWidth / 2
            val gaveRatio = total.gaveCents.toFloat() / maxValue.toFloat()
            val tookRatio = total.tookCents.toFloat() / maxValue.toFloat()
            drawLine(
                color = SuccessGreen.copy(alpha = 0.88f),
                start = Offset(center - barWidth, baseline),
                end = Offset(center - barWidth, baseline - size.height * gaveRatio),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = DebtRed.copy(alpha = 0.88f),
                start = Offset(center + barWidth, baseline),
                end = Offset(center + barWidth, baseline - size.height * tookRatio),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun ExportCard(
    transactionCount: Int,
    onExport: () -> Unit,
    onShare: () -> Unit,
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
                    modifier = Modifier
                        .size(44.dp)
                        .background(DioonBlueSoft, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = DioonBlue)
                }
                Spacer(Modifier.size(10.dp))
                Column {
                    Text("تقرير PDF احترافي", style = MaterialTheme.typography.titleMedium)
                    Text("يشمل $transactionCount حركة والملخص المالي", color = TextSecondary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onExport,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = DioonBlue),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null)
                    Spacer(Modifier.size(7.dp))
                    Text("حفظ PDF")
                }
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Outlined.IosShare, contentDescription = null)
                    Spacer(Modifier.size(7.dp))
                    Text("مشاركة")
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
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.partyName, fontWeight = FontWeight.SemiBold)
                Text(
                    if (gave) "أعطيت • ${formatDateTime(row.createdAt)}" else "أخذت • ${formatDateTime(row.createdAt)}",
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
        Column(
            modifier = Modifier.padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(10.dp))
            Text("لا توجد بيانات للتقرير", fontWeight = FontWeight.Bold)
            Text("أضف حساباً وسجّل حركة لتظهر النتائج هنا", color = TextSecondary)
        }
    }
}
