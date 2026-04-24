package org.tavall.ai.app.harness.cleanjava.symbol;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class JavaReflectionAugmentationService {

  public JavaReflectionAugmentationResult augment(List<JavaClassProfile> profiles, List<Path> classpathRoots) {
    if (profiles == null || profiles.isEmpty() || classpathRoots == null || classpathRoots.isEmpty()) {
      return new JavaReflectionAugmentationResult(false, List.of(), List.of());
    }
    try (URLClassLoader classLoader = new URLClassLoader(urls(classpathRoots), Thread.currentThread().getContextClassLoader())) {
      List<JavaReflectionProfile> reflectionProfiles = new ArrayList<>();
      List<String> warnings = new ArrayList<>();
      for (JavaClassProfile profile : profiles) {
        try {
          Class<?> loadedClass = Class.forName(profile.qualifiedName(), false, classLoader);
          reflectionProfiles.add(profile(loadedClass));
        } catch (ClassNotFoundException | LinkageError exception) {
          warnings.add("Reflection unavailable for " + profile.qualifiedName() + ": " + exception.getClass().getSimpleName());
        }
      }
      return new JavaReflectionAugmentationResult(!reflectionProfiles.isEmpty(), List.copyOf(reflectionProfiles), List.copyOf(warnings));
    } catch (Exception exception) {
      return new JavaReflectionAugmentationResult(false, List.of(), List.of("Reflection class loader failed: " + exception.getMessage()));
    }
  }

  private JavaReflectionProfile profile(Class<?> loadedClass) {
    return new JavaReflectionProfile(
        loadedClass.getName(),
        annotations(loadedClass.getDeclaredAnnotations()),
        declaredFields(loadedClass.getDeclaredFields()),
        declaredConstructors(loadedClass.getDeclaredConstructors()),
        declaredMethods(loadedClass.getDeclaredMethods()),
        loadedClass.getSuperclass() == null ? "" : loadedClass.getSuperclass().getName(),
        java.util.Arrays.stream(loadedClass.getInterfaces()).map(Class::getName).sorted().toList()
    );
  }

  private URL[] urls(List<Path> classpathRoots) throws MalformedURLException {
    List<URL> urls = new ArrayList<>();
    for (Path classpathRoot : classpathRoots) {
      urls.add(classpathRoot.toUri().toURL());
    }
    return urls.toArray(URL[]::new);
  }

  private List<String> annotations(java.lang.annotation.Annotation[] annotations) {
    return java.util.Arrays.stream(annotations)
        .map(annotation -> annotation.annotationType().getName())
        .sorted()
        .toList();
  }

  private List<String> declaredFields(Field[] fields) {
    return java.util.Arrays.stream(fields)
        .map(field -> String.join("|",
            field.getName(),
            field.getType().getName(),
            java.lang.reflect.Modifier.toString(field.getModifiers()).toLowerCase(Locale.ROOT)))
        .sorted()
        .toList();
  }

  private List<String> declaredConstructors(Constructor<?>[] constructors) {
    return java.util.Arrays.stream(constructors)
        .map(constructor -> String.join("|",
            loadedName(constructor.getDeclaringClass()),
            parameterList(constructor.getParameterTypes()),
            java.lang.reflect.Modifier.toString(constructor.getModifiers()).toLowerCase(Locale.ROOT)))
        .sorted()
        .toList();
  }

  private List<String> declaredMethods(Method[] methods) {
    return java.util.Arrays.stream(methods)
        .map(method -> String.join("|",
            method.getName(),
            method.getReturnType().getName(),
            parameterList(method.getParameterTypes()),
            java.lang.reflect.Modifier.toString(method.getModifiers()).toLowerCase(Locale.ROOT)))
        .sorted(Comparator.naturalOrder())
        .toList();
  }

  private String parameterList(Class<?>[] parameterTypes) {
    return java.util.Arrays.stream(parameterTypes).map(this::loadedName).toList().toString();
  }

  private String loadedName(Class<?> type) {
    return type == null ? "" : type.getName();
  }
}

