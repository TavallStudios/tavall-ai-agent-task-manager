package org.tavall.ai.app.cleanjava;

import org.tavall.ai.app.harness.approval.HarnessApprovalGateResult;
import org.tavall.ai.app.harness.approval.HarnessApprovalService;
import org.tavall.ai.app.harness.cleanjava.CleanJavaDeterministicHarnessService;
import org.tavall.ai.app.harness.cleanjava.CleanJavaHarnessRunResult;
import org.tavall.ai.app.harness.cleanjava.CleanJavaTaskContext;
import org.tavall.ai.app.harness.cleanjava.CleanJavaTaskContextService;
import org.tavall.ai.app.harness.cleanjava.symbol.JavaSymbolPostEditResult;
import org.tavall.ai.app.harness.tools.HarnessToolBundleRequest;
import org.tavall.ai.app.harness.tools.HarnessToolBundleResult;
import org.tavall.ai.app.harness.tools.HarnessToolBundleService;
import org.tavall.ai.app.validation.ValidationPipelineService;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

@Service
public class CleanJavaHarnessValidator {

  private final HarnessApprovalService harnessApprovalService;
  private final CleanJavaDeterministicHarnessService cleanJavaDeterministicHarnessService;
  private final CleanJavaTaskContextService cleanJavaTaskContextService;
  private final HarnessToolBundleService harnessToolBundleService;
  private final ValidationPipelineService validationPipelineService;

  public CleanJavaHarnessValidator(
      HarnessApprovalService harnessApprovalService,
      CleanJavaDeterministicHarnessService cleanJavaDeterministicHarnessService,
      CleanJavaTaskContextService cleanJavaTaskContextService,
      HarnessToolBundleService harnessToolBundleService,
      ValidationPipelineService validationPipelineService
  ) {
    this.harnessApprovalService = harnessApprovalService;
    this.cleanJavaDeterministicHarnessService = cleanJavaDeterministicHarnessService;
    this.cleanJavaTaskContextService = cleanJavaTaskContextService;
    this.harnessToolBundleService = harnessToolBundleService;
    this.validationPipelineService = validationPipelineService;
  }

  public CleanJavaTaskContext buildTaskContext(
      String taskId,
      String workerTaskId,
      String projectKey,
      Path repoPath,
      String queryText
  ) {
    return cleanJavaTaskContextService.buildContext(taskId, workerTaskId, projectKey, repoPath, queryText);
  }

  public CleanJavaHarnessRunResult runHarness(
      String taskId,
      String workerTaskId,
      String projectKey,
      Path repoPath,
      String queryText
  ) {
    return cleanJavaDeterministicHarnessService.run(taskId, workerTaskId, projectKey, repoPath, queryText);
  }

  public HarnessApprovalGateResult runApprovalGate(
      String taskId,
      String workerTaskId,
      Path repoPath,
      String diffArtifactId,
      Integer workerExitCode,
      Boolean requiresIntegrationTests,
      Integer integrationTimeoutSeconds
  ) {
    return harnessApprovalService.runApprovalGate(
        taskId,
        workerTaskId,
        repoPath,
        diffArtifactId,
        workerExitCode,
        requiresIntegrationTests,
        integrationTimeoutSeconds
    );
  }

  public HarnessApprovalGateResult runApprovalGate(
      String taskId,
      String workerTaskId,
      Path repoPath,
      String diffArtifactId,
      Integer workerExitCode,
      Boolean requiresIntegrationTests,
      Integer integrationTimeoutSeconds,
      JavaSymbolPostEditResult javaSymbolPostEditResult
  ) {
    return harnessApprovalService.runApprovalGate(
        taskId,
        workerTaskId,
        repoPath,
        diffArtifactId,
        workerExitCode,
        requiresIntegrationTests,
        integrationTimeoutSeconds,
        javaSymbolPostEditResult
    );
  }

  public HarnessToolBundleResult runToolBundle(HarnessToolBundleRequest request) {
    return harnessToolBundleService.executeBundle(request);
  }

  public Object runIntegrationHarness(Path repoPath, Integer timeoutSeconds) {
    return validationPipelineService.runIntegrationTests(repoPath, timeoutSeconds);
  }
}

