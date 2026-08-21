package com.montecarlo.ledger.ui

import android.widget.Toast
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.montecarlo.ledger.GlassTokens
import com.montecarlo.ledger.MainViewModel
import com.montecarlo.ledger.data.BillOccurrenceEntity
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.data.TransactionEntity

/**
 * Bottom-sheet flows extracted from AppChrome so the navigation scaffold stays
 * readable. Each sheet owns exactly one concern; persistence outcomes are routed
 * through [handleResult] (mirrors AppChrome's handlePersistenceResult).
 */
private typealias PersistenceResultHandler = (result: Result<Unit>, showSuccess: Boolean, onSuccessAction: () -> Unit) -> Unit

@Composable
private fun MaterialTheme_colorSchemeSafe(): ColorScheme = androidx.compose.material3.MaterialTheme.colorScheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetContainer(
    colorScheme: ColorScheme,
    onDismissRequest: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = colorScheme.surfaceContainerLow.copy(alpha = 0.96f),
        contentColor = GlassTokens.TextPrimary,
        content = content,
    )
}

@Composable
internal fun EditSheets(
    selectedIncome: IncomeEntity?,
    selectedPayment: PaymentEntity?,
    selectedTransaction: TransactionEntity?,
    onSelectIncome: (IncomeEntity?) -> Unit,
    onSelectPayment: (PaymentEntity?) -> Unit,
    onSelectTransaction: (TransactionEntity?) -> Unit,
    viewModel: MainViewModel,
    handleResult: PersistenceResultHandler,
) {
    val colorScheme = MaterialTheme_colorSchemeSafe()

    selectedIncome?.let { income ->
        SheetContainer(colorScheme, { onSelectIncome(null) }) {
            EditIncomeScreen(
                income = income,
                onSave = {
                    viewModel.updateIncome(it) { result ->
                        handleResult(result, true) { onSelectIncome(null) }
                    }
                },
                onDelete = {
                    viewModel.deleteIncome(it) { result ->
                        handleResult(result, true) { onSelectIncome(null) }
                    }
                },
                onDismiss = { onSelectIncome(null) }
            )
        }
    }

    selectedPayment?.let { payment ->
        SheetContainer(colorScheme, { onSelectPayment(null) }) {
            EditPaymentScreen(
                payment = payment,
                onSave = {
                    viewModel.updatePayment(it) { result ->
                        handleResult(result, true) { onSelectPayment(null) }
                    }
                },
                onDelete = {
                    viewModel.deletePayment(it) { result ->
                        handleResult(result, true) { onSelectPayment(null) }
                    }
                },
                onDismiss = { onSelectPayment(null) }
            )
        }
    }

    selectedTransaction?.let { transaction ->
        SheetContainer(colorScheme, { onSelectTransaction(null) }) {
            EditTransactionScreen(
                transaction = transaction,
                onSave = {
                    viewModel.updateTransaction(it) { result ->
                        handleResult(result, true) { onSelectTransaction(null) }
                    }
                },
                onSaveRule = { description, category ->
                    viewModel.saveTransactionRule(description, category)
                },
                onDelete = {
                    viewModel.deleteTransaction(it) { result ->
                        handleResult(result, true) { onSelectTransaction(null) }
                    }
                },
                onDismiss = { onSelectTransaction(null) }
            )
        }
    }
}

@Composable
internal fun AddKindSheet(
    addKind: AddKind?,
    pendingBillPrefill: BillPrefill?,
    payments: List<PaymentEntity>,
    billOccurrences: List<BillOccurrenceEntity>,
    dashboardErrorMessage: String?,
    onSetAddKind: (AddKind?) -> Unit,
    onClearBillPrefill: () -> Unit,
    onShowAddAnotherPrompt: () -> Unit,
    onTransactionSaved: () -> Unit,
    viewModel: MainViewModel,
    handleResult: PersistenceResultHandler,
) {
    val colorScheme = MaterialTheme_colorSchemeSafe()
    if (addKind == null) return

    SheetContainer(colorScheme, { onSetAddKind(null) }) {
        when (addKind) {
            AddKind.Income -> AddIncomeScreen(
                onCancel = { onSetAddKind(null) },
                onSave = {
                    viewModel.addIncome(it) { result ->
                        handleResult(result, true) { onSetAddKind(null) }
                    }
                }
            )
            AddKind.Bill -> AddPaymentScreen(
                initialDraft = pendingBillPrefill,
                onSave = {
                    viewModel.addPayment(it) { result ->
                        handleResult(result, false) {
                            onClearBillPrefill()
                            onSetAddKind(null)
                            onShowAddAnotherPrompt()
                        }
                    }
                },
                onDismiss = {
                    onClearBillPrefill()
                    onSetAddKind(null)
                }
            )
            AddKind.Transaction -> AddTransactionScreen(
                payments = payments,
                billOccurrences = billOccurrences,
                externalErrorMessage = dashboardErrorMessage,
                onCancel = { onSetAddKind(null) },
                onSave = { description, amountCents, type, linkedOccurrenceId, category, date ->
                    viewModel.addTransaction(description, amountCents, type, linkedOccurrenceId, category, date) {
                        onSetAddKind(null)
                        onTransactionSaved()
                    }
                }
            )
            AddKind.Goal -> AddGoalDialog(
                onDismiss = { onSetAddKind(null) },
                onSave = { goal ->
                    viewModel.addGoal(goal) { result ->
                        handleResult(result, true) { onSetAddKind(null) }
                    }
                }
            )
        }
    }
}

@Composable
internal fun ImportCsvSheets(
    transactionPreview: CsvImportPreview?,
    billPreview: BillCsvImportPreview?,
    onDismissTransaction: () -> Unit,
    onDismissBill: () -> Unit,
    viewModel: MainViewModel,
) {
    val colorScheme = MaterialTheme_colorSchemeSafe()
    val context = LocalContext.current

    transactionPreview?.let { preview ->
        SheetContainer(colorScheme, onDismissTransaction) {
            CsvImportSheetContent(
                preview = preview,
                onDismiss = onDismissTransaction,
                onImport = { mappedPreview ->
                    viewModel.importTransactions(mappedPreview.importedTransactions) { result ->
                        val message = result.fold(
                            onSuccess = { "Imported ${mappedPreview.importedTransactions.size} transactions" },
                            onFailure = { it.message ?: "Import failed" },
                        )
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        onDismissTransaction()
                    }
                }
            )
        }
    }

    billPreview?.let { preview ->
        SheetContainer(colorScheme, onDismissBill) {
            BillCsvImportSheetContent(
                preview = preview,
                onDismiss = onDismissBill,
                onImport = { mappedPreview ->
                    viewModel.importPayments(mappedPreview.importedPayments) { result ->
                        val message = result.fold(
                            onSuccess = { "Imported ${mappedPreview.importedPayments.size} bills" },
                            onFailure = { it.message ?: "Import failed" },
                        )
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        onDismissBill()
                    }
                }
            )
        }
    }
}

@Composable
internal fun BankBalanceSheet(
    bankBalanceCents: Long,
    onDismiss: () -> Unit,
    viewModel: MainViewModel,
    handleResult: PersistenceResultHandler,
) {
    val colorScheme = MaterialTheme_colorSchemeSafe()
    SheetContainer(colorScheme, onDismiss) {
        BankBalanceSheetContent(
            initialAmountCents = bankBalanceCents,
            onDismiss = onDismiss,
            onConfirm = { amountCents ->
                viewModel.setBankBalance(amountCents) { result ->
                    handleResult(result, true) { onDismiss() }
                }
            }
        )
    }
}
