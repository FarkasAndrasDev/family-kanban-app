package com.farkasandrasdev.familykanbanapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.farkasandrasdev.familykanbanapp.SprintState
import com.farkasandrasdev.familykanbanapp.SprintViewModel
import com.farkasandrasdev.familykanbanapp.model.Sprint
import com.farkasandrasdev.familykanbanapp.model.Task
import com.farkasandrasdev.familykanbanapp.model.UserProfile
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SprintsScreen(
    currentUser: UserProfile,
    sprintViewModel: SprintViewModel = viewModel { SprintViewModel() }
) {
    val state by sprintViewModel.state.collectAsState()
    var showCreate      by remember { mutableStateOf(false) }
    var editingSprint   by remember { mutableStateOf<Sprint?>(null) }
    var selectedTask    by remember { mutableStateOf<Pair<String, Task>?>(null) } // sprintId to Task

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            is SprintState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            is SprintState.Error -> {
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { sprintViewModel.load() }) { Text("Retry") }
                }
            }

            is SprintState.Success -> {
                if (s.sprints.isEmpty()) {
                    Column(
                        Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("No sprints yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap + to create your first sprint.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val active    = s.sprints.filter { it.status == "active" }
                    val planned   = s.sprints.filter { it.status == "planned" }
                    val completed = s.sprints.filter { it.status == "completed" }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (active.isNotEmpty()) {
                            item { SectionHeader("Active") }
                            items(active, key = { it.id }) { sprint ->
                                ExpandableSprintCard(
                                    sprint      = sprint,
                                    tasks       = s.sprintTasks[sprint.id],
                                    profiles    = s.sprintProfiles[sprint.id] ?: emptyMap(),
                                    currentUser = currentUser,
                                    onExpand    = { sprintViewModel.loadSprintTasks(sprint.id) },
                                    onTaskClick = { task -> selectedTask = sprint.id to task },
                                    onEditSprint = { editingSprint = sprint }
                                )
                            }
                        }
                        if (planned.isNotEmpty()) {
                            item { SectionHeader("Planned") }
                            items(planned, key = { it.id }) { sprint ->
                                ExpandableSprintCard(
                                    sprint      = sprint,
                                    tasks       = s.sprintTasks[sprint.id],
                                    profiles    = s.sprintProfiles[sprint.id] ?: emptyMap(),
                                    currentUser = currentUser,
                                    onExpand    = { sprintViewModel.loadSprintTasks(sprint.id) },
                                    onTaskClick = { task -> selectedTask = sprint.id to task },
                                    onEditSprint = { editingSprint = sprint }
                                )
                            }
                        }
                        if (completed.isNotEmpty()) {
                            item { SectionHeader("Completed") }
                            items(completed, key = { it.id }) { sprint ->
                                ExpandableSprintCard(
                                    sprint      = sprint,
                                    tasks       = s.sprintTasks[sprint.id],
                                    profiles    = s.sprintProfiles[sprint.id] ?: emptyMap(),
                                    currentUser = currentUser,
                                    onExpand    = { sprintViewModel.loadSprintTasks(sprint.id) },
                                    onTaskClick = { task -> selectedTask = sprint.id to task },
                                    onEditSprint = { editingSprint = sprint }
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreate = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }

    // ── Create sheet ──────────────────────────────────────────────
    if (showCreate) {
        SprintEditSheet(
            sprint    = null,
            onDismiss = { showCreate = false },
            onSave    = { name, startDate, endDate, _ ->
                sprintViewModel.createSprint(name, startDate, endDate, currentUser.id)
                showCreate = false
            }
        )
    }

    // ── Edit sheet ────────────────────────────────────────────────
    editingSprint?.let { sprint ->
        SprintEditSheet(
            sprint    = sprint,
            onDismiss = { editingSprint = null },
            onSave    = { name, startDate, endDate, status ->
                sprintViewModel.updateSprint(sprint.id, name, startDate, endDate, status)
                editingSprint = null
            }
        )
    }

    // ── Task detail sheet for completed sprint tasks ───────────────
    selectedTask?.let { (sprintId, task) ->
        val profiles = (state as? SprintState.Success)?.sprintProfiles?.get(sprintId) ?: emptyMap()
        TaskDetailSheet(
            task        = task,
            profiles    = profiles,
            currentUser = currentUser,
            onDismiss   = { selectedTask = null },
            onSave      = { title, description, priority, dueDate ->
                sprintViewModel.updateTask(sprintId, task.id, title, description, priority, dueDate)
                selectedTask = null
            },
            onAssign    = { userId -> sprintViewModel.assignTask(sprintId, task.id, userId) },
            onDelete    = { sprintViewModel.deleteTask(sprintId, task.id); selectedTask = null }
        )
    }
}

// ── Section header ────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

// ── Expandable sprint card (all statuses) ────────────────────────

@Composable
private fun ExpandableSprintCard(
    sprint: Sprint,
    tasks: List<Task>?,           // null = not yet loaded
    profiles: Map<String, UserProfile>,
    currentUser: UserProfile,
    onExpand: () -> Unit,
    onTaskClick: (Task) -> Unit,
    onEditSprint: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(sprint.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${sprint.startDate}  →  ${sprint.endDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SprintStatusChip(sprint.status)
                    TextButton(onClick = {
                        if (!expanded && tasks == null) onExpand()
                        expanded = !expanded
                    }) {
                        Text(if (expanded) "Hide" else "Show tasks")
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (tasks == null) {
                        CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).size(24.dp))
                    } else if (tasks.isEmpty()) {
                        Text(
                            "No tasks in this sprint.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        tasks.forEach { task ->
                            SprintTaskRow(
                                task     = task,
                                assignee = task.assignedTo?.let { profiles[it] },
                                onClick  = { onTaskClick(task) }
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(top = 4.dp))
                    TextButton(
                        onClick = onEditSprint,
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("Edit sprint") }
                }
            }
        }
    }
}

// ── Task row inside sprint ────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SprintTaskRow(
    task: Task,
    assignee: UserProfile?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(task.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                if (!task.description.isNullOrBlank()) {
                    Text(
                        task.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PriorityChip(task.priority)
                if (assignee != null) AssigneeBubble(assignee)
            }
        }
    }
}

// ── Status chip ───────────────────────────────────────────────────

@Composable
internal fun SprintStatusChip(status: String) {
    val (label, color) = when (status) {
        "active"    -> "Active"    to MaterialTheme.colorScheme.primary
        "completed" -> "Completed" to MaterialTheme.colorScheme.outline
        else        -> "Planned"   to MaterialTheme.colorScheme.tertiary
    }
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.12f)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ── Create / edit sheet ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SprintEditSheet(
    sprint: Sprint?,                // null = create mode
    onDismiss: () -> Unit,
    onSave: (name: String, startDate: String, endDate: String, status: String) -> Unit
) {
    val isEdit = sprint != null

    // ── Date defaults (only used in create mode) ──────────────────
    val nowMillis = System.currentTimeMillis()
    val today = Instant.fromEpochMilliseconds(nowMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val daysUntilMonday = (DayOfWeek.MONDAY.ordinal - today.dayOfWeek.ordinal + 7) % 7
    val nextMonday = today.plus(daysUntilMonday, DateTimeUnit.DAY)
    val sprintEnd  = nextMonday.plus(6, DateTimeUnit.DAY)
    val jan4 = kotlinx.datetime.LocalDate(nextMonday.year, 1, 4)
    val mondayOfWeek1 = jan4.plus(-(jan4.dayOfWeek.ordinal), DateTimeUnit.DAY)
    val weekNumber = (nextMonday.toEpochDays() - mondayOfWeek1.toEpochDays()) / 7 + 1

    fun dateToMillis(dateStr: String?) = dateStr?.let { s ->
        runCatching {
            val p = s.split("-")
            kotlinx.datetime.LocalDate(p[0].toInt(), p[1].toInt(), p[2].toInt())
                .atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        }.getOrNull()
    }

    val defaultStartMillis = dateToMillis(sprint?.startDate)
        ?: nextMonday.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    val defaultEndMillis   = dateToMillis(sprint?.endDate)
        ?: sprintEnd.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    val defaultName = sprint?.name
        ?: "${nextMonday.year}-${weekNumber.toString().padStart(2, '0')}"

    // ── State ─────────────────────────────────────────────────────
    var name            by remember { mutableStateOf(defaultName) }
    var status          by remember { mutableStateOf(sprint?.status ?: "planned") }
    val startPickerState = rememberDatePickerState(initialSelectedDateMillis = defaultStartMillis)
    val endPickerState   = rememberDatePickerState(initialSelectedDateMillis = defaultEndMillis)
    var showStartPicker  by remember { mutableStateOf(false) }
    var showEndPicker    by remember { mutableStateOf(false) }

    fun millisToDate(millis: Long?) = millis?.let {
        Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date.toString()
    } ?: today.toString()

    val startLabel = millisToDate(startPickerState.selectedDateMillis)
    val endLabel   = millisToDate(endPickerState.selectedDateMillis)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (isEdit) "Edit Sprint" else "New Sprint",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Sprint name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = startLabel, onValueChange = {},
                readOnly = true, label = { Text("Start date") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { TextButton(onClick = { showStartPicker = true }) { Text("Pick") } }
            )

            OutlinedTextField(
                value = endLabel, onValueChange = {},
                readOnly = true, label = { Text("End date") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { TextButton(onClick = { showEndPicker = true }) { Text("Pick") } }
            )

            // Status picker — only shown in edit mode
            if (isEdit) {
                Text("Status", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("planned", "active", "completed").forEach { s ->
                        FilterChip(
                            selected = status == s,
                            onClick  = { status = s },
                            label    = { Text(s.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }

            Button(
                onClick  = { onSave(name, startLabel, endLabel, status) },
                enabled  = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (isEdit) "Save" else "Create Sprint") }
        }
    }

    if (showStartPicker) {
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = { TextButton(onClick = { showStartPicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = startPickerState) }
    }

    if (showEndPicker) {
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = { TextButton(onClick = { showEndPicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = endPickerState) }
    }
}
