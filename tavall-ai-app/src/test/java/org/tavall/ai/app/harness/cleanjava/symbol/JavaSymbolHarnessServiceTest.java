package org.tavall.ai.app.harness.cleanjava.symbol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cache.CacheDomain;
import cache.CacheSource;
import cache.CacheType;
import cache.JavaContractDeltaCache;
import cache.JavaSymbolNeighborhoodCache;
import cache.JavaSymbolProfileCache;
import org.tavall.ai.app.persistence.mongo.JavaSymbolDocumentStore;
import org.tavall.ai.app.orchestration.SharedTaskContextService;
import org.tavall.ai.app.retrieval.SemanticSyncService;
import org.tavall.ai.app.support.IntegrationTestSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

class JavaSymbolHarnessServiceTest extends IntegrationTestSupport {

  private static final String FIXTURE_PATH = "src/main/java/example/FixtureApp.java";

  @Autowired
  private JavaContractDeltaCache contractDeltaCache;

  @Autowired
  private JavaSymbolDocumentStore javaSymbolDocumentStore;

  @Autowired
  private JavaSymbolHarnessService javaSymbolHarnessService;

  @Autowired
  private JavaSymbolNeighborhoodCache neighborhoodCache;

  @Autowired
  private JavaSymbolProfileCache profileCache;

  @Autowired
  private SemanticSyncService semanticSyncService;

  @Autowired
  private SharedTaskContextService sharedTaskContextService;

  @Test
  void shouldSkipNonJavaRepo(@TempDir Path tempDir) throws Exception {
    Path repoPath = Files.createDirectories(tempDir.resolve("non-java-repo"));
    Files.writeString(repoPath.resolve("README.md"), "# no java\n", StandardCharsets.UTF_8);

    JavaSymbolBaseline baseline = javaSymbolHarnessService.captureBaseline(
        "non-java",
        "",
        "",
        "project",
        repoPath,
        "inspect repo",
        "base",
        List.of(),
        List.of()
    );

    assertEquals("skipped-no-java", baseline.status());
    assertFalse(baseline.javaRepository());
    assertTrue(javaSymbolHarnessService.buildRunContext(baseline).promptSection().contains("No deterministic Java symbol context"));
  }

  @Test
  void shouldCaptureChangedFileNeighborsDeterministicallyAndCache(@TempDir Path tempDir) throws Exception {
    Path repoPath = initializeCappedJavaRepo(tempDir.resolve("neighbor-repo"));

    JavaSymbolBaseline baseline = javaSymbolHarnessService.captureBaseline(
        "neighbors",
        "",
        "",
        "project",
        repoPath,
        "Refactor Anchor without changing behavior",
        "base",
        List.of("src/main/java/example/Anchor.java"),
        List.of("src/main/java/example/Anchor.java")
    );

    assertTrue(baseline.javaRepository());
    assertEquals(20, baseline.neighborhood().orderedProfiles().size());
    assertTrue(baseline.neighborhood().targetClassNames().contains("example.Anchor"));
    assertTrue(baseline.neighborhood().targetClassNames().stream().anyMatch(name -> name.startsWith("example.helpers.Helper")));
    assertTrue(baseline.warnings().stream().anyMatch(warning -> warning.contains("capped at 20 classes")));
    assertNotNull(neighborhoodCache.getIfPresent(
        "neighbors:baseline",
        CacheDomain.JAVA,
        CacheType.JAVA_SYMBOL_NEIGHBORHOOD,
        CacheSource.MEMORY
    ));
    assertNotNull(profileCache.getIfPresent(
        "neighbors:baseline:example.Anchor",
        CacheDomain.JAVA,
        CacheType.JAVA_SYMBOL_PROFILE,
        CacheSource.MEMORY
    ));
  }

  @Test
  void shouldIgnoreLocalVariableOnlyRefactor(@TempDir Path tempDir) throws Exception {
    Path repoPath = initializeSimpleJavaRepo(tempDir.resolve("local-variable-repo"), baselineFixtureSource());
    JavaSymbolBaseline baseline = captureBaseline("local-variable", repoPath);
    Files.writeString(repoPath.resolve(FIXTURE_PATH), localVariableRefactorSource(), StandardCharsets.UTF_8);

    JavaSymbolPostEditResult postEdit = javaSymbolHarnessService.capturePostEdit(
        "local-variable",
        "",
        "",
        "project",
        repoPath,
        baseline,
        List.of(FIXTURE_PATH)
    );

    assertEquals("passed", postEdit.contractDeltaReport().status());
    assertFalse(postEdit.contractDeltaReport().risky());
    assertNotNull(contractDeltaCache.getIfPresent(
        "local-variable",
        CacheDomain.JAVA,
        CacheType.JAVA_CONTRACT_DELTA,
        CacheSource.MEMORY
    ));
  }

  @Test
  void shouldFlagSignatureChangeAsRisky(@TempDir Path tempDir) throws Exception {
    Path repoPath = initializeSimpleJavaRepo(tempDir.resolve("signature-repo"), baselineFixtureSource());
    JavaSymbolBaseline baseline = captureBaseline("signature-change", repoPath);
    Files.writeString(repoPath.resolve(FIXTURE_PATH), signatureChangeSource(), StandardCharsets.UTF_8);

    JavaSymbolPostEditResult postEdit = javaSymbolHarnessService.capturePostEdit(
        "signature-change",
        "",
        "",
        "project",
        repoPath,
        baseline,
        List.of(FIXTURE_PATH)
    );

    assertEquals("failed", postEdit.contractDeltaReport().status());
    assertTrue(postEdit.contractDeltaReport().risky());
    assertTrue(postEdit.contractDeltaReport().changes().stream().anyMatch(change -> "method-removed".equals(change.kind())));
  }

  @Test
  void shouldRecordDegradedSourceOnlyWhenCompileFails(@TempDir Path tempDir) throws Exception {
    Path repoPath = initializeSimpleJavaRepo(tempDir.resolve("compile-failure-repo"), baselineFixtureSource());
    Files.writeString(repoPath.resolve("pom.xml"), """
        <project xmlns="http://maven.apache.org/POM/4.0.0"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>
          <groupId>example</groupId>
          <artifactId>compile-failure-repo</artifactId>
          <version>1.0.0</version>
        </project>
        """, StandardCharsets.UTF_8);
    writeFailingMavenWrappers(repoPath);
    JavaSymbolBaseline baseline = captureBaseline("compile-failure", repoPath);
    Files.writeString(repoPath.resolve(FIXTURE_PATH), localVariableRefactorSource(), StandardCharsets.UTF_8);

    JavaSymbolPostEditResult postEdit = javaSymbolHarnessService.capturePostEdit(
        "compile-failure",
        "",
        "",
        "project",
        repoPath,
        baseline,
        List.of(FIXTURE_PATH)
    );

    assertEquals("degraded-source-only", postEdit.status(), String.join(" | ", postEdit.warnings()));
    assertEquals("passed", postEdit.contractDeltaReport().status());
    assertTrue(postEdit.warnings().stream().anyMatch(warning -> warning.contains("Post-edit compile failed")));
  }

  @Test
  void shouldUseMongoFallbackWithoutSkippingPipeline(@TempDir Path tempDir) throws Exception {
    Path repoPath = initializeSimpleJavaRepo(tempDir.resolve("mongo-fallback-repo"), baselineFixtureSource());
    JavaSymbolBaseline baseline = captureBaseline("mongo-fallback", repoPath);
    Files.writeString(repoPath.resolve(FIXTURE_PATH), localVariableRefactorSource(), StandardCharsets.UTF_8);

    JavaSymbolPostEditResult postEdit = javaSymbolHarnessService.capturePostEdit(
        "mongo-fallback",
        "",
        "",
        "project",
        repoPath,
        baseline,
        List.of(FIXTURE_PATH)
    );

    assertTrue(javaSymbolDocumentStore.localFallbackEnabled());
    assertTrue(baseline.javaRepository());
    assertEquals("passed", postEdit.contractDeltaReport().status());
  }

  @Test
  void shouldQueueJavaSymbolProfilesForSemanticSearch(@TempDir Path tempDir) throws Exception {
    Path repoPath = initializeSimpleJavaRepo(tempDir.resolve("semantic-queue-repo"), baselineFixtureSource());

    JavaSymbolBaseline baseline = javaSymbolHarnessService.captureBaseline(
        "semantic-queue",
        "",
        "",
        "project",
        repoPath,
        "Update FixtureApp greet method safely",
        "base",
        List.of(FIXTURE_PATH),
        List.of(FIXTURE_PATH)
    );
    List<org.tavall.ai.app.model.orchestration.RetrievedSemanticContext> before = sharedTaskContextService.searchProjectRelatedContexts(
        "project",
        "FixtureApp greet method java contract",
        5
    );
    int processed = semanticSyncService.processPendingOperations();
    List<org.tavall.ai.app.model.orchestration.RetrievedSemanticContext> after = sharedTaskContextService.searchProjectRelatedContexts(
        "project",
        "FixtureApp greet method java contract",
        5
    );

    assertTrue(baseline.javaRepository());
    assertTrue(before.isEmpty());
    assertTrue(processed >= 1);
    assertTrue(Boolean.parseBoolean(String.valueOf(after.getFirst().payload().get("javaSymbol"))));
    assertEquals("example.FixtureApp", after.getFirst().payload().get("className"));
  }

  private JavaSymbolBaseline captureBaseline(String correlationId, Path repoPath) {
    return javaSymbolHarnessService.captureBaseline(
        correlationId,
        "",
        "",
        "project",
        repoPath,
        "Update FixtureApp safely",
        "base",
        List.of(FIXTURE_PATH),
        List.of(FIXTURE_PATH)
    );
  }

  private Path initializeSimpleJavaRepo(Path repoPath, String fixtureSource) throws Exception {
    Files.createDirectories(repoPath.resolve("src/main/java/example"));
    Files.writeString(repoPath.resolve(FIXTURE_PATH), fixtureSource, StandardCharsets.UTF_8);
    return repoPath;
  }

  private Path initializeCappedJavaRepo(Path repoPath) throws Exception {
    Files.createDirectories(repoPath.resolve("src/main/java/example/helpers"));
    StringBuilder anchor = new StringBuilder("package example;\n\n");
    for (int index = 1; index <= 22; index++) {
      anchor.append("import example.helpers.Helper").append(String.format("%02d", index)).append(";\n");
    }
    anchor.append("\npublic class Anchor {\n");
    for (int index = 1; index <= 22; index++) {
      String suffix = String.format("%02d", index);
      anchor.append("  private final Helper").append(suffix).append(" helper").append(suffix).append(" = new Helper").append(suffix).append("();\n");
    }
    anchor.append("\n  public String greet(String name) {\n    return \"hello \" + name;\n  }\n}\n");
    Files.writeString(repoPath.resolve("src/main/java/example/Anchor.java"), anchor.toString(), StandardCharsets.UTF_8);
    for (int index = 1; index <= 22; index++) {
      String suffix = String.format("%02d", index);
      Files.writeString(
          repoPath.resolve("src/main/java/example/helpers/Helper" + suffix + ".java"),
          """
          package example.helpers;

          public class Helper%s {
          }
          """.formatted(suffix),
          StandardCharsets.UTF_8
      );
    }
    return repoPath;
  }

  private void writeFailingMavenWrappers(Path repoPath) throws IOException {
    Files.writeString(repoPath.resolve("mvnw.cmd"), "@echo off\r\necho compile failed\r\nexit /b 1\r\n", StandardCharsets.UTF_8);
    Files.writeString(repoPath.resolve("mvnw"), "#!/usr/bin/env bash\necho compile failed\nexit 1\n", StandardCharsets.UTF_8);
    try {
      Files.setPosixFilePermissions(repoPath.resolve("mvnw"), EnumSet.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE
      ));
    } catch (UnsupportedOperationException ignored) {
    }
  }

  private String baselineFixtureSource() {
    return """
        package example;

        public class FixtureApp {

          public String greet(String name) {
            String message = "hello " + name;
            return message;
          }
        }
        """;
  }

  private String localVariableRefactorSource() {
    return """
        package example;

        public class FixtureApp {

          public String greet(String name) {
            String salutation = "hello " + name;
            return salutation;
          }
        }
        """;
  }

  private String signatureChangeSource() {
    return """
        package example;

        public class FixtureApp {

          public int greet() {
            return 42;
          }
        }
        """;
  }
}

