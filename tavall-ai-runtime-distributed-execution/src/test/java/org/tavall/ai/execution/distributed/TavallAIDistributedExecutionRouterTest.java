package org.tavall.ai.execution.distributed;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TavallAIDistributedExecutionRouterTest {
    @Test
    void selectsHighestPriorityEligibleNodeTargetDeterministically() {
        FakeProvider provider = new FakeProvider("cloud-authorized", List.of(
                target("node-b", TavallAIExecutionSurface.NODE_AGENT, 20, "code"),
                target("node-a", TavallAIExecutionSurface.NODE_AGENT, 20, "code"),
                target("node-c", TavallAIExecutionSurface.NODE_AGENT, 10, "code")
        ));
        provider.result("node-a", TavallAIExecutionProviderResult.success("result://node-a"));

        TavallAIExecutionResult result = router(provider).execute(request(
                Set.of("code"),
                List.of(TavallAIExecutionSurface.NODE_AGENT),
                Set.of(),
                3
        ));

        assertTrue(result.success());
        assertEquals("result://node-a", result.resultReference());
        assertEquals(List.of("node-a"), attemptTargetIds(result));
    }

    @Test
    void fallsBackFromNodeToChatGptWebAfterRetryableFailure() {
        FakeProvider provider = new FakeProvider("cloud-authorized", List.of(
                target("node-a", TavallAIExecutionSurface.NODE_AGENT, 100, "code", "vision"),
                target("web-a", TavallAIExecutionSurface.CHATGPT_WEB, 100, "code", "vision")
        ));
        provider.result("node-a", TavallAIExecutionProviderResult.retryableFailure("node unavailable"));
        provider.result("web-a", TavallAIExecutionProviderResult.success("result://web-a"));

        TavallAIExecutionResult result = router(provider).execute(request(
                Set.of("code", "vision"),
                List.of(TavallAIExecutionSurface.NODE_AGENT, TavallAIExecutionSurface.CHATGPT_WEB),
                Set.of(),
                3
        ));

        assertTrue(result.success());
        assertEquals(List.of("node-a", "web-a"), attemptTargetIds(result));
        assertEquals(TavallAIExecutionSurface.CHATGPT_WEB, result.attempts().getLast().surface());
    }

    @Test
    void filtersTargetsMissingRequiredCapabilities() {
        FakeProvider provider = new FakeProvider("cloud-authorized", List.of(
                target("node-code", TavallAIExecutionSurface.NODE_AGENT, 100, "code"),
                target("web-vision", TavallAIExecutionSurface.CHATGPT_WEB, 10, "code", "vision")
        ));
        provider.result("web-vision", TavallAIExecutionProviderResult.success("result://vision"));

        TavallAIExecutionResult result = router(provider).execute(request(
                Set.of("vision"),
                List.of(),
                Set.of(),
                2
        ));

        assertTrue(result.success());
        assertEquals(List.of("web-vision"), attemptTargetIds(result));
    }

    @Test
    void honorsAllowedSurfaceConstraintWithoutWideningIt() {
        FakeProvider provider = new FakeProvider("cloud-authorized", List.of(
                target("node-a", TavallAIExecutionSurface.NODE_AGENT, 100, "code"),
                target("web-a", TavallAIExecutionSurface.CHATGPT_WEB, 1, "code")
        ));
        provider.result("web-a", TavallAIExecutionProviderResult.success("result://web"));

        TavallAIExecutionResult result = router(provider).execute(request(
                Set.of("code"),
                List.of(TavallAIExecutionSurface.NODE_AGENT),
                Set.of(TavallAIExecutionSurface.CHATGPT_WEB),
                2
        ));

        assertTrue(result.success());
        assertEquals(List.of("web-a"), attemptTargetIds(result));
    }

    @Test
    void stopsImmediatelyOnNonRetryableFailure() {
        FakeProvider provider = new FakeProvider("cloud-authorized", List.of(
                target("node-a", TavallAIExecutionSurface.NODE_AGENT, 100, "code"),
                target("web-a", TavallAIExecutionSurface.CHATGPT_WEB, 10, "code")
        ));
        provider.result("node-a", TavallAIExecutionProviderResult.terminalFailure("request rejected"));
        provider.result("web-a", TavallAIExecutionProviderResult.success("result://web"));

        TavallAIExecutionResult result = router(provider).execute(request(
                Set.of("code"),
                List.of(TavallAIExecutionSurface.NODE_AGENT, TavallAIExecutionSurface.CHATGPT_WEB),
                Set.of(),
                3
        ));

        assertFalse(result.success());
        assertEquals(TavallAIExecutionStatus.EXECUTION_FAILED, result.status());
        assertEquals(List.of("node-a"), attemptTargetIds(result));
    }

    @Test
    void respectsMaximumAttemptBudget() {
        FakeProvider provider = new FakeProvider("cloud-authorized", List.of(
                target("node-a", TavallAIExecutionSurface.NODE_AGENT, 30, "code"),
                target("node-b", TavallAIExecutionSurface.NODE_AGENT, 20, "code"),
                target("web-a", TavallAIExecutionSurface.CHATGPT_WEB, 10, "code")
        ));
        provider.defaultResult(TavallAIExecutionProviderResult.retryableFailure("temporarily unavailable"));

        TavallAIExecutionResult result = router(provider).execute(request(
                Set.of("code"),
                List.of(),
                Set.of(),
                2
        ));

        assertFalse(result.success());
        assertEquals(TavallAIExecutionStatus.ATTEMPTS_EXHAUSTED, result.status());
        assertEquals(List.of("node-a", "node-b"), attemptTargetIds(result));
    }

    @Test
    void failsClosedWhenNoEligibleTargetExists() {
        FakeProvider provider = new FakeProvider("cloud-authorized", List.of(
                new TavallAIExecutionTarget(
                        "node-unready",
                        "cloud-authorized",
                        TavallAIExecutionSurface.NODE_AGENT,
                        Set.of("code"),
                        false,
                        100,
                        "lease://opaque"
                )
        ));

        TavallAIExecutionResult result = router(provider).execute(request(
                Set.of("code"),
                List.of(),
                Set.of(),
                2
        ));

        assertFalse(result.success());
        assertEquals(TavallAIExecutionStatus.NO_ELIGIBLE_TARGET, result.status());
        assertTrue(result.attempts().isEmpty());
    }

    private TavallAIDistributedExecutionRouter router(FakeProvider provider) {
        return new TavallAIDistributedExecutionRouter(List.of(provider));
    }

    private TavallAIExecutionRequest request(
            Set<String> capabilities,
            List<TavallAIExecutionSurface> preferredSurfaces,
            Set<TavallAIExecutionSurface> allowedSurfaces,
            int maximumAttempts
    ) {
        return new TavallAIExecutionRequest(
                "execution-1",
                "job-1",
                7,
                capabilities,
                preferredSurfaces,
                allowedSurfaces,
                maximumAttempts,
                "Build and validate the requested artifact.",
                "authority://opaque-job-lease"
        );
    }

    private TavallAIExecutionTarget target(
            String id,
            TavallAIExecutionSurface surface,
            int priority,
            String... capabilities
    ) {
        return new TavallAIExecutionTarget(
                id,
                "cloud-authorized",
                surface,
                Set.of(capabilities),
                true,
                priority,
                "target://" + id
        );
    }

    private List<String> attemptTargetIds(TavallAIExecutionResult result) {
        return result.attempts().stream().map(TavallAIExecutionAttempt::targetId).toList();
    }

    private static final class FakeProvider implements TavallAIExecutionTargetProvider {
        private final String id;
        private final List<TavallAIExecutionTarget> targets;
        private final Map<String, TavallAIExecutionProviderResult> results = new LinkedHashMap<>();
        private TavallAIExecutionProviderResult defaultResult = TavallAIExecutionProviderResult.terminalFailure(
                "no fake result configured"
        );
        private final List<String> executions = new ArrayList<>();

        private FakeProvider(String id, List<TavallAIExecutionTarget> targets) {
            this.id = id;
            this.targets = targets;
        }

        void result(String targetId, TavallAIExecutionProviderResult result) {
            results.put(targetId, result);
        }

        void defaultResult(TavallAIExecutionProviderResult result) {
            defaultResult = result;
        }

        @Override
        public String providerId() {
            return id;
        }

        @Override
        public List<TavallAIExecutionTarget> authorizedTargets(TavallAIExecutionRequest request) {
            return targets;
        }

        @Override
        public TavallAIExecutionProviderResult execute(
                TavallAIExecutionTarget target,
                TavallAIExecutionRequest request
        ) {
            executions.add(target.id());
            return results.getOrDefault(target.id(), defaultResult);
        }
    }
}
