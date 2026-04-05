package com.agenttaskmanager.app.http;

import com.agenttaskmanager.app.desktop.DesktopMcpPolicyService;
import com.agenttaskmanager.app.desktop.DesktopOperationCatalogService;
import com.agenttaskmanager.app.desktop.DesktopRemoteRunnerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class DesktopControlPlaneServlet extends HttpServlet {

  private final DesktopMcpPolicyService mcpPolicyService;
  private final DesktopRemoteRunnerService remoteRunnerService;
  private final DesktopOperationCatalogService operationCatalogService;
  private final ObjectMapper objectMapper;

  public DesktopControlPlaneServlet(
      DesktopMcpPolicyService mcpPolicyService,
      DesktopRemoteRunnerService remoteRunnerService,
      DesktopOperationCatalogService operationCatalogService,
      ObjectMapper objectMapper
  ) {
    this.mcpPolicyService = mcpPolicyService;
    this.remoteRunnerService = remoteRunnerService;
    this.operationCatalogService = operationCatalogService;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    route(request, response);
  }

  @Override
  protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
    route(request, response);
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    route(request, response);
  }

  @Override
  protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
    route(request, response);
  }

  private void route(HttpServletRequest request, HttpServletResponse response) throws IOException {
    try {
      String method = request.getMethod();
      String path = normalizePath(request);
      if ("GET".equals(method) && "/codex-client/operations".equals(path)) {
        writeJson(response, HttpServletResponse.SC_OK, operationCatalogService.catalog());
        return;
      }
      if (path.startsWith("/desktop/mcp-policy/")) {
        routeMcpPolicy(request, response, method, path);
        return;
      }
      if (path.startsWith("/desktop/remote-runners")) {
        routeRemoteRunners(request, response, method, path);
        return;
      }
      if (path.startsWith("/desktop/remote-scenarios")) {
        routeRemoteScenarios(request, response, method, path);
        return;
      }
      writeJson(response, HttpServletResponse.SC_NOT_FOUND, Map.of("error", "not_found"));
    } catch (IllegalArgumentException exception) {
      writeJson(response, HttpServletResponse.SC_BAD_REQUEST, Map.of("error", exception.getMessage()));
    } catch (Exception exception) {
      writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, Map.of("error", exception.getMessage()));
    }
  }

  private void routeMcpPolicy(
      HttpServletRequest request,
      HttpServletResponse response,
      String method,
      String path
  ) throws IOException {
    if ("GET".equals(method) && "/desktop/mcp-policy/global".equals(path)) {
      writeJson(response, HttpServletResponse.SC_OK, mcpPolicyService.loadGlobalPolicy());
      return;
    }
    if ("PUT".equals(method) && "/desktop/mcp-policy/global".equals(path)) {
      writeJson(response, HttpServletResponse.SC_OK, mcpPolicyService.saveGlobalPolicy(readBody(request)));
      return;
    }
    if ("GET".equals(method) && path.startsWith("/desktop/mcp-policy/repos/")) {
      writeJson(response, HttpServletResponse.SC_OK, mcpPolicyService.loadRepoPolicy(readSuffix(path, "/desktop/mcp-policy/repos/")));
      return;
    }
    if ("PUT".equals(method) && path.startsWith("/desktop/mcp-policy/repos/")) {
      String scopeKey = readSuffix(path, "/desktop/mcp-policy/repos/");
      writeJson(response, HttpServletResponse.SC_OK, mcpPolicyService.saveRepoPolicy(scopeKey, readBody(request)));
      return;
    }
    if ("GET".equals(method) && "/desktop/mcp-policy/preview".equals(path)) {
      String scopeKey = readString(request.getParameter("scopeKey"), "workspace-default");
      writeJson(response, HttpServletResponse.SC_OK, mcpPolicyService.loadMergedPreview(scopeKey));
      return;
    }
    writeJson(response, HttpServletResponse.SC_NOT_FOUND, Map.of("error", "not_found"));
  }

  private void routeRemoteRunners(
      HttpServletRequest request,
      HttpServletResponse response,
      String method,
      String path
  ) throws IOException {
    if ("GET".equals(method) && "/desktop/remote-runners".equals(path)) {
      writeJson(response, HttpServletResponse.SC_OK, remoteRunnerService.listProfiles());
      return;
    }
    if ("PUT".equals(method) && path.startsWith("/desktop/remote-runners/")) {
      String profileId = readSuffix(path, "/desktop/remote-runners/");
      writeJson(response, HttpServletResponse.SC_OK, remoteRunnerService.upsertProfile(profileId, readBody(request)));
      return;
    }
    if ("DELETE".equals(method) && path.startsWith("/desktop/remote-runners/")) {
      String profileId = readSuffix(path, "/desktop/remote-runners/");
      remoteRunnerService.deleteProfile(profileId);
      writeJson(response, HttpServletResponse.SC_OK, Map.of("deleted", profileId));
      return;
    }
    if ("POST".equals(method) && path.endsWith("/select")) {
      String profileId = readSuffix(path.substring(0, path.length() - "/select".length()), "/desktop/remote-runners/");
      remoteRunnerService.selectProfile(profileId);
      writeJson(response, HttpServletResponse.SC_OK, Map.of("selected", profileId));
      return;
    }
    if ("POST".equals(method) && path.endsWith("/test")) {
      String profileId = readSuffix(path.substring(0, path.length() - "/test".length()), "/desktop/remote-runners/");
      writeJson(response, HttpServletResponse.SC_OK, remoteRunnerService.testProfile(profileId, readBody(request)));
      return;
    }
    writeJson(response, HttpServletResponse.SC_NOT_FOUND, Map.of("error", "not_found"));
  }

  private void routeRemoteScenarios(
      HttpServletRequest request,
      HttpServletResponse response,
      String method,
      String path
  ) throws IOException {
    if ("POST".equals(method) && "/desktop/remote-scenarios/run".equals(path)) {
      writeJson(response, HttpServletResponse.SC_OK, remoteRunnerService.startScenarioRun(readBody(request)));
      return;
    }
    if ("GET".equals(method) && path.startsWith("/desktop/remote-scenarios/") && path.endsWith("/artifacts")) {
      String sessionId = readSuffix(
          path.substring(0, path.length() - "/artifacts".length()),
          "/desktop/remote-scenarios/"
      );
      writeJson(response, HttpServletResponse.SC_OK, remoteRunnerService.scenarioArtifacts(sessionId));
      return;
    }
    if ("GET".equals(method) && path.startsWith("/desktop/remote-scenarios/")) {
      writeJson(
          response,
          HttpServletResponse.SC_OK,
          remoteRunnerService.scenarioStatus(readSuffix(path, "/desktop/remote-scenarios/"))
      );
      return;
    }
    writeJson(response, HttpServletResponse.SC_NOT_FOUND, Map.of("error", "not_found"));
  }

  private Map<String, Object> readBody(HttpServletRequest request) throws IOException {
    if (request.getContentLengthLong() <= 0) {
      return Map.of();
    }
    return objectMapper.readValue(request.getInputStream(), new TypeReference<>() {
    });
  }

  private void writeJson(HttpServletResponse response, int statusCode, Object payload) throws IOException {
    response.setStatus(statusCode);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getWriter(), payload);
  }

  private String normalizePath(HttpServletRequest request) {
    String path = request.getPathInfo();
    if (path == null || path.isBlank()) {
      return "/";
    }
    return path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
  }

  private String readSuffix(String path, String prefix) {
    if (!path.startsWith(prefix) || path.length() <= prefix.length()) {
      throw new IllegalArgumentException("Missing path parameter.");
    }
    return path.substring(prefix.length());
  }

  private String readString(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.strip();
  }
}

