package io.agenttaskmanager.companion.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import io.agenttaskmanager.companion.backend.SessionBackendClient
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JPanel

class SessionToolWindowPanel(project: Project) : JPanel(BorderLayout()) {
    private val backendClient = SessionBackendClient()
    private val listModel = DefaultListModel<String>()
    private val sessionList = JBList(listModel)

    init {
        add(JBScrollPane(sessionList), BorderLayout.CENTER)
        refreshSessions()
    }

    private fun refreshSessions() {
        listModel.clear()
        backendClient.listSessions().forEach { session ->
            listModel.addElement("${session.title} [${session.lifecycleState}]")
        }
    }
}
