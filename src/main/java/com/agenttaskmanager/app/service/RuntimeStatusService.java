package com.agenttaskmanager.app.service;

import com.agenttaskmanager.app.config.TaskRuntimeProperties;
import com.agenttaskmanager.app.model.RuntimeStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class RuntimeStatusService {

  private final JdbcClient jdbcClient;
  private final StringRedisTemplate redisTemplate;
  private final TaskRuntimeProperties runtimeProperties;
  private final PromptRequestService promptRequestService;

  public RuntimeStatusService(
      JdbcClient jdbcClient,
      StringRedisTemplate redisTemplate,
      TaskRuntimeProperties runtimeProperties,
      PromptRequestService promptRequestService
  ) {
    this.jdbcClient = jdbcClient;
    this.redisTemplate = redisTemplate;
    this.runtimeProperties = runtimeProperties;
    this.promptRequestService = promptRequestService;
  }

  public RuntimeStatus getRuntimeStatus() {
    Long taskCount = jdbcClient.sql("SELECT count(*) FROM agent_task_manager.agent_tasks")
        .query(Long.class)
        .single();

    boolean multiAgentEnabled = false;
    boolean redisReachable = false;
    String redisValue = null;
    try {
      redisValue = redisTemplate.opsForValue().get(runtimeProperties.getRedisNamespace() + ":multi_agent:enabled");
      redisReachable = true;
    } catch (DataAccessException ignored) {
      redisReachable = false;
    }

    if (redisValue != null) {
      String normalized = redisValue.trim().toLowerCase();
      multiAgentEnabled =
          normalized.equals("1")
              || normalized.equals("true")
              || normalized.equals("on")
              || normalized.equals("enabled");
    }

    return new RuntimeStatus(
        taskCount == null ? 0 : taskCount,
        promptRequestService.queuedPromptCount(),
        multiAgentEnabled,
        redisReachable,
        runtimeProperties.getRedisNamespace()
    );
  }
}

