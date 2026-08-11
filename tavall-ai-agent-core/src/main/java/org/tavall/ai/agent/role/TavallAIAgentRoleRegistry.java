package org.tavall.ai.agent.role;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

/** Immutable registry of Tavall AI roles discovered from independently deployable modules. */
public final class TavallAIAgentRoleRegistry {
    private final Map<String, TavallAIAgentRole> rolesById;

    public TavallAIAgentRoleRegistry(Iterable<? extends TavallAIAgentRoleProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        Map<String, TavallAIAgentRole> discovered = new LinkedHashMap<>();
        for (TavallAIAgentRoleProvider provider : providers) {
            TavallAIAgentRoleProvider safeProvider = Objects.requireNonNull(provider, "provider");
            TavallAIAgentRole role = Objects.requireNonNull(safeProvider.role(), "provider role");
            TavallAIAgentRole previous = discovered.putIfAbsent(role.id(), role);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate Tavall AI role id: " + role.id());
            }
        }
        rolesById = Collections.unmodifiableMap(discovered);
    }

    public static TavallAIAgentRoleRegistry load() {
        return load(Thread.currentThread().getContextClassLoader());
    }

    public static TavallAIAgentRoleRegistry load(ClassLoader classLoader) {
        ClassLoader safeClassLoader = Objects.requireNonNull(classLoader, "classLoader");
        List<TavallAIAgentRoleProvider> providers = new ArrayList<>();
        ServiceLoader.load(TavallAIAgentRoleProvider.class, safeClassLoader).forEach(providers::add);
        return new TavallAIAgentRoleRegistry(providers);
    }

    public Optional<TavallAIAgentRole> find(String roleId) {
        if (roleId == null || roleId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rolesById.get(roleId.trim()));
    }

    public TavallAIAgentRole require(String roleId) {
        return find(roleId).orElseThrow(
                () -> new IllegalArgumentException("Unknown Tavall AI role: " + roleId)
        );
    }

    public Collection<TavallAIAgentRole> roles() {
        return List.copyOf(rolesById.values());
    }

    public int size() {
        return rolesById.size();
    }
}
