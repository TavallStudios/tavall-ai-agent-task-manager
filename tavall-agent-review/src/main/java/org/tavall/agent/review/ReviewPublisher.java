package org.tavall.agent.review;

@FunctionalInterface
public interface ReviewPublisher {
    void publish(ReviewRequest request, ReviewReport report);

    static ReviewPublisher noop() {
        return (request, report) -> { };
    }
}
