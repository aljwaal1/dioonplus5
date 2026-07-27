package com.dioonplus.app.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

private data class LedgerPerson(
    val name: String,
    val lastActivity: String,
    val amount: String,
    val isOwedToUser: Boolean,
    val initials: String,
)

private val sampleCustomers = listOf(
    LedgerPerson("أحمد الخطيب", "آخر حركة اليوم، 10:25", "1,250 د.أ", true, "أخ"),
    LedgerPerson("محمد سالم", "آخر حركة أمس", "480 د.أ", false, "مس"),
    LedgerPerson("سارة محمود", "آخر حركة منذ 3 أيام", "325 د.أ", true, "سم"),
    LedgerPerson("متجر النور", "آخر حركة منذ أسبوع", "1,840 د.أ", false, "من"),
)

@Composable
fun HomeScreen(contentPadding: PaddingValues) {
    var selectedLedger by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { HomeHeader() }
            item { LedgerTabs(selectedLedger = selectedLedger, onSelected = { selectedLedger = it }) }
            item { OverviewCard() }
            item {
                TextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("ابحث بالاسم أو رقم الهاتف") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        disabledContainerColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (selectedLedger == 0) "العملاء" else "الموردون",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "${sampleCustomers.size} حسابات",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
            items(sampleCustomers.filter { it.name.contains(search, ignoreCase = true) }) { person ->
                PersonCard(person)
            }
        }

        ExtendedFloatingActionButton(
            onClick = { },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            containerColor = DioonBlue,
            contentColor = Color.White,
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text(if (selectedLedger == 0) "إضافة عميل" else "إضافة مورد") },
        )
    }
}

@Composable
private fun HomeHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(DioonBlue),
                contentAlignment = Alignment.Center,
            ) {
                Text("د+", color = Color.White, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.size(12.dp))
            Column {
                Text("صباح الخير", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text("دفتر حساباتي", style = MaterialTheme.typography.titleLarge, color = DioonBlueDark)
            }
        }
        IconButton(onClick = { }) {
            Icon(Icons.Outlined.NotificationsNone, contentDescription = "التنبيهات", tint = DioonBlueDark)
        }
    }
}

@Composable
private fun LedgerTabs(selectedLedger: Int, onSelected: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            listOf("العملاء", "الموردون").forEachIndexed { index, title ->
                val selected = selectedLedger == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) DioonBlue else Color.Transparent)
                        .clickable { onSelected(index) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title,
                        color = if (selected) Color.White else TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewCard() {
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
                    Text("ملخص الحسابات", style = MaterialTheme.typography.titleMedium)
                    Text("محدّث الآن", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Icon(Icons.Outlined.VisibilityOff, contentDescription = "إخفاء الأرصدة", tint = TextSecondary)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewMetric(
                    modifier = Modifier.weight(1f),
                    label = "لك عند العملاء",
                    value = "3,415 د.أ",
                    valueColor = SuccessGreen,
                    background = SuccessGreenSoft,
                )
                OverviewMetric(
                    modifier = Modifier.weight(1f),
                    label = "عليك للموردين",
                    value = "2,320 د.أ",
                    valueColor = DebtRed,
                    background = DebtRedSoft,
                )
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DioonBlueSoft,
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("صافي الرصيد", color = DioonBlueDark, fontWeight = FontWeight.SemiBold)
                    Text("+1,095 د.أ", color = DioonBlue, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun OverviewMetric(
    modifier: Modifier,
    label: String,
    value: String,
    valueColor: Color,
    background: Color,
) {
    Surface(modifier = modifier, color = background, shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
        }
    }
}

@Composable
private fun PersonCard(person: LedgerPerson) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(DioonBlueSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(person.initials, color = DioonBlueDark, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(person.lastActivity, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = person.amount,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (person.isOwedToUser) SuccessGreen else DebtRed,
                )
                Text(
                    text = if (person.isOwedToUser) "عليه لك" else "له عليك",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }
    }
}
