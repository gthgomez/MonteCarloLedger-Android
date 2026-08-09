package com.montecarlo.ledger.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.montecarlo.ledger.GlassTokens
import com.montecarlo.ledger.MainViewModel
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.data.TransactionEntity

@Composable
fun LedgerScreen(
    viewModel: MainViewModel,
    onEditTransaction: (TransactionEntity) -> Unit,
    onEditIncome: (IncomeEntity) -> Unit,
    onEditPayment: (PaymentEntity) -> Unit,
    onAddIncome: () -> Unit,
    onAddPayment: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Activity", "Paychecks", "Bills")

    Column {
        SecondaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.70f),
            contentColor = GlassTokens.CyanBright,
            divider = {
                androidx.compose.material3.HorizontalDivider(
                    color = GlassTokens.DividerColor,
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            color = if (selectedTab == index) GlassTokens.CyanBright else GlassTokens.TextSecondary,
                        )
                    }
                )
            }
        }
        when (selectedTab) {
            0 -> TransactionHistoryScreen(viewModel, onEditTransaction)
            1 -> IncomeListScreen(viewModel, onEditIncome, onAddIncome)
            2 -> PaymentListScreen(viewModel, onEditPayment, onAddPayment)
        }
    }
}
