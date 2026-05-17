package com.farkasandrasdev.familykanbanapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

sealed interface BacklogState {
    data object Loading : BacklogState
    data class Success(
        val tasks: List<Task>,
        val profiles: Map<String, UserProfile>   // id → profile
    ) : BacklogState
    data class Error(val message: String) : BacklogState
}

class BacklogViewModel : ViewModel() {

    private val _state = MutableStateFlow<BacklogState>(BacklogState.Loading)
    val state: StateFlow<BacklogState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = BacklogState.Loading
            try {
                val tasks = supabase.from("tasks")
                    .select { filter { filter("sprint_id", FilterOperator.IS, "null") } }
                    .decodeList<Task>()
                    .sortedBy { it.position }

                val profileIds = (tasks.mapNotNull { it.assignedTo }
                        + tasks.map { it.createdBy }
                        + tasks.mapNotNull { it.updatedBy })
                    .distinct()
                val profiles: Map<String, UserProfile> = if (profileIds.isEmpty()) {
                    emptyMap()
                } else {
                    runCatching {
                        supabase.from("profiles")
                            .select { filter { isIn("id", profileIds) } }
                            .decodeList<ProfileRow>()
                            .associate { it.id to UserProfile(it.id, it.displayName, it.avatarUrl) }
                    }.getOrDefault(emptyMap())
                }

                _state.value = BacklogState.Success(tasks, profiles)
            } catch (e: Exception) {
                _state.value = BacklogState.Error(e.message ?: "Failed to load backlog")
            }
        }
    }

    fun addTask(
        title: String,
        description: String?,
        priority: String,
        dueDate: String?,
        createdBy: String
    ) {
        viewModelScope.launch {
            try {
                val maxPos = (_state.value as? BacklogState.Success)
                    ?.tasks?.maxOfOrNull { it.position } ?: -1

                supabase.from("tasks").insert(buildJsonObject {
                    put("title", title)
                    if (!description.isNullOrBlank()) put("description", description)
                    put("status", TaskStatus.TODO.value)
                    put("priority", priority)
                    put("position", maxPos + 1)
                    if (!dueDate.isNullOrBlank()) put("due_date", dueDate)
                    put("created_by", createdBy)
                    // sprint_id intentionally omitted → NULL
                })
                load()
            } catch (e: Exception) {
                _state.value = BacklogState.Error(e.message ?: "Failed to add task")
            }
        }
    }

    fun moveToSprint(taskId: String, sprintId: String) {
        viewModelScope.launch {
            try {
                supabase.from("tasks").update(
                    buildJsonObject { put("sprint_id", sprintId) }
                ) { filter { eq("id", taskId) } }

                val current = _state.value as? BacklogState.Success ?: return@launch
                _state.value = current.copy(tasks = current.tasks.filter { it.id != taskId })
            } catch (e: Exception) {
                _state.value = BacklogState.Error(e.message ?: "Failed to move task to sprint")
            }
        }
    }

    fun updateTask(
        taskId: String,
        title: String,
        description: String?,
        priority: String,
        dueDate: String?
    ) {
        viewModelScope.launch {
            try {
                supabase.from("tasks").update(
                    buildJsonObject {
                        put("title", title)
                        if (!description.isNullOrBlank()) put("description", description)
                        else put("description", null as String?)
                        put("priority", priority)
                        if (!dueDate.isNullOrBlank()) put("due_date", dueDate)
                        else put("due_date", null as String?)
                    }
                ) { filter { eq("id", taskId) } }

                val current = _state.value as? BacklogState.Success ?: return@launch
                _state.value = current.copy(tasks = current.tasks.map { t ->
                    if (t.id == taskId) t.copy(
                        title       = title,
                        description = description?.ifBlank { null },
                        priority    = priority,
                        dueDate     = dueDate?.ifBlank { null }
                    ) else t
                })
            } catch (e: Exception) {
                _state.value = BacklogState.Error(e.message ?: "Failed to update task")
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            try {
                supabase.from("tasks").delete { filter { eq("id", taskId) } }
                val current = _state.value as? BacklogState.Success ?: return@launch
                _state.value = current.copy(tasks = current.tasks.filter { it.id != taskId })
            } catch (e: Exception) {
                _state.value = BacklogState.Error(e.message ?: "Failed to delete task")
            }
        }
    }
}

