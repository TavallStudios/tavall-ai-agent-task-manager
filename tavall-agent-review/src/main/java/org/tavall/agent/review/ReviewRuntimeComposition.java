package org.tavall.agent.review;

import org.tavall.dependency.DependencyLoader;
import org.tavall.internal.event.EventBus;

/** Named tavall-di scope for composing the review runtime without global singleton leakage. */
public final class ReviewRuntimeComposition {
    public static final String SCOPE = "tavall-ai-review-runtime";

    private ReviewRuntimeComposition() { }

    public static ReviewRuntime compose(
            ReviewSource source,
            ReviewAnalyzerRegistry analyzers,
            ReviewPublisher publisher,
            ReviewAuditSink auditSink,
            ReviewContextCache contextCache,
            EventBus eventBus
    ) {
        DependencyLoader dependencies = DependencyLoader.getDependencyLoader(SCOPE);
        dependencies.clear();
        dependencies.registerInstance(ReviewSource.class, source);
        dependencies.registerInstance(ReviewAnalyzerRegistry.class, analyzers);
        dependencies.registerInstance(ReviewPublisher.class, publisher == null ? ReviewPublisher.noop() : publisher);
        dependencies.registerInstance(ReviewAuditSink.class, auditSink == null ? ReviewAuditSink.noop() : auditSink);
        dependencies.registerInstance(ReviewContextCache.class, contextCache == null ? ReviewContextCache.none() : contextCache);
        dependencies.registerInstance(EventBus.class, eventBus == null ? new EventBus() : eventBus);
        return new ReviewRuntime(
                dependencies.requireInstance(ReviewSource.class),
                dependencies.requireInstance(ReviewAnalyzerRegistry.class),
                dependencies.requireInstance(ReviewPublisher.class),
                dependencies.requireInstance(ReviewAuditSink.class),
                dependencies.requireInstance(ReviewContextCache.class),
                dependencies.requireInstance(EventBus.class)
        );
    }
}
