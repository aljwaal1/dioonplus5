from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


# Party details: stop auto-opening the keyboard and keep helper/error areas at a fixed height.
path = "app/src/main/java/com/dioonplus/app/ui/screens/PartyDetailsScreen.kt"
text = read(path)
text = text.replace("    LaunchedEffect(Unit) { requester.requestFocus(); keyboard?.show() }\n", "")
text = text.replace(".focusRequester(requester)", "")
text = text.replace(
    'supportingText = { if (error) Text("أدخل مبلغاً صحيحاً") }',
    'supportingText = { Text(if (error) "أدخل مبلغاً صحيحاً" else " ") }',
)
text = text.replace(
    'supportingText = { if (error) Text("أدخل مبلغاً صحيحاً أكبر من صفر") }',
    'supportingText = { Text(if (error) "أدخل مبلغاً صحيحاً أكبر من صفر" else " ") }',
)
text = text.replace(
    'supportingText = if (amountError) ({ Text("أدخل مبلغاً صحيحاً أكبر من صفر") }) else null',
    'supportingText = { Text(if (amountError) "أدخل مبلغاً صحيحاً أكبر من صفر" else " ") }',
)
write(path, text)

# Home add-account dialog: reserve helper text space so the dialog never jumps.
path = "app/src/main/java/com/dioonplus/app/ui/screens/HomeScreen.kt"
text = read(path)
text = text.replace(
    'supportingText = if (nameError) ({ Text("الاسم مطلوب") }) else null',
    'supportingText = { Text(if (nameError) "الاسم مطلوب" else " ") }',
)
# Remove automatic focus/keyboard opening in add-account form if present.
text = text.replace("    LaunchedEffect(Unit) { requester.requestFocus(); keyboard?.show() }\n", "")
text = text.replace(".focusRequester(requester)", "")
write(path, text)

# Settings PIN dialogs: always reserve a stable error line.
path = "app/src/main/java/com/dioonplus/app/ui/screens/SettingsScreen.kt"
text = read(path)
text = text.replace(
    '                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }',
    '                Text(error.orEmpty().ifBlank { " " }, color = MaterialTheme.colorScheme.error, modifier = Modifier.height(22.dp))',
)
text = text.replace(
    '                if (error) Text("رمز PIN غير صحيح", color = MaterialTheme.colorScheme.error)',
    '                Text(if (error) "رمز PIN غير صحيح" else " ", color = MaterialTheme.colorScheme.error, modifier = Modifier.height(22.dp))',
)
write(path, text)

# Keep the visual-only release version separate.
path = "app/build.gradle.kts"
text = read(path)
text = replace_once(text, 'versionCode = 10', 'versionCode = 11', 'versionCode')
text = replace_once(text, 'versionName = "0.5.1"', 'versionName = "0.5.2"', 'versionName')
write(path, text)
