package org.tavall.ai.execution.model;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.tavall.ai.context.TavallAIProjectContextBundle;
import org.tavall.ai.core.catalog.AIFunctionCatalog;
import org.tavall.ai.core.catalog.AIFunctionCatalogView;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Provider-neutral single-model execution engine owned by Tavall AI.
 *
 * <p>The agent descriptor is non-AI behavior metadata. This engine is where that descriptor is
 * paired with an actual model provider, an authoritative Function Catalog view, and optional
 * bounded project context.</p>
 */
public final class TavallAIModelExecutionEngine {
    public static final String PROVIDER_NOT_FOUND = "provider_not_found";
    public static final String BUDGET_EXCEEDED = "budget_exceeded";
    public static final String PROVIDER_FAILURE = "provider_failure";
    public static final String FUNCTION_VIEW_MISMATCH = "function_view_mismatch";
    public static final String EXECUTION_TIMEOUT = "execution_timeout";

    private final AIFunctionCatalog catalog;
    private final TavallAIModelFunctionViewResolver functionViewResolver;
    private final Map<String, TavallAIModelProvider> providers;

    public TavallAIModelExecutionEngine(
            AIFunctionCatalog catalog,
            TavallAIModelFunctionViewResolver functionViewResolver,
            Iterable<? extends TavallAIModelProvider> providers
    ) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.functionViewResolver = Objects.requireNonNull(functionViewResolver, "functionViewResolver");
        Objects.requireNonNull(providers, "providers");
        Map<String, TavallAIModelProvider> byId = new LinkedHashMap<>();
        for (TavallAIModelProvider provider : providers) {
            TavallAIModelProvider safeProvider = Objects.requireNonNull(provider, "provider");
            String providerId = requireText(safeProvider.providerId(), "providerId");
            if (byId.putIfAbsent(providerId, safeProvider) != null) {
                throw new IllegalArgumentException("Duplicate Tavall AI model provider id: " + providerId);
            }
        }
        this.providers = Map.copyOf(byId);
    }

    public TavallAIModelExecutionResult execute(
            TavallAIModelExecutionDefinition definition,
            TavallAIModelJob job,
            TavallAIModelExecutionBudget budget
    ) {
        return execute(definition, job, budget, TavallAIProjectContextBundle.empty());
    }

    public TavallAIModelExecutionResult execute(
            TavallAIModelExecutionDefinition definition,
            TavallAIModelJob job,
            TavallAIModelExecutionBudget budget,
            TavallAIProjectContextBundle projectContext
    ) {
        TavallAIModelExecutionDefinition safeDefinition = Objects.requireNonNull(definition, "definition");
        TavallAIModelJob safeJob = Objects.requireNonNull(job, "job");
        TavallAIModelExecutionBudget safeBudget = Objects.requireNonNull(budget, "budget");
        TavallAIProjectContextBundle safeProjectContext = Objects.requireNonNullElseGet(
                projectContext, TavallAIProjectContextBundle::empty
        );
        if (safeJob.delegationDepth() > safeBudget.maxDelegations()) {
            return failure(BUDGET_EXCEEDED, "Job delegation depth exceeds the model execution budget.", 0);
        }

        TavallAIModelProvider provider = providers.get(safeDefinition.providerId());
        if (provider == null) {
            return failure(PROVIDER_NOT_FOUND,
                    "No Tavall AI model provider registered with id: " + safeDefinition.providerId(), 0);
        }

        AIFunctionCatalogView policyView = Objects.requireNonNull(
                functionViewResolver.resolve(catalog, safeDefinition, safeJob),
                "functionViewResolver result"
        );
        if (!policyView.isBackedBy(catalog)) {
            return failure(FUNCTION_VIEW_MISMATCH,
                    "Authoritative Function Catalog view is backed by a different catalog.", 0);
        }

        AIFunctionCatalogView effectiveView = policyView
                .narrow(function -> safeDefinition.agent().requestedFunctionNames().contains(function.getName()))
                .withInvocationLimit(safeBudget.maxToolCalls());
        TavallAIModelExecutionRequest request = new TavallAIModelExecutionRequest(
                safeDefinition, safeJob, safeBudget, effectiveView, safeProjectContext
        );

        ExecutorService executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("tavall-ai-model-provider-", 0).factory()
        );
        Future<TavallAIModelExecutionResult> execution = executor.submit(
                () -> Objects.requireNonNull(provider.execute(request), "provider result")
        );
        try {
            TavallAIModelExecutionResult providerResult = execution.get(
                    safeBudget.timeout().toNanos(), TimeUnit.NANOSECONDS
            );
            int actualToolCalls = effectiveView.invocationCount();
            if (providerResult.delegatedTasks() > safeBudget.maxDelegations()) {
                return failure(BUDGET_EXCEEDED,
                        "Provider reported delegated tasks above the model execution budget.", actualToolCalls);
            }
            return new TavallAIModelExecutionResult(
                    providerResult.status(),
                    providerResult.output(),
                    actualToolCalls,
                    providerResult.delegatedTasks(),
                    providerResult.errorMessage()
            );
        } catch (TimeoutException exception) {
            effectiveView.revoke();
            execution.cancel(true);
            return failure(EXECUTION_TIMEOUT,
                    "Tavall AI model provider exceeded the execution timeout.", effectiveView.invocationCount());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            effectiveView.revoke();
            execution.cancel(true);
            return failure(PROVIDER_FAILURE,
                    "Tavall AI model execution was interrupted.", effectiveView.invocationCount());
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            return failure(PROVIDER_FAILURE, messageFor(cause), effectiveView.invocationCount());
        } catch (ArithmeticException exception) {
            effectiveView.revoke();
            execution.cancel(true);
            return failure(BUDGET_EXCEEDED,
                    "Tavall AI model execution timeout is outside the supported nanosecond range.",
                    effectiveView.invocationCount());
        } finally {
            effectiveView.revoke();
            executor.shutdownNow();
        }
    }

    private static TavallAIModelExecutionResult failure(String errorCode, String message, int toolCalls) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("errorCode", errorCode);
        payload.put("message", message);
        return new TavallAIModelExecutionResult(TavallAIModelExecutionStatus.FAILED, payload, toolCalls, 0, message);
    }

    private static String messageFor(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static String requireText(String value, String fieldName) {
        if (value != null && !value.isBlank()) return value.trim();
        throw new IllegalArgumentException(fieldName + " must not be blank");
    }
}
