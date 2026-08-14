package org.tavall.agent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

/** Immutable registry of Tavall agents discovered through bootstrap. */
public final class TavallAgentRegistry {
    private final Map<String, TavallAgent> agentsById;

    public TavallAgentRegistry(Iterable<? extends TavallAgentProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        Map<String, TavallAgent> discovered = new LinkedHashMap<>();
        for (TavallAgentProvider provider : providers) {
            TavallAgentProvider safeProvider = Objects.requireNonNull(provider, "provider");
            TavallAgent agent = Objects.requireNonNull(safeProvider.agent(), "provider agent");
            TavallAgent previous = discovered.putIfAbsent(agent.id(), agent);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate Tavall agent id: " + agent.id());
            }
        }
        agentsById = Collections.unmodifiableMap(discovered);
    }

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
        return Optional.ofNullable(agentsById.get(agentId.trim()));
    }

    public TavallAgent require(String agentId) {
        return find(agentId).orElseThrow(() -> new IllegalArgumentException("Unknown Tavall agent: " + agentId));
    }

    public Collection<TavallAgent> agents() {
        return List.copyOf(agentsById.values());
    }

    public int size() {
        return agentsById.size();
    }
}
