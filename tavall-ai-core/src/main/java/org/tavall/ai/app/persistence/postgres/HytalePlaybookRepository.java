package org.tavall.ai.app.persistence.postgres;

import org.tavall.ai.app.model.HytaleLearningNotFoundException;
import org.tavall.ai.app.model.hytalelearning.HytalePlaybook;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class HytalePlaybookRepository {

  private final JdbcClient jdbcClient;
  private final JsonSupport jsonSupport;

  public HytalePlaybookRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
    this.jdbcClient = jdbcClient;
    this.jsonSupport = jsonSupport;
  }

  public HytalePlaybook create(
      String playbookId,
      String machineId,
      String clientProfileId,
      String serverTarget,
      String scenarioId,
      String name,
      String targetWindow,
      List<Map<String, Object>> actions,
      List<String> expectedAnchors,
      Map<String, Object> failureRecovery,
      String latestSummary,
      Map<String, Object> metadata
  ) {
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.hytale_playbooks (
              playbook_id,
              machine_id,
              client_profile_id,
              server_target,
              scenario_id,
              name,
              target_window,
              actions,
              expected_anchors,
              failure_recovery,
              latest_summary,
              metadata
            ) VALUES (
              :playbookId,
              :machineId,
              NULLIF(:clientProfileId, ''),
              NULLIF(:serverTarget, ''),
              NULLIF(:scenarioId, ''),
              :name,
              :targetWindow,
              CAST(:actions AS jsonb),
              CAST(:expectedAnchors AS jsonb),
              CAST(:failureRecovery AS jsonb),
              :latestSummary,
              CAST(:metadata AS jsonb)
            )
            """)
        .param("playbookId", playbookId)
        .param("machineId", machineId)
        .param("clientProfileId", blank(clientProfileId))
        .param("serverTarget", blank(serverTarget))
        .param("scenarioId", blank(scenarioId))
        .param("name", name)
        .param("targetWindow", targetWindow)
        .param("actions", jsonSupport.write(actions))
        .param("expectedAnchors", jsonSupport.write(expectedAnchors))
        .param("failureRecovery", jsonSupport.write(failureRecovery))
        .param("latestSummary", latestSummary)
        .param("metadata", jsonSupport.write(metadata))
        .update();
    return get(playbookId);
  }

  public HytalePlaybook updateApproval(String playbookId, boolean approved, String approvedBy) {
    int updated = jdbcClient.sql("""
            UPDATE agent_task_manager.hytale_playbooks
            SET approved = :approved,
                approved_at = CASE WHEN :approved THEN now() ELSE NULL END,
                approved_by = CASE WHEN :approved THEN NULLIF(:approvedBy, '') ELSE NULL END,
                updated_at = now()
            WHERE playbook_id = :playbookId
            """)
        .param("approved", approved)
        .param("approvedBy", blank(approvedBy))
        .param("playbookId", playbookId)
        .update();
    if (updated == 0) {
      throw new HytaleLearningNotFoundException("Hytale playbook not found: " + playbookId);
    }
    return get(playbookId);
  }

  public HytalePlaybook updatePinned(String playbookId, boolean pinned, String pinnedBy) {
    int updated = jdbcClient.sql("""
            UPDATE agent_task_manager.hytale_playbooks
            SET pinned = :pinned,
                pinned_at = CASE WHEN :pinned THEN now() ELSE NULL END,
                pinned_by = CASE WHEN :pinned THEN NULLIF(:pinnedBy, '') ELSE NULL END,
                updated_at = now()
            WHERE playbook_id = :playbookId
            """)
        .param("pinned", pinned)
        .param("pinnedBy", blank(pinnedBy))
        .param("playbookId", playbookId)
        .update();
    if (updated == 0) {
      throw new HytaleLearningNotFoundException("Hytale playbook not found: " + playbookId);
    }
    return get(playbookId);
  }

  public HytalePlaybook get(String playbookId) {
    return find(playbookId)
        .orElseThrow(() -> new HytaleLearningNotFoundException("Hytale playbook not found: " + playbookId));
  }

  public Optional<HytalePlaybook> find(String playbookId) {
    return jdbcClient.sql("""
            SELECT
              playbook_id,
              machine_id,
              client_profile_id,
              server_target,
              scenario_id,
              name,
              target_window,
              actions,
              expected_anchors,
              failure_recovery,
              approved,
              pinned,
              latest_summary,
              metadata,
              created_at,
              updated_at,
              approved_at,
              approved_by,
              pinned_at,
              pinned_by
            FROM agent_task_manager.hytale_playbooks
            WHERE playbook_id = :playbookId
            """)
        .param("playbookId", playbookId)
        .query((rs, rowNum) -> mapPlaybook(
            rs.getString("playbook_id"),
            rs.getString("machine_id"),
            rs.getString("client_profile_id"),
            rs.getString("server_target"),
            rs.getString("scenario_id"),
            rs.getString("name"),
            rs.getString("target_window"),
            rs.getString("actions"),
            rs.getString("expected_anchors"),
            rs.getString("failure_recovery"),
            rs.getBoolean("approved"),
            rs.getBoolean("pinned"),
            rs.getString("latest_summary"),
            rs.getString("metadata"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getObject("approved_at", OffsetDateTime.class),
            rs.getString("approved_by"),
            rs.getObject("pinned_at", OffsetDateTime.class),
            rs.getString("pinned_by")
        ))
        .optional();
  }

  public List<HytalePlaybook> listByScope(
      String machineId,
      String clientProfileId,
      String serverTarget,
      String scenarioId,
      boolean executableOnly,
      int limit
  ) {
    return jdbcClient.sql("""
            SELECT
              playbook_id,
              machine_id,
              client_profile_id,
              server_target,
              scenario_id,
              name,
              target_window,
              actions,
              expected_anchors,
              failure_recovery,
              approved,
              pinned,
              latest_summary,
              metadata,
              created_at,
              updated_at,
              approved_at,
              approved_by,
              pinned_at,
              pinned_by
            FROM agent_task_manager.hytale_playbooks
            WHERE (:machineId = '' OR machine_id = :machineId)
              AND (:clientProfileId = '' OR COALESCE(client_profile_id, '') = :clientProfileId)
              AND (:serverTarget = '' OR COALESCE(server_target, '') = :serverTarget)
              AND (:scenarioId = '' OR COALESCE(scenario_id, '') = :scenarioId)
              AND (:executableOnly = false OR approved = true OR pinned = true)
            ORDER BY pinned DESC, approved DESC, updated_at DESC
            LIMIT :limit
            """)
        .param("machineId", blank(machineId))
        .param("clientProfileId", blank(clientProfileId))
        .param("serverTarget", blank(serverTarget))
        .param("scenarioId", blank(scenarioId))
        .param("executableOnly", executableOnly)
        .param("limit", limit)
        .query((rs, rowNum) -> mapPlaybook(
            rs.getString("playbook_id"),
            rs.getString("machine_id"),
            rs.getString("client_profile_id"),
            rs.getString("server_target"),
            rs.getString("scenario_id"),
            rs.getString("name"),
            rs.getString("target_window"),
            rs.getString("actions"),
            rs.getString("expected_anchors"),
            rs.getString("failure_recovery"),
            rs.getBoolean("approved"),
            rs.getBoolean("pinned"),
            rs.getString("latest_summary"),
            rs.getString("metadata"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getObject("approved_at", OffsetDateTime.class),
            rs.getString("approved_by"),
            rs.getObject("pinned_at", OffsetDateTime.class),
            rs.getString("pinned_by")
        ))
        .list();
  }

  private HytalePlaybook mapPlaybook(
      String playbookId,
      String machineId,
      String clientProfileId,
      String serverTarget,
      String scenarioId,
      String name,
      String targetWindow,
      String actionsJson,
      String expectedAnchorsJson,
      String failureRecoveryJson,
      boolean approved,
      boolean pinned,
      String latestSummary,
      String metadataJson,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      OffsetDateTime approvedAt,
      String approvedBy,
      OffsetDateTime pinnedAt,
      String pinnedBy
  ) {
    return new HytalePlaybook(
        playbookId,
        machineId,
        clientProfileId,
        serverTarget,
        scenarioId,
        name,
        targetWindow,
        jsonSupport.read(actionsJson, new TypeReference<>() {
        }),
        jsonSupport.readStringList(expectedAnchorsJson),
        jsonSupport.readMap(failureRecoveryJson),
        approved,
        pinned,
        latestSummary,
        jsonSupport.readMap(metadataJson),
        createdAt,
        updatedAt,
        approvedAt,
        approvedBy,
        pinnedAt,
        pinnedBy
    );
  }

  private String blank(String value) {
    return value == null ? "" : value;
  }
}

