package com.farkasandrasdev.familykanbanapp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ProfileRow(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url")   val avatarUrl: String? = null
)
