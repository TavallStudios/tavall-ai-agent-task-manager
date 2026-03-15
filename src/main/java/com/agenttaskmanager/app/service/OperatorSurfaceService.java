package com.agenttaskmanager.app.service;

import com.agenttaskmanager.app.config.McpServerProperties;
import com.agenttaskmanager.app.config.OperatorSurfaceProperties;
import com.agenttaskmanager.app.config.RepoCatalogProperties;
import com.agenttaskmanager.app.model.OperatorSurfaceStatus;
import com.agenttaskmanager.app.model.OperatorToolCard;
import com.agenttaskmanager.app.model.RuntimeStatus;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OperatorSurfaceService {

  private final OperatorSurfaceProperties properties;
  private final RepoCatalogProperties repoCatalogProperties;
  private final McpServerProperties mcpServerProperties;
  private final RuntimeStatusService runtimeStatusService;
  private final HttpClient httpClient;

  public OperatorSurfaceService(
      OperatorSurfaceProperties properties,
      RepoCatalogProperties repoCatalogProperties,
      McpServerProperties mcpServerProperties,
      RuntimeStatusService runtimeStatusService
  ) {
    this.properties = properties;
    this.repoCatalogProperties = repoCatalogProperties;
    this.mcpServerProperties = mcpServerProperties;
    this.runtimeStatusService = runtimeStatusService;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
  }

  public OperatorSurfaceStatus loadStatus() {
    RuntimeStatus runtimeStatus = runtimeStatusService.getRuntimeStatus();
    return new OperatorSurfaceStatus(
        OffsetDateTime.now(ZoneOffset.UTC),
        List.copyOf(repoCatalogProperties.getRoots()),
        List.of(
            "Open the browser IDE first when the remote editor transport starts dropping.",
            "Queue work through the prompt bridge when you only need remote execution.",
            "Use direct SSH only as a break-glass path for service recovery."
        ),
        List.of(
            dashboardCard(runtimeStatus),
            browserIdeCard(),
            promptBridgeCard(runtimeStatus),
            mcpCard(),
            shellCard()
        )
    );
  }

  private OperatorToolCard dashboardCard(RuntimeStatus runtimeStatus) {
    String summary = runtimeStatus.bridgeEnabled()
        ? "Dashboard is live and the prompt bridge is " + (runtimeStatus.bridgeOnline() ? "online." : "degraded.")
        : "Dashboard is live. Prompt bridge is disabled.";
    return new OperatorToolCard(
        "dashboard",
        "Task dashboard",
        "online",
        summary,
        "Use this for queues, orchestration state, task history, and bridge activity.",
        externalUrl(properties.getDashboardPath()),
        "Open dashboard",
        null
    );
  }

  private OperatorToolCard browserIdeCard() {
    ProbeResult ideProbe = probeUrl(properties.getIdeHealthUrl());
    String status = ideProbe.reachable() ? "online" : "offline";
    String summary = ideProbe.reachable()
        ? "code-server is answering for workspace " + properties.getIdeWorkspace() + "."
        : "code-server is not answering on the configured health endpoint.";
    String description = ideProbe.reachable()
        ? "Use the browser IDE when Remote SSH gets wedged or tunnel state goes stale."
        : "If this stays offline, inspect the code-server service before opening more SSH tunnels.";
    return new OperatorToolCard(
        "browser-ide",
        "Browser IDE",
        status,
        summary,
        description,
        externalUrl(properties.getIdePath()),
        "Open browser IDE",
        properties.getSshCommand() + " \"sudo systemctl status code-server@ubuntu.service\""
    );
  }

  private OperatorToolCard promptBridgeCard(RuntimeStatus runtimeStatus) {
    String status = !runtimeStatus.bridgeEnabled()
        ? "configured"
        : runtimeStatus.bridgeOnline() ? "online" : "degraded";
    String summary = !runtimeStatus.bridgeEnabled()
        ? "Prompt bridge is disabled."
        : runtimeStatus.bridgeOnline()
            ? "Bridge session " + defaultValue(runtimeStatus.bridgeSessionId(), "pending") + " is active."
            : "Bridge is enabled but not currently online.";
    String description = runtimeStatus.bridgeActiveRequestId() == null
        ? "Use this lane for queued, headless work when you do not need an interactive IDE."
        : "Active request " + runtimeStatus.bridgeActiveRequestId() + " is currently running.";
    return new OperatorToolCard(
        "prompt-bridge",
        "Prompt bridge",
        status,
        summary,
        description,
        externalUrl(properties.getDashboardPath()),
        "Queue work",
        properties.getSshCommand() + " \"sudo journalctl -u agenttaskmanager.service -n 100 --no-pager\""
    );
  }

  private OperatorToolCard mcpCard() {
    String endpoint = buildUrl(mcpServerProperties.getBaseUrl(), mcpServerProperties.getEndpoint());
    return new OperatorToolCard(
        "mcp",
        "MCP endpoint",
        StringUtils.hasText(endpoint) ? "configured" : "offline",
        StringUtils.hasText(endpoint)
            ? "Mounted at " + endpoint
            : "No MCP endpoint is configured.",
        "Use this for tool and resource automation without depending on an editor tunnel.",
        endpoint,
        "Open MCP route",
        null
    );
  }

  private OperatorToolCard shellCard() {
    return new OperatorToolCard(
        "shell",
        "Break-glass shell",
        "configured",
        "Direct terminal access stays available as the final recovery lane.",
        "Keep SSH as a fallback, not the primary workspace transport.",
        null,
        null,
        properties.getSshCommand()
    );
  }

  private String externalUrl(String path) {
    return buildUrl(properties.getExternalBaseUrl(), path);
  }

  private String buildUrl(String base, String path) {
    if (!StringUtils.hasText(base)) {
      return "";
    }
    if (!StringUtils.hasText(path)) {
      return base;
    }
    String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    String normalizedPath = path.startsWith("/") ? path : "/" + path;
    return normalizedBase + normalizedPath;
  }

  private ProbeResult probeUrl(String url) {
    if (!StringUtils.hasText(url)) {
      return new ProbeResult(false);
    }

    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
          .timeout(Duration.ofSeconds(2))
          .GET()
          .build();
      int statusCode = httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
      return new ProbeResult(statusCode >= 200 && statusCode < 500);
    } catch (IOException | InterruptedException | IllegalArgumentException exception) {
      if (exception instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return new ProbeResult(false);
    }
  }

  private String defaultValue(String value, String fallback) {
    return StringUtils.hasText(value) ? value : fallback;
  }

  private record ProbeResult(boolean reachable) {
  }
}
