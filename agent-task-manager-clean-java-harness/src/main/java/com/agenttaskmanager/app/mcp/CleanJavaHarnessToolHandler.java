package com.agenttaskmanager.app.mcp;

import com.agenttaskmanager.app.harness.approval.HarnessApprovalService;
import com.agenttaskmanager.app.harness.intake.HarnessTaskIntakeService;
import com.agenttaskmanager.app.harness.routing.HarnessRoutingService;
import com.agenttaskmanager.app.harness.state.HarnessStateService;
import com.agenttaskmanager.app.mcp.cleanjava.CleanJavaHarnessTools;
import com.agenttaskmanager.app.validation.ValidationPipelineService;
import org.springframework.stereotype.Component;

@Component
public class CleanJavaHarnessToolHandler extends CleanJavaHarnessTools {

  public CleanJavaHarnessToolHandler(
      HarnessApprovalService harnessApprovalService,
      HarnessRoutingService harnessRoutingService,
      HarnessStateService harnessStateService,
      HarnessTaskIntakeService harnessTaskIntakeService,
      ValidationPipelineService validationPipelineService,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(
        harnessApprovalService,
        harnessRoutingService,
        harnessStateService,
        harnessTaskIntakeService,
        validationPipelineService,
        schemaFactory,
        resultFactory,
        payloadMapper
    );
  }
}
