package com.dioonplus.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.dioonplus.app.security.AppPreferences
import com.dioonplus.app.ui.screens.HomeScreen
import com.dioonplus.app.ui.screens.PartyDetailsScreen
import com.dioonplus.app.ui.screens.PinLockScreen
import com.dioonplus.app.ui.screens.ReportsScreen
import com.dioonplus.app.ui.screens.SettingsScreen
import com.dioonplus.app.ui.theme.BorderColor
import com.dioonplus.app.ui.theme.DioonBlue
import com.dioonplus.app.ui.theme.DioonBlueDark
import com.dioonplus.app.ui.theme.DioonBlueSoft
import com.dioonplus.app.ui.theme.TextSecondary
import com.dioonplus.app.util.CurrencySettings

private data class BottomDestination(
    val label: String,
    val icon: ImageVector,
)

@Composable
fun DioonPlusApp() {
    val context = LocalContext.current
    val appState = remember(context.applicationContext) {
        DioonAppState(context.applicationContext)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val preferences = remember(context.applicationContext) {
        AppPreferences(context.applicationContext)
    }
    CurrencySettings.current = preferences.currency
    var entryLockEnabled by rememberSaveable {
        mutableStateOf(preferences.entryLockEnabled && preferences.hasPin())
    }
    var unlocked by rememberSaveable { mutableStateOf(!entryLockEnabled) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        if (!unlocked && entryLockEnabled && preferences.hasPin()) {
            PinLockScreen(
                onUnlock = { pin ->
                    preferences.verifyPin(pin).also { valid ->
                        if (valid) unlocked = true
                    }
                },
            )
        } else {
            val selectedParty = appState.selectedParty
            if (selectedParty != null) {
                PartyDetailsScreen(
                    appState = appState,
                    party = selectedParty,
                    preferences = preferences,
                    onBack = appState::closeParty,
                )
            } else {
                var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
                val destinations = listOf(
                    BottomDestination("الرئيسية", Icons.Outlined.Home),
                    BottomDestination("التقارير", Icons.Outlined.Assessment),
                    BottomDestination("الإعدادات", Icons.Outlined.Settings),
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier.navigationBarsPadding(),
                            tonalElevation = 6.dp,
                            containerColor = MaterialTheme.colorScheme.surface,
                            windowInsets = WindowInsets(0, 0, 0, 0),
                        ) {
                            destinations.forEachIndexed { index, item ->
                                NavigationBarItem(
                                    selected = selectedIndex == index,
                                    onClick = {
                                        selectedIndex = index
                                        appState.refreshAll()
                                    },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = DioonBlue,
                                        selectedTextColor = DioonBlue,
                                        indicatorColor = DioonBlue.copy(alpha = 0.10f),
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                    ),
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    when (selectedIndex) {
                        0 -> HomeScreen(contentPadding = innerPadding, appState = appState, preferences = preferences)
                        1 -> ReportsWithSharingHint(contentPadding = innerPadding, appState = appState)
                        else -> SettingsScreen(
                            contentPadding = innerPadding,
                            appState = appState,
                            preferences = preferences,
                            onEntryLockChanged = { enabled ->
                                entryLockEnabled = enabled && preferences.hasPin()
                                if (!entryLockEnabled) unlocked = true
                            },
                            onLockNow = {
                                if (entryLockEnabled && preferences.hasPin()) unlocked = false
                            },
                        )
                    }
                }
            }
        }

        appState.errorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = appState::dismissError,
                title = { Text("تعذر تنفيذ العملية") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = appState::dismissError) { Text("حسناً") }
                },
            )
        }
    }
}

@Composable
private fun ReportsWithSharingHint(contentPadding: PaddingValues, appState: DioonAppState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 14.dp),
            color = DioonBlueSoft,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier.size(38.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.PersonSearch, contentDescription = null, tint = DioonBlue)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("إرسال تقرير عام أو كشف حساب فردي", color = DioonBlueDark, fontWeight = FontWeight.Bold)
                    Text(
                        "اترك «صاحب الحساب» على الكل لإرسال جميع الكشوفات، أو اختر شخصاً ليظهر زر إرسال كشفه مباشرة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Icon(Icons.Outlined.Send, contentDescription = null, tint = DioonBlue)
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            ReportsScreen(contentPadding = PaddingValues(0.dp), appState = appState)
        }
    }
}
