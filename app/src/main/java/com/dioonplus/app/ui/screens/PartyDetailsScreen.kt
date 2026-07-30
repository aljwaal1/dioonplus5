package com.dioonplus.app.ui.screens

import android.app.DatePickerDialog
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dioonplus.app.DioonAppState
import com.dioonplus.app.data.*
import com.dioonplus.app.security.AppPreferences
import com.dioonplus.app.ui.theme.*
import com.dioonplus.app.util.CurrencySettings
import com.dioonplus.app.util.formatDateTime
import com.dioonplus.app.util.formatMoney
import com.dioonplus.app.util.parseMoneyToCents
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PartyDetailsScreen(appState: DioonAppState, party: Party, preferences: AppPreferences, onBack: () -> Unit) {
    var addEntryType by remember { mutableStateOf<EntryType?>(null) }
    var editingEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var paymentEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var fullSettlementEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var pendingDeleteEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var showEditParty by remember { mutableStateOf(false) }
    var showDeleteParty by remember { mutableStateOf(false) }
    val tone = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 45) }
    val haptic = LocalHapticFeedback.current
    DisposableEffect(Unit) { onDispose { tone.release() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(Modifier.statusBarsPadding(), color = Color.White, shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "رجوع") }
                    Column(Modifier.weight(1f)) {
                        Text(party.name, style = MaterialTheme.typography.titleLarge)
                        Text((if (party.type == PartyType.CUSTOMER) "عميل" else "مورد") + if (party.phone.isBlank()) "" else " • ${party.phone}", color = TextSecondary)
                    }
                    IconButton(onClick = { showEditParty = true }) { Icon(Icons.Outlined.Edit, "تعديل", tint = DioonBlue) }
                    IconButton(onClick = { showDeleteParty = true }) { Icon(Icons.Outlined.DeleteOutline, "حذف", tint = DebtRed) }
                }
            }
        },
        bottomBar = {
            Surface(Modifier.navigationBarsPadding(), color = Color.White, shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button({ addEntryType = EntryType.GAVE }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen), shape = RoundedCornerShape(16.dp)) { Text("أعطيت") }
                    Button({ addEntryType = EntryType.TOOK }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = DebtRed), shape = RoundedCornerShape(16.dp)) { Text("أخذت") }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { BalanceCard(party) }
            val openDebts = appState.entries.count { !it.isPayment && it.remainingCents > 0 }
            if (openDebts > 0) item { Text("الديون المفتوحة: $openDebts", color = DioonBlueDark, fontWeight = FontWeight.SemiBold) }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("سجل الحركات", style = MaterialTheme.typography.titleLarge); Text("${appState.entries.size} حركة", color = TextSecondary) } }
            if (appState.entries.isEmpty()) item { EmptyEntriesCard() }
            else items(appState.entries, key = { it.id }) { entry ->
                EntryCard(entry, onEdit = { if (!entry.isPayment) editingEntry = entry }, onDelete = { pendingDeleteEntry = entry }, onPayment = { paymentEntry = entry }, onSettle = { fullSettlementEntry = entry })
            }
        }
    }

    addEntryType?.let { type -> EntryEditorDialog(type, null, { addEntryType = null }) { selectedType, amount, note, createdAt, dueAt ->
        val cents = parseMoneyToCents(amount) ?: return@EntryEditorDialog false
        appState.addEntry(selectedType, cents, note, createdAt, dueAt).also { if (it) { if (preferences.soundEnabled) tone.startTone(ToneGenerator.TONE_PROP_ACK, 120); if (preferences.vibrationEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); addEntryType = null } }
    } }

    editingEntry?.let { entry -> EntryEditorDialog(entry.type, entry, { editingEntry = null }) { selectedType, amount, note, createdAt, dueAt ->
        val cents = parseMoneyToCents(amount) ?: return@EntryEditorDialog false
        appState.updateEntry(entry.id, selectedType, cents, note, createdAt, dueAt).also { if (it) editingEntry = null }
    } }

    paymentEntry?.let { entry -> PaymentDialog(entry, { paymentEntry = null }) { amount, note, date ->
        val cents = parseMoneyToCents(amount) ?: return@PaymentDialog false
        appState.addPayment(entry.id, cents, note, date).also { if (it) paymentEntry = null }
    } }

    fullSettlementEntry?.let { entry -> AlertDialog(
        onDismissRequest = { fullSettlementEntry = null },
        title = { Text("تسجيل سداد كامل؟") },
        text = { Text("سيتم تسجيل دفعة بقيمة ${formatMoney(entry.remainingCents)} وإغلاق هذا الدين مع الاحتفاظ بسجله.") },
        confirmButton = { Button({ if (appState.settleInFull(entry.id)) fullSettlementEntry = null }) { Text("تأكيد السداد") } },
        dismissButton = { TextButton({ fullSettlementEntry = null }) { Text("إلغاء") } },
    ) }

    if (showEditParty) EditPartyDialog(party, { showEditParty = false }) { name, phone -> appState.updateParty(party.id, name, phone).also { if (it) showEditParty = false } }
    if (showDeleteParty) AlertDialog(onDismissRequest = { showDeleteParty = false }, title = { Text("حذف الحساب؟") }, text = { Text("سيتم حذف الحساب وجميع حركاته ودفعاته نهائياً.") }, confirmButton = { Button({ if (appState.deleteParty(party.id)) showDeleteParty = false }, colors = ButtonDefaults.buttonColors(containerColor = DebtRed)) { Text("حذف نهائياً") } }, dismissButton = { TextButton({ showDeleteParty = false }) { Text("إلغاء") } })
    pendingDeleteEntry?.let { entry -> AlertDialog(onDismissRequest = { pendingDeleteEntry = null }, title = { Text("حذف الحركة؟") }, text = { Text(if (entry.isPayment) "سيتم حذف دفعة السداد وإعادة المبلغ إلى المتبقي." else "سيتم حذف الدين وجميع الدفعات المرتبطة به.") }, confirmButton = { Button({ if (appState.deleteEntry(entry.id)) pendingDeleteEntry = null }, colors = ButtonDefaults.buttonColors(containerColor = DebtRed)) { Text("حذف") } }, dismissButton = { TextButton({ pendingDeleteEntry = null }) { Text("إلغاء") } }) }
}

@Composable private fun BalanceCard(party: Party) {
    val positive = party.balanceCents >= 0; val accent = if (positive) SuccessGreen else DebtRed; val soft = if (positive) SuccessGreenSoft else DebtRedSoft
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp)) { Text("الرصيد الحالي", color = TextSecondary); Text(formatMoney(party.balanceCents), style = MaterialTheme.typography.headlineMedium, color = accent, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(8.dp)); Surface(color = soft, shape = RoundedCornerShape(12.dp)) { Text(when { party.balanceCents > 0 -> "عليه لك"; party.balanceCents < 0 -> "له عليك"; else -> "الحساب متوازن" }, Modifier.padding(12.dp), color = accent) } }
    }
}

@Composable private fun EmptyEntriesCard() { Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.ReceiptLong, null, tint = TextSecondary); Text("لا توجد حركات بعد") } } }

@Composable private fun EntryCard(entry: LedgerEntry, onEdit: () -> Unit, onDelete: () -> Unit, onPayment: () -> Unit, onSettle: () -> Unit) {
    val gave = entry.type == EntryType.GAVE; val accent = if (gave) SuccessGreen else DebtRed
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (entry.isPayment) "دفعة سداد" else if (gave) "أعطيت" else "أخذت", fontWeight = FontWeight.Bold, color = accent)
                    Text(formatDateTime(entry.createdAt), color = TextSecondary)
                    if (entry.note.isNotBlank()) Text(entry.note, color = DioonBlueDark)
                }
                Text(formatMoney(entry.amountCents), color = accent, fontWeight = FontWeight.Bold)
            }
            if (!entry.isPayment) {
                val statusText = when (entry.debtStatus) { DebtStatus.OPEN -> "غير مسدد"; DebtStatus.PARTIAL -> "مسدد جزئياً"; DebtStatus.PAID -> "مسدد بالكامل" }
                Surface(color = when (entry.debtStatus) { DebtStatus.PAID -> SuccessGreenSoft; DebtStatus.PARTIAL -> DioonBlueSoft; else -> DebtRedSoft }, shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(statusText, fontWeight = FontWeight.SemiBold)
                        Text("الأصل ${formatMoney(entry.amountCents)} • المدفوع ${formatMoney(entry.paidCents)} • المتبقي ${formatMoney(entry.remainingCents)}", style = MaterialTheme.typography.bodySmall)
                        entry.dueAt?.let { Text("الاستحقاق: ${formatDate(it)}", style = MaterialTheme.typography.bodySmall, color = dueColor(it)) }
                    }
                }
                if (entry.remainingCents > 0) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onPayment, Modifier.weight(1f)) { Icon(Icons.Outlined.Payments, null); Spacer(Modifier.size(5.dp)); Text("تسجيل دفعة") }
                    Button(onSettle, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)) { Text("سداد كامل") }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!entry.isPayment) IconButton(onEdit) { Icon(Icons.Outlined.Edit, "تعديل", tint = DioonBlue) }
                IconButton(onDelete) { Icon(Icons.Outlined.DeleteOutline, "حذف", tint = TextSecondary) }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable private fun EntryEditorDialog(initialType: EntryType, existingEntry: LedgerEntry?, onDismiss: () -> Unit, onSave: (EntryType, String, String, Long, Long?) -> Boolean) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(existingEntry?.type ?: initialType) }; var amount by remember { mutableStateOf(existingEntry?.amountCents?.let(::centsToInput).orEmpty()) }; var note by remember { mutableStateOf(existingEntry?.note.orEmpty()) }
    var date by remember { mutableStateOf(existingEntry?.createdAt ?: System.currentTimeMillis()) }; var dueAt by remember { mutableStateOf(existingEntry?.dueAt) }; var error by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }; val keyboard = LocalSoftwareKeyboardController.current
    fun save() { error = parseMoneyToCents(amount) == null; if (!error && onSave(type, amount, note, date, dueAt)) keyboard?.hide() }
    fun pick(current: Long, maxToday: Boolean, update: (Long) -> Unit) { keyboard?.hide(); val c = Calendar.getInstance().apply { timeInMillis = current }; DatePickerDialog(context, { _, y, m, d -> update(Calendar.getInstance().apply { set(y,m,d,9,0,0); set(Calendar.MILLISECOND,0) }.timeInMillis) }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).apply { if (maxToday) datePicker.maxDate = System.currentTimeMillis() }.show() }
    LaunchedEffect(Unit) { requester.requestFocus(); keyboard?.show() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existingEntry == null) "إضافة حركة مالية" else "تعديل الحركة") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (existingEntry?.paidCents?.let { it > 0L } == true) {
                Text(if (type == EntryType.GAVE) "نوع الحركة: أعطيت" else "نوع الحركة: أخذت", color = TextSecondary)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { EntryTypeChoice(Modifier.weight(1f), type == EntryType.GAVE, "أعطيت", SuccessGreen) { type = EntryType.GAVE }; EntryTypeChoice(Modifier.weight(1f), type == EntryType.TOOK, "أخذت", DebtRed) { type = EntryType.TOOK } }
            }
            OutlinedTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch in listOf('.',',','٫') }; error = false }, Modifier.fillMaxWidth().focusRequester(requester), label = { Text("المبلغ") }, suffix = { Text(CurrencySettings.current.symbol) }, isError = error, supportingText = { if (error) Text("أدخل مبلغاً صحيحاً") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next))
            OutlinedButton({ pick(date, true) { date = it } }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.CalendarMonth, null); Text(" تاريخ الحركة: ${formatDate(date)}") }
            OutlinedButton({ pick(dueAt ?: System.currentTimeMillis(), false) { dueAt = it } }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Event, null); Text(if (dueAt == null) " إضافة تاريخ استحقاق" else " الاستحقاق: ${formatDate(dueAt!!)}") }
            if (dueAt != null) TextButton({ dueAt = null }) { Text("إزالة تاريخ الاستحقاق") }
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظة اختيارية") }, minLines = 2, keyboardActions = KeyboardActions(onDone = { save() }))
        }
    }, confirmButton = { Button({ save() }) { Text("حفظ") } }, dismissButton = { TextButton(onDismiss) { Text("إلغاء") } })
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable private fun PaymentDialog(entry: LedgerEntry, onDismiss: () -> Unit, onSave: (String, String, Long) -> Boolean) {
    var amount by remember { mutableStateOf("") }; var note by remember { mutableStateOf("دفعة سداد") }; var date by remember { mutableStateOf(System.currentTimeMillis()) }; var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current; val requester = remember { FocusRequester() }; val keyboard = LocalSoftwareKeyboardController.current
    fun save() { val cents = parseMoneyToCents(amount); error = when { cents == null -> "أدخل مبلغاً صحيحاً"; cents > entry.remainingCents -> "الدفعة أكبر من المتبقي"; else -> null }; if (error == null && onSave(amount, note, date)) keyboard?.hide() }
    LaunchedEffect(Unit) { requester.requestFocus(); keyboard?.show() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تسجيل دفعة") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("المتبقي: ${formatMoney(entry.remainingCents)}", fontWeight = FontWeight.Bold)
        OutlinedTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch in listOf('.',',','٫') }; error = null }, Modifier.fillMaxWidth().focusRequester(requester), label = { Text("قيمة الدفعة") }, suffix = { Text(CurrencySettings.current.symbol) }, isError = error != null, supportingText = { error?.let { Text(it) } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedButton({ val c = Calendar.getInstance().apply { timeInMillis = date }; DatePickerDialog(context, { _,y,m,d -> date = Calendar.getInstance().apply { set(y,m,d) }.timeInMillis }, c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show() }, Modifier.fillMaxWidth()) { Text("تاريخ الدفعة: ${formatDate(date)}") }
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظة") })
        TextButton({ amount = centsToInput(entry.remainingCents) }) { Text("استخدام كامل المبلغ المتبقي") }
    } }, confirmButton = { Button({ save() }) { Text("حفظ الدفعة") } }, dismissButton = { TextButton(onDismiss) { Text("إلغاء") } })
}

@Composable private fun EntryTypeChoice(modifier: Modifier, selected: Boolean, title: String, color: Color, onClick: () -> Unit) { if (selected) Button(onClick, modifier, colors = ButtonDefaults.buttonColors(containerColor = color)) { Text(title) } else OutlinedButton(onClick, modifier) { Text(title) } }

@OptIn(ExperimentalComposeUiApi::class)
@Composable private fun EditPartyDialog(party: Party, onDismiss: () -> Unit, onSave: (String,String)->Boolean) {
    var name by remember { mutableStateOf(party.name) }; var phone by remember { mutableStateOf(party.phone) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تعديل بيانات الحساب") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),label={Text("الاسم")}); OutlinedTextField(phone,{phone=it},Modifier.fillMaxWidth(),label={Text("رقم الهاتف")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone)) } }, confirmButton = { Button({ if(name.isNotBlank()) onSave(name,phone) }) { Text("حفظ") } }, dismissButton = { TextButton(onDismiss) { Text("إلغاء") } })
}

private fun centsToInput(cents: Long) = BigDecimal.valueOf(cents,3).stripTrailingZeros().toPlainString()
private fun formatDate(timestamp: Long) = SimpleDateFormat("d MMMM yyyy", Locale("ar")).format(Date(timestamp))
private fun dueColor(timestamp: Long): Color { val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.timeInMillis; return if (timestamp < today) DebtRed else DioonBlueDark }
