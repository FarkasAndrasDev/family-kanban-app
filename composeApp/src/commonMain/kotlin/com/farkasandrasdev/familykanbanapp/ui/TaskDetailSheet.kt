package com.farkasandrasdev.familykanbanapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farkasandrasdev.familykanbanapp.model.Task
import com.farkasandrasdev.familykanbanapp.model.TaskPriority
import com.farkasandrasdev.familykanbanapp.model.TaskStatus
import com.farkasandrasdev.familykanbanapp.model.UserProfile
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

internal data class ColumnDef(val status: TaskStatus, val label: String)

internal val BOARD_COLUMNS = listOf(
    ColumnDef(TaskStatus.TODO,        "To Do"),
    ColumnDef(TaskStatus.IN_PROGRESS, "In Progress"),
    ColumnDef(TaskStatus.DONE,        "Done"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailSheet(
    task: Task,
    profiles: Map<String, UserProfile>,
    currentUser: UserProfile,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String?, priority: String, dueDate: String?) -> Unit,
    onAssign: (userId: String?) -> Unit,
    onMove: ((newStatus: String) -> Unit)? = null,
    onMoveToBacklog: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    var title       by remember { mutableStateOf(task.title) }
    var description by remember { mutableStateOf(task.description ?: "") }
    var priority    by remember { mutableStateOf(task.priority) }

    val nowMillis   = System.currentTimeMillis()
    val todayMillis = Instant.fromEpochMilliseconds(nowMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
        .atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    val initialMillis = task.dueDate?.let { dateStr ->
        runCatching {
            val p = dateStr.split("-")
            kotlinx.datetime.LocalDate(p[0].toInt(), p[1].toInt(), p[2].toInt())
                .atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        }.getOrNull()
    } ?: todayMillis

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    var showDatePicker  by remember { mutableStateOf(false) }
    val selectedDateLabel = datePickerState.selectedDateMillis?.let {
        Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date.toString()
    } ?: task.dueDate ?: ""

    val assignee  = task.assignedTo?.let { profiles[it] }
    val createdBy = profiles[task.createdBy]

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Edit Task", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // Created by
            if (createdBy != null || task.createdAt != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Created by", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (createdBy != null) {
                        AssigneeBubble(createdBy)
                        Text(createdBy.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (task.createdAt != null) {
                        Text(task.createdAt.take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Assigned to
            if (assignee != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Assigned to", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AssigneeBubble(assignee)
                    Text(assignee.displayName, style = MaterialTheme.typography.bodySmall)
                }
            }

            // Assignment buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (task.assignedTo != currentUser.id) {
                    OutlinedButton(onClick = { onAssign(currentUser.id) }) { Text("Assign to me") }
                }
                profiles.values
                    .filter { it.id != currentUser.id && it.id != task.assignedTo }
                    .forEach { other ->
                        OutlinedButton(onClick = { onAssign(other.id) }) {
                            Text("Assign to ${other.displayName}")
                        }
                    }
                if (task.assignedTo != null) {
                    OutlinedButton(onClick = { onAssign(null) }) { Text("Clear assignment") }
                }
            }

            // Last modified
            val wasModified = task.updatedAt != null && task.createdAt != null &&
                task.updatedAt.take(19) != task.createdAt.take(19)
            if (wasModified) {
                val updatedBy = task.updatedBy?.let { profiles[it] }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Modified by", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (updatedBy != null) {
                        AssigneeBubble(updatedBy)
                        Text(updatedBy.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (task.updatedAt != null) {
                        Text(task.updatedAt.take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalDivider()

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
                value = selectedDateLabel, onValueChange = {},
                readOnly = true, label = { Text("Due date") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { TextButton(onClick = { showDatePicker = true }) { Text("Pick") } }
            )

            Button(
                onClick  = { onSave(title, description.ifBlank { null }, priority, selectedDateLabel.ifBlank { null }) },
                enabled  = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }

            if (onMove != null || onMoveToBacklog != null) {
                HorizontalDivider()
                Text("Move to", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onMove != null) {
                        BOARD_COLUMNS.filter { it.status.value != task.status }.forEach { col ->
                            OutlinedButton(onClick = { onMove(col.status.value) }) { Text(col.label) }
                        }
                    }
                    if (onMoveToBacklog != null) {
                        OutlinedButton(onClick = onMoveToBacklog) { Text("Backlog") }
                    }
                }
            }

            HorizontalDivider()

            TextButton(
                onClick  = onDelete,
                colors   = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Delete task") }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
}
