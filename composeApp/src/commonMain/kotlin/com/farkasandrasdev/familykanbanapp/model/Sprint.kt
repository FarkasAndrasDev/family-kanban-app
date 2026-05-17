package com.farkasandrasdev.familykanbanapp.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Sprint(
    val id: String,
    val name: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date")   val endDate: String,
    val status: String,
    @SerialName("created_by")  val createdBy: String,
    @SerialName("created_at")  val createdAt: String? = null
)
