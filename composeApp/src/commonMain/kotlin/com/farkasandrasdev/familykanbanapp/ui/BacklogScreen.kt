package com.farkasandrasdev.familykanbanapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.farkasandrasdev.familykanbanapp.BacklogState
import com.farkasandrasdev.familykanbanapp.BacklogViewModel
import com.farkasandrasdev.familykanbanapp.SprintState
import com.farkasandrasdev.familykanbanapp.SprintViewModel
import com.farkasandrasdev.familykanbanapp.model.Sprint
import com.farkasandrasdev.familykanbanapp.model.Task
import com.farkasandrasdev.familykanbanapp.model.TaskPriority
import com.farkasandrasdev.familykanbanapp.model.UserProfile
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacklogScreen(
    currentUser: UserProfile,
    backlogViewModel: BacklogViewModel = viewModel { BacklogViewModel() },
    sprintViewModel: SprintViewModel = viewModel { SprintViewModel() }
) {
    val backlogState  by backlogViewModel.state.collectAsState()
    val sprintState   by sprintViewModel.state.collectAsState()
    var showAddTask   by remember { mutableStateOf(false) }
    var selectedTask  by remember { mutableStateOf<Task?>(null) }

    val availableSprints = (sprintState as? SprintState.Success)
        ?.sprints?.filter { it.status == "planned" || it.status == "active" }
        ?: emptyList()

    Box(Modifier.fillMaxSize()) {
        when (val s = backlogState) {
            is BacklogState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            is BacklogState.Error -> {
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { backlogViewModel.load() }) { Text("Retry") }
                }
            }

            is BacklogState.Success -> {
                if (s.tasks.isEmpty()) {
                    Column(
                        Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Backlog is empty", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap + to add tasks that aren't part of a sprint yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(s.tasks, key = { it.id }) { task ->
                            BacklogTaskRow(
                                task        = task,
                                assignee    = task.assignedTo?.let { s.profiles[it] },
                                currentUser = currentUser,
                                onAssign    = { userId -> backlogViewModel.assignTask(task.id, userId) },
                                onClick     = { selectedTask = task }
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddTask = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }

    // ── Add task sheet ────────────────────────────────────────────
    if (showAddTask) {
        AddTaskSheet(
            onDismiss = { showAddTask = false },
            onConfirm = { title, description, priority, dueDate ->
                backlogViewModel.addTask(
                    title       = title,
                    description = description,
                    priority    = priority,
                    dueDate     = dueDate,
                    createdBy   = currentUser.id
                )
                showAddTask = false
            }
        )
    }

    // ── Task detail / edit sheet ──────────────────────────────────
    val profiles = (backlogState as? BacklogState.Success)?.profiles ?: emptyMap()
    selectedTask?.let { task ->
        BacklogTaskDetailSheet(
            task             = task,
            profiles         = profiles,
            currentUser      = currentUser,
            availableSprints = availableSprints,
            onDismiss        = { selectedTask = null },
            onSave           = { title, description, priority, dueDate ->
                backlogViewModel.updateTask(task.id, title, description, priority, dueDate)
                selectedTask = null
            },
            onAssign         = { userId -> backlogViewModel.assignTask(task.id, userId) },
            onMoveToSprint   = { sprintId ->
                backlogViewModel.moveToSprint(task.id, sprintId)
                selectedTask = null
            },
            onDelete = {
                backlogViewModel.deleteTask(task.id)
                selectedTask = null
            }
        )
    }
}

// ── Backlog task row ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BacklogTaskRow(
    task: Task,
    assignee: UserProfile?,
    currentUser: UserProfile,
    onAssign: (userId: String?) -> Unit,
    onClick: () -> Unit
) {
    val today    = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val tomorrow = today.plus(1, kotlinx.datetime.DateTimeUnit.DAY)
    val isDueSoon = task.dueDate != null &&
        (task.dueDate <= today.toString() || task.dueDate == tomorrow.toString())

    val cardColors = if (isDueSoon)
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
    else
        CardDefaults.cardColors()

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = cardColors
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!task.description.isNullOrBlank()) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PriorityChip(task.priority)
                if (task.dueDate != null) {
                    Text(
                        text = task.dueDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (assignee != null) {
                    AssigneeBubble(assignee)
                }
            }
        }
        // Assignment row
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (task.assignedTo != currentUser.id) {
                TextButton(
                    onClick = { onAssign(currentUser.id) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) { Text("Assign to me", style = MaterialTheme.typography.labelSmall) }
            }
            if (task.assignedTo != null) {
                TextButton(
                    onClick = { onAssign(null) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) { Text("Clear assignment", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

// ── Backlog task detail / edit sheet ─────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BacklogTaskDetailSheet(
    task: Task,
    profiles: Map<String, UserProfile>,
    currentUser: UserProfile,
    availableSprints: List<Sprint>,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String?, priority: String, dueDate: String?) -> Unit,
    onAssign: (userId: String?) -> Unit,
    onMoveToSprint: (sprintId: String) -> Unit,
    onDelete: () -> Unit
) {
    var title       by remember { mutableStateOf(task.title) }
    var description by remember { mutableStateOf(task.description ?: "") }
    var priority    by remember { mutableStateOf(task.priority) }

    val nowMillis = System.currentTimeMillis()
    val todayMillis = Instant.fromEpochMilliseconds(nowMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
        .atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

    val initialMillis = task.dueDate?.let { dateStr ->
        runCatching {
            val parts = dateStr.split("-")
            val y = parts[0].toInt(); val m = parts[1].toInt(); val d = parts[2].toInt()
            kotlinx.datetime.LocalDate(y, m, d)
                .atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        }.getOrNull()
    } ?: todayMillis

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    var showDatePicker  by remember { mutableStateOf(false) }

    val selectedDateLabel = datePickerState.selectedDateMillis?.let { millis ->
        Instant.fromEpochMilliseconds(millis)
            .toLocalDateTime(TimeZone.UTC).date.toString()
    } ?: task.dueDate ?: ""

    val assignee   = task.assignedTo?.let { profiles[it] }
    val createdBy  = profiles[task.createdBy]
    val updatedBy  = task.updatedBy?.let { profiles[it] }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Edit Task", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // Created by + date
            if (createdBy != null || task.createdAt != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Created by",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (createdBy != null) {
                        AssigneeBubble(createdBy)
                        Text(
                            createdBy.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (task.createdAt != null) {
                        Text(
                            text = task.createdAt.take(10),  // YYYY-MM-DD from ISO timestamp
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Assigned to
            if (assignee != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Assigned to",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AssigneeBubble(assignee)
                    Text(
                        assignee.displayName,
                        style = MaterialTheme.typography.bodySmall
                    )
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

            // Last modified — only show if actually edited after creation
            val wasModified = task.updatedAt != null &&
                task.createdAt != null &&
                task.updatedAt.take(19) != task.createdAt.take(19)
            if (wasModified) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Modified by",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (updatedBy != null) {
                        AssigneeBubble(updatedBy)
                        Text(
                            updatedBy.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (task.updatedAt != null) {
                        Text(
                            text = task.updatedAt.take(10),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                onClick  = { onSave(title, description.ifBlank { null }, priority, selectedDateLabel.ifBlank { null }) },
                enabled  = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }

            HorizontalDivider()

            if (availableSprints.isEmpty()) {
                Text(
                    "No planned or active sprints to move to.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("Move to sprint", style = MaterialTheme.typography.labelMedium)
                availableSprints.forEach { sprint ->
                    OutlinedButton(
                        onClick  = { onMoveToSprint(sprint.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${sprint.name}  (${sprint.status})")
                    }
                }
            }

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
