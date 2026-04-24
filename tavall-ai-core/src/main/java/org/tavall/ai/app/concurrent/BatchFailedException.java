package org.tavall.ai.app.concurrent;

public class BatchFailedException extends Exception {

  private final BatchResult<?> result;

  public BatchFailedException(String message, Throwable cause, BatchResult<?> result) {
    super(message, cause);
    this.result = result;
  }

  public BatchResult<?> result() {
    return result;
  }
}

