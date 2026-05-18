package no.paral.clockifyplugin.toolwindow

import no.paral.clockifyplugin.api.ClockifyClient
import no.paral.clockifyplugin.api.CreateTimeEntryRequest
import no.paral.clockifyplugin.settings.ClockifyCredentials
import no.paral.clockifyplugin.settings.ClockifyProjectSettings
import no.paral.clockifyplugin.ui.ClockifyUiUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.HierarchyEvent
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerDateModel

class ClockifyToolWindowFactory : ToolWindowFactory {

    private var formPanel: JComponent? = null
    private var titleField: JBTextField? = null
    private var noteArea: JBTextArea? = null
    private var startSpinner: JSpinner? = null
    private var endSpinner: JSpinner? = null
    private var workspaceCombo: ComboBox<WorkspaceItem>? = null
    private var projectCombo: ComboBox<ProjectItem>? = null
    private var taskCombo: ComboBox<TaskItem>? = null
    private var statusLabel: JLabel? = null
    private var logButton: JButton? = null
    private var isUpdatingCombos = false

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = ContentFactory.getInstance().createContent(
            buildContainer(project), null, false
        )
        toolWindow.contentManager.addContent(content)
    }

    private fun buildContainer(project: Project): JComponent {
        val container = JPanel(BorderLayout())
        val scrollPane = JBScrollPane().apply { border = null }
        container.add(scrollPane, BorderLayout.CENTER)

        fun refresh() {
            scrollPane.viewport.view = buildPanel(project)
            container.revalidate()
            container.repaint()
        }

        container.addHierarchyListener { e ->
            if ((e.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L && container.isShowing) {
                refresh()
            }
        }

        refresh()
        return container
    }

    private fun buildPanel(project: Project): JComponent {
        if (ClockifyCredentials.apiToken.isNullOrBlank()) {
            return panel {
                row {
                    comment(
                        "No API token configured.<br>" +
                        "Add your token in <b>Settings → Tools → Clockify</b>."
                    )
                }
                row {
                    button("Open Clockify Settings") {
                        ClockifyUiUtils.openSettings(project)
                    }.align(AlignX.FILL)
                }
            }.apply { border = JBUI.Borders.empty(12) }
        }

        if (formPanel == null) {
            formPanel = createFormPanel(project)
        }
        refreshWorkspaces(project)
        return formPanel!!
    }

    private fun createFormPanel(project: Project): JComponent {
        val tField = JBTextField()
        val nArea = JBTextArea(3, 40).apply { lineWrap = true; wrapStyleWord = true }

        val now = Date()
        val oneHourAgo = Date(now.time - 3_600_000)
        val sSpinner = JSpinner(SpinnerDateModel(oneHourAgo, null, null, Calendar.MINUTE)).apply {
            editor = JSpinner.DateEditor(this, "yyyy-MM-dd HH:mm")
        }
        val eSpinner = JSpinner(SpinnerDateModel(now, null, null, Calendar.MINUTE)).apply {
            editor = JSpinner.DateEditor(this, "yyyy-MM-dd HH:mm")
        }

        val wCombo = ComboBox<WorkspaceItem>()
        val pCombo = ComboBox<ProjectItem>()
        val tkCombo = ComboBox<TaskItem>()
        val sLabel = JLabel("")

        titleField = tField; noteArea = nArea
        startSpinner = sSpinner; endSpinner = eSpinner
        workspaceCombo = wCombo; projectCombo = pCombo; taskCombo = tkCombo
        statusLabel = sLabel

        wCombo.addActionListener {
            if (isUpdatingCombos) return@addActionListener
            val token = ClockifyCredentials.apiToken ?: return@addActionListener
            val sel = wCombo.selectedItem as? WorkspaceItem ?: return@addActionListener
            ClockifyProjectSettings.getInstance(project).state.apply {
                workspaceId = sel.id; workspaceName = sel.name
            }
            loadProjects(token, sel.id, preselectId = null)
        }

        pCombo.addActionListener {
            if (isUpdatingCombos) return@addActionListener
            val token = ClockifyCredentials.apiToken ?: return@addActionListener
            val wId = (wCombo.selectedItem as? WorkspaceItem)?.id ?: return@addActionListener
            val sel = pCombo.selectedItem as? ProjectItem ?: return@addActionListener
            ClockifyProjectSettings.getInstance(project).state.apply {
                projectId = sel.id; projectName = sel.name
            }
            if (sel.id.isBlank()) {
                tkCombo.model = DefaultComboBoxModel<TaskItem>(arrayOf(TaskItem("", "(no task)")))
            } else {
                loadTasks(token, wId, sel.id)
            }
        }

        return panel {
            row("Title:") { cell(tField).resizableColumn().align(AlignX.FILL) }
            row("Note:") { cell(JBScrollPane(nArea)).resizableColumn() }
            separator()
            row("Workspace:") { cell(wCombo).resizableColumn() }
            row("Project:") { cell(pCombo).resizableColumn() }
            row("Task:") { cell(tkCombo).resizableColumn() }
            separator()
            row("Start:") { cell(sSpinner) }
            row("End:") { cell(eSpinner) }
            row {
                button("Log Time") { onLogTime() }
                    .align(AlignX.FILL)
                    .component.also { logButton = it }
            }
            row { cell(sLabel) }
        }.apply { border = JBUI.Borders.empty(8, 12) }
    }

    private fun refreshWorkspaces(project: Project) {
        val token = ClockifyCredentials.apiToken ?: return
        val savedState = ClockifyProjectSettings.getInstance(project).state
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val workspaces = ClockifyClient(token).getWorkspaces()
                ApplicationManager.getApplication().invokeLater {
                    isUpdatingCombos = true
                    try {
                        val items = workspaces.map { WorkspaceItem(it.id, it.name) }.toTypedArray()
                        workspaceCombo?.model = DefaultComboBoxModel(items)
                        val idx = workspaces.indexOfFirst { it.id == savedState.workspaceId }
                        workspaceCombo?.selectedIndex = if (idx >= 0) idx else if (workspaces.isNotEmpty()) 0 else -1
                    } finally {
                        isUpdatingCombos = false
                    }
                    val sel = workspaceCombo?.selectedItem as? WorkspaceItem
                    if (sel != null) loadProjects(token, sel.id, preselectId = savedState.projectId)
                }
            } catch (_: Exception) { /* silent — dropdowns stay as-is on network failure */ }
        }
    }

    private fun loadProjects(token: String, workspaceId: String, preselectId: String?) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val projects = ClockifyClient(token).getProjects(workspaceId)
                ApplicationManager.getApplication().invokeLater {
                    isUpdatingCombos = true
                    try {
                        val items = (listOf(ProjectItem("", "(none)")) +
                                projects.map { ProjectItem(it.id, it.name) }).toTypedArray()
                        projectCombo?.model = DefaultComboBoxModel(items)
                        val idx = if (preselectId != null) projects.indexOfFirst { it.id == preselectId } else -1
                        projectCombo?.selectedIndex = if (idx >= 0) idx + 1 else 0
                    } finally {
                        isUpdatingCombos = false
                    }
                    val sel = projectCombo?.selectedItem as? ProjectItem
                    if (sel != null && sel.id.isNotBlank()) {
                        loadTasks(token, workspaceId, sel.id)
                    } else {
                        taskCombo?.model = DefaultComboBoxModel<TaskItem>(arrayOf(TaskItem("", "(no task)")))
                    }
                }
            } catch (_: Exception) { /* silent — dropdowns stay as-is on network failure */ }
        }
    }

    private fun loadTasks(token: String, workspaceId: String, projectId: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val tasks = ClockifyClient(token).getTasks(workspaceId, projectId)
                ApplicationManager.getApplication().invokeLater {
                    val items = (listOf(TaskItem("", "(no task)")) +
                            tasks.map { TaskItem(it.id, it.name) }).toTypedArray()
                    taskCombo?.model = DefaultComboBoxModel(items)
                    taskCombo?.selectedIndex = 0
                }
            } catch (_: Exception) { /* silent — dropdowns stay as-is on network failure */ }
        }
    }

    private fun onLogTime() {
        val token = ClockifyCredentials.apiToken ?: run {
            showStatus("No API token configured.", error = true); return
        }
        val workspaceId = (workspaceCombo?.selectedItem as? WorkspaceItem)?.id?.ifBlank { null } ?: run {
            showStatus("Select a workspace first.", error = true); return
        }
        val start = (startSpinner?.value as? Date)?.toInstant() ?: return
        val end = (endSpinner?.value as? Date)?.toInstant() ?: return
        if (!end.isAfter(start)) {
            showStatus("End time must be after start time.", error = true); return
        }

        val title = titleField?.text?.trim().orEmpty()
        val note = noteArea?.text?.trim().orEmpty()
        val description = listOf(title, note).filter { it.isNotBlank() }.joinToString("\n")

        val selectedProject = (projectCombo?.selectedItem as? ProjectItem)?.id?.ifBlank { null }
        val selectedTask = (taskCombo?.selectedItem as? TaskItem)?.id?.ifBlank { null }

        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
        val request = CreateTimeEntryRequest(
            start = fmt.format(start),
            end = fmt.format(end),
            description = description,
            projectId = selectedProject,
            taskId = selectedTask
        )

        logButton?.isEnabled = false
        showStatus("Logging…", error = false)

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                ClockifyClient(token).createTimeEntry(workspaceId, request)
                ApplicationManager.getApplication().invokeLater {
                    showStatus("Time logged successfully!", error = false)
                    logButton?.isEnabled = true
                    val now = Date()
                    startSpinner?.value = Date(now.time - 3_600_000)
                    endSpinner?.value = now
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    showStatus("Failed: ${e.message}", error = true)
                    logButton?.isEnabled = true
                }
            }
        }
    }

    private fun showStatus(message: String, error: Boolean) {
        statusLabel?.foreground = if (error) JBColor.RED else JBColor.foreground()
        statusLabel?.text = message
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
}
