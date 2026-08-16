package org.tavall.agent.review;

import org.tavall.database.core.database.IDatabase;

import java.util.Objects;

/** Durable review audit trail using tavall-database's prepared-query contract. */
public final class TavallDatabaseReviewAuditSink implements ReviewAuditSink {
    private final IDatabase database;

    public TavallDatabaseReviewAuditSink(IDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
        if (!database.isAvailable()) throw new IllegalStateException("Review audit database is unavailable");
        database.queries().executePreparedStatement("""
                CREATE TABLE IF NOT EXISTS tavall_ai_review_audit (
                    repository VARCHAR(255) NOT NULL,
                    exact_head_sha VARCHAR(128) NOT NULL,
                    profile VARCHAR(32) NOT NULL,
                    disposition VARCHAR(32) NOT NULL,
                    finding_count INTEGER NOT NULL,
                    completed_at VARCHAR(64) NOT NULL,
                    findings TEXT NOT NULL,
                    evidence TEXT NOT NULL,
                    PRIMARY KEY (repository, exact_head_sha, completed_at)
                )
                """);
    }

    @Override
    public void record(ReviewReport report) {
        boolean saved = database.queries().executePreparedStatement("""
                INSERT INTO tavall_ai_review_audit
                    (repository, exact_head_sha, profile, disposition, finding_count, completed_at, findings, evidence)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                report.repository(),
                report.exactHeadSha(),
                report.profile().name(),
                report.disposition().name(),
                report.findings().size(),
                report.completedAt().toString(),
                report.findings().toString(),
                report.validationEvidence().toString());
        if (!saved) throw new IllegalStateException("Failed to persist review audit for " + report.repository() + "@" + report.exactHeadSha());
    }
}
