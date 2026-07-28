package com.dioonplus.app.ui.screens

import android.app.DatePickerDialog
import android.media.AudioManager
import android.media.ToneGenerator
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.dioonplus.app.data.EntryType
import com.dioonplus.app.data.LedgerEntry
import com.dioonplus.app.data.Party
import com.dioonplus.app.data.PartyType
import com.dioonplus.app.ui.theme.BorderColor
import com.dioonplus.app.ui.theme.DebtRed
import com.dioonplus.app.ui.theme.DebtRedSoft
import com.dioonplus.app.ui.theme.DioonBlue
import com.dioonplus.app.ui.theme.DioonBlueDark
import com.dioonplus.app.ui.theme.SuccessGreen
import com.dioonplus.app.ui.theme.SuccessGreenSoft
import com.dioonplus.app.ui.theme.TextSecondary
import com.dioonplus.app.util.formatDateTime
import com.dioonplus.app.util.formatMoney
import com.dioonplus.app.util.parseMoneyToCents
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun PartyDetailsScreen(
    appState: DioonAppState,
    party: Party,
    onBack: () -> Unit,
) {
    var addEntryType by remember { mutableStateOf<EntryType?>(null) }
    var editingEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var pendingDeleteEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var showEditParty by remember { mutableStateOf(false) }
    var showDeleteParty by remember { mutableStateOf(false) }
    val tone = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 45) }
    val haptic = LocalHapticFeedback.current
    DisposableEffect(Unit) { onDispose { tone.release() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                modifier = Modifier.statusBarsPadding(),
                color = Color.White,
                shadowElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "رجوع")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(party.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = buildString {
                                append(if (party.type == PartyType.CUSTOMER) "عميل" else "مورد")
                                if (party.phone.isNotBlank()) append(" • ${party.phone}")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                    if (party.phone.isNotBlank()) {
                        Icon(Icons.Outlined.Phone, contentDescription = null, tint = DioonBlueDark)
                    }
                    IconButton(onClick = { showEditParty = true }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "تعديل الحساب", tint = DioonBlue)
                    }
                    IconButton(onClick = { showDeleteParty = true }) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف الحساب", tint = DebtRed)
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.navigationBarsPadding(),
                color = Color.White,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { addEntryType = EntryType.GAVE },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("أعطيت", modifier = Modifier.padding(vertical = 4.dp))
                    }
                    Button(
                        onClick = { addEntryType = EntryType.TOOK },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DebtRed),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("أخذت", modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { BalanceCard(party) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("سجل الحركات", style = MaterialTheme.typography.titleLarge)
                    Text("${appState.entries.size} حركة", color = TextSecondary)
                }
            }
            if (appState.entries.isEmpty()) {
                item { EmptyEntriesCard() }
            } else {
                items(appState.entries, key = { it.id }) { entry ->
                    EntryCard(
                        entry = entry,
                        onEdit = { editingEntry = entry },
                        onDelete = { pendingDeleteEntry = entry },
                    )
                }
            }
        }
    }

    addEntryType?.let { type ->
        EntryEditorDialog(
            initialType = type,
            existingEntry = null,
            onDismiss = { addEntryType = null },
            onSave = { selectedType, amount, note, createdAt ->
                val cents = parseMoneyToCents(amount)
                if (cents == null) {
                    false
                } else {
                    val saved = appState.addEntry(selectedType, cents, note, createdAt)
                    if (saved) {
                        tone.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        addEntryType = null
                    }
                    saved
                }
            },
        )
    }

    editingEntry?.let { entry ->
        EntryEditorDialog(
            initialType = entry.type,
            existingEntry = entry,
            onDismiss = { editingEntry = null },
            onSave = { selectedType, amount, note, createdAt ->
                val cents = parseMoneyToCents(amount)
                if (cents == null) {
                    false
                } else {
                    val saved = appState.updateEntry(entry.id, selectedType, cents, note, createdAt)
                    if (saved) {
                        tone.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        editingEntry = null
                    }
                    saved
                }
            },
        )
    }

    if (showEditParty) {
        EditPartyDialog(
            party = party,
            onDismiss = { showEditParty = false },
            onSave = { name, phone ->
                val saved = appState.updateParty(party.id, name, phone)
                if (saved) {
                    tone.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                    showEditParty = false
                }
                saved
            },
        )
    }

    if (showDeleteParty) {
        AlertDialog(
            onDismissRequest = { showDeleteParty = false },
            title = { Text("حذف ${if (party.type == PartyType.CUSTOMER) "العميل" else "المورد"}؟") },
            text = {
                Text(
                    if (appState.entries.isEmpty()) {
                        "سيتم حذف الحساب نهائياً."
                    } else {
                        "سيتم حذف الحساب وجميع حركاته وعددها ${appState.entries.size}. لا يمكن التراجع بعد التأكيد."
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (appState.deleteParty(party.id)) showDeleteParty = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DebtRed),
                ) { Text("حذف نهائياً") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteParty = false }) { Text("إلغاء") }
            },
        )
    }

    pendingDeleteEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDeleteEntry = null },
            title = { Text("حذف الحركة؟") },
            text = {
                Text(
                    "سيتم حذف حركة ${if (entry.type == EntryType.GAVE) "أعطيت" else "أخذت"} بقيمة ${formatMoney(entry.amountCents)} وتحديث الرصيد فوراً.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (appState.deleteEntry(entry.id)) pendingDeleteEntry = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DebtRed),
                ) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteEntry = null }) { Text("إلغاء") }
            },
        )
    }
}

@Composable
private fun BalanceCard(party: Party) {
    val positive = party.balanceCents >= 0
    val accent = if (positive) SuccessGreen else DebtRed
    val soft = if (positive) SuccessGreenSoft else DebtRedSoft
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("الرصيد الحالي", color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Text(
                formatMoney(party.balanceCents),
                style = MaterialTheme.typography.headlineMedium,
                color = accent,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(10.dp))
            Surface(color = soft, shape = RoundedCornerShape(12.dp)) {
                Text(
                    text = when {
                        party.balanceCents > 0 -> "عليه لك"
                        party.balanceCents < 0 -> "له عليك"
                        else -> "الحساب متوازن"
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun EmptyEntriesCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = TextSecondary)
            Spacer(Modifier.height(10.dp))
            Text("لا توجد حركات بعد", fontWeight = FontWeight.SemiBold)
            Text("استخدم أزرار أعطيت أو أخذت لإضافة أول حركة", color = TextSecondary)
        }
    }
}

@Composable
private fun EntryCard(
    entry: LedgerEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val gave = entry.type == EntryType.GAVE
    val accent = if (gave) SuccessGreen else DebtRed
    val soft = if (gave) SuccessGreenSoft else DebtRedSoft
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
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
                    .size(46.dp)
                    .background(soft, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (gave) "أع" else "أخ", color = accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(if (gave) "أعطيت" else "أخذت", fontWeight = FontWeight.SemiBold)
                Text(formatDateTime(entry.createdAt), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                if (entry.note.isNotBlank()) {
                    Text(entry.note, style = MaterialTheme.typography.bodyMedium, color = DioonBlueDark)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMoney(entry.amountCents), color = accent, fontWeight = FontWeight.Bold)
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = "تعديل الحركة", tint = DioonBlue)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف الحركة", tint = TextSecondary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EditPartyDialog(
    party: Party,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Boolean,
) {
    var name by remember(party.id) { mutableStateOf(party.name) }
    var phone by remember(party.id) { mutableStateOf(party.phone) }
    var nameError by remember { mutableStateOf(false) }
    val nameRequester = remember { FocusRequester() }
    val phoneRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun save() {
        nameError = name.isBlank()
        if (!nameError && onSave(name, phone)) keyboardController?.hide()
    }

    LaunchedEffect(Unit) {
        nameRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل بيانات الحساب") },
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
                    onValueChange = { name = it; nameError = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameRequester),
                    label = { Text("الاسم") },
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError) ({ Text("الاسم مطلوب") }) else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { phoneRequester.requestFocus() }),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it.filter { character -> character.isDigit() || character == '+' || character == ' ' }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(phoneRequester),
                    label = { Text("رقم الهاتف - اختياري") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                )
            }
        },
        confirmButton = { Button(onClick = { save() }) { Text("حفظ التعديلات") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EntryEditorDialog(
    initialType: EntryType,
    existingEntry: LedgerEntry?,
    onDismiss: () -> Unit,
    onSave: (EntryType, String, String, Long) -> Boolean,
) {
    val context = LocalContext.current
    var selectedType by remember(existingEntry?.id, initialType) { mutableStateOf(existingEntry?.type ?: initialType) }
    var amount by remember(existingEntry?.id) {
        mutableStateOf(existingEntry?.amountCents?.let(::centsToInput).orEmpty())
    }
    var note by remember(existingEntry?.id) { mutableStateOf(existingEntry?.note.orEmpty()) }
    var selectedDate by remember(existingEntry?.id) {
        mutableStateOf(existingEntry?.createdAt ?: System.currentTimeMillis())
    }
    var amountError by remember { mutableStateOf(false) }
    val amountRequester = remember { FocusRequester() }
    val noteRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun save() {
        amountError = parseMoneyToCents(amount) == null
        if (!amountError && onSave(selectedType, amount, note, selectedDate)) keyboardController?.hide()
    }

    fun showDatePicker() {
        keyboardController?.hide()
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedDate }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                selectedDate = Calendar.getInstance().apply {
                    timeInMillis = selectedDate
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }.timeInMillis
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    LaunchedEffect(Unit) {
        amountRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingEntry == null) "إضافة حركة مالية" else "تعديل الحركة المالية") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("نوع الحركة", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EntryTypeChoice(
                        modifier = Modifier.weight(1f),
                        selected = selectedType == EntryType.GAVE,
                        title = "أعطيت",
                        selectedColor = SuccessGreen,
                        onClick = { selectedType = EntryType.GAVE },
                    )
                    EntryTypeChoice(
                        modifier = Modifier.weight(1f),
                        selected = selectedType == EntryType.TOOK,
                        title = "أخذت",
                        selectedColor = DebtRed,
                        onClick = { selectedType = EntryType.TOOK },
                    )
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it.filter { character ->
                            character.isDigit() || character == '.' || character == ',' || character == '٫'
                        }
                        amountError = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(amountRequester),
                    label = { Text("المبلغ") },
                    singleLine = true,
                    isError = amountError,
                    supportingText = if (amountError) ({ Text("أدخل مبلغاً صحيحاً أكبر من صفر") }) else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(onNext = { noteRequester.requestFocus() }),
                )
                OutlinedButton(
                    onClick = { showDatePicker() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("تاريخ الحركة: ${formatEntryDate(selectedDate)}")
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(noteRequester),
                    label = { Text("ملاحظة اختيارية") },
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { save() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedType == EntryType.GAVE) SuccessGreen else DebtRed,
                ),
            ) { Text(if (existingEntry == null) "حفظ الحركة" else "حفظ التعديلات") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun EntryTypeChoice(
    modifier: Modifier,
    selected: Boolean,
    title: String,
    selectedColor: Color,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = selectedColor),
            shape = RoundedCornerShape(14.dp),
        ) { Text(title) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(14.dp),
        ) { Text(title, color = TextSecondary) }
    }
}

private fun centsToInput(cents: Long): String =
    BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString()

private fun formatEntryDate(timestamp: Long): String =
    SimpleDateFormat("d MMMM yyyy", Locale("ar")).format(Date(timestamp))
