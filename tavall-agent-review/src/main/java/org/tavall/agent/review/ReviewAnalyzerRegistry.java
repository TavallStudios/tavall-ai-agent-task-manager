package org.tavall.agent.review;

import org.tavall.internal.utils.reflection.ReflectUtil;
import org.tavall.registry.AbstractRegistry;

import java.lang.reflect.InvocationTargetException;

/** Tavall registry-backed analyzer catalog with optional configured-class discovery. */
public final class ReviewAnalyzerRegistry extends AbstractRegistry<String, ReviewAnalyzer> {
    public ReviewAnalyzerRegistry register(ReviewAnalyzer analyzer) {
        if (analyzer == null) throw new IllegalArgumentException("analyzer cannot be null");
        createRegistry(analyzer.id(), analyzer);
        return this;
    }

    public ReviewAnalyzerRegistry registerConfigured(String className) {
        Class<?> candidate = ReflectUtil.getClass(className);
        if (candidate == null || !ReviewAnalyzer.class.isAssignableFrom(candidate)) {
            throw new IllegalArgumentException("Configured review analyzer does not implement ReviewAnalyzer: " + className);
        }
        try {
            ReviewAnalyzer analyzer = (ReviewAnalyzer) candidate.getDeclaredConstructor().newInstance();
            return register(analyzer);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalArgumentException("Configured review analyzer must expose an accessible no-arg constructor: " + className, exception);
        }
    }
}
