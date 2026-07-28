from pathlib import Path

root = Path(__file__).resolve().parents[1]
path = root / "app/src/main/java/com/dioonplus/app/ui/screens/SettingsScreen.kt"
text = path.read_text(encoding="utf-8")
old = '            onDismiss = { showPinDialog = false },\n            onSaved = {'
new = '''            onDismiss = {
                enableLockAfterPinSave = false
                showPinDialog = false
            },
            onSaved = {'''
if text.count(old) != 1:
    raise RuntimeError(f"Expected one PIN dismiss callback, found {text.count(old)}")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
for relative in ["tools/fix_lock_dialog_cancel.py", ".github/workflows/fix-lock-dialog-cancel.yml"]:
    target = root / relative
    if target.exists():
        target.unlink()
