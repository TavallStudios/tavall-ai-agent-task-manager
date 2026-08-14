package org.tavall.ai.execution.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.tavall.agent.TavallAgent;
import org.tavall.agent.TavallAgentCapability;
import org.tavall.agent.TavallAgentKind;
import org.tavall.ai.core.annotation.AIFunction;
import org.tavall.ai.core.catalog.AIFunctionCatalog;
import org.tavall.ai.core.catalog.AIFunctionCatalogView;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TavallAIModelExecutionEngineTest {
    @Test
    void intersectsAgentRequestsWithAuthoritativeFunctionPolicyAndUsesObservedToolCount() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        AIFunctionCatalog catalog = new AIFunctionCatalog(mapper);
        TestFunctions functions = new TestFunctions();
        catalog.registerInstances(functions);
        AtomicBoolean providerCalled = new AtomicBoolean();
        TavallAIModelProvider provider = provider("test", providerCalled, request -> {
            assertEquals(Set.of("safe_read"), request.functionView().getFunctionDefinitions().keySet());
            var denied = request.functionView().invokeResult("dangerous_write", mapper.createObjectNode());
            return new TavallAIModelExecutionResult(
                    TavallAIModelExecutionStatus.COMPLETED, denied.getPayload(), 999, 0, ""
            );
        });
        TavallAIModelExecutionEngine engine = new TavallAIModelExecutionEngine(
                catalog,
                (root, definition, job) -> new AIFunctionCatalogView(
                        root, function -> function.getName().equals("safe_read")
                ),
                List.of(provider)
        );

        TavallAIModelExecutionResult result = engine.execute(
                new TavallAIModelExecutionDefinition(agent("auditor", Set.of("safe_read", "dangerous_write")), "test"),
                new TavallAIModelJob("job-1", "inspect", 0, Map.of()),
                new TavallAIModelExecutionBudget(Duration.ofMinutes(1), 5, 0)
        );

        assertTrue(providerCalled.get());
        assertEquals(1, result.toolCalls());
        assertEquals(AIFunctionCatalogView.SCOPE_DENIED_ERROR_CODE, result.output().path("errorCode").asText());
        assertFalse(functions.dangerousWriteInvoked);
    }

    @Test
    void rejectsFunctionPolicyViewBackedByAnotherCatalogBeforeProviderRuns() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        AIFunctionCatalog root = new AIFunctionCatalog(mapper);
        root.registerInstances(new SameNameFunction());
        AIFunctionCatalog other = new AIFunctionCatalog(mapper);
        other.registerInstances(new SameNameFunction());
        AtomicBoolean providerCalled = new AtomicBoolean();
        TavallAIModelExecutionEngine engine = new TavallAIModelExecutionEngine(
                root,
                (catalog, definition, job) -> new AIFunctionCatalogView(other, ignored -> true),
                List.of(provider("test", providerCalled, request -> completed()))
        );

        TavallAIModelExecutionResult result = engine.execute(
                new TavallAIModelExecutionDefinition(agent("review", Set.of("same_name")), "test"),
                new TavallAIModelJob("job-mismatch", "work", 0, Map.of()),
                new TavallAIModelExecutionBudget(Duration.ofMinutes(1), 1, 0)
        );

        assertFalse(providerCalled.get());
        assertEquals(TavallAIModelExecutionEngine.FUNCTION_VIEW_MISMATCH,
                result.output().path("errorCode").asText());
    }

    @Test
    void enforcesToolBudgetBeforeSecondSideEffect() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        AIFunctionCatalog catalog = new AIFunctionCatalog(mapper);
        CountingFunctions functions = new CountingFunctions();
        catalog.registerInstances(functions);
        TavallAIModelProvider provider = provider("test", new AtomicBoolean(), request -> {
            assertTrue(request.functionView().invokeResult("counted_write", mapper.createObjectNode()).isSuccess());
            var second = request.functionView().invokeResult("counted_write", mapper.createObjectNode());
            return new TavallAIModelExecutionResult(
                    TavallAIModelExecutionStatus.COMPLETED, second.getPayload(), 999, 0, ""
            );
        });
        TavallAIModelExecutionEngine engine = new TavallAIModelExecutionEngine(
                catalog,
                (root, definition, job) -> new AIFunctionCatalogView(root, ignored -> true),
                List.of(provider)
        );

        TavallAIModelExecutionResult result = engine.execute(
                new TavallAIModelExecutionDefinition(agent("writer", Set.of("counted_write")), "test"),
                new TavallAIModelJob("job-budget", "write", 0, Map.of()),
                new TavallAIModelExecutionBudget(Duration.ofMinutes(1), 1, 0)
        );

        assertEquals(1, functions.invocations.get());
        assertEquals(1, result.toolCalls());
        assertEquals(AIFunctionCatalogView.INVOCATION_BUDGET_EXCEEDED_ERROR_CODE,
                result.output().path("errorCode").asText());
    }

    @Test
    void timesOutProviderExecutionAndFailsClosedForMissingProvider() {
        AIFunctionCatalog catalog = new AIFunctionCatalog(new ObjectMapper().findAndRegisterModules());
        TavallAIModelExecutionEngine timeoutEngine = new TavallAIModelExecutionEngine(
                catalog,
                (root, definition, job) -> new AIFunctionCatalogView(root, ignored -> true),
                List.of(new TavallAIModelProvider() {
                    public String providerId() { return "blocking"; }
                    public TavallAIModelExecutionResult execute(TavallAIModelExecutionRequest request) throws Exception {
                        Thread.sleep(Duration.ofSeconds(5));
                        return completed();
                    }
                })
        );
        TavallAIModelExecutionResult timeout = timeoutEngine.execute(
                new TavallAIModelExecutionDefinition(agent("agent", Set.of()), "blocking"),
                new TavallAIModelJob("job-timeout", "wait", 0, Map.of()),
                new TavallAIModelExecutionBudget(Duration.ofMillis(75), 0, 0)
        );
        assertEquals(TavallAIModelExecutionEngine.EXECUTION_TIMEOUT,
                timeout.output().path("errorCode").asText());

        TavallAIModelExecutionEngine missingEngine = new TavallAIModelExecutionEngine(
                catalog,
                (root, definition, job) -> new AIFunctionCatalogView(root, ignored -> false),
                List.of()
        );
        TavallAIModelExecutionResult missing = missingEngine.execute(
                new TavallAIModelExecutionDefinition(agent("agent", Set.of()), "missing"),
                new TavallAIModelJob("job-missing", "work", 0, Map.of()),
                new TavallAIModelExecutionBudget(Duration.ofMinutes(1), 0, 0)
        );
        assertEquals(TavallAIModelExecutionEngine.PROVIDER_NOT_FOUND,
                missing.output().path("errorCode").asText());
    }

    private static TavallAgent agent(String id, Set<String> functions) {
        return new TavallAgent(
                id,
                "Test agent",
                TavallAgentKind.WORK,
                "Perform the assigned work.",
                functions,
                Set.of(),
                Set.of(TavallAgentCapability.FUNCTION_DISCOVERY),
                false,
                false
        );
    }

    private static TavallAIModelProvider provider(
            String id,
            AtomicBoolean called,
            ProviderBody body
    ) {
        return new TavallAIModelProvider() {
            public String providerId() { return id; }
            public TavallAIModelExecutionResult execute(TavallAIModelExecutionRequest request) throws Exception {
                called.set(true);
                return body.execute(request);
            }
        };
    }

    private static TavallAIModelExecutionResult completed() {
        return new TavallAIModelExecutionResult(
                TavallAIModelExecutionStatus.COMPLETED,
                JsonNodeFactory.instance.objectNode(),
                0,
                0,
                ""
        );
    }

    @FunctionalInterface
    private interface ProviderBody {
        TavallAIModelExecutionResult execute(TavallAIModelExecutionRequest request) throws Exception;
    }

    private static final class TestFunctions {
        private boolean dangerousWriteInvoked;
        @AIFunction(name = "safe_read", description = "Safe read") String safeRead() { return "ok"; }
        @AIFunction(name = "dangerous_write", description = "Dangerous write") String dangerousWrite() {
            dangerousWriteInvoked = true;
            return "oops";
        }
    }

    private static final class CountingFunctions {
        private final AtomicInteger invocations = new AtomicInteger();
        @AIFunction(name = "counted_write", description = "One mutation") int write() {
            return invocations.incrementAndGet();
        }
    }

    private static final class SameNameFunction {
        @AIFunction(name = "same_name", description = "Same name") String run() { return "ok"; }
    }
}
