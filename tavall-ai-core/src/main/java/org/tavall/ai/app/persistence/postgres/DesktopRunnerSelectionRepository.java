package org.tavall.ai.app.persistence.postgres;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DesktopRunnerSelectionRepository {

  private static final String DEFAULT_SELECTION_KEY = "default";

  private final JdbcClient jdbcClient;

  public DesktopRunnerSelectionRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public Optional<String> loadSelectedProfileId() {
    return jdbcClient.sql("""
            SELECT profile_id
            FROM agent_task_manager.desktop_remote_runner_selection
            WHERE selection_key = :selectionKey
              AND profile_id IS NOT NULL
              AND profile_id <> ''
            """)
        .param("selectionKey", DEFAULT_SELECTION_KEY)
        .query(String.class)
        .optional();
  }

  public void saveSelectedProfileId(String profileId) {
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.desktop_remote_runner_selection (
              selection_key,
              profile_id,
              updated_at
            ) VALUES (
              :selectionKey,
              :profileId,
              now()
            )
            ON CONFLICT (selection_key) DO UPDATE
            SET profile_id = EXCLUDED.profile_id,
                updated_at = now()
            """)
        .param("selectionKey", DEFAULT_SELECTION_KEY)
        .param("profileId", profileId == null ? "" : profileId.strip())
        .update();
  }

  public void clearSelectedProfile(String profileId) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.desktop_remote_runner_selection
            SET profile_id = NULL,
                updated_at = now()
            WHERE selection_key = :selectionKey
              AND profile_id = :profileId
            """)
        .param("selectionKey", DEFAULT_SELECTION_KEY)
        .param("profileId", profileId == null ? "" : profileId.strip())
        .update();
  }
}


