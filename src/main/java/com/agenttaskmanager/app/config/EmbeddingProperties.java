package com.agenttaskmanager.app.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.embedding")
public class EmbeddingProperties {

  private List<String> providerOrder = new ArrayList<>(List.of("gemini", "local", "hash"));
  private int dimensions = 384;
  private String geminiApiKey = "";
  private String geminiModel = "gemini-embedding-001";
  private String localCommand = "python3 " + Path.of(System.getProperty("user.dir", "."), "scripts", "fastembed_embed.py");
  private String localModel = "BAAI/bge-small-en-v1.5";
  private int localTimeoutSeconds = 30;

  public List<String> getProviderOrder() {
    return providerOrder;
  }

  public void setProviderOrder(List<String> providerOrder) {
    this.providerOrder = providerOrder;
  }

  public int getDimensions() {
    return dimensions;
  }

  public void setDimensions(int dimensions) {
    this.dimensions = dimensions;
  }

  public String getGeminiApiKey() {
    return geminiApiKey;
  }

  public void setGeminiApiKey(String geminiApiKey) {
    this.geminiApiKey = geminiApiKey;
  }

  public String getGeminiModel() {
    return geminiModel;
  }

  public void setGeminiModel(String geminiModel) {
    this.geminiModel = geminiModel;
  }

  public String getLocalCommand() {
    return localCommand;
  }

  public void setLocalCommand(String localCommand) {
    this.localCommand = localCommand;
  }

  public String getLocalModel() {
    return localModel;
  }

  public void setLocalModel(String localModel) {
    this.localModel = localModel;
  }

  public int getLocalTimeoutSeconds() {
    return localTimeoutSeconds;
  }

  public void setLocalTimeoutSeconds(int localTimeoutSeconds) {
    this.localTimeoutSeconds = localTimeoutSeconds;
  }
}
