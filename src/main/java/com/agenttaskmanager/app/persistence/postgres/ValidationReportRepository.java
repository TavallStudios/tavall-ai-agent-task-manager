package com.agenttaskmanager.app.persistence.postgres;

import com.agenttaskmanager.app.model.validation.ValidationEngine;
import com.agenttaskmanager.app.model.validation.ValidationReport;
import com.agenttaskmanager.app.model.validation.ValidationSeverity;
import com.agenttaskmanager.app.model.validation.ValidationViolation;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ValidationReportRepository {

  private final JdbcClient jdbcClient;

  public ValidationReportRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public ValidationReport storeReport(
      String taskId,
      String workerTaskId,
      String cleanupReviewId,
      ValidationReport report
  ) {
    String reportId = report.reportId() == null || report.reportId().isBlank()
        ? "vr_" + UUID.randomUUID()
        : report.reportId();

    jdbcClient.sql("""
            INSERT INTO agent_task_manager.validation_reports (
              report_id,
              task_id,
              worker_task_id,
              cleanup_review_id,
              status,
              compliance_score,
              summary,
              completed_at
            ) VALUES (
              :reportId,
              :taskId,
              NULLIF(:workerTaskId, ''),
              NULLIF(:cleanupReviewId, ''),
              :status,
              :complianceScore,
              :summary,
              COALESCE(:completedAt, now())
            )
            ON CONFLICT (report_id) DO UPDATE SET
              status = EXCLUDED.status,
              compliance_score = EXCLUDED.compliance_score,
              summary = EXCLUDED.summary,
              cleanup_review_id = EXCLUDED.cleanup_review_id,
              completed_at = EXCLUDED.completed_at,
              updated_at = now()
            """)
        .param("reportId", reportId)
        .param("taskId", taskId)
        .param("workerTaskId", workerTaskId == null ? "" : workerTaskId)
        .param("cleanupReviewId", cleanupReviewId == null ? "" : cleanupReviewId)
        .param("status", report.status())
        .param("complianceScore", report.complianceScore())
        .param("summary", report.summary())
        .param("completedAt", report.completedAt())
        .update();

    jdbcClient.sql("DELETE FROM agent_task_manager.validation_violations WHERE report_id = :reportId")
        .param("reportId", reportId)
        .update();

    for (ValidationViolation violation : report.violations()) {
      jdbcClient.sql("""
              INSERT INTO agent_task_manager.validation_violations (
                report_id,
                rule_id,
                severity,
                target_type,
                target_name,
                engine_source,
                explanation,
                remediation
              ) VALUES (
                :reportId,
                :ruleId,
                :severity,
                :targetType,
                :targetName,
                :engineSource,
                :explanation,
                NULLIF(:remediation, '')
              )
              """)
          .param("reportId", reportId)
          .param("ruleId", violation.ruleId())
          .param("severity", violation.severity().name())
          .param("targetType", violation.targetType())
          .param("targetName", violation.targetName())
          .param("engineSource", violation.engineSource().name())
          .param("explanation", violation.explanation())
          .param("remediation", violation.remediation() == null ? "" : violation.remediation())
          .update();
    }

    return getReport(reportId);
  }

  public ValidationReport getReport(String reportId) {
    ValidationReport summary = jdbcClient.sql("""
            SELECT
              report_id,
              task_id,
              worker_task_id,
              status,
              compliance_score,
              summary,
              completed_at
            FROM agent_task_manager.validation_reports
            WHERE report_id = :reportId
            """)
        .param("reportId", reportId)
        .query((rs, rowNum) -> new ValidationReport(
            rs.getString("report_id"),
            rs.getString("task_id"),
            rs.getString("worker_task_id"),
            rs.getString("status"),
            rs.getDouble("compliance_score"),
            rs.getString("summary"),
            List.of(),
            rs.getObject("completed_at", OffsetDateTime.class)
        ))
        .single();
    return new ValidationReport(
        summary.reportId(),
        summary.taskId(),
        summary.workerTaskId(),
        summary.status(),
        summary.complianceScore(),
        summary.summary(),
        listViolations(reportId),
        summary.completedAt()
    );
  }

  public List<ValidationReport> listReportsByTask(String taskId) {
    return jdbcClient.sql("""
            SELECT
              report_id,
              task_id,
              worker_task_id,
              status,
              compliance_score,
              summary,
              completed_at
            FROM agent_task_manager.validation_reports
            WHERE task_id = :taskId
            ORDER BY updated_at DESC
            """)
        .param("taskId", taskId)
        .query((rs, rowNum) -> new ValidationReport(
            rs.getString("report_id"),
            rs.getString("task_id"),
            rs.getString("worker_task_id"),
            rs.getString("status"),
            rs.getDouble("compliance_score"),
            rs.getString("summary"),
            listViolations(rs.getString("report_id")),
            rs.getObject("completed_at", OffsetDateTime.class)
        ))
        .list();
  }

  private List<ValidationViolation> listViolations(String reportId) {
    return jdbcClient.sql("""
            SELECT
              rule_id,
              severity,
              target_type,
              target_name,
              engine_source,
              explanation,
              remediation
            FROM agent_task_manager.validation_violations
            WHERE report_id = :reportId
            ORDER BY severity DESC, violation_id ASC
            """)
        .param("reportId", reportId)
        .query((rs, rowNum) -> new ValidationViolation(
            rs.getString("rule_id"),
            ValidationSeverity.valueOf(rs.getString("severity")),
            rs.getString("target_type"),
            rs.getString("target_name"),
            ValidationEngine.valueOf(rs.getString("engine_source")),
            rs.getString("explanation"),
            rs.getString("remediation")
        ))
        .list();
  }
}
