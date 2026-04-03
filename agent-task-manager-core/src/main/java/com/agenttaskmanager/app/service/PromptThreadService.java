package com.agenttaskmanager.app.service;

import com.agenttaskmanager.app.model.PromptThreadDetail;
import com.agenttaskmanager.app.model.PromptThreadMemoryLookupResult;
import com.agenttaskmanager.app.model.PromptThreadSummary;
import com.agenttaskmanager.app.persistence.postgres.PromptThreadRepository;
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
