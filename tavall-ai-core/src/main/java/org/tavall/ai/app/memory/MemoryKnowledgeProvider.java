package org.tavall.ai.app.memory;

public interface MemoryKnowledgeProvider {

  String providerId();

  MemoryKnowledgeRole role();

  MemoryKnowledgeContext retrieve(MemoryKnowledgeQuery query);
}
