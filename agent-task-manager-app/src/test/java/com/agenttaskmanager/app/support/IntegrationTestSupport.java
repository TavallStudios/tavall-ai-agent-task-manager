package com.agenttaskmanager.app.support;

import com.agenttaskmanager.app.AgentTaskManagerApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
    "logging.level.com.agenttaskmanager=INFO",
    "app.bridge.enabled=false",
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
    registry.add(
        "app.orchestration.worker-command",
        TestWorkspacePaths::fakeCodexCommand
    );
  }
}
