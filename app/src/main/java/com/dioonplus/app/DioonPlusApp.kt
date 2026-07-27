package com.dioonplus.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.dioonplus.app.data.LedgerDatabase
import com.dioonplus.app.security.AppPreferences
import com.dioonplus.app.ui.screens.HomeScreen
import com.dioonplus.app.ui.screens.PartyDetailsScreen
import com.dioonplus.app.ui.screens.PinLockScreen
import com.dioonplus.app.ui.screens.ReportsScreen
import com.dioonplus.app.ui.screens.SettingsScreen
import com.dioonplus.app.ui.theme.DioonBlue
import com.dioonplus.app.ui.theme.TextSecondary

private data class BottomDestination(
    val label: String,
    val icon: ImageVector,
)

@Composable
fun DioonPlusApp() {
    val context = LocalContext.current
    val appState = remember(context.applicationContext) {
        DioonAppState(LedgerDatabase(context.applicationContext))
    }
    val preferences = remember(context.applicationContext) {
        AppPreferences(context.applicationContext)
    }
    var unlocked by rememberSaveable { mutableStateOf(!preferences.hasPin()) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        if (!unlocked && preferences.hasPin()) {
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
                            tonalElevation = 6.dp,
                            containerColor = MaterialTheme.colorScheme.surface,
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
                        0 -> HomeScreen(contentPadding = innerPadding, appState = appState)
                        1 -> ReportsScreen(contentPadding = innerPadding, appState = appState)
                        else -> SettingsScreen(
                            contentPadding = innerPadding,
                            appState = appState,
                            preferences = preferences,
                            onLockNow = {
                                if (preferences.hasPin()) unlocked = false
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
