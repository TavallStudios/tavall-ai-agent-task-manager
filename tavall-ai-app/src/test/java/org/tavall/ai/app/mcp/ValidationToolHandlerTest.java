package org.tavall.ai.app.mcp;

import org.tavall.ai.app.mcp.tools.validation.ValidationToolHandler;
import org.tavall.ai.app.support.IntegrationTestSupport;
import io.modelcontextprotocol.server.McpSyncServer;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ValidationToolHandlerTest extends IntegrationTestSupport {

  @Autowired
  private ValidationToolHandler handler;

  @Autowired
  private McpSyncServer mcpSyncServer;

  @Test
  void shouldRegisterValidationTools() {
    Set<String> handlerTools = McpToolHandlerIntegrationAssertions.handlerToolNames(handler);
    Set<String> serverTools = McpToolHandlerIntegrationAssertions.serverToolNames(mcpSyncServer);

    McpToolHandlerIntegrationAssertions.assertContainsAll(
        handlerTools,
        "runArchUnitValidation",
        "runSpoonValidation",
        "runJavaLintValidation",
        "runIntegrationTests",
        "validatePatchScope",
        "storeValidationReport",
        "runCleanupDiffReview"
    );
    McpToolHandlerIntegrationAssertions.assertContainsAll(serverTools, "runArchUnitValidation", "runJavaLintValidation", "runIntegrationTests");
  }
}

