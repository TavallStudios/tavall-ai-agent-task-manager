package com.agenttaskmanager.app.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.memory-runtime")
public class MemoryRuntimeProperties {

  private int exactStateLimit = 8;
  private int semanticCandidateLimit = 8;
  private Duration hotStateTtl = Duration.ofHours(6);
  private Duration idempotencyTtl = Duration.ofMinutes(30);
  private Duration continuityTtl = Duration.ofHours(12);

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
}
