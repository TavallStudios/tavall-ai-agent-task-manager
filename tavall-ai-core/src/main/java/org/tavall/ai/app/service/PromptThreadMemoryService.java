package org.tavall.ai.app.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.tavall.ai.app.model.PromptThreadMemoryLookupResult;
import org.tavall.ai.app.model.PromptThreadSummary;
import org.tavall.ai.app.orchestration.PromptMemoryLookupService;
import org.tavall.ai.app.persistence.postgres.PromptThreadRepository;

@Service
public class PromptThreadMemoryService {

  private static final String DEFAULT_EXECUTION_TARGET = "";

  private final PromptMemoryLookupService promptMemoryLookupService;
  private final PromptThreadRepository promptThreadRepository;

  public PromptThreadMemoryService(
      PromptMemoryLookupService promptMemoryLookupService,
      PromptThreadRepository promptThreadRepository
  ) {
    this.promptMemoryLookupService = promptMemoryLookupService;
    this.promptThreadRepository = promptThreadRepository;
  }

  public PromptThreadMemoryLookupResult lookup(String projectKey, String threadKey, String queryText) {
    return promptMemoryLookupService.lookup(projectKey, threadKey, queryText);
  }

  public List<PromptThreadSummary> searchThreads(String queryText, int limit) {
    return promptThreadRepository.search(queryText, limit, DEFAULT_EXECUTION_TARGET);
  }
}
