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
import com.farkasandrasdev.familykanbanapp.model.TaskPriority
import com.farkasandrasdev.familykanbanapp.model.TaskStatus
import com.farkasandrasdev.familykanbanapp.model.UserProfile

private data class ColumnDef(val status: TaskStatus, val label: String)

private val COLUMNS = listOf(
    ColumnDef(TaskStatus.TODO,        "To Do"),
    ColumnDef(TaskStatus.IN_PROGRESS, "In Progress"),
    ColumnDef(TaskStatus.DONE,        "Done"),
)

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
                        items(COLUMNS) { col ->
                            val colTasks = s.tasks
                                .filter { it.status == col.status.value }
                                .sortedBy { it.position }

                            KanbanColumnView(
                                label = col.label,
                                tasks = colTasks,
                                profiles = s.profiles,
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
            task     = task,
            profiles = (state as? BoardState.Success)?.profiles ?: emptyMap(),
            onDismiss = { selectedTask = null },
            onMove    = { newStatus -> boardViewModel.moveTask(task.id, newStatus); selectedTask = null },
            onDelete  = { boardViewModel.deleteTask(task.id); selectedTask = null }
        )
    }
}

// ── Column ────────────────────────────────────────────────────────

@Composable
private fun KanbanColumnView(
    label: String,
    tasks: List<Task>,
    profiles: Map<String, UserProfile>,
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
                TaskCard(task = task, profiles = profiles, onClick = { onTaskClick(task) })
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
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
        }
    }
}



// ── Add task sheet ────────────────────────────────────────────────
// (defined in AddTaskSheet.kt)

// ── Task detail sheet ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDetailSheet(
    task: Task,
    profiles: Map<String, UserProfile>,
    onDismiss: () -> Unit,
    onMove: (newStatus: String) -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            if (!task.description.isNullOrBlank()) {
                Text(task.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PriorityChip(task.priority)
                if (task.dueDate != null) {
                    Text("Due: ${task.dueDate}", style = MaterialTheme.typography.bodySmall)
                }
            }

            val assignee = task.assignedTo?.let { profiles[it] }
            if (assignee != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssigneeBubble(assignee)
                    Text(assignee.displayName, style = MaterialTheme.typography.bodySmall)
                }
            }

            HorizontalDivider()

            Text("Move to", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                COLUMNS.filter { it.status.value != task.status }.forEach { col ->
                    OutlinedButton(onClick = { onMove(col.status.value) }) { Text(col.label) }
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
}
