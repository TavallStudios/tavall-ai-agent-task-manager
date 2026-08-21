package org.tavall.agent;

import org.tavall.ai.bootstrap.TavallProviderDependencyBootstrap;
import org.tavall.ai.bootstrap.TavallProviderIndex;
import org.tavall.registry.AbstractRegistry;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Read-only Tavall Registry-backed catalog of Tavall agents. */
public final class TavallAgentRegistry {
    private final AgentCatalog catalog = new AgentCatalog();

    public TavallAgentRegistry(Iterable<? extends TavallAgentProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        for (TavallAgentProvider provider : providers) {
            TavallAgentProvider safeProvider = Objects.requireNonNull(provider, "provider");
            TavallAgent agent = Objects.requireNonNull(safeProvider.agent(), "provider agent");
            TavallAgent previous = catalog.putIfAbsent(agent.id(), agent);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate Tavall agent id: " + agent.id());
            }
        }
    }

    public static TavallAgentRegistry load() {
        return load(Thread.currentThread().getContextClassLoader());
    }

    public static TavallAgentRegistry load(ClassLoader classLoader) {
        ClassLoader safeClassLoader = Objects.requireNonNull(classLoader, "classLoader");
        List<Class<? extends TavallAgentProvider>> providerTypes = TavallProviderIndex.load(
                safeClassLoader,
                TavallProviderIndex.AGENT_PROVIDER_RESOURCE,
                TavallAgentProvider.class
        );
        List<TavallAgentProvider> providers = TavallProviderDependencyBootstrap.resolve(
                safeClassLoader,
                providerTypes
        );
        return new TavallAgentRegistry(providers);
    }

    public Optional<TavallAgent> find(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(catalog.get(agentId.trim()));
    }

    public TavallAgent require(String agentId) {
        return find(agentId).orElseThrow(() -> new IllegalArgumentException("Unknown Tavall agent: " + agentId));
    }

    public Collection<TavallAgent> agents() {
        return catalog.values().stream()
                .sorted(Comparator.comparing(TavallAgent::id))
                .toList();
    }

    public int size() {
        return catalog.size();
    }

    private static final class AgentCatalog extends AbstractRegistry<String, TavallAgent> {
    }
}
