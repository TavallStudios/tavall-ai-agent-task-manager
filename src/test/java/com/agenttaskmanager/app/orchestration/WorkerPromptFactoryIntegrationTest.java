package com.agenttaskmanager.app.orchestration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import com.agenttaskmanager.app.model.orchestration.WorkerTask;
import com.agenttaskmanager.app.model.orchestration.WorkerTransportKind;
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
    sharedTaskContextService.storeTaskEmbedding(
        "worker-test",
        "task-" + suffix,
        "worker-" + suffix,
        "worker-memory",
        "Worker prompt should check memory first " + suffix,
        Map.of("scope", "worker-prompt")
    );

    WorkerTask workerTask = new WorkerTask(
        "worker-" + suffix,
        "task-" + suffix,
        null,
        "implementer",
        "Investigate prompt handling " + suffix,
        TaskLifecycleStatus.QUEUED,
        null,
        WorkerTransportKind.LOCAL_CODEX_EXEC,
        0,
        3,
        "check memory first",
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        null,
        null
    );

    String prompt = workerPromptFactory.buildPrompt("worker-test", workerTask);

    assertTrue(prompt.contains("Memory policy:"));
    assertTrue(prompt.contains("Worker prompt should check memory first " + suffix));
    assertTrue(prompt.contains("before the final response"));
  }
}
