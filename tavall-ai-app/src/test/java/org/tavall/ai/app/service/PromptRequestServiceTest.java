package org.tavall.ai.app.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.tavall.ai.app.model.PromptRequestSummary;
import org.tavall.ai.app.orchestration.SharedTaskContextService;
import org.tavall.ai.app.orchestration.PromptMemoryLookupService;
import org.tavall.ai.app.support.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "app.qdrant.project-collection-prefix=agent_task_manager_prompt_request_test",
    "app.embedding.provider-order=hash",
    "app.embedding.dimensions=32"
})
class PromptRequestServiceTest extends IntegrationTestSupport {

  @Autowired
  private PromptRequestService promptRequestService;

  @Autowired
  private PromptMemoryLookupService promptMemoryLookupService;

  @Autowired
  private SharedTaskContextService sharedTaskContextService;

  @Test
  void shouldCapturePromptRequestsIntoProjectMemoryAutomatically() {
    String suffix = UUID.randomUUID().toString();
    String projectKey = "prompt-request-test";
    String promptText = "Remember this prompt memory " + suffix;
    sharedTaskContextService.deleteProjectSemanticContexts(projectKey, java.util.Map.of());

    PromptRequestSummary summary = promptRequestService.create(
        projectKey,
        "/srv/AgentTaskManager",
        "remote-headless",
        "edit",
        promptText,
        "integration-test",
        "integration-suite"
    );

    PromptMemoryLookupService.PromptMemorySnapshot snapshot = promptMemoryLookupService.lookup(projectKey, promptText);

    assertTrue(summary.requestId().startsWith("pr_"));
    assertTrue(snapshot.summary().contains("Memory lookup completed."));
    assertTrue(snapshot.section().contains(promptText));
  }
}

