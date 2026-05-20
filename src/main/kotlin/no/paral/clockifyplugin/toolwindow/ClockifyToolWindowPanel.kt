package no.paral.clockifyplugin.toolwindow

import no.paral.clockifyplugin.ui.ClockifyUiUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.HierarchyEvent
import java.util.Calendar
import java.util.Date
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerDateModel

class ClockifyToolWindowPanel(private val project: Project) {

    // Callbacks wired by ClockifyToolWindowFactory
    var onWorkspaceSelected: ((WorkspaceItem) -> Unit)? = null
    var onProjectSelected: ((workspaceId: String, projectItem: ProjectItem) -> Unit)? = null
    var onLogTime: (() -> Unit)? = null

    private var titleField: JBTextField? = null
    private var noteArea: ExpandableTextField? = null
    private var startSpinner: JSpinner? = null
    private var endSpinner: JSpinner? = null
    private var workspaceCombo: ComboBox<WorkspaceItem>? = null
    private var projectCombo: ComboBox<ProjectItem>? = null
    private var taskCombo: ComboBox<TaskItem>? = null
    private var statusLabel: JLabel? = null
    private var logButton: JButton? = null

    var isUpdatingCombos = false

    // ── Read accessors ────────────────────────────────────────────────────────

    val title: String get() = titleField?.text?.trim().orEmpty()
    val note: String get() = noteArea?.text?.trim().orEmpty()
    val startTime: Date? get() = startSpinner?.value as? Date
    val endTime: Date? get() = endSpinner?.value as? Date
    val selectedWorkspaceItem: WorkspaceItem? get() = workspaceCombo?.selectedItem as? WorkspaceItem
    val selectedProjectItem: ProjectItem? get() = projectCombo?.selectedItem as? ProjectItem
    val selectedTaskItem: TaskItem? get() = taskCombo?.selectedItem as? TaskItem

    // ── Write methods ─────────────────────────────────────────────────────────

    fun setWorkspaces(items: List<WorkspaceItem>, preselectId: String?) {
        isUpdatingCombos = true
        try {
            workspaceCombo?.model = DefaultComboBoxModel(items.toTypedArray())
            val idx = items.indexOfFirst { it.id == preselectId }
            workspaceCombo?.selectedIndex = if (idx >= 0) idx else if (items.isNotEmpty()) 0 else -1
        } finally {
            isUpdatingCombos = false
        }
    }

    fun setProjects(items: List<ProjectItem>, preselectId: String?) {
        isUpdatingCombos = true
        try {
            projectCombo?.model = DefaultComboBoxModel(items.toTypedArray())
            val idx = if (preselectId != null) items.drop(1).indexOfFirst { it.id == preselectId } else -1
            projectCombo?.selectedIndex = if (idx >= 0) idx + 1 else 0
        } finally {
            isUpdatingCombos = false
        }
    }

    fun setTasks(items: List<TaskItem>) {
        taskCombo?.model = DefaultComboBoxModel(items.toTypedArray())
        taskCombo?.selectedIndex = 0
    }

    fun setStatus(message: String, error: Boolean) {
        statusLabel?.foreground = if (error) JBColor.RED else JBColor.foreground()
        statusLabel?.text = message
    }

    fun setLogButtonEnabled(enabled: Boolean) {
        logButton?.isEnabled = enabled
    }

    fun resetTimes() {
        val now = Date()
        startSpinner?.value = Date(now.time - 3_600_000)
        endSpinner?.value = now
    }

    // ── Component builders ────────────────────────────────────────────────────

    fun buildContainer(): JComponent {
        val container = JPanel(BorderLayout())
        val scrollPane = JBScrollPane().apply { border = null }
        container.add(scrollPane, BorderLayout.CENTER)

        fun refresh() {
            scrollPane.viewport.view = buildContentPanel()
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

    private fun buildContentPanel(): JComponent {
        return buildFormPanel()
    }

    fun buildNoTokenPanel(): JComponent =
        panel {
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

    fun buildFormPanel(): JComponent {
        val tField = JBTextField()
        val nArea = ExpandableTextField()
        val now = Date()
        val sSpinner = JSpinner(SpinnerDateModel(Date(now.time - 3_600_000), null, null, Calendar.MINUTE)).apply {
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
            val sel = wCombo.selectedItem as? WorkspaceItem ?: return@addActionListener
            onWorkspaceSelected?.invoke(sel)
        }

        pCombo.addActionListener {
            if (isUpdatingCombos) return@addActionListener
            val wId = (wCombo.selectedItem as? WorkspaceItem)?.id ?: return@addActionListener
            val sel = pCombo.selectedItem as? ProjectItem ?: return@addActionListener
            onProjectSelected?.invoke(wId, sel)
        }

        return panel {
            row("Title:") { cell(tField).resizableColumn().align(AlignX.FILL) }
            row("Note:") { cell(nArea).resizableColumn().align(AlignX.FILL) }
            separator()
            row("Workspace:") { cell(wCombo).resizableColumn() }
            row("Project:") { cell(pCombo).resizableColumn() }
            row("Task:") { cell(tkCombo).resizableColumn() }
            separator()
            row("Start:") { cell(sSpinner) }
            row("End:") { cell(eSpinner) }
            row {
                button("Log Time") { onLogTime?.invoke() }
                    .align(AlignX.FILL)
                    .component.also { logButton = it }
            }
            row { cell(sLabel) }
        }.apply { border = JBUI.Borders.empty(8, 12) }
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    data class WorkspaceItem(val id: String, val name: String) {
        override fun toString() = name
    }

    data class ProjectItem(val id: String, val name: String) {
        override fun toString() = name
    }

    data class TaskItem(val id: String, val name: String) {
        override fun toString() = name
    }
}
