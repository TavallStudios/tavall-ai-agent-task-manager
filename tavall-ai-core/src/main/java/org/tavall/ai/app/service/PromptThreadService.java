package org.tavall.ai.app.service;

import org.tavall.ai.app.model.PromptThreadDetail;
import org.tavall.ai.app.model.PromptThreadMemoryLookupResult;
import org.tavall.ai.app.model.PromptThreadSummary;
import org.tavall.ai.app.persistence.postgres.PromptThreadRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PromptThreadService {

  private final PromptThreadMemoryService promptThreadMemoryService;
  private final PromptThreadRepository promptThreadRepository;

  public PromptThreadService(
      PromptThreadMemoryService promptThreadMemoryService,
      PromptThreadRepository promptThreadRepository
  ) {
    this.promptThreadMemoryService = promptThreadMemoryService;
    this.promptThreadRepository = promptThreadRepository;
  }

  public List<PromptThreadSummary> list(int limit, String bridgeTarget) {
    return promptThreadRepository.list(limit, bridgeTarget);
  }

  public PromptThreadDetail getDetail(String threadKey) {
    return promptThreadRepository.getDetail(threadKey);
  }

  public List<PromptThreadSummary> search(String queryText, int limit) {
    return promptThreadMemoryService.searchThreads(queryText, limit);
  }

  public PromptThreadMemoryLookupResult lookupMemory(String projectKey, String threadKey, String queryText) {
    return promptThreadMemoryService.lookup(projectKey, threadKey, queryText);
  }
}

