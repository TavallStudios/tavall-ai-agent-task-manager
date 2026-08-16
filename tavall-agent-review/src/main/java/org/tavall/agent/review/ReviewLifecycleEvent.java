package org.tavall.agent.review;

import org.tavall.enums.EventCapability;
import org.tavall.platform.global.abstracts.AbstractEvent;

import java.util.Objects;

public final class ReviewLifecycleEvent extends AbstractEvent {
    public enum Phase { STARTED, COMPLETED, FAILED }

    private final Phase phase;
    private final ReviewRequest request;
    private final ReviewReport report;
    private final Throwable failure;

    private ReviewLifecycleEvent(Phase phase, ReviewRequest request, ReviewReport report, Throwable failure) {
        this.phase = Objects.requireNonNull(phase, "phase");
        this.request = Objects.requireNonNull(request, "request");
        this.report = report;
        this.failure = failure;
        enableCapability(EventCapability.FIREABLE);
    }

    public static ReviewLifecycleEvent started(ReviewRequest request) {
        return new ReviewLifecycleEvent(Phase.STARTED, request, null, null);
    }

    public static ReviewLifecycleEvent completed(ReviewRequest request, ReviewReport report) {
        return new ReviewLifecycleEvent(Phase.COMPLETED, request, report, null);
    }

    public static ReviewLifecycleEvent failed(ReviewRequest request, Throwable failure) {
        return new ReviewLifecycleEvent(Phase.FAILED, request, null, failure);
    }

    public Phase phase() { return phase; }
    public ReviewRequest request() { return request; }
    public ReviewReport report() { return report; }
    public Throwable failure() { return failure; }
}
