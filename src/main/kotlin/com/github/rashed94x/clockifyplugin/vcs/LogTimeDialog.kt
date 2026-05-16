package com.github.rashed94x.clockifyplugin.vcs

import com.github.rashed94x.clockifyplugin.api.ClockifyClient
import com.github.rashed94x.clockifyplugin.api.CreateTimeEntryRequest
import com.github.rashed94x.clockifyplugin.settings.ClockifyCredentials
import com.github.rashed94x.clockifyplugin.settings.ClockifyProjectSettings
import git4idea.repo.GitRepositoryManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel

class LogTimeDialog(private val project: Project, commitMessage: String) : DialogWrapper(project) {

    private val titleField = JBTextField(formatBranchName(getBranchName(project)))
    private val descriptionArea = JBTextArea(4, 40).apply {
        text = commitMessage
        lineWrap = true
        wrapStyleWord = true
    }
    private val durationField = JBTextField()
    private val projectCombo = JComboBox<ProjectItem>()
    private val taskCombo = JComboBox<TaskItem>()
    private val errorLabel = JLabel("").apply { foreground = JBColor.RED }

    // Prevents workspace→project cascade from firing on programmatic model updates
    private var isUpdatingCombos = false

    init {
        title = "Log Time to Clockify"
        setOKButtonText("Log Time")

        projectCombo.addActionListener {
            if (isUpdatingCombos) return@addActionListener
            val token = ClockifyCredentials.apiToken ?: return@addActionListener
            val workspaceId = ClockifyProjectSettings.getInstance(project).state.workspaceId
            if (workspaceId.isBlank()) return@addActionListener
            val selected = projectCombo.selectedItem as? ProjectItem ?: return@addActionListener
            if (selected.id.isBlank()) {
                taskCombo.model = DefaultComboBoxModel(arrayOf(TaskItem("", "(no task)")))
            } else {
                loadTasks(token, workspaceId, selected.id)
            }
        }

        init()
        loadProjects()
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Title:") {
            cell(titleField).columns(COLUMNS_LARGE)
        }
        row("Description:") {
            cell(JBScrollPane(descriptionArea)).resizableColumn()
        }
        row("Duration:") {
            cell(durationField).columns(15).comment("e.g. 1h 30m · 45m · 2h · 90 (minutes)")
        }
        row("Project:") { cell(projectCombo).resizableColumn() }
        row("Task:") { cell(taskCombo).resizableColumn() }
        row { cell(errorLabel) }
    }

    private fun loadProjects() {
        val token = ClockifyCredentials.apiToken ?: return
        val state = ClockifyProjectSettings.getInstance(project).state
        val workspaceId = state.workspaceId.ifBlank { return }
        val savedProjectId = state.projectId

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val projects = ClockifyClient(token).getProjects(workspaceId)
                ApplicationManager.getApplication().invokeLater {
                    isUpdatingCombos = true
                    try {
                        val items = (listOf(ProjectItem("", "(none)")) +
                                projects.map { ProjectItem(it.id, it.name) }).toTypedArray()
                        projectCombo.model = DefaultComboBoxModel(items)
                        val idx = projects.indexOfFirst { it.id == savedProjectId }
                        projectCombo.selectedIndex = if (idx >= 0) idx + 1 else 0
                    } finally {
                        isUpdatingCombos = false
                    }
                    val selected = projectCombo.selectedItem as? ProjectItem
                    if (selected != null && selected.id.isNotBlank()) {
                        loadTasks(token, workspaceId, selected.id)
                    }
                }
            } catch (_: Exception) { /* combos stay empty; user can still log without a project */ }
        }
    }

    private fun loadTasks(token: String, workspaceId: String, projectId: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val tasks = ClockifyClient(token).getTasks(workspaceId, projectId)
                ApplicationManager.getApplication().invokeLater {
                    val items = (listOf(TaskItem("", "(no task)")) +
                            tasks.map { TaskItem(it.id, it.name) }).toTypedArray()
                    taskCombo.model = DefaultComboBoxModel(items)
                    taskCombo.selectedIndex = 0
                }
            } catch (_: Exception) { /* silently ignore */ }
        }
    }

    override fun doOKAction() {
        errorLabel.text = ""

        val durationSeconds = parseDuration(durationField.text)
        if (durationSeconds == null || durationSeconds <= 0) {
            errorLabel.text = "Enter a valid duration (e.g. 1h 30m, 45m, 2h)."
            return
        }

        val token = ClockifyCredentials.apiToken
        if (token.isNullOrBlank()) {
            errorLabel.text = "API token not configured — go to Settings → Tools → Clockify."
            return
        }

        val workspaceId = ClockifyProjectSettings.getInstance(project).state.workspaceId
        if (workspaceId.isBlank()) {
            errorLabel.text = "No workspace configured — go to Settings → Tools → Clockify."
            return
        }

        val selectedProject = (projectCombo.selectedItem as? ProjectItem)?.id?.ifBlank { null }
        val selectedTask = (taskCombo.selectedItem as? TaskItem)?.id?.ifBlank { null }

        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
        val now = Instant.now()
        val request = CreateTimeEntryRequest(
            start = fmt.format(now.minusSeconds(durationSeconds)),
            end = fmt.format(now),
            description = descriptionArea.text.trim(),
            projectId = selectedProject,
            taskId = selectedTask
        )

        getOKAction().isEnabled = false

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                ClockifyClient(token).createTimeEntry(workspaceId, request)
                ApplicationManager.getApplication().invokeLater {
                    super.doOKAction()
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Clockify")
                        .createNotification("Time logged successfully!", NotificationType.INFORMATION)
                        .notify(project)
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    getOKAction().isEnabled = true
                    errorLabel.text = "Failed to log time: ${e.message}"
                }
            }
        }
    }

    private data class ProjectItem(val id: String, val name: String) {
        override fun toString() = name
    }

    private data class TaskItem(val id: String, val name: String) {
        override fun toString() = name
    }

    companion object {
        private fun getBranchName(project: Project): String =
            try {
                GitRepositoryManager.getInstance(project)
                    .repositories
                    .firstOrNull()
                    ?.currentBranchName ?: ""
            } catch (_: Throwable) { "" }

        private fun formatBranchName(branch: String): String =
            branch.substringAfterLast('/')
                .replace('-', ' ')
                .replace('_', ' ')
                .trim()
                .split("\\s+".toRegex())
                .filter { it.isNotEmpty() }
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

        fun parseDuration(input: String): Long? {
            val s = input.trim().lowercase()
            if (s.isBlank()) return null
            val hasH = 'h' in s
            val hasM = 'm' in s
            if (!hasH && !hasM) return s.toLongOrNull()?.times(60)
            val hours = Regex("""(\d+(?:\.\d+)?)\s*h""").find(s)
                ?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val minutes = Regex("""(\d+)\s*m""").find(s)
                ?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val total = (hours * 3600 + minutes * 60).toLong()
            return if (total > 0) total else null
        }
    }
}
