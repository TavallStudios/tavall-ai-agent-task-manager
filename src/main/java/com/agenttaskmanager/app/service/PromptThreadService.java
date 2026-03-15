package com.agenttaskmanager.app.service;

import com.agenttaskmanager.app.model.PromptThreadDetail;
import com.agenttaskmanager.app.model.PromptThreadSummary;
import com.agenttaskmanager.app.persistence.postgres.PromptThreadRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PromptThreadService {

  private final PromptThreadRepository promptThreadRepository;

  public PromptThreadService(PromptThreadRepository promptThreadRepository) {
    this.promptThreadRepository = promptThreadRepository;
  }

  public List<PromptThreadSummary> list(int limit, String bridgeTarget) {
    return promptThreadRepository.list(limit, bridgeTarget);
  }

  public PromptThreadDetail getDetail(String threadKey) {
    return promptThreadRepository.getDetail(threadKey);
  }
}
