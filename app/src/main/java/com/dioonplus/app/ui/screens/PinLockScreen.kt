package com.dioonplus.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dioonplus.app.ui.theme.DioonBlue
import com.dioonplus.app.ui.theme.DioonBlueDark
import com.dioonplus.app.ui.theme.DioonBlueSoft
import com.dioonplus.app.ui.theme.TextSecondary

@Composable
fun PinLockScreen(onUnlock: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    fun submit() {
        if (onUnlock(pin)) {
            invalid = false
        } else {
            invalid = true
            pin = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(DioonBlueSoft, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = DioonBlue, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("ديون بلس", style = MaterialTheme.typography.headlineMedium, color = DioonBlueDark)
            Text("أدخل رمز PIN لفتح دفتر حساباتك", color = TextSecondary)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { value ->
                    pin = value.filter(Char::isDigit).take(6)
                    invalid = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("رمز PIN") },
                singleLine = true,
                isError = invalid,
                supportingText = if (invalid) ({ Text("الرمز غير صحيح") }) else null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = ::submit,
                modifier = Modifier.fillMaxWidth(),
                enabled = pin.length in 4..6,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("فتح التطبيق", modifier = Modifier.padding(vertical = 5.dp), fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}
