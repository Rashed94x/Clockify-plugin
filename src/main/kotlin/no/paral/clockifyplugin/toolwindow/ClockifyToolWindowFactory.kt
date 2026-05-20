package no.paral.clockifyplugin.toolwindow

import no.paral.clockifyplugin.api.ClockifyClient
import no.paral.clockifyplugin.api.CreateTimeEntryRequest
import no.paral.clockifyplugin.settings.ClockifyCredentials
import no.paral.clockifyplugin.settings.ClockifyProjectSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.event.HierarchyEvent
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.swing.JComponent
import javax.swing.JPanel

private val NO_TASK = ClockifyToolWindowPanel.TaskItem("", "(no task)")

class ClockifyToolWindowFactory : ToolWindowFactory {

    private var panel: ClockifyToolWindowPanel? = null

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.contentManager.addContent(
            ContentFactory.getInstance().createContent(buildContainer(project), null, false)
        )
    }

    private fun buildContainer(project: Project): JComponent {
        val container = JPanel(BorderLayout())
        val scrollPane = JBScrollPane().apply { border = null }
        container.add(scrollPane, BorderLayout.CENTER)

        fun refresh() {
            scrollPane.viewport.view = buildContent(project)
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

    private fun buildContent(project: Project): JComponent {
        if (ClockifyCredentials.apiToken.isNullOrBlank()) {
            panel = null
            return ClockifyToolWindowPanel(project).buildNoTokenPanel()
        }
        if (panel == null) {
            panel = ClockifyToolWindowPanel(project).also { wireCallbacks(it, project) }
        }
        refreshWorkspaces(project)
        return panel!!.buildFormPanel()
    }

    // ── Callback wiring ───────────────────────────────────────────────────────

    private fun wireCallbacks(p: ClockifyToolWindowPanel, project: Project) {
        p.onWorkspaceSelected = { ws ->
            ClockifyProjectSettings.getInstance(project).state.apply {
                workspaceId = ws.id; workspaceName = ws.name
            }
            val token = ClockifyCredentials.apiToken
            if (token != null) loadProjects(token, ws.id, preselectId = null)
        }

        p.onProjectSelected = { wId, pr ->
            ClockifyProjectSettings.getInstance(project).state.apply {
                projectId = pr.id; projectName = pr.name
            }
            val token = ClockifyCredentials.apiToken
            if (pr.id.isBlank()) {
                p.setTasks(listOf(NO_TASK))
            } else if (token != null) {
                loadTasks(token, wId, pr.id)
            }
        }

        p.onLogTime = { onLogTime() }
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private fun refreshWorkspaces(project: Project) {
        val token = ClockifyCredentials.apiToken ?: return
        val savedState = ClockifyProjectSettings.getInstance(project).state
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val workspaces = ClockifyClient(token).getWorkspaces()
                ApplicationManager.getApplication().invokeLater {
                    panel?.setWorkspaces(
                        workspaces.map { ClockifyToolWindowPanel.WorkspaceItem(it.id, it.name) },
                        preselectId = savedState.workspaceId
                    )
                    panel?.selectedWorkspaceItem?.let { loadProjects(token, it.id, savedState.projectId) }
                }
            } catch (_: Exception) { /* silent — dropdowns stay as-is on network failure */ }
        }
    }

    private fun loadProjects(token: String, workspaceId: String, preselectId: String?) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val projects = ClockifyClient(token).getProjects(workspaceId)
                ApplicationManager.getApplication().invokeLater {
                    val items = listOf(ClockifyToolWindowPanel.ProjectItem("", "(none)")) +
                            projects.map { ClockifyToolWindowPanel.ProjectItem(it.id, it.name) }
                    panel?.setProjects(items, preselectId)
                    val sel = panel?.selectedProjectItem
                    if (sel != null && sel.id.isNotBlank()) {
                        loadTasks(token, workspaceId, sel.id)
                    } else {
                        panel?.setTasks(listOf(NO_TASK))
                    }
                }
            } catch (_: Exception) { /* silent */ }
        }
    }

    private fun loadTasks(token: String, workspaceId: String, projectId: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val tasks = ClockifyClient(token).getTasks(workspaceId, projectId)
                ApplicationManager.getApplication().invokeLater {
                    val items = listOf(NO_TASK) +
                            tasks.map { ClockifyToolWindowPanel.TaskItem(it.id, it.name) }
                    panel?.setTasks(items)
                }
            } catch (_: Exception) { /* silent */ }
        }
    }

    // ── Log time action ───────────────────────────────────────────────────────

    private fun onLogTime() {
        val p = panel ?: return
        val token = ClockifyCredentials.apiToken ?: run {
            p.setStatus("No API token configured.", error = true); return
        }
        val workspaceId = p.selectedWorkspaceItem?.id?.ifBlank { null } ?: run {
            p.setStatus("Select a workspace first.", error = true); return
        }
        val start = p.startTime?.toInstant() ?: return
        val end = p.endTime?.toInstant() ?: return
        if (!end.isAfter(start)) {
            p.setStatus("End time must be after start time.", error = true); return
        }

        val description = listOf(p.title, p.note).filter { it.isNotBlank() }.joinToString("\n")
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
        val request = CreateTimeEntryRequest(
            start = fmt.format(start),
            end = fmt.format(end),
            description = description,
            projectId = p.selectedProjectItem?.id?.ifBlank { null },
            taskId = p.selectedTaskItem?.id?.ifBlank { null }
        )

        p.setLogButtonEnabled(false)
        p.setStatus("Logging…", error = false)

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                ClockifyClient(token).createTimeEntry(workspaceId, request)
                ApplicationManager.getApplication().invokeLater {
                    p.setStatus("Time logged successfully!", error = false)
                    p.setLogButtonEnabled(true)
                    p.resetTimes()
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    p.setStatus("Failed: ${e.message}", error = true)
                    p.setLogButtonEnabled(true)
                }
            }
        }
    }
}
