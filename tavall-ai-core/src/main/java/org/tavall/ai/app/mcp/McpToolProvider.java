package org.tavall.ai.app.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.util.List;

public interface McpToolProvider {

  List<SyncToolSpecification> toolSpecifications();

  default List<String> serverGroups() {
    return List.of("tavall-ai");
  }
}


