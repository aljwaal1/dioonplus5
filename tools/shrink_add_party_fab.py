from pathlib import Path

root = Path(__file__).resolve().parents[1]
path = root / "app/src/main/java/com/dioonplus/app/ui/screens/HomeScreen.kt"
text = path.read_text(encoding="utf-8")
text = text.replace(
    "import androidx.compose.material3.ExtendedFloatingActionButton\n",
    "import androidx.compose.material3.FloatingActionButton\n",
    1,
)
old = '''        ExtendedFloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            containerColor = DioonBlue,
            contentColor = Color.White,
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = {
                Text(if (appState.selectedPartyType == PartyType.CUSTOMER) "إضافة عميل" else "إضافة مورد")
            },
        )
'''
new = '''        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, bottom = 14.dp)
                .size(52.dp),
            containerColor = DioonBlue,
            contentColor = Color.White,
            shape = CircleShape,
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = if (appState.selectedPartyType == PartyType.CUSTOMER) "إضافة عميل" else "إضافة مورد",
                modifier = Modifier.size(24.dp),
            )
        }
'''
if old not in text:
    raise RuntimeError("Floating action button block not found")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
