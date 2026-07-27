package com.dioonplus.app.ui.screens

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
import androidx.compose.material.icons.outlined.DeleteOutline
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
import com.dioonplus.app.ui.theme.BorderColor
import com.dioonplus.app.ui.theme.DebtRed
import com.dioonplus.app.ui.theme.DebtRedSoft
import com.dioonplus.app.ui.theme.DioonBlueDark
import com.dioonplus.app.ui.theme.SuccessGreen
import com.dioonplus.app.ui.theme.SuccessGreenSoft
import com.dioonplus.app.ui.theme.TextSecondary
import com.dioonplus.app.util.formatDateTime
import com.dioonplus.app.util.formatMoney
import com.dioonplus.app.util.parseMoneyToCents

@Composable
fun PartyDetailsScreen(
    appState: DioonAppState,
    party: Party,
    onBack: () -> Unit,
) {
    var entryType by remember { mutableStateOf<EntryType?>(null) }
    var pendingDelete by remember { mutableStateOf<LedgerEntry?>(null) }
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
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "رجوع")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(party.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (party.phone.isBlank()) "حساب بدون رقم هاتف" else party.phone,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                    if (party.phone.isNotBlank()) {
                        Icon(Icons.Outlined.Phone, contentDescription = null, tint = DioonBlueDark)
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
                        onClick = { entryType = EntryType.GAVE },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("أعطيت", modifier = Modifier.padding(vertical = 4.dp))
                    }
                    Button(
                        onClick = { entryType = EntryType.TOOK },
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
                    EntryCard(entry = entry, onDelete = { pendingDelete = entry })
                }
            }
        }
    }

    entryType?.let { type ->
        AddEntryDialog(
            type = type,
            onDismiss = { entryType = null },
            onSave = { amount, note ->
                val cents = parseMoneyToCents(amount)
                if (cents == null) {
                    false
                } else {
                    val saved = appState.addEntry(type, cents, note)
                    if (saved) {
                        tone.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        entryType = null
                    }
                    saved
                }
            },
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف الحركة؟") },
            text = { Text("سيتم تحديث الرصيد فوراً بعد حذف هذه الحركة.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        appState.deleteEntry(entry.id)
                        pendingDelete = null
                    },
                ) { Text("حذف", color = DebtRed) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("إلغاء") }
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
private fun EntryCard(entry: LedgerEntry, onDelete: () -> Unit) {
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
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف", tint = TextSecondary)
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AddEntryDialog(
    type: EntryType,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Boolean,
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf(false) }
    val amountFocusRequester = remember { FocusRequester() }
    val noteFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        amountFocusRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == EntryType.GAVE) "إضافة حركة: أعطيت" else "إضافة حركة: أخذت") },
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
                    value = amount,
                    onValueChange = {
                        amount = it
                        amountError = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(amountFocusRequester),
                    label = { Text("المبلغ") },
                    suffix = { Text("د.أ") },
                    singleLine = true,
                    isError = amountError,
                    supportingText = if (amountError) ({ Text("أدخل مبلغاً صحيحاً أكبر من صفر") }) else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { noteFocusRequester.requestFocus() },
                    ),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(noteFocusRequester),
                    label = { Text("ملاحظة اختيارية") },
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            amountError = parseMoneyToCents(amount) == null
                            if (!amountError) {
                                keyboardController?.hide()
                                onSave(amount, note)
                            }
                        },
                    ),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    amountError = parseMoneyToCents(amount) == null
                    if (!amountError) {
                        keyboardController?.hide()
                        onSave(amount, note)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (type == EntryType.GAVE) SuccessGreen else DebtRed,
                ),
            ) { Text("حفظ الحركة") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}
