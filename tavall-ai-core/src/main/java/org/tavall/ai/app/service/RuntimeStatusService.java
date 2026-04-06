package org.tavall.ai.app.service;

import org.tavall.ai.app.config.TaskRuntimeProperties;
import org.tavall.ai.app.model.RuntimeStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class RuntimeStatusService {

  private final JdbcClient jdbcClient;
  private final StringRedisTemplate redisTemplate;
  private final TaskRuntimeProperties runtimeProperties;

  public RuntimeStatusService(
      JdbcClient jdbcClient,
      StringRedisTemplate redisTemplate,
      TaskRuntimeProperties runtimeProperties
  ) {
    this.jdbcClient = jdbcClient;
    this.redisTemplate = redisTemplate;
    this.runtimeProperties = runtimeProperties;
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
        0,
        multiAgentEnabled,
        redisReachable,
        runtimeProperties.getRedisNamespace(),
        false,
        false,
        null,
        null,
        "disabled",
        null,
        null
    );
  }
}

