package org.tavall.ai.app.mcp;

import org.tavall.ai.app.harness.cleanjava.CleanJavaTaskContextService;
import org.tavall.ai.app.mcp.cleanjava.CleanJavaMcpTools;
import org.tavall.ai.app.validation.ValidationPipelineService;
import org.springframework.stereotype.Component;

@Component
public class CleanJavaMcpToolHandler extends CleanJavaMcpTools {

  public CleanJavaMcpToolHandler(
      CleanJavaTaskContextService cleanJavaTaskContextService,
      ValidationPipelineService validationPipelineService,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(cleanJavaTaskContextService, validationPipelineService, schemaFactory, resultFactory, payloadMapper);
  }
}

