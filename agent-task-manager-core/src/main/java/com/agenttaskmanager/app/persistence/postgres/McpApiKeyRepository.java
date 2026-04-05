package com.agenttaskmanager.app.persistence.postgres;

import com.agenttaskmanager.app.security.McpApiKeyRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class McpApiKeyRepository {

  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;

  public McpApiKeyRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;
  }

  public void upsertBootstrapKey(
      String apiKeyId,
      String displayName,
      String keyHash,
      String workspaceId,
      String userId,
      String projectId,
      List<String> roles,
      Map<String, Object> metadata
  ) {
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.mcp_api_keys (
              api_key_id,
              display_name,
              key_hash,
              workspace_id,
              user_id,
              project_id,
              status,
              roles,
              metadata
            ) VALUES (
              :apiKeyId,
              :displayName,
              :keyHash,
              :workspaceId,
              :userId,
              :projectId,
              'active',
              CAST(:roles AS jsonb),
              CAST(:metadata AS jsonb)
            )
            ON CONFLICT (api_key_id) DO UPDATE SET
              display_name = EXCLUDED.display_name,
              key_hash = EXCLUDED.key_hash,
              workspace_id = EXCLUDED.workspace_id,
              user_id = EXCLUDED.user_id,
              project_id = EXCLUDED.project_id,
              roles = EXCLUDED.roles,
              metadata = EXCLUDED.metadata,
              status = 'active',
              updated_at = now()
            """)
        .param("apiKeyId", apiKeyId)
        .param("displayName", displayName)
        .param("keyHash", keyHash)
        .param("workspaceId", workspaceId)
        .param("userId", userId)
        .param("projectId", projectId == null ? "" : projectId)
        .param("roles", writeJson(roles))
        .param("metadata", writeJson(metadata))
        .update();
  }

  public Optional<McpApiKeyRecord> findActiveByHash(String keyHash) {
    return jdbcClient.sql("""
            SELECT *
            FROM agent_task_manager.mcp_api_keys
            WHERE key_hash = :keyHash
              AND status = 'active'
            """)
        .param("keyHash", keyHash)
        .query((rs, rowNum) -> new McpApiKeyRecord(
            rs.getString("api_key_id"),
            rs.getString("display_name"),
            rs.getString("key_hash"),
            rs.getString("user_id"),
            rs.getString("workspace_id"),
            rs.getString("project_id"),
            rs.getString("status"),
            readRoles(rs.getString("roles")),
            readMap(rs.getString("metadata"))
        ))
        .optional();
  }

  public void touchLastUsed(String apiKeyId) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.mcp_api_keys
            SET last_used_at = now(),
                updated_at = now()
            WHERE api_key_id = :apiKeyId
            """)
        .param("apiKeyId", apiKeyId)
        .update();
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to serialize api-key payload.", exception);
    }
  }

  private List<String> readRoles(String value) {
    try {
      return objectMapper.readValue(value, new TypeReference<>() {
      });
    } catch (Exception exception) {
      return List.of();
    }
  }

  private Map<String, Object> readMap(String value) {
    try {
      return objectMapper.readValue(value, new TypeReference<>() {
      });
    } catch (Exception exception) {
      return Map.of();
    }
  }
}
