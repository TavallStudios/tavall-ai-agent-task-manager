package org.tavall.ai.app.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.tavall.ai.app.harness.tools.SharedRepoSnapshotService;
import org.tavall.ai.app.support.IntegrationTestSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

class SharedRepoSnapshotServiceTest extends IntegrationTestSupport {

  @Autowired
  private SharedRepoSnapshotService sharedRepoSnapshotService;

  @Test
  void shouldRoundTripRepoSnapshotArchive(@TempDir Path tempDir) throws Exception {
    Path repoPath = tempDir.resolve("fixture-repo");
    Files.createDirectories(repoPath.resolve(".git"));
    Files.createDirectories(repoPath.resolve("src/main/java/example"));
    Files.writeString(repoPath.resolve(".git/config"), "[core]\n\trepositoryformatversion = 0\n", StandardCharsets.UTF_8);
    Files.writeString(
        repoPath.resolve("src/main/java/example/FixtureApp.java"),
        "package example;\nclass FixtureApp {}\n",
        StandardCharsets.UTF_8
    );

    String archiveBase64 = sharedRepoSnapshotService.createArchiveBase64(repoPath);
    Path stagedRepoPath = sharedRepoSnapshotService.stageArchive("fixture-repo", archiveBase64);

    assertTrue(Files.isDirectory(stagedRepoPath.resolve(".git")));
    assertEquals(
        "[core]\n\trepositoryformatversion = 0\n",
        Files.readString(stagedRepoPath.resolve(".git/config"), StandardCharsets.UTF_8)
    );
    assertTrue(Files.readString(
        stagedRepoPath.resolve("src/main/java/example/FixtureApp.java"),
        StandardCharsets.UTF_8
    ).contains("FixtureApp"));
  }
}

