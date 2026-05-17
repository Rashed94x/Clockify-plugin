package com.github.rashed94x.clockifyplugin.toolwindow

import com.github.rashed94x.clockifyplugin.settings.ClockifyCredentials
import com.github.rashed94x.clockifyplugin.settings.ClockifyProjectSettings
import com.github.rashed94x.clockifyplugin.vcs.LogTimeDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.awt.BorderLayout
import java.awt.event.HierarchyEvent
import javax.swing.JComponent
import javax.swing.JPanel

class ClockifyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = ContentFactory.getInstance().createContent(
            buildContainer(project), null, false
        )
        toolWindow.contentManager.addContent(content)
    }

    // Wraps the inner panel so it can be rebuilt each time the tool window becomes visible,
    // picking up any settings changes made since it was last shown.
    private fun buildContainer(project: Project): JComponent {
        val container = JPanel(BorderLayout())

        fun refresh() {
            container.removeAll()
            container.add(buildPanel(project), BorderLayout.NORTH)
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
        val state = ClockifyProjectSettings.getInstance(project).state
        val hasToken = !ClockifyCredentials.apiToken.isNullOrBlank()
        val hasWorkspace = state.workspaceId.isNotBlank()

        return panel {
            row {
                button("Log Time…") {
                    LogTimeDialog(project, "").show()
                }.align(AlignX.FILL)
            }

            if (hasWorkspace) {
                separator()
                row {
                    label("Workspace")
                }
                row {
                    label(state.workspaceName.ifBlank { state.workspaceId }).comment("")
                }
                if (state.projectName.isNotBlank()) {
                    row {
                        label("Default Project")
                    }
                    row {
                        label(state.projectName)
                    }
                }
            }

            if (!hasToken) {
                separator()
                row {
                    comment(
                        "No API token configured.<br>" +
                        "Open <b>Settings → Tools → Clockify</b> to get started."
                    )
                }
            } else if (!hasWorkspace) {
                separator()
                row {
                    comment(
                        "No workspace selected.<br>" +
                        "Open <b>Settings → Tools → Clockify</b> to choose one."
                    )
                }
            }
        }
    }
}
