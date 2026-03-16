package com.agenttaskmanager.app.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        new HarnessToolBundleRequest("java-context", null, null, null, repoPath.toString(), "FixtureApp", 5)
    );

    assertEquals("java-context", result.bundleName());
    assertEquals(5, result.summary().get("downstreamCalls"));
    assertTrue(String.valueOf(result.sections().get("cleanJavaRules")).contains("No top-level class or interface over 300 lines."));

    @SuppressWarnings("unchecked")
    Map<String, Object> downstream = (Map<String, Object>) result.sections().get("downstream");
    assertTrue(downstream.containsKey("directory"));
    assertTrue(downstream.containsKey("gitStatus"));
    assertTrue(downstream.containsKey("gitDiff"));
    assertTrue(downstream.containsKey("search"));
    assertTrue(downstream.containsKey("javaFiles"));
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
