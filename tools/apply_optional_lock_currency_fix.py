from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    (ROOT / path).write_text(content, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


# Persist a separate switch for requiring the PIN at application entry.
path = "app/src/main/java/com/dioonplus/app/security/AppPreferences.kt"
text = read(path)
text = replace_once(
    text,
    '''    fun clearPin() {
        preferences.edit().remove(KEY_PIN_SALT).remove(KEY_PIN_HASH).apply()
    }
''',
    '''    fun clearPin() {
        preferences.edit()
            .remove(KEY_PIN_SALT)
            .remove(KEY_PIN_HASH)
            .putBoolean(KEY_ENTRY_LOCK, false)
            .apply()
    }
''',
    "clearPin disables entry lock",
)
text = replace_once(
    text,
    '''    var vibrationEnabled: Boolean
        get() = preferences.getBoolean(KEY_VIBRATION, true)
        set(value) = preferences.edit().putBoolean(KEY_VIBRATION, value).apply()

    var recoveryEmail: String
''',
    '''    var vibrationEnabled: Boolean
        get() = preferences.getBoolean(KEY_VIBRATION, true)
        set(value) = preferences.edit().putBoolean(KEY_VIBRATION, value).apply()

    var entryLockEnabled: Boolean
        get() = preferences.getBoolean(KEY_ENTRY_LOCK, hasPin())
        set(value) = preferences.edit().putBoolean(KEY_ENTRY_LOCK, value && hasPin()).apply()

    var recoveryEmail: String
''',
    "entry lock preference",
)
text = replace_once(
    text,
    '''        private const val KEY_VIBRATION = "vibration_enabled"
        private const val KEY_RECOVERY_EMAIL = "recovery_email"
''',
    '''        private const val KEY_VIBRATION = "vibration_enabled"
        private const val KEY_ENTRY_LOCK = "entry_lock_enabled"
        private const val KEY_RECOVERY_EMAIL = "recovery_email"
''',
    "entry lock key",
)
write(path, text)

# Keep the lock switch as application state so enabling/disabling it takes effect immediately.
path = "app/src/main/java/com/dioonplus/app/DioonPlusApp.kt"
text = read(path)
text = replace_once(
    text,
    '''    CurrencySettings.current = preferences.currency
    var unlocked by rememberSaveable { mutableStateOf(!preferences.hasPin()) }
''',
    '''    CurrencySettings.current = preferences.currency
    var entryLockEnabled by rememberSaveable {
        mutableStateOf(preferences.entryLockEnabled && preferences.hasPin())
    }
    var unlocked by rememberSaveable { mutableStateOf(!entryLockEnabled) }
''',
    "app entry lock state",
)
text = replace_once(
    text,
    '''        if (!unlocked && preferences.hasPin()) {''',
    '''        if (!unlocked && entryLockEnabled && preferences.hasPin()) {''',
    "lock screen condition",
)
text = replace_once(
    text,
    '''                            preferences = preferences,
                            onLockNow = {
                                if (preferences.hasPin()) unlocked = false
                            },
''',
    '''                            preferences = preferences,
                            onEntryLockChanged = { enabled ->
                                entryLockEnabled = enabled && preferences.hasPin()
                                if (!entryLockEnabled) unlocked = true
                            },
                            onLockNow = {
                                if (entryLockEnabled && preferences.hasPin()) unlocked = false
                            },
''',
    "settings lock callbacks",
)
write(path, text)

# Add explicit optional entry-lock control to settings.
path = "app/src/main/java/com/dioonplus/app/ui/screens/SettingsScreen.kt"
text = read(path)
text = replace_once(
    text,
    '''    appState: DioonAppState,
    preferences: AppPreferences,
    onLockNow: () -> Unit,
''',
    '''    appState: DioonAppState,
    preferences: AppPreferences,
    onEntryLockChanged: (Boolean) -> Unit,
    onLockNow: () -> Unit,
''',
    "settings signature",
)
text = replace_once(
    text,
    '''    var pendingBackupJson by remember { mutableStateOf<String?>(null) }
    var pinRevision by remember { mutableStateOf(0) }
    var soundEnabled by remember { mutableStateOf(preferences.soundEnabled) }
''',
    '''    var pendingBackupJson by remember { mutableStateOf<String?>(null) }
    var pinRevision by remember { mutableStateOf(0) }
    var entryLockEnabled by remember {
        mutableStateOf(preferences.entryLockEnabled && preferences.hasPin())
    }
    var enableLockAfterPinSave by remember { mutableStateOf(false) }
    var soundEnabled by remember { mutableStateOf(preferences.soundEnabled) }
''',
    "settings lock state",
)
old_security_block = '''        item {
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
'''
new_security_block = '''        item {
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
'''
text = replace_once(text, old_security_block, new_security_block, "settings security block")
text = replace_once(
    text,
    '''            onSaved = {
                pinRevision++
                showPinDialog = false
                Toast.makeText(context, "تم حفظ رمز PIN", Toast.LENGTH_SHORT).show()
            },
''',
    '''            onSaved = {
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
''',
    "PIN saved callback",
)
text = replace_once(
    text,
    '''            onRemoved = {
                pinRevision++
                showRemovePinDialog = false
                Toast.makeText(context, "تمت إزالة رمز PIN", Toast.LENGTH_SHORT).show()
            },
''',
    '''            onRemoved = {
                entryLockEnabled = false
                enableLockAfterPinSave = false
                onEntryLockChanged(false)
                pinRevision++
                showRemovePinDialog = false
                Toast.makeText(context, "تمت إزالة رمز الدخول وإيقاف الحماية", Toast.LENGTH_SHORT).show()
            },
''',
    "PIN removed callback",
)
write(path, text)

# Replace any fixed/default currency hint in the money editor with the selected currency.
path = "app/src/main/java/com/dioonplus/app/ui/screens/PartyDetailsScreen.kt"
text = read(path)
text = replace_once(
    text,
    '''import com.dioonplus.app.ui.theme.TextSecondary
import com.dioonplus.app.util.formatDateTime
''',
    '''import com.dioonplus.app.ui.theme.TextSecondary
import com.dioonplus.app.util.CurrencySettings
import com.dioonplus.app.util.formatDateTime
''',
    "currency settings import",
)
text = replace_once(
    text,
    '''                    label = { Text("المبلغ") },
                    singleLine = true,
                    isError = amountError,
                    supportingText = if (amountError) ({ Text("أدخل مبلغاً صحيحاً أكبر من صفر") }) else null,
''',
    '''                    label = { Text("المبلغ") },
                    placeholder = { Text("مثال: 100 ${CurrencySettings.current.symbol}") },
                    suffix = { Text(CurrencySettings.current.symbol) },
                    singleLine = true,
                    isError = amountError,
                    supportingText = {
                        if (amountError) {
                            Text("أدخل مبلغاً صحيحاً أكبر من صفر")
                        } else {
                            Text("العملة: ${CurrencySettings.current.arabicName} (${CurrencySettings.current.code})")
                        }
                    },
''',
    "dynamic currency amount field",
)
write(path, text)

# Bump patch version.
path = "app/build.gradle.kts"
text = read(path)
text = replace_once(
    text,
    '''        versionCode = 7
        versionName = "0.4.0"''',
    '''        versionCode = 8
        versionName = "0.4.1"''',
    "version bump",
)
write(path, text)

# Remove one-time patch automation files after applying.
for relative in [
    "tools/apply_optional_lock_currency_fix.py",
    ".github/workflows/apply-optional-lock-currency-fix.yml",
]:
    target = ROOT / relative
    if target.exists():
        target.unlink()
