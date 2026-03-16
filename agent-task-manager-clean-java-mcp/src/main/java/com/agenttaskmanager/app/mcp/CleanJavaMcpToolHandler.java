package com.agenttaskmanager.app.mcp;

import com.agenttaskmanager.app.harness.cleanjava.CleanJavaTaskContextService;
import com.agenttaskmanager.app.mcp.cleanjava.CleanJavaMcpTools;
import com.agenttaskmanager.app.validation.ValidationPipelineService;
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
