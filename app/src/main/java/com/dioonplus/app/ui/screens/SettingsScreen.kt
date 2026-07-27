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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dioonplus.app.DioonAppState
import com.dioonplus.app.security.AppPreferences
import com.dioonplus.app.ui.theme.BorderColor
import com.dioonplus.app.ui.theme.DebtRed
import com.dioonplus.app.ui.theme.DioonBlue
import com.dioonplus.app.ui.theme.DioonBlueDark
import com.dioonplus.app.ui.theme.TextSecondary
import com.dioonplus.app.util.fileSafeDate

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    appState: DioonAppState,
    preferences: AppPreferences,
    onLockNow: () -> Unit,
) {
    val context = LocalContext.current
    var showPinDialog by remember { mutableStateOf(false) }
    var showRemovePinDialog by remember { mutableStateOf(false) }
    var pendingBackupJson by remember { mutableStateOf<String?>(null) }
    var pinRevision by remember { mutableStateOf(0) }
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
            Text("حماية ونسخ احتياطي دون تسجيل برقم هاتف", color = TextSecondary)
        }
        item {
            SettingsRow(
                icon = if (hasPin) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                title = "رمز PIN",
                subtitle = if (hasPin) "مفعّل لحماية التطبيق" else "غير مفعّل — أضف رمزاً من 4 إلى 6 أرقام",
                onClick = { showPinDialog = true },
            )
        }
        if (hasPin) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onLockNow,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("قفل الآن") }
                    OutlinedButton(
                        onClick = { showRemovePinDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("إزالة PIN", color = DebtRed) }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Backup, contentDescription = null, tint = DioonBlueDark)
                        Spacer(Modifier.padding(6.dp))
                        Column {
                            Text("النسخ الاحتياطي", style = MaterialTheme.typography.titleMedium)
                            Text("ملف محلي مشترك بين أجهزتك ويحفظ الحسابات والحركات", color = TextSecondary)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                exportLauncher.launch("DioonPlus-backup-${fileSafeDate()}.json")
                            },
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
                    Text("الخصوصية", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "البيانات محفوظة داخل جهازك. لا يطلب التطبيق رقم هاتف ولا يرسل حساباتك إلى خادم خارجي.",
                        color = TextSecondary,
                    )
                }
            }
        }
    }

    if (showPinDialog) {
        SetPinDialog(
            hasExistingPin = hasPin,
            preferences = preferences,
            onDismiss = { showPinDialog = false },
            onSaved = {
                pinRevision++
                showPinDialog = false
                Toast.makeText(context, "تم حفظ رمز PIN", Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (showRemovePinDialog) {
        RemovePinDialog(
            preferences = preferences,
            onDismiss = { showRemovePinDialog = false },
            onRemoved = {
                pinRevision++
                showRemovePinDialog = false
                Toast.makeText(context, "تمت إزالة رمز PIN", Toast.LENGTH_SHORT).show()
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
            dismissButton = {
                TextButton(onClick = { pendingBackupJson = null }) { Text("إلغاء") }
            },
        )
    }
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
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
    }
}

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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasExistingPin) "تغيير رمز PIN" else "إنشاء رمز PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasExistingPin) {
                    PinField("رمز PIN الحالي", currentPin) { currentPin = it; error = null }
                }
                PinField("رمز PIN الجديد", newPin) { newPin = it; error = null }
                PinField("تأكيد الرمز", confirmation) { confirmation = it; error = null }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    error = when {
                        hasExistingPin && !preferences.verifyPin(currentPin) -> "رمز PIN الحالي غير صحيح"
                        !newPin.matches(Regex("\\d{4,6}")) -> "الرمز الجديد يجب أن يتكون من 4 إلى 6 أرقام"
                        newPin != confirmation -> "الرمزان غير متطابقين"
                        else -> null
                    }
                    if (error == null) {
                        preferences.setPin(newPin)
                        onSaved()
                    }
                },
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun RemovePinDialog(
    preferences: AppPreferences,
    onDismiss: () -> Unit,
    onRemoved: () -> Unit,
) {
    var currentPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إزالة رمز PIN") },
        text = {
            Column {
                Text("أدخل الرمز الحالي لتأكيد إزالة حماية الدخول.")
                Spacer(Modifier.height(10.dp))
                PinField("رمز PIN الحالي", currentPin) { currentPin = it; error = false }
                if (error) Text("رمز PIN غير صحيح", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (preferences.verifyPin(currentPin)) {
                        preferences.clearPin()
                        onRemoved()
                    } else {
                        error = true
                    }
                },
            ) { Text("إزالة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun PinField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(6)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
}
