package com.agenttaskmanager.app.web;

import com.agenttaskmanager.app.model.TaskDetail;
import com.agenttaskmanager.app.model.TaskSummary;
import com.agenttaskmanager.app.service.TaskService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/tasks")
public class TaskApiController {

  private final TaskService taskService;

  public TaskApiController(TaskService taskService) {
    this.taskService = taskService;
  }

  @GetMapping
  public TaskListResponse listTasks(
      @RequestParam(defaultValue = "25") @Min(1) @Max(100) int limit,
      @RequestParam(defaultValue = "") String project,
      @RequestParam(defaultValue = "") String status
  ) {
    List<TaskSummary> tasks = taskService.listTasks(project, status, limit);
    return new TaskListResponse(tasks);
  }

  @GetMapping("/{taskId}")
  public TaskDetail getTask(@PathVariable String taskId) {
    return taskService.getTask(taskId);
  }

  public record TaskListResponse(List<TaskSummary> items) {
  }
}

