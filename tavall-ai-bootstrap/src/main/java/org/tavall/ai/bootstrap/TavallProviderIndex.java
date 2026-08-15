package org.tavall.ai.bootstrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reads Tavall-owned provider membership metadata without constructing providers.
 * Provider instances remain Tavall DI-owned.
 */
public final class TavallProviderIndex {
    public static final String AGENT_PROVIDER_RESOURCE = "META-INF/tavall/agent-provider";
    public static final String RUNTIME_MODULE_PROVIDER_RESOURCE = "META-INF/tavall/runtime-module-provider";

    private TavallProviderIndex() {
    }

    public static <T> List<Class<? extends T>> load(
            ClassLoader classLoader,
            String resourceName,
            Class<T> providerType
    ) {
        Objects.requireNonNull(classLoader, "classLoader");
        Objects.requireNonNull(resourceName, "resourceName");
        Objects.requireNonNull(providerType, "providerType");

        List<Class<? extends T>> providers = new ArrayList<>();
        Set<String> seenClassNames = new LinkedHashSet<>();
        try {
            Enumeration<URL> resources = classLoader.getResources(resourceName);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        resource.openStream(),
                        StandardCharsets.UTF_8
                ))) {
                    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                        String className = line.trim();
                        if (className.isEmpty() || className.startsWith("#")) {
                            continue;
                        }
                        if (!seenClassNames.add(className)) {
                            throw new IllegalStateException(
                                    "Duplicate Tavall provider index entry for " + className + " in " + resourceName
                            );
                        }
                        providers.add(loadProviderType(classLoader, className, providerType));
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed reading Tavall provider index " + resourceName, exception);
        }
        return List.copyOf(providers);
    }

    private static <T> Class<? extends T> loadProviderType(
            ClassLoader classLoader,
            String className,
            Class<T> providerType
    ) {
        try {
            Class<?> rawType = Class.forName(className, false, classLoader);
            if (!providerType.isAssignableFrom(rawType)) {
                throw new IllegalStateException(
                        "Indexed provider " + className + " does not implement " + providerType.getName()
                );
            }
            if (rawType.isInterface() || Modifier.isAbstract(rawType.getModifiers())) {
                throw new IllegalStateException("Indexed Tavall provider must be concrete: " + className);
            }
            return rawType.asSubclass(providerType);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Indexed Tavall provider is not loadable: " + className, exception);
        }
    }
}
