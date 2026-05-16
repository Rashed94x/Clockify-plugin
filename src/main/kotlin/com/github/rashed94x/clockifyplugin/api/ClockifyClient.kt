package com.github.rashed94x.clockifyplugin.api

import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI

class ClockifyClient(private val apiToken: String) {

    private val baseUrl = "https://api.clockify.me/api/v1"
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun get(path: String): String {
        val connection = (URI.create("$baseUrl$path").toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("X-Api-Key", apiToken)
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                val body = connection.errorStream?.bufferedReader()?.readText() ?: ""
                throw ClockifyApiException(statusCode, body)
            }
            return connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    private fun post(path: String, body: String): String {
        val connection = (URI.create("$baseUrl$path").toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("X-Api-Key", apiToken)
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            connection.outputStream.bufferedWriter().use { it.write(body) }
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
                throw ClockifyApiException(statusCode, errorBody)
            }
            return connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    fun getUser(): ClockifyUser = json.decodeFromString(get("/user"))

    fun getWorkspaces(): List<ClockifyWorkspace> = json.decodeFromString(get("/workspaces"))

    fun getProjects(workspaceId: String): List<ClockifyProject> =
        json.decodeFromString(get("/workspaces/$workspaceId/projects?archived=false&page-size=500"))

    fun getTasks(workspaceId: String, projectId: String): List<ClockifyTask> =
        json.decodeFromString(get("/workspaces/$workspaceId/projects/$projectId/tasks?page-size=500"))

    fun createTimeEntry(workspaceId: String, request: CreateTimeEntryRequest) {
        post("/workspaces/$workspaceId/time-entries",
            json.encodeToString(CreateTimeEntryRequest.serializer(), request))
    }
}

class ClockifyApiException(val statusCode: Int, message: String) :
    Exception("Clockify API error $statusCode: $message")
