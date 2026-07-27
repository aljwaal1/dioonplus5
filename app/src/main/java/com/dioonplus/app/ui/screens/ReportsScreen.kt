package com.dioonplus.app.ui.screens

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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.IosShare
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dioonplus.app.ui.theme.BorderColor
import com.dioonplus.app.ui.theme.DebtRed
import com.dioonplus.app.ui.theme.DebtRedSoft
import com.dioonplus.app.ui.theme.DioonBlue
import com.dioonplus.app.ui.theme.DioonBlueDark
import com.dioonplus.app.ui.theme.DioonBlueSoft
import com.dioonplus.app.ui.theme.SuccessGreen
import com.dioonplus.app.ui.theme.SuccessGreenSoft
import com.dioonplus.app.ui.theme.TextSecondary

@Composable
fun ReportsScreen(contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("التقارير", style = MaterialTheme.typography.headlineMedium, color = DioonBlueDark)
            Text("صورة واضحة عن حركة ديونك ودفعاتك", color = TextSecondary)
        }
        item { DateRangeCard() }
        item { ReportSummary() }
        item { ActivityChartCard() }
        item { PdfPreviewCard() }
        item { ExportActions() }
    }
}

@Composable
private fun DateRangeCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Text("هذا الشهر", fontWeight = FontWeight.SemiBold)
                    Text("1 يوليو - 31 يوليو 2026", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
            Icon(Icons.Outlined.FilterAlt, contentDescription = "تصفية", tint = DioonBlueDark)
        }
    }
}

@Composable
private fun ReportSummary() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard(Modifier.weight(1f), "الديون", "4,920", DebtRed, DebtRedSoft)
        MetricCard(Modifier.weight(1f), "الدفعات", "3,825", SuccessGreen, SuccessGreenSoft)
        MetricCard(Modifier.weight(1f), "الصافي", "+1,095", DioonBlue, DioonBlueSoft)
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
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = accent)
            Text("د.أ", style = MaterialTheme.typography.bodyMedium, color = accent)
        }
    }
}

@Composable
private fun ActivityChartCard() {
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
                    Text("مقارنة الديون بالدفعات", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LegendDot(DebtRed, "ديون")
                    LegendDot(SuccessGreen, "دفعات")
                }
            }
            Spacer(Modifier.height(18.dp))
            BarChart()
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("س", "ح", "ن", "ث", "ر", "خ", "ج").forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
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
private fun BarChart() {
    val debt = listOf(0.55f, 0.78f, 0.44f, 0.90f, 0.60f, 0.72f, 0.48f)
    val paid = listOf(0.38f, 0.62f, 0.70f, 0.50f, 0.82f, 0.58f, 0.76f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
    ) {
        val groupWidth = size.width / debt.size
        val baseline = size.height
        val barWidth = groupWidth * 0.18f
        debt.indices.forEach { index ->
            val center = groupWidth * index + groupWidth / 2
            drawLine(
                color = DebtRed.copy(alpha = 0.85f),
                start = Offset(center - barWidth, baseline),
                end = Offset(center - barWidth, baseline - size.height * debt[index]),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = SuccessGreen.copy(alpha = 0.85f),
                start = Offset(center + barWidth, baseline),
                end = Offset(center + barWidth, baseline - size.height * paid[index]),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun PdfPreviewCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("معاينة تقرير PDF", style = MaterialTheme.typography.titleMedium)
            Text("شكل واضح واحترافي يصلح للطباعة والمشاركة", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(Modifier.height(14.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(DioonBlue, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("د+", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.size(8.dp))
                            Column {
                                Text("ديون بلس", fontWeight = FontWeight.Bold, color = DioonBlueDark)
                                Text("كشف حركة الحساب", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            }
                        }
                        Text("رقم #00024", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                    Spacer(Modifier.height(14.dp))
                    Surface(color = DioonBlueSoft, shape = RoundedCornerShape(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("العميل: أحمد الخطيب", fontWeight = FontWeight.SemiBold)
                            Text("الرصيد: 1,250 د.أ", color = DioonBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    PreviewTableRow("22 يوليو", "دفعة", "250", SuccessGreen)
                    PreviewTableRow("18 يوليو", "دين", "500", DebtRed)
                    PreviewTableRow("10 يوليو", "دين", "1,000", DebtRed)
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("3 معاملات", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text("صفحة 1 من 1", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewTableRow(date: String, type: String, amount: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(date, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(type, style = MaterialTheme.typography.bodyMedium)
        Text("$amount د.أ", style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ExportActions() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = DioonBlue),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Outlined.FileDownload, contentDescription = null)
            Spacer(Modifier.size(7.dp))
            Text("تصدير PDF")
        }
        OutlinedButton(
            onClick = { },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Outlined.IosShare, contentDescription = null)
            Spacer(Modifier.size(7.dp))
            Text("مشاركة")
        }
    }
}
