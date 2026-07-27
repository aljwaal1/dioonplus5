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


# Currency catalogue used by the whole app and PDF reports.
write(
    "app/src/main/java/com/dioonplus/app/util/CurrencyOption.kt",
    '''package com.dioonplus.app.util

enum class CurrencyOption(
    val code: String,
    val arabicName: String,
    val symbol: String,
) {
    JOD("JOD", "الدينار الأردني", "د.أ"),
    SAR("SAR", "الريال السعودي", "ر.س"),
    YER("YER", "الريال اليمني", "ر.ي"),
    EGP("EGP", "الجنيه المصري", "ج.م"),
    USD("USD", "الدولار الأمريكي", "$"),
    EUR("EUR", "اليورو", "€"),
    AED("AED", "الدرهم الإماراتي", "د.إ"),
    KWD("KWD", "الدينار الكويتي", "د.ك"),
    QAR("QAR", "الريال القطري", "ر.ق"),
    OMR("OMR", "الريال العُماني", "ر.ع"),
    BHD("BHD", "الدينار البحريني", "د.ب"),
    IQD("IQD", "الدينار العراقي", "د.ع"),
    SYP("SYP", "الليرة السورية", "ل.س"),
    LBP("LBP", "الليرة اللبنانية", "ل.ل"),
    SDG("SDG", "الجنيه السوداني", "ج.س"),
    LYD("LYD", "الدينار الليبي", "د.ل"),
    TND("TND", "الدينار التونسي", "د.ت"),
    DZD("DZD", "الدينار الجزائري", "د.ج"),
    MAD("MAD", "الدرهم المغربي", "د.م"),
    TRY("TRY", "الليرة التركية", "₺"),
    GBP("GBP", "الجنيه الإسترليني", "£"),
    SEK("SEK", "الكرونة السويدية", "kr"),
    ;

    val displayName: String get() = "$arabicName ($code)"

    companion object {
        fun fromCode(code: String?): CurrencyOption =
            entries.firstOrNull { it.code == code } ?: JOD
    }
}

object CurrencySettings {
    @Volatile
    var current: CurrencyOption = CurrencyOption.JOD
}
''',
)

# Format all balances with the selected currency, while leaving stored numeric values unchanged.
formatters_path = "app/src/main/java/com/dioonplus/app/util/Formatters.kt"
text = read(formatters_path)
text = replace_once(
    text,
    '''fun formatMoney(cents: Long, includeSign: Boolean = false): String {
    val formatter = NumberFormat.getNumberInstance(arabicLocale).apply {''',
    '''fun formatMoney(cents: Long, includeSign: Boolean = false): String {
    val currency = CurrencySettings.current
    val formatter = NumberFormat.getNumberInstance(arabicLocale).apply {''',
    "formatMoney currency",
)
text = replace_once(
    text,
    '    return "$sign${formatter.format(absolute)} د.أ"',
    '    return "$sign${formatter.format(absolute)} ${currency.symbol}"',
    "formatMoney symbol",
)
write(formatters_path, text)

# Persist the selected currency locally.
prefs_path = "app/src/main/java/com/dioonplus/app/security/AppPreferences.kt"
text = read(prefs_path)
text = replace_once(
    text,
    "import android.util.Base64\n",
    "import android.util.Base64\nimport com.dioonplus.app.util.CurrencyOption\n",
    "AppPreferences import",
)
text = replace_once(
    text,
    '''    var recoveryEmail: String
        get() = preferences.getString(KEY_RECOVERY_EMAIL, "").orEmpty()
        set(value) = preferences.edit().putString(KEY_RECOVERY_EMAIL, value.trim()).apply()
''',
    '''    var recoveryEmail: String
        get() = preferences.getString(KEY_RECOVERY_EMAIL, "").orEmpty()
        set(value) = preferences.edit().putString(KEY_RECOVERY_EMAIL, value.trim()).apply()

    var currency: CurrencyOption
        get() = CurrencyOption.fromCode(preferences.getString(KEY_CURRENCY, CurrencyOption.JOD.code))
        set(value) = preferences.edit().putString(KEY_CURRENCY, value.code).apply()
''',
    "AppPreferences currency property",
)
text = replace_once(
    text,
    '        private const val KEY_RECOVERY_EMAIL = "recovery_email"\n',
    '        private const val KEY_RECOVERY_EMAIL = "recovery_email"\n        private const val KEY_CURRENCY = "currency_code"\n',
    "AppPreferences currency key",
)
write(prefs_path, text)

# Initialise the global display currency from local preferences at app startup.
app_path = "app/src/main/java/com/dioonplus/app/DioonPlusApp.kt"
text = read(app_path)
text = replace_once(
    text,
    "import com.dioonplus.app.ui.theme.TextSecondary\n",
    "import com.dioonplus.app.ui.theme.TextSecondary\nimport com.dioonplus.app.util.CurrencySettings\n",
    "DioonPlusApp currency import",
)
text = replace_once(
    text,
    '''    val preferences = remember(context.applicationContext) {
        AppPreferences(context.applicationContext)
    }
    var unlocked''',
    '''    val preferences = remember(context.applicationContext) {
        AppPreferences(context.applicationContext)
    }
    CurrencySettings.current = preferences.currency
    var unlocked''',
    "DioonPlusApp currency init",
)
write(app_path, text)

# Keep activity and dialog content visible above large software keyboards.
main_path = "app/src/main/java/com/dioonplus/app/MainActivity.kt"
text = read(main_path)
text = replace_once(
    text,
    "import android.os.Bundle\n",
    "import android.os.Bundle\nimport android.view.WindowManager\n",
    "MainActivity import",
)
text = replace_once(
    text,
    '''        super.onCreate(savedInstanceState)
        enableEdgeToEdge()''',
    '''        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        enableEdgeToEdge()''',
    "MainActivity resize",
)
write(main_path, text)

manifest_path = "app/src/main/AndroidManifest.xml"
text = read(manifest_path)
text = replace_once(
    text,
    '''        <activity
            android:name=".MainActivity"
            android:exported="true">''',
    '''        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">''',
    "Manifest adjustResize",
)
write(manifest_path, text)

# Add scrollable, IME-aware content to customer/supplier entry dialog.
home_path = "app/src/main/java/com/dioonplus/app/ui/screens/HomeScreen.kt"
text = read(home_path)
text = replace_once(text, "import androidx.compose.foundation.layout.height\n", "import androidx.compose.foundation.layout.height\nimport androidx.compose.foundation.layout.heightIn\nimport androidx.compose.foundation.layout.imePadding\n", "Home layout imports")
text = replace_once(text, "import androidx.compose.foundation.lazy.items\n", "import androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.rememberScrollState\n", "Home scroll import")
text = replace_once(text, "import androidx.compose.foundation.text.KeyboardOptions\n", "import androidx.compose.foundation.text.KeyboardOptions\nimport androidx.compose.foundation.verticalScroll\n", "Home vertical scroll import")
text = replace_once(
    text,
    "            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {",
    '''            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {''',
    "Home keyboard aware dialog",
)
write(home_path, text)

# Add scrollable, IME-aware content to transaction entry dialog.
details_path = "app/src/main/java/com/dioonplus/app/ui/screens/PartyDetailsScreen.kt"
text = read(details_path)
text = replace_once(text, "import androidx.compose.foundation.layout.height\n", "import androidx.compose.foundation.layout.height\nimport androidx.compose.foundation.layout.heightIn\nimport androidx.compose.foundation.layout.imePadding\n", "Details layout imports")
text = replace_once(text, "import androidx.compose.foundation.lazy.items\n", "import androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.rememberScrollState\n", "Details scroll import")
text = replace_once(text, "import androidx.compose.foundation.text.KeyboardOptions\n", "import androidx.compose.foundation.text.KeyboardOptions\nimport androidx.compose.foundation.verticalScroll\n", "Details vertical scroll import")
text = replace_once(
    text,
    "            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {",
    '''            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {''',
    "Details keyboard aware dialog",
)
write(details_path, text)

# Replace settings with a compact, keyboard-aware screen including currency selection.
write(
    "app/src/main/java/com/dioonplus/app/ui/screens/SettingsScreen.kt",
    '''package com.dioonplus.app.ui.screens

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
    onLockNow: () -> Unit,
) {
    val context = LocalContext.current
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var selectedCurrency by remember { mutableStateOf(preferences.currency) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showRemovePinDialog by remember { mutableStateOf(false) }
    var pendingBackupJson by remember { mutableStateOf<String?>(null) }
    var pinRevision by remember { mutableStateOf(0) }
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
''',
)

# Bump version for this combined UX/currency update.
build_path = "app/build.gradle.kts"
text = read(build_path)
text = replace_once(text, '        versionCode = 4\n        versionName = "0.2.2"', '        versionCode = 5\n        versionName = "0.2.3"', "version bump")
write(build_path, text)

# Remove one-time patch automation files after applying.
for relative in [
    "tools/apply_ux_currency_patch.py",
    ".github/workflows/apply-ux-currency-patch.yml",
]:
    target = ROOT / relative
    if target.exists():
        target.unlink()
