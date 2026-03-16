package com.agenttaskmanager.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.knowledge-index")
public class KnowledgeIndexProperties {

  private boolean enabled = false;
  private String knowledgeBase = "project-knowledge";
  private String sourceRoot = "";
  private String jarPath = "";
  private int maxChunkChars = 1800;
  private int targetChunkLines = 80;
  private int overlapLines = 20;
  private int promptResultLimit = 3;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getKnowledgeBase() {
    return knowledgeBase;
  }

  public void setKnowledgeBase(String knowledgeBase) {
    this.knowledgeBase = knowledgeBase;
  }

  public String getSourceRoot() {
    return sourceRoot;
  }

  public void setSourceRoot(String sourceRoot) {
    this.sourceRoot = sourceRoot;
  }

  public String getJarPath() {
    return jarPath;
  }

  public void setJarPath(String jarPath) {
    this.jarPath = jarPath;
  }

  public int getMaxChunkChars() {
    return maxChunkChars;
  }

  public void setMaxChunkChars(int maxChunkChars) {
    this.maxChunkChars = maxChunkChars;
  }

  public int getTargetChunkLines() {
    return targetChunkLines;
  }

  public void setTargetChunkLines(int targetChunkLines) {
    this.targetChunkLines = targetChunkLines;
  }

  public int getOverlapLines() {
    return overlapLines;
  }

  public void setOverlapLines(int overlapLines) {
    this.overlapLines = overlapLines;
  }

  public int getPromptResultLimit() {
    return promptResultLimit;
  }

  public void setPromptResultLimit(int promptResultLimit) {
    this.promptResultLimit = promptResultLimit;
  }
}
