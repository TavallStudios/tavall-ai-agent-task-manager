package com.agenttaskmanager.app.service;

public class TaskNotFoundException extends RuntimeException {

  public TaskNotFoundException(String taskId) {
    super("Task not found: " + taskId);
  }
}

