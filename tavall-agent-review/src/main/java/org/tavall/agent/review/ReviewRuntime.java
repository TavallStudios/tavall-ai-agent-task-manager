package org.tavall.agent.review;

import org.tavall.internal.event.EventBus;
import org.tavall.internal.utils.concurrent.AsyncTask;
import org.tavall.logging.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Orchestrates an independent exact-head review without mutating repository code. */
public final class ReviewRuntime {
    private final ReviewSource source;
    private final ReviewAnalyzerRegistry analyzers;
    private final ReviewPublisher publisher;
    private final ReviewAuditSink auditSink;
    private final ReviewContextCache contextCache;
    private final EventBus eventBus;

    public ReviewRuntime(
            ReviewSource source,
            ReviewAnalyzerRegistry analyzers,
            ReviewPublisher publisher,
            ReviewAuditSink auditSink,
            ReviewContextCache contextCache,
            EventBus eventBus
    ) {
        this.source = Objects.requireNonNull(source, "source");
        this.analyzers = Objects.requireNonNull(analyzers, "analyzers");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
        this.contextCache = Objects.requireNonNull(contextCache, "contextCache");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    public ReviewReport review(ReviewRequest request) {
        requireExactHead(request);
        eventBus.fire(ReviewLifecycleEvent.started(request));
        Log.info("Review started for " + request.repository() + "@" + request.exactHeadSha() + " (" + request.profile() + ")");
        try {
            ReviewContext context = contextCache.get(request).orElseGet(() -> {
                ReviewContext loaded = source.load(request);
                if (!loaded.request().exactHeadSha().equals(request.exactHeadSha())) {
                    throw new IllegalStateException("Review source returned a different exact head");
                }
                contextCache.put(loaded);
                return loaded;
            });

            List<ReviewAnalyzer> selected = analyzers.getRegistryDataAsList().stream()
                    .filter(analyzer -> selectedFor(request.focuses(), analyzer.categories()))
                    .toList();
            if (selected.isEmpty()) throw new IllegalStateException("No review analyzers are registered for the requested focus");

            List<CompletableFuture<List<ReviewFinding>>> tasks = selected.stream()
                    .map(analyzer -> AsyncTask.supplyAsync(() -> analyzer.analyze(context)))
                    .toList();
            List<ReviewFinding> findings = new ArrayList<>();
            for (CompletableFuture<List<ReviewFinding>> task : tasks) {
                findings.addAll(task.join());
            }

            ReviewReport report = ReviewReport.create(request.repository(), request.exactHeadSha(), request.profile(),
                    findings, context.evidence(), context.inspectedAreas());
            auditSink.record(report);
            if (request.publicationPolicy().publish()) publisher.publish(request, report);
            eventBus.fire(ReviewLifecycleEvent.completed(request, report));
            Log.success("Review completed for " + request.repository() + "@" + request.exactHeadSha() + ": " + report.disposition());
            return report;
        } catch (CompletionException exception) {
            Throwable failure = exception.getCause() == null ? exception : exception.getCause();
            eventBus.fire(ReviewLifecycleEvent.failed(request, failure));
            Log.exception(failure);
            throw new IllegalStateException("Review analyzer failed for exact head " + request.exactHeadSha(), failure);
        } catch (RuntimeException exception) {
            eventBus.fire(ReviewLifecycleEvent.failed(request, exception));
            Log.exception(exception);
            throw exception;
        }
    }

    private static boolean selectedFor(Set<ReviewCategory> focuses, Set<ReviewCategory> categories) {
        return focuses == null || focuses.isEmpty() || categories.stream().anyMatch(focuses::contains);
    }

    private static void requireExactHead(ReviewRequest request) {
        if (request.exactHeadSha().isBlank()) throw new IllegalArgumentException("Review requires an exact head SHA");
    }
}
