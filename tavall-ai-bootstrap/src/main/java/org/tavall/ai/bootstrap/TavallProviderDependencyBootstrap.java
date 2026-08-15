package org.tavall.ai.bootstrap;

import org.tavall.dependency.injection.helpers.DependencyInjectorHelper;
import org.tavall.dependency.maps.DependencyMap;
import org.tavall.dependency.maps.interfaces.IDependencyMap;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Resolves indexed Tavall provider concretes from the Tavall DI lifecycle. */
public final class TavallProviderDependencyBootstrap {
    private TavallProviderDependencyBootstrap() {
    }

    public static <T> List<T> resolve(
            ClassLoader classLoader,
            List<Class<? extends T>> providerTypes
    ) {
        Objects.requireNonNull(classLoader, "classLoader");
        Objects.requireNonNull(providerTypes, "providerTypes");

        Set<String> providerPackages = new LinkedHashSet<>();
        providerTypes.forEach(providerType -> providerPackages.add(providerType.getPackageName()));
        for (String providerPackage : providerPackages) {
            DependencyInjectorHelper<Object, Object> helper = new DependencyInjectorHelper<>();
            helper.setBasePackage(providerPackage);
            helper.setupDISystem(classLoader);
        }

        IDependencyMap dependencies = DependencyMap.getDependencyMap();
        return providerTypes.stream()
                .map(dependencies::getInstance)
                .map(provider -> (T) provider)
                .toList();
    }
}
