package org.tavall.ai.bootstrap;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/** Discovers and validates the loadable module graph used by Tavall AI runtimes. */
public final class TavallAIModuleRegistry {
    private final Map<String, TavallAIModule> modules;

    private TavallAIModuleRegistry(Map<String, TavallAIModule> modules) {
        this.modules = Map.copyOf(modules);
    }

    public static TavallAIModuleRegistry load(ClassLoader classLoader) {
        ServiceLoader<TavallAIModuleProvider> loader = ServiceLoader.load(
                TavallAIModuleProvider.class,
                Objects.requireNonNull(classLoader, "classLoader")
        );
        return of(loader);
    }

    public static TavallAIModuleRegistry of(Iterable<? extends TavallAIModuleProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        LinkedHashMap<String, TavallAIModule> modules = new LinkedHashMap<>();
        for (TavallAIModuleProvider provider : providers) {
            TavallAIModule module = Objects.requireNonNull(
                    Objects.requireNonNull(provider, "provider").module(),
                    "module"
            );
            if (modules.putIfAbsent(module.id(), module) != null) {
                throw new IllegalArgumentException("Duplicate Tavall AI module id: " + module.id());
            }
        }
        validateDependencies(modules);
        return new TavallAIModuleRegistry(modules);
    }

    public int size() {
        return modules.size();
    }

    public Collection<TavallAIModule> modules() {
        return List.copyOf(modules.values());
    }

    public TavallAIModule require(String id) {
        TavallAIModule module = modules.get(id);
        if (module == null) {
            throw new IllegalArgumentException("Unknown Tavall AI module: " + id);
        }
        return module;
    }

    private static void validateDependencies(Map<String, TavallAIModule> modules) {
        for (TavallAIModule module : modules.values()) {
            for (String requiredModuleId : module.requiredModuleIds()) {
                if (!modules.containsKey(requiredModuleId)) {
                    throw new IllegalStateException(
                            "Tavall AI module " + module.id() + " requires missing module " + requiredModuleId
                    );
                }
            }
        }
    }
}
