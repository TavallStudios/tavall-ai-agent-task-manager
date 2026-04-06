package org.tavall.ai.app.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import java.util.List;

public interface McpPromptProvider {

  List<SyncPromptSpecification> promptSpecifications();
}

