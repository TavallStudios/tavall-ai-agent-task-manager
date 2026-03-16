package com.agenttaskmanager.app.retrieval;

public enum SemanticCollectionDomain {
  KNOWLEDGE_RULES("knowledge"),
  TASK_HISTORY("tasks"),
  CODE_REPO("code"),
  CHAT_ARTIFACT("artifacts");

  private final String collectionSuffix;

  SemanticCollectionDomain(String collectionSuffix) {
    this.collectionSuffix = collectionSuffix;
  }

  public String collectionSuffix() {
    return collectionSuffix;
  }
}
