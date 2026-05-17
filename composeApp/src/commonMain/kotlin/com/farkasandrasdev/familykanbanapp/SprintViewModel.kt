package com.farkasandrasdev.familykanbanapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farkasandrasdev.familykanbanapp.model.Sprint
import com.farkasandrasdev.familykanbanapp.model.Task
import com.farkasandrasdev.familykanbanapp.model.UserProfile
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed interface SprintState {
    data object Loading : SprintState
    data class Success(
        val sprints: List<Sprint>,
        // Tasks and profiles for expanded completed sprints (sprintId → data)
        val sprintTasks: Map<String, List<Task>> = emptyMap(),
        val sprintProfiles: Map<String, Map<String, UserProfile>> = emptyMap()
    ) : SprintState
    data class Error(val message: String) : SprintState
}

class SprintViewModel : ViewModel() {

    private val _state = MutableStateFlow<SprintState>(SprintState.Loading)
    val state: StateFlow<SprintState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = SprintState.Loading
            try {
                val sprints = supabase.from("sprints")
                    .select()
                    .decodeList<Sprint>()
                    .sortedByDescending { it.createdAt }
                _state.value = SprintState.Success(sprints)
            } catch (e: Exception) {
                _state.value = SprintState.Error(e.message ?: "Failed to load sprints")
            }
        }
    }

    fun createSprint(
        name: String,
        startDate: String,
        endDate: String,
        createdBy: String
    ) {
        viewModelScope.launch {
            try {
                supabase.from("sprints").insert(buildJsonObject {
                    put("name", name)
                    put("start_date", startDate)
                    put("end_date", endDate)
                    put("status", "planned")
                    put("created_by", createdBy)
                })
                load()
            } catch (e: Exception) {
                _state.value = SprintState.Error(e.message ?: "Failed to create sprint")
            }
        }
    }

    fun updateSprint(
        sprintId: String,
        name: String,
        startDate: String,
        endDate: String,
        status: String
    ) {
        viewModelScope.launch {
            try {
                // If activating, complete any other active sprint first
                if (status == "active") {
                    supabase.from("sprints").update(
                        buildJsonObject { put("status", "completed") }
                    ) { filter { eq("status", "active") } }
                }
                supabase.from("sprints").update(buildJsonObject {
                    put("name", name)
                    put("start_date", startDate)
                    put("end_date", endDate)
                    put("status", status)
                }) { filter { eq("id", sprintId) } }
                load()
            } catch (e: Exception) {
                _state.value = SprintState.Error(e.message ?: "Failed to update sprint")
            }
        }
    }

    fun activateSprint(sprintId: String) {
        viewModelScope.launch {
            try {
                // Complete any currently active sprint first
                supabase.from("sprints").update(
                    buildJsonObject { put("status", "completed") }
                ) { filter { eq("status", "active") } }

                // Activate the selected sprint
                supabase.from("sprints").update(
                    buildJsonObject { put("status", "active") }
                ) { filter { eq("id", sprintId) } }

                load()
            } catch (e: Exception) {
                _state.value = SprintState.Error(e.message ?: "Failed to activate sprint")
            }
        }
    }

    fun completeSprint(sprintId: String) {
        viewModelScope.launch {
            try {
                supabase.from("sprints").update(
                    buildJsonObject { put("status", "completed") }
                ) { filter { eq("id", sprintId) } }

                load()
            } catch (e: Exception) {
                _state.value = SprintState.Error(e.message ?: "Failed to complete sprint")
            }
        }
    }

    fun loadSprintTasks(sprintId: String) {
        viewModelScope.launch {
            try {
                val tasks = supabase.from("tasks")
                    .select { filter { eq("sprint_id", sprintId) } }
                    .decodeList<Task>()
                    .sortedBy { it.position }

                val profileIds = (tasks.mapNotNull { it.assignedTo }
                        + tasks.map { it.createdBy }
                        + tasks.mapNotNull { it.updatedBy })
                    .distinct()
                val profiles: Map<String, UserProfile> = if (profileIds.isEmpty()) emptyMap()
                else runCatching {
                    supabase.from("profiles")
                        .select { filter { isIn("id", profileIds) } }
                        .decodeList<ProfileRow>()
                        .associate { it.id to UserProfile(it.id, it.displayName, it.avatarUrl) }
                }.getOrDefault(emptyMap())

                val current = _state.value as? SprintState.Success ?: return@launch
                _state.value = current.copy(
                    sprintTasks    = current.sprintTasks    + (sprintId to tasks),
                    sprintProfiles = current.sprintProfiles + (sprintId to profiles)
                )
            } catch (e: Exception) {
                _state.value = SprintState.Error(e.message ?: "Failed to load sprint tasks")
            }
        }
    }

    fun updateTask(sprintId: String, taskId: String, title: String, description: String?, priority: String, dueDate: String?) {
        viewModelScope.launch {
            try {
                supabase.from("tasks").update(buildJsonObject {
                    put("title", title)
                    if (!description.isNullOrBlank()) put("description", description)
                    else put("description", null as String?)
                    put("priority", priority)
                    if (!dueDate.isNullOrBlank()) put("due_date", dueDate)
                    else put("due_date", null as String?)
                }) { filter { eq("id", taskId) } }

                val current = _state.value as? SprintState.Success ?: return@launch
                val updated = current.sprintTasks[sprintId]?.map { t ->
                    if (t.id == taskId) t.copy(title = title, description = description?.ifBlank { null }, priority = priority, dueDate = dueDate?.ifBlank { null }) else t
                } ?: return@launch
                _state.value = current.copy(sprintTasks = current.sprintTasks + (sprintId to updated))
            } catch (e: Exception) {
                _state.value = SprintState.Error(e.message ?: "Failed to update task")
            }
        }
    }

    fun assignTask(sprintId: String, taskId: String, userId: String?) {
        viewModelScope.launch {
            try {
                supabase.from("tasks").update(
                    buildJsonObject { put("assigned_to", userId) }
                ) { filter { eq("id", taskId) } }

                val current = _state.value as? SprintState.Success ?: return@launch
                val updated = current.sprintTasks[sprintId]?.map { t ->
                    if (t.id == taskId) t.copy(assignedTo = userId) else t
                } ?: return@launch
                _state.value = current.copy(sprintTasks = current.sprintTasks + (sprintId to updated))
            } catch (e: Exception) {
                _state.value = SprintState.Error(e.message ?: "Failed to assign task")
            }
        }
    }

    fun deleteTask(sprintId: String, taskId: String) {
        viewModelScope.launch {
            try {
                supabase.from("tasks").delete { filter { eq("id", taskId) } }

                val current = _state.value as? SprintState.Success ?: return@launch
                val updated = current.sprintTasks[sprintId]?.filter { it.id != taskId } ?: return@launch
                _state.value = current.copy(sprintTasks = current.sprintTasks + (sprintId to updated))
            } catch (e: Exception) {
                _state.value = SprintState.Error(e.message ?: "Failed to delete task")
            }
        }
    }
}
