package com.agenttaskmanager.app.persistence.postgres;

import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DesktopMcpPolicyRepository {

  private final JdbcClient jdbcClient;
  private final JsonSupport jsonSupport;

  public DesktopMcpPolicyRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
    this.jdbcClient = jdbcClient;
    this.jsonSupport = jsonSupport;
  }

  public Optional<Map<String, Object>> loadPolicy(String scopeKey) {
    return jdbcClient.sql("""
            SELECT policy
            FROM agent_task_manager.desktop_mcp_policies
            WHERE scope_key = :scopeKey
            """)
        .param("scopeKey", normalizeScope(scopeKey))
        .query((rs, rowNum) -> jsonSupport.readMap(rs.getString("policy")))
        .optional();
  }

  public void upsertPolicy(String scopeKey, Map<String, Object> policy) {
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.desktop_mcp_policies (
              scope_key,
              policy,
              updated_at
            ) VALUES (
              :scopeKey,
              CAST(:policy AS jsonb),
              now()
            )
            ON CONFLICT (scope_key) DO UPDATE
            SET policy = EXCLUDED.policy,
                updated_at = now()
            """)
        .param("scopeKey", normalizeScope(scopeKey))
        .param("policy", jsonSupport.write(policy))
        .update();
  }

  private String normalizeScope(String scopeKey) {
    if (scopeKey == null || scopeKey.isBlank()) {
      return "workspace-default";
    }
    return scopeKey.strip();
  }
}

