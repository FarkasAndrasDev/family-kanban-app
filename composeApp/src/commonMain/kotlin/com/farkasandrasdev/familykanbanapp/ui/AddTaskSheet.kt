package com.farkasandrasdev.familykanbanapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farkasandrasdev.familykanbanapp.model.TaskPriority
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskSheet(
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String?, priority: String, dueDate: String?) -> Unit
) {
    var title       by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority    by remember { mutableStateOf(TaskPriority.MEDIUM.value) }

    val nowMillis = System.currentTimeMillis()
    val today = Instant.fromEpochMilliseconds(nowMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val todayMillis = today.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = todayMillis)
    var showDatePicker by remember { mutableStateOf(false) }

    val selectedDateLabel = datePickerState.selectedDateMillis?.let { millis ->
        Instant.fromEpochMilliseconds(millis)
            .toLocalDateTime(TimeZone.UTC)
            .date
            .toString()          // YYYY-MM-DD
    } ?: today.toString()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("New Task", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Title") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("Description (optional)") }, minLines = 2, maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Priority", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskPriority.entries.forEach { p ->
                    FilterChip(
                        selected = priority == p.value,
                        onClick  = { priority = p.value },
                        label    = { Text(p.value.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            OutlinedTextField(
                value = selectedDateLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Due date") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = { showDatePicker = true }) { Text("Pick") }
                }
            )

            Button(
                onClick  = { onConfirm(title, description.ifBlank { null }, priority, selectedDateLabel) },
                enabled  = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add Task") }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
