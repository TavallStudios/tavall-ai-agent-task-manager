package com.agenttaskmanager.app.cleanjava;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.agenttaskmanager.app.AgentTaskManagerApplication;
import com.agenttaskmanager.app.mcp.McpCatalog;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(
    classes = AgentTaskManagerApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
    "spring.sql.init.mode=always",
    "logging.level.root=INFO",
    "logging.level.org.springframework=WARN",
    "logging.level.com.agenttaskmanager=INFO",
    "app.bridge.enabled=false",
    "app.orchestration.autonomy-enabled=false",
    "app.mcp.tool-groups=clean-java-harness",
    "app.orchestration.worker-command=/bin/true",
    "app.repo-catalog.roots=/srv,${java.io.tmpdir}",
    "app.mongodb.database=agent_task_manager_harness_test",
    "app.qdrant.collection=agent_task_manager_harness_context_test",
    "app.embedding.provider-order=hash",
    "app.embedding.dimensions=32",
    "app.knowledge-index.enabled=false"
})
class CleanJavaHarnessCatalogIntegrationTest {

  @Autowired
  private McpCatalog mcpCatalog;

  @Test
  void shouldExposeOnlyHarnessToolsWhenHarnessGroupIsActive() {
    Set<String> toolNames = mcpCatalog.toolSpecifications().stream()
        .map(specification -> specification.tool().name())
        .collect(Collectors.toSet());

    assertEquals(
        Set.of(
            "intakeHarnessTask",
            "routeHarnessTask",
            "loadHarnessState",
            "runHarnessToolBundle",
            "runHarnessApprovalGate",
            "loadCleanJavaTaskContext",
            "runCleanJavaHarness",
            "runJavaIntegrationHarness"
        ),
        toolNames
    );
  }
}
