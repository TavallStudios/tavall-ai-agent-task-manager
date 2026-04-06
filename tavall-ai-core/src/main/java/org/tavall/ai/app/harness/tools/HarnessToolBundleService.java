package org.tavall.ai.app.harness.tools;

import org.tavall.ai.app.concurrent.AsyncTask;
import org.tavall.ai.app.dashboard.DashboardSummaryService;
import org.tavall.ai.app.desktop.DesktopMcpPolicyService;
import org.tavall.ai.app.harness.cleanjava.CleanJavaTaskContextService;
import org.tavall.ai.app.harness.state.HarnessStateService;
import org.tavall.ai.app.mcp.DownstreamMcpToolCall;
import org.tavall.ai.app.mcp.DownstreamMcpToolClientService;
import org.tavall.ai.app.mcp.DownstreamMcpToolResult;
import org.tavall.ai.app.orchestration.HarnessMemoryService;
import org.tavall.ai.app.orchestration.SharedTaskContextService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;
import org.springframework.stereotype.Service;

@Service
public class HarnessToolBundleService {

  private final DashboardSummaryService dashboardSummaryService;
  private final CleanJavaTaskContextService cleanJavaTaskContextService;
  private final DesktopMcpPolicyService desktopMcpPolicyService;
  private final DownstreamMcpToolClientService downstreamMcpToolClientService;
  private final HarnessMemoryService harnessMemoryService;
  private final HarnessStateService harnessStateService;
  private final RemoteHarnessToolBundleClientService remoteHarnessToolBundleClientService;
  private final SharedTaskContextService sharedTaskContextService;

  public HarnessToolBundleService(
      DashboardSummaryService dashboardSummaryService,
      CleanJavaTaskContextService cleanJavaTaskContextService,
      DesktopMcpPolicyService desktopMcpPolicyService,
      DownstreamMcpToolClientService downstreamMcpToolClientService,
      HarnessMemoryService harnessMemoryService,
      HarnessStateService harnessStateService,
      RemoteHarnessToolBundleClientService remoteHarnessToolBundleClientService,
      SharedTaskContextService sharedTaskContextService
  ) {
    this.dashboardSummaryService = dashboardSummaryService;
    this.cleanJavaTaskContextService = cleanJavaTaskContextService;
    this.desktopMcpPolicyService = desktopMcpPolicyService;
    this.downstreamMcpToolClientService = downstreamMcpToolClientService;
    this.harnessMemoryService = harnessMemoryService;
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

    List<Callable<SectionResult>> tasks = new ArrayList<>();
    tasks.addAll(internalSectionTasks(normalizedRequest, bundleType));
    tasks.add(() -> new SectionResult(
        "repoContext",
        loadRepoContext(normalizedRequest, bundleType, repoPath, limit)
    ));

    int internalCap = desktopMcpPolicyService
        .loadHarnessPreferenceCaps(normalizedRequest.projectKey())
        .internalConcurrencyCap();
    List<SectionResult> results = runBatchedTasks(tasks, internalCap, "harness-bundle-" + bundleType.value());

    Map<String, Object> sections = new LinkedHashMap<>();
    RepoContextBundle repoContext = null;
    for (SectionResult result : results) {
      if ("repoContext".equals(result.key())) {
        repoContext = (RepoContextBundle) result.value();
      } else {
        sections.put(result.key(), result.value());
      }
    }
    if (repoContext == null) {
      repoContext = loadRepoContext(normalizedRequest, bundleType, repoPath, limit);
    }
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
    Object memorySection = sections.get("memory");
    if (memorySection instanceof Map<?, ?> map) {
      summary.put("memoryStatus", map.get("status"));
      summary.put("qdrantHealth", map.get("qdrantHealth"));
    }
    return new HarnessToolBundleResult(bundleType.value(), summary, sections, repoContext.downstreamResults());
  }

  private List<Callable<SectionResult>> internalSectionTasks(
      HarnessToolBundleRequest request,
      HarnessToolBundleType bundleType
  ) {
    List<Callable<SectionResult>> tasks = new ArrayList<>();

    if (request.taskId() != null && !request.taskId().isBlank()
        && bundleType != HarnessToolBundleType.REPO_CONTEXT) {
      tasks.add(() -> new SectionResult("harnessState", harnessStateService.loadState(request.taskId())));
      tasks.add(() -> new SectionResult("taskContext", sharedTaskContextService.loadTaskContext(request.taskId())));
      tasks.add(() -> new SectionResult("sharedTaskContext", sharedTaskContextService.listSharedTaskContext(request.taskId())));
    }

    if (request.queryText() != null && !request.queryText().isBlank()
        && bundleType != HarnessToolBundleType.REPO_CONTEXT) {
      tasks.add(() -> new SectionResult(
          "semanticContext",
          semanticContext(request.projectKey(), request.queryText(), normalizeLimit(request.limit()))
      ));
    }

    if (bundleType == HarnessToolBundleType.WORKER_CONTEXT) {
      tasks.add(() -> new SectionResult("dashboardSummary", dashboardSummaryService.loadDashboardSummary()));
    }

    if (bundleType == HarnessToolBundleType.LANGUAGE_CONTEXT) {
      tasks.add(() -> new SectionResult("cleanJavaRules", readDoc("RULES.md")));
      tasks.add(() -> new SectionResult(
          "cleanJavaContext",
          cleanJavaTaskContextService.buildContext(
              request.taskId(),
              request.workerTaskId(),
              request.projectKey(),
              Path.of(normalizeRepoPath(request.repoPath())),
              request.queryText()
          )
      ));
    }

    if (request.projectKey() != null && !request.projectKey().isBlank()) {
      tasks.add(() -> new SectionResult(
          "memory",
          harnessMemoryService.buildBundleMemory(
              bundleType.value(),
              request.projectKey(),
              request.taskId(),
              request.workerTaskId(),
              request.repoPath(),
              request.queryText()
          )
      ));
    }

    return tasks;
  }

  private <T> List<T> runBatchedTasks(List<? extends Callable<? extends T>> tasks, int cap, String scopeName) {
    if (tasks.isEmpty()) {
      return List.of();
    }
    int batchSize = cap <= 0 ? tasks.size() : Math.min(cap, tasks.size());
    List<T> results = new ArrayList<>(tasks.size());
    for (int index = 0; index < tasks.size(); index += batchSize) {
      int end = Math.min(index + batchSize, tasks.size());
      List<? extends Callable<? extends T>> batch = tasks.subList(index, end);
      results.addAll(runBatch(batch, scopeName));
    }
    return results;
  }

  private <T> List<T> runBatch(List<? extends Callable<? extends T>> batch, String scopeName) {
    AsyncTask.ScopeOptions options = AsyncTask.ScopeOptions.defaults().withName(scopeName);
    try {
      return AsyncTask.runMultipleAsync(batch, options);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Harness bundle tasks interrupted.", exception);
    } catch (StructuredTaskScope.TimeoutException | StructuredTaskScope.FailedException exception) {
      throw new IllegalStateException("Harness bundle tasks failed.", exception);
    }
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

    if (bundleType == HarnessToolBundleType.LANGUAGE_CONTEXT) {
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

  private record SectionResult(String key, Object value) {
  }
}

