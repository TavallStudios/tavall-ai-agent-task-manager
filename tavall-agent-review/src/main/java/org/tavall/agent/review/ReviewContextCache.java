package org.tavall.agent.review;

import java.util.Optional;

public interface ReviewContextCache extends AutoCloseable {
    Optional<ReviewContext> get(ReviewRequest request);

    void put(ReviewContext context);

    void invalidate(ReviewRequest request);

    @Override
    default void close() { }

    static ReviewContextCache none() {
        return new ReviewContextCache() {
            @Override public Optional<ReviewContext> get(ReviewRequest request) { return Optional.empty(); }
            @Override public void put(ReviewContext context) { }
            @Override public void invalidate(ReviewRequest request) { }
        };
    }
}
