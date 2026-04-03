package io.agenttaskmanager.companion.backend

import io.agenttaskmanager.companion.model.SessionDetail
import io.agenttaskmanager.companion.model.SessionSummary
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class SessionBackendClient(
    private val backendUrl: String = "http://localhost:9000"
) {
    private val httpClient = HttpClient.newHttpClient()

    fun listSessions(): List<SessionSummary> {
        val request = HttpRequest.newBuilder(URI.create("$backendUrl/api/codex-client/sessions")).GET().build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            return emptyList()
        }
        return emptyList()
    }

    fun getSession(sessionId: String): SessionDetail? {
        val request = HttpRequest.newBuilder(URI.create("$backendUrl/api/codex-client/sessions/$sessionId")).GET().build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            return null
        }
        return null
    }
}
