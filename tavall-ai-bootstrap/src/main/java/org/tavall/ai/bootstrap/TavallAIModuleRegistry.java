package org.tavall.ai.bootstrap;

import org.tavall.registry.AbstractRegistry;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.ServiceLoader;

/** Tavall Registry-backed module graph used by Tavall AI runtimes. */
public final class TavallAIModuleRegistry extends AbstractRegistry<String, TavallAIModule> {
    private TavallAIModuleRegistry() {
    }

    /** Transitional discovery seam while provider construction moves to Tavall DI. */
    public static TavallAIModuleRegistry load(ClassLoader classLoader) {
        ServiceLoader<TavallAIModuleProvider> loader = ServiceLoader.load(
                TavallAIModuleProvider.class,
                Objects.requireNonNull(classLoader, "classLoader")
        );
        return of(loader);
    }

    public static TavallAIModuleRegistry of(Iterable<? extends TavallAIModuleProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        TavallAIModuleRegistry registry = new TavallAIModuleRegistry();
        for (TavallAIModuleProvider provider : providers) {
            TavallAIModule module = Objects.requireNonNull(
                    Objects.requireNonNull(provider, "provider").module(),
                    "module"
            );
            if (registry.putIfAbsent(module.id(), module) != null) {
                throw new IllegalArgumentException("Duplicate Tavall AI module id: " + module.id());
            }
        }
        validateDependencies(registry);
        return registry;
    }

    public Collection<TavallAIModule> modules() {
        return values().stream()
                .sorted(Comparator.comparing(TavallAIModule::id))
                .toList();
    }

    public TavallAIModule require(String id) {
        TavallAIModule module = get(id);
        if (module == null) {
            throw new IllegalArgumentException("Unknown Tavall AI module: " + id);
        }
        return module;
    }

    private static void validateDependencies(TavallAIModuleRegistry modules) {
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
