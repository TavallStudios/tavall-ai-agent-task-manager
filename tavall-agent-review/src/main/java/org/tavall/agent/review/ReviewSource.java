package org.tavall.agent.review;

@FunctionalInterface
public interface ReviewSource {
    ReviewContext load(ReviewRequest request);
}
