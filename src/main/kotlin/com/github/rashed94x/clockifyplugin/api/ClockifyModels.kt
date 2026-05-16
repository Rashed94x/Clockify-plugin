package com.github.rashed94x.clockifyplugin.api

import kotlinx.serialization.Serializable

@Serializable
data class ClockifyUser(val id: String, val name: String, val email: String)

@Serializable
data class ClockifyWorkspace(val id: String, val name: String)

@Serializable
data class ClockifyProject(val id: String, val name: String, val archived: Boolean = false)

@Serializable
data class ClockifyTask(val id: String, val name: String)

@Serializable
data class CreateTimeEntryRequest(
    val start: String,
    val end: String,
    val description: String,
    val projectId: String? = null,
    val taskId: String? = null
)
