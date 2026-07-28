from pathlib import Path

root = Path(__file__).resolve().parents[1]

party = root / "app/src/main/java/com/dioonplus/app/ui/screens/PartyDetailsScreen.kt"
text = party.read_text(encoding="utf-8")
old = '    pendingDeleteEntry?.let { entry -> AlertDialog(onDismissRequest = { pendingDeleteEntry = null }, title = { Text("حذف الحركة؟") }, text = { Text(if (entry.isPayment) "سيتم حذف دفعة السداد وإعادة المبلغ إلى المتبقي." else "سيتم حذف الدين وجميع الدفعات المرتبطة به.") }, confirmButton = { Button({ if (appState.deleteEntry(entry.id)) pendingDeleteEntry = null }, colors = ButtonDefaults.buttonColors(containerColor = DebtRed)) { Text("حذف") } }, dismissButton = { TextButton({ pendingDeleteEntry = null }) { Text("إلغاء") } })\n'
new = '    pendingDeleteEntry?.let { entry -> AlertDialog(onDismissRequest = { pendingDeleteEntry = null }, title = { Text("حذف الحركة؟") }, text = { Text(if (entry.isPayment) "سيتم حذف دفعة السداد وإعادة المبلغ إلى المتبقي." else "سيتم حذف الدين وجميع الدفعات المرتبطة به.") }, confirmButton = { Button({ if (appState.deleteEntry(entry.id)) pendingDeleteEntry = null }, colors = ButtonDefaults.buttonColors(containerColor = DebtRed)) { Text("حذف") } }, dismissButton = { TextButton({ pendingDeleteEntry = null }) { Text("إلغاء") } }) }\n'
if text.count(old) != 1:
    raise RuntimeError(f"pending delete block matches: {text.count(old)}")
party.write_text(text.replace(old, new, 1), encoding="utf-8")

home = root / "app/src/main/java/com/dioonplus/app/ui/screens/HomeScreen.kt"
text = home.read_text(encoding="utf-8")
old = 'import androidx.compose.material3.Text\n'
new = 'import androidx.compose.material3.Text\nimport androidx.compose.material3.TextButton\n'
if text.count(old) != 1:
    raise RuntimeError(f"home text import matches: {text.count(old)}")
home.write_text(text.replace(old, new, 1), encoding="utf-8")

for relative in ["tools/fix_due_release_compile.py", ".github/workflows/fix-due-release-compile.yml"]:
    path = root / relative
    if path.exists():
        path.unlink()
