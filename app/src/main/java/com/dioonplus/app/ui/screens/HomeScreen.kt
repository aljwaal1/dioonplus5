package com.dioonplus.app.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dioonplus.app.DioonAppState
import com.dioonplus.app.data.DueItem
import com.dioonplus.app.data.Party
import com.dioonplus.app.data.PartyType
import com.dioonplus.app.ui.theme.BorderColor
import com.dioonplus.app.ui.theme.DebtRed
import com.dioonplus.app.ui.theme.DebtRedSoft
import com.dioonplus.app.ui.theme.DioonBlue
import com.dioonplus.app.ui.theme.DioonBlueDark
import com.dioonplus.app.ui.theme.DioonBlueSoft
import com.dioonplus.app.ui.theme.SuccessGreen
import com.dioonplus.app.ui.theme.SuccessGreenSoft
import com.dioonplus.app.ui.theme.TextSecondary
import com.dioonplus.app.util.formatDateTime
import com.dioonplus.app.util.formatMoney
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(contentPadding: PaddingValues, appState: DioonAppState) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showDueDialog by remember { mutableStateOf(false) }
    var balancesVisible by remember { mutableStateOf(true) }
    val tone = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 40) }
    DisposableEffect(Unit) { onDispose { tone.release() } }

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
            item { HomeHeader(appState.dueItems.size) { showDueDialog = true } }
            item {
                LedgerTabs(
                    selectedType = appState.selectedPartyType,
                    onSelected = appState::selectPartyType,
                )
            }
            item {
                OverviewCard(
                    receivableCents = appState.summary.receivableCents,
                    payableCents = appState.summary.payableCents,
                    balancesVisible = balancesVisible,
                    onToggleVisibility = { balancesVisible = !balancesVisible },
                )
            }
            if (appState.dueItems.isNotEmpty()) {
                item { DueSummaryCard(appState.dueSummary.overdueCount, appState.dueSummary.todayCount, appState.dueSummary.upcomingCount) { showDueDialog = true } }
            }
            item {
                TextField(
                    value = appState.searchQuery,
                    onValueChange = appState::updateSearch,
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
                        text = if (appState.selectedPartyType == PartyType.CUSTOMER) "العملاء" else "الموردون",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "${appState.parties.size} حسابات",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
            if (appState.parties.isEmpty()) {
                item {
                    EmptyPartiesCard(
                        type = appState.selectedPartyType,
                        onAdd = { showAddDialog = true },
                    )
                }
            } else {
                items(appState.parties, key = { it.id }) { party ->
                    PartyCard(
                        party = party,
                        balancesVisible = balancesVisible,
                        onClick = { appState.openParty(party.id) },
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            containerColor = DioonBlue,
            contentColor = Color.White,
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = {
                Text(if (appState.selectedPartyType == PartyType.CUSTOMER) "إضافة عميل" else "إضافة مورد")
            },
        )
    }

    if (showDueDialog) {
        DueItemsDialog(
            items = appState.dueItems,
            onDismiss = { showDueDialog = false },
            onOpen = { item -> showDueDialog = false; appState.openParty(item.partyId) },
        )
    }

    if (showAddDialog) {
        AddPartyDialog(
            type = appState.selectedPartyType,
            onDismiss = { showAddDialog = false },
            onSave = { name, phone ->
                val saved = appState.addParty(name, phone, appState.selectedPartyType)
                if (saved) {
                    tone.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                    showAddDialog = false
                }
                saved
            },
        )
    }
}

@Composable
private fun HomeHeader(dueCount: Int, onNotifications: () -> Unit) {
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
                Text("مرحباً بك", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text("دفتر حساباتي", style = MaterialTheme.typography.titleLarge, color = DioonBlueDark)
            }
        }
        IconButton(onClick = onNotifications) {
            BadgedBox(badge = { if (dueCount > 0) Badge { Text(dueCount.toString()) } }) {
                Icon(Icons.Outlined.NotificationsNone, contentDescription = "التنبيهات", tint = DioonBlueDark)
            }
        }
    }
}

@Composable
private fun LedgerTabs(selectedType: PartyType, onSelected: (PartyType) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            listOf(
                PartyType.CUSTOMER to "العملاء",
                PartyType.SUPPLIER to "الموردون",
            ).forEach { (type, title) ->
                val selected = selectedType == type
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) DioonBlue else Color.Transparent)
                        .clickable { onSelected(type) }
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
private fun OverviewCard(
    receivableCents: Long,
    payableCents: Long,
    balancesVisible: Boolean,
    onToggleVisibility: () -> Unit,
) {
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
                    Text("محفوظ محلياً على جهازك", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        if (balancesVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (balancesVisible) "إخفاء الأرصدة" else "إظهار الأرصدة",
                        tint = TextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewMetric(
                    modifier = Modifier.weight(1f),
                    label = "لك عند الآخرين",
                    value = if (balancesVisible) formatMoney(receivableCents) else "••••",
                    valueColor = SuccessGreen,
                    background = SuccessGreenSoft,
                )
                OverviewMetric(
                    modifier = Modifier.weight(1f),
                    label = "عليك للآخرين",
                    value = if (balancesVisible) formatMoney(payableCents) else "••••",
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
                    Text(
                        if (balancesVisible) formatMoney(receivableCents - payableCents, includeSign = true) else "••••",
                        color = DioonBlue,
                        fontWeight = FontWeight.Bold,
                    )
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
private fun PartyCard(party: Party, balancesVisible: Boolean, onClick: () -> Unit) {
    val accent = if (party.balanceCents >= 0) SuccessGreen else DebtRed
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                Text(initials(party.name), color = DioonBlueDark, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = party.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    party.lastActivityAt?.let { "آخر حركة ${formatDateTime(it)}" } ?: "لا توجد حركات بعد",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (balancesVisible) formatMoney(party.balanceCents) else "••••",
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                )
                Text(
                    text = when {
                        party.balanceCents > 0 -> "عليه لك"
                        party.balanceCents < 0 -> "له عليك"
                        else -> "متوازن"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun EmptyPartiesCard(type: PartyType, onAdd: () -> Unit) {
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
            Icon(Icons.Outlined.PeopleOutline, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                if (type == PartyType.CUSTOMER) "لا يوجد عملاء بعد" else "لا يوجد موردون بعد",
                fontWeight = FontWeight.Bold,
            )
            Text("ابدأ بإضافة أول حساب ثم سجّل الحركات", color = TextSecondary)
            Spacer(Modifier.height(14.dp))
            Button(onClick = onAdd) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(if (type == PartyType.CUSTOMER) "إضافة عميل" else "إضافة مورد")
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AddPartyDialog(
    type: PartyType,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Boolean,
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    val nameFocusRequester = remember { FocusRequester() }
    val phoneFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        nameFocusRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == PartyType.CUSTOMER) "إضافة عميل" else "إضافة مورد") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocusRequester),
                    label = { Text("الاسم") },
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError) ({ Text("الاسم مطلوب") }) else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { phoneFocusRequester.requestFocus() },
                    ),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { character -> character.isDigit() || character == '+' || character == ' ' } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(phoneFocusRequester),
                    label = { Text("رقم الهاتف - اختياري") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            nameError = name.isBlank()
                            if (!nameError) {
                                keyboardController?.hide()
                                onSave(name, phone)
                            }
                        },
                    ),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    nameError = name.isBlank()
                    if (!nameError) {
                        keyboardController?.hide()
                        onSave(name, phone)
                    }
                },
            ) { Text("حفظ") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}

private fun initials(name: String): String = name
    .trim()
    .split(Regex("\\s+"))
    .filter { it.isNotBlank() }
    .take(2)
    .joinToString("") { it.take(1) }
    .ifBlank { "ح" }


@Composable
private fun DueSummaryCard(overdue: Int, today: Int, upcoming: Int, onClick: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Text("الاستحقاقات والتذكيرات", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DueMetric(Modifier.weight(1f), "متأخر", overdue, DebtRedSoft, DebtRed)
                DueMetric(Modifier.weight(1f), "اليوم", today, DioonBlueSoft, DioonBlue)
                DueMetric(Modifier.weight(1f), "قادم", upcoming, SuccessGreenSoft, SuccessGreen)
            }
        }
    }
}

@Composable
private fun DueMetric(modifier: Modifier, label: String, count: Int, bg: Color, color: Color) {
    Surface(modifier, color = bg, shape = RoundedCornerShape(12.dp)) { Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(count.toString(), color = color, fontWeight = FontWeight.Bold); Text(label, color = color) } }
}

@Composable
private fun DueItemsDialog(items: List<DueItem>, onDismiss: () -> Unit, onOpen: (DueItem) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("الاستحقاقات") }, text = {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.entryId }) { item ->
                Surface(Modifier.fillMaxWidth().clickable { onOpen(item) }, color = Color.White, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.partyName, fontWeight = FontWeight.Bold)
                        Text("المتبقي ${formatMoney(item.remainingCents)}", color = DebtRed)
                        Text("الاستحقاق ${SimpleDateFormat("d MMM yyyy", Locale("ar")).format(Date(item.dueAt))}", color = TextSecondary)
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onDismiss) { Text("إغلاق") } })
}
