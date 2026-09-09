package com.college.library.ui.screens.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.library.ui.theme.CardBlue
import com.college.library.ui.theme.CardGreen
import com.college.library.ui.theme.CardOrange
import com.college.library.ui.theme.CardPurple
import com.college.library.ui.theme.DangerRed
import com.college.library.ui.theme.Gold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared building blocks for the operations screens (gate log, acquisitions,
 * transfers, serials, ILL, enterprise).
 *
 * These six screens are structurally the same — a filtered list of records with
 * an add dialog and a status workflow — so the chrome lives here once rather
 * than being copy-pasted six times.
 */

private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dateTimeFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

fun formatDate(epochMillis: Long): String =
    if (epochMillis <= 0) "—" else dateFmt.format(Date(epochMillis))

fun formatTime(epochMillis: Long): String =
    if (epochMillis <= 0) "—" else timeFmt.format(Date(epochMillis))

fun formatDateTime(epochMillis: Long): String =
    if (epochMillis <= 0) "—" else dateTimeFmt.format(Date(epochMillis))

/** Today as "YYYY-MM-DD", the format the dateStr fields use. */
fun todayStamp(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

/** Consistent colour per workflow status, shared by every operations screen. */
fun statusColor(status: String): Color = when (status.lowercase()) {
    "pending", "requested", "underreview" -> CardOrange
    "approved", "ordered" -> CardBlue
    "shipped", "dispatched" -> CardPurple
    "received", "fulfilled", "active", "returned" -> CardGreen
    "cancelled", "rejected", "declined", "lapsed" -> DangerRed
    else -> Color.Gray
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationsScaffold(
    title: String,
    subtitle: String? = null,
    onNavigateBack: () -> Unit,
    onAdd: (() -> Unit)? = null,
    addLabel: String = "Add",
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (subtitle != null) {
                            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Gold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = actions,
            )
        },
        floatingActionButton = {
            if (onAdd != null) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Text(addLabel, fontWeight = FontWeight.Bold) }
            }
        },
        content = content,
    )
}

@Composable
fun StatusChip(status: String, modifier: Modifier = Modifier) {
    val color = statusColor(status)
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            status.replaceFirstChar { it.uppercase() },
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Small headline figure used in the summary rows above each list. */
@Composable
fun StatTile(label: String, value: String, color: Color = Gold, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                label.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

@Composable
fun EmptyState(icon: String, message: String, hint: String? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 44.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            message,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                hint,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
    }
}

/** Labelled text field used throughout the add/edit dialogs. */
@Composable
fun DialogField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    numeric: Boolean = false,
    singleLine: Boolean = true,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = singleLine,
        keyboardOptions = if (numeric) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

/** Dropdown for the fixed status/frequency vocabularies. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogDropdown(
    label: String,
    options: List<String>,
    selected: String,
    // `modifier` comes BEFORE `onSelect` so that onSelect is the last parameter
    // and every call site's trailing lambda binds to it. With the two the other
    // way round the trailing lambda binds to `modifier` instead, and the
    // compiler reports "No value passed for parameter 'onSelect'" — which was
    // breaking all three call sites.
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontSize = 12.sp) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Confirmation-free status advance menu.
 *
 * Tapping a record's status chip offers the other statuses. Used by
 * acquisitions, transfers and ILL, which all have the same shape of workflow.
 */
@Composable
fun StatusMenu(
    current: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier.clickable { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusChip(current)
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = "Change status",
                tint = statusColor(current),
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.filter { it != current }.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 13.sp) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Banner shown when the screen has no institution to read from. */
@Composable
fun NotSignedInNotice() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardOrange.copy(alpha = 0.12f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Not signed in to an institution", fontWeight = FontWeight.Bold, color = CardOrange)
            Spacer(Modifier.height(4.dp))
            Text(
                "Sign in so this device knows which library's records to show.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}
