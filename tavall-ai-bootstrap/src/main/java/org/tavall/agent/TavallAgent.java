package org.tavall.agent;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Provider-neutral description of one Tavall agent.
 *
 * <p>An agent is behavior and execution requirements: instructions, requested Function Catalog
 * function names, and coarse policy metadata. It contains no model, AI runtime, scheduler daemon,
 * process supervisor, or infrastructure authority.</p>
 */
public record TavallAgent(
        String id,
        String description,
        TavallAgentKind kind,
        String instructions,
        Set<String> requiredFunctionNames,
        Set<String> optionalFunctionNames,
        Set<TavallAgentCapability> capabilities,
        boolean maySpawnSubagents,
        boolean mayRequestDistributedSession
) {
    public TavallAgent {
        id = requireText(id, "id");
        description = requireText(description, "description");
        kind = Objects.requireNonNull(kind, "kind");
        instructions = requireText(instructions, "instructions");
        requiredFunctionNames = copyFunctionNames(requiredFunctionNames, "requiredFunctionNames");
        optionalFunctionNames = copyFunctionNames(optionalFunctionNames, "optionalFunctionNames");
        capabilities = capabilities == null || capabilities.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(capabilities));

        Set<String> duplicateFunctionNames = new LinkedHashSet<>(requiredFunctionNames);
        duplicateFunctionNames.retainAll(optionalFunctionNames);
        if (!duplicateFunctionNames.isEmpty()) {
            throw new IllegalArgumentException(
                    "A function cannot be both required and optional: " + duplicateFunctionNames
            );
        }

        if (maySpawnSubagents && !capabilities.contains(TavallAgentCapability.SUBAGENT_ORCHESTRATION)) {
            throw new IllegalArgumentException("Subagent-capable agents must declare SUBAGENT_ORCHESTRATION");
        }
        if (mayRequestDistributedSession
                && !capabilities.contains(TavallAgentCapability.DISTRIBUTED_SCHEDULING)) {
            throw new IllegalArgumentException(
                    "Distributed-session-capable agents must declare DISTRIBUTED_SCHEDULING"
            );
        }
    }

    /** Function names used to derive the execution's restricted Function Catalog view. */
    public Set<String> requestedFunctionNames() {
        LinkedHashSet<String> requested = new LinkedHashSet<>(requiredFunctionNames);
        requested.addAll(optionalFunctionNames);
        return Collections.unmodifiableSet(requested);
    }

    private static Set<String> copyFunctionNames(Set<String> names, String fieldName) {
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        if (names != null) {
            for (String name : names) {
                copy.add(requireText(name, fieldName + " entry"));
            }
        }
        return Collections.unmodifiableSet(copy);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
