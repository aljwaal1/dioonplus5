from pathlib import Path

root = Path(__file__).resolve().parents[1]
settings = root / "app/src/main/java/com/dioonplus/app/ui/screens/SettingsScreen.kt"
text = settings.read_text(encoding="utf-8")
old = 'Regex("\\d{4,6}")'
new = 'Regex("\\\\d{4,6}")'
if text.count(old) != 1:
    raise RuntimeError(f"Expected one invalid regex, found {text.count(old)}")
settings.write_text(text.replace(old, new, 1), encoding="utf-8")
for relative in ["tools/fix_currency_build.py", ".github/workflows/fix-currency-build.yml"]:
    path = root / relative
    if path.exists():
        path.unlink()
