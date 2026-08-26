package org.tavall.ai.app.orchestration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.tavall.ai.app.model.orchestration.TaskLifecycleStatus;
import org.tavall.ai.app.model.orchestration.WorkerTask;
import org.tavall.ai.app.model.orchestration.WorkerTransportKind;
import org.tavall.ai.app.model.orchestration.WorkerType;
import org.tavall.ai.app.retrieval.SemanticCollectionDomain;
import org.tavall.ai.app.retrieval.SemanticContentType;
import org.tavall.ai.app.retrieval.SemanticMemoryService;
import org.tavall.ai.app.support.IntegrationTestSupport;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "app.qdrant.project-collection-prefix=agent_task_manager_worker_memory_test",
    "app.embedding.provider-order=hash",
    "app.embedding.dimensions=32"
})
class WorkerPromptFactoryTest extends IntegrationTestSupport {

  @Autowired
  private SemanticMemoryService semanticMemoryService;

  @Autowired
  private WorkerPromptFactory workerPromptFactory;

  @Test
  void shouldInjectRetrievedMemoryIntoWorkerPrompts() {
    String suffix = UUID.randomUUID().toString();
    semanticMemoryService.deleteProjectContexts("worker-test", Map.of());
    semanticMemoryService.storeProjectDocument(
        "worker-test",
        "task-" + suffix,
        "worker-" + suffix,
        "worker-memory",
        "worker-memory",
        "Worker prompt should check memory first " + suffix,
        SemanticCollectionDomain.TASK_HISTORY,
        SemanticContentType.RUN_SUMMARY,
        Map.of(
            "scope", "PROJECT",
            "userId", "",
            "workspaceId", "",
            "status", "active",
            "tombstoned", false
        )
    );

    WorkerTask workerTask = new WorkerTask(
        "worker-" + suffix,
        "task-" + suffix,
        null,
        WorkerType.CODE,
        "implementer",
        "Investigate prompt handling " + suffix,
        TaskLifecycleStatus.QUEUED,
        null,
        WorkerTransportKind.LOCAL_CODEX_EXEC,
        0,
        3,
        "check memory first",
        Map.of("requiresIntegrationTests", true),
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        null,
        null
    );

    String prompt = workerPromptFactory.buildPrompt("worker-test", workerTask);

    assertTrue(prompt.contains("Deterministic execution policy:"));
    assertTrue(prompt.contains("Memory policy:"));
    assertTrue(prompt.contains("Worker type: CODE"));
    assertTrue(prompt.contains("Worker prompt should check memory first " + suffix));
    assertTrue(prompt.contains("before the final response"));
    assertTrue(prompt.contains("Tool combination patterns:"));
    assertTrue(prompt.contains("Contextual tool policy:"));
    assertTrue(prompt.contains("Contextual tool policy (auto-inferred):"));
    assertTrue(prompt.contains("decision: REQUIRED"));
    assertTrue(prompt.contains("required sequence:"));
    assertTrue(prompt.contains("Worker focus:"));
    assertTrue(prompt.contains("runHarnessToolBundle(worker-context)"));
    assertTrue(prompt.contains("prepareGitBranch"));
    assertTrue(prompt.contains("createGitCommit"));
    assertTrue(prompt.contains("loadTaskContext + loadValidationHistory + searchPriorFixes"));
    assertTrue(prompt.contains("local clean Java validation runs after worker execution"));
    assertTrue(prompt.contains("Final response contract:"));
  }
}
