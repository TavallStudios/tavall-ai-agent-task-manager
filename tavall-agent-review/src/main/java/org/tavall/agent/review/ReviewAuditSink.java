package org.tavall.agent.review;

@FunctionalInterface
public interface ReviewAuditSink {
    void record(ReviewReport report);

    static ReviewAuditSink noop() {
        return report -> { };
    }
}
