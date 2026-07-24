package org.tavall.ai.app.support;

import org.tavall.ai.app.AgentTaskManagerApplication;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(
    classes = AgentTaskManagerApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "spring.sql.init.mode=always",
    "logging.level.root=INFO",
    "logging.level.org.springframework=WARN",
    "logging.level.org.tavall.ai=INFO",
    "app.bridge.enabled=false",
    "app.memory-sync.managed-repo-backfill-enabled=false",
    "app.orchestration.autonomy-enabled=false",
    "app.orchestration.worker-model=fake-model",
    "app.security.username=test-agent",
    "app.security.password=test-password",
    "app.codex.remote-tool-execution-enabled=false",
    "app.repo-catalog.roots=/srv,${java.io.tmpdir}",
    "app.mongodb.database=agent_task_manager_test",
    "app.qdrant.collection=agent_task_manager_context_test",
    "app.embedding.provider-order=hash",
    "app.embedding.dimensions=32"
})
public abstract class IntegrationTestSupport {

  @DynamicPropertySource
  static void registerTestPaths(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> requiredTestEnvironment("AGENT_TASK_MANAGER_TEST_DB_URL"));
    registry.add("spring.datasource.username", () -> requiredTestEnvironment("AGENT_TASK_MANAGER_TEST_DB_USERNAME"));
    registry.add("spring.datasource.password", () -> requiredTestEnvironment("AGENT_TASK_MANAGER_TEST_DB_PASSWORD"));
    registry.add(
        "app.orchestration.worker-command",
        TestWorkspacePaths::fakeCodexCommand
    );
    registry.add(
        "app.bridge.command",
        TestWorkspacePaths::fakeCodexCommand
    );
    registry.add(
        "app.bridge.agent-id",
        () -> "test-bridge-agent"
    );
  }

  private static String requiredTestEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " must be set to an isolated test database.");
    }
    return value;
  }
}
