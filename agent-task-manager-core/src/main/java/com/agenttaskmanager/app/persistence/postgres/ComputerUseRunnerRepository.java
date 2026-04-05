package com.agenttaskmanager.app.persistence.postgres;

import com.agenttaskmanager.app.model.computeruse.ComputerUseRunnerRegistration;
import com.agenttaskmanager.app.model.computeruse.ComputerUseRunnerSummary;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ComputerUseRunnerRepository {

  private final JdbcClient jdbcClient;
  private final JsonSupport jsonSupport;

  public ComputerUseRunnerRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
    this.jdbcClient = jdbcClient;
    this.jsonSupport = jsonSupport;
  }

  public ComputerUseRunnerSummary upsertRunner(ComputerUseRunnerRegistration registration) {
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.computer_use_runners (
              runner_id,
              display_name,
              host_name,
              base_url,
              launcher_path,
              client_path,
              status,
              current_lease_session_id,
              supported_capture_modes,
              capabilities,
              metadata,
              last_seen_at
            ) VALUES (
              :runnerId,
              :displayName,
              :hostName,
              :baseUrl,
              :launcherPath,
              :clientPath,
              'online',
              NULL,
              CAST(:supportedCaptureModes AS jsonb),
              CAST(:capabilities AS jsonb),
              CAST(:metadata AS jsonb),
              now()
            )
            ON CONFLICT (runner_id) DO UPDATE
            SET display_name = EXCLUDED.display_name,
                host_name = EXCLUDED.host_name,
                base_url = EXCLUDED.base_url,
                launcher_path = EXCLUDED.launcher_path,
                client_path = EXCLUDED.client_path,
                status = 'online',
                supported_capture_modes = EXCLUDED.supported_capture_modes,
                capabilities = EXCLUDED.capabilities,
                metadata = EXCLUDED.metadata,
                last_seen_at = now(),
                updated_at = now()
            """)
        .param("runnerId", registration.runnerId())
        .param("displayName", registration.displayName())
        .param("hostName", registration.hostName())
        .param("baseUrl", registration.baseUrl())
        .param("launcherPath", registration.launcherPath())
        .param("clientPath", registration.clientPath())
        .param("supportedCaptureModes", jsonSupport.write(registration.supportedCaptureModes()))
        .param("capabilities", jsonSupport.write(registration.capabilities()))
        .param("metadata", jsonSupport.write(registration.metadata()))
        .update();
    return getRunner(registration.runnerId());
  }

  public List<ComputerUseRunnerSummary> listRunners() {
    return jdbcClient.sql("""
            SELECT runner_id, display_name, host_name, base_url, launcher_path, client_path,
                   status, current_lease_session_id, supported_capture_modes, capabilities,
                   metadata, created_at, updated_at, last_seen_at
            FROM agent_task_manager.computer_use_runners
            ORDER BY updated_at DESC, runner_id ASC
            """)
        .query((rs, rowNum) -> mapRunner(rs))
        .list();
  }

  public ComputerUseRunnerSummary getRunner(String runnerId) {
    return jdbcClient.sql("""
            SELECT runner_id, display_name, host_name, base_url, launcher_path, client_path,
                   status, current_lease_session_id, supported_capture_modes, capabilities,
                   metadata, created_at, updated_at, last_seen_at
            FROM agent_task_manager.computer_use_runners
            WHERE runner_id = :runnerId
            """)
        .param("runnerId", runnerId)
        .query((rs, rowNum) -> mapRunner(rs))
        .single();
  }

  public Optional<ComputerUseRunnerSummary> findRunner(String runnerId) {
    return jdbcClient.sql("""
            SELECT runner_id, display_name, host_name, base_url, launcher_path, client_path,
                   status, current_lease_session_id, supported_capture_modes, capabilities,
                   metadata, created_at, updated_at, last_seen_at
            FROM agent_task_manager.computer_use_runners
            WHERE runner_id = :runnerId
            """)
        .param("runnerId", runnerId)
        .query((rs, rowNum) -> mapRunner(rs))
        .optional();
  }

  public void updateLease(String runnerId, String sessionId, String status) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.computer_use_runners
            SET current_lease_session_id = NULLIF(:sessionId, ''),
                status = :status,
                last_seen_at = now(),
                updated_at = now()
            WHERE runner_id = :runnerId
            """)
        .param("runnerId", runnerId)
        .param("sessionId", sessionId == null ? "" : sessionId)
        .param("status", status)
        .update();
  }

  public void deleteRunner(String runnerId) {
    jdbcClient.sql("""
            DELETE FROM agent_task_manager.computer_use_runners
            WHERE runner_id = :runnerId
            """)
        .param("runnerId", runnerId)
        .update();
  }

  private ComputerUseRunnerSummary mapRunner(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new ComputerUseRunnerSummary(
        rs.getString("runner_id"),
        rs.getString("display_name"),
        rs.getString("host_name"),
        rs.getString("base_url"),
        rs.getString("launcher_path"),
        rs.getString("client_path"),
        rs.getString("status"),
        rs.getString("current_lease_session_id"),
        jsonSupport.readStringList(rs.getString("supported_capture_modes")),
        jsonSupport.readMap(rs.getString("capabilities")),
        jsonSupport.readMap(rs.getString("metadata")),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class),
        rs.getObject("last_seen_at", OffsetDateTime.class)
    );
  }
}
