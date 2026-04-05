package com.agenttaskmanager.app.harness.cleanjava.symbol;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import spoon.reflect.code.CtComment;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtModifiable;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

public class JavaClassProfileFactory {

  public JavaClassProfile create(CtType<?> type, Path repoRoot, Set<String> projectTypeNames) {
    String sourcePath = sourcePath(type, repoRoot);
    return new JavaClassProfile(
        type.getQualifiedName(),
        type.getSimpleName(),
        type.getPackage() == null ? "" : type.getPackage().getQualifiedName(),
        sourcePath,
        modifiers(type),
        annotations(type.getAnnotations()),
        qualifiedName(type.getSuperclass()),
        sorted(type.getSuperInterfaces().stream().map(this::qualifiedName).toList()),
        imports(type),
        fields(type),
        methods(type),
        sorted(type.getNestedTypes().stream().map(CtType::getQualifiedName).toList()),
        referencedTypes(type, projectTypeNames),
        comments(type.getComments())
    );
  }

  private List<JavaFieldProfile> fields(CtType<?> type) {
    return type.getFields().stream()
        .filter(field -> field.getPosition() != null && field.getPosition().isValidPosition())
        .map(this::field)
        .sorted(Comparator.comparing(JavaFieldProfile::name))
        .toList();
  }

  private List<JavaMethodProfile> methods(CtType<?> type) {
    TreeSet<JavaMethodProfile> profiles = new TreeSet<>(Comparator
        .comparing(JavaMethodProfile::constructor)
        .thenComparing(JavaMethodProfile::name)
        .thenComparing(profile -> String.join(",", profile.parameterTypes())));
    if (type instanceof CtClass<?> ctClass) {
      ctClass.getConstructors().stream()
          .filter(constructor -> constructor.getPosition() != null && constructor.getPosition().isValidPosition())
          .map(this::constructor)
          .forEach(profiles::add);
    }
    type.getMethods().stream()
        .filter(method -> method.getPosition() != null && method.getPosition().isValidPosition())
        .map(this::method)
        .forEach(profiles::add);
    return List.copyOf(profiles);
  }

  private JavaFieldProfile field(CtField<?> field) {
    return new JavaFieldProfile(
        field.getSimpleName(),
        qualifiedName(field.getType()),
        modifiers(field),
        annotations(field.getAnnotations()),
        firstComment(field.getComments())
    );
  }

  private JavaMethodProfile constructor(CtConstructor<?> constructor) {
    return new JavaMethodProfile(
        constructor.getSimpleName(),
        "",
        constructor.getParameters().stream().map(parameter -> qualifiedName(parameter.getType())).toList(),
        constructor.getParameters().stream().map(parameter -> parameter.getSimpleName()).toList(),
        modifiers(constructor),
        annotations(constructor.getAnnotations()),
        sorted(constructor.getThrownTypes().stream().map(this::qualifiedName).toList()),
        locals(constructor),
        firstComment(constructor.getComments()),
        true
    );
  }

  private JavaMethodProfile method(CtMethod<?> method) {
    return new JavaMethodProfile(
        method.getSimpleName(),
        qualifiedName(method.getType()),
        method.getParameters().stream().map(parameter -> qualifiedName(parameter.getType())).toList(),
        method.getParameters().stream().map(parameter -> parameter.getSimpleName()).toList(),
        modifiers(method),
        annotations(method.getAnnotations()),
        sorted(method.getThrownTypes().stream().map(this::qualifiedName).toList()),
        locals(method),
        firstComment(method.getComments()),
        false
    );
  }

  private List<JavaLocalVariableProfile> locals(spoon.reflect.declaration.CtExecutable<?> executable) {
    return executable.getElements(element -> element instanceof CtLocalVariable<?>).stream()
        .map(element -> (CtLocalVariable<?>) element)
        .map(local -> new JavaLocalVariableProfile(
            local.getSimpleName(),
            qualifiedName(local.getType()),
            local.getClass().getSimpleName()
        ))
        .sorted(Comparator.comparing(JavaLocalVariableProfile::name))
        .toList();
  }

  private List<String> referencedTypes(CtType<?> type, Set<String> projectTypeNames) {
    return type.getReferencedTypes().stream()
        .map(this::qualifiedName)
        .filter(projectTypeNames::contains)
        .filter(reference -> !reference.equals(type.getQualifiedName()))
        .distinct()
        .sorted()
        .toList();
  }

  private List<String> imports(CtType<?> type) {
    if (type.getPosition() == null || type.getPosition().getCompilationUnit() == null) {
      return List.of();
    }
    return type.getPosition().getCompilationUnit().getImports().stream()
        .map(importReference -> importReference.getReference() == null ? "" : importReference.getReference().toString())
        .filter(value -> !value.isBlank())
        .distinct()
        .sorted()
        .toList();
  }

  private List<String> comments(List<CtComment> comments) {
    return comments.stream()
        .map(CtComment::getContent)
        .map(this::normalize)
        .filter(comment -> !comment.isBlank())
        .distinct()
        .toList();
  }

  private String firstComment(List<CtComment> comments) {
    return comments(comments).stream().findFirst().orElse("");
  }

  private List<String> modifiers(CtModifiable modifiable) {
    return modifiable.getModifiers().stream()
        .map(modifierKind -> modifierKind.name().toLowerCase())
        .sorted()
        .toList();
  }

  private List<String> annotations(List<CtAnnotation<?>> annotations) {
    return annotations.stream()
        .map(annotation -> qualifiedName(annotation.getAnnotationType()))
        .filter(value -> !value.isBlank())
        .distinct()
        .sorted()
        .toList();
  }

  private String sourcePath(CtType<?> type, Path repoRoot) {
    if (type.getPosition() == null || type.getPosition().getFile() == null) {
      return "";
    }
    Path sourcePath = type.getPosition().getFile().toPath().toAbsolutePath().normalize();
    return repoRoot.toAbsolutePath().normalize().relativize(sourcePath).toString().replace('\\', '/');
  }

  private String qualifiedName(CtTypeReference<?> reference) {
    if (reference == null) {
      return "";
    }
    String qualifiedName = reference.getQualifiedName();
    return qualifiedName == null ? "" : qualifiedName.strip();
  }

  private List<String> sorted(List<String> values) {
    return values.stream()
        .map(this::normalize)
        .filter(value -> !value.isBlank())
        .collect(java.util.stream.Collectors.toCollection(TreeSet::new))
        .stream()
        .toList();
  }

  private String normalize(String value) {
    return value == null ? "" : value.replaceAll("\\s+", " ").strip();
  }
}
