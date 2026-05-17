package com.farkasandrasdev.familykanbanapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.farkasandrasdev.familykanbanapp.BoardState
import com.farkasandrasdev.familykanbanapp.BoardViewModel
import com.farkasandrasdev.familykanbanapp.model.Task
import com.farkasandrasdev.familykanbanapp.model.UserProfile
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KanbanBoardScreen(
    currentUser: UserProfile,
    boardViewModel: BoardViewModel = viewModel { BoardViewModel() }
) {
    val state by boardViewModel.state.collectAsState()
    var showAddTask by remember { mutableStateOf(false) }
    var selectedTask by remember { mutableStateOf<Task?>(null) }

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            is BoardState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            is BoardState.NoActiveSprint -> {
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("No active sprint", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Create a sprint in Supabase and set its status to 'active'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { boardViewModel.load() }) { Text("Refresh") }
                }
            }

            is BoardState.Error -> {
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { boardViewModel.load() }) { Text("Retry") }
                }
            }

            is BoardState.Success -> {
                Column(Modifier.fillMaxSize()) {
                    // Sprint banner
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Sprint: ${s.sprint.name}  ·  ${s.sprint.startDate} → ${s.sprint.endDate}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    LazyRow(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(BOARD_COLUMNS) { col ->
                            val colTasks = s.tasks
                                .filter { it.status == col.status.value }
                                .sortedBy { it.position }

                            KanbanColumnView(
                                label = col.label,
                                tasks = colTasks,
                                profiles = s.profiles,
                                currentUser = currentUser,
                                onAssign = { taskId, userId -> boardViewModel.assignTask(taskId, userId) },
                                onAddTask = { showAddTask = true },
                                onTaskClick = { selectedTask = it }
                            )
                        }
                    }
                }

                // Add task FAB
                FloatingActionButton(
                    onClick = { showAddTask = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }

    // ── Add task sheet ────────────────────────────────────────────
    val successState = state as? BoardState.Success
    if (showAddTask && successState != null) {
        AddTaskSheet(
            onDismiss = { showAddTask = false },
            onConfirm = { title, description, priority, dueDate ->
                boardViewModel.addTask(
                    title       = title,
                    description = description,
                    priority    = priority,
                    dueDate     = dueDate,
                    sprintId    = successState.sprint.id,
                    createdBy   = currentUser.id
                )
                showAddTask = false
            }
        )
    }

    // ── Task detail sheet ─────────────────────────────────────────
    selectedTask?.let { task ->
        TaskDetailSheet(
            task            = task,
            profiles        = (state as? BoardState.Success)?.profiles ?: emptyMap(),
            currentUser     = currentUser,
            onDismiss       = { selectedTask = null },
            onSave          = { title, description, priority, dueDate ->
                boardViewModel.updateTask(task.id, title, description, priority, dueDate)
                selectedTask = null
            },
            onAssign        = { userId -> boardViewModel.assignTask(task.id, userId) },
            onMove          = { newStatus -> boardViewModel.moveTask(task.id, newStatus); selectedTask = null },
            onMoveToBacklog = { boardViewModel.moveToBacklog(task.id); selectedTask = null },
            onDelete        = { boardViewModel.deleteTask(task.id); selectedTask = null }
        )
    }
}

// ── Column ────────────────────────────────────────────────────────

@Composable
private fun KanbanColumnView(
    label: String,
    tasks: List<Task>,
    profiles: Map<String, UserProfile>,
    currentUser: UserProfile,
    onAssign: (taskId: String, userId: String?) -> Unit,
    onAddTask: () -> Unit,
    onTaskClick: (Task) -> Unit
) {
    Column(
        modifier = Modifier
            .width(240.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Badge { Text("${tasks.size}") }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(tasks, key = { it.id }) { task ->
                TaskCard(
                    task        = task,
                    profiles    = profiles,
                    currentUser = currentUser,
                    onAssign    = { userId -> onAssign(task.id, userId) },
                    onClick     = { onTaskClick(task) }
                )
            }
        }

        TextButton(onClick = onAddTask, Modifier.fillMaxWidth()) {
            Text("+ Add task")
        }
    }
}

// ── Task card ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskCard(
    task: Task,
    profiles: Map<String, UserProfile>,
    currentUser: UserProfile,
    onAssign: (userId: String?) -> Unit,
    onClick: () -> Unit
) {
    val today    = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val tomorrow = today.plus(1, kotlinx.datetime.DateTimeUnit.DAY)
    val isDueSoon = task.dueDate != null &&
        task.status != "done" &&
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
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PriorityChip(task.priority)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (task.dueDate != null) {
                        Text(
                            text = task.dueDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val assignee = task.assignedTo?.let { profiles[it] }
                    if (assignee != null) {
                        AssigneeBubble(assignee)
                    }
                }
            }

            // Assignment row
            val isAssignedToMe = task.assignedTo == currentUser.id
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!isAssignedToMe) {
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
}



// ── Add task sheet / Task detail sheet ───────────────────────────
// (defined in AddTaskSheet.kt and TaskDetailSheet.kt)
