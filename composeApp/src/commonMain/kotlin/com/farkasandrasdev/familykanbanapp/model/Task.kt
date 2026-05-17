package com.farkasandrasdev.familykanbanapp.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class TaskStatus(val value: String) {
    TODO("todo"),
    IN_PROGRESS("in_progress"),
    DONE("done");
    companion object { fun from(value: String) = entries.first { it.value == value } }
}

enum class TaskPriority(val value: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");
    companion object { fun from(value: String) = entries.first { it.value == value } }
}

@Serializable
data class Task(
    val id: String,
    val title: String,
    val description: String?    = null,
    val status: String,
    val priority: String,
    val position: Int           = 0,
    @SerialName("due_date")    val dueDate: String?    = null,
    @SerialName("sprint_id")   val sprintId: String?   = null,
    @SerialName("assigned_to") val assignedTo: String? = null,
    @SerialName("created_by")  val createdBy: String,
    @SerialName("created_at")  val createdAt: String?  = null,
    @SerialName("updated_by")  val updatedBy: String?  = null,
    @SerialName("updated_at")  val updatedAt: String?  = null
)
