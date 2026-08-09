package com.montecarlo.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.montecarlo.ledger.GlassTokens
import kotlinx.coroutines.delay

@Composable
fun AppLockScreen(
    errorMessage: String?,
    lockoutRemainingSeconds: Long,
    onUnlock: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pin by remember { mutableStateOf("") }
    var countdownSeconds by remember(lockoutRemainingSeconds) { mutableLongStateOf(lockoutRemainingSeconds) }

    // Local countdown timer that ticks every second while locked out.
    val lockedOut = countdownSeconds > 0
    LaunchedEffect(lockedOut) {
        if (lockedOut) {
            while (countdownSeconds > 0) {
                delay(1000L)
                countdownSeconds -= 1
            }
        }
    }

    fun formatCountdown(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return when {
            minutes > 0 && seconds > 0 -> "${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            tint = GlassTint.Teal,
            surfaceStyle = GlassSurfaceStyle.Hero,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = GlassTokens.CyanBright,
                )
                Text(
                    "Unlock MonteCarlo Ledger",
                    style = MaterialTheme.typography.titleLarge,
                    color = GlassTokens.TextPrimary,
                )
                if (lockedOut) {
                    Text(
                        "Too many failed attempts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTokens.ErrorRed,
                    )
                    Text(
                        "Try again in ${formatCountdown(countdownSeconds)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassTokens.TextSecondary,
                    )
                } else {
                    Text(
                        "Enter your app lock PIN.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTokens.TextSecondary,
                    )
                }
                OutlinedTextField(
                    value = pin,
                    onValueChange = { next -> pin = next.filter { it.isDigit() }.take(12) },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null,
                    enabled = !lockedOut,
                    supportingText = {
                        if (errorMessage != null && !lockedOut) {
                            Text(errorMessage)
                        }
                    },
                )
                Button(
                    onClick = { onUnlock(pin) },
                    enabled = pin.length >= 4 && !lockedOut,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Unlock")
                }
            }
        }
    }
}
