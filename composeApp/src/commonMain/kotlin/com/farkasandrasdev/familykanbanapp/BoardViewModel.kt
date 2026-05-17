package com.farkasandrasdev.familykanbanapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farkasandrasdev.familykanbanapp.model.Sprint
import com.farkasandrasdev.familykanbanapp.model.Task
import com.farkasandrasdev.familykanbanapp.model.TaskStatus
import com.farkasandrasdev.familykanbanapp.model.UserProfile
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed interface BoardState {
    data object Loading : BoardState
    data object NoActiveSprint : BoardState
    data class Success(
        val sprint: Sprint,
        val tasks: List<Task>,
        val profiles: Map<String, UserProfile>   // id → profile
    ) : BoardState
    data class Error(val message: String) : BoardState
}

class BoardViewModel : ViewModel() {

    private val _state = MutableStateFlow<BoardState>(BoardState.Loading)
    val state: StateFlow<BoardState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = BoardState.Loading
            try {
                // 1. Active sprint
                val sprints = supabase.from("sprints")
                    .select { filter { eq("status", "active") } }
                    .decodeList<Sprint>()

                val sprint = sprints.firstOrNull()
                    ?: run { _state.value = BoardState.NoActiveSprint; return@launch }

                // 2. Tasks for this sprint, ordered by position
                val tasks = supabase.from("tasks")
                    .select { filter { eq("sprint_id", sprint.id) } }
                    .decodeList<Task>()
                    .sortedBy { it.position }

                // 3. Profiles for assignees that appear on the tasks
                val assigneeIds = tasks.mapNotNull { it.assignedTo }.distinct()
                val profiles: Map<String, UserProfile> = if (assigneeIds.isEmpty()) {
                    emptyMap()
                } else {
                    supabase.from("profiles")
                        .select { filter { isIn("id", assigneeIds) } }
                        .decodeList<ProfileRow>()
                        .associate { it.id to UserProfile(it.id, it.displayName, it.avatarUrl) }
                }

                _state.value = BoardState.Success(sprint, tasks, profiles)
            } catch (e: Exception) {
                _state.value = BoardState.Error(e.message ?: "Failed to load board")
            }
        }
    }

    fun addTask(
        title: String,
        description: String?,
        priority: String,
        dueDate: String?,
        sprintId: String,
        createdBy: String
    ) {
        viewModelScope.launch {
            try {
                // Position = max existing position in todo + 1
                val maxPos = (_state.value as? BoardState.Success)
                    ?.tasks?.filter { it.status == TaskStatus.TODO.value }
                    ?.maxOfOrNull { it.position } ?: -1

                supabase.from("tasks").insert(buildJsonObject {
                    put("title", title)
                    if (!description.isNullOrBlank()) put("description", description)
                    put("status", TaskStatus.TODO.value)
                    put("priority", priority)
                    put("position", maxPos + 1)
                    if (!dueDate.isNullOrBlank()) put("due_date", dueDate)
                    put("sprint_id", sprintId)
                    put("created_by", createdBy)
                })
                load()
            } catch (e: Exception) {
                _state.value = BoardState.Error(e.message ?: "Failed to add task")
            }
        }
    }

    fun moveTask(taskId: String, newStatus: String) {
        viewModelScope.launch {
            try {
                val current = _state.value as? BoardState.Success ?: return@launch
                val maxPos = current.tasks
                    .filter { it.status == newStatus }
                    .maxOfOrNull { it.position } ?: -1

                supabase.from("tasks").update(buildJsonObject {
                    put("status", newStatus)
                    put("position", maxPos + 1)
                }) { filter { eq("id", taskId) } }

                // Optimistic local update
                _state.value = current.copy(
                    tasks = current.tasks.map {
                        if (it.id == taskId) it.copy(status = newStatus, position = maxPos + 1) else it
                    }
                )
            } catch (e: Exception) {
                _state.value = BoardState.Error(e.message ?: "Failed to move task")
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            try {
                supabase.from("tasks").delete { filter { eq("id", taskId) } }
                val current = _state.value as? BoardState.Success ?: return@launch
                _state.value = current.copy(tasks = current.tasks.filter { it.id != taskId })
            } catch (e: Exception) {
                _state.value = BoardState.Error(e.message ?: "Failed to delete task")
            }
        }
    }
}

