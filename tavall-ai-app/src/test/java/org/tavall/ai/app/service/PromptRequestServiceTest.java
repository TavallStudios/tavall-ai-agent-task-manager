package org.tavall.ai.app.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.tavall.ai.app.model.PromptRequestSummary;
import org.tavall.ai.app.orchestration.PromptMemoryLookupService;
import org.tavall.ai.app.retrieval.SemanticMemoryService;
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
  private SemanticMemoryService semanticMemoryService;

  @Test
  void shouldPersistPromptThreadHistoryWithoutAutomaticProjectMemory() {
    String suffix = UUID.randomUUID().toString();
    String projectKey = "prompt-request-test";
    String promptText = "Remember this prompt memory " + suffix;
    semanticMemoryService.deleteProjectContexts(projectKey, java.util.Map.of());

    PromptRequestSummary summary = promptRequestService.create(
        projectKey,
        "/srv/AgentTaskManager",
        "remote-headless",
        "edit",
        promptText,
        "integration-test",
        "integration-suite"
    );

    var snapshot = promptMemoryLookupService.lookup(projectKey, summary.threadKey(), promptText);

    assertTrue(summary.requestId().startsWith("pr_"));
    assertTrue(snapshot.summary().contains("Memory pipeline retrieved"));
    assertTrue(snapshot.section().contains(promptText));
    assertTrue(semanticMemoryService.searchProject(projectKey, promptText, 10, java.util.Map.of()).isEmpty());
  }
}
