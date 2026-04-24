package org.tavall.ai.app.orchestration;

import org.tavall.ai.app.model.PromptThreadMemoryLookupResult;
import org.tavall.ai.app.model.orchestration.WorkerTask;
import org.tavall.ai.app.retrieval.QdrantHealthService;
import org.tavall.ai.app.retrieval.RepoSemanticSyncService;
import org.tavall.ai.app.service.PromptThreadMemoryService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class HarnessMemoryService {

  private final PromptThreadMemoryService promptThreadMemoryService;
  private final QdrantHealthService qdrantHealthService;
  private final RepoSemanticSyncService repoSemanticSyncService;

  public HarnessMemoryService(
      PromptThreadMemoryService promptThreadMemoryService,
      QdrantHealthService qdrantHealthService,
      RepoSemanticSyncService repoSemanticSyncService
  ) {
    this.promptThreadMemoryService = promptThreadMemoryService;
    this.qdrantHealthService = qdrantHealthService;
    this.repoSemanticSyncService = repoSemanticSyncService;
  }

  public MemorySnapshot lookupForWorker(String projectKey, WorkerTask workerTask) {
    return lookup(projectKey, workerThreadKey(workerTask.workerTaskId()), workerQuery(workerTask));
  }

  public MemorySnapshot lookup(String projectKey, String threadKey, String queryText) {
    QdrantHealthService.Snapshot qdrantSnapshot = qdrantHealthService.currentSnapshot();
    if (projectKey == null || projectKey.isBlank()) {
      return new MemorySnapshot(
          "skipped",
          qdrantSnapshot.status(),
          false,
          queryText == null ? "" : queryText.strip(),
          new PromptThreadMemoryLookupResult("", null, "Memory lookup skipped.", "Memory lookup skipped.", java.util.List.of(), java.util.List.of(), java.util.List.of()),
          Map.of()
      );
    }
    String effectiveQuery = queryText == null || queryText.isBlank() ? "project " + projectKey : queryText.strip();
    PromptThreadMemoryLookupResult lookup = promptThreadMemoryService.lookup(projectKey, threadKey, effectiveQuery);
    return new MemorySnapshot(
        "retrieved",
        qdrantSnapshot.status(),
        true,
        effectiveQuery,
        lookup,
        repoSemanticSyncService.loadStatus(projectKey)
    );
  }

  public Map<String, Object> buildBundleMemory(
      String bundleName,
      String projectKey,
      String taskId,
      String workerTaskId,
      String repoPath,
      String queryText
  ) {
    String derivedThreadKey = workerTaskId == null || workerTaskId.isBlank() ? "" : workerThreadKey(workerTaskId);
    String derivedQuery = queryText == null || queryText.isBlank()
        ? String.join(" ", bundleName, blank(taskId), blank(workerTaskId), blank(repoPath)).strip()
        : queryText.strip();
    MemorySnapshot snapshot = lookup(projectKey, derivedThreadKey, derivedQuery);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("status", snapshot.memoryStatus());
    payload.put("queryText", snapshot.queryText());
    payload.put("qdrantHealth", snapshot.qdrantHealth());
    payload.put("summary", snapshot.lookupResult().summary());
    payload.put("section", snapshot.lookupResult().section());
    payload.put("memorySatisfied", snapshot.memorySatisfied());
    payload.put("threadContextCount", snapshot.lookupResult().threadContexts().size());
    payload.put("projectContextCount", snapshot.lookupResult().projectContexts().size());
    payload.put("knowledgeContextCount", snapshot.lookupResult().knowledgeContexts().size());
    payload.put("sync", snapshot.syncStatus());
    return payload;
  }

  public String workerThreadKey(String workerTaskId) {
    return "worker-task:" + blank(workerTaskId);
  }

  private String workerQuery(WorkerTask workerTask) {
    return (workerTask.workerType().name()
        + " "
        + blank(workerTask.taskRole())
        + " "
        + blank(workerTask.title())
        + " "
        + blank(workerTask.latestSummary())).strip();
  }

  private String blank(String value) {
    return value == null ? "" : value.strip();
  }

  public record MemorySnapshot(
      String memoryStatus,
      String qdrantHealth,
      boolean memorySatisfied,
      String queryText,
      PromptThreadMemoryLookupResult lookupResult,
      Map<String, Object> syncStatus
  ) {
  }
}

