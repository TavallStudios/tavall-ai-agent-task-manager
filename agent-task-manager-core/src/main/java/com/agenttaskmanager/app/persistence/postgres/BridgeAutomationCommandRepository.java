package com.agenttaskmanager.app.persistence.postgres;

import com.agenttaskmanager.app.model.BridgeAutomationCommandNotFoundException;
import com.agenttaskmanager.app.model.bridge.BridgeAutomationClaim;
import com.agenttaskmanager.app.model.bridge.BridgeAutomationCommandSummary;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BridgeAutomationCommandRepository {

  private final JdbcClient jdbcClient;
  private final JsonSupport jsonSupport;

  public BridgeAutomationCommandRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
    this.jdbcClient = jdbcClient;
    this.jsonSupport = jsonSupport;
  }

  public BridgeAutomationCommandSummary enqueue(
      String commandRequestId,
      String sessionId,
      String targetAgentId,
      String repoPath,
      String bridgeTarget,
      String commandId,
      String isolationClass,
      Map<String, Object> arguments,
      String requestedBy,
      String requestedFrom
  ) {
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.bridge_automation_commands (
              command_request_id,
              session_id,
              target_agent_id,
              repo_path,
              bridge_target,
              command_id,
              isolation_class,
              requested_by,
              requested_from,
              status,
              latest_summary,
              command_arguments
            ) VALUES (
              :commandRequestId,
              :sessionId,
              :targetAgentId,
              NULLIF(:repoPath, ''),
              :bridgeTarget,
              :commandId,
              :isolationClass,
              :requestedBy,
              NULLIF(:requestedFrom, ''),
              'queued',
              'Queued for cooperative automation provider',
              CAST(:arguments AS jsonb)
            )
            """)
        .param("commandRequestId", commandRequestId)
        .param("sessionId", sessionId)
        .param("targetAgentId", targetAgentId)
        .param("repoPath", repoPath == null ? "" : repoPath)
        .param("bridgeTarget", bridgeTarget)
        .param("commandId", commandId)
        .param("isolationClass", isolationClass)
        .param("requestedBy", requestedBy)
        .param("requestedFrom", requestedFrom == null ? "" : requestedFrom)
        .param("arguments", jsonSupport.write(arguments))
        .update();
    return get(commandRequestId);
  }

  public List<BridgeAutomationCommandSummary> listForSession(String sessionId, int limit) {
    return jdbcClient.sql("""
            SELECT
              command_request_id,
              session_id,
              target_agent_id,
              repo_path,
              bridge_target,
              command_id,
              isolation_class,
              status,
              requested_by,
              requested_from,
              latest_summary,
              command_arguments,
              result_payload,
              created_at,
              updated_at,
              completed_at
            FROM agent_task_manager.bridge_automation_commands
            WHERE session_id = :sessionId
            ORDER BY updated_at DESC, created_at DESC
            LIMIT :limit
            """)
        .param("sessionId", sessionId)
        .param("limit", limit)
        .query((rs, rowNum) -> mapSummary(
            rs.getString("command_request_id"),
            rs.getString("session_id"),
            rs.getString("target_agent_id"),
            rs.getString("repo_path"),
            rs.getString("bridge_target"),
            rs.getString("command_id"),
            rs.getString("isolation_class"),
            rs.getString("status"),
            rs.getString("requested_by"),
            rs.getString("requested_from"),
            rs.getString("latest_summary"),
            rs.getString("command_arguments"),
            rs.getString("result_payload"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getObject("completed_at", OffsetDateTime.class)
        ))
        .list();
  }

  public Optional<BridgeAutomationClaim> claimNextQueued(String sessionId) {
    return jdbcClient.sql("""
            WITH next_command AS (
              SELECT command_request_id
              FROM agent_task_manager.bridge_automation_commands
              WHERE status = 'queued'
                AND session_id = :sessionId
              ORDER BY created_at ASC
              FOR UPDATE SKIP LOCKED
              LIMIT 1
            )
            UPDATE agent_task_manager.bridge_automation_commands AS command
            SET status = 'claimed',
                latest_summary = 'Claimed by cooperative automation bridge',
                claimed_at = now(),
                updated_at = now()
            FROM next_command
            WHERE command.command_request_id = next_command.command_request_id
            RETURNING
              command.command_request_id,
              command.session_id,
              command.target_agent_id,
              command.repo_path,
              command.bridge_target,
              command.command_id,
              command.isolation_class,
              command.command_arguments,
              command.requested_by,
              command.requested_from,
              command.created_at
            """)
        .param("sessionId", sessionId)
        .query((rs, rowNum) -> new BridgeAutomationClaim(
            rs.getString("command_request_id"),
            rs.getString("session_id"),
            rs.getString("target_agent_id"),
            rs.getString("repo_path"),
            rs.getString("bridge_target"),
            rs.getString("command_id"),
            rs.getString("isolation_class"),
            jsonSupport.readMap(rs.getString("command_arguments")),
            rs.getString("requested_by"),
            rs.getString("requested_from"),
            rs.getObject("created_at", OffsetDateTime.class)
        ))
        .optional();
  }

  public BridgeAutomationCommandSummary complete(
      String commandRequestId,
      String summary,
      Map<String, Object> result
  ) {
    return updateTerminalStatus(commandRequestId, "completed", summary, result);
  }

  public BridgeAutomationCommandSummary fail(
      String commandRequestId,
      String summary,
      Map<String, Object> result
  ) {
    return updateTerminalStatus(commandRequestId, "failed", summary, result);
  }

  public BridgeAutomationCommandSummary get(String commandRequestId) {
    return jdbcClient.sql("""
            SELECT
              command_request_id,
              session_id,
              target_agent_id,
              repo_path,
              bridge_target,
              command_id,
              isolation_class,
              status,
              requested_by,
              requested_from,
              latest_summary,
              command_arguments,
              result_payload,
              created_at,
              updated_at,
              completed_at
            FROM agent_task_manager.bridge_automation_commands
            WHERE command_request_id = :commandRequestId
            """)
        .param("commandRequestId", commandRequestId)
        .query((rs, rowNum) -> mapSummary(
            rs.getString("command_request_id"),
            rs.getString("session_id"),
            rs.getString("target_agent_id"),
            rs.getString("repo_path"),
            rs.getString("bridge_target"),
            rs.getString("command_id"),
            rs.getString("isolation_class"),
            rs.getString("status"),
            rs.getString("requested_by"),
            rs.getString("requested_from"),
            rs.getString("latest_summary"),
            rs.getString("command_arguments"),
            rs.getString("result_payload"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getObject("completed_at", OffsetDateTime.class)
        ))
        .optional()
        .orElseThrow(() -> new BridgeAutomationCommandNotFoundException(commandRequestId));
  }

  private BridgeAutomationCommandSummary updateTerminalStatus(
      String commandRequestId,
      String status,
      String summary,
      Map<String, Object> result
  ) {
    int updated = jdbcClient.sql("""
            UPDATE agent_task_manager.bridge_automation_commands
            SET status = :status,
                latest_summary = :summary,
                result_payload = CAST(:result AS jsonb),
                completed_at = now(),
                updated_at = now()
            WHERE command_request_id = :commandRequestId
            """)
        .param("status", status)
        .param("summary", summary)
        .param("result", jsonSupport.write(result))
        .param("commandRequestId", commandRequestId)
        .update();
    if (updated == 0) {
      throw new BridgeAutomationCommandNotFoundException(commandRequestId);
    }
    return get(commandRequestId);
  }

  private BridgeAutomationCommandSummary mapSummary(
      String commandRequestId,
      String sessionId,
      String targetAgentId,
      String repoPath,
      String bridgeTarget,
      String commandId,
      String isolationClass,
      String status,
      String requestedBy,
      String requestedFrom,
      String latestSummary,
      String argumentsJson,
      String resultJson,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      OffsetDateTime completedAt
  ) {
    return new BridgeAutomationCommandSummary(
        commandRequestId,
        sessionId,
        targetAgentId,
        repoPath,
        bridgeTarget,
        commandId,
        isolationClass,
        status,
        requestedBy,
        requestedFrom,
        latestSummary,
        jsonSupport.readMap(argumentsJson),
        jsonSupport.readMap(resultJson),
        createdAt,
        updatedAt,
        completedAt
    );
  }
}
