package org.tavall.ai.app.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.memory-runtime")
public class MemoryRuntimeProperties {

  private int exactStateLimit = 8;
  private int semanticCandidateLimit = 8;
  private int externalContextLimit = 6;
  private int graphifyDepth = 3;
  private int graphifyTokenBudget = 1600;
  private Duration hotStateTtl = Duration.ofHours(6);
  private Duration idempotencyTtl = Duration.ofMinutes(30);
  private Duration continuityTtl = Duration.ofHours(12);
  private Duration externalProviderTimeout = Duration.ofSeconds(6);
  private String graphifyMcpEndpoint = "";
  private String graphifyApiKey = "";
  private String graphitiMcpEndpoint = "";
  private String graphitiApiKey = "";
  private String graphitiGroupId = "tavall";

  public int getExactStateLimit() {
    return exactStateLimit;
  }

  public void setExactStateLimit(int exactStateLimit) {
    this.exactStateLimit = exactStateLimit;
  }

  public int getSemanticCandidateLimit() {
    return semanticCandidateLimit;
  }

  public void setSemanticCandidateLimit(int semanticCandidateLimit) {
    this.semanticCandidateLimit = semanticCandidateLimit;
  }

  public int getExternalContextLimit() {
    return externalContextLimit;
  }

  public void setExternalContextLimit(int externalContextLimit) {
    this.externalContextLimit = externalContextLimit;
  }

  public int getGraphifyDepth() {
    return graphifyDepth;
  }

  public void setGraphifyDepth(int graphifyDepth) {
    this.graphifyDepth = graphifyDepth;
  }

  public int getGraphifyTokenBudget() {
    return graphifyTokenBudget;
  }

  public void setGraphifyTokenBudget(int graphifyTokenBudget) {
    this.graphifyTokenBudget = graphifyTokenBudget;
  }

  public Duration getHotStateTtl() {
    return hotStateTtl;
  }

  public void setHotStateTtl(Duration hotStateTtl) {
    this.hotStateTtl = hotStateTtl;
  }

  public Duration getIdempotencyTtl() {
    return idempotencyTtl;
  }

  public void setIdempotencyTtl(Duration idempotencyTtl) {
    this.idempotencyTtl = idempotencyTtl;
  }

  public Duration getContinuityTtl() {
    return continuityTtl;
  }

  public void setContinuityTtl(Duration continuityTtl) {
    this.continuityTtl = continuityTtl;
  }

  public Duration getExternalProviderTimeout() {
    return externalProviderTimeout;
  }

  public void setExternalProviderTimeout(Duration externalProviderTimeout) {
    this.externalProviderTimeout = externalProviderTimeout;
  }

  public String getGraphifyMcpEndpoint() {
    return graphifyMcpEndpoint;
  }

  public void setGraphifyMcpEndpoint(String graphifyMcpEndpoint) {
    this.graphifyMcpEndpoint = graphifyMcpEndpoint;
  }

  public String getGraphifyApiKey() {
    return graphifyApiKey;
  }

  public void setGraphifyApiKey(String graphifyApiKey) {
    this.graphifyApiKey = graphifyApiKey;
  }

  public String getGraphitiMcpEndpoint() {
    return graphitiMcpEndpoint;
  }

  public void setGraphitiMcpEndpoint(String graphitiMcpEndpoint) {
    this.graphitiMcpEndpoint = graphitiMcpEndpoint;
  }

  public String getGraphitiApiKey() {
    return graphitiApiKey;
  }

  public void setGraphitiApiKey(String graphitiApiKey) {
    this.graphitiApiKey = graphitiApiKey;
  }

  public String getGraphitiGroupId() {
    return graphitiGroupId;
  }

  public void setGraphitiGroupId(String graphitiGroupId) {
    this.graphitiGroupId = graphitiGroupId;
  }
}
