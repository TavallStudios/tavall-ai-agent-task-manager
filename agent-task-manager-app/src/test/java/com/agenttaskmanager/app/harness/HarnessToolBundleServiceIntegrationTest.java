package com.agenttaskmanager.app.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agenttaskmanager.app.harness.cleanjava.CleanJavaTaskContext;
import com.agenttaskmanager.app.harness.tools.HarnessToolBundleRequest;
import com.agenttaskmanager.app.harness.tools.HarnessToolBundleResult;
import com.agenttaskmanager.app.support.IntegrationTestSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

class HarnessToolBundleServiceIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private com.agenttaskmanager.app.harness.tools.HarnessToolBundleService harnessToolBundleService;

  @Test
  void shouldBrokerJavaContextAcrossDownstreamRepoTools(@TempDir Path tempDir) throws Exception {
    Path repoPath = initializeFixtureRepo(tempDir.resolve("fixture-repo"));
    Files.writeString(
        repoPath.resolve("src/main/java/example/FixtureApp.java"),
        """
        package example;

        public class FixtureApp {
          public String status() {
            return "dirty";
          }
        }
        """,
        StandardCharsets.UTF_8
    );

    HarnessToolBundleResult result = harnessToolBundleService.executeBundle(
        new HarnessToolBundleRequest("language-context", null, null, "fixture-repo", repoPath.toString(), "FixtureApp", 5)
    );

    assertEquals("language-context", result.bundleName());
    assertEquals(5, result.summary().get("downstreamCalls"));
    assertTrue(String.valueOf(result.sections().get("cleanJavaRules")).contains("No top-level class or interface over 300 lines."));
    CleanJavaTaskContext cleanJavaContext = (CleanJavaTaskContext) result.sections().get("cleanJavaContext");
    assertTrue(cleanJavaContext.rules().contains("Fixture rule"));
    assertTrue(cleanJavaContext.examples().contains("Fixture example"));
    assertTrue(cleanJavaContext.architecture().contains("Fixture architecture"));
    assertEquals(1, ((Number) cleanJavaContext.packageDependencyMap().get("javaFileCount")).intValue());

    @SuppressWarnings("unchecked")
    Map<String, Object> downstream = (Map<String, Object>) result.sections().get("downstream");
    @SuppressWarnings("unchecked")
    Map<String, Object> memory = (Map<String, Object>) result.sections().get("memory");
    assertTrue(downstream.containsKey("directory"));
    assertTrue(downstream.containsKey("gitStatus"));
    assertTrue(downstream.containsKey("gitDiff"));
    assertTrue(downstream.containsKey("search"));
    assertTrue(downstream.containsKey("javaFiles"));
    assertEquals("retrieved", result.summary().get("memoryStatus"));
    assertTrue(result.summary().containsKey("qdrantHealth"));
    assertEquals("retrieved", memory.get("status"));
    assertEquals("FixtureApp", memory.get("queryText"));
    assertTrue(memory.containsKey("qdrantHealth"));
    assertTrue(result.downstreamCalls().stream().filter(call -> "completed".equals(call.status())).count() >= 4);
    assertTrue(((Number) result.summary().get("downstreamErrors")).longValue() <= 1);
    assertTrue(String.valueOf(downstream.get("search")).contains("FixtureApp"));
  }

  private Path initializeFixtureRepo(Path repoPath) throws Exception {
    Files.createDirectories(repoPath.resolve("src/main/java/example"));
    Files.writeString(
        repoPath.resolve("README.md"),
        "# Harness Bundle Fixture\n",
        StandardCharsets.UTF_8
    );
    Files.writeString(
        repoPath.resolve("RULES.md"),
        "# Rules\n\nFixture rule\n",
        StandardCharsets.UTF_8
    );
    Files.writeString(
        repoPath.resolve("EXAMPLES.md"),
        "# Examples\n\nFixture example\n",
        StandardCharsets.UTF_8
    );
    Files.writeString(
        repoPath.resolve("ARCHITECTURE.md"),
        "# Architecture\n\nFixture architecture\n",
        StandardCharsets.UTF_8
    );
    Files.writeString(
        repoPath.resolve("src/main/java/example/FixtureApp.java"),
        """
        package example;

        public class FixtureApp {
        }
        """,
        StandardCharsets.UTF_8
    );
    run(repoPath, "git", "init", "-b", "main");
    run(repoPath, "git", "config", "user.email", "integration@example.com");
    run(repoPath, "git", "config", "user.name", "Integration Test");
    run(repoPath, "git", "add", ".");
    run(repoPath, "git", "commit", "-m", "Initial fixture");
    return repoPath;
  }

  private void run(Path repoPath, String... command) throws Exception {
    Process process = new ProcessBuilder(command)
        .directory(repoPath.toFile())
        .redirectErrorStream(true)
        .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exitCode = process.waitFor();

    if (exitCode != 0) {
      throw new IOException(String.join(" ", command) + " failed: " + output);
    }
  }
}
