package com.montecarlo.ledger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.montecarlo.ledger.GlassTokens

@Composable
fun QuickAddFab(
    onAddExpense: () -> Unit,
    onAddBill: () -> Unit,
    onAddIncome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpeedDialItem(
                    label = "Log Expense",
                    icon = Icons.Default.ShoppingCart,
                    tintColor = GlassTokens.CyanBright,
                    onClick = {
                        expanded = false
                        onAddExpense()
                    }
                )
                SpeedDialItem(
                    label = "Add Bill",
                    icon = Icons.Default.ReceiptLong,
                    tintColor = GlassTokens.VioletLight,
                    onClick = {
                        expanded = false
                        onAddBill()
                    }
                )
                SpeedDialItem(
                    label = "Log Paycheck",
                    icon = Icons.Default.AttachMoney,
                    tintColor = GlassTokens.PositiveGreen,
                    onClick = {
                        expanded = false
                        onAddIncome()
                    }
                )
            }
        }

        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = GlassTokens.Cyan,
            contentColor = Color.White,
            modifier = Modifier.semantics { role = Role.Button }
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = if (expanded) "Close quick add menu" else "Quick add options"
            )
        }
    }
}

@Composable
private fun SpeedDialItem(
    label: String,
    icon: ImageVector,
    tintColor: Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics { role = Role.Button }
    ) {
        GlassCard(
            surfaceStyle = GlassSurfaceStyle.Quiet,
            tint = GlassTint.Neutral,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = GlassTokens.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = tintColor,
            contentColor = Color.Black
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
    }
}
