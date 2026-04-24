package org.tavall.ai.app.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.semantic-index")
public class SemanticIndexProperties {

  private int maxChunkChars = 1800;
  private int textTargetChunkLines = 80;
  private int codeTargetChunkLines = 120;
  private int docTargetChunkLines = 60;
  private int overlapLines = 12;
  private int chatWindowMessages = 6;
  private int chatWindowOverlap = 2;
  private List<String> reindexRepoNames = new ArrayList<>(List.of(
      "AgentTaskManager",
      "Webstore",
      "CustomMinecraftServer",
      "Minecraft-CTF",
      "TavallCouriers",
      "Portfolio",
      "MCRSpeedrun"
  ));

  public int getMaxChunkChars() {
    return maxChunkChars;
  }

  public void setMaxChunkChars(int maxChunkChars) {
    this.maxChunkChars = maxChunkChars;
  }

  public int getTextTargetChunkLines() {
    return textTargetChunkLines;
  }

  public void setTextTargetChunkLines(int textTargetChunkLines) {
    this.textTargetChunkLines = textTargetChunkLines;
  }

  public int getCodeTargetChunkLines() {
    return codeTargetChunkLines;
  }

  public void setCodeTargetChunkLines(int codeTargetChunkLines) {
    this.codeTargetChunkLines = codeTargetChunkLines;
  }

  public int getDocTargetChunkLines() {
    return docTargetChunkLines;
  }

  public void setDocTargetChunkLines(int docTargetChunkLines) {
    this.docTargetChunkLines = docTargetChunkLines;
  }

  public int getOverlapLines() {
    return overlapLines;
  }

  public void setOverlapLines(int overlapLines) {
    this.overlapLines = overlapLines;
  }

  public int getChatWindowMessages() {
    return chatWindowMessages;
  }

  public void setChatWindowMessages(int chatWindowMessages) {
    this.chatWindowMessages = chatWindowMessages;
  }

  public int getChatWindowOverlap() {
    return chatWindowOverlap;
  }

  public void setChatWindowOverlap(int chatWindowOverlap) {
    this.chatWindowOverlap = chatWindowOverlap;
  }

  public List<String> getReindexRepoNames() {
    return reindexRepoNames;
  }

  public void setReindexRepoNames(List<String> reindexRepoNames) {
    this.reindexRepoNames = reindexRepoNames;
  }
}

