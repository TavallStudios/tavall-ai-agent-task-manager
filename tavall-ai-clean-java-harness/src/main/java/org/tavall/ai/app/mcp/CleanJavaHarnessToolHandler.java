package org.tavall.ai.app.mcp;

import org.tavall.ai.app.harness.approval.HarnessApprovalService;
import org.tavall.ai.app.harness.cleanjava.CleanJavaDeterministicHarnessService;
import org.tavall.ai.app.harness.cleanjava.CleanJavaTaskContextService;
import org.tavall.ai.app.harness.intake.HarnessTaskIntakeService;
import org.tavall.ai.app.harness.routing.HarnessRoutingService;
import org.tavall.ai.app.harness.state.HarnessStateService;
import org.tavall.ai.app.harness.tools.HarnessToolBundleService;
import org.tavall.ai.app.mcp.cleanjava.CleanJavaHarnessTools;
import org.tavall.ai.app.validation.ValidationPipelineService;
import org.springframework.stereotype.Component;

@Component
public class CleanJavaHarnessToolHandler extends CleanJavaHarnessTools {

  public CleanJavaHarnessToolHandler(
      HarnessApprovalService harnessApprovalService,
      CleanJavaDeterministicHarnessService cleanJavaDeterministicHarnessService,
      CleanJavaTaskContextService cleanJavaTaskContextService,
      HarnessRoutingService harnessRoutingService,
      HarnessStateService harnessStateService,
      HarnessTaskIntakeService harnessTaskIntakeService,
      HarnessToolBundleService harnessToolBundleService,
      ValidationPipelineService validationPipelineService,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(
        harnessApprovalService,
        cleanJavaDeterministicHarnessService,
        cleanJavaTaskContextService,
        harnessRoutingService,
        harnessStateService,
        harnessTaskIntakeService,
        harnessToolBundleService,
        validationPipelineService,
        schemaFactory,
        resultFactory,
        payloadMapper
    );
  }
}

