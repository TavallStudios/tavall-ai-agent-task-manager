package org.tavall.ai.bootstrap;

import org.tavall.registry.AbstractRegistry;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Read-only Tavall Registry-backed module graph used by Tavall AI runtimes. */
public final class TavallAIModuleRegistry {
    private final ModuleCatalog catalog = new ModuleCatalog();

    private TavallAIModuleRegistry() {
    }

    public static TavallAIModuleRegistry load(ClassLoader classLoader) {
        ClassLoader safeClassLoader = Objects.requireNonNull(classLoader, "classLoader");
        List<Class<? extends TavallAIModuleProvider>> providerTypes = TavallProviderIndex.load(
                safeClassLoader,
                TavallProviderIndex.RUNTIME_MODULE_PROVIDER_RESOURCE,
                TavallAIModuleProvider.class
        );
        List<TavallAIModuleProvider> providers = TavallProviderDependencyBootstrap.resolve(
                safeClassLoader,
                providerTypes
        );
        return of(providers);
    }

    public static TavallAIModuleRegistry of(Iterable<? extends TavallAIModuleProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        TavallAIModuleRegistry registry = new TavallAIModuleRegistry();
        for (TavallAIModuleProvider provider : providers) {
            TavallAIModule module = Objects.requireNonNull(
                    Objects.requireNonNull(provider, "provider").module(),
                    "module"
            );
            if (registry.catalog.putIfAbsent(module.id(), module) != null) {
                throw new IllegalArgumentException("Duplicate Tavall AI module id: " + module.id());
            }
        }
        validateDependencies(registry);
        return registry;
    }

    public Collection<TavallAIModule> modules() {
        return catalog.values().stream()
                .sorted(Comparator.comparing(TavallAIModule::id))
                .toList();
    }

    public TavallAIModule require(String id) {
        TavallAIModule module = catalog.get(id);
        if (module == null) {
            throw new IllegalArgumentException("Unknown Tavall AI module: " + id);
        }
        return module;
    }

    public int size() {
        return catalog.size();
    }

    private static void validateDependencies(TavallAIModuleRegistry modules) {
        for (TavallAIModule module : modules.catalog.values()) {
            for (String requiredModuleId : module.requiredModuleIds()) {
                if (!modules.catalog.containsKey(requiredModuleId)) {
                    throw new IllegalStateException(
                            "Tavall AI module " + module.id() + " requires missing module " + requiredModuleId
                    );
                }
            }
        }
    }

    private static final class ModuleCatalog extends AbstractRegistry<String, TavallAIModule> {
    }
}
