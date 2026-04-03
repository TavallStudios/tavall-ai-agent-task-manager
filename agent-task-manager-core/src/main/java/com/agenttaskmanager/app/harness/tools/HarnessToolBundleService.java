package com.agenttaskmanager.app.harness.tools;

import com.agenttaskmanager.app.dashboard.DashboardSummaryService;
import com.agenttaskmanager.app.harness.cleanjava.CleanJavaTaskContextService;
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
  private final CleanJavaTaskContextService cleanJavaTaskContextService;
  private final DownstreamMcpToolClientService downstreamMcpToolClientService;
  private final HarnessStateService harnessStateService;
  private final RemoteHarnessToolBundleClientService remoteHarnessToolBundleClientService;
  private final SharedTaskContextService sharedTaskContextService;

  public HarnessToolBundleService(
      DashboardSummaryService dashboardSummaryService,
      CleanJavaTaskContextService cleanJavaTaskContextService,
      DownstreamMcpToolClientService downstreamMcpToolClientService,
      HarnessStateService harnessStateService,
      RemoteHarnessToolBundleClientService remoteHarnessToolBundleClientService,
      SharedTaskContextService sharedTaskContextService
  ) {
    this.dashboardSummaryService = dashboardSummaryService;
    this.cleanJavaTaskContextService = cleanJavaTaskContextService;
    this.downstreamMcpToolClientService = downstreamMcpToolClientService;
    this.harnessStateService = harnessStateService;
    this.remoteHarnessToolBundleClientService = remoteHarnessToolBundleClientService;
    this.sharedTaskContextService = sharedTaskContextService;
  }

  public HarnessToolBundleResult executeBundle(HarnessToolBundleRequest request) {
    HarnessToolBundleType bundleType = request.bundleType();
    String repoPath = normalizeRepoPath(request.repoPath());
    int limit = normalizeLimit(request.limit());
    HarnessToolBundleRequest normalizedRequest = new HarnessToolBundleRequest(
        bundleType.value(),
        request.taskId(),
        request.workerTaskId(),
        request.projectKey(),
        repoPath,
        request.queryText(),
        limit
    );
    if (bundleType == HarnessToolBundleType.REPO_CONTEXT && remoteHarnessToolBundleClientService.isEnabled()) {
      return remoteRepoContextResult(normalizedRequest);
    }

    ExecutorService executor = Executors.newFixedThreadPool(bundleType == HarnessToolBundleType.REPO_CONTEXT ? 2 : 4);
    try {
      List<CompletableFuture<Map.Entry<String, Object>>> internalFutures = internalSections(normalizedRequest, bundleType, executor);
      CompletableFuture<RepoContextBundle> repoContextFuture = CompletableFuture.supplyAsync(
          () -> loadRepoContext(normalizedRequest, bundleType, repoPath, limit),
          executor
      );

      Map<String, Object> sections = new LinkedHashMap<>();
      for (CompletableFuture<Map.Entry<String, Object>> future : internalFutures) {
        Map.Entry<String, Object> section = future.join();
        sections.put(section.getKey(), section.getValue());
      }

      RepoContextBundle repoContext = repoContextFuture.join();
      sections.put("downstream", repoContext.downstreamSections());

      Map<String, Object> summary = new LinkedHashMap<>();
      summary.put("bundleName", bundleType.value());
      summary.put("taskId", normalizedRequest.taskId());
      summary.put("workerTaskId", normalizedRequest.workerTaskId());
      summary.put("repoPath", repoPath);
      summary.put("downstreamCalls", repoContext.downstreamResults().size());
      summary.put("downstreamErrors", repoContext.downstreamResults().stream().filter(DownstreamMcpToolResult::isError).count());
      summary.put("internalSections", sections.size() - 1);
      summary.put("repoContextSource", repoContext.source());
      return new HarnessToolBundleResult(bundleType.value(), summary, sections, repoContext.downstreamResults());
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
      futures.add(CompletableFuture.supplyAsync(
          () -> Map.entry(
              "cleanJavaContext",
              cleanJavaTaskContextService.buildContext(
                  request.taskId(),
                  request.workerTaskId(),
                  request.projectKey(),
                  Path.of(normalizeRepoPath(request.repoPath())),
                  request.queryText()
              )
          ),
          executor
      ));
    }

    return futures;
  }

  private RepoContextBundle loadRepoContext(
      HarnessToolBundleRequest request,
      HarnessToolBundleType bundleType,
      String repoPath,
      int limit
  ) {
    if (remoteHarnessToolBundleClientService.isEnabled()) {
      HarnessToolBundleResult remoteResult = remoteHarnessToolBundleClientService.loadRemoteRepoContext(request);
      return new RepoContextBundle("remote-mcp", downstreamSection(remoteResult), remoteResult.downstreamCalls());
    }
    List<DownstreamMcpToolResult> downstreamResults = downstreamMcpToolClientService.callTools(
        request.projectKey(),
        downstreamCalls(bundleType, repoPath, request.queryText(), limit)
    );
    return new RepoContextBundle("local-downstream", downstreamSections(downstreamResults), downstreamResults);
  }

  private HarnessToolBundleResult remoteRepoContextResult(HarnessToolBundleRequest request) {
    HarnessToolBundleResult remoteResult = remoteHarnessToolBundleClientService.loadRemoteRepoContext(request);
    Map<String, Object> summary = new LinkedHashMap<>(remoteResult.summary());
    summary.put("bundleName", HarnessToolBundleType.REPO_CONTEXT.value());
    summary.put("taskId", request.taskId());
    summary.put("workerTaskId", request.workerTaskId());
    summary.put("repoPath", request.repoPath());
    summary.put("repoContextSource", "remote-mcp");
    return new HarnessToolBundleResult(
        HarnessToolBundleType.REPO_CONTEXT.value(),
        summary,
        remoteResult.sections(),
        remoteResult.downstreamCalls()
    );
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

  @SuppressWarnings("unchecked")
  private Map<String, Object> downstreamSection(HarnessToolBundleResult remoteResult) {
    Object section = remoteResult.sections().get("downstream");
    if (section instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    return downstreamSections(remoteResult.downstreamCalls());
  }

  private Object semanticContext(String projectKey, String queryText, int limit) {
    if (projectKey == null || projectKey.isBlank()) {
      return List.of();
    }
    return sharedTaskContextService.searchProjectRelatedContexts(projectKey, queryText, limit);
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

  private record RepoContextBundle(
      String source,
      Map<String, Object> downstreamSections,
      List<DownstreamMcpToolResult> downstreamResults
  ) {
  }
}
