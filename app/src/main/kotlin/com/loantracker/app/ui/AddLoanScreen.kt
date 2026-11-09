package com.loantracker.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loantracker.app.data.Loan
import com.loantracker.app.data.PaymentInterval
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private val DISPLAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

private const val AMOUNT_STEP = 500
private const val AMOUNT_MIN = 500
private const val AMOUNT_SLIDER_MAX = 100_000
private const val AMOUNT_HARD_MAX = 9_999_999
private const val RATE_STEP = 5
private const val RATE_MAX = 50

private val AMOUNT_QUICK = listOf(1_000, 2_000, 3_000, 5_000, 10_000, 20_000, 50_000)
private val RATE_QUICK = listOf(0, 5, 10, 15, 20, 25, 30)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLoanScreen(vm: LoanViewModel, onBack: () -> Unit) {
    val allLoans by vm.allLoans.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    var title by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf(5_000) }
    var amountText by rememberSaveable { mutableStateOf("5000") }
    var rate by rememberSaveable { mutableStateOf(0) }
    var startEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var keyword by rememberSaveable { mutableStateOf("001709") }  // constant account number
    var lenderName by rememberSaveable { mutableStateOf("") }
    var lenderPhone by rememberSaveable { mutableStateOf("") }
    var lenderEmail by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var showMore by rememberSaveable { mutableStateOf(false) }
    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var repaidTouched by rememberSaveable { mutableStateOf(false) }
    var repaidChoice by rememberSaveable { mutableStateOf(false) }
    var repaidEpoch by rememberSaveable { mutableStateOf(0L) }  // 0 = default date
    var showRepaidPicker by rememberSaveable { mutableStateOf(false) }
    var lenderMenuOpen by remember { mutableStateOf(false) }

    // A loan is always one month: only the start date is chosen.
    val today = remember { LocalDate.now() }
    val startDate = LocalDate.ofEpochDay(startEpoch)
    val dueDate = startDate.plusMonths(1)
    val durationDays = ChronoUnit.DAYS.between(startDate, dueDate) + 1

    // Past loans: if the month is already over, assume it was repaid (the user can switch it off).
    val isPast = startDate.isBefore(today)
    val hasEnded = dueDate.isBefore(today)
    val markRepaid = isPast && (if (repaidTouched) repaidChoice else hasEnded)
    val repaidDefault = if (hasEnded) dueDate else today
    val repaidDate = if (repaidEpoch == 0L) repaidDefault
        else LocalDate.ofEpochDay(repaidEpoch).let {
            when {
                it.isBefore(startDate) -> startDate
                it.isAfter(today) -> today
                else -> it
            }
        }

    val interestAmount = amount * rate / 100.0
    val totalPayable = amount + interestAmount

    // Previously used lenders (latest loan per lender) for quick pick + autofill.
    val knownLenders = remember(allLoans) {
        allLoans
            .filter { !it.lenderName.isNullOrBlank() }
            .sortedByDescending { it.startDate }
            .distinctBy { it.lenderName!!.trim().lowercase() }
            .associateBy { it.lenderName!!.trim() }
    }

    fun setAmount(value: Int) {
        val v = value.coerceIn(0, AMOUNT_HARD_MAX)
        amount = v
        amountText = if (v == 0) "" else v.toString()
    }

    val canSave = title.isNotBlank() && amount > 0

    fun save() {
        if (!canSave) return
        val loan = Loan(
            id = LoanViewModel.newId(),
            title = title.trim(),
            principal = amount.toDouble(),
            interestRate = rate.toDouble(),
            startDate = startDate,
            dueDate = dueDate,
            expectedPerInterval = 0.0,
            interval = PaymentInterval.WEEKLY,
            keyword = keyword.trim().ifBlank { null },
            lenderName = lenderName.trim().ifBlank { null },
            lenderPhone = lenderPhone.trim().ifBlank { null },
            lenderEmail = lenderEmail.trim().ifBlank { null },
            notes = notes.trim().ifBlank { null }
        )
        if (markRepaid) vm.saveLoanRepaid(loan, repaidDate) else vm.saveLoan(loan)
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text("New loan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.navigationBarsPadding().imePadding()
            ) {
                Button(
                    onClick = { save() },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp)
                ) {
                    Text(if (markRepaid) "Save as repaid" else "Save loan", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Loan title *") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // ---- Amount
            FormSection("Amount") {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }.take(7)
                        amountText = digits
                        amount = digits.toIntOrNull() ?: 0
                    },
                    prefix = { Text("KSh ") },
                    textStyle = MaterialTheme.typography.titleLarge,
                    singleLine = true,
                    isError = amount <= 0,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth()
                )
                SliderStepper(
                    value = amount.coerceIn(AMOUNT_MIN, AMOUNT_SLIDER_MAX).toFloat(),
                    range = AMOUNT_MIN.toFloat()..AMOUNT_SLIDER_MAX.toFloat(),
                    onValueChange = { setAmount((it / AMOUNT_STEP).roundToInt() * AMOUNT_STEP) },
                    onMinus = {
                        val t = if (amount % AMOUNT_STEP == 0) amount - AMOUNT_STEP
                                else (amount / AMOUNT_STEP) * AMOUNT_STEP
                        setAmount(t.coerceAtLeast(AMOUNT_MIN))
                    },
                    onPlus = { setAmount((amount / AMOUNT_STEP + 1) * AMOUNT_STEP) },
                    minusDesc = "Decrease amount by 500",
                    plusDesc = "Increase amount by 500"
                )
                ChipRow(
                    options = AMOUNT_QUICK,
                    selected = amount,
                    label = { "%,d".format(it) },
                    onSelect = { setAmount(it) }
                )
            }

            // ---- Interest
            FormSection(
                title = "Interest rate",
                trailing = {
                    Text(
                        "$rate%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                SliderStepper(
                    value = rate.toFloat(),
                    range = 0f..RATE_MAX.toFloat(),
                    steps = RATE_MAX / RATE_STEP - 1,
                    onValueChange = { rate = ((it / RATE_STEP).roundToInt() * RATE_STEP).coerceIn(0, RATE_MAX) },
                    onMinus = { rate = (rate - RATE_STEP).coerceAtLeast(0) },
                    onPlus = { rate = (rate + RATE_STEP).coerceAtMost(RATE_MAX) },
                    minusDesc = "Decrease interest by 5 percent",
                    plusDesc = "Increase interest by 5 percent"
                )
                ChipRow(
                    options = RATE_QUICK,
                    selected = rate,
                    label = { "$it%" },
                    onSelect = { rate = it }
                )
            }

            // ---- Start date (due date is always one month later)
            FormSection("Start date") {
                val isToday = startDate == today
                val isYesterday = startDate == today.minusDays(1)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isToday,
                        onClick = { startEpoch = today.toEpochDay() },
                        label = { Text("Today") }
                    )
                    FilterChip(
                        selected = isYesterday,
                        onClick = { startEpoch = today.minusDays(1).toEpochDay() },
                        label = { Text("Yesterday") }
                    )
                    FilterChip(
                        selected = !isToday && !isYesterday,
                        onClick = { showStartPicker = true },
                        label = {
                            Text(
                                if (!isToday && !isYesterday) startDate.format(DISPLAY_FMT)
                                else "Pick date"
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
                Text(
                    "Due ${dueDate.format(DISPLAY_FMT)} · 1 month ($durationDays days)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---- Past loan: record that it was already repaid
            if (isPast) {
                FormSection("Past loan") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Already fully repaid",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (markRepaid) "Saved as completed and added to your history."
                                else if (hasEnded) "Kept as an overdue loan until you record payments."
                                else "Stays active so you can record payments.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = markRepaid,
                            onCheckedChange = { repaidTouched = true; repaidChoice = it }
                        )
                    }
                    if (markRepaid) {
                        Text(
                            "Repaid on",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = repaidEpoch == 0L,
                                onClick = { repaidEpoch = 0L },
                                label = { Text(repaidDefault.format(DISPLAY_FMT)) }
                            )
                            FilterChip(
                                selected = repaidEpoch != 0L,
                                onClick = { showRepaidPicker = true },
                                label = {
                                    Text(
                                        if (repaidEpoch != 0L) repaidDate.format(DISPLAY_FMT)
                                        else "Pick date"
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.CalendarMonth,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // ---- Summary
            if (amount > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "To repay",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                        Text(
                            formatMoney(totalPayable),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${formatMoney(amount.toDouble())} + ${formatMoney(interestAmount)} interest",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // ---- Lender + M-Pesa account
            FormSection("Lender") {
                if (knownLenders.isEmpty()) {
                    OutlinedTextField(
                        value = lenderName,
                        onValueChange = { lenderName = it },
                        label = { Text("Lender name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    val matches = knownLenders.entries.filter {
                        it.key.contains(lenderName.trim(), ignoreCase = true) &&
                            !it.key.equals(lenderName.trim(), ignoreCase = true)
                    }
                    ExposedDropdownMenuBox(
                        expanded = lenderMenuOpen && matches.isNotEmpty(),
                        onExpandedChange = { lenderMenuOpen = it }
                    ) {
                        OutlinedTextField(
                            value = lenderName,
                            onValueChange = { lenderName = it; lenderMenuOpen = true },
                            label = { Text("Lender name") },
                            singleLine = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(
                                    expanded = lenderMenuOpen && matches.isNotEmpty()
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = lenderMenuOpen && matches.isNotEmpty(),
                            onDismissRequest = { lenderMenuOpen = false }
                        ) {
                            matches.forEach { (name, source) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        lenderName = name
                                        lenderPhone = source.lenderPhone.orEmpty()
                                        lenderEmail = source.lenderEmail.orEmpty()
                                        source.keyword?.let { keyword = it }
                                        if (title.isBlank()) title = name
                                        lenderMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("M-Pesa account number") },
                    supportingText = { Text("Matches SMS payments to this loan") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                TextButton(onClick = { showMore = !showMore }) {
                    Icon(
                        if (showMore) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (showMore) "Hide contact & notes" else "Add contact & notes (optional)")
                }
                if (showMore) {
                    OutlinedTextField(
                        value = lenderPhone,
                        onValueChange = { lenderPhone = it },
                        label = { Text("Lender phone") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = lenderEmail,
                        onValueChange = { lenderEmail = it },
                        label = { Text("Lender email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showRepaidPicker) {
        DatePickerModal(
            initialDate = repaidDate,
            onConfirm = { repaidEpoch = it.toEpochDay(); showRepaidPicker = false },
            onDismiss = { showRepaidPicker = false }
        )
    }
    if (showStartPicker) {
        DatePickerModal(
            initialDate = startDate,
            onConfirm = { startEpoch = it.toEpochDay(); showStartPicker = false },
            onDismiss = { showStartPicker = false }
        )
    }
}

@Composable
private fun FormSection(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                trailing?.invoke()
            }
            content()
        }
    }
}

/** − [ slider ] +  — roll the slider or tap the buttons. */
@Composable
private fun SliderStepper(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusDesc: String,
    plusDesc: String,
    steps: Int = 0
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onMinus) {
            Icon(Icons.Filled.Remove, contentDescription = minusDesc)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        )
        FilledTonalIconButton(onClick = onPlus) {
            Icon(Icons.Filled.Add, contentDescription = plusDesc)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onConfirm(Instant.ofEpochMilli(it)
                        .atZone(ZoneId.systemDefault()).toLocalDate())
                } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) { DatePicker(state = state) }
}
