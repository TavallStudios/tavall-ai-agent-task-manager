package com.agenttaskmanager.app.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import java.util.List;

public interface McpResourceProvider {

  List<SyncResourceSpecification> resourceSpecifications();
}
