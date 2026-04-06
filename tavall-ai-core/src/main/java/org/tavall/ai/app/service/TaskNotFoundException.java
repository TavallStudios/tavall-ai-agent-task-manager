package org.tavall.ai.app.service;

public class TaskNotFoundException extends RuntimeException {

  public TaskNotFoundException(String taskId) {
    super("Task not found: " + taskId);
  }
}


