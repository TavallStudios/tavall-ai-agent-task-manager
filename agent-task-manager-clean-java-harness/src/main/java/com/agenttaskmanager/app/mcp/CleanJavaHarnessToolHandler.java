package com.agenttaskmanager.app.mcp;

import com.agenttaskmanager.app.mcp.cleanjava.CleanJavaHarnessTools;
import com.agenttaskmanager.app.validation.ValidationPipelineService;
import org.springframework.stereotype.Component;

@Component
public class CleanJavaHarnessToolHandler extends CleanJavaHarnessTools {

  public CleanJavaHarnessToolHandler(
      ValidationPipelineService validationPipelineService,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(validationPipelineService, schemaFactory, resultFactory, payloadMapper);
  }
}
