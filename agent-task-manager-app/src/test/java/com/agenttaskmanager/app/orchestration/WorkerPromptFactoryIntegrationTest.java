package com.agenttaskmanager.app.orchestration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import com.agenttaskmanager.app.model.orchestration.WorkerTask;
import com.agenttaskmanager.app.model.orchestration.WorkerTransportKind;
import com.agenttaskmanager.app.model.orchestration.WorkerType;
import com.agenttaskmanager.app.retrieval.SemanticCollectionDomain;
import com.agenttaskmanager.app.retrieval.SemanticContentType;
import com.agenttaskmanager.app.support.IntegrationTestSupport;
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
class WorkerPromptFactoryIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private SharedTaskContextService sharedTaskContextService;

  @Autowired
  private WorkerPromptFactory workerPromptFactory;

  @Test
  void shouldInjectRetrievedMemoryIntoWorkerPrompts() {
    String suffix = UUID.randomUUID().toString();
    sharedTaskContextService.deleteProjectSemanticContexts("worker-test", Map.of());
    sharedTaskContextService.storeProjectSemanticDocument(
        "worker-test",
        "task-" + suffix,
        "worker-" + suffix,
        "worker-memory",
        "worker-memory",
        "Worker prompt should check memory first " + suffix,
        SemanticCollectionDomain.TASK_HISTORY,
        SemanticContentType.RUN_SUMMARY,
        Map.of("scope", "worker-prompt")
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
    assertTrue(prompt.contains("Worker focus:"));
    assertTrue(prompt.contains("runHarnessToolBundle(worker-context)"));
    assertTrue(prompt.contains("loadCleanJavaTaskContext + runHarnessToolBundle(java-context)"));
    assertTrue(prompt.contains("runCleanJavaHarness: run Spoon source-shape checks first"));
    assertTrue(prompt.contains("Final response contract:"));
  }
}
