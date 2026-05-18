package no.paral.clockifyplugin.ui

import no.paral.clockifyplugin.settings.ClockifySettingsConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project

object ClockifyUiUtils {
    fun openSettings(project: Project) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, ClockifySettingsConfigurable::class.java)
    }
}
