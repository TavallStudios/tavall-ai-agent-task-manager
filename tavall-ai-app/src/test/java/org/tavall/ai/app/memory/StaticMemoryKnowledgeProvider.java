package org.tavall.ai.app.memory;

final class StaticMemoryKnowledgeProvider implements MemoryKnowledgeProvider {

  private final MemoryKnowledgeContext context;

  StaticMemoryKnowledgeProvider(MemoryKnowledgeContext context) {
    this.context = context;
  }

  @Override
  public String providerId() {
    return context.provider();
  }

  @Override
  public MemoryKnowledgeRole role() {
    return context.role();
  }

  @Override
  public MemoryKnowledgeContext retrieve(MemoryKnowledgeQuery query) {
    return context;
  }
}
