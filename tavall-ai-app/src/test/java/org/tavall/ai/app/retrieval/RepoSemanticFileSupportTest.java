package org.tavall.ai.app.retrieval;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepoSemanticFileSupportTest {

  private final RepoSemanticFileSupport support = new RepoSemanticFileSupport();

  @Test
  void excludesIdeMetadataFromSemanticSyncCandidates(@TempDir Path repoRoot) throws Exception {
    Path ideFile = repoRoot.resolve(".idea/dataSources.xml");
    Files.createDirectories(ideFile.getParent());
    Files.writeString(ideFile, "<data-source />");

    assertTrue(support.isIndexable(ideFile));
    assertFalse(support.isSyncCandidate(repoRoot, ideFile));
    assertFalse(support.isExcludedRelativePath("src/main/java/example/Example.java"));
  }
}
