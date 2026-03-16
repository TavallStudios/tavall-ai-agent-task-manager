package com.agenttaskmanager.app.harness.tools;

import com.agenttaskmanager.app.dashboard.DashboardSummaryService;
import com.agenttaskmanager.app.harness.state.HarnessStateService;
import com.agenttaskmanager.app.mcp.DownstreamMcpToolCall;
import com.agenttaskmanager.app.mcp.DownstreamMcpToolClientService;
import com.agenttaskmanager.app.mcp.DownstreamMcpToolResult;
import com.agenttaskmanager.app.orchestration.SharedTaskContextService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;

@Service
public class HarnessToolBundleService {

  private final DashboardSummaryService dashboardSummaryService;
  private final DownstreamMcpToolClientService downstreamMcpToolClientService;
  private final HarnessStateService harnessStateService;
  private final SharedTaskContextService sharedTaskContextService;

  public HarnessToolBundleService(
      DashboardSummaryService dashboardSummaryService,
      DownstreamMcpToolClientService downstreamMcpToolClientService,
      HarnessStateService harnessStateService,
      SharedTaskContextService sharedTaskContextService
  ) {
    this.dashboardSummaryService = dashboardSummaryService;
    this.downstreamMcpToolClientService = downstreamMcpToolClientService;
    this.harnessStateService = harnessStateService;
    this.sharedTaskContextService = sharedTaskContextService;
  }

  public HarnessToolBundleResult executeBundle(HarnessToolBundleRequest request) {
    HarnessToolBundleType bundleType = request.bundleType();
    String repoPath = normalizeRepoPath(request.repoPath());
    int limit = normalizeLimit(request.limit());

    ExecutorService executor = Executors.newFixedThreadPool(bundleType == HarnessToolBundleType.REPO_CONTEXT ? 2 : 4);
    try {
      List<CompletableFuture<Map.Entry<String, Object>>> internalFutures = internalSections(request, bundleType, executor);
      CompletableFuture<List<DownstreamMcpToolResult>> downstreamFuture = CompletableFuture.supplyAsync(
          () -> downstreamMcpToolClientService.callTools(request.projectKey(), downstreamCalls(bundleType, repoPath, request.queryText(), limit)),
          executor
      );

      Map<String, Object> sections = new LinkedHashMap<>();
      for (CompletableFuture<Map.Entry<String, Object>> future : internalFutures) {
        Map.Entry<String, Object> section = future.join();
        sections.put(section.getKey(), section.getValue());
      }

      List<DownstreamMcpToolResult> downstreamResults = downstreamFuture.join();
      sections.put("downstream", downstreamSections(downstreamResults));

      Map<String, Object> summary = new LinkedHashMap<>();
      summary.put("bundleName", bundleType.value());
      summary.put("taskId", request.taskId());
      summary.put("workerTaskId", request.workerTaskId());
      summary.put("repoPath", repoPath);
      summary.put("downstreamCalls", downstreamResults.size());
      summary.put("downstreamErrors", downstreamResults.stream().filter(DownstreamMcpToolResult::isError).count());
      summary.put("internalSections", sections.size() - 1);
      return new HarnessToolBundleResult(bundleType.value(), summary, sections, downstreamResults);
    } finally {
      executor.shutdownNow();
    }
  }

  private List<CompletableFuture<Map.Entry<String, Object>>> internalSections(
      HarnessToolBundleRequest request,
      HarnessToolBundleType bundleType,
      ExecutorService executor
  ) {
    List<CompletableFuture<Map.Entry<String, Object>>> futures = new ArrayList<>();

    if (request.taskId() != null && !request.taskId().isBlank()
        && bundleType != HarnessToolBundleType.REPO_CONTEXT) {
      futures.add(CompletableFuture.supplyAsync(
          () -> Map.entry("harnessState", harnessStateService.loadState(request.taskId())),
          executor
      ));
      futures.add(CompletableFuture.supplyAsync(
          () -> Map.entry("taskContext", sharedTaskContextService.loadTaskContext(request.taskId())),
          executor
      ));
      futures.add(CompletableFuture.supplyAsync(
          () -> Map.entry("sharedTaskContext", sharedTaskContextService.listSharedTaskContext(request.taskId())),
          executor
      ));
    }

    if (request.queryText() != null && !request.queryText().isBlank()
        && bundleType != HarnessToolBundleType.REPO_CONTEXT) {
      futures.add(CompletableFuture.supplyAsync(
          () -> Map.entry("semanticContext", semanticContext(request.projectKey(), request.queryText(), normalizeLimit(request.limit()))),
          executor
      ));
    }

    if (bundleType == HarnessToolBundleType.WORKER_CONTEXT) {
      futures.add(CompletableFuture.supplyAsync(
          () -> Map.entry("dashboardSummary", dashboardSummaryService.loadDashboardSummary()),
          executor
      ));
    }

    if (bundleType == HarnessToolBundleType.JAVA_CONTEXT) {
      futures.add(CompletableFuture.supplyAsync(
          () -> Map.entry("cleanJavaRules", readDoc("RULES.md")),
          executor
      ));
    }

    return futures;
  }

  private List<DownstreamMcpToolCall> downstreamCalls(
      HarnessToolBundleType bundleType,
      String repoPath,
      String queryText,
      int limit
  ) {
    List<DownstreamMcpToolCall> calls = new ArrayList<>();
    calls.add(new DownstreamMcpToolCall("directory", "filesystem", "list_directory", Map.of("path", repoPath)));
    calls.add(new DownstreamMcpToolCall("gitStatus", "git", "git_status", Map.of("repo_path", repoPath)));
    calls.add(new DownstreamMcpToolCall("gitDiff", "git", "git_diff_unstaged", Map.of("repo_path", repoPath, "context_lines", 20)));

    if (queryText != null && !queryText.isBlank()) {
      calls.add(new DownstreamMcpToolCall(
          "search",
          "ripgrep",
          "advanced-search",
          Map.of(
              "pattern", queryText,
              "path", repoPath,
              "maxResults", limit,
              "showLineNumbers", true
          )
      ));
    } else {
      calls.add(new DownstreamMcpToolCall("files", "ripgrep", "list-files", Map.of("path", repoPath)));
    }

    if (bundleType == HarnessToolBundleType.JAVA_CONTEXT) {
      calls.add(new DownstreamMcpToolCall(
          "javaFiles",
          "ripgrep",
          "list-files",
          Map.of("path", repoPath, "fileType", "java")
      ));
    }
    return calls;
  }

  private Map<String, Object> downstreamSections(List<DownstreamMcpToolResult> results) {
    Map<String, Object> sections = new LinkedHashMap<>();
    for (DownstreamMcpToolResult result : results) {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("serverName", result.serverName());
      payload.put("toolName", result.toolName());
      payload.put("status", result.status());
      payload.put("structuredContent", result.structuredContent());
      payload.put("textContent", result.textContent());
      payload.put("stderr", result.stderr());
      payload.put("errorMessage", result.errorMessage());
      payload.put("durationMs", result.durationMs());
      sections.put(result.key(), payload);
    }
    return sections;
  }

  private Object semanticContext(String projectKey, String queryText, int limit) {
    return projectKey == null || projectKey.isBlank()
        ? sharedTaskContextService.searchRelatedContexts(queryText, limit)
        : sharedTaskContextService.searchProjectRelatedContexts(projectKey, queryText, limit);
  }

  private String readDoc(String fileName) {
    try {
      return Files.readString(repoRoot().resolve(fileName), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      return "Failed to read " + fileName + ": " + exception.getMessage();
    }
  }

  private String normalizeRepoPath(String repoPath) {
    String normalized = repoPath == null || repoPath.isBlank() ? "." : repoPath;
    return Path.of(normalized).toAbsolutePath().normalize().toString();
  }

  private int normalizeLimit(Integer limit) {
    if (limit == null) {
      return 8;
    }
    return Math.max(1, Math.min(limit, 25));
  }

  private Path repoRoot() {
    Path current = Path.of(".").toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isRegularFile(current.resolve("AGENTS.md"))
          && Files.isRegularFile(current.resolve("RULES.md"))
          && Files.isRegularFile(current.resolve("pom.xml"))) {
        return current;
      }
      current = current.getParent();
    }
    return Path.of(".").toAbsolutePath().normalize();
  }
}
