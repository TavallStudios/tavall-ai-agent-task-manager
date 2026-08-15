package org.tavall.agent;

import org.tavall.registry.AbstractRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

/** Tavall Registry-backed catalog of Tavall agents discovered through bootstrap. */
public final class TavallAgentRegistry extends AbstractRegistry<String, TavallAgent> {
    public TavallAgentRegistry(Iterable<? extends TavallAgentProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        for (TavallAgentProvider provider : providers) {
            TavallAgentProvider safeProvider = Objects.requireNonNull(provider, "provider");
            TavallAgent agent = Objects.requireNonNull(safeProvider.agent(), "provider agent");
            TavallAgent previous = putIfAbsent(agent.id(), agent);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate Tavall agent id: " + agent.id());
            }
        }
    }

    /**
     * Transitional discovery seam while provider construction moves to Tavall DI.
     * Registry ownership is already canonical Tavall Registry; ServiceLoader must not expand beyond this seam.
     */
    public static TavallAgentRegistry load() {
        return load(Thread.currentThread().getContextClassLoader());
    }

    public static TavallAgentRegistry load(ClassLoader classLoader) {
        ClassLoader safeClassLoader = Objects.requireNonNull(classLoader, "classLoader");
        List<TavallAgentProvider> providers = new ArrayList<>();
        ServiceLoader.load(TavallAgentProvider.class, safeClassLoader).forEach(providers::add);
        return new TavallAgentRegistry(providers);
    }

    public Optional<TavallAgent> find(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(get(agentId.trim()));
    }

    public TavallAgent require(String agentId) {
        return find(agentId).orElseThrow(() -> new IllegalArgumentException("Unknown Tavall agent: " + agentId));
    }

    public Collection<TavallAgent> agents() {
        return values().stream()
                .sorted(Comparator.comparing(TavallAgent::id))
                .toList();
    }
}
