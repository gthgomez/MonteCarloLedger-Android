package com.montecarlo.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.montecarlo.ledger.GlassTokens
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.processing.PaymentSchedule
import com.montecarlo.ledger.util.centsToDisplay
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.abs

internal data class CsvImportPreview(
    val sourceName: String,
    val csvText: String = "",
    val headers: List<String> = emptyList(),
    val mapping: TransactionCsvColumnMapping = TransactionCsvColumnMapping(),
    val importedTransactions: List<TransactionEntity>,
    val skippedRows: Int,
    val totalRows: Int,
    val duplicateRows: Int = 0,
)

internal data class TransactionCsvColumnMapping(
    val dateIndex: Int? = null,
    val descriptionIndex: Int? = null,
    val amountIndex: Int? = null,
    val debitIndex: Int? = null,
    val creditIndex: Int? = null,
)

@Composable
internal fun CsvImportSheetContent(
    preview: CsvImportPreview,
    onDismiss: () -> Unit,
    onImport: (CsvImportPreview) -> Unit,
) {
    var dateIndex by remember(preview.mapping.dateIndex) { mutableStateOf(preview.mapping.dateIndex) }
    var descriptionIndex by remember(preview.mapping.descriptionIndex) { mutableStateOf(preview.mapping.descriptionIndex) }
    var amountIndex by remember(preview.mapping.amountIndex) { mutableStateOf(preview.mapping.amountIndex) }
    var debitIndex by remember(preview.mapping.debitIndex) { mutableStateOf(preview.mapping.debitIndex) }
    var creditIndex by remember(preview.mapping.creditIndex) { mutableStateOf(preview.mapping.creditIndex) }

    val mapping = TransactionCsvColumnMapping(
        dateIndex = dateIndex,
        descriptionIndex = descriptionIndex,
        amountIndex = amountIndex,
        debitIndex = debitIndex,
        creditIndex = creditIndex,
    )
    val activePreview = remember(
        preview.csvText,
        preview.sourceName,
        mapping,
    ) {
        if (preview.csvText.isBlank()) preview else parseTransactionCsv(
            csvText = preview.csvText,
            sourceName = preview.sourceName,
            mapping = mapping
        )
    }
    val totalImportedCents = activePreview.importedTransactions.sumOf { it.amount_cents }
    val sampleRows = activePreview.importedTransactions.take(3)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Import CSV",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Ready to import ${activePreview.importedTransactions.size} transactions from ${preview.sourceName}.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "${activePreview.totalRows} rows scanned, ${activePreview.skippedRows} skipped.",
            style = MaterialTheme.typography.bodyMedium,
            color = GlassTokens.TextSecondary
        )
        if (activePreview.duplicateRows > 0) {
            Text(
                text = "${activePreview.duplicateRows} duplicate rows skipped.",
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTokens.TextSecondary
            )
        }
        Text(
            text = "Map the columns below if your bank export uses different headers.",
            style = MaterialTheme.typography.bodySmall,
            color = GlassTokens.TextDim
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CsvMappingDropdown(
                label = "Date column",
                headers = preview.headers,
                selectedIndex = dateIndex,
                onSelectedIndexChange = { dateIndex = it }
            )
            CsvMappingDropdown(
                label = "Description column",
                headers = preview.headers,
                selectedIndex = descriptionIndex,
                onSelectedIndexChange = { descriptionIndex = it }
            )
            CsvMappingDropdown(
                label = "Amount column",
                headers = preview.headers,
                selectedIndex = amountIndex,
                onSelectedIndexChange = { amountIndex = it }
            )
            CsvMappingDropdown(
                label = "Debit column",
                headers = preview.headers,
                selectedIndex = debitIndex,
                onSelectedIndexChange = { debitIndex = it }
            )
            CsvMappingDropdown(
                label = "Credit column",
                headers = preview.headers,
                selectedIndex = creditIndex,
                onSelectedIndexChange = { creditIndex = it }
            )
        }
        Text(
            text = "Sample rows:",
            style = MaterialTheme.typography.labelLarge,
            color = GlassTokens.TextSecondary
        )
        sampleRows.forEach { row ->
            Text(
                text = "${row.date.formatDateDisplay()} • ${row.description} • ${centsToDisplay(row.amount_cents)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (activePreview.importedTransactions.size > sampleRows.size) {
            Text(
                text = "…and ${activePreview.importedTransactions.size - sampleRows.size} more.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTokens.TextDim
            )
        }
        Text(
            text = "Total imported: ${centsToDisplay(totalImportedCents)}",
            style = MaterialTheme.typography.bodyMedium,
            color = GlassTokens.TextSecondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(onClick = { onImport(activePreview) }, modifier = Modifier.weight(1f)) {
                Text("Import")
            }
        }
    }
}

internal data class BillCsvImportPreview(
    val sourceName: String,
    val csvText: String = "",
    val headers: List<String> = emptyList(),
    val mapping: BillCsvColumnMapping = BillCsvColumnMapping(),
    val importedPayments: List<PaymentEntity>,
    val skippedRows: Int,
    val totalRows: Int,
    val duplicateRows: Int = 0,
)

internal data class BillCsvColumnMapping(
    val nameIndex: Int? = null,
    val amountIndex: Int? = null,
    val frequencyIndex: Int? = null,
    val dueDateIndex: Int? = null,
    val dueDayIndex: Int? = null,
    val autoIndex: Int? = null,
)

@Composable
internal fun BillCsvImportSheetContent(
    preview: BillCsvImportPreview,
    onDismiss: () -> Unit,
    onImport: (BillCsvImportPreview) -> Unit,
) {
    var nameIndex by remember(preview.mapping.nameIndex) { mutableStateOf(preview.mapping.nameIndex) }
    var amountIndex by remember(preview.mapping.amountIndex) { mutableStateOf(preview.mapping.amountIndex) }
    var frequencyIndex by remember(preview.mapping.frequencyIndex) { mutableStateOf(preview.mapping.frequencyIndex) }
    var dueDateIndex by remember(preview.mapping.dueDateIndex) { mutableStateOf(preview.mapping.dueDateIndex) }
    var dueDayIndex by remember(preview.mapping.dueDayIndex) { mutableStateOf(preview.mapping.dueDayIndex) }
    var autoIndex by remember(preview.mapping.autoIndex) { mutableStateOf(preview.mapping.autoIndex) }

    val mapping = BillCsvColumnMapping(
        nameIndex = nameIndex,
        amountIndex = amountIndex,
        frequencyIndex = frequencyIndex,
        dueDateIndex = dueDateIndex,
        dueDayIndex = dueDayIndex,
        autoIndex = autoIndex,
    )
    val activePreview = remember(
        preview.csvText,
        preview.sourceName,
        mapping,
    ) {
        if (preview.csvText.isBlank()) preview else parseBillCsv(
            csvText = preview.csvText,
            sourceName = preview.sourceName,
            mapping = mapping
        )
    }
    val totalImportedCents = activePreview.importedPayments.sumOf { it.amount_cents }
    val sampleRows = activePreview.importedPayments.take(3)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Import bills CSV",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Ready to import ${activePreview.importedPayments.size} bills from ${preview.sourceName}.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "${activePreview.totalRows} rows scanned, ${activePreview.skippedRows} skipped.",
            style = MaterialTheme.typography.bodyMedium,
            color = GlassTokens.TextSecondary
        )
        if (activePreview.duplicateRows > 0) {
            Text(
                text = "${activePreview.duplicateRows} duplicate rows skipped.",
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTokens.TextSecondary
            )
        }
        Text(
            text = "Map the columns below if your bill export uses different headers.",
            style = MaterialTheme.typography.bodySmall,
            color = GlassTokens.TextDim
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CsvMappingDropdown(
                label = "Bill name column",
                headers = preview.headers,
                selectedIndex = nameIndex,
                onSelectedIndexChange = { nameIndex = it }
            )
            CsvMappingDropdown(
                label = "Amount column",
                headers = preview.headers,
                selectedIndex = amountIndex,
                onSelectedIndexChange = { amountIndex = it }
            )
            CsvMappingDropdown(
                label = "Frequency column",
                headers = preview.headers,
                selectedIndex = frequencyIndex,
                onSelectedIndexChange = { frequencyIndex = it }
            )
            CsvMappingDropdown(
                label = "Due date column",
                headers = preview.headers,
                selectedIndex = dueDateIndex,
                onSelectedIndexChange = { dueDateIndex = it }
            )
            CsvMappingDropdown(
                label = "Due day column",
                headers = preview.headers,
                selectedIndex = dueDayIndex,
                onSelectedIndexChange = { dueDayIndex = it }
            )
            CsvMappingDropdown(
                label = "Auto-pay column",
                headers = preview.headers,
                selectedIndex = autoIndex,
                onSelectedIndexChange = { autoIndex = it }
            )
        }
        Text(
            text = "Sample bills:",
            style = MaterialTheme.typography.labelLarge,
            color = GlassTokens.TextSecondary
        )
        sampleRows.forEach { row ->
            Text(
                text = "${row.name} • ${centsToDisplay(row.amount_cents)} • ${
                    PaymentSchedule.recurrenceSummary(
                        recurrence = row.frequency,
                        dayOfMonth = row.day_of_month,
                        nextDate = row.next_date
                    )
                } • ${row.next_date.formatDateDisplay()}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (activePreview.importedPayments.size > sampleRows.size) {
            Text(
                text = "…and ${activePreview.importedPayments.size - sampleRows.size} more.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTokens.TextDim
            )
        }
        Text(
            text = "Total imported: ${centsToDisplay(totalImportedCents)}",
            style = MaterialTheme.typography.bodyMedium,
            color = GlassTokens.TextSecondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(onClick = { onImport(activePreview) }, modifier = Modifier.weight(1f)) {
                Text("Import")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CsvMappingDropdown(
    label: String,
    headers: List<String>,
    selectedIndex: Int?,
    onSelectedIndexChange: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = selectedIndex?.let { headers.getOrNull(it) } ?: "Auto-detect"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Auto-detect") },
                onClick = {
                    onSelectedIndexChange(null)
                    expanded = false
                }
            )
            headers.forEachIndexed { index, header ->
                DropdownMenuItem(
                    text = { Text(header.ifBlank { "Column ${index + 1}" }) },
                    onClick = {
                        onSelectedIndexChange(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

internal fun parseTransactionCsv(
    csvText: String,
    sourceName: String = "CSV file",
    mapping: TransactionCsvColumnMapping? = null,
): CsvImportPreview {
    val lines = csvText
        .lineSequence()
        .map { it.trimEnd('\r') }
        .filter { it.isNotBlank() }
        .toList()

    require(lines.isNotEmpty()) { "CSV file is empty." }

    val headers = parseCsvLine(lines.first())
    val normalizedHeaders = headers.map { normalizeHeader(it) }

    val detectedMapping = detectTransactionCsvColumnMapping(normalizedHeaders)
    val effectiveMapping = TransactionCsvColumnMapping(
        dateIndex = mapping?.dateIndex ?: detectedMapping.dateIndex,
        descriptionIndex = mapping?.descriptionIndex ?: detectedMapping.descriptionIndex,
        amountIndex = mapping?.amountIndex ?: detectedMapping.amountIndex,
        debitIndex = mapping?.debitIndex ?: detectedMapping.debitIndex,
        creditIndex = mapping?.creditIndex ?: detectedMapping.creditIndex,
    )

    require(effectiveMapping.dateIndex != null) { "CSV must include a date column." }
    require(effectiveMapping.descriptionIndex != null) { "CSV must include a description column." }
    require(effectiveMapping.amountIndex != null || effectiveMapping.debitIndex != null || effectiveMapping.creditIndex != null) {
        "CSV must include an amount, debit, or credit column."
    }
    val dateIndex = requireNotNull(effectiveMapping.dateIndex)
    val descriptionIndex = requireNotNull(effectiveMapping.descriptionIndex)
    val amountIndex = effectiveMapping.amountIndex
    val debitIndex = effectiveMapping.debitIndex
    val creditIndex = effectiveMapping.creditIndex

    val transactions = mutableListOf<TransactionEntity>()
    var skippedRows = 0
    var duplicateRows = 0
    val seenRows = mutableSetOf<String>()

    lines.drop(1).forEach { line -> 
        val columns = parseCsvLine(line)
        if (columns.all { it.isBlank() }) return@forEach

        val date = columns.getOrNull(dateIndex)?.let { parseDateOrNull(it) }
        val description = columns.getOrNull(descriptionIndex)?.trim().orEmpty()
        val amountCents = resolveAmountCents(
            columns = columns,
            amountIndex = amountIndex,
            debitIndex = debitIndex,
            creditIndex = creditIndex,
        )

        if (date == null || description.isBlank() || amountCents == null || amountCents == 0L) {
            skippedRows++
            return@forEach
        }

        val normalizedKey = listOf(
            date.toString(),
            description.lowercase(Locale.US).trim(),
            amountCents.toString(),
            if (amountCents < 0) "expense" else "income",
        ).joinToString("|")
        if (!seenRows.add(normalizedKey)) {
            duplicateRows++
            return@forEach
        }

        transactions += TransactionEntity(
            description = description,
            amount_cents = amountCents,
            date = date.toString(),
            type = if (amountCents < 0) "expense" else "income",
        )
    }

    require(transactions.isNotEmpty()) { "No importable transactions were found." }

    return CsvImportPreview(
        sourceName = sourceName,
        csvText = csvText,
        headers = headers,
        mapping = effectiveMapping,
        importedTransactions = transactions,
        skippedRows = skippedRows,
        totalRows = lines.size - 1,
        duplicateRows = duplicateRows,
    )
}

private fun detectTransactionCsvColumnMapping(headers: List<String>): TransactionCsvColumnMapping {
    return TransactionCsvColumnMapping(
        dateIndex = findHeaderIndex(headers, DATE_HEADERS),
        descriptionIndex = findHeaderIndex(headers, DESCRIPTION_HEADERS),
        amountIndex = findHeaderIndex(headers, AMOUNT_HEADERS),
        debitIndex = findHeaderIndex(headers, DEBIT_HEADERS),
        creditIndex = findHeaderIndex(headers, CREDIT_HEADERS),
    )
}

internal fun parseBillCsv(
    csvText: String,
    sourceName: String = "CSV file",
    mapping: BillCsvColumnMapping? = null,
    today: LocalDate = LocalDate.now(),
): BillCsvImportPreview {
    val lines = csvText
        .lineSequence()
        .map { it.trimEnd('\r') }
        .filter { it.isNotBlank() }
        .toList()

    require(lines.isNotEmpty()) { "CSV file is empty." }

    val headers = parseCsvLine(lines.first())
    val normalizedHeaders = headers.map { normalizeHeader(it) }
    val detectedMapping = detectBillCsvColumnMapping(normalizedHeaders)
    val effectiveMapping = BillCsvColumnMapping(
        nameIndex = mapping?.nameIndex ?: detectedMapping.nameIndex,
        amountIndex = mapping?.amountIndex ?: detectedMapping.amountIndex,
        frequencyIndex = mapping?.frequencyIndex ?: detectedMapping.frequencyIndex,
        dueDateIndex = mapping?.dueDateIndex ?: detectedMapping.dueDateIndex,
        dueDayIndex = mapping?.dueDayIndex ?: detectedMapping.dueDayIndex,
        autoIndex = mapping?.autoIndex ?: detectedMapping.autoIndex,
    )

    require(effectiveMapping.nameIndex != null) { "CSV must include a bill name column." }
    require(effectiveMapping.amountIndex != null) { "CSV must include an amount column." }
    val resolvedNameIndex = requireNotNull(effectiveMapping.nameIndex)
    val resolvedAmountIndex = requireNotNull(effectiveMapping.amountIndex)
    val frequencyIndex = effectiveMapping.frequencyIndex
    val dueDateIndex = effectiveMapping.dueDateIndex
    val dueDayIndex = effectiveMapping.dueDayIndex
    val autoIndex = effectiveMapping.autoIndex

    val payments = mutableListOf<PaymentEntity>()
    var skippedRows = 0
    var duplicateRows = 0
    val seenRows = mutableSetOf<String>()

    lines.drop(1).forEach { line ->
        val columns = parseCsvLine(line)
        if (columns.all { it.isBlank() }) return@forEach

        val name = columns.getOrNull(resolvedNameIndex)?.trim().orEmpty()
        val amountCents = columns.getOrNull(resolvedAmountIndex)?.let { parseMoneyCents(it)?.let { cents -> abs(cents) } }
        val frequency = parseBillFrequency(frequencyIndex?.let { columns.getOrNull(it) })
        val normalizedFrequency = normalizeFrequencyKey(frequency)
        val dueDay = dueDayIndex?.let { columns.getOrNull(it) }?.let { parseIntOrNull(it) }
        val dueDateText = dueDateIndex?.let { columns.getOrNull(it) }?.trim().orEmpty()
        val dueDate = parseDateOrNull(dueDateText)
        val autoWithdraw = autoIndex?.let { columns.getOrNull(it) }?.let { parseBooleanFlag(it) } == true
        val resolvedDueDay = dueDay ?: dueDate?.dayOfMonth
        val nextDate = when (normalizedFrequency) {
            "monthly" -> resolvedDueDay?.let {
                PaymentSchedule.resolveNextPaymentDate(
                    today = today,
                    recurrence = frequency,
                    dueDay = it,
                    dueDate = dueDate,
                )
            }
            else -> dueDate?.let {
                PaymentSchedule.resolveNextPaymentDate(
                    today = today,
                    recurrence = frequency,
                    dueDay = resolvedDueDay,
                    dueDate = it,
                )
            }
        }

        if (name.isBlank() || amountCents == null || amountCents <= 0 || nextDate.isNullOrBlank()) {
            skippedRows++
            return@forEach
        }

        val canonicalDayOfMonth = when {
            normalizedFrequency == "monthly" -> resolvedDueDay
            else -> null
        }

        val normalizedKey = listOf(
            name.lowercase(Locale.US).trim(),
            amountCents.toString(),
            frequency.lowercase(Locale.US).trim(),
            canonicalDayOfMonth?.toString().orEmpty(),
            nextDate,
            autoWithdraw.toString(),
        ).joinToString("|")
        if (!seenRows.add(normalizedKey)) {
            duplicateRows++
            return@forEach
        }

        payments += PaymentEntity(
            name = name,
            amount_cents = amountCents,
            frequency = frequency,
            day_of_month = canonicalDayOfMonth,
            next_date = nextDate,
            is_active = 1,
            isAutoWithdraw = autoWithdraw,
        )
    }

    require(payments.isNotEmpty()) { "No importable bills were found." }

    return BillCsvImportPreview(
        sourceName = sourceName,
        csvText = csvText,
        headers = headers,
        mapping = effectiveMapping,
        importedPayments = payments,
        skippedRows = skippedRows,
        totalRows = lines.size - 1,
        duplicateRows = duplicateRows,
    )
}

private fun detectBillCsvColumnMapping(headers: List<String>): BillCsvColumnMapping {
    return BillCsvColumnMapping(
        nameIndex = findHeaderIndex(headers, BILL_NAME_HEADERS),
        amountIndex = findHeaderIndex(headers, BILL_AMOUNT_HEADERS),
        frequencyIndex = findHeaderIndex(headers, BILL_FREQUENCY_HEADERS),
        dueDateIndex = findHeaderIndex(headers, BILL_DUE_DATE_HEADERS),
        dueDayIndex = findHeaderIndex(headers, BILL_DUE_DAY_HEADERS),
        autoIndex = findHeaderIndex(headers, BILL_AUTO_HEADERS),
    )
}

private fun resolveAmountCents(
    columns: List<String>,
    amountIndex: Int?,
    debitIndex: Int?,
    creditIndex: Int?,
): Long? {
    val amountRaw = amountIndex?.let { columns.getOrNull(it) }
    val debitRaw = debitIndex?.let { columns.getOrNull(it) }
    val creditRaw = creditIndex?.let { columns.getOrNull(it) }

    amountRaw?.let { raw ->
        val amount = parseMoneyCents(raw)
        val hasExplicitSign = raw.contains('-') || raw.contains('(') || raw.contains(')')
        if (amount != null && (hasExplicitSign || (debitRaw.isNullOrBlank() && creditRaw.isNullOrBlank()))) {
            return amount
        }
    }

    debitRaw?.let { raw ->
        parseMoneyCents(raw)?.let { return -abs(it) }
    }

    creditRaw?.let { raw ->
        parseMoneyCents(raw)?.let { return abs(it) }
    }

    return null
}

private fun parseMoneyCents(raw: String?): Long? {
    val cleaned = raw?.trim().orEmpty()
        .replace("$", "")
        .replace("€", "")
        .replace("£", "")
        .replace(",", "")
        .replace(" ", "")

    if (cleaned.isBlank()) return null

    val normalized = if (cleaned.startsWith("(") && cleaned.endsWith(")")) {
        "-${cleaned.substring(1, cleaned.length - 1)}"
    } else {
        cleaned
    }

    val value = runCatching { BigDecimal(normalized) }.getOrNull() ?: return null
    return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
}

private fun parseIntOrNull(raw: String?): Int? {
    val cleaned = raw?.trim().orEmpty()
    if (cleaned.isBlank()) return null
    return runCatching {
        cleaned.replace(Regex("[^0-9-]"), "").toInt()
    }.getOrNull()
}

private fun parseBooleanFlag(raw: String?): Boolean {
    val normalized = raw?.trim()?.lowercase(Locale.US).orEmpty()
    return normalized in setOf("true", "yes", "y", "1", "on", "auto", "autopay", "auto-pay")
}

private fun parseBillFrequency(raw: String?): String {
    val normalized = raw?.trim().orEmpty().lowercase(Locale.US).replace(Regex("[^a-z]+"), "")
    return when (normalized) {
        "", "monthly", "month" -> "Monthly"
        "weekly", "everyweek" -> "Weekly"
        "biweekly", "fortnightly" -> "Bi-weekly"
        "semimonthly", "twicemonthly", "twicemonth" -> "Semi-monthly"
        "bimonthly", "everytwomonths" -> "Bi-monthly"
        "quarterly", "everythreemonths" -> "Quarterly"
        "yearly", "annually", "annual" -> "Yearly"
        "onetime", "once", "oneoff", "single" -> "One-time"
        else -> raw?.trim().orEmpty().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    }
}

private fun normalizeFrequencyKey(frequency: String): String {
    return frequency.lowercase(Locale.US).replace(" ", "").replace("-", "")
}

private fun parseDateOrNull(raw: String): LocalDate? {
    val value = raw.trim()
        .substringBefore('T')
        .substringBefore(' ')

    if (value.isBlank()) return null

    for (formatter in DATE_FORMATTERS) {
        try {
            return LocalDate.parse(value, formatter)
        } catch (_: DateTimeParseException) {
            continue
        }
    }

    return null
}

private fun parseCsvLine(line: String): List<String> {
    val values = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var index = 0

    while (index < line.length) {
        val character = line[index]
        when (character) {
            '"' -> {
                if (inQuotes && index + 1 < line.length && line[index + 1] == '"') {
                    current.append('"')
                    index++
                } else {
                    inQuotes = !inQuotes
                }
            }
            ',' -> {
                if (inQuotes) {
                    current.append(character)
                } else {
                    values += current.toString().trim()
                    current.clear()
                }
            }
            else -> current.append(character)
        }
        index++
    }

    values += current.toString().trim()
    return values
}

private fun normalizeHeader(value: String): String {
    return value.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "")
}

private fun findHeaderIndex(headers: List<String>, candidates: Set<String>): Int? {
    headers.forEachIndexed { index, header ->
        if (header in candidates) return index
    }
    return null
}

private val DATE_HEADERS = setOf(
    "date",
    "transactiondate",
    "posteddate",
    "transdate",
    "entrydate",
)

private val DESCRIPTION_HEADERS = setOf(
    "description",
    "details",
    "memo",
    "merchant",
    "payee",
    "name",
    "transactiondescription",
    "postingdescription",
)

private val AMOUNT_HEADERS = setOf(
    "amount",
    "transactionamount",
    "amt",
    "value",
)

private val DEBIT_HEADERS = setOf(
    "debit",
    "withdrawal",
    "outflow",
    "moneyout",
)

private val CREDIT_HEADERS = setOf(
    "credit",
    "deposit",
    "inflow",
    "moneyin",
)

private val BILL_NAME_HEADERS = setOf(
    "name",
    "bill",
    "payee",
    "merchant",
    "description",
    "service",
    "subscription",
)

private val BILL_AMOUNT_HEADERS = setOf(
    "amount",
    "billamount",
    "paymentamount",
    "charge",
    "dueamount",
    "value",
)

private val BILL_FREQUENCY_HEADERS = setOf(
    "frequency",
    "recurrence",
    "cadence",
    "interval",
    "schedule",
)

private val BILL_DUE_DATE_HEADERS = setOf(
    "duedate",
    "nextdue",
    "nextdate",
    "nextpayment",
    "billingdate",
)

private val BILL_DUE_DAY_HEADERS = setOf(
    "dueday",
    "dayofmonth",
    "billingday",
    "paymentday",
)

private val BILL_AUTO_HEADERS = setOf(
    "autopay",
    "autowithdraw",
    "autodraft",
    "auto",
)

private val DATE_FORMATTERS = listOf(
    DateTimeFormatter.ofPattern("uuuu-MM-dd"),
    DateTimeFormatter.ofPattern("uuuu/M/d"),
    DateTimeFormatter.ofPattern("M/d/uuuu"),
    DateTimeFormatter.ofPattern("MM/dd/uuuu"),
    DateTimeFormatter.ofPattern("M/d/yy"),
    DateTimeFormatter.ofPattern("MM/dd/yy"),
    DateTimeFormatter.ofPattern("M-d-uuuu"),
    DateTimeFormatter.ofPattern("MM-dd-uuuu"),
)

