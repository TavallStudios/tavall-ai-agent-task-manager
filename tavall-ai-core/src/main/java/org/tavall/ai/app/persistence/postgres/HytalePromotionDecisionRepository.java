package org.tavall.ai.app.persistence.postgres;

import org.tavall.ai.app.model.hytalelearning.HytalePromotionDecision;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class HytalePromotionDecisionRepository {

  private final JdbcClient jdbcClient;
  private final JsonSupport jsonSupport;

  public HytalePromotionDecisionRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
    this.jdbcClient = jdbcClient;
    this.jsonSupport = jsonSupport;
  }

  public HytalePromotionDecision create(
      String decisionId,
      String sessionId,
      String subjectType,
      String subjectId,
      String semanticKind,
      String decisionStatus,
      String summary,
      String promotedDocumentId,
      Map<String, Object> metadata
  ) {
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.hytale_promotion_decisions (
              decision_id,
              session_id,
              subject_type,
              subject_id,
              semantic_kind,
              decision_status,
              summary,
              promoted_document_id,
              metadata
            ) VALUES (
              :decisionId,
              NULLIF(:sessionId, ''),
              :subjectType,
              :subjectId,
              :semanticKind,
              :decisionStatus,
              :summary,
              NULLIF(:promotedDocumentId, ''),
              CAST(:metadata AS jsonb)
            )
            """)
        .param("decisionId", decisionId)
        .param("sessionId", blank(sessionId))
        .param("subjectType", subjectType)
        .param("subjectId", subjectId)
        .param("semanticKind", semanticKind)
        .param("decisionStatus", decisionStatus)
        .param("summary", summary)
        .param("promotedDocumentId", blank(promotedDocumentId))
        .param("metadata", jsonSupport.write(metadata))
        .update();
    return listBySession(sessionId, 1).get(0);
  }

  public List<HytalePromotionDecision> listBySession(String sessionId, int limit) {
    return jdbcClient.sql("""
            SELECT
              decision_id,
              session_id,
              subject_type,
              subject_id,
              semantic_kind,
              decision_status,
              summary,
              promoted_document_id,
              metadata,
              created_at
            FROM agent_task_manager.hytale_promotion_decisions
            WHERE (:sessionId = '' OR COALESCE(session_id, '') = :sessionId)
            ORDER BY created_at DESC
            LIMIT :limit
            """)
        .param("sessionId", blank(sessionId))
        .param("limit", limit)
        .query((rs, rowNum) -> new HytalePromotionDecision(
            rs.getString("decision_id"),
            rs.getString("session_id"),
            rs.getString("subject_type"),
            rs.getString("subject_id"),
            rs.getString("semantic_kind"),
            rs.getString("decision_status"),
            rs.getString("summary"),
            rs.getString("promoted_document_id"),
            jsonSupport.readMap(rs.getString("metadata")),
            rs.getObject("created_at", OffsetDateTime.class)
        ))
        .list();
  }

  private String blank(String value) {
    return value == null ? "" : value;
  }
}

