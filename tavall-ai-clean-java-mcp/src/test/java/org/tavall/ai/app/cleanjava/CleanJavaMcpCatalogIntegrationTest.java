package org.tavall.ai.app.cleanjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.tavall.ai.app.mcp.McpCatalog;
import org.tavall.ai.app.mcp.cleanjava.CleanJavaMcpTools;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(
    classes = CleanJavaMcpApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
    "spring.sql.init.mode=always",
    "logging.level.root=INFO",
    "logging.level.org.springframework=WARN",
    "logging.level.org.tavall.ai=INFO",
    "app.bridge.enabled=false",
    "app.orchestration.autonomy-enabled=false",
    "app.mcp.tool-groups=clean-java-mcp",
    "app.orchestration.worker-command=/bin/true",
    "app.repo-catalog.roots=/srv,${java.io.tmpdir}",
    "app.mongodb.database=agent_task_manager_clean_java_mcp_test",
    "app.qdrant.collection=agent_task_manager_clean_java_mcp_legacy_test",
    "app.embedding.provider-order=hash",
    "app.embedding.dimensions=32",
    "app.knowledge-index.enabled=false"
})
class CleanJavaMcpCatalogIntegrationTest {

  @Autowired
  private McpCatalog mcpCatalog;

  @Test
  void shouldExposeOnlyStandaloneCleanJavaMcpToolsWhenGroupIsActive() {
    Set<String> toolNames = mcpCatalog.toolSpecifications().stream()
        .map(specification -> specification.tool().name())
        .collect(Collectors.toSet());

    assertEquals(
        Set.of(
            "loadCleanJavaRules",
            "loadCleanJavaMcpTaskContext",
            "runCleanJavaArchUnit",
            "runCleanJavaSpoon",
            "validateCleanJavaPatchScope"
        ),
        toolNames
    );
  }

  @Test
  void shouldResolveRulesWhenSearchStartsFromModuleTargetDirectory() throws Exception {
    Path moduleTarget = Path.of("target").toAbsolutePath().normalize();
    Path resolved = CleanJavaMcpTools.resolveDocPath(
        java.util.List.of(Path.of("/tmp/not-the-repo"), moduleTarget),
        "RULES.md"
    );

    assertTrue(Files.isSameFile(resolved, moduleTarget.getParent().getParent().resolve("RULES.md")));
  }
}

