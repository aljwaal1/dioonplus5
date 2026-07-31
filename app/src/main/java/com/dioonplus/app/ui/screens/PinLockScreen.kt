package com.dioonplus.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dioonplus.app.ui.theme.BorderColor
import com.dioonplus.app.ui.theme.DebtRed
import com.dioonplus.app.ui.theme.DebtRedSoft
import com.dioonplus.app.ui.theme.DioonBlue
import com.dioonplus.app.ui.theme.DioonBlueDark
import com.dioonplus.app.ui.theme.DioonBlueSoft
import com.dioonplus.app.ui.theme.ElevatedSurface
import com.dioonplus.app.ui.theme.TextSecondary

@Composable
fun PinLockScreen(onUnlock: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    fun appendDigit(digit: String) {
        if (pin.length < 6) {
            pin += digit
            invalid = false
        }
    }

    fun submit() {
        if (pin.length !in 4..6) return
        if (onUnlock(pin)) {
            invalid = false
        } else {
            invalid = true
            pin = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.6f))

        Surface(
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(22.dp),
            color = DioonBlueSoft,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = DioonBlue,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = "ديون بلس",
            style = MaterialTheme.typography.headlineMedium,
            color = DioonBlueDark,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "أدخل رمز الدخول",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )

        Spacer(Modifier.height(26.dp))
        PinDots(length = pin.length, invalid = invalid)
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (invalid) "رمز الدخول غير صحيح" else "يتكون الرمز من 4 إلى 6 أرقام",
            style = MaterialTheme.typography.bodySmall,
            color = if (invalid) DebtRed else TextSecondary,
        )

        Spacer(Modifier.weight(0.45f))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = ElevatedSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                ).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        row.forEach { digit ->
                            NumberKey(label = digit, onClick = { appendDigit(digit) })
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionKey(
                        enabled = pin.isNotEmpty(),
                        onClick = {
                            if (pin.isNotEmpty()) {
                                pin = pin.dropLast(1)
                                invalid = false
                            }
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Backspace,
                            contentDescription = "حذف رقم",
                            tint = if (pin.isNotEmpty()) DioonBlueDark else TextSecondary.copy(alpha = 0.4f),
                        )
                    }
                    NumberKey(label = "0", onClick = { appendDigit("0") })
                    ActionKey(
                        enabled = pin.length in 4..6,
                        primary = true,
                        onClick = ::submit,
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = "فتح التطبيق", tint = Color.White)
                    }
                }
            }
        }

        Spacer(Modifier.weight(0.35f))
    }
}

@Composable
private fun PinDots(length: Int, invalid: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(6) { index ->
            val filled = index < length
            Box(
                modifier = Modifier
                    .size(if (filled) 14.dp else 12.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            invalid -> DebtRed
                            filled -> DioonBlue
                            else -> BorderColor
                        },
                    ),
            )
        }
    }
}

@Composable
private fun NumberKey(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(62.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        shadowElevation = 1.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = DioonBlueDark,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ActionKey(
    enabled: Boolean,
    primary: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(62.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = when {
            primary && enabled -> DioonBlue
            primary -> DioonBlue.copy(alpha = 0.35f)
            else -> Color.Transparent
        },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}
