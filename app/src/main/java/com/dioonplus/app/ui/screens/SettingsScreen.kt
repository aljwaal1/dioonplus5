package com.dioonplus.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dioonplus.app.DioonAppState
import com.dioonplus.app.security.AppPreferences
import com.dioonplus.app.ui.theme.BorderColor
import com.dioonplus.app.ui.theme.DebtRed
import com.dioonplus.app.ui.theme.DioonBlueDark
import com.dioonplus.app.ui.theme.TextSecondary
import com.dioonplus.app.util.CurrencyOption
import com.dioonplus.app.util.CurrencySettings
import com.dioonplus.app.util.fileSafeDate

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    appState: DioonAppState,
    preferences: AppPreferences,
    onEntryLockChanged: (Boolean) -> Unit,
    onLockNow: () -> Unit,
) {
    val context = LocalContext.current
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var selectedCurrency by remember { mutableStateOf(preferences.currency) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showRemovePinDialog by remember { mutableStateOf(false) }
    var pendingBackupJson by remember { mutableStateOf<String?>(null) }
    var pinRevision by remember { mutableStateOf(0) }
    var entryLockEnabled by remember {
        mutableStateOf(preferences.entryLockEnabled && preferences.hasPin())
    }
    var enableLockAfterPinSave by remember { mutableStateOf(false) }
    var soundEnabled by remember { mutableStateOf(preferences.soundEnabled) }
    var vibrationEnabled by remember { mutableStateOf(preferences.vibrationEnabled) }
    val hasPin = remember(pinRevision) { preferences.hasPin() }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val backup = appState.exportBackup()
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(backup) }
                    ?: error("تعذر فتح الملف")
            }.onSuccess {
                Toast.makeText(context, "تم حفظ النسخة الاحتياطية", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "تعذر حفظ النسخة الاحتياطية", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("تعذر قراءة الملف")
            }.onSuccess { pendingBackupJson = it }
                .onFailure { Toast.makeText(context, "تعذر قراءة النسخة الاحتياطية", Toast.LENGTH_LONG).show() }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("الإعدادات", style = MaterialTheme.typography.headlineMedium, color = DioonBlueDark)
            Text("العملة والحماية والنسخ الاحتياطي", color = TextSecondary)
        }
        item {
            SettingsRow(
                icon = Icons.Outlined.Payments,
                title = "العملة الافتراضية",
                subtitle = selectedCurrency.displayName + " — " + selectedCurrency.symbol,
                onClick = { showCurrencyDialog = true },
            )
        }
        item {
            ToggleCard(
                title = "طلب رمز الدخول عند فتح التطبيق",
                subtitle = if (entryLockEnabled) {
                    "مفعّل — سيُطلب رمز PIN عند كل تشغيل جديد"
                } else {
                    "متوقف — يفتح التطبيق مباشرة دون كلمة مرور"
                },
                checked = entryLockEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        if (hasPin) {
                            entryLockEnabled = true
                            preferences.entryLockEnabled = true
                            onEntryLockChanged(true)
                        } else {
                            enableLockAfterPinSave = true
                            showPinDialog = true
                        }
                    } else {
                        enableLockAfterPinSave = false
                        entryLockEnabled = false
                        preferences.entryLockEnabled = false
                        onEntryLockChanged(false)
                    }
                },
            )
        }
        item {
            SettingsRow(
                icon = if (hasPin) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                title = "رمز الدخول (PIN)",
                subtitle = when {
                    !hasPin -> "غير مُنشأ — أنشئ رمزاً من 4 إلى 6 أرقام"
                    entryLockEnabled -> "محفوظ ومفعّل عند فتح التطبيق"
                    else -> "محفوظ، لكن طلبه عند الدخول متوقف"
                },
                onClick = { showPinDialog = true },
            )
        }
        if (hasPin) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onLockNow,
                        enabled = entryLockEnabled,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("قفل الآن") }
                    OutlinedButton(
                        onClick = { showRemovePinDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("إزالة الرمز", color = DebtRed) }
                }
            }
        }
        item {
            ToggleCard(
                title = "أصوات التطبيق",
                subtitle = "صوت خفيف عند نجاح الحفظ",
                checked = soundEnabled,
                onCheckedChange = {
                    soundEnabled = it
                    preferences.soundEnabled = it
                },
            )
        }
        item {
            ToggleCard(
                title = "الاهتزاز",
                subtitle = "اهتزاز خفيف لتأكيد العمليات",
                checked = vibrationEnabled,
                onCheckedChange = {
                    vibrationEnabled = it
                    preferences.vibrationEnabled = it
                },
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Backup, contentDescription = null, tint = DioonBlueDark)
                        Spacer(Modifier.padding(6.dp))
                        Column {
                            Text("النسخ الاحتياطي", style = MaterialTheme.typography.titleMedium)
                            Text("يحفظ الحسابات والحركات في ملف محلي", color = TextSecondary)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { exportLauncher.launch("DioonPlus-backup-${fileSafeDate()}.json") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null)
                            Spacer(Modifier.padding(3.dp))
                            Text("إنشاء نسخة")
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Outlined.Upload, contentDescription = null)
                            Spacer(Modifier.padding(3.dp))
                            Text("استعادة")
                        }
                    }
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ملاحظة العملة", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "تغيير العملة يغيّر رمز العرض في الحسابات والتقارير وPDF فقط، ولا يحوّل المبالغ المسجلة ولا يغيّر قيمتها.",
                        color = TextSecondary,
                    )
                }
            }
        }
    }

    if (showCurrencyDialog) {
        CurrencyDialog(
            selected = selectedCurrency,
            onDismiss = { showCurrencyDialog = false },
            onSelected = { currency ->
                selectedCurrency = currency
                preferences.currency = currency
                CurrencySettings.current = currency
                showCurrencyDialog = false
                Toast.makeText(context, "تم اعتماد ${currency.arabicName}", Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (showPinDialog) {
        SetPinDialog(
            hasExistingPin = hasPin,
            preferences = preferences,
            onDismiss = { showPinDialog = false },
            onSaved = {
                val shouldEnable = enableLockAfterPinSave || !hasPin || entryLockEnabled
                preferences.entryLockEnabled = shouldEnable
                entryLockEnabled = shouldEnable
                onEntryLockChanged(shouldEnable)
                enableLockAfterPinSave = false
                pinRevision++
                showPinDialog = false
                Toast.makeText(
                    context,
                    if (shouldEnable) "تم حفظ رمز الدخول وتفعيل الحماية" else "تم تحديث رمز الدخول",
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
    }

    if (showRemovePinDialog) {
        RemovePinDialog(
            preferences = preferences,
            onDismiss = { showRemovePinDialog = false },
            onRemoved = {
                entryLockEnabled = false
                enableLockAfterPinSave = false
                onEntryLockChanged(false)
                pinRevision++
                showRemovePinDialog = false
                Toast.makeText(context, "تمت إزالة رمز الدخول وإيقاف الحماية", Toast.LENGTH_SHORT).show()
            },
        )
    }

    pendingBackupJson?.let { json ->
        AlertDialog(
            onDismissRequest = { pendingBackupJson = null },
            title = { Text("استعادة النسخة الاحتياطية؟") },
            text = { Text("ستستبدل النسخة الحالية من الحسابات والحركات. لا يمكن التراجع بعد التأكيد.") },
            confirmButton = {
                Button(
                    onClick = {
                        val imported = appState.importBackup(json)
                        pendingBackupJson = null
                        Toast.makeText(
                            context,
                            if (imported) "تمت استعادة البيانات" else "فشلت الاستعادة",
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                ) { Text("استعادة") }
            },
            dismissButton = { TextButton(onClick = { pendingBackupJson = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun CurrencyDialog(
    selected: CurrencyOption,
    onDismiss: () -> Unit,
    onSelected: (CurrencyOption) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختر العملة الافتراضية") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(CurrencyOption.entries, key = { it.code }) { currency ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(currency) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = currency == selected,
                            onClick = { onSelected(currency) },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(currency.arabicName, style = MaterialTheme.typography.titleMedium)
                            Text(currency.code, color = TextSecondary)
                        }
                        Text(currency.symbol, style = MaterialTheme.typography.titleLarge, color = DioonBlueDark)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = DioonBlueDark)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun ToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = TextSecondary)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SetPinDialog(
    hasExistingPin: Boolean,
    preferences: AppPreferences,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val currentRequester = remember { FocusRequester() }
    val newRequester = remember { FocusRequester() }
    val confirmRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun save() {
        error = when {
            hasExistingPin && !preferences.verifyPin(currentPin) -> "رمز PIN الحالي غير صحيح"
            !newPin.matches(Regex("\\d{4,6}")) -> "الرمز الجديد يجب أن يتكون من 4 إلى 6 أرقام"
            newPin != confirmation -> "الرمزان غير متطابقين"
            else -> null
        }
        if (error == null) {
            keyboardController?.hide()
            preferences.setPin(newPin)
            onSaved()
        }
    }

    LaunchedEffect(Unit) {
        if (hasExistingPin) currentRequester.requestFocus() else newRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasExistingPin) "تغيير رمز PIN" else "إنشاء رمز PIN") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (hasExistingPin) {
                    PinField(
                        label = "رمز PIN الحالي",
                        value = currentPin,
                        modifier = Modifier.focusRequester(currentRequester),
                        imeAction = ImeAction.Next,
                        onNext = { newRequester.requestFocus() },
                        onDone = ::save,
                        onValueChange = { currentPin = it; error = null },
                    )
                }
                PinField(
                    label = "رمز PIN الجديد",
                    value = newPin,
                    modifier = Modifier.focusRequester(newRequester),
                    imeAction = ImeAction.Next,
                    onNext = { confirmRequester.requestFocus() },
                    onDone = ::save,
                    onValueChange = { newPin = it; error = null },
                )
                PinField(
                    label = "تأكيد الرمز",
                    value = confirmation,
                    modifier = Modifier.focusRequester(confirmRequester),
                    imeAction = ImeAction.Done,
                    onNext = {},
                    onDone = ::save,
                    onValueChange = { confirmation = it; error = null },
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = ::save) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RemovePinDialog(
    preferences: AppPreferences,
    onDismiss: () -> Unit,
    onRemoved: () -> Unit,
) {
    var currentPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun remove() {
        if (preferences.verifyPin(currentPin)) {
            keyboardController?.hide()
            preferences.clearPin()
            onRemoved()
        } else {
            error = true
        }
    }

    LaunchedEffect(Unit) {
        requester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إزالة رمز PIN") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
            ) {
                Text("أدخل الرمز الحالي لتأكيد إزالة حماية الدخول.")
                Spacer(Modifier.height(10.dp))
                PinField(
                    label = "رمز PIN الحالي",
                    value = currentPin,
                    modifier = Modifier.focusRequester(requester),
                    imeAction = ImeAction.Done,
                    onNext = {},
                    onDone = ::remove,
                    onValueChange = { currentPin = it; error = false },
                )
                if (error) Text("رمز PIN غير صحيح", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { Button(onClick = ::remove) { Text("إزالة") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun PinField(
    label: String,
    value: String,
    modifier: Modifier,
    imeAction: ImeAction,
    onNext: () -> Unit,
    onDone: () -> Unit,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(6)) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onDone() }),
    )
}
