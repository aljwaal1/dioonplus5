package com.dioonplus.app.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dioonplus.app.ui.theme.BorderColor
import com.dioonplus.app.ui.theme.DebtRed
import com.dioonplus.app.ui.theme.DioonBlue
import com.dioonplus.app.ui.theme.DioonBlueDark
import com.dioonplus.app.ui.theme.DioonBlueSoft
import com.dioonplus.app.ui.theme.ElevatedSurface
import com.dioonplus.app.ui.theme.TextSecondary

private const val DEVELOPER_EMAIL = "yaya15112016@gmail.com"

@Composable
fun PinLockScreen(onUnlock: (String) -> Boolean) {
    val context = LocalContext.current
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

    fun contactDeveloper() {
        val intent = Intent(
            Intent.ACTION_SENDTO,
            Uri.parse("mailto:$DEVELOPER_EMAIL?subject=${Uri.encode("مساعدة في تطبيق ديون بلس")}"),
        )
        runCatching { context.startActivity(intent) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D3F87),
                        Color(0xFF165FC5),
                        Color(0xFFF4F7FC),
                        Color(0xFFF4F7FC),
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(22.dp))

            Surface(
                modifier = Modifier.size(76.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "ديون بلس",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "دفتر حساباتك محمي",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.82f),
            )

            Spacer(Modifier.height(22.dp))
            PinDots(length = pin.length, invalid = invalid)
            Spacer(Modifier.height(9.dp))
            Text(
                text = if (invalid) "رمز الدخول غير صحيح، حاول مرة أخرى" else "أدخل رمزك المكوّن من 4 إلى 6 أرقام",
                style = MaterialTheme.typography.bodySmall,
                color = if (invalid) Color(0xFFFFD8DB) else Color.White.copy(alpha = 0.78f),
            )

            Spacer(Modifier.height(24.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                color = ElevatedSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.72f)),
                shadowElevation = 10.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
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
                                tint = if (pin.isNotEmpty()) DioonBlueDark else TextSecondary.copy(alpha = 0.38f),
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

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = ::contactDeveloper) {
                Icon(Icons.Outlined.Email, contentDescription = null, tint = DioonBlueDark)
                Spacer(Modifier.size(7.dp))
                Text("مراسلة المطور", color = DioonBlueDark, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PinDots(length: Int, invalid: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(11.dp),
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
                            invalid -> Color(0xFFFFC8CC)
                            filled -> Color.White
                            else -> Color.White.copy(alpha = 0.28f)
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
            .size(60.dp)
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
            .size(60.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = when {
            primary && enabled -> DioonBlue
            primary -> DioonBlue.copy(alpha = 0.30f)
            else -> DioonBlueSoft.copy(alpha = 0.45f)
        },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}
