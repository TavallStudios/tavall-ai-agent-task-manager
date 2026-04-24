package org.tavall.ai.app.persistence.postgres;

import org.tavall.ai.app.model.hytalelearning.HytaleVisualAnchor;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class HytaleVisualAnchorRepository {

  private final JdbcClient jdbcClient;
  private final JsonSupport jsonSupport;

  public HytaleVisualAnchorRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
    this.jdbcClient = jdbcClient;
    this.jsonSupport = jsonSupport;
  }

  public HytaleVisualAnchor upsert(
      String anchorId,
      String machineId,
      String clientProfileId,
      String serverTarget,
      String scenarioId,
      String anchorKey,
      String sourceWindow,
      Map<String, Object> normalizedRegion,
      String description,
      double confidence,
      String storageBackend,
      String storageKey,
      Map<String, Object> metadata
  ) {
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.hytale_visual_anchors (
              anchor_id,
              machine_id,
              client_profile_id,
              server_target,
              scenario_id,
              anchor_key,
              source_window,
              normalized_region,
              description,
              confidence,
              storage_backend,
              storage_key,
              last_validated_at,
              metadata
            ) VALUES (
              :anchorId,
              :machineId,
              NULLIF(:clientProfileId, ''),
              NULLIF(:serverTarget, ''),
              NULLIF(:scenarioId, ''),
              :anchorKey,
              :sourceWindow,
              CAST(:normalizedRegion AS jsonb),
              :description,
              :confidence,
              :storageBackend,
              NULLIF(:storageKey, ''),
              now(),
              CAST(:metadata AS jsonb)
            )
            ON CONFLICT (anchor_id) DO UPDATE SET
              machine_id = EXCLUDED.machine_id,
              client_profile_id = EXCLUDED.client_profile_id,
              server_target = EXCLUDED.server_target,
              scenario_id = EXCLUDED.scenario_id,
              anchor_key = EXCLUDED.anchor_key,
              source_window = EXCLUDED.source_window,
              normalized_region = EXCLUDED.normalized_region,
              description = EXCLUDED.description,
              confidence = EXCLUDED.confidence,
              storage_backend = EXCLUDED.storage_backend,
              storage_key = EXCLUDED.storage_key,
              last_validated_at = now(),
              metadata = EXCLUDED.metadata,
              updated_at = now()
            """)
        .param("anchorId", anchorId)
        .param("machineId", machineId)
        .param("clientProfileId", blank(clientProfileId))
        .param("serverTarget", blank(serverTarget))
        .param("scenarioId", blank(scenarioId))
        .param("anchorKey", anchorKey)
        .param("sourceWindow", sourceWindow)
        .param("normalizedRegion", jsonSupport.write(normalizedRegion))
        .param("description", description)
        .param("confidence", confidence)
        .param("storageBackend", storageBackend)
        .param("storageKey", blank(storageKey))
        .param("metadata", jsonSupport.write(metadata))
        .update();
    return listByScope(machineId, clientProfileId, serverTarget, scenarioId, 50).stream()
        .filter(anchor -> anchor.anchorId().equals(anchorId))
        .findFirst()
        .orElseThrow();
  }

  public List<HytaleVisualAnchor> listByScope(
      String machineId,
      String clientProfileId,
      String serverTarget,
      String scenarioId,
      int limit
  ) {
    return jdbcClient.sql("""
            SELECT
              anchor_id,
              machine_id,
              client_profile_id,
              server_target,
              scenario_id,
              anchor_key,
              source_window,
              normalized_region,
              description,
              confidence,
              storage_backend,
              storage_key,
              last_validated_at,
              metadata,
              created_at,
              updated_at
            FROM agent_task_manager.hytale_visual_anchors
            WHERE (:machineId = '' OR machine_id = :machineId)
              AND (:clientProfileId = '' OR COALESCE(client_profile_id, '') = :clientProfileId)
              AND (:serverTarget = '' OR COALESCE(server_target, '') = :serverTarget)
              AND (:scenarioId = '' OR COALESCE(scenario_id, '') = :scenarioId)
            ORDER BY confidence DESC, updated_at DESC
            LIMIT :limit
            """)
        .param("machineId", blank(machineId))
        .param("clientProfileId", blank(clientProfileId))
        .param("serverTarget", blank(serverTarget))
        .param("scenarioId", blank(scenarioId))
        .param("limit", limit)
        .query((rs, rowNum) -> new HytaleVisualAnchor(
            rs.getString("anchor_id"),
            rs.getString("machine_id"),
            rs.getString("client_profile_id"),
            rs.getString("server_target"),
            rs.getString("scenario_id"),
            rs.getString("anchor_key"),
            rs.getString("source_window"),
            jsonSupport.readMap(rs.getString("normalized_region")),
            rs.getString("description"),
            rs.getDouble("confidence"),
            rs.getString("storage_backend"),
            rs.getString("storage_key"),
            rs.getObject("last_validated_at", OffsetDateTime.class),
            jsonSupport.readMap(rs.getString("metadata")),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        ))
        .list();
  }

  private String blank(String value) {
    return value == null ? "" : value;
  }
}

