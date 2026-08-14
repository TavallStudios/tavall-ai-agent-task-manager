package org.tavall.ai.execution.distributed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Routes one bounded AI execution request across provider-supplied authorized targets.
 *
 * <p>This class owns no infrastructure authority. Target providers are responsible for returning
 * only targets that are currently authorized for the request and for executing through their own
 * approved runtime/transport boundary.</p>
 */
public final class TavallAIDistributedExecutionRouter {
    private final Map<String, TavallAIExecutionTargetProvider> providers;

    public TavallAIDistributedExecutionRouter(Iterable<? extends TavallAIExecutionTargetProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        LinkedHashMap<String, TavallAIExecutionTargetProvider> byId = new LinkedHashMap<>();
        for (TavallAIExecutionTargetProvider provider : providers) {
            TavallAIExecutionTargetProvider safeProvider = Objects.requireNonNull(provider, "provider");
            String providerId = requireText(safeProvider.providerId(), "providerId");
            if (byId.putIfAbsent(providerId, safeProvider) != null) {
                throw new IllegalArgumentException("Duplicate distributed execution provider: " + providerId);
            }
        }
        this.providers = Map.copyOf(byId);
    }

    public TavallAIExecutionResult execute(TavallAIExecutionRequest request) {
        TavallAIExecutionRequest safeRequest = Objects.requireNonNull(request, "request");
        List<Candidate> candidates = candidates(safeRequest);
        if (candidates.isEmpty()) {
            return TavallAIExecutionResult.failed(
                    TavallAIExecutionStatus.NO_ELIGIBLE_TARGET,
                    "No authorized ready target satisfies the requested execution capabilities and surfaces",
                    List.of()
            );
        }

        List<TavallAIExecutionAttempt> attempts = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (attempts.size() >= safeRequest.maximumAttempts()) {
                return TavallAIExecutionResult.failed(
                        TavallAIExecutionStatus.ATTEMPTS_EXHAUSTED,
                        "Distributed execution attempt budget exhausted",
                        attempts
                );
            }

            TavallAIExecutionProviderResult providerResult;
            try {
                providerResult = Objects.requireNonNull(
                        candidate.provider().execute(candidate.target(), safeRequest),
                        "execution provider result"
                );
            } catch (Exception exception) {
                providerResult = TavallAIExecutionProviderResult.terminalFailure(
                        "Execution provider threw " + exception.getClass().getSimpleName()
                );
            }

            TavallAIExecutionAttempt attempt = TavallAIExecutionAttempt.from(candidate.target(), providerResult);
            attempts.add(attempt);
            if (providerResult.success()) {
                return TavallAIExecutionResult.completed(providerResult.resultReference(), attempts);
            }
            if (!providerResult.retryable()) {
                return TavallAIExecutionResult.failed(
                        TavallAIExecutionStatus.EXECUTION_FAILED,
                        providerResult.message(),
                        attempts
                );
            }
        }

        TavallAIExecutionStatus status = attempts.size() >= safeRequest.maximumAttempts()
                ? TavallAIExecutionStatus.ATTEMPTS_EXHAUSTED
                : TavallAIExecutionStatus.EXECUTION_FAILED;
        return TavallAIExecutionResult.failed(
                status,
                "All eligible distributed execution targets failed",
                attempts
        );
    }

    private List<Candidate> candidates(TavallAIExecutionRequest request) {
        ArrayList<Candidate> result = new ArrayList<>();
        for (TavallAIExecutionTargetProvider provider : providers.values()) {
            List<TavallAIExecutionTarget> targets = Objects.requireNonNull(
                    provider.authorizedTargets(request),
                    "authorizedTargets result"
            );
            for (TavallAIExecutionTarget target : targets) {
                TavallAIExecutionTarget safeTarget = Objects.requireNonNull(target, "authorized target");
                if (!safeTarget.providerId().equals(provider.providerId())) {
                    throw new IllegalStateException(
                            "Execution target provider mismatch for " + safeTarget.id()
                    );
                }
                if (!safeTarget.ready()
                        || !request.allows(safeTarget.surface())
                        || !safeTarget.supportsAll(request.requiredCapabilities())) {
                    continue;
                }
                result.add(new Candidate(provider, safeTarget));
            }
        }

        result.sort(Comparator
                .comparingInt((Candidate candidate) -> request.surfacePreference(candidate.target().surface()))
                .thenComparing(Comparator.comparingInt(
                        (Candidate candidate) -> candidate.target().routingPriority()
                ).reversed())
                .thenComparing(candidate -> candidate.target().id())
                .thenComparing(candidate -> candidate.provider().providerId()));
        return List.copyOf(result);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private record Candidate(
            TavallAIExecutionTargetProvider provider,
            TavallAIExecutionTarget target
    ) {
    }
}
