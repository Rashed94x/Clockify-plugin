package no.paral.clockifyplugin.vcs

import no.paral.clockifyplugin.api.ClockifyClient
import no.paral.clockifyplugin.api.CreateTimeEntryRequest
import no.paral.clockifyplugin.settings.ClockifyCredentials
import no.paral.clockifyplugin.settings.ClockifyProjectSettings
import no.paral.clockifyplugin.ui.ClockifyUiUtils
import git4idea.repo.GitRepositoryManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.awt.event.HierarchyEvent
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JSpinner
import javax.swing.SpinnerDateModel

class LogTimeDialog(private val project: Project, commitMessage: String) : DialogWrapper(project) {

    private val titleField = JBTextField(formatBranchName(getBranchName(project)))
    private val noteArea = ExpandableTextField().apply { text = commitMessage }


    private val startSpinner = JSpinner(
        SpinnerDateModel(Date(System.currentTimeMillis() - 3_600_000), null, null, Calendar.MINUTE)
    ).apply { editor = JSpinner.DateEditor(this, "yyyy-MM-dd HH:mm") }

    private val endSpinner = JSpinner(
        SpinnerDateModel(Date(), null, null, Calendar.MINUTE)
    ).apply { editor = JSpinner.DateEditor(this, "yyyy-MM-dd HH:mm") }

    private val workspaceCombo = ComboBox<WorkspaceItem>()
    private val projectCombo = ComboBox<ProjectItem>(DefaultComboBoxModel(arrayOf(ProjectItem("", "(none)"))))
    private val taskCombo = ComboBox<TaskItem>(DefaultComboBoxModel(arrayOf(TaskItem("", "(no task)"))))
    private val errorLabel = JLabel("").apply { foreground = JBColor.RED }

    private var isUpdatingCombos = false

    init {
        title = "Log Time to Clockify"
        setOKButtonText("Log Time")
        init()
    }

    override fun createCenterPanel(): JComponent {
        if (ClockifyCredentials.apiToken.isNullOrBlank()) {
            getOKAction().isEnabled = false
            return panel {
                row {
                    comment(
                        "No API token configured.<br>" +
                                "Go to <b>Settings → Tools → Clockify</b> to add your token."
                    )
                }
                row {
                    button("Open Clockify Settings") {
                        ClockifyUiUtils.openSettings(project)
                    }
                }
            }
        }

        workspaceCombo.addActionListener {
            if (isUpdatingCombos) return@addActionListener
            val token = ClockifyCredentials.apiToken ?: return@addActionListener
            val sel = workspaceCombo.selectedItem as? WorkspaceItem ?: return@addActionListener
            loadProjects(token, sel.id, preselectId = null)
        }

        projectCombo.addActionListener {
            if (isUpdatingCombos) return@addActionListener
            val token = ClockifyCredentials.apiToken ?: return@addActionListener
            val wId = (workspaceCombo.selectedItem as? WorkspaceItem)?.id ?: return@addActionListener
            val sel = projectCombo.selectedItem as? ProjectItem ?: return@addActionListener
            if (sel.id.isBlank()) {
                taskCombo.model = DefaultComboBoxModel(arrayOf(TaskItem("", "(no task)")))
            } else {
                loadTasks(token, wId, sel.id)
            }
        }

        val content = panel {
            row("Title:") {
                cell(titleField).resizableColumn().align(AlignX.FILL)
            }
            row("Note:") { cell(noteArea).resizableColumn().align(AlignX.FILL) }
            separator()
            row("Workspace:") { cell(workspaceCombo).resizableColumn() }
            row("Project:") { cell(projectCombo).resizableColumn() }
            row("Task:") { cell(taskCombo).resizableColumn() }
            separator()
            row("Start:") { cell(startSpinner) }
            row("End:") { cell(endSpinner) }
            row { cell(errorLabel) }
        }

        // Trigger load when the panel becomes visible — at this point the dialog IS
        // the current modality, so defaultModalityState() captures the right state.
        content.addHierarchyListener { e ->
            if ((e.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L && content.isShowing) {
                loadWorkspaces()
            }
        }

        return content
    }

    private fun loadWorkspaces() {
        val token = ClockifyCredentials.apiToken ?: return
        val savedState = ClockifyProjectSettings.getInstance(project).state
        val modality = ModalityState.defaultModalityState()

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val workspaces = ClockifyClient(token).getWorkspaces()
                ApplicationManager.getApplication().invokeLater({
                    isUpdatingCombos = true
                    try {
                        val items = workspaces.map { WorkspaceItem(it.id, it.name) }.toTypedArray()
                        workspaceCombo.model = DefaultComboBoxModel(items)
                        val idx = workspaces.indexOfFirst { it.id == savedState.workspaceId }
                        workspaceCombo.selectedIndex = if (idx >= 0) idx else if (workspaces.isNotEmpty()) 0 else -1
                    } finally {
                        isUpdatingCombos = false
                    }
                    val sel = workspaceCombo.selectedItem as? WorkspaceItem
                    if (sel != null) loadProjects(token, sel.id, preselectId = savedState.projectId)
                }, modality)
            } catch (_: Exception) { /* combos stay as-is on network failure */
            }
        }
    }

    private fun loadProjects(token: String, workspaceId: String, preselectId: String?) {
        val modality = ModalityState.defaultModalityState()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val projects = ClockifyClient(token).getProjects(workspaceId)
                ApplicationManager.getApplication().invokeLater({
                    isUpdatingCombos = true
                    try {
                        val items = (listOf(ProjectItem("", "(none)")) +
                                projects.map { ProjectItem(it.id, it.name) }).toTypedArray()
                        projectCombo.model = DefaultComboBoxModel(items)
                        val idx = if (preselectId != null) projects.indexOfFirst { it.id == preselectId } else -1
                        projectCombo.selectedIndex = if (idx >= 0) idx + 1 else 0
                    } finally {
                        isUpdatingCombos = false
                    }
                    val sel = projectCombo.selectedItem as? ProjectItem
                    if (sel != null && sel.id.isNotBlank()) {
                        loadTasks(token, workspaceId, sel.id)
                    } else {
                        taskCombo.model = DefaultComboBoxModel(arrayOf(TaskItem("", "(no task)")))
                    }
                }, modality)
            } catch (_: Exception) { /* combos stay as-is on network failure */
            }
        }
    }

    private fun loadTasks(token: String, workspaceId: String, projectId: String) {
        val modality = ModalityState.defaultModalityState()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val tasks = ClockifyClient(token).getTasks(workspaceId, projectId)
                ApplicationManager.getApplication().invokeLater({
                    val items = (listOf(TaskItem("", "(no task)")) +
                            tasks.map { TaskItem(it.id, it.name) }).toTypedArray()
                    taskCombo.model = DefaultComboBoxModel(items)
                    taskCombo.selectedIndex = 0
                }, modality)
            } catch (_: Exception) { /* silently ignore */
            }
        }
    }

    override fun doOKAction() {
        errorLabel.text = ""

        val start = (startSpinner.value as? Date)?.toInstant() ?: return
        val end = (endSpinner.value as? Date)?.toInstant() ?: return
        if (!end.isAfter(start)) {
            errorLabel.text = "End time must be after start time."
            return
        }

        val token = ClockifyCredentials.apiToken
        if (token.isNullOrBlank()) {
            errorLabel.text = "API token not configured — go to Settings → Tools → Clockify."
            return
        }

        val workspaceId = (workspaceCombo.selectedItem as? WorkspaceItem)?.id?.ifBlank { null } ?: run {
            errorLabel.text = "Select a workspace first."
            return
        }

        val title = titleField.text.trim()
        val note = noteArea.text.trim()
        val description = listOf(title, note).filter { it.isNotBlank() }.joinToString("\n")

        val selectedProject = (projectCombo.selectedItem as? ProjectItem)?.id?.ifBlank { null }
        val selectedTask = (taskCombo.selectedItem as? TaskItem)?.id?.ifBlank { null }

        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
        val request = CreateTimeEntryRequest(
            start = fmt.format(start),
            end = fmt.format(end),
            description = description,
            projectId = selectedProject,
            taskId = selectedTask
        )

        getOKAction().isEnabled = false
        val modality = ModalityState.defaultModalityState()

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                ClockifyClient(token).createTimeEntry(workspaceId, request)
                ApplicationManager.getApplication().invokeLater({
                    close(OK_EXIT_CODE)
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Clockify")
                        .createNotification("Time logged successfully!", NotificationType.INFORMATION)
                        .notify(project)
                }, modality)
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater({
                    getOKAction().isEnabled = true
                    errorLabel.text = "Failed to log time: ${e.message}"
                }, modality)
            }
        }
    }

    private data class WorkspaceItem(val id: String, val name: String) {
        override fun toString() = name
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
            } catch (_: Throwable) {
                ""
            }

        private fun formatBranchName(branch: String): String =
            branch.substringAfterLast('/')
                .replace('-', ' ')
                .replace('_', ' ')
                .trim()
                .split("\\s+".toRegex())
                .filter { it.isNotEmpty() }
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }
}
